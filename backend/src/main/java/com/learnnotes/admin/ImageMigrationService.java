package com.learnnotes.admin;

import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.doc.AnnotationAccess;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.service.DocService;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import com.learnnotes.uploads.service.ImageStorageService;
import com.learnnotes.uploads.service.UploadCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次性迁移：把没有归属的老图片（{@code /uploads/YYYY/MM/<hash>.<ext>}，按内容哈希全局共享）
 * 复制进各自的用户目录（{@code /uploads/u{ownerId}/YYYY/MM/...}）并改写正文/见解里的引用。
 *
 * 为什么这么做：老布局下一个文件可能被多个用户的文档共同引用，"删一个人的违规内容"就无从下手。
 * 迁完之后每个用户的图片各归各家，删账号整目录清、删文档按引用清，都不会波及别人。
 *
 * 三条安全约定：
 * 1. **只复制不移动**：老文件原样留着，任何已流出的老 URL 不会 404；需要清理老文件是后续独立动作。
 * 2. **先落文件后改正文**：正文一旦指向新路径，目标文件必须已经存在，否则就是裂图。
 * 3. **锚点前移**：图片 URL 一变，所在块的 hash8 就变，挂在图片块上的见解会被重挂判成 STALE。
 *    这里在重挂之前把它们的 anchor_hash 前移到新哈希，块身份没变就不该让用户看到待复核标记。
 *
 * 幂等：已是 {@code u{id}/} 的引用不再处理；正文哈希没变则不起新版本。可以放心重跑。
 */
