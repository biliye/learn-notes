package com.learnnotes.doc.dto;

import com.learnnotes.markdown.Block;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档详情（§5.3）：breadcrumb / blocks / annotations 同响应返回。
 */
@Data
public class DocDetailDto {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private List<String> tags = new ArrayList<>();
    private Integer currentVersion;
    private LocalDateTime updatedAt;
    private List<BreadcrumbItem> breadcrumb = new ArrayList<>();
    private List<Block> blocks = new ArrayList<>();
    /** 由 T09 填满（含 ORPHAN） */
    private List<Object> annotations = new ArrayList<>();

    @Data
    public static class BreadcrumbItem {
        private Long id;
        private String name;
        private String slug;
    }
}
