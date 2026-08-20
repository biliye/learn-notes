package com.learnnotes.export.service;

import com.learnnotes.annotation.ReanchorService;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
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
    public Map<String, Object> importInsights(String categorySlug, String topicSlug, String docSlug,
                                              List<Map<String, Object>> insights) {
        Doc doc = locateDoc(categorySlug, topicSlug, docSlug);
        if (doc == null) {
            throw BizException.notFound("目标文档不存在，请先导入文档：" + categorySlug + "/" + topicSlug + "/" + docSlug);
        }
        List<Block> currentBlocks = MarkdownBlockParser.parse(doc.getContentMd()).getBlocks();
        int currentVersion = doc.getCurrentVersion();

        Map<String, Object> result = new LinkedHashMap<>();
        int created = 0, skipped = 0, stale = 0, orphan = 0;

        for (Map<String, Object> insight : insights) {
            String anchor = (String) insight.get("anchor");
            String contentMd = (String) insight.get("contentMd");
            if (anchor == null || contentMd == null) {
                continue;
            }
            int anchorIndex = anchorIndex(anchor, insight.get("anchorIndex"));
            String hash = anchorHash(anchor);
            Integer createdVersion = asInt(insight.get("docVersionAtCreate"), currentVersion);
            String snippet = (String) insight.get("blockSnippet");

            // 幂等：已存在完全相同的 (anchor, contentMd) 则跳过
            List<DocAnnotation> existing = annotationMapper.selectByDoc(doc.getId());
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
        }
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("stale", stale);
        result.put("orphan", orphan);
        return result;
    }

    private Doc locateDoc(String categorySlug, String topicSlug, String docSlug) {
        if (docSlug == null || topicSlug == null || categorySlug == null) {
            return null;
        }
        CatalogNode category = catalogMapper.selectByParentAndSlug(0, categorySlug);
        if (category == null) {
            return null;
        }
        CatalogNode topic = catalogMapper.selectByParentAndSlug(category.getId(), topicSlug);
        if (topic == null) {
            return null;
        }
        return docMapper.selectByTopicAndSlug(topic.getId(), docSlug);
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
