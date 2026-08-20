package com.learnnotes.catalog.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录树节点（§5.2）：两级嵌套。
 */
@Data
public class CatalogNodeDto {

    private Long id;
    private Long parentId;
    private String name;
    private String slug;
    private String remark;
    private String icon;
    private Integer sortOrder;
    private Integer docCount;
    private Boolean autoCreated;
    private List<CatalogNodeDto> children = new ArrayList<>();
}
