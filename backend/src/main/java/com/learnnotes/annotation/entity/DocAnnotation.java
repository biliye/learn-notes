package com.learnnotes.annotation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * doc_annotation 实体（块级锚点个人见解）。
 */
@Data
public class DocAnnotation {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_STALE = "STALE";
    public static final String STATUS_ORPHAN = "ORPHAN";

    private Long id;
    private Long docId;
    private String anchorHash;
    private Integer anchorIndex;
    private String blockSnippet;
    private String contentMd;
    private String status;
    private Integer docVersionAtCreate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
