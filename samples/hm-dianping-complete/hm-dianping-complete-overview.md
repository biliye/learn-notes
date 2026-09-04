---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: 黑马点评完整实现版：项目全景与功能清单
slug: hm-dianping-complete-overview
tags: [Java, Redis, SpringBoot, MyBatis-Plus, 秒杀]
summary: 黑马点评完整实现版项目的全景：技术栈、分层结构、功能地图、Redis key 设计与全局唯一订单 ID 的生成方式。
order: 10
spec_version: v2
---

# 黑马点评完整实现版：项目全景与功能清单

黑马点评是一份基于 Redis 的电商类实战项目。完整实现版把「秒杀」推进到了生产级形态：用 Lua 脚本原子扣库存、用 Redis Stream 做异步下单。本文先勾勒项目全景，再给出功能清单与 Redis key 设计，为后面两篇（分布式锁、秒杀）做铺垫。

## 技术栈

项目是一个 Spring Boot 单体应用，核心依赖如下。

```text
Spring Boot        2.3.12.RELEASE
MyBatis-Plus       3.4.3
MySQL              5.1.47 (数据库名 hmdp)
spring-data-redis  2.6.2 + Lettuce 6.1.6.RELEASE (连接池 commons-pool2)
Redisson           3.13.6
Hutool             5.7.17
Lombok             +
服务端口           8081
```

Lettuce 是默认客户端，配了连接池。Redisson 用于分布式锁，是秒杀「一人一单」和缓存击穿互斥的锁实现。

应用名是 `hmdp`，数据源指向 `jdbc:mysql://127.0.0.1:3306/hmdp`。Redis 通过 `application.yaml` 里的 `spring.redis` 配置连接。

## 分层结构

代码按标准的 controller/service/mapper 三层组织，外加 entity、dto、config、utils 支撑包。

```text
com.hmdp
├── HmDianPingApplication.java   启动类
├── controller                   对外接口
├── service                      业务接口 + impl
├── mapper                       MyBatis-Plus 的 mapper
├── entity                       数据库实体
├── dto                          出入参数据传输对象
├── config                       配置类（Mybatis、Mvc、Redisson）
└── utils                        工具类（RedisIdWorker、锁、缓存、拦截器等）
```

service 接口放在 `service` 包，实现类写在 `service/impl`。统一包装结果用 `dto.Result`，切面与拦截器之类的通用逻辑放在 `utils`。

## 功能地图

下面这张表把核心功能、入口和用到的 Redis 结构对上。

| 功能 | 入口 | 说明 | 用到的 Redis |
|---|---|---|---|
| 登录 | `/user/code`、`/user/login` | 手机验证码登录，token 保存用户 | `login:code:`、`login:token:` |
| 店铺缓存 | `/shop/...` | 查询店铺，缓存 + 缓存击穿处理 | `cache:shop:`、`lock:shop:` |
| 优惠券 | `/voucher/list/{shopId}` | 查询店铺优惠券列表 | `seckill:stock:` |
| 秒杀 | `/voucher-order/seckill/{id}` | Lua 扣库存 + Stream 异步建单 | `seckill:stock:`、`seckill:order:` |
| 点赞 | `/blog/like/{id}` | 点赞与取消，存用户集合 | `blog:liked:` |
| 关注 Feed 流 | `/follow/...` | 推送关注用户的笔记 | `feed:` |
| 附近店铺 | `/shop/of/type` | 按地理位置找店铺 | `shop:geo:` |
| 签到 | `/user/sign` | Bitmap 做签到与连续天数统计 | `sign:` |

登录用验证码 + token 的方案，店铺缓存用「逻辑过期 + 互斥锁」两种思路处理缓存击穿，秒杀做了完整流程，社交部分覆盖点赞、Feed 流、附近店铺和签到。

## Redis key 设计

前缀统一在 `RedisConstants` 里定义，避免手写字符串散落各处。

| 前缀 | 类型 | 说明 |
|---|---|---|
| `login:code:` | string | 验证码，TTL 2 分钟 |
| `login:token:` | string | 登录凭证，保存用户 DTO |
| `cache:shop:` | string | 店铺缓存 |
| `lock:shop:` | string | 店铺缓存互斥锁 |
| `lock:order:` | string | 下单互斥锁 |
| `seckill:stock:` | string | 秒杀库存 |
| `seckill:order:` | set | 记录本券下过单的用户 |
| `stream.orders` | stream | 秒杀订单消息队列 |
| `blog:liked:` | set | 笔记的点赞用户 |
| `feed:` | zset | 关注 Feed 流 |
| `shop:geo:` | geo | 店铺坐标 |
| `sign:` | bitmap | 签到记录 |

秒杀逻辑会同时用到 `seckill:stock:`（库存）、`seckill:order:`（判重复）和 `stream.orders`（异步下单的消息队列）。

## 全局 ID 生成

订单 id 不能依赖数据库自增，秒杀场景要用到分布式全局 id。`RedisIdWorker` 用「时间戳 + 序列号」的方案：高位是当前时间与起始时间（2022-01-01）的秒差，低位是当天自增序列。

```java
public long nextId(String keyPrefix) {
    LocalDateTime now = LocalDateTime.now();
    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
    long timestamp = nowSecond - BEGIN_TIMESTAMP;
    String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
    long count = stringRedisTemplate.opsForValue()
            .increment("icr:" + keyPrefix + ":" + date);
    return timestamp << COUNT_BITS | count;
}
```

`BEGIN_TIMESTAMP` 是起点时间戳，`COUNT_BITS` 是 32。`increment` 是原子自增，当 Redis 里没有该 key 时从 1 开始，所以同一天内序列号唯一；不同天序列号从 0 重新计数，但因为高位有位移，整体依然唯一且趋势递增。

## 小结

黑马点评完整实现版在基础项目之上，把秒杀做成了「Lua 扣库存 + Stream 异步建单」的成熟方案。分布式锁、Redis 缓存、Feed 流、GEO 等覆盖了 Redis 在业务里的大多数用法。接下来两篇分别展开分布式锁与秒杀下单的核心实现。
