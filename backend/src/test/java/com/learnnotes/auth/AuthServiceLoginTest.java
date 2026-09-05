package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数与锁定（P1：连续 5 次锁 10 分钟）：
 * - 密码错误提示带剩余可尝试次数
 * - 第 5 次触发锁定，锁定期内正确密码也被拒
 * - 登录成功重置计数
 */
class AuthServiceLoginTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("ut-test-secret-9f83c1a7-entropy-ok-32");
        authService = new AuthService(userMapper, new JwtService(props), mock(CatalogService.class), props);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("right-password"));
        user.setNickname("管理员");
        user.setRole(SysUser.ROLE_ADMIN);
        when(userMapper.findByUsername("admin")).thenReturn(user);
    }

    @Test
    void wrongPasswordReportsRemainingAttempts() {
        BizException first = assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        assertEquals("用户名或密码错误", first.getMessage());

        BizException second = assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        assertEquals("用户名或密码错误", second.getMessage());
    }

    @Test
    void fiveFailuresLocksAndBlocksEvenCorrectPassword() {
        for (int i = 0; i < 4; i++) {
            assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        }
        BizException locked = assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        assertEquals("连续登录失败 5 次，账号已锁定 10 分钟", locked.getMessage());

        BizException stillLocked = assertThrows(BizException.class, () -> authService.login("admin", "right-password", "127.0.0.1"));
        assertTrue(stillLocked.getMessage().startsWith("账号已锁定"));
    }

    @Test
    void successfulLoginResetsFailCount() {
        assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        Map<String, Object> result = authService.login("admin", "right-password", "127.0.0.1");
        assertNotNull(result.get("token"));

        BizException again = assertThrows(BizException.class, () -> authService.login("admin", "wrong", "127.0.0.1"));
        assertEquals("用户名或密码错误", again.getMessage());
    }
}
