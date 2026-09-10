package com.learnnotes.admin;

import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.annotation.service.AnnotationService;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.SlugUtil;
import com.learnnotes.config.AppProperties;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.mapper.DocVersionMapper;
import com.learnnotes.imports.DocStorage;
import com.learnnotes.markdown.AnchorUtil;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import com.learnnotes.uploads.service.ImageStorageService;
import com.learnnotes.uploads.service.UploadCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 老图片迁移：把无归属的 /uploads/2026/08/x.png 复制进 /uploads/u{owner}/… 并改写引用。
 *
 * 这里刻意用**真实**的 DocService + AnnotationService + 重挂逻辑（只 mock 数据库 mapper），
 * 因为最危险的失效方式不是"文件没复制"，而是"图片块 URL 一变、挂在它上面的见解全被降级成 STALE"。
 */
class ImageMigrationServiceTest {

    @TempDir
    Path tempDir;

    private DocMapper docMapper;
    private DocAnnotationMapper annotationMapper;
    private ImageMigrationService migration;

    private static final long OWNER = 7L;
    private static final long DOC_ID = 11L;
    private static final String LEGACY_REL = "2026/08/abc1234567890def.png";

    private Doc doc;
    /** 见解的"数据库"：mock 的读写都作用在这份可变列表上，让重挂看到的是落库后的状态 */
    private final List<DocAnnotation> annotations = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        docMapper = mock(DocMapper.class);
        annotationMapper = mock(DocAnnotationMapper.class);

        AppProperties props = new AppProperties();
        props.setUploadDir(tempDir.resolve("uploads").toString());
        ImageStorageService imageStorage = new ImageStorageService(props);
        UploadCleanupService uploadCleanup = new UploadCleanupService(imageStorage, docMapper, annotationMapper);
        AnnotationService annotationService = new AnnotationService(annotationMapper, docMapper);

        com.learnnotes.doc.service.DocService docService = new com.learnnotes.doc.service.DocService(
                docMapper, mock(DocVersionMapper.class), mock(CatalogService.class), annotationService,
                mock(DocStorage.class), props, mock(com.learnnotes.auth.mapper.SysUserMapper.class),
                annotationMapper, uploadCleanup);

        migration = new ImageMigrationService(docMapper, annotationMapper, docService, annotationService,
                imageStorage, uploadCleanup);

        doc = new Doc();
        doc.setId(DOC_ID);
        doc.setOwnerId(OWNER);
        doc.setTopicId(3L);
        doc.setSlug("note");
        doc.setTitle("笔记");
        doc.setCurrentVersion(1);
        doc.setContentMd("# 标题\n\n段落文字\n\n![图](/uploads/" + LEGACY_REL + ")\n");
        doc.setContentHash(SlugUtil.sha1Hex(doc.getContentMd()));

        when(docMapper.selectAll()).thenReturn(List.of(doc));
        when(docMapper.selectById(DOC_ID)).thenReturn(doc);
        when(docMapper.updateGuarded(any(Doc.class), eq(1))).thenAnswer(inv -> {
            Doc updated = inv.getArgument(0);
            // 模拟落库：后续步骤/重跑读到的是新正文
            doc.setContentMd(updated.getContentMd());
            doc.setContentHash(updated.getContentHash());
            doc.setCurrentVersion(updated.getCurrentVersion());
            return 1;
        });

