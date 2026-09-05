package com.learnnotes.search;

import com.learnnotes.auth.CurrentUser;
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
 * 不做分词、不做 ES、不做高亮 HTML（只给 ** 标记，前端自行处理）。V3 起只搜当前用户自己的文档。
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

    public List<Map<String, Object>> search(CurrentUser user, String q, Integer size) {
        if (q == null || q.length() < 1) {
            throw BizException.badRequest("q 不能为空");
        }
        int limit = size == null ? MAX_RESULT : Math.min(Math.max(size, 1), MAX_RESULT);
        String pattern = SearchUtil.escapeLike(q.trim());
        List<Map<String, Object>> results = new ArrayList<>();
        for (DocSearchRow row : docMapper.search(user.userId(), pattern, limit)) {
            Map<String, Object> m = new HashMap<>();
            m.put("docId", row.getId());
            m.put("title", row.getTitle());
            m.put("breadcrumb", breadcrumb(row.getTopicId()));
            m.put("snippet", makeSnippet(row.getContentMd(), row.getTitle(), q.trim()));
            results.add(m);
        }
        return results;
    }

    /** 完整目录链：根→…→本目录（任意深度） */
    private List<Map<String, Object>> breadcrumb(Long topicId) {
        List<Map<String, Object>> breadcrumb = new ArrayList<>();
        List<CatalogNode> chain = new ArrayList<>();
        CatalogNode cur = topicId == null ? null : catalogMapper.selectById(topicId);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        while (cur != null && seen.add(cur.getId())) {
            chain.add(0, cur);
            if (cur.getParentId() == null || cur.getParentId() == 0) {
                break;
            }
            cur = catalogMapper.selectById(cur.getParentId());
        }
        for (CatalogNode n : chain) {
            breadcrumb.add(nodeMap(n));
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
        if (text == null || q == null || q.isEmpty()) {
            return null;
        }
        // toLowerCase 可能改变字符串长度（ﬁ→fi、İ 等），lowercase 上的 idx 不能直接映射回原文，
        // 只用它定位大致位置，再用大小写不敏感匹配在原文中精确定位，避免 substring 越界
        int approx = text.toLowerCase(java.util.Locale.ROOT).indexOf(q.toLowerCase(java.util.Locale.ROOT));
        if (approx < 0) {
            return null;
        }
        int from = Math.max(0, approx - 8);
        int hit = -1;
        for (int i = from; i + q.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, q, 0, q.length())) {
                hit = i;
                break;
            }
        }
        if (hit < 0) {
            return null;
        }
        int start = Math.max(0, hit - SNIPPET_RADIUS);
        int end = Math.min(text.length(), hit + q.length() + SNIPPET_RADIUS);
        String pre = start > 0 ? "…" : "";
        String post = end < text.length() ? "…" : "";
        return pre + text.substring(start, hit)
                + "**" + text.substring(hit, hit + q.length()) + "**"
                + text.substring(hit + q.length(), end) + post;
    }
}


