package com.learnnotes.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理员用户列表行（含文档数） */
@Data
public class AdminUserRow {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Long docCount;
    private LocalDateTime createdAt;
}
