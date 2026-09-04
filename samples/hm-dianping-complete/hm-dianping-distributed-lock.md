---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: 分布式锁：从 SimpleRedisLock 到 Redisson
slug: hm-dianping-distributed-lock
tags: [Java, Redis, 分布式锁, Redisson]
summary: 黑马点评里分布式锁的两个阶段：用 setnx + Lua 实现的 SimpleRedisLock，以及升级后的 Redisson 看门狗锁，并讲清各自缺陷与应用场景。
order: 20
spec_version: v2
---

# 分布式锁：从 SimpleRedisLock 到 Redisson

「一人一单」和缓存击穿互斥，都需要一个能让多个线程抢的锁。JVM 内的 `synchronized` 只在本进程生效，集群下会失效，所以要把锁放到 Redis。黑马点评完整实现版里保留了从 `synchronized` 到 `SimpleRedisLock` 再到 `Redisson` 的演进注释，正好是一个讲分布式锁的完整素材。

## synchronized 为什么不行

在单机开发时，直接在方法上用 `synchronized` 互斥同一个用户即可。但它有几个问题：只能锁单台 JVM；锁的粒度跟对象绑定，需要借助字符串 intern；多节点部署后完全失效。

```java
synchronized (userId.toString().intern()) {
    // 查询订单、扣库存、创建订单
}
```

因此只要应用部署了多个实例，就必须把锁移到共享的 Redis 上。

## SimpleRedisLock：用 setnx 加锁

简单锁的接口是 `ILock`，提供 `tryLock(timeoutSec)` 和 `unlock()` 两个方法。

```java
public interface ILock {
    boolean tryLock(long timeoutSec);
    void unlock();
}
```

加锁用 `setIfAbsent`（对应 Redis 的 `SETNX`），带过期时间；value 不是简单 true，而是当前线程的唯一标识。这样锁有 owner，释放时才能只删自己的锁。

```java
public boolean tryLock(long timeoutSec) {
    String threadId = ID_PREFIX + Thread.currentThread().getId();
    Boolean success = stringRedisTemplate.opsForValue()
            .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(success);
}
```

## owner 标识与原子释放

value 用「前置 UUID + 线程 id」拼成 `ID_PREFIX + Thread.currentThread().getId()`。这样每个线程的锁标识都不一样，释放锁前先比对，防止误删别人的锁。

释放锁这一步存在经典竞态：如果先 `get`（判断 owner）再 `del`，判断与删除之间锁可能过期被别的线程抢走，从而删掉别人的锁。于是把「比对 + 删除」写进 `unlock.lua`，让 Redis 按原子方式执行。

```lua
-- 比较线程标示与锁中的标示是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
```

```java
public void unlock() {
    stringRedisTemplate.execute(
            UNLOCK_SCRIPT,
            Collections.singletonList(KEY_PREFIX + name),
            ID_PREFIX + Thread.currentThread().getId());
}
```

这样 SimpleRedisLock 就解决了「锁误删」：比对的 value 与加锁时一致，再用 Lua 保证比对与删除的原子性。

## SimpleRedisLock 的缺陷

它仍然不是生产级方案，主要是这三点。

| 缺陷 | 说明 |
|---|---|
| 不可重入 | 同一线程再次加锁会失败，无法嵌套使用 |
| 无自动续期 | 业务执行超过 timeoutSec 后锁被释放，其他线程趁虚而入 |
| 单点风险 | Redis 主节点宕机时锁不可用，或主从切换后锁丢失 |

因为锁有固定过期时间，「业务还没做完锁就没了」最致命。理想情况是锁在业务结束前不被释放，而 Redisson 的看门狗正好解决这一点。

## 升级到 Redisson

引入 `redisson` 依赖后，用配置类注册一个 `RedissonClient` 单例。

```java
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://192.168.150.101:6379")
              .setPassword("123321");
        return Redisson.create(config);
    }
}
```

业务里用 `redissonClient.getLock(key)` 拿到 `RLock`，`tryLock()` 尝试加锁，`finally` 里 `unlock()` 释放。加锁成功后，Redisson 会起一个看门狗定时任务，默认每 10 秒把锁的过期时间续到 30 秒，业务没做完锁就不会被提前释放。

```java
RLock redisLock = redissonClient.getLock("lock:order:" + userId);
boolean isLock = redisLock.tryLock();
if (!isLock) {
    return; // 获取锁失败，不允许重复下单
}
try {
    // 查询订单、扣库存、创建订单
} finally {
    redisLock.unlock();
}
```

对比 SimpleRedisLock，Redisson 还支持可重入、公平锁、读写锁等，看门狗机制则解决「锁过期、业务还没完成」的最大痛点。

## 两个应用场景

黑马点评这份代码里，分布式锁出现在两处。

一是**缓存击穿互斥**：查店铺缓存未命中时，抢 `lock:shop:{id}`，抢到锁的人去查库并重建缓存。这样同一时刻只有一个线程落到数据库，避免热点 key 击穿。

二是**一人一单**：在 `createVoucherOrder` 里用 `lock:order:{userId}` 锁住同一用户，单人单次抢购只能下一单，重复点击或并发请求被锁挡住。

两处都遵循「加锁在最外层、finally 里释放、锁的 key 用业务主键」的写法，key 前缀正是 `lock:shop:` 与 `lock:order:`。

## 小结

分布式锁从 `synchronized` 起步，自实现的 `SimpleRedisLock` 解决了「setnx 加锁 + owner 比对 + Lua 原子释放」，却仍受不可重入、无续期、单点限制。最终用 Redisson 落地：看门狗续期让锁更稳，底层 Lua 保证原子性。它在缓存击穿互斥和秒杀「一人一单」里都有应用。
