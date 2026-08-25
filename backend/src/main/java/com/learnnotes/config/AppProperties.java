package com.learnnotes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.* 配置（来自环境变量，见 .env.example）。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Register register = new Register();
    /** agent 导入专用 API Token（R16） */
    private String apiToken;
    /** 导入原文落盘根目录（R17） */
    private String storageDir;
    /** 图片上传目录（D11） */
    private String uploadDir;
    /** 单图大小上限（MB） */
    private double maxImageMb = 5;

    @Data
    public static class Jwt {
        /** HS256 密钥，必填，缺失启动失败 */
        private String secret;
        private long expireMinutes = 720;
    }

    @Data
    public static class Admin {
        private String username;
        private String password;
    }

    @Data
    public static class Register {
        /** 是否开放注册（默认开；私密部署可关） */
        private boolean enabled = true;
    }
}
