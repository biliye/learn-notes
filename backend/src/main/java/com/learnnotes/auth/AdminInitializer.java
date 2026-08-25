package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.catalog.service.CatalogService;
import com.learnnotes.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始管理员创建（R24）：sys_user 为空时按环境变量创建；已存在则不动、不覆盖密码。
 * 密码只从环境读，不得出现明文默认值。V3 起角色为 ADMIN 并补建其默认 INBOX 树。
 */
@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final CatalogService catalogService;
    private final AppProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminInitializer(SysUserMapper userMapper, CatalogService catalogService, AppProperties props) {
        this.userMapper = userMapper;
        this.catalogService = catalogService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.countAll() > 0) {
            ensureAdminDefaults();
            return;
        }
        String username = props.getAdmin().getUsername();
        String password = props.getAdmin().getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD 未配置，跳过初始管理员创建");
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(username.trim());
        user.setRole(SysUser.ROLE_ADMIN);
        userMapper.insert(user);
        catalogService.ensureDefaults(user.getId());
        log.info("已创建初始管理员账号：{}", username);
    }

    /**
     * 存量部署升级后：首个账号即管理员，但其默认 INBOX 可能缺失
     * （V3 迁移已回填 owner，兜底路径由运行时幂等补齐）。
     */
    private void ensureAdminDefaults() {
        SysUser admin = userMapper.findFirstAdmin();
        if (admin != null) {
            catalogService.ensureDefaults(admin.getId());
        }
    }
}
