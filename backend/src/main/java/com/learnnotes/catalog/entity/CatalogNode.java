package com.learnnotes.catalog.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * catalog_node 实体（D1：单表自引用，固定两级，根 parent_id=0）。
 */
@Data
public class CatalogNode {

    public static final int LEVEL_CATEGORY = 1;
    public static final int LEVEL_TOPIC = 2;

    private Long id;
    private Long ownerId;
    private Long parentId;
    private Integer nodeLevel;
    private String name;
    private String slug;
    private String remark;
    private String icon;
    private Integer sortOrder;
    private Boolean autoCreated;
    private Integer docCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
