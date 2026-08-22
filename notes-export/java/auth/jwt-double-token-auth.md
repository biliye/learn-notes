---
category: Java
category_slug: java
topic: 认证授权
topic_slug: auth
title: JWT 双令牌登录认证与拦截器
slug: jwt-double-token-auth
tags: [JWT, 拦截器, 登录, ThreadLocal]
summary: 苍穹外卖管理端与用户端各用一套 JWT 密钥，由 HandlerInterceptor 统一校验，登录用户 id 存入 ThreadLocal。
order: 20
---

# JWT 双令牌登录认证与拦截器

登录认证是项目的入口安全防线。苍穹外卖的管理员和小程序用户走两套独立 token，各自有独立的密钥与过期时间。

## 双令牌配置

在 application.yml 中用 sky.jwt 前缀分别配置管理端与用户端，互不干扰。

```yaml
sky:
  jwt:
    admin-secret-key: itcast
    admin-ttl: 7200000
    admin-token-name: token
    user-secret-key: itheima
    user-ttl: 7200000
    user-token-name: authentication
```

登录成功后用 JwtUtil 生成 token 返回给前端，前端后续请求把它放在请求头里。

## 拦截器校验流程

自定义 HandlerInterceptor，在 preHandle 里完成三步：取头、验签、存上下文。

```java
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod)) {
        return true;  // 静态资源等非动态方法直接放行
    }
    String token = request.getHeader(jwtProperties.getAdminTokenName());
    try {
        Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
        Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
        BaseContext.setCurrentId(empId);
        return true;
    } catch (Exception ex) {
        response.setStatus(401);
        return false;
    }
}
```

校验失败直接返回 401，由前端统一跳转登录页。

## 拦截器注册与放行规则

在 WebMvcConfiguration 里注册，注意把登录接口和公开接口排除在外。

```java
registry.addInterceptor(jwtTokenAdminInterceptor)
        .addPathPatterns("/admin/**")
        .excludePathPatterns("/admin/employee/login");
registry.addInterceptor(jwtTokenUserInterceptor)
        .addPathPatterns("/user/**")
        .excludePathPatterns("/user/user/login")
        .excludePathPatterns("/user/shop/status");
```

店铺状态查询对未登录用户开放，所以单独放行。

## BaseContext 与 ThreadLocal

登录用户 id 通过 ThreadLocal 保存，同一线程内随处可取，service 层写审计字段时直接 get。

```java
public class BaseContext {
    private static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    public static void setCurrentId(Long id) { threadLocal.set(id); }
    public static Long getCurrentId() { return threadLocal.get(); }
    public static void removeCurrentId() { threadLocal.remove(); }
}
```

注意请求结束时最好调用 remove，防止线程池复用导致数据串台。

## 小结

拦截器 + ThreadLocal 是单体项目最朴素的认证方案：拦截器负责"验"，ThreadLocal 负责"传"，配置类负责"管"。理解这三个角色，就能举一反三。
