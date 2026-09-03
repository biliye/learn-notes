package com.learnnotes.doc.service;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SearchUtil;
import com.learnnotes.common.SlugUtil;
import com.learnnotes.doc.AnnotationAccess;
import com.learnnotes.doc.dto.DocDetailDto;
import com.learnnotes.doc.dto.DocListItem;
import com.learnnotes.doc.dto.DocListRow;
import com.learnnotes.doc.dto.DocPageDto;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.entity.DocVersion;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.mapper.DocVersionMapper;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档 CRUD + 版本 + 详情装配（R5、R10、R11、§5.3）。V3 起按用户隔离：
 * 普通用户只能读写自己的文档；管理员可访问任意文档。
 */
@Service
public class DocService {

    /** 英文单词/数字串 */
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]+");

    private final DocMapper docMapper;
    private final DocVersionMapper versionMapper;
    private final CatalogService catalogService;
    private final AnnotationAccess annotationAccess;

    public DocService(DocMapper docMapper,
                      DocVersionMapper versionMapper,
                      CatalogService catalogService,
                      AnnotationAccess annotationAccess) {
        this.docMapper = docMapper;
        this.versionMapper = versionMapper;
        this.catalogService = catalogService;
        this.annotationAccess = annotationAccess;
    }

    // ---------- 读 ----------

    public DocPageDto list(CurrentUser user, Long topicId, Long categoryId, String keyword, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        String kw = null;
        if (keyword != null && !keyword.isBlank()) {
            kw = "%" + SearchUtil.escapeLike(keyword.trim()) + "%";
        }
        // categoryId 语义：该目录整棵子树（含根），把子树节点展开后交给 mapper 过滤
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = catalogService.subtreeIds(user.userId(), categoryId);
            if (categoryIds.isEmpty()) {
                DocPageDto empty = new DocPageDto();
                empty.setTotal(0);
                empty.setPage(page);
                empty.setSize(size);
                empty.setItems(new ArrayList<>());
                return empty;
            }
        }
        long total = docMapper.countList(user.userId(), topicId, categoryIds, kw);
        List<DocListItem> items = docMapper.selectList(user.userId(), topicId, categoryIds, kw, (page - 1) * size, size).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
        DocPageDto dto = new DocPageDto();
        dto.setTotal(total);
        dto.setPage(page);
        dto.setSize(size);
        dto.setItems(items);
        return dto;
    }

    private DocListItem toListItem(DocListRow row) {
        DocListItem item = new DocListItem();
        item.setId(row.getId());
        item.setTopicId(row.getTopicId());
        item.setTitle(row.getTitle());
        item.setSlug(row.getSlug());
        item.setSummary(row.getSummary());
        item.setTags(splitTags(row.getTags()));
        item.setCurrentVersion(row.getCurrentVersion());
        item.setWordCount(row.getWordCount());
        item.setSortOrder(row.getSortOrder());
        item.setCreatedAt(row.getCreatedAt());
        item.setUpdatedAt(row.getUpdatedAt());
        return item;
    }

    public DocDetailDto detail(Long id, CurrentUser user) {
        Doc doc = requireById(id, user);
        // 面包屑输出 根→…→本目录 完整链（doc 归属已在 requireById 校验）
        List<CatalogNode> chain = catalogService.pathFromRoot(doc.getTopicId());

        DocDetailDto dto = new DocDetailDto();
        dto.setId(doc.getId());
        dto.setTitle(doc.getTitle());
        dto.setSlug(doc.getSlug());
        dto.setSummary(doc.getSummary());
        dto.setTags(splitTags(doc.getTags()));
        dto.setCurrentVersion(doc.getCurrentVersion());
        dto.setUpdatedAt(doc.getUpdatedAt());

        for (CatalogNode n : chain) {
            DocDetailDto.BreadcrumbItem b = new DocDetailDto.BreadcrumbItem();
            b.setId(n.getId());
            b.setName(n.getName());
            b.setSlug(n.getSlug());
            dto.getBreadcrumb().add(b);
        }

        dto.setBlocks(MarkdownBlockParser.parse(doc.getContentMd()).getBlocks());
        dto.setAnnotations(annotationAccess.listForDoc(doc.getId()));
        return dto;
    }

    public String raw(Long id, CurrentUser user) {
        return requireById(id, user).getContentMd();
    }

    public List<Map<String, Object>> versions(Long id, CurrentUser user) {
        requireById(id, user);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DocVersion v : versionMapper.selectByDoc(id)) {
            Map<String, Object> m = new HashMap<>();
            m.put("version", v.getVersion());
            m.put("changeNote", v.getChangeNote());
            m.put("createdAt", v.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> versionContent(Long id, int version, CurrentUser user) {
        requireById(id, user);
        DocVersion v = versionMapper.selectByDocAndVersion(id, version);
        if (v == null) {
            throw BizException.notFound("版本不存在：" + version);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("version", v.getVersion());
        m.put("contentMd", v.getContentMd());
        m.put("createdAt", v.getCreatedAt());
        return m;
    }

    // ---------- 写 ----------

    @Transactional
    public Doc create(CurrentUser user, Long topicId, String title, String slug, String summary, List<String> tags,
                      String contentMd, String sourceFilename) {
        requireDocFolder(topicId, user);
        if (title == null || title.isBlank()) {
            throw BizException.badRequest("标题不能为空");
        }
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("正文不能为空");
        }
        String finalSlug = slug == null || slug.isBlank() ? SlugUtil.slugify(title) : slug;
        if (docMapper.selectByTopicAndSlug(user.userId(), topicId, finalSlug) != null) {
            throw BizException.conflict("该目录下已存在 slug 为 " + finalSlug + " 的文档");
        }
        Doc doc = buildDoc(user.userId(), topicId, finalSlug, title, summary, tags, contentMd, sourceFilename, 1, 100);
        docMapper.insert(doc);
        writeVersion(doc, 1, null);
        catalogService.incrDocCount(topicId, 1);
        return doc;
    }

    /**
     * 更新文档。content_hash 相同则不产生新版本（避免重复导入刷版本号）；
     * 正文变化则版本 +1、写 doc_version（存新正文）、触发 D6 重挂。
     */
    @Transactional
    public UpdateResult update(CurrentUser user, Long id, String title, String summary, List<String> tags,
                               String contentMd, String changeNote, String sourceFilename) {
        Doc doc = requireById(id, user);
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("正文不能为空");
        }
        String newHash = SlugUtil.sha1Hex(contentMd);
        UpdateResult result = new UpdateResult();
        result.doc = doc;
        result.version = doc.getCurrentVersion();
        if (newHash.equals(doc.getContentHash())) {
            result.changed = false;
            result.reanchor = AnnotationAccess.ReanchorCount.zero();
            return result;
        }

        int newVersion = doc.getCurrentVersion() + 1;
        List<Block> oldBlocks = MarkdownBlockParser.parse(doc.getContentMd()).getBlocks();
        List<Block> newBlocks = MarkdownBlockParser.parse(contentMd).getBlocks();

        Doc updated = buildDoc(doc.getOwnerId(), doc.getTopicId(), doc.getSlug(),
                title != null ? title : doc.getTitle(),
                summary != null ? summary : doc.getSummary(),
                tags != null ? tags : splitTags(doc.getTags()),
                contentMd, sourceFilename != null ? sourceFilename : doc.getSourceFilename(),
                newVersion, doc.getSortOrder());
        updated.setId(doc.getId());
        docMapper.update(updated);
        writeVersion(updated, newVersion, changeNote);

        result.changed = true;
        result.version = newVersion;
        result.reanchor = annotationAccess.reanchor(doc.getId(), oldBlocks, newBlocks);
        return result;
    }

    @Transactional
    public void move(CurrentUser user, Long id, Long topicId) {
        Doc doc = requireById(id, user);
        requireDocFolder(topicId, user);
        if (Objects.equals(doc.getTopicId(), topicId)) {
            return;
        }
        docMapper.update(buildMoveDoc(id, topicId));
        catalogService.incrDocCount(doc.getTopicId(), -1);
        catalogService.incrDocCount(topicId, 1);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Doc doc = requireById(id, user);
        annotationAccess.deleteByDoc(id);
        versionMapper.deleteByDoc(id);
        docMapper.deleteById(id);
        catalogService.incrDocCount(doc.getTopicId(), -1);
    }

    // ---------- 内部 ----------

    private Doc buildDoc(Long ownerId, Long topicId, String slug, String title, String summary, List<String> tags,
                         String contentMd, String sourceFilename, int version, Integer sortOrder) {
        List<Block> blocks = MarkdownBlockParser.parse(contentMd).getBlocks();
        Doc doc = new Doc();
        doc.setOwnerId(ownerId);
        doc.setTopicId(topicId);
        doc.setSlug(slug);
        doc.setTitle(title == null ? "" : title.trim());
        doc.setSummary(summary);
        doc.setTags(joinTags(tags));
        doc.setSourceFilename(sourceFilename);
        doc.setCurrentVersion(version);
        doc.setContentMd(contentMd);
        doc.setContentHash(SlugUtil.sha1Hex(contentMd));
        doc.setBlockCount(blocks.size());
        doc.setWordCount(wordCount(contentMd));
        doc.setSortOrder(sortOrder);
        return doc;
    }

    private Doc buildMoveDoc(Long id, Long topicId) {
        Doc doc = new Doc();
        doc.setId(id);
        doc.setTopicId(topicId);
        return doc;
    }

    private void writeVersion(Doc doc, int version, String changeNote) {
        DocVersion v = new DocVersion();
        v.setDocId(doc.getId());
        v.setVersion(version);
        v.setContentMd(doc.getContentMd());
        v.setContentHash(doc.getContentHash());
        v.setChangeNote(changeNote);
        versionMapper.insert(v);
    }

    /** 文档目标目录校验（V4 多级目录）：非顶层大类、且当前没有子目录（叶目录）。 */
    private CatalogNode requireDocFolder(Long topicId, CurrentUser user) {
        CatalogNode node = catalogService.requireById(topicId, user);
        if (node.getParentId() == null || node.getParentId() == 0) {
            throw BizException.badRequest("顶层大类不能直接放文档，请先在其下新建目录");
        }
        if (catalogService.hasChildren(node)) {
            throw BizException.badRequest("该目录下还有子目录，文档只能放在没有子目录的目录里");
        }
        return node;
    }

    /**
     * 按 id 取文档并校验归属：本人或管理员可访问，否则 403。
     */
    public Doc requireById(Long id, CurrentUser user) {
        if (id == null) {
            throw BizException.badRequest("id 不能为空");
        }
        Doc doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("文档不存在：" + id);
        }
        if (!user.isAdmin() && !doc.getOwnerId().equals(user.userId())) {
            throw BizException.forbidden("无权访问该文档");
        }
        return doc;
    }

    /**
     * 字数统计：去掉代码块后按"中文字符数 + 英文单词数"计。
     */
    public static int wordCount(String contentMd) {
        List<Block> blocks = MarkdownBlockParser.parse(contentMd).getBlocks();
        int count = 0;
        for (Block b : blocks) {
            if ("code".equals(b.getType())) {
                continue;
            }
            String text = b.getRaw();
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp >= 0x4E00 && cp <= 0x9FFF) {
                    count++;
                }
            }
            var m = WORD.matcher(text);
            while (m.find()) {
                count++;
            }
        }
        return count;
    }

    public static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags.stream().filter(Objects::nonNull).map(String::trim).toList());
    }

    public static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** 更新结果（供 T08 导入响应使用） */
    public static class UpdateResult {
        public Doc doc;
        public boolean changed;
        public int version;
        public AnnotationAccess.ReanchorCount reanchor;
    }
}
