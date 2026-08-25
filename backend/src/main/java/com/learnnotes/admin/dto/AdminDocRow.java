package com.learnnotes.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理员跨用户文档列表行 */
@Data
public class AdminDocRow {

    private Long id;
    private String title;
    private Long topicId;
    private String topicName;
    private String categoryName;
    private Long ownerId;
    private String ownerUsername;
    private String ownerNickname;
    private Integer wordCount;
    private LocalDateTime updatedAt;
}
