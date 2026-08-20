package com.learnnotes.doc.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * doc_version 实体（历史版本，只追加）。
 */
@Data
public class DocVersion {

    private Long id;
    private Long docId;
    private Integer version;
    private String contentMd;
    private String contentHash;
    private String changeNote;
    private LocalDateTime createdAt;
}
