package com.learnnotes.export.controller;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.R;
import com.learnnotes.export.service.ExportService;
import com.learnnotes.export.service.InsightImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 导出与回灌接口（§5.7）。导出按当前用户隔离：管理员全量、普通用户仅自己的数据。
 */
@RestController
@RequestMapping("/api")
public class ExportController {

    private final ExportService exportService;
    private final InsightImportService insightImportService;

    public ExportController(ExportService exportService, InsightImportService insightImportService) {
        this.exportService = exportService;
        this.insightImportService = insightImportService;
    }

    @GetMapping(value = "/export/all", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> exportAll(HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        String filename = exportService.zipFileName();
        StreamingResponseBody body = outputStream -> exportService.writeZip(outputStream, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @PostMapping("/import/insights")
    public R<Map<String, Object>> importInsights(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) body.get("insights");
        return R.ok(insightImportService.importInsights(
                CurrentUser.from(request),
                slugPath(body),
                (String) body.get("docSlug"),
                insights));
    }

    /** slugPath（数组：大类 → … → 叶目录）优先；旧 categorySlug/topicSlug 两参兼容 */
    @SuppressWarnings("unchecked")
    private static List<String> slugPath(Map<String, Object> body) {
        Object p = body.get("slugPath");
        if (p instanceof List<?> list && !list.isEmpty()) {
            return (List<String>) (List<?>) list;
        }
        List<String> legacy = new java.util.ArrayList<>();
        String category = (String) body.get("categorySlug");
        String topic = (String) body.get("topicSlug");
        if (category != null && !category.isBlank()) {
            legacy.add(category);
        }
        if (topic != null && !topic.isBlank()) {
            legacy.add(topic);
        }
        return legacy;
    }
}
