package com.learnnotes.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnnotes.common.ErrorCode;
import com.learnnotes.common.R;
import com.learnnotes.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * 鉴权拦截器（D7）：拦 /api/**。
 * - 白名单：POST /api/auth/login、GET /api/health
 * - 其余要求 Authorization: Bearer &lt;jwt&gt;
 * - /api/import/** 额外接受 X-Api-Token（常量时间比较，供 agent 脚本免登录调用，R16）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final AppProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtService jwtService, AppProperties props) {
        this.jwtService = jwtService;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (isWhitelisted(method, uri)) {
            return true;
        }

        // X-Api-Token 通道：导入接口 + 导出接口（备份脚本用），见 R16 / §5.7
        if (uri.startsWith("/api/import/") || "/api/export/all".equals(uri)) {
            String token = request.getHeader("X-Api-Token");
            if (props.getApiToken() != null && !props.getApiToken().isEmpty()
                    && constantTimeEquals(props.getApiToken(), token)) {
                request.setAttribute("username", "api-token");
                return true;
            }
        }

        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String username = jwtService.parseUsername(auth.substring(7));
            if (username != null) {
                request.setAttribute("username", username);
                return true;
            }
        }

        writeUnauthorized(response);
        return false;
    }

    private boolean isWhitelisted(String method, String uri) {
        if ("POST".equals(method) && "/api/auth/login".equals(uri)) {
            return true;
        }
        if ("GET".equals(method) && "/api/health".equals(uri)) {
            return true;
        }
        return false;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                R.fail(ErrorCode.UNAUTHORIZED, "未登录或 token 失效")));
    }
}
