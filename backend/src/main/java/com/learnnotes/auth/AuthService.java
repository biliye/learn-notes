package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 登录/注册服务：BCrypt 校验 + JWT 签发 + 登录失败锁定（连续 5 次锁 10 分钟，P1，内存计数）。
 * 安全：锁定按「用户名+IP」记，攻击者无法仅凭用户名把站主锁在门外；不存在的用户名不记状态（防内存涨爆）；
 * 状态表带过期清扫。
 * 注册：V3 起开放，创建 USER 角色账号并建默认 INBOX 分类树。
 */
@Service
public class AuthService {

    private static final int MAX_FAIL = 5;
    private static final long LOCK_MILLIS = 10 * 60_000L;
    /** loginStates 超过该容量时触发一次过期清扫（防随机用户名刷内存） */
    private static final int STATE_SWEEP_THRESHOLD = 10_000;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{3,32}");

    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final CatalogService catalogService;
    private final AppProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 「用户名|IP」→ {failCount, lockUntil} */
    private final Map<String, LoginState> loginStates = new ConcurrentHashMap<>();

    public AuthService(SysUserMapper userMapper, JwtService jwtService,
                       CatalogService catalogService, AppProperties props) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.catalogService = catalogService;
        this.props = props;
    }

    public Map<String, Object> login(String username, String password, String ip) {
        if (username == null || password == null) {
            throw BizException.badRequest("用户名或密码不能为空");
        }
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            // 用户不存在不记锁定状态：随机用户名刷不涨内存，也不暴露用户名是否存在
            throw BizException.unauthorized("用户名或密码错误");
        }
        String key = username.toLowerCase(Locale.ROOT) + "|" + (ip == null ? "-" : ip);
        if (loginStates.size() > STATE_SWEEP_THRESHOLD) {
            sweepExpiredStates();
        }
        LoginState state = loginStates.computeIfAbsent(key, k -> new LoginState());
        synchronized (state) {
            if (state.lockUntil > System.currentTimeMillis()) {
                throw BizException.locked("账号已锁定，请 " + ((state.lockUntil - System.currentTimeMillis()) / 1000 / 60 + 1) + " 分钟后再试");
            }
            if (!encoder.matches(password, user.getPasswordHash())) {
                state.failCount++;
                if (state.failCount >= MAX_FAIL) {
                    state.lockUntil = System.currentTimeMillis() + LOCK_MILLIS;
                    state.failCount = 0;
                    throw BizException.locked("连续登录失败 " + MAX_FAIL + " 次，账号已锁定 10 分钟");
                }
                throw BizException.unauthorized("用户名或密码错误，还可尝试 " + (MAX_FAIL - state.failCount) + " 次");
            }
            state.failCount = 0;
            state.lockUntil = 0;
            loginStates.remove(key);
            return userInfo(user);
        }
    }

    /** 清掉已过期且无失败计数的锁定状态，防 map 无限增长 */
    private void sweepExpiredStates() {
        long now = System.currentTimeMillis();
        loginStates.entrySet().removeIf(e -> {
            LoginState s = e.getValue();
            synchronized (s) {
                return s.lockUntil <= now && s.failCount == 0;
            }
        });
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String nickname) {
        if (!props.getRegister().isEnabled()) {
            throw BizException.forbidden("当前未开放注册");
        }
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw BizException.badRequest("用户名需为 3~32 位字母/数字/下划线/连字符");
        }
        if (password == null || password.length() < 6) {
            throw BizException.badRequest("密码至少 6 位");
        }
        if (nickname != null && nickname.trim().length() > 32) {
            throw BizException.badRequest("昵称最长 32 字");
        }
        if (userMapper.findByUsername(username) != null) {
            throw BizException.conflict("用户名已被注册");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname.trim());
        user.setRole(SysUser.ROLE_USER);
        userMapper.insert(user);
        catalogService.ensureDefaults(user.getId());
        return userInfo(user);
    }

    public Map<String, Object> me(String username) {
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return userInfo(user);
    }

    private Map<String, Object> userInfo(SysUser user) {
        String token = jwtService.issue(user);
        return Map.of(
                "token", token,
                "expiresIn", jwtService.getExpireSeconds(),
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname(),
                "role", user.getRole());
    }

    static class LoginState {
        int failCount;
        long lockUntil;
    }
}
