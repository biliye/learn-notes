package com.learnnotes.search;

import com.learnnotes.catalog.entity.CatalogNode;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.SearchUtil;
import com.learnnotes.doc.dto.DocSearchRow;
import com.learnnotes.doc.mapper.DocMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索（R9、D10）：MySQL LIKE 双字段匹配，返回 docId/title/breadcrumb/snippet，上限 50 条。
 * 不做分词、不做 ES、不做高亮 HTML（只给 ** 标记，前端自行处理）。
 */
@Service
public class SearchService {

    private static final int MAX_RESULT = 50;
    private static final int SNIPPET_RADIUS = 60;

    private final DocMapper docMapper;
    private final CatalogNodeMapper catalogMapper;

    public SearchService(DocMapper docMapper, CatalogNodeMapper catalogMapper) {
        this.docMapper = docMapper;
        this.catalogMapper = catalogMapper;
    }

    public List<Map<String, Object>> search(String q, Integer size) {
        if (q == null || q.length() < 1) {
            throw BizException.badRequest("q 不能为空");
        }
        int limit = size == null ? MAX_RESULT : Math.min(Math.max(size, 1), MAX_RESULT);
        String pattern = SearchUtil.escapeLike(q.trim());
        List<Map<String, Object>> results = new ArrayList<>();
        for (DocSearchRow row : docMapper.search(pattern, limit)) {
            Map<String, Object> m = new HashMap<>();
            m.put("docId", row.getId());
            m.put("title", row.getTitle());
            m.put("breadcrumb", breadcrumb(row.getTopicId()));
            m.put("snippet", makeSnippet(row.getContentMd(), row.getTitle(), q.trim()));
            results.add(m);
        }
        return results;
    }

    private List<Map<String, Object>> breadcrumb(Long topicId) {
        List<Map<String, Object>> breadcrumb = new ArrayList<>();
        CatalogNode topic = catalogMapper.selectById(topicId);
        if (topic == null) {
            return breadcrumb;
        }
        breadcrumb.add(nodeMap(topic));
        if (topic.getParentId() != null && topic.getParentId() != 0) {
            CatalogNode category = catalogMapper.selectById(topic.getParentId());
            if (category != null) {
                breadcrumb.add(0, nodeMap(category));
            }
        }
        return breadcrumb;
    }

    private Map<String, Object> nodeMap(CatalogNode node) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", node.getId());
        m.put("name", node.getName());
        m.put("slug", node.getSlug());
        return m;
    }

    /**
     * snippet：命中词前后各 60 字，命中词用 **…** 包裹；标题优先，其次正文。
     */
    String makeSnippet(String contentMd, String title, String q) {
        String titleHit = wrapHit(title, q);
        if (titleHit != null) {
            return titleHit;
        }
        if (contentMd == null) {
            return title == null ? "" : title;
        }
        String bodyHit = wrapHit(contentMd, q);
        return bodyHit != null ? bodyHit : title;
    }

    private String wrapHit(String text, String q) {
        if (text == null || q == null) {
            return null;
        }
        int idx = text.toLowerCase().indexOf(q.toLowerCase());
        if (idx < 0) {
            return null;
        }
        int start = Math.max(0, idx - SNIPPET_RADIUS);
        int end = Math.min(text.length(), idx + q.length() + SNIPPET_RADIUS);
        String pre = start > 0 ? "…" : "";
        String post = end < text.length() ? "…" : "";
        return pre + text.substring(start, idx)
                + "**" + text.substring(idx, idx + q.length()) + "**"
                + text.substring(idx + q.length(), end) + post;
    }
}


