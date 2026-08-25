package com.learnnotes.doc.controller;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.R;
import com.learnnotes.doc.dto.DocDetailDto;
import com.learnnotes.doc.dto.DocPageDto;
import com.learnnotes.doc.service.DocService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文档接口（§5.3）。数据按当前登录用户隔离，管理员可访问任意文档。
 */
@RestController
@RequestMapping("/api/docs")
public class DocController {

    private final DocService service;

    public DocController(DocService service) {
        this.service = service;
    }

    @GetMapping
    public R<DocPageDto> list(HttpServletRequest request,
                              @RequestParam(required = false) Long topicId,
                              @RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.list(CurrentUser.from(request), topicId, categoryId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public R<DocDetailDto> detail(HttpServletRequest request, @PathVariable Long id) {
        return R.ok(service.detail(id, CurrentUser.from(request)));
    }

    /**
     * 原始 Markdown 下载（R11）。
     * 统一响应体的唯一例外：直接返回 text/markdown 纯文本（规格 §5.3 约定）。
     */
    @GetMapping(value = "/{id}/raw", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String raw(HttpServletRequest request, @PathVariable Long id) {
        return service.raw(id, CurrentUser.from(request));
    }

    @PostMapping
    public R<Map<String, Object>> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        var doc = service.create(
                CurrentUser.from(request),
                asLong(body.get("topicId")),
                (String) body.get("title"),
                (String) body.get("slug"),
                (String) body.get("summary"),
                asStringList(body.get("tags")),
                (String) body.get("contentMd"),
                (String) body.get("sourceFilename"));
        return R.ok(Map.of("docId", doc.getId(), "version", doc.getCurrentVersion()));
    }

    @PutMapping("/{id}")
    public R<Map<String, Object>> update(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        DocService.UpdateResult result = service.update(
                CurrentUser.from(request),
                id,
                (String) body.get("title"),
                (String) body.get("summary"),
                asStringList(body.get("tags")),
                (String) body.get("contentMd"),
                (String) body.get("changeNote"),
                (String) body.get("sourceFilename"));
        return R.ok(Map.of("docId", id, "changed", result.changed, "version", result.version));
    }

    @PutMapping("/{id}/move")
    public R<Void> move(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.move(CurrentUser.from(request), id, asLong(body.get("topicId")));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        service.delete(CurrentUser.from(request), id);
        return R.ok();
    }

    @GetMapping("/{id}/versions")
    public R<List<Map<String, Object>>> versions(HttpServletRequest request, @PathVariable Long id) {
        return R.ok(service.versions(id, CurrentUser.from(request)));
    }

    @GetMapping("/{id}/versions/{version}")
    public R<Map<String, Object>> versionContent(HttpServletRequest request, @PathVariable Long id,
                                                 @PathVariable int version) {
        return R.ok(service.versionContent(id, version, CurrentUser.from(request)));
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : o == null ? null : Long.valueOf(o.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (o instanceof List<?> list) {
            return (List<String>) (List<?>) list;
        }
        return null;
    }
}
