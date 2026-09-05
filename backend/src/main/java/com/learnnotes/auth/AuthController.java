package com.learnnotes.auth;

import com.learnnotes.common.BizException;
import com.learnnotes.common.IpRateLimiter;
import com.learnnotes.common.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（§5.1）。无 logout 接口（前端清 token 即可，YAGNI）。
 * 登录/注册按 IP 限流（nginx 已透传 X-Real-IP / X-Forwarded-For）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int LOGIN_PER_MINUTE = 10;
    private static final int REGISTER_PER_MINUTE = 5;

    private final AuthService authService;
    private final IpRateLimiter rateLimiter;

    public AuthController(AuthService authService, IpRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String ip = clientIp(request);
        if (!rateLimiter.tryAcquire("login|" + ip, LOGIN_PER_MINUTE, 60_000L)) {
            throw BizException.locked("尝试过于频繁，请稍后再试");
        }
        return R.ok(authService.login(body.get("username"), body.get("password"), ip));
    }

    /** 注册（V3 起开放，APP_REGISTER_ENABLED 可关） */
    @PostMapping("/register")
    public R<Map<String, Object>> register(HttpServletRequest request, @RequestBody Map<String, String> body) {
        if (!rateLimiter.tryAcquire("register|" + clientIp(request), REGISTER_PER_MINUTE, 60_000L)) {
            throw BizException.locked("尝试过于频繁，请稍后再试");
        }
        return R.ok(authService.register(body.get("username"), body.get("password"), body.get("nickname")));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return R.ok(authService.me(username));
    }

    /** 优先取反代写入的 X-Real-IP / X-Forwarded-For 首段，否则回退 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
