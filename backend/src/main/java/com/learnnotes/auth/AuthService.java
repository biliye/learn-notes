package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.common.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 登录/建号服务：BCrypt 校验 + JWT 签发 + 登录失败锁定（连续 5 次锁 10 分钟，P1，内存计数）。
 * 安全：锁定按「用户名+IP」记，攻击者无法仅凭用户名把站主锁在门外；不存在的用户名不记状态（防内存涨爆）；
 * 状态表带过期清扫。
 * 建号：自助注册已关闭，账号只能由管理员经 {@code POST /api/admin/users} 创建，建号时同步建默认 INBOX 分类树。
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
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 「用户名|IP」→ {failCount, lockUntil} */
    private final Map<String, LoginState> loginStates = new ConcurrentHashMap<>();

    public AuthService(SysUserMapper userMapper, JwtService jwtService,
                       CatalogService catalogService) {
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.catalogService = catalogService;
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
                // 统一话术，不提示剩余次数（避免向枚举者泄露账号存在性）
                throw BizException.unauthorized("用户名或密码错误");
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

    /**
     * 管理员建号（唯一建号入口，自助注册已关闭）。
     * 密码下限 8 位与 {@link #changePassword} 一致；role 缺省 USER，可为 ADMIN（留一个备用管理员）。
     */
    @Transactional
    public Map<String, Object> createUser(String username, String password, String nickname, String role) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw BizException.badRequest("用户名需为 3~32 位字母/数字/下划线/连字符");
        }
        if (password == null || password.length() < 8) {
            throw BizException.badRequest("密码至少 8 位");
        }
        if (password.length() > 128) {
            throw BizException.badRequest("密码最长 128 位");
        }
        if (nickname != null && nickname.trim().length() > 32) {
            throw BizException.badRequest("昵称最长 32 字");
        }
        String normalizedRole = (role == null || role.isBlank())
                ? SysUser.ROLE_USER : role.trim().toUpperCase(Locale.ROOT);
        if (!SysUser.ROLE_USER.equals(normalizedRole) && !SysUser.ROLE_ADMIN.equals(normalizedRole)) {
            throw BizException.badRequest("角色只能是 USER 或 ADMIN");
        }
        if (userMapper.findByUsername(username) != null) {
            throw BizException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname.trim());
        user.setRole(normalizedRole);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发同名：唯一索引兜底（uk_user_username），别把 500 抛给管理员
            throw BizException.conflict("用户名已存在");
        }
        catalogService.ensureDefaults(user.getId());
        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole());
    }

    public Map<String, Object> me(String username) {
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return userInfo(user);
    }

    /**
     * 修改密码：校验旧密码，新密码 ≥ 8 位。
     * 注意：已签发的 JWT 在剩余有效期内仍然可用（无吊销机制），改密后建议前端清 token 重新登录。
     */
    public Map<String, Object> changePassword(String username, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw BizException.badRequest("旧密码与新密码不能为空");
        }
        validateNewPassword(newPassword);
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        if (!encoder.matches(oldPassword, user.getPasswordHash())) {
            throw BizException.badRequest("旧密码错误");
        }
        userMapper.updatePasswordHash(user.getId(), encoder.encode(newPassword));
        return userInfo(user);
    }

    /**
     * 管理员重置他人口令：不需要旧密码（账号锁死/忘记密码时的兜底）。
     * 只改口令，**不吊销该账号已签发的 JWT**——对方手上的旧 token 在剩余有效期内仍可用，
     * 要立刻踢下线目前只能靠停用/删除账号（无吊销机制，见 changePassword 注释）。
     */
    public Map<String, Object> resetPassword(SysUser target, String newPassword) {
        validateNewPassword(newPassword);
        userMapper.updatePasswordHash(target.getId(), encoder.encode(newPassword));
        return Map.of(
                "userId", target.getId(),
                "username", target.getUsername(),
                "nickname", target.getNickname() == null ? target.getUsername() : target.getNickname());
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw BizException.badRequest("新密码不能为空");
        }
        if (newPassword.length() < 8) {
            throw BizException.badRequest("新密码至少 8 位");
        }
        if (newPassword.length() > 128) {
            throw BizException.badRequest("新密码最长 128 位");
        }
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
