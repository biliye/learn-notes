package com.learnnotes.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_user 实体（多用户：ADMIN/USER）。
 */
@Data
public class SysUser {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
