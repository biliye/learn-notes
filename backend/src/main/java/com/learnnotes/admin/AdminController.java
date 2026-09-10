package com.learnnotes.admin;

import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员接口（V3）：跨用户查看文档、用户列表，以及建号/改密/删号。仅 ADMIN 角色可访问。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final ImageMigrationService imageMigrationService;

    public AdminController(AdminService service, ImageMigrationService imageMigrationService) {
        this.service = service;
        this.imageMigrationService = imageMigrationService;
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

    /** 建号：body = {username, password, nickname?, role?}；返回新用户（不含 token，管理员不会自动登录成对方） */
    @PostMapping("/users")
    public R<Map<String, Object>> createUser(HttpServletRequest request, @RequestBody Map<String, String> body) {
        return R.ok(service.createUser(CurrentUser.from(request),
                body.get("username"), body.get("password"), body.get("nickname"), body.get("role")));
    }

    /** 重置普通用户口令：body = {newPassword}；无需旧密码 */
    @PostMapping("/users/{id}/password")
    public R<Map<String, Object>> resetPassword(HttpServletRequest request, @PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        return R.ok(service.resetUserPassword(CurrentUser.from(request), id, body.get("newPassword")));
    }

    /** 删除普通用户账号（连带其文档与分类，不可恢复） */
    @DeleteMapping("/users/{id}")
    public R<Map<String, Object>> deleteUser(HttpServletRequest request, @PathVariable Long id) {
        return R.ok(service.deleteUser(CurrentUser.from(request), id));
    }

    /**
     * 一次性维护任务：把老图片（无归属的 /uploads/YYYY/MM/…）复制进各用户目录并改写引用。
     * 先用 dryRun=true 看报告（不改动），确认后 dryRun=false 执行。幂等，可重跑。
     */
    @PostMapping("/maintenance/migrate-images")
    public R<Map<String, Object>> migrateImages(HttpServletRequest request,
                                               @RequestParam(defaultValue = "true") boolean dryRun) {
        service.requireAdmin(CurrentUser.from(request));
        return R.ok(imageMigrationService.migrate(dryRun));
    }
}
