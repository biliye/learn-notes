---
category: Redis
category_slug: redis
topic: 分布式锁
topic_slug: distributed-lock
title: Redis SETNX 互斥锁与缺陷分析
slug: redis-mutex-lock
tags: [Redis, 分布式锁, SETNX, 并发]
summary: 黑马点评用 setIfAbsent 实现互斥锁解决缓存击穿，本文拆解 tryLock / unlock 的写法、在查询中的用法，以及它不可重入、锁误删等缺陷。
order: 10
---

# Redis SETNX 互斥锁与缺陷分析

缓存击穿的互斥锁方案，本质是"多实例间只有一个请求能重建缓存"。黑马点评用 Redis 的 `SETNX`（`setIfAbsent`）实现了最简互斥锁，代码在 `ShopServiceImpl` 与 `CacheClient` 中。

## 加锁与释放

```java
// 加锁：key 不存在才写入，10 秒自动过期（已实现）
private boolean tryLock(String key) {
    Boolean flag = stringRedisTemplate.opsForValue()
            .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);   // true = 抢锁成功
}

// 释放：直接删 key（已实现）
private void unlock(String key) {
    stringRedisTemplate.delete(key);
}
```

`setIfAbsent` 是原子操作："key 不存在才写入，且同时设置过期时间"。必须把过期时间放进同一个命令，否则先 SETNX 再 EXPIRE 两步走，进程在两步之间崩溃就会留下永不过期的死锁。10 秒 TTL 是兜底，防止持锁线程崩溃后锁永远不释放。

## 在查询中的使用

`queryWithMutex` 把锁用在"缓存 miss 之后、查库之前"：

```java
// 缓存未命中 → 抢锁（已实现）
boolean isLock = tryLock(lockKey);        // lock:shop:{id}
if (!isLock) {
    Thread.sleep(50);                     // 没抢到 → 睡 50ms 重试（递归）
    return queryWithMutex(id);
}
try {
    // 抢到锁 → double check：别的线程可能已重建好缓存
    String cached = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(cached)) {
        return JSONUtil.toBean(cached, Shop.class);
    }
    Shop shop = getById(id);              // 真正查库
    // ... 写缓存（存在则写空值）
} finally {
    unlock(lockKey);                      // 无论成败都要释放
}
```

三个细节值得记住：抢锁失败用短休眠 + 递归重试而不是自旋空转；抢到锁后要 **double check** 缓存，避免重复查库；释放锁必须放 `finally`，防止异常路径死锁。

## 缺陷分析

这套最简互斥锁能跑通课程场景，但距离生产可用还有明显差距。

| 缺陷 | 后果 | 改进方向 |
|---|---|---|
| 锁没有 owner 标识 | 线程 A 的锁过期后线程 B 拿到锁，A 的 finally 把 B 的锁删了 | value 存 UUID，删除前用 Lua 校验 owner |
| 不可重入 | 同一线程嵌套加锁会死等自己 | 用可重入锁（Redisson 的 lock） |
| 10 秒固定过期 | 重建缓存超过 10 秒，锁提前释放，多请求同时查库 | 看门狗自动续期 |
| 删除锁非原子 | 判断 + 删除两步之间锁可能已过期换主 | `if (owner 匹配) del key` 用 Lua 脚本原子执行 |
| 单点问题 | Redis 主节点宕机，锁丢失 | RedLock / Redisson 多节点方案 |

## 小结

SETNX 互斥锁是理解 Redis 分布式锁的最佳起点：三行核心代码讲清"原子加锁 + 过期兜底"，`queryWithMutex` 展示它在业务里的完整用法。学完应能复述缺陷表里的每一条，并知道生产环境直接用 Redisson 而不是自己造轮子。
