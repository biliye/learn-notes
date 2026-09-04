---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: Redis 登录会话与 BitMap 连续签到
slug: hm-dianping-login-sign
tags: [Redis, Hash, ThreadLocal, BitMap, 签到]
summary: 短信验证码登录用 Redis 存验证码与用户信息，双拦截器配合 ThreadLocal 做登录态；BitMap 按月记录签到并统计连续签到天数。
order: 60
spec_version: v2
---

# Redis 登录会话与 BitMap 连续签到

这一篇讲黑马点评的登录与会话管理，以及签到功能。登录部分：短信验证码存 Redis，用户信息以 Hash 存 Redis，用随机 token 作为会话标识，配合双拦截器和 ThreadLocal 维护登录态。签到部分：用 BitMap 以「一天一个 bit」按月记录签到，再用位运算统计连续签到天数。

## 发送验证码：Redis String 存 2 分钟

发送验证码时校验手机号格式，生成 6 位随机码，存到 Redis 的 String，key 为 `login:code:{phone}`，有效期 2 分钟。

```java
@Override
public Result sendCode(String phone, HttpSession session) {
    if (RegexUtils.isPhoneInvalid(phone)) {
        return Result.fail("手机号格式错误！");
    }
    String code = RandomUtil.randomNumbers(6);
    stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
    log.debug("发送短信验证码成功，验证码：{}", code);
    return Result.ok();
}
```

验证码放 Redis 而不是 Session，是为了让登录态能够跨服务共享，也为后续「分布式、可独立存储」打基础。过期时间由 `LOGIN_CODE_TTL` 控制，值为 2（分钟）。

## 登录：校验验证码并写用户 Hash

登录时先校验手机号与验证码，验证码从 Redis 取，与前端提交的 `code` 比对，不一致直接报错。一致后按手机号查用户，不存在则自动注册一个新用户。随后生成随机 token，把用户信息转成 Map 存入 Redis 的 Hash（`login:token:{token}`），并设置有效期。

```java
@Override
public Result login(LoginFormDTO loginForm, HttpSession session) {
    String phone = loginForm.getPhone();
    if (RegexUtils.isPhoneInvalid(phone)) {
        return Result.fail("手机号格式错误！");
    }
    String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
    String code = loginForm.getCode();
    if (cacheCode == null || !cacheCode.equals(code)) {
        return Result.fail("验证码错误");
    }
    User user = query().eq("phone", phone).one();
    if (user == null) {
        user = createUserWithPhone(phone);
    }
    String token = UUID.randomUUID().toString(true);
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
            CopyOptions.create()
                    .setIgnoreNullValue(true)
                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
    String tokenKey = LOGIN_USER_KEY + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
    stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
    return Result.ok(token);
}
```

关键点是把 `UserDTO` 转成 `Map`：用 `BeanUtil.beanToMap` 并设置 `setIgnoreNullValue(true)` 忽略 null 字段，再用 `fieldValue.toString()` 把所有值转成字符串，因为 Hash 的值必须是字符串。`UUID.randomUUID().toString(true)` 生成不带短横线的 token，作为会话标识返回给前端，后续每次请求都带这个 token。

token 的有效期是 `LOGIN_USER_TTL`，值为 36000（分钟，约 25 天）。它会在每次请求时被刷新（见下面拦截器），即「滑动续期」，只要活跃就一直有效。

## ThreadLocal 用户持有器

`UserHolder` 用 `ThreadLocal` 保存当前线程的登录用户 `UserDTO`。因为同一个请求的拦截器、Service 都在同一个线程内执行，用 ThreadLocal 就能在任意位置拿到当前登录用户，不必层层传递。

```java
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }
}
```

`ThreadLocal` 每个线程一份，请求结束后必须 `removeUser()` 清掉，否则线程复用（线程池）时可能把上一个用户的登录态带到下一个请求，造成串号。

## 双拦截器：续期 + 拦截未登录

登录态用两个拦截器实现，作用分层：`RefreshTokenInterceptor` 负责解析 token、写 ThreadLocal、刷新有效期，且无条件放行；`LoginInterceptor` 只负责检查 ThreadLocal 里有没有用户，没有就返回 401。

