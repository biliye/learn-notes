package com.learnnotes.admin;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员接口（V3）：跨用户查看文档与用户列表。仅 ADMIN 角色可访问。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/docs")
    public R<Map<String, Object>> docs(HttpServletRequest request,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.listDocs(CurrentUser.from(request), keyword, page, size));
    }

    @GetMapping("/users")
    public R<Map<String, Object>> users(HttpServletRequest request) {
        return R.ok(service.listUsers(CurrentUser.from(request)));
    }
}
