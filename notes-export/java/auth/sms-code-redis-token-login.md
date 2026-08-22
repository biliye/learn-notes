---
category: Java
category_slug: java
topic: 认证授权
topic_slug: auth
title: 短信验证码登录与 Redis Token 会话
slug: sms-code-redis-token-login
tags: [Redis, 登录, 拦截器, ThreadLocal]
summary: 黑马点评用 Redis 替代 Session 保存登录态：验证码存 String、用户信息存 Hash，双拦截器负责刷新与校验，登录用户放入 ThreadLocal。
order: 30
---

# 短信验证码登录与 Redis Token 会话

黑马点评的登录是"手机号 + 验证码"模式，但登录态没有用 Session，而是存进 Redis：验证码用 String，登录用户用 Hash，前端拿到 token 后每次请求带在 `authorization` 头里。这套方案天然支持多实例部署。

## 发送验证码

```java
// UserServiceImpl.sendCode（已实现）
if (RegexUtils.isPhoneInvalid(phone)) {       // 1. 正则校验手机号
    return Result.fail("手机号格式错误");
}
String code = RandomUtil.randomNumbers(6);    // 2. 生成 6 位随机码
// 3. 验证码存 Redis，key=login:code:{phone}，TTL 2 分钟
stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,
        LOGIN_CODE_TTL, TimeUnit.MINUTES);
log.debug("发送验证码成功，验证码 {}", code);   // 课程里用日志代替短信通道
```

验证码的 key 带手机号、TTL 只有 2 分钟，目的是限制重试窗口。真实项目这里要接短信服务商，并加发送频率限制（同一手机号 60 秒内不能重复发）。

## 登录与 token 签发

```java
// UserServiceImpl.login（已实现）
String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
if (cacheCode == null || !cacheCode.equals(code)) {
    return Result.fail("验证码错误");
}
User user = query().eq("phone", phone).one();
if (user == null) {
    user = createUserWithPhone(phone);        // 新用户自动注册，昵称 user_ + 10 位随机数
}
String token = UUID.randomUUID().toString();  // token 用 UUID，无状态
Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
        CopyOptions.create().setIgnoreNullValue(true)
                .setFieldValueEditor((f, v) -> v.toString()));
String tokenKey = LOGIN_USER_KEY + token;     // login:token:{token}
stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);  // 用户信息存 Hash
stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES); // 约 25 天
return Result.ok(token);
```

用户信息转成 `Map<String, Object>` 再放进 Hash，是因为 `opsForHash` 只接受 Map 类型；`setFieldValueEditor` 把字段值统一转成字符串，避免 Long 等类型写入出错。token 返回给前端，前端后续请求通过请求头回传。

## 双拦截器：刷新 + 校验

`MvcConfig` 注册了两个拦截器，配合实现"无感续期 + 精准拦截"。

```java
// MvcConfig（已实现）
registry.addInterceptor(new LoginInterceptor())            // order=1，真正拦未登录
        .excludePathPatterns("/shop/**", "/voucher/**", "/shop-type/**",
                "/upload/**", "/blog/hot", "/user/code", "/user/login");
registry.addInterceptor(new RefreshtTokenInterceptor(stringRedisTemplate))  // order=0
        .addPathPatterns("/**");
```

```java
// RefreshtTokenInterceptor.preHandle：读 token → 查 Redis → 放行前续期（已实现）
String token = request.getHeader("authorization");
Map<Object, Object> userMap =
        stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
if (userMap.isEmpty()) {
    return true;                              // 没登录也放行，交给 order=1 的拦截器
}
UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
UserHolder.saveUser(userDTO);                 // 存入 ThreadLocal
stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
```

```java
// LoginInterceptor.preHandle：只查 ThreadLocal，为空直接 401（已实现）
if (UserHolder.getUser() == null) {
    response.setStatus(401);
    return false;
}
```

```java
// UserHolder：ThreadLocal 保存当前线程的登录用户（已实现）
private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
public static void saveUser(UserDTO user) { tl.set(user); }
public static UserDTO getUser() { return tl.get(); }
public static void removeUser() { tl.remove(); }
```

两个拦截器职责分离：刷新拦截器对所有路径执行，负责解析 token、续期、写 ThreadLocal；登录拦截器只查 ThreadLocal，负责拦截。`RefreshtTokenInterceptor.afterCompletion` 里调用 `UserHolder.removeUser()` 清理线程变量，防止线程池复用导致用户串号。

## 与 Session 方案对比

| 维度 | Session | Redis Token |
|---|---|---|
| 存储位置 | Tomcat 内存 | Redis |
| 多实例部署 | 需要 session 共享或粘滞会话 | 天然支持 |
| 服务端主动失效 | 可以 | 可以（删 key） |
| 续期 | 需要额外机制 | `expire` 一行搞定 |

代价是每次请求多一次 Redis 读（Hash 的 `entries`），以及 token 无法像 Session 一样携带完整对象，需要反序列化。整体收益远大于成本，这也是现在主流方案。

## 常见坑

当前代码里登出接口还是 TODO（返回"功能未完成"），实现时只需删除 `login:token:{token}` 这个 key。另一个坑是 `LOGIN_USER_TTL` 单位是分钟且值较大（36000 分钟约 25 天），与"30 分钟无操作自动过期"的常见需求不同，续期策略按业务需要调整。

## 小结

这套登录方案的价值不在验证码本身，而在"登录态存哪、怎么验、怎么续"的完整链路：Redis Hash 存用户、UUID 当 token、双拦截器分工、ThreadLocal 传值。照着 `UserServiceImpl` → `MvcConfig` → 两个拦截器 → `UserHolder` 的顺序读一遍，就能串起整条认证链路。
