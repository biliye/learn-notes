package com.learnnotes.doc.dto;

import lombok.Data;

/**
 * 搜索查询行（含正文用于生成 snippet）。
 */
@Data
public class DocSearchRow {

    private Long id;
    private String title;
    private Long topicId;
    private String contentMd;
}
