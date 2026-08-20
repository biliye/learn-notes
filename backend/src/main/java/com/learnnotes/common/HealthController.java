package com.learnnotes.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查（免鉴权白名单，见 T04）。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${app.version:0.1.0}")
    private String version;

    @GetMapping
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "UP", "version", version));
    }
}
