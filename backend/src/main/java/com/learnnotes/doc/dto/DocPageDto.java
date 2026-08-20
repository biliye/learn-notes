package com.learnnotes.doc.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分页文档列表。
 */
@Data
public class DocPageDto {

    private long total;
    private int page;
    private int size;
    private List<DocListItem> items;
}
