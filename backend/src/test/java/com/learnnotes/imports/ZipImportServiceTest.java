package com.learnnotes.imports;

import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import com.learnnotes.imports.dto.ZipImportResult;
import com.learnnotes.imports.service.ZipImportService;
import com.learnnotes.uploads.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 压缩包导入（编辑器草稿流）测试：解包、front-matter、图片引用重写、缺图/未引用图提示、路径穿越拒绝。
 */
class ZipImportServiceTest {

    @TempDir
    Path tempDir;

    private ZipImportService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setUploadDir(tempDir.resolve("uploads").toString());
        props.setMaxImageMb(5);
        service = new ZipImportService(new ImageStorageService(props));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile zipOf(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "note.zip", "application/zip", out.toByteArray());
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** md + 图片：相对路径引用被重写为 /uploads/...，title 取 front-matter，图片真实落盘 */
    @Test
    void mdWithImagesRewrittenAndStored() throws Exception {
        String md = """
                ---
                category: Java
                topic: 函数
                title: 方法与函数式接口
                slug: java-method-functional
                tags: [基础, lambda]
                ---

                # 方法

                一张示意图：

                ![示意图](images/demo.png)
                """;
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("docs/note.md", utf8(md));
        zip.put("docs/images/demo.png", pngBytes());

        ZipImportResult r = service.importZip(zipOf(zip));

        assertEquals("方法与函数式接口", r.getTitle());
        assertEquals("java-method-functional", r.getSlug());
        assertEquals(2, r.getTags().size());
        // front-matter 已剥离、图片引用已重写
        assertFalse(r.getContentMd().contains("category:"));
        assertTrue(r.getContentMd().contains("![示意图](/uploads/"));
        assertFalse(r.getContentMd().contains("images/demo.png"));
        assertEquals(1, r.getImportedImages());
        // 图片真实落盘在 uploads 目录
        long pngs;
        try (var walk = Files.walk(tempDir.resolve("uploads"))) {
            pngs = walk.filter(p -> p.toString().endsWith(".png")).count();
        }
        assertEquals(1, pngs);
    }

    /** 引用了但包内没有的图：保留原引用并提示 */
    @Test
    void missingImageKeptAndWarned() throws Exception {
        String md = "# 缺图\n\n![不存在](missing.png)\n";
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("note.md", utf8(md));

        ZipImportResult r = service.importZip(zipOf(zip));

        assertTrue(r.getContentMd().contains("![不存在](missing.png)"));
        assertEquals(0, r.getImportedImages());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("未在压缩包内找到")));
    }

    /** 未被正文引用的图片：不导入并提示 */
    @Test
    void unreferencedImageSkipped() throws Exception {
        String md = "# 只有文字\n";
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("note.md", utf8(md));
        zip.put("extra.png", pngBytes());

        ZipImportResult r = service.importZip(zipOf(zip));

        assertEquals(1, r.getSkippedImages());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("未被正文引用")));
    }

    /** 没有 md 的压缩包 → 400 */
    @Test
    void noMdRejected() throws Exception {
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("images/a.png", pngBytes());
        BizException e = assertThrows(BizException.class, () -> service.importZip(zipOf(zip)));
        assertEquals(400, e.getHttpStatus());
        assertTrue(e.getMessage().contains("没有找到"));
    }

    /** 多个 md：只取路径排序第一个并提示 */
    @Test
    void multipleMdTakesFirstSorted() throws Exception {
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("b.md", utf8("# B 文档"));
        zip.put("a.md", utf8("# A 文档"));
        zip.put("images/x.png", pngBytes());

        ZipImportResult r = service.importZip(zipOf(zip));

        assertEquals("A 文档", r.getTitle());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("仅导入第一个")));
    }

    /** 正文引用越出压缩包根目录（根目录 md 引用 ../）→ 拒绝并保留原引用 */
    @Test
    void traversalRefRejected() throws Exception {
        String md = "# 越界\n\n![越界](../secret.png)\n";
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("note.md", utf8(md));

        ZipImportResult r = service.importZip(zipOf(zip));

        assertTrue(r.getContentMd().contains("![越界](../secret.png)"));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("越出压缩包根目录")));
    }

    /** 子目录 md 引用 ../ 若仍在压缩包根内 → 正常解析（docs/note.md 引用 ../logo.png → logo.png） */
    @Test
    void parentRefWithinZipResolved() throws Exception {
        String md = "# 正常\n\n![logo](../logo.png)\n";
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("docs/note.md", utf8(md));
        zip.put("logo.png", pngBytes());

        ZipImportResult r = service.importZip(zipOf(zip));

        assertTrue(r.getContentMd().contains("![logo](/uploads/"));
        assertFalse(r.getContentMd().contains("../logo.png"));
        assertEquals(1, r.getImportedImages());
    }

    /** 压缩包内条目路径含 ../ → 直接拒绝该条目 */
    @Test
    void traversalEntryRejected() throws Exception {
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("../../evil.png", pngBytes());
        zip.put("note.md", utf8("# 正常"));

        ZipImportResult r = service.importZip(zipOf(zip));

        assertEquals(0, r.getSkippedImages());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("跳过非法路径条目")));
    }

    /** title 缺失时取正文首个 H1 */
    @Test
    void titleFromFirstH1WhenNoFm() throws Exception {
        String md = "# 标题取自一级标题\n\n正文。\n";
        Map<String, byte[]> zip = new LinkedHashMap<>();
        zip.put("note.md", utf8(md));

        ZipImportResult r = service.importZip(zipOf(zip));

        assertEquals("标题取自一级标题", r.getTitle());
    }
}
