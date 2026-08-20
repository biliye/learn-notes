package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录服务：BCrypt 校验 + JWT 签发 + 登录失败锁定（连续 5 次锁 10 分钟，P1，内存计数）。
 */
@Service
public class AuthService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_MILLIS = 10 * 60_000L;

    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** username → {failCount, lockUntil} */
    private final Map<String, LoginState> loginStates = new ConcurrentHashMap<>();

    public AuthService(SysUserMapper userMapper, JwtService jwtService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            throw BizException.badRequest("用户名或密码不能为空");
        }
        LoginState state = loginStates.computeIfAbsent(username, k -> new LoginState());
        synchronized (state) {
            if (state.lockUntil > System.currentTimeMillis()) {
                throw BizException.locked("账号已锁定，请 " + ((state.lockUntil - System.currentTimeMillis()) / 1000 / 60 + 1) + " 分钟后再试");
            }
            SysUser user = userMapper.findByUsername(username);
            if (user == null || !encoder.matches(password, user.getPasswordHash())) {
                state.failCount++;
                if (state.failCount >= MAX_FAIL) {
                    state.lockUntil = System.currentTimeMillis() + LOCK_MILLIS;
                    state.failCount = 0;
                    throw BizException.locked("连续登录失败 " + MAX_FAIL + " 次，账号已锁定 10 分钟");
                }
                throw BizException.unauthorized("用户名或密码错误");
            }
            state.failCount = 0;
            state.lockUntil = 0;
            String token = jwtService.issue(user.getUsername());
            return Map.of(
                    "token", token,
                    "expiresIn", jwtService.getExpireSeconds(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname());
        }
    }

    public Map<String, Object> me(String username) {
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return Map.of(
                "username", user.getUsername(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname());
    }

    static class LoginState {
        int failCount;
        long lockUntil;
    }
}
