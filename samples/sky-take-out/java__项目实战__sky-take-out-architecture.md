---
category: Java
category_slug: java
topic: 项目实战
topic_slug: project-practice
title: 苍穹外卖项目架构与模块划分
slug: sky-take-out-architecture
tags: [Spring Boot, MyBatis, Maven, 架构]
summary: 苍穹外卖（sky-take-out）是单体多模块的 Spring Boot 外卖项目，本文梳理模块职责、分层结构与技术栈。
order: 10
---

# 苍穹外卖项目架构与模块划分

苍穹外卖是经典的 Java Web 实战项目，采用 Spring Boot 2.7 + MyBatis + Redis 的单体架构，按 Maven 多模块拆分，代码组织清晰、便于学习分层思想。

## 技术栈

| 层次 | 技术 | 用途 |
|---|---|---|
| 基础框架 | Spring Boot 2.7.3 | 应用骨架、自动装配 |
| 持久层 | MyBatis + PageHelper + Druid | ORM、分页、连接池 |
| 缓存 | Redis | 店铺状态、缓存等 |
| 接口文档 | Knife4j（Swagger 2） | 前后端联调文档 |
| 工具 | Lombok、fastjson、commons-lang | 简化开发 |
| 外部依赖 | 阿里云 OSS、微信 API | 图片存储、小程序登录支付 |

## 模块划分

项目按职责拆成三个 Maven 模块，父工程只做依赖版本管理，不写业务代码。

```text
sky-take-out/
├── sky-common   通用模块：结果封装、常量、异常、工具类、上下文
├── sky-pojo     实体模块：entity / dto / vo 三层数据对象
└── sky-server   服务模块：controller / service / mapper，真正的业务代码
```

模块依赖方向是单向的：server 依赖 common 和 pojo，common 与 pojo 互不依赖，避免循环依赖。

## 分包约定

sky-server 内部按技术层次分包，而不是按业务分包，这是单体项目最常见的组织方式。

```text
controller   接收请求（admin 管理端 / user 用户端 / notify 回调）
service      业务逻辑（接口 + impl 实现）
mapper       MyBatis 数据访问（接口 + xml）
config / interceptor / aspect / annotation / task / websocket   横切与基础设施
```

管理端与用户端接口通过 `/admin/**` 和 `/user/**` 前缀区分，拦截器按路径匹配做权限控制。

## 小结

单体多模块 + 分层分包是中小型项目的主流形态：模块边界保证依赖清晰，分包保证职责单一。学这个项目时，建议先画清楚"请求从 controller 到 mapper 的调用链"，再逐模块深入。
