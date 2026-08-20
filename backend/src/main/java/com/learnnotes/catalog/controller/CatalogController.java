package com.learnnotes.catalog.controller;

import com.learnnotes.catalog.dto.CatalogNodeDto;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分类目录树接口（§5.2）。
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    public R<List<CatalogNodeDto>> tree() {
        return R.ok(service.tree());
    }

    @PostMapping
    public R<CatalogNodeDto> create(@RequestBody Map<String, Object> body) {
        return R.ok(service.create(
                asLong(body.get("parentId")),
                (String) body.get("name"),
                (String) body.get("slug"),
                (String) body.get("remark"),
                (String) body.get("icon"),
                asInteger(body.get("sortOrder"))));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.update(id,
                (String) body.get("name"),
                (String) body.get("remark"),
                (String) body.get("icon"),
                asInteger(body.get("sortOrder")));
        return R.ok();
    }

    @PutMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        service.move(id, asLong(body.get("parentId")), asInteger(body.get("sortOrder")));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof Number n ? n.longValue() : Long.valueOf(o.toString());
    }

    private static Integer asInteger(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof Number n ? n.intValue() : Integer.valueOf(o.toString());
    }
}
