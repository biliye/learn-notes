package com.learnnotes.imports.service;

import com.learnnotes.auth.CurrentUser;
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
 * 原文一字不改入库。V4 起支持多级目录：按 MetaResolver 解析出的目录路径
 * （大类 → … → 叶目录）逐级 find-or-create，文档落在链末叶目录。
 * V3 起数据归到当前登录用户（X-Api-Token 通道归到管理员）。
 */
@Slf4j
@Service
public class ImportService {

    public static final String ON_CONFLICT_NEW_VERSION = "NEW_VERSION";
    public static final String ON_CONFLICT_SKIP = "SKIP";
    public static final String ON_CONFLICT_FAIL = "FAIL";

    private final CatalogNodeMapper catalogMapper;
    private final DocService docService;
    private final DocMapper docMapper;
    private final DocStorage docStorage;

    public ImportService(CatalogNodeMapper catalogMapper,
                         DocService docService,
                         DocMapper docMapper,
                         DocStorage docStorage) {
        this.catalogMapper = catalogMapper;
        this.docService = docService;
        this.docMapper = docMapper;
        this.docStorage = docStorage;
    }

    @Transactional
    public ImportResult importDoc(CurrentUser user, String filename, String content,
                                  String categoryHint, String topicHint, String onConflict) {
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

        // 1. 归类节点链（不存在逐级自动创建并标记 auto_created=1，R14；按当前用户隔离）
        List<CatalogNode> chain = ensureChain(user.userId(), meta.getPathNames(), meta.getPathSlugs());
        CatalogNode category = chain.get(0);
        CatalogNode topic = chain.get(chain.size() - 1);
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
        Doc existing = docMapper.selectByTopicAndSlug(user.userId(), topic.getId(), docSlug);
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
                    DocService.UpdateResult ur = docService.update(user, existing.getId(), title,
                            meta.getSummary(), meta.getTags(), content, "agent 导入更新", filename);
                    result.setDocId(existing.getId());
                    result.setCreated(false);
                    result.setVersion(ur.version);
                    result.setReanchor(toReanchor(ur.reanchor));
                }
            }
        } else {
            Doc doc = docService.create(user, topic.getId(), title, docSlug, meta.getSummary(), meta.getTags(),
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
        List<String> slugPath = chain.stream().map(CatalogNode::getSlug).toList();
        String storedPath = docStorage.pathFor(user.username(), slugPath, docSlug);
        result.setStoredPath(storedPath);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        docStorage.write(user.username(), slugPath, docSlug, content);
                    } catch (Exception e) {
                        log.warn("原文落盘失败（不影响入库）：{}", e.getMessage());
                    }
                }
            });
        } else {
            try {
                docStorage.write(user.username(), slugPath, docSlug, content);
            } catch (Exception e) {
                log.warn("原文落盘失败（不影响入库）：{}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 多文件上传：逐个处理，单个失败不影响其他（R12）。
     */
    public List<ImportResult> importUpload(CurrentUser user, List<String> filenames, List<String> contents,
                                           String categoryHint, String topicHint) {
        List<ImportResult> results = new ArrayList<>();
        for (int i = 0; i < filenames.size(); i++) {
            String filename = filenames.get(i);
            String content = contents.get(i);
            ImportResult r = new ImportResult();
            r.setFilename(filename);
            try {
                r = importDoc(user, filename, content, categoryHint, topicHint, ON_CONFLICT_NEW_VERSION);
            } catch (Exception e) {
                r.setError(e.getMessage());
            }
            results.add(r);
        }
        return results;
    }

    // ---------- 内部 ----------

    /**
     * 沿目录路径（大类 → … → 叶目录）逐级 find-or-create。
     * 单层路径自动追加"未归类"叶目录；顶层大类的层级不足时：自动创建的大类自动上调，
     * 手动配置的大类报错（提示到分类管理调大层级）。
     *
     * @return 完整目录链（根→叶目录，至少有 1 个节点）
     */
    private List<CatalogNode> ensureChain(Long ownerId, List<String> names, List<String> slugs) {
        if (names == null || names.isEmpty()) {
            throw BizException.badRequest("无法确定归类目录：front-matter/文件名没有可解析的目录路径");
        }
        List<String> dirNames = new ArrayList<>(names);
        List<String> dirSlugs = new ArrayList<>(slugs == null ? new ArrayList<>() : slugs);
        if (dirNames.size() == 1) {
            // 只给了大类：落到其下"未归类"叶目录（旧语义）
            dirNames.add(MetaResolver.UNCATEGORIZED_NAME);
            dirSlugs.add(MetaResolver.UNCATEGORIZED_SLUG);
        }
        while (dirSlugs.size() < dirNames.size()) {
            dirSlugs.add(null);
        }

        List<CatalogNode> chain = new ArrayList<>();
        long parentId = 0;
        for (int i = 0; i < dirNames.size(); i++) {
            int level = i + 1;
            CatalogNode parent = chain.isEmpty() ? null : chain.get(chain.size() - 1);
            CatalogNode node = ensureNode(ownerId, parentId, level, dirNames.get(i), dirSlugs.get(i), parent);
            chain.add(node);
            parentId = node.getId();
        }

        // 顶层大类层级核对：需要 dirNames.size() 层，允许则可能自动上调 auto_created 大类
        CatalogNode top = chain.get(0);
        int needDepth = dirNames.size();
        int currentMax = top.getMaxLevel() == null ? CatalogNode.DEFAULT_MAX_LEVEL : top.getMaxLevel();
        if (needDepth > currentMax) {
            if (Boolean.TRUE.equals(top.getAutoCreated())) {
                CatalogNode upd = new CatalogNode();
                upd.setId(top.getId());
                upd.setMaxLevel(needDepth);
                catalogMapper.update(upd);
                top.setMaxLevel(needDepth);
            } else {
                throw BizException.conflict("分类「" + top.getName() + "」只允许 " + currentMax
                        + " 级目录，本次导入需要 " + needDepth + " 级：请先在分类管理里调大该大类的目录层级，或精简目录路径");
            }
        }
        return chain;
    }

    /** find-or-create：slug 精确（忽略大小写）→ name 精确 → name 去空格小写；不存在则创建。 */
    private CatalogNode ensureNode(Long ownerId, long parentId, int level, String name, String slugHint,
                                   CatalogNode parent) {
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("分类名称缺失");
        }
        String normalizedName = name.trim().replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        // 匹配顺序：slug 精确（忽略大小写）→ name 精确 → name 去空格并小写
        for (CatalogNode node : catalogMapper.selectByParent(ownerId, parentId)) {
            if (slugHint != null && node.getSlug().equalsIgnoreCase(slugHint)) {
                return node;
            }
        }
        for (CatalogNode node : catalogMapper.selectByParent(ownerId, parentId)) {
            if (node.getName().equals(name.trim())) {
                return node;
            }
        }
        for (CatalogNode node : catalogMapper.selectByParent(ownerId, parentId)) {
            if (node.getName().replaceAll("\\s", "").toLowerCase(Locale.ROOT).equals(normalizedName)) {
                return node;
            }
        }
        // 需要新建子目录：父目录若已含文档则不能再细分（文档只放叶目录）
        if (parent != null && parent.getDocCount() != null && parent.getDocCount() > 0) {
            throw BizException.badRequest("目录「" + parent.getName() + "」下已有 " + parent.getDocCount()
                    + " 篇文档，不能再往下建目录（请先在分类管理里把文档移出再细分）");
        }
        String slug = slugHint != null && !slugHint.isBlank() ? slugHint : SlugUtil.slugify(name, "node");
        // slug 冲突时追加 -2（与手动创建一致）
        if (catalogMapper.selectByParentAndSlug(ownerId, parentId, slug) != null) {
            slug = slug + "-" + (catalogMapper.countByParent(ownerId, parentId) + 1);
        }
        CatalogNode node = new CatalogNode();
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeLevel(level);
        node.setMaxLevel(parentId == 0 ? Math.max(CatalogNode.DEFAULT_MAX_LEVEL, level) : null);
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
