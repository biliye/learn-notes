package com.learnnotes.doc.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * doc 实体（当前版本正文内联，读路径单表命中）。
 */
@Data
public class Doc {

    private Long id;
    private Long topicId;
    private String slug;
    private String title;
    private String summary;
    private String tags;
    private String sourceFilename;
    private Integer currentVersion;
    private String contentMd;
    private String contentHash;
    private Integer blockCount;
    private Integer wordCount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
