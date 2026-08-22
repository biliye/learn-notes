---
category: Java
category_slug: java
topic: 项目实战
topic_slug: project-practice
title: 黑马点评待实现功能与设计方向
slug: hm-dianping-feature-roadmap
tags: [Redis, 秒杀, Feed流, GEO, 架构]
summary: 当前代码中秒杀、点赞、Feed 流、附近商户等仍是空壳，本文基于 RedisConstants 与表结构整理课程设计方向，标注哪些已实现、哪些待完成。
order: 20
---

# 黑马点评待实现功能与设计方向

`RedisConstants` 中已规划好全部 key 前缀，但很多功能在控制器层就返回"功能未完成"。本文整理各空壳功能的设计方向，代码中尚未实现的部分会明确标注，供后续动手补齐时参考。

## 秒杀下单

`VoucherOrderController.seckillVoucher` 目前直接返回失败，`VoucherOrderServiceImpl` 是空壳。课程标准设计分三步：先校验秒杀时间与库存，再用 Redis 预扣库存保证原子性，最后异步创建订单。

```java
// 设计方向 1：Redis 预扣库存（当前未实现）
// 下单前先扣 Redis 库存 seckill:stock:{voucherId}
// 扣成功才走数据库，避免高并发直接打 MySQL 行锁
stringRedisTemplate.opsForValue().decrement(SECKILL_STOCK_KEY + voucherId);
```

预扣库存要配合 Lua 脚本保证"判断库存 + 扣减"原子执行，否则并发下会超卖。一人一单限制则用 `voucher_order` 表的用户唯一约束或 `SETNX` 兜底。

## 达人探店点赞

`BlogController.likeBlog` 现在只是 `liked = liked + 1` 的 SQL 更新，重复点赞也会叠加。设计方向是用 Set 记录点赞用户，判断当前用户是否点过赞。

```java
// 设计方向：Set 记录点赞用户（当前未实现）
String key = BLOG_LIKED_KEY + blogId; // blog:liked:{blogId}
// 点赞：sadd key userId；取消：srem key userId
// 是否点赞：sismember key userId，再决定 liked 字段 +1 还是 -1
```

如果要按点赞时间排序展示榜单，把 Set 换成 ZSet，score 用点赞时间戳即可。

## 关注与 Feed 流

`FollowController` 为空，`ScrollResult`（list / minTime / offset）这个 DTO 已经为 Feed 流准备好。设计方向是关注后把博文 id 推送到自己的收件箱。

```java
// 设计方向：推模式 Feed 流（当前未实现）
// 发布笔记时，遍历粉丝列表，往 feed:{userId} 的 ZSet 推入笔记 id
// score 用时间戳；查询时用滚动分页：ZREVRANGEBYSCORE 按 minTime 翻页
// offset 记录同分数区间已取条数，避免重复
```

推模式读多写少，适合大 V 少、普通人多的场景；反过来的拉模式适合粉丝量大的场景。

## 附近商户与 GEO

`Shop` 实体里 `distance` 字段标注了 `@TableField(exist = false)`，是为 GEO 预留的。设计方向是把店铺坐标写入 Redis GEO 集合，按距离排序查询。

```java
// 设计方向：GEO 存储店铺坐标（当前未实现）
// 写入：GEOADD shop:geo:{typeId} x y shopId
// 查询：GEOSEARCH 按半径或矩形找附近店铺，距离回填到 distance 字段
```

店铺表本身有 `x`（经度）`y`（纬度）字段，数据源是现成的。

## 签到与 UV

`sign:` 前缀对应 BitMap 签到：按年月份建 key，第几天签到就把对应 bit 置 1，统计连续签到天数用位运算。UV 统计则用 HyperLogLog，`PFADD` 记录访客、`PFCOUNT` 去重计数，误差率约 0.81%，适合不要求精确的场景。

## 小结

空壳功能的设计方向都遵循同一条主线：把热点读写从数据库挪到 Redis，用合适的数据结构承载。动手实现时建议按依赖顺序推进：先秒杀（独立）、再点赞（独立）、再 Feed 流（依赖关注），最后补 GEO 与签到。