`RefreshTokenInterceptor` 在 `preHandle` 里取请求头 `authorization`，没有 token 就放行；有 token 就从 Redis 取 Hash，命中就把用户填进 `UserHolder` 并刷新 token 有效期；在 `afterCompletion` 里移除用户。

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String token = request.getHeader("authorization");
    if (StrUtil.isBlank(token)) {
        return true;
    }
    String key = LOGIN_USER_KEY + token;
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
    if (userMap.isEmpty()) {
        return true;
    }
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    UserHolder.saveUser(userDTO);
    stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;
}

@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
}
```

`RefreshTokenInterceptor` 之所以无条件放行，是让未登录用户也能访问公开接口（比如浏览店铺、看笔记），只是 ThreadLocal 里没有用户而已。它在请求响应完成后清空 ThreadLocal，防止线程复用串数据。

`LoginInterceptor` 只看 `UserHolder.getUser()` 是否为 null，为 null 说明没登录，直接设 401 并拦截；有则放行。它拦截的是需要登录才能访问的路径。

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if (UserHolder.getUser() == null) {
        response.setStatus(401);
        return false;
    }
    return true;
}
```

双拦截器的思路是「一个管刷新登录态、一个管是否放行」，两者分工明确：前者几乎所有请求都过，后者只对受保护路径生效。

## BitMap 签到

签到用一个 bit 表示一天：0 未签到，1 已签到。key 为 `sign:{userId}:yyyyMM`（按月一个 key），第几天就对应第几个 bit。`setBit(key, dayOfMonth - 1, true)` 把当天位置设成 1。

```java
@Override
public Result sign() {
    Long userId = UserHolder.getUser().getId();
    LocalDateTime now = LocalDateTime.now();
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;
    int dayOfMonth = now.getDayOfMonth();
    stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
    return Result.ok();
}
```

key 由 `USER_SIGN_KEY`（`sign:`）、用户 id 和 `:yyyyMM`（月）拼接成 `sign:{id}:202609` 这样的形式。`dayOfMonth` 减 1 是因为 bit 位从 0 开始，1 号对应 offset 0。一个 bit 只占 1 位，一个月最多 31 位，1000 万用户也才占几 MB，非常省内存。

## BITFIELD 取当月二进制再统计连续天数

统计连续签到天数时，用 `bitField` 按「无符号整数」取当月截止今天的所有签到 bit，返回一个十进制整数。这个整数的二进制位从低到高依次是 1 号到今天是否签到。

```java
@Override
public Result signCount() {
    Long userId = UserHolder.getUser().getId();
    LocalDateTime now = LocalDateTime.now();
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = USER_SIGN_KEY + userId + keySuffix;
    int dayOfMonth = now.getDayOfMonth();
    List<Long> result = stringRedisTemplate.opsForValue().bitField(
            key,
            BitFieldSubCommands.create()
                    .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
    );
    if (result == null || result.isEmpty()) {
        return Result.ok(0);
    }
    Long num = result.get(0);
    if (num == null || num == 0) {
        return Result.ok(0);
    }
    int count = 0;
    while (true) {
        if ((num & 1) == 0) {
            break;
        } else {
            count++;
        }
        num >>>= 1;
    }
    return Result.ok(count);
}
```

`bitField` 的 `get(... unsigned(dayOfMonth))` 表示取一个无符号整数，长度正好是当月已过去的天数，`valueAt(0)` 表示从第 0 位开始。返回的二进制串从低位到高位分别对应 1 号到今天，比如 `dayOfMonth = 5` 时取 `u5`。

连续签到计数用的是位运算：`num & 1` 取最低位，为 0 说明今天没签到，中断计数直接 break；为 1 说明签到，计数器加一。然后用无符号右移 `num >>>= 1` 丢弃最低位、看下一位。这样从今天往前逐位检查，直到碰到第一个未签到的天，得到区间内的连续签到天数。

## 小结

登录与会话：验证码用 Redis String 存 2 分钟，登录成功把用户信息以 Hash 存进 `login:token:{token}`（有效期 36000 分钟，滑动续期），token 作为会话标识返回前端。双拦截器里 `RefreshTokenInterceptor` 负责解析 token、填充并清空 `ThreadLocal`、刷新有效期，`LoginInterceptor` 负责拦截未登录请求返回 401。签到：用 BitMap 以 `sign:{userId}:yyyyMM` 按月记录，一天一个 bit；统计连续天数用 `BITFIELD` 取当月二进制整数，配合 `num & 1` 与无符号右移从今天往前数连续签到天数。