@Service
public class ImageMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ImageMigrationService.class);

    /** 迁移写版本时留的变更说明，方便在历史里认出这次批量改动 */
    private static final String CHANGE_NOTE = "图片路径迁移：老路径 → 用户目录";

    private final DocMapper docMapper;
    private final DocAnnotationMapper annotationMapper;
    private final DocService docService;
    private final AnnotationAccess annotationAccess;
    private final ImageStorageService imageStorage;
    private final UploadCleanupService uploadCleanup;

    public ImageMigrationService(DocMapper docMapper, DocAnnotationMapper annotationMapper,
                                 DocService docService, AnnotationAccess annotationAccess,
                                 ImageStorageService imageStorage, UploadCleanupService uploadCleanup) {
        this.docMapper = docMapper;
        this.annotationMapper = annotationMapper;
        this.docService = docService;
        this.annotationAccess = annotationAccess;
        this.imageStorage = imageStorage;
        this.uploadCleanup = uploadCleanup;
    }

    /**
     * 跑一次迁移。dryRun=true 只统计不改动（先看报告再决定）。
     * 整体一个事务：出错全部回滚，宁可一篇都不迁，也不留半迁移的中间态（文件复制是附加的，回滚不会丢东西）。
     */
    @Transactional
    public Map<String, Object> migrate(boolean dryRun) {
        long started = System.currentTimeMillis();
        List<Doc> docs = docMapper.selectAll();

        // 老图被几个用户引用：>1 说明当初的全局去重让多人共用一份文件，报告里要标出来
        Map<String, Set<Long>> ownersByRel = new LinkedHashMap<>();
        Set<String> allRefs = new LinkedHashSet<>();

        int docsWithLegacy = 0;
        int copied = 0;
        int annotationsRewritten = 0;
        int anchorsShifted = 0;
        List<String> samples = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Doc doc : docs) {
            long ownerId = doc.getOwnerId() == null ? 0L : doc.getOwnerId();
            String oldMd = doc.getContentMd();
            if (oldMd == null || oldMd.isBlank()) {
                continue;
            }
            List<DocAnnotation> anns = annotationMapper.selectByDoc(doc.getId());

            Set<String> refs = new LinkedHashSet<>(uploadCleanup.collectRefs(oldMd));
            for (DocAnnotation ann : anns) {
                refs.addAll(uploadCleanup.collectRefs(ann.getBlockSnippet(), ann.getContentMd()));
            }
            allRefs.addAll(refs);

            Set<String> legacy = new LinkedHashSet<>();
            for (String rel : refs) {
                if (UploadCleanupService.isLegacyRel(rel)) {
                    legacy.add(rel);
                }
            }
            if (legacy.isEmpty()) {
                continue;
            }
            docsWithLegacy++;
            for (String rel : legacy) {
                ownersByRel.computeIfAbsent(rel, k -> new LinkedHashSet<>()).add(ownerId);
            }

            // 老相对路径 → 该用户目录下的新相对路径
            Map<String, String> mapping = new LinkedHashMap<>();
            for (String rel : legacy) {
                mapping.put(rel, ImageStorageService.ownerSegment(ownerId) + "/" + rel);
            }
            String newMd = replaceRefs(oldMd, mapping);

            if (dryRun) {
                if (samples.size() < 10) {
                    samples.add("doc#" + doc.getId() + " (owner=" + ownerId + ") "
                            + legacy.iterator().next() + " → " + ImageStorageService.ownerSegment(ownerId) + "/"
                            + legacy.iterator().next());
                }
                continue;
            }

            // 1) 先把文件复制到位（复制而不是移动：老 URL 继续可用）
            for (String rel : legacy) {
                if (copyIntoOwnerDir(ownerId, rel)) {
                    copied++;
                }
            }

            // 2) 见解里的引用跟着改（快照 + 见解正文）
            annotationsRewritten += annotationAccess.rewriteUploadPaths(doc.getId(), mapping);

            // 3) 锚点前移：只处理"仅因本次 URL 改写而变化的块"
            List<Block> oldBlocks = MarkdownBlockParser.parse(oldMd).getBlocks();
            List<Block> newBlocks = MarkdownBlockParser.parse(newMd).getBlocks();
            if (oldBlocks.size() != newBlocks.size()) {
                // 纯 URL 改写不该改变块数量；出现即说明正文里有别的结构差异，保守跳过锚点前移
                warnings.add("doc#" + doc.getId() + " 块数变化（" + oldBlocks.size() + "→"
                        + newBlocks.size() + "），已跳过锚点前移");
            } else {
                Set<Integer> rewritten = rewrittenIndexes(oldBlocks, newBlocks, mapping);
                if (!rewritten.isEmpty()) {
                    anchorsShifted += annotationAccess.shiftAnchorsForRewrittenBlocks(
                            doc.getId(), oldBlocks, newBlocks, rewritten);
                }
            }

            // 4) 正文落库：重算 content_hash → 写新版本 → D6 重挂
            docService.rewriteContent(doc.getId(), newMd, CHANGE_NOTE);

            if (samples.size() < 10) {
                samples.add("doc#" + doc.getId() + " (owner=" + ownerId + ") " + legacy.iterator().next()
                        + " → " + ImageStorageService.ownerSegment(ownerId) + "/" + legacy.iterator().next());
            }
        }

        int sharedFiles = 0;
        for (Set<Long> owners : ownersByRel.values()) {
            if (owners.size() > 1) {
                sharedFiles++;
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dryRun", dryRun);
        report.put("docsScanned", docs.size());
        report.put("docsWithLegacyImages", docsWithLegacy);
        report.put("legacyImages", ownersByRel.size());
        report.put("sharedLegacyImages", sharedFiles);
        report.put("filesCopied", copied);
        report.put("annotationsRewritten", annotationsRewritten);
        report.put("anchorsShifted", anchorsShifted);
        report.put("orphanImages", countOrphanImages(allRefs));
        report.put("samples", samples);
        report.put("warnings", warnings);
        report.put("elapsedMs", System.currentTimeMillis() - started);
        log.info("图片路径迁移完成：dryRun={} 文档={} 老图={} 复制={} 锚点前移={}",
                dryRun, docsWithLegacy, ownersByRel.size(), copied, anchorsShifted);
        return report;
    }

    /** 块序号：该块文本仅因 URL 改写而变化（改写后的旧文本 == 新文本）才计入 */
    private Set<Integer> rewrittenIndexes(List<Block> oldBlocks, List<Block> newBlocks,
                                          Map<String, String> mapping) {
        Set<Integer> indexes = new LinkedHashSet<>();
        for (int i = 0; i < oldBlocks.size(); i++) {
            String oldRaw = oldBlocks.get(i).getRaw();
            String newRaw = newBlocks.get(i).getRaw();
            if (oldRaw == null || oldRaw.equals(newRaw)) {
                continue;
            }
            if (replaceRefs(oldRaw, mapping).equals(newRaw)) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    /** 把一个用户的某张老图复制进他自己的目录；源不存在就跳过（引用悬空是既有事实，不制造新问题） */
    private boolean copyIntoOwnerDir(long ownerId, String rel) {
        Path root = imageStorage.uploadRoot();
        Path source = root.resolve(rel).normalize();
        Path target = root.resolve(ImageStorageService.ownerSegment(ownerId)).resolve(rel).normalize();
        if (!source.startsWith(root) || !target.startsWith(root)) {
            log.warn("跳过越界的图片路径：{}", rel);
            return false;
        }
        if (!Files.isRegularFile(source)) {
            log.warn("老图片文件不存在，跳过复制：{}", rel);
            return false;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warn("复制老图片失败：{} -> {}", rel, e.getMessage());
            return false;
        }
    }

    /** 上传目录里没有任何文档/见解引用、也不是任何用户目录下的文件数（只报告，不删） */
    private int countOrphanImages(Set<String> referenced) {
        Path root = imageStorage.uploadRoot();
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var walk = Files.walk(root)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        // 用户目录下的文件天然归于某人，不算孤儿
                        return !rel.matches("^u\\d+/.*") && !referenced.contains(rel);
                    })
                    .count();
        } catch (IOException e) {
            log.warn("统计孤儿图片失败（忽略）：{}", e.getMessage());
            return 0;
        }
    }

    /** 把文本里 /uploads/<老路径> 换成 /uploads/<新路径>；按整段引用替换，避免子串误伤 */
    private static String replaceRefs(String text, Map<String, String> mapping) {
        if (text == null || text.isEmpty() || mapping.isEmpty()) {
            return text;
        }
        String out = text;
        // 长路径优先，避免 A 是 B 的前缀时替换出错误结果
        List<String> keys = new ArrayList<>(mapping.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        for (String rel : keys) {
            out = out.replace("/uploads/" + rel, "/uploads/" + mapping.get(rel));
        }
        return out;
    }
}
