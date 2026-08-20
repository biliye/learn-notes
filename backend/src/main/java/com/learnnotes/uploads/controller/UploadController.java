package com.learnnotes.uploads.controller;

import com.learnnotes.common.R;
import com.learnnotes.uploads.UploadResult;
import com.learnnotes.uploads.service.ImageStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口（§5.6、D11）。
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final ImageStorageService storageService;

    public UploadController(ImageStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/image")
    public R<UploadResult> uploadImage(@RequestParam("file") MultipartFile file) {
        return R.ok(storageService.save(file));
    }
}
