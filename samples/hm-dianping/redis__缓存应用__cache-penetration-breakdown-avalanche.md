---
category: Redis
category_slug: redis
topic: 缓存应用
topic_slug: cache-app
title: 缓存穿透、击穿、雪崩与黑马点评的解法
slug: cache-penetration-breakdown-avalanche
tags: [Redis, 缓存, 缓存穿透, 缓存击穿]
summary: 高频读场景引入缓存后的三大问题：穿透、击穿、雪崩。本文结合 CacheClient 讲清定义、危害与黑马点评项目里的落地代码。
order: 20
---

# 缓存穿透、击穿、雪崩与黑马点评的解法

缓存能抗住高频读，但也引入三个经典问题：穿透、击穿、雪崩。三者都表现为"请求打到数据库"，成因不同，解法也不同。黑马点评用 `CacheClient` 统一封装了前两种的解法。

## 缓存穿透

查询一个**数据库中根本不存在**的数据，缓存永远 miss，请求全部落到数据库。攻击者用不存在的 id 循环请求就能拖垮数据库。

黑马点评的解法是**空值缓存**：查不到就把空字符串写进缓存，给一个短 TTL（`CACHE_NULL_TTL`，2 分钟），后续同类请求直接命中空值。

```java
// CacheClient.queryWithPassThrough：缓存穿透解法（已实现）
public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
        Function<ID, R> dbFallback, Long time, TimeUnit unit) {
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(json)) {          // 缓存命中
        return JSONUtil.toBean(json, type);
    }
    if (json != null) {                       // 命中的是空值
        return null;
    }
    R r = dbFallback.apply(id);               // 查数据库
    if (r == null) {                          // 不存在 → 缓存空值 2 分钟
        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
    }
    this.set(key, r, time, unit);             // 存在 → 正常写缓存
    return r;
}
```

`dbFallback` 是 `Function<ID, R>`，把"查库"逻辑作为参数传入，同一个方法可以复用于店铺、用户等任何实体，这是通用缓存工具的关键设计。

## 缓存击穿

**热点 key 过期的一瞬间**，大量并发请求同时发现缓存 miss，一起打到数据库。与穿透的区别是：数据存在，只是缓存刚好失效。

黑马点评提供了两套解法：互斥锁与逻辑过期，都在 `ShopServiceImpl` 里，当前 `queryById` 走的是互斥锁方案。

```java
// 互斥锁解法（已实现）：抢锁失败就休眠 50ms 后递归重试
boolean isLock = tryLock(lockKey);          // setIfAbsent lock:shop:{id} 10s
if (!isLock) {
    Thread.sleep(50);
    return queryWithMutex(id);              // 递归重试，等持锁线程重建缓存
}
// 拿到锁后 double check：可能持锁线程已把缓存写好了
String shopJsonTest = stringRedisTemplate.opsForValue().get(key);
if (StrUtil.isNotBlank(shopJsonTest)) {
    return JSONUtil.toBean(shopJsonTest, Shop.class);
}
```

```java
// 逻辑过期解法（已实现）：RedisData 里存 data + expireTime 两个字段
// 过期后不是删 key，而是抢锁后开独立线程重建缓存，请求继续返回旧数据
CACHE_REBUILD_EXECUTOR.submit(() -> this.saveShop2Redis(id, 20L));
```

互斥锁保证"同一时刻只有一个请求重建缓存"，逻辑过期保证"缓存永不失效，旧数据兜底 + 异步刷新"。前者强一致但有一瞬阻塞，后者响应快但数据短暂不一致。

## 缓存雪崩

大量 key 在**同一时刻集中过期**，导致大批请求同时打数据库。代码中尚未做专门处理，常规手段是给 TTL 加随机值打散过期时间，或者用多级缓存（本地缓存 + Redis）分层兜底。

```java
// 雪崩预防思路：TTL 加随机扰动，避免同一秒集体过期
long ttl = CACHE_SHOP_TTL + RandomUtil.randomLong(1, 300); // 单位：秒
```

## 缓存更新策略

`ShopServiceImpl.update` 采用 Cache Aside 模式：先更新数据库，再删除缓存，下次查询时重建。

```java
@Override
public Result update(Shop shop) {
    updateById(shop);                              // 1. 先更新数据库
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId()); // 2. 再删缓存
    return Result.ok();
}
```

先更库再删缓存比"先删缓存再更库"更稳：后者在更新期间会有旧数据被重新写回缓存的风险。删缓存而不是直接改缓存，是为了避免并发写时出现数据错乱。

## 常见坑：店铺类型缓存没有 TTL

`ShopTypeServiceImpl.queryTypeList` 把类型列表 JSON 塞进 `shopTypes` 这个 key，但 `set` 时没传过期时间，且新增类型时也不清缓存。结果就是类型列表一旦缓存就永不更新，是典型的"缓存与数据库不一致"示例。

```java
// 有坑的写法：无 TTL + 无失效逻辑（当前实现）
stringRedisTemplate.opsForValue().set("shopTypes", JSONUtil.toJsonStr(shopTypeList));
```

对比店铺缓存，正确姿势是显式 TTL + 写操作后主动删 key。这个坑说明：缓存不是"存了就完事"，过期策略与失效时机必须一起设计。

## 小结

穿透、击穿、雪崩三个问题的共同点是"缓存 miss 时如何保护数据库"。黑马点评把解法沉淀成了 `CacheClient` 泛型工具：空值缓存防穿透，互斥锁或逻辑过期防击穿，TTL 随机化防雪崩，Cache Aside 保证更新一致性。学完这节，能独立说出每个问题的触发条件、危害与对应代码位置，就算真正掌握了。