        // 见解表也要"落库"：重挂会重新查一遍见解，mock 若只吞掉写操作，
        // 就会用改前的旧 hash 去匹配，把本该 ACTIVE 的见解判成 STALE（测试会因此失真）
        when(annotationMapper.selectByDoc(anyLong())).thenAnswer(inv -> List.copyOf(annotations));
        when(annotationMapper.updateAnchor(any(), anyString(), anyInt(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    String hash = inv.getArgument(1);
                    int index = inv.getArgument(2);
                    String status = inv.getArgument(3);
                    String snippet = inv.getArgument(4);
                    annotations.stream().filter(a -> a.getId().equals(id)).findFirst().ifPresent(a -> {
                        a.setAnchorHash(hash);
                        a.setAnchorIndex(index);
                        a.setStatus(status);
                        a.setBlockSnippet(snippet);
                    });
                    return 1;
                });
        when(annotationMapper.updateContent(any(), anyString())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            String content = inv.getArgument(1);
            annotations.stream().filter(a -> a.getId().equals(id)).findFirst()
                    .ifPresent(a -> a.setContentMd(content));
            return 1;
        });
    }

    /** 造一个真实存在的"老图"文件 */
    private void writeLegacyImage() throws Exception {
        Path p = tempDir.resolve("uploads").resolve(LEGACY_REL);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    /** 挂一段见解在图片块上（用真实的块 hash/index）。图片段落没有专门类型，按块文本定位 */
    private DocAnnotation insightOnImageBlock(String contentMd) {
        List<Block> blocks = MarkdownBlockParser.parse(contentMd).getBlocks();
        Block imageBlock = blocks.stream()
                .filter(b -> b.getRaw() != null && b.getRaw().contains(LEGACY_REL))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("没找到图片块：" + contentMd));
        DocAnnotation ann = new DocAnnotation();
        ann.setId(77L);
        ann.setDocId(DOC_ID);
        ann.setAnchorHash(AnchorUtil.parseHash(imageBlock.getAnchor()));
        ann.setAnchorIndex(imageBlock.getIndex());
        ann.setBlockSnippet("![图](/uploads/" + LEGACY_REL + ")");
        ann.setContentMd("这张图说明了一切");
        ann.setStatus(DocAnnotation.STATUS_ACTIVE);
        ann.setDocVersionAtCreate(1);
        return ann;
    }

    @Test
    void dryRunOnlyReportsAndTouchesNothing() throws Exception {
        writeLegacyImage();
        // 本用例没有见解

        Map<String, Object> report = migration.migrate(true);

        assertEquals(true, report.get("dryRun"));
        assertEquals(1, report.get("docsWithLegacyImages"));
        assertEquals(1, report.get("legacyImages"));
        assertEquals(0, report.get("filesCopied"));
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve("u7")), "dryRun 不能落文件");
        assertEquals(doc.getContentMd().contains("/uploads/" + LEGACY_REL), true, "dryRun 不能改正文");
        verify(docMapper, never()).updateGuarded(any(), any(Integer.class));
    }

    @Test
    void migratesFileRewritesBodyAndBumpsVersion() throws Exception {
        writeLegacyImage();
        // 本用例没有见解

        Map<String, Object> report = migration.migrate(false);

        assertEquals(1, report.get("filesCopied"));
        // 文件进本人目录，原件保留（外部直链不裂）
        assertTrue(Files.exists(tempDir.resolve("uploads").resolve("u7").resolve(LEGACY_REL)));
        assertTrue(Files.exists(tempDir.resolve("uploads").resolve(LEGACY_REL)), "老文件要保留");

        ArgumentCaptor<Doc> captor = ArgumentCaptor.forClass(Doc.class);
        verify(docMapper).updateGuarded(captor.capture(), eq(1));
        Doc saved = captor.getValue();
        assertTrue(saved.getContentMd().contains("/uploads/u7/" + LEGACY_REL), saved.getContentMd());
        assertFalse(saved.getContentMd().contains("/uploads/" + LEGACY_REL + ")"),
                "不该残留老引用：" + saved.getContentMd());
        assertEquals(2, saved.getCurrentVersion(), "改写正文应留下新版本");
        assertNotNull(saved.getContentHash());
    }

    /** 最关键的回归：图片块 URL 改了，挂在它上面的见解不能被降级成 STALE */
    @Test
    void insightOnImageBlockStaysActive() throws Exception {
        writeLegacyImage();
        DocAnnotation ann = insightOnImageBlock(doc.getContentMd());
        annotations.add(ann);

        Map<String, Object> report = migration.migrate(false);

        assertEquals(1, report.get("anchorsShifted"));

        // 前移 + 重挂都会调用 updateAnchor：最终一次必须是 ACTIVE，且 hash 指向新块
        List<Block> newBlocks = MarkdownBlockParser.parse(doc.getContentMd()).getBlocks();
        Block newImageBlock = newBlocks.stream()
                .filter(b -> b.getRaw() != null && b.getRaw().contains("u7/" + LEGACY_REL))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("改写后没找到图片块：" + doc.getContentMd()));
        String expectedHash = AnchorUtil.parseHash(newImageBlock.getAnchor());

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(annotationMapper, atLeastOnce()).updateAnchor(any(), hashCaptor.capture(),
                any(Integer.class), statusCaptor.capture(), anyString());

        List<String> statuses = statusCaptor.getAllValues();
        List<String> hashes = hashCaptor.getAllValues();
        assertEquals(DocAnnotation.STATUS_ACTIVE, statuses.get(statuses.size() - 1),
                "见解应保持 ACTIVE，实际：" + statuses);
        assertEquals(expectedHash, hashes.get(hashes.size() - 1), "锚点应前移到新块的 hash");
        assertFalse(statuses.contains(DocAnnotation.STATUS_STALE), "不该出现 STALE：" + statuses);
    }

    /** 幂等：第二次跑什么都不该做 */
    @Test
    void secondRunIsNoOp() throws Exception {
        writeLegacyImage();
        // 本用例没有见解

        migration.migrate(false);
        Map<String, Object> second = migration.migrate(false);

        assertEquals(0, second.get("docsWithLegacyImages"), "已是 u{id}/ 路径不该再被处理");
        assertEquals(0, second.get("filesCopied"));
        verify(docMapper, atLeastOnce()).updateGuarded(any(), any(Integer.class));
    }

    /** 见解正文里的老图引用也要改写 */
    @Test
    void rewritesLegacyRefsInsideInsightBody() throws Exception {
        writeLegacyImage();
        DocAnnotation ann = insightOnImageBlock(doc.getContentMd());
        ann.setBlockSnippet("图"); // 快照不含路径，路径只出现在见解正文里
        ann.setContentMd("看这张 ![图](/uploads/" + LEGACY_REL + ")");
        annotations.add(ann);

        migration.migrate(false);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(annotationMapper).updateContent(eq(77L), contentCaptor.capture());
        assertEquals("看这张 ![图](/uploads/u7/" + LEGACY_REL + ")", contentCaptor.getValue());
    }
}
