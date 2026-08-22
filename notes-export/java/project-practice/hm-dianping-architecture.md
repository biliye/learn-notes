---
category: Java
category_slug: java
topic: 项目实战
topic_slug: project-practice
title: 黑马点评项目架构与功能地图
slug: hm-dianping-architecture
tags: [Spring Boot, MyBatis-Plus, Redis, 架构]
summary: 黑马点评（hm-dianping）是单体 Spring Boot + Redis 的商户点评实战项目，本文梳理技术栈、分层结构与"已实现 / 待实现"功能地图。
order: 10
---

# 黑马点评项目架构与功能地图

黑马点评是经典的 Redis 实战项目：一个类似大众点评的商户点评应用，核心价值在于用 Redis 解决登录会话、缓存、秒杀、Feed 流等真实场景问题。当前代码仓库是课程的"基础版"，登录与缓存部分已实现，秒杀、点赞、Feed 流等仍是空壳。

## 技术栈

| 层次 | 技术 | 用途 |
|---|---|---|
| 基础框架 | Spring Boot 2.3.12 | 应用骨架 |
| 持久层 | MyBatis-Plus 3.4.3 | ORM、分页插件 |
| 缓存 | Redis + Lettuce（commons-pool2 连接池） | 登录、缓存、后续的秒杀与 Feed |
| 数据库 | MySQL（mysql-connector 5.1.47） | 业务数据落库 |
| 工具 | Hutool 5.7.17、Lombok | JSON、Bean 拷贝、随机数、日志 |

应用端口 `8081`，数据库连接串指向 `redis_mysql` 库。项目为单模块单体，没有像苍穹外卖那样拆 Maven 多模块。

## 分层结构

```text
controller   接收请求（User / Shop / ShopType / Voucher / VoucherOrder / Blog / Follow / Upload）
service      业务逻辑（接口 + impl 实现，继承 MyBatis-Plus 的 ServiceImpl）
mapper       MyBatis-Plus 数据访问（接口 + 少量自定义 XML）
entity       PO 实体，@TableName 映射表
dto          传输对象（Result / LoginFormDTO / ScrollResult / UserDTO）
config       MvcConfig / MybatisConfig / WebExceptionAdvice
utils        拦截器、线程变量、Redis 常量与缓存工具
```

controller 层很薄，只做参数接收与结果包装，业务都在 service 里；统一返回 `Result`（success / errorMsg / data / total），全局异常由 `WebExceptionAdvice` 兜底。

## 功能地图

| 功能 | 状态 | 说明 |
|---|---|---|
| 短信验证码登录 | 已实现 | Redis 存验证码与 token，双拦截器校验 |
| 店铺查询缓存 | 已实现 | 穿透空值缓存、击穿互斥锁与逻辑过期 |
| 店铺类型列表缓存 | 已实现 | 整表 JSON 缓存，但无 TTL（有坑） |
| 普通券 / 秒杀券发布 | 已实现 | `@Transactional` 双表写入 |
| 秒杀下单 | 空壳 | 控制器直接返回"功能未完成" |
| 达人探店点赞 | 空壳 | 只做了 SQL 点赞数 +1，无 Redis |
| 关注与 Feed 流 | 空壳 | Follow 控制器为空 |
| 附近商户 GEO | 空壳 | `Shop.distance` 字段已预留未用 |
| 登出 | 空壳 | 控制器 TODO |

`RedisConstants` 里已经规划好全部 key 前缀（`seckill:stock:`、`blog:liked:`、`feed:`、`shop:geo:`、`sign:`），说明课程设计蓝图完整，后续实现按图索骥即可。

## 数据库表

```text
tb_user / tb_user_info      用户与详情
tb_shop / tb_shop_type      店铺与店铺类型
tb_voucher / tb_seckill_voucher / tb_voucher_order   优惠券、秒杀券（一对一）、订单
tb_blog / tb_blog_comments  达人探店笔记与评论
tb_follow                   用户关注关系
```

## 小结

这个项目的学习主线是"一个场景引入一个 Redis 知识点"：登录用 String + Hash，缓存用 String + 锁，秒杀用 Lua，Feed 用 ZSet，附近用 GEO，签到用 BitMap。先看懂已实现的登录与缓存，再按功能地图逐个补齐空壳，就能把 Redis 主流数据结构串成体系。
