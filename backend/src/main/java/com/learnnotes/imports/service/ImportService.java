package com.learnnotes.imports.service;

import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SlugUtil;
import com.learnnotes.doc.AnnotationAccess;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.service.DocService;
import com.learnnotes.imports.DocStorage;
import com.learnnotes.imports.MetaResolver;
import com.learnnotes.imports.dto.ImportResult;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.FrontMatterParser;
import com.learnnotes.markdown.MarkdownBlockParser;
import com.learnnotes.markdown.ParsedDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文档导入与自动归类（R12–R17、D8）—— agent 的主入口。
 * 原文一字不改入库。
 */
@Slf4j
@Service
public class ImportService {

    public static final String ON_CONFLICT_NEW_VERSION = "NEW_VERSION";
    public static final String ON_CONFLICT_SKIP = "SKIP";
    public static final String ON_CONFLICT_FAIL = "FAIL";

    private final CatalogService catalogService;
    private final CatalogNodeMapper catalogMapper;
    private final DocService docService;
    private final DocMapper docMapper;
    private final DocStorage docStorage;

    public ImportService(CatalogService catalogService,
                         CatalogNodeMapper catalogMapper,
                         DocService docService,
                         DocMapper docMapper,
                         DocStorage docStorage) {
        this.catalogService = catalogService;
        this.catalogMapper = catalogMapper;
        this.docService = docService;
        this.docMapper = docMapper;
        this.docStorage = docStorage;
    }

