package com.learnnotes.catalog.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录树节点（§5.2）：任意深度嵌套。
 */
@Data
public class CatalogNodeDto {

    private Long id;
    private Long parentId;
    /** 相对所属大类的深度（大类=1） */
    private Integer nodeLevel;
    /** 仅大类(parent_id=0)有效：子树允许的最大层级 */
    private Integer maxLevel;
    private String name;
    private String slug;
    private String remark;
    private String icon;
    private Integer sortOrder;
    private Integer docCount;
    private Boolean autoCreated;
    private List<CatalogNodeDto> children = new ArrayList<>();
}
