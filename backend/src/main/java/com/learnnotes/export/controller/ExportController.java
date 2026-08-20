package com.learnnotes.export.controller;

import com.learnnotes.common.R;
import com.learnnotes.export.service.ExportService;
import com.learnnotes.export.service.InsightImportService;
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
 * 导出与回灌接口（§5.7）。
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
    public ResponseEntity<StreamingResponseBody> exportAll() {
        String filename = exportService.zipFileName();
        StreamingResponseBody body = outputStream -> exportService.writeZip(outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @PostMapping("/import/insights")
    public R<Map<String, Object>> importInsights(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) body.get("insights");
        return R.ok(insightImportService.importInsights(
                (String) body.get("categorySlug"),
                (String) body.get("topicSlug"),
                (String) body.get("docSlug"),
                insights));
    }
}
