package com.learnnotes.annotation.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 见解响应结构（§5.5，内联在文档详情 annotations 字段）。
 */
@Data
public class AnnotationDto {

    private Long id;
    private String anchor;
    private Integer anchorIndex;
    private String contentMd;
    private String status;
    private String blockSnippet;
    private Integer docVersionAtCreate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AnnotationDto from(com.learnnotes.annotation.entity.DocAnnotation ann) {
        AnnotationDto dto = new AnnotationDto();
        dto.setId(ann.getId());
        dto.setAnchor("b" + ann.getAnchorIndex() + "-" + ann.getAnchorHash());
        dto.setAnchorIndex(ann.getAnchorIndex());
        dto.setContentMd(ann.getContentMd());
        dto.setStatus(ann.getStatus());
        dto.setBlockSnippet(ann.getBlockSnippet());
        dto.setDocVersionAtCreate(ann.getDocVersionAtCreate());
        dto.setCreatedAt(ann.getCreatedAt());
        dto.setUpdatedAt(ann.getUpdatedAt());
        return dto;
    }
}
