package com.learnnotes.annotation.controller;

import com.learnnotes.annotation.dto.AnnotationDto;
import com.learnnotes.annotation.service.AnnotationService;
import com.learnnotes.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人见解接口（§5.5）。
 */
@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final AnnotationService service;

    public AnnotationController(AnnotationService service) {
        this.service = service;
    }

    @PostMapping
    public R<AnnotationDto> create(@RequestBody Map<String, Object> body) {
        return R.ok(service.create(
                asLong(body.get("docId")),
                (String) body.get("anchor"),
                (String) body.get("contentMd")));
    }

    @PutMapping("/{id}")
    public R<AnnotationDto> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(service.update(id, (String) body.get("contentMd")));
    }

    @PostMapping("/{id}/reanchor")
    public R<AnnotationDto> reanchor(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(service.reanchorManual(id, (String) body.get("anchor")));
    }

    @PostMapping("/{id}/confirm")
    public R<AnnotationDto> confirm(@PathVariable Long id) {
        return R.ok(service.confirm(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : o == null ? null : Long.valueOf(o.toString());
    }
}