    @Transactional
    public ImportResult importDoc(String filename, String content, String categoryHint, String topicHint,
                                  String onConflict) {
        if (content == null || content.isBlank()) {
            throw BizException.badRequest("content 不能为空");
        }
        String mode = onConflict == null || onConflict.isBlank() ? ON_CONFLICT_NEW_VERSION : onConflict;
        if (!List.of(ON_CONFLICT_NEW_VERSION, ON_CONFLICT_SKIP, ON_CONFLICT_FAIL).contains(mode)) {
            throw BizException.badRequest("onConflict 只能为 NEW_VERSION / SKIP / FAIL");
        }

        ParsedDoc parsed = FrontMatterParser.parse(content);
        MarkdownBlockParser.ParseResult parsedBody = MarkdownBlockParser.parseBody(parsed.getBody());
        MetaResolver.ResolvedMeta meta = MetaResolver.resolve(filename, content, parsed.getMeta(), categoryHint, topicHint);

        ImportResult result = new ImportResult();
        result.setResolvedBy(meta.getSource().name());
        result.setWarnings(new ArrayList<>(parsedBody.getWarnings()));
        result.getWarnings().addAll(meta.getWarnings());

        // 1. 归类节点（不存在自动创建并标记 auto_created=1，R14）
        CatalogNode category = ensureNode(0L, CatalogNode.LEVEL_CATEGORY, meta.getCategoryName(), meta.getCategorySlug());
        CatalogNode topic = ensureNode(category.getId(), CatalogNode.LEVEL_TOPIC, meta.getTopicName(), meta.getTopicSlug());
        result.setCategory(nodeInfo(category));
        result.setTopic(nodeInfo(topic));

        // 2. 标题与 slug
        String title = meta.getTitle() != null ? meta.getTitle() : firstH1(parsedBody.getBlocks());
        if (title == null || title.isBlank()) {
            throw BizException.badRequest("无法确定文档标题：front-matter 无 title 且正文没有一级标题");
        }
        String docSlug = meta.getDocSlug() != null && !meta.getDocSlug().isBlank()
                ? meta.getDocSlug() : SlugUtil.slugify(title);
        validateDocSlug(docSlug);

        // 3. 冲突处理（R15：slug 重复默认视为同文档新版本）
        Doc existing = docMapper.selectByTopicAndSlug(topic.getId(), docSlug);
        if (existing != null) {
            switch (mode) {
                case ON_CONFLICT_SKIP -> {
                    result.setDocId(existing.getId());
                    result.setCreated(false);
                    result.setVersion(existing.getCurrentVersion());
                    result.setTitle(existing.getTitle());
                    result.setSlug(existing.getSlug());
                    result.setReanchor(zeroReanchor());
                    result.getWarnings().add("slug 已存在且 onConflict=SKIP，未做任何修改");
                    return result;
                }
                case ON_CONFLICT_FAIL -> throw BizException.conflict(
                        "slug 冲突：" + docSlug + "，onConflict=FAIL 拒绝覆盖");
                default -> {
                    DocService.UpdateResult ur = docService.update(existing.getId(), title,
                            meta.getSummary(), meta.getTags(), content, "agent 导入更新", filename);
                    result.setDocId(existing.getId());
                    result.setCreated(false);
                    result.setVersion(ur.version);
                    result.setReanchor(toReanchor(ur.reanchor));
                }
            }
        } else {
            Doc doc = docService.create(topic.getId(), title, docSlug, meta.getSummary(), meta.getTags(),
                    content, filename);
            if (meta.getOrder() != null) {
                docMapper.update(setOrderDoc(doc.getId(), meta.getOrder()));
            }
            result.setDocId(doc.getId());
            result.setCreated(true);
            result.setVersion(doc.getCurrentVersion());
            result.setReanchor(zeroReanchor());
        }
        result.setTitle(title);
        result.setSlug(docSlug);

        // 4. 原文落盘（R17，备份 L3）——事务提交后执行，失败只记 warning 不回滚
        String storedPath = docStorage.pathFor(category.getSlug(), topic.getSlug(), docSlug);
        result.setStoredPath(storedPath);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        docStorage.write(category.getSlug(), topic.getSlug(), docSlug, content);
                    } catch (Exception e) {
                        log.warn("原文落盘失败（不影响入库）：{}", e.getMessage());
                    }
                }
            });
        } else {
            try {
                docStorage.write(category.getSlug(), topic.getSlug(), docSlug, content);
            } catch (Exception e) {
                log.warn("原文落盘失败（不影响入库）：{}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 多文件上传：逐个处理，单个失败不影响其他（R12）。
     */
    public List<ImportResult> importUpload(List<String> filenames, List<String> contents,
                                           String categoryHint, String topicHint) {
        List<ImportResult> results = new ArrayList<>();
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);
            String content = contents.get(i);
            ImportResult r = new ImportResult();
            r.setFilename(filename);
            try {
                r = importDoc(filename, content, categoryHint, topicHint, ON_CONFLICT_NEW_VERSION);
            } catch (Exception e) {
                r.setError(e.getMessage());
            }
            results.add(r);
        }
        return results;
    }

    // ---------- 内部 ----------

    private CatalogNode ensureNode(Long parentId, int level, String name, String slugHint) {
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("分类名称缺失");
        }
        String normalizedName = name.trim().replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        // 匹配顺序：slug 精确（忽略大小写）→ name 精确 → name 去空格并小写
        for (CatalogNode node : catalogMapper.selectByParent(parentId)) {
            if (slugHint != null && node.getSlug().equalsIgnoreCase(slugHint)) {
                return node;
            }
        }
        for (CatalogNode node : catalogMapper.selectByParent(parentId)) {
            if (node.getName().equals(name.trim())) {
                return node;
            }
        }
        for (CatalogNode node : catalogMapper.selectByParent(parentId)) {
            if (node.getName().replaceAll("\\s", "").toLowerCase(Locale.ROOT).equals(normalizedName)) {
                return node;
            }
        }
        String slug = slugHint != null && !slugHint.isBlank() ? slugHint : SlugUtil.slugify(name, "node");
        // slug 冲突时追加 -2（与手动创建一致）
        if (catalogMapper.selectByParentAndSlug(parentId, slug) != null) {
            slug = slug + "-" + (catalogMapper.countByParent(parentId) + 1);
        }
        CatalogNode node = new CatalogNode();
        node.setParentId(parentId);
        node.setNodeLevel(level);
        node.setName(name.trim());
        node.setSlug(slug);
        node.setSortOrder(100);
        node.setAutoCreated(true);
        catalogMapper.insert(node);
        return node;
    }

    private void validateDocSlug(String slug) {
        if (slug.contains("..") || slug.contains("/") || slug.contains("\\")
                || slug.startsWith(".") || slug.matches("^[a-zA-Z]:.*")) {
            throw BizException.badRequest("slug 含非法字符，已拒绝：" + slug);
        }
    }

    private String firstH1(List<Block> blocks) {
        for (Block b : blocks) {
            if ("heading".equals(b.getType()) && b.getLevel() != null && b.getLevel() == 1) {
                // 去掉 # 前缀与行尾标记
                return b.getRaw().replaceAll("^#+\\s*", "").replaceAll("\\s*#+$", "").trim();
            }
        }
        return null;
    }

    private ImportResult.NodeInfo nodeInfo(CatalogNode node) {
        ImportResult.NodeInfo info = new ImportResult.NodeInfo();
        info.setId(node.getId());
        info.setName(node.getName());
        info.setAutoCreated(Boolean.TRUE.equals(node.getAutoCreated()));
        return info;
    }

    private ImportResult.Reanchor zeroReanchor() {
        ImportResult.Reanchor r = new ImportResult.Reanchor();
        r.setActive(0);
        r.setStale(0);
        r.setOrphan(0);
        return r;
    }

    private ImportResult.Reanchor toReanchor(AnnotationAccess.ReanchorCount count) {
        ImportResult.Reanchor r = new ImportResult.Reanchor();
        r.setActive(count.getActive());
        r.setStale(count.getStale());
        r.setOrphan(count.getOrphan());
        return r;
    }

    private Doc setOrderDoc(Long id, int order) {
        Doc doc = new Doc();
        doc.setId(id);
        doc.setSortOrder(order);
        return doc;
    }
}
