package com.learnnotes.doc.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列表查询的数据库行（tags 为原始逗号分隔字符串，由 service 转换为 List）。
 */
@Data
public class DocListRow {

    private Long id;
    private Long topicId;
    private String slug;
    private String title;
    private String summary;
    private String tags;
    private String sourceFilename;
    private Integer currentVersion;
    private Integer blockCount;
    private Integer wordCount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
