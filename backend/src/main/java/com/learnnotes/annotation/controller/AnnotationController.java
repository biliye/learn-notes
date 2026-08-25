package com.learnnotes.annotation.controller;

import com.learnnotes.annotation.dto.AnnotationDto;
import com.learnnotes.annotation.service.AnnotationService;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人见解接口（§5.5）。数据按当前登录用户隔离。
 */
@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final AnnotationService service;

    public AnnotationController(AnnotationService service) {
        this.service = service;
    }

    @PostMapping
    public R<AnnotationDto> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return R.ok(service.create(
                CurrentUser.from(request),
                asLong(body.get("docId")),
                (String) body.get("anchor"),
                (String) body.get("contentMd")));
    }

    @PutMapping("/{id}")
    public R<AnnotationDto> update(HttpServletRequest request, @PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        return R.ok(service.update(CurrentUser.from(request), id, (String) body.get("contentMd")));
    }

    @PostMapping("/{id}/reanchor")
    public R<AnnotationDto> reanchor(HttpServletRequest request, @PathVariable Long id,
                                     @RequestBody Map<String, Object> body) {
        return R.ok(service.reanchorManual(CurrentUser.from(request), id, (String) body.get("anchor")));
    }

    @PostMapping("/{id}/confirm")
    public R<AnnotationDto> confirm(HttpServletRequest request, @PathVariable Long id) {
        return R.ok(service.confirm(CurrentUser.from(request), id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        service.delete(CurrentUser.from(request), id);
        return R.ok();
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : o == null ? null : Long.valueOf(o.toString());
    }
}
