---
category: Redis
category_slug: redis
topic: 缓存应用
topic_slug: cache-app
title: Redis 缓存店铺营业状态
slug: shop-status-cache
tags: [Redis, 缓存, Spring Data Redis]
summary: 店铺营业状态是高频读、低频写的典型数据，直接存 Redis String，读写各一次操作，避免频繁访问数据库。
order: 10
---

# Redis 缓存店铺营业状态

店铺营业状态是最典型的高频读、低频写数据：用户端每次进入首页都要查一次，一天可能只改几次。放数据库每次查都浪费，放 Redis 刚刚好。

## 控制器直接读写缓存

店铺状态没有独立的 service，controller 直接注入 RedisTemplate 完成读写，代码非常短。

```java
@RestController("adminShopController")
@RequestMapping("/admin/shop")
public class ShopController {

    public final static String SHOP_STATUS = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    @PutMapping("/{status}")
    public Result status(@PathVariable Integer status) {
        redisTemplate.opsForValue().set(SHOP_STATUS, status);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Integer> getStatus() {
        Integer value = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS);
        return Result.success(value);
    }
}
```

状态以整数值存在键 SHOP_STATUS 上，1 营业、0 打烊，改动即时生效，无需改表。

## 用户端读缓存

用户端查店铺状态走同一个键，未登录也能查，所以拦截器里对 `/user/shop/status` 放行。

```java
registry.addInterceptor(jwtTokenUserInterceptor)
        .addPathPatterns("/user/**")
        .excludePathPatterns("/user/user/login")
        .excludePathPatterns("/user/shop/status");
```

## 为什么这个场景适合 Redis

营业状态写入极低频、读取极高频，值只有一个，连过期策略都不用设计，直接永久 String 即可。它是"缓存优先"思想的入门级示范：先把读写热点从 MySQL 挪到内存，后续再谈淘汰策略和一致性。

## 小结

判断一个数据要不要进缓存，就看两点：读多写少、时效性要求低。店铺状态两者都占，所以用一个 RedisTemplate 两行代码就解决了。学的时候注意体会"哪个 controller 都不需要 service"的取舍——不是所有逻辑都值得起一个 service。
