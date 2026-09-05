package com.learnnotes.auth;

import com.learnnotes.common.BizException;
import com.learnnotes.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * JWT 签发与校验（D7）：HS256，sub=username，附加 uid/role claims，
 * exp=now+expireMinutes。V3 前的旧 token 无 uid/role，解析失败 → 401（升级后需重新登录）。
 */
@Component
public class JwtService {

    /** 已知的示例/占位 secret：长度足够但仍可通过 Keys 校验，直接拒绝启动 */
    private static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "change-me-to-a-long-random-string-at-least-32-chars",
            "changeme-changeme-changeme-changeme-1234",
            "your-secret-key-your-secret-key-your-secret",
            "please-change-this-weak-secret-to-random-32");
    private static final String CHANGE_ME_HINT = "change-me";

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret() == null ? "" : props.getJwt().getSecret();
        String trimmed = secret.trim();
        if (trimmed.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET 未配置或不足 32 字符，拒绝启动");
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (PLACEHOLDER_SECRETS.contains(lower)
                || lower.contains(CHANGE_ME_HINT) || lower.contains("changeme")
                || lower.chars().distinct().count() < 8) {
            throw new IllegalStateException("APP_JWT_SECRET 是占位符或熵过低（不同字符 < 8），拒绝启动；请换成随机长串");
        }
        this.key = Keys.hmacShaKeyFor(trimmed.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = props.getJwt().getExpireMinutes() * 60_000L;
    }

    public String issue(SysUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    public long getExpireSeconds() {
        return expireMillis / 1000L;
    }

    /**
     * 校验并解析；失败返回 null（调用方统一转 401）。
     */
    public CurrentUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Object uid = claims.get("uid");
            if (!(uid instanceof Number)) {
                return null;
            }
            String role = claims.get("role", String.class);
            return new CurrentUser(((Number) uid).longValue(), claims.getSubject(), role);
        } catch (Exception e) {
            return null;
        }
    }
}
