package com.learnnotes.export.service;

import com.learnnotes.annotation.ReanchorService;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SlugUtil;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 见解回灌（R33、§5.7）：按 anchor 重建见解，未命中走 D6 重挂，重复 (anchor, contentMd) 跳过（幂等）。
 * V3 起只在当前用户自己的分类树内定位文档；V4 起按任意深度 slug 链（大类 → … → 叶目录）定位。
 */
@Service
public class InsightImportService {

    private final CatalogNodeMapper catalogMapper;
    private final DocMapper docMapper;
    private final DocAnnotationMapper annotationMapper;

    public InsightImportService(CatalogNodeMapper catalogMapper, DocMapper docMapper,
                                DocAnnotationMapper annotationMapper) {
        this.catalogMapper = catalogMapper;
        this.docMapper = docMapper;
        this.annotationMapper = annotationMapper;
    }

    @Transactional
    public Map<String, Object> importInsights(CurrentUser user, List<String> slugPath,
                                              String docSlug, List<Map<String, Object>> insights) {
        Doc doc = locateDoc(user, slugPath, docSlug);
        if (doc == null) {
            throw BizException.notFound("目标文档不存在，请先导入文档：" + String.join("/", slugPath) + "/" + docSlug);
        }
        List<Block> currentBlocks = MarkdownBlockParser.parse(doc.getContentMd()).getBlocks();
        int currentVersion = doc.getCurrentVersion();

        Map<String, Object> result = new LinkedHashMap<>();
        int created = 0, skipped = 0, stale = 0, orphan = 0;

        // 一次加载，循环内增量维护（原实现每条见解都全量查一遍，N+1）
        List<DocAnnotation> existing = annotationMapper.selectByDoc(doc.getId());

        if (insights == null) {
            insights = List.of();
        }
        for (Map<String, Object> insight : insights) {
            String anchor = (String) insight.get("anchor");
            String contentMd = (String) insight.get("contentMd");
            if (anchor == null || contentMd == null) {
                continue;
            }
            int anchorIndex = anchorIndex(anchor, insight.get("anchorIndex"));
            String rawHash = anchorHash(anchor);
            // anchor_hash 列是 CHAR(8)：畸形超长 hash 截断，空 hash 条目直接跳过
            if (rawHash == null || rawHash.isBlank()) {
                continue;
            }
            final String hash = rawHash.length() > 8 ? rawHash.substring(0, 8) : rawHash;
            Integer createdVersion = asInt(insight.get("docVersionAtCreate"), currentVersion);
            String snippet = (String) insight.get("blockSnippet");

            // 幂等：已存在完全相同的 (anchor, contentMd) 则跳过
            boolean dup = existing.stream().anyMatch(a ->
                    a.getAnchorHash().equals(hash) && a.getContentMd().equals(contentMd));
            if (dup) {
                skipped++;
                continue;
            }

            // 当前块列表匹配：唯一 hash → ACTIVE；多命中 → 最近；无命中 → D6 相似度
            ReanchorService.AnchorMatch match = ReanchorService.findMatchWithText(
                    hash, anchorIndex, snippet == null ? "" : snippet, currentBlocks);

            DocAnnotation ann = new DocAnnotation();
            ann.setDocId(doc.getId());
            ann.setContentMd(contentMd);
            ann.setDocVersionAtCreate(createdVersion);
            switch (match.getStatus()) {
                case ACTIVE -> {
                    ann.setAnchorHash(hashOf(match.getAnchor()));
                    ann.setAnchorIndex(match.getIndex());
                    ann.setBlockSnippet(snippetOf(currentBlocks.get(match.getIndex())));
                    ann.setStatus(DocAnnotation.STATUS_ACTIVE);
                    created++;
                }
                case STALE -> {
                    ann.setAnchorHash(hashOf(match.getAnchor()));
                    ann.setAnchorIndex(match.getIndex());
                    ann.setBlockSnippet(snippetOf(currentBlocks.get(match.getIndex())));
                    ann.setStatus(DocAnnotation.STATUS_STALE);
                    stale++;
                }
                default -> {
                    // ORPHAN：保留原 hash/index 与创建时快照
                    ann.setAnchorHash(hash);
                    ann.setAnchorIndex(anchorIndex);
                    ann.setBlockSnippet(snippet == null ? contentMd : snippet);
                    ann.setStatus(DocAnnotation.STATUS_ORPHAN);
                    orphan++;
                }
            }
            annotationMapper.insert(ann);
            existing.add(ann);
        }
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("stale", stale);
        result.put("orphan", orphan);
        return result;
    }

    /** 按 slug 链（大类 → … → 叶目录）+ 文档 slug 定位文档 */
    private Doc locateDoc(CurrentUser user, List<String> slugPath, String docSlug) {
        if (slugPath == null || slugPath.isEmpty() || docSlug == null) {
            return null;
        }
        long parentId = 0;
        CatalogNode current = null;
        for (String slug : slugPath) {
            if (slug == null || slug.isBlank()) {
                return null;
            }
            current = catalogMapper.selectByParentAndSlug(user.userId(), parentId, slug);
            if (current == null) {
                return null;
            }
            parentId = current.getId();
        }
        return current == null ? null : docMapper.selectByTopicAndSlug(user.userId(), current.getId(), docSlug);
    }

    private int anchorIndex(String anchor, Object fallback) {
        if (anchor.startsWith("b") && anchor.indexOf('-') > 1) {
            try {
                return Integer.parseInt(anchor.substring(1, anchor.indexOf('-')));
            } catch (NumberFormatException ignore) {
                // fallthrough
            }
        }
        return fallback instanceof Number n ? n.intValue() : 0;
    }

    private String anchorHash(String anchor) {
        int dash = anchor.indexOf('-');
        return dash >= 0 ? anchor.substring(dash + 1) : anchor;
    }

    private String hashOf(String anchor) {
        return anchor.substring(anchor.indexOf('-') + 1);
    }

    private String snippetOf(Block block) {
        String norm = com.learnnotes.markdown.AnchorUtil.normalize(
                block.getRaw(), "code".equals(block.getType()));
        return norm.length() <= 300 ? norm : norm.substring(0, 300);
    }

    private Integer asInt(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }
}
