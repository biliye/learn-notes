package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import com.learnnotes.common.ErrorCode;
import com.learnnotes.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员建号（自助注册已关闭，createUser 是唯一建号路径）：
 * 参数校验矩阵、用户名唯一性（含并发同名走唯一索引）、角色缺省与非法值、默认 INBOX 分类树。
 */
class AuthServiceCreateUserTest {

    private SysUserMapper userMapper;
    private CatalogService catalogService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        catalogService = mock(CatalogService.class);
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("ut-test-secret-9f83c1a7-entropy-ok-32");
        authService = new AuthService(userMapper, new JwtService(props), catalogService);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysUser.class).setId(7L);
            return 1;
        });
    }

    @Test
    void createsUserWithEncodedPasswordDefaultRoleAndInbox() {
        Map<String, Object> created = authService.createUser("newbie", "password123", "新同学", null);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("newbie", saved.getUsername());
        assertEquals("新同学", saved.getNickname());
        assertEquals(SysUser.ROLE_USER, saved.getRole());
        assertNotEquals("password123", saved.getPasswordHash(), "密码必须哈希落库");
        assertEquals(true, new BCryptPasswordEncoder().matches("password123", saved.getPasswordHash()));

        verify(catalogService).ensureDefaults(7L);
        assertEquals(7L, created.get("userId"));
        assertEquals("newbie", created.get("username"));
        assertEquals("USER", created.get("role"));
    }

    @Test
    void blankNicknameFallsBackToUsername() {
        authService.createUser("solo", "password123", "  ", null);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("solo", captor.getValue().getNickname());
    }

    @Test
    void adminRoleAccepted() {
        Map<String, Object> created = authService.createUser("backup-admin", "password123", null, "admin");

        assertEquals("ADMIN", created.get("role"));
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        assertEquals(SysUser.ROLE_ADMIN, captor.getValue().getRole());
    }

    @Test
    void rejectsInvalidInputs() {
        assertBadRequest(() -> authService.createUser("ab", "password123", null, null), "用户名需为 3~32 位字母/数字/下划线/连字符");
        assertBadRequest(() -> authService.createUser("has space", "password123", null, null), "用户名需为 3~32 位字母/数字/下划线/连字符");
        assertBadRequest(() -> authService.createUser("okname", "short12", null, null), "密码至少 8 位");
        assertBadRequest(() -> authService.createUser("okname", "p".repeat(129), null, null), "密码最长 128 位");
        assertBadRequest(() -> authService.createUser("okname", "password123", "x".repeat(33), null), "昵称最长 32 字");
        assertBadRequest(() -> authService.createUser("okname", "password123", null, "SUPER"), "角色只能是 USER 或 ADMIN");
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void rejectsDuplicateUsernameWithConflict() {
        SysUser existing = new SysUser();
        existing.setUsername("taken");
        when(userMapper.findByUsername("taken")).thenReturn(existing);

        BizException e = assertThrows(BizException.class,
                () -> authService.createUser("taken", "password123", null, null));
        assertEquals(ErrorCode.CONFLICT, e.getCode());
        assertEquals(409, e.getHttpStatus());
        assertEquals("用户名已存在", e.getMessage());
        verify(catalogService, never()).ensureDefaults(any());
    }

    /** 预检查与插入之间的并发窗口由唯一索引兜底，不能漏成 500 */
    @Test
    void duplicateKeyOnInsertAlsoMapsToConflict() {
        when(userMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("uk_user_username"));

        BizException e = assertThrows(BizException.class,
                () -> authService.createUser("race", "password123", null, null));
        assertEquals(ErrorCode.CONFLICT, e.getCode());
    }

    private void assertBadRequest(Runnable call, String expectedMessage) {
        BizException e = assertThrows(BizException.class, call::run);
        assertEquals(ErrorCode.BAD_REQUEST, e.getCode());
        assertEquals(expectedMessage, e.getMessage());
    }
}
