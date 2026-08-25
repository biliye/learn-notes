package com.learnnotes.auth;

import com.learnnotes.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（D7）：HS256，sub=username，附加 uid/role claims，
 * exp=now+expireMinutes。V3 前的旧 token 无 uid/role，解析失败 → 401（升级后需重新登录）。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(AppProperties props) {
        String secret = props.getJwt().getSecret();
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
