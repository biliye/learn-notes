package com.learnnotes.imports.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩包导入响应（§5.4，编辑器草稿流）：解析后只返回草稿，**不入库**，
 * 由前端填入新建文档编辑器，用户核对后走 §5.3 POST /api/docs 手动保存。
 */
@Data
public class ZipImportResult {

    private String filename;
    private String title;
    private String slug;
    private String summary;
    private List<String> tags = new ArrayList<>();
    /** 剥离 front-matter、相对路径图片已重写为 /uploads/... 的正文 */
    private String contentMd;
    /** 成功上传并重写引用的图片引用数 */
    private int importedImages;
    /** 压缩包内未被正文引用的图片数（未导入） */
    private int skippedImages;
    private List<String> warnings = new ArrayList<>();
}
