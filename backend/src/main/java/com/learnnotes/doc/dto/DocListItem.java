package com.learnnotes.doc.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档列表项（§5.3 GET /api/docs）：不含正文。
 */
@Data
public class DocListItem {

    private Long id;
    private Long topicId;
    private String title;
    private String slug;
    private String summary;
    private List<String> tags;
    private Integer currentVersion;
    private Integer wordCount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
