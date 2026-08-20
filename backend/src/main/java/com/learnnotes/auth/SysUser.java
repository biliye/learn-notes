package com.learnnotes.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_user 实体（单用户，仅一行）。
 */
@Data
public class SysUser {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
