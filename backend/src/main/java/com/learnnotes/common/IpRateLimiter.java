package com.learnnotes.common;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 极简固定窗口 IP 限流（内存版）：登录/注册等开放接口防刷。
 * 单实例足够；多实例部署需换 Redis 等集中存储。
 */
@Component
public class IpRateLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 取一次配额；窗口内超过 limit 返回 false。
     */
    public boolean tryAcquire(String key, int limit, long windowMillis) {
        if (key == null || key.isBlank()) {
            return true;
        }
        if (windows.size() > SWEEP_THRESHOLD) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(e -> e.getValue().windowEnd <= now);
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) ->
                old == null || old.windowEnd <= now ? new Window(now + windowMillis) : old);
        synchronized (w) {
            w.count++;
            return w.count <= limit;
        }
    }

    private static class Window {
        final long windowEnd;
        int count;

        Window(long windowEnd) {
            this.windowEnd = windowEnd;
        }
    }
}
