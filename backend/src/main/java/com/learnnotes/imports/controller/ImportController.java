package com.learnnotes.imports.controller;

import com.learnnotes.common.BizException;
import com.learnnotes.common.R;
import com.learnnotes.imports.dto.ImportResult;
import com.learnnotes.imports.service.ImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 导入接口（§5.4，agent 主入口）。鉴权见 AuthInterceptor：/api/import/** 接受 X-Api-Token。
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024; // 2MB
    private static final int MAX_FILES = 20;

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/doc")
    public R<ImportResult> importDoc(@RequestBody Map<String, Object> body) {
        return R.ok(importService.importDoc(
                (String) body.get("filename"),
                (String) body.get("content"),
                (String) body.get("categoryHint"),
                (String) body.get("topicHint"),
                (String) body.get("onConflict")));
    }

    @PostMapping("/upload")
    public R<List<ImportResult>> importUpload(@RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(required = false) String categoryHint,
                                              @RequestParam(required = false) String topicHint) {
        if (files == null || files.isEmpty()) {
            throw BizException.badRequest("files 不能为空");
        }
        if (files.size() > MAX_FILES) {
            throw BizException.badRequest("单次最多上传 " + MAX_FILES + " 个文件");
        }
        List<String> filenames = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
            String lower = name.toLowerCase();
            if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
                throw BizException.badRequest("只接受 .md / .markdown 文件：" + name);
            }
            if (f.getSize() > MAX_FILE_BYTES) {
                throw BizException.badRequest("单文件不能超过 2MB：" + name);
            }
            try {
                contents.add(new String(f.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw BizException.badRequest("读取文件失败：" + name);
            }
            filenames.add(name);
        }
        return R.ok(importService.importUpload(filenames, contents, categoryHint, topicHint));
    }
}
