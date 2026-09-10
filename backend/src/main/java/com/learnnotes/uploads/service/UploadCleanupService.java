package com.learnnotes.uploads.service;

import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.doc.mapper.DocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传图片的清理与引用判定（删文档、删账号共用）。
 *
 * 路径两代并存：
 * - 老图（V5 图片隔离之前）：{@code /uploads/YYYY/MM/<hash>.<ext>}，**按内容哈希全局共享**，没有归属，
 *   删它之前必须确认全站再没人引用；
 * - 新图：{@code /uploads/u{ownerId}/YYYY/MM/<hash>.<ext>}，目录按用户独占，删账号可直接整目录清掉。
 */
@Service
public class UploadCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupService.class);

    /** 与 DocService 的 IMAGE_REF 保持一致：只认站内 /uploads/ 路径 */
    private static final Pattern IMAGE_REF = Pattern.compile("/uploads/[0-9a-zA-Z/._-]+");

    /** 新式路径：u{userId}/... */
    private static final Pattern OWNED_PATH = Pattern.compile("^u\\d+/.*");

    private final ImageStorageService imageStorage;
    private final DocMapper docMapper;
    private final DocAnnotationMapper annotationMapper;

    public UploadCleanupService(ImageStorageService imageStorage, DocMapper docMapper,
                                DocAnnotationMapper annotationMapper) {
        this.imageStorage = imageStorage;
        this.docMapper = docMapper;
        this.annotationMapper = annotationMapper;
    }

    /** 从若干段 Markdown 文本里收集 /uploads/ 相对路径（去重、保持出现顺序） */
    public Set<String> collectRefs(String... texts) {
        Set<String> refs = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher m = IMAGE_REF.matcher(text);
            while (m.find()) {
                refs.add(m.group().substring("/uploads/".length()));
            }
        }
        return refs;
    }

    /** 老式路径（没有 u{id}/ 段）：这类文件无归属，跨用户共享，删前必须全站问一句 */
    public static boolean isLegacyRel(String rel) {
        return rel != null && !OWNED_PATH.matcher(rel).matches();
    }

    /**
     * 删掉一篇文档后清理它引用的图片：全站（含其他用户、含见解正文）再无引用才删。
     * 尽力而为，单文件失败只记日志。
     */
    public void deleteUnreferenced(long docId, Set<String> rels) {
        Path root = imageStorage.uploadRoot();
        for (String rel : rels) {
            try {
                String pattern = "%" + rel + "%";
                if (docMapper.countOtherRefs(docId, pattern) > 0
                        || annotationMapper.countOtherRefs(docId, pattern) > 0) {
                    continue;
                }
                deleteWithin(root, rel);
            } catch (Exception e) {
                log.warn("清理图片失败（忽略）：{} -> {}", rel, e.getMessage());
            }
        }
    }

    /**
     * 删账号时清理该用户文档引用过的**老式**图片：还有别人的文档/见解引用就留着（跨用户共享的代价）。
     * 新式 {@code u{id}/} 路径不在这里处理——那是他自己的目录，由 {@link #purgeOwner} 整目录清。
     *
     * @return 实际删掉的文件数
     */
    public int deleteLegacyRefsAfterOwnerDeleted(long ownerId, Set<String> rels) {
        Path root = imageStorage.uploadRoot();
        int deleted = 0;
        for (String rel : rels) {
            if (!isLegacyRel(rel)) {
                continue;
            }
            try {
                String pattern = "%" + rel + "%";
                if (docMapper.countRefsExcludingOwner(ownerId, pattern) > 0
                        || annotationMapper.countRefsExcludingOwner(ownerId, pattern) > 0) {
                    continue;
                }
                if (deleteWithin(root, rel)) {
                    deleted++;
                }
            } catch (Exception e) {
                log.warn("清理老图片失败（忽略）：{} -> {}", rel, e.getMessage());
            }
        }
        return deleted;
    }

    /** 删除某用户自己的图片目录（u{id}/），返回删掉的文件数 */
    public int purgeOwner(long ownerId) {
        return imageStorage.purgeUser(ownerId);
    }

    /** 解析到上传根目录内再删；越界（含 ..）直接放弃 */
    private boolean deleteWithin(Path root, String rel) throws IOException {
        Path target = root.resolve(rel).normalize();
        if (!target.startsWith(root)) {
            log.warn("跳过越界的图片路径：{}", rel);
            return false;
        }
        return Files.deleteIfExists(target);
    }
}
