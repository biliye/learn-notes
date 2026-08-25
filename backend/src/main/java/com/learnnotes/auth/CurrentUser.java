package com.learnnotes.auth;

import com.learnnotes.common.BizException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前登录用户（拦截器解析后写入 request attribute，控制器取用）。
 */
public record CurrentUser(Long userId, String username, String role) {

    public static final String ATTR = "currentUser";

    public boolean isAdmin() {
        return SysUser.ROLE_ADMIN.equals(role);
    }

    public static CurrentUser from(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR);
        if (value instanceof CurrentUser user) {
            return user;
        }
        throw BizException.unauthorized("未登录或 token 失效");
    }
}
