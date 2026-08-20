package com.learnnotes.search;

import com.learnnotes.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 搜索接口（§5.3）。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public R<List<Map<String, Object>>> search(@RequestParam String q,
                                               @RequestParam(required = false) Integer size) {
        return R.ok(service.search(q, size));
    }
}
