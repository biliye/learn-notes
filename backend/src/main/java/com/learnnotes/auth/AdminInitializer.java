package com.learnnotes.auth;

import com.learnnotes.auth.mapper.SysUserMapper;
import com.learnnotes.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 初始管理员创建（R24）：sys_user 为空时按环境变量创建；已存在则不动、不覆盖密码。
 * 密码只从环境读，不得出现明文默认值。
 */
@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final AppProperties props;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminInitializer(SysUserMapper userMapper, AppProperties props) {
        this.userMapper = userMapper;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.countAll() > 0) {
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
        userMapper.insert(user);
        log.info("已创建初始管理员账号：{}", username);
    }
}
