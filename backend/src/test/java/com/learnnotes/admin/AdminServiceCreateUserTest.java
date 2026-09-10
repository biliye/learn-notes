package com.learnnotes.admin;

import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.AuthService;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.auth.SysUser;
import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.mapper.CatalogNodeMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.common.ErrorCode;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.doc.mapper.DocVersionMapper;
import com.learnnotes.uploads.service.UploadCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 建号接口的权限闸门：只有 ADMIN 能建号（普通用户即使拿到 token 也必须被拒）。
 */
class AdminServiceCreateUserTest {

    private AuthService authService;
    private AdminService adminService;

    private static final CurrentUser ADMIN = new CurrentUser(1L, "biliye", SysUser.ROLE_ADMIN);
    private static final CurrentUser MEMBER = new CurrentUser(2L, "newbie", SysUser.ROLE_USER);

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        adminService = new AdminService(mock(DocMapper.class), mock(SysUserMapper.class),
                mock(CatalogNodeMapper.class), authService, mock(DocVersionMapper.class),
                mock(DocAnnotationMapper.class), mock(UploadCleanupService.class));
        when(authService.createUser(any(), any(), any(), any()))
                .thenReturn(Map.of("userId", 9L, "username", "newbie", "nickname", "新同学", "role", "USER"));
    }

    @Test
    void adminCanCreate() {
        Map<String, Object> created = adminService.createUser(ADMIN, "newbie", "password123", "新同学", null);

        assertEquals(9L, created.get("userId"));
        verify(authService).createUser("newbie", "password123", "新同学", null);
    }

    @Test
    void normalUserIsRejected() {
        BizException e = assertThrows(BizException.class,
                () -> adminService.createUser(MEMBER, "newbie", "password123", null, null));

        assertEquals(ErrorCode.FORBIDDEN, e.getCode());
        assertEquals(403, e.getHttpStatus());
        assertEquals("仅管理员可访问", e.getMessage());
        verify(authService, never()).createUser(any(), any(), any(), any());
    }
}
