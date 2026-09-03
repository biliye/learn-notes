package com.learnnotes.catalog.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * catalog_node 实体（D1：单表自引用，任意深度目录树，根 parent_id=0）。
 * node_level=1 为大类（顶层）；max_level 仅大类保存其子树允许的最大层级。
 */
@Data
public class CatalogNode {

    public static final int LEVEL_CATEGORY = 1;
    public static final int LEVEL_TOPIC = 2;

    /** 大类默认允许两层（现状 大类→小方向） */
    public static final int DEFAULT_MAX_LEVEL = 2;
    /** 大类可配置的层级上限 */
    public static final int MAX_LEVEL_LIMIT = 10;

    private Long id;
    private Long ownerId;
    private Long parentId;
    private Integer nodeLevel;
    /** 仅大类(parent_id=0)有效：子树允许的最大 node_level（含大类）；非大类为 null */
    private Integer maxLevel;
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
