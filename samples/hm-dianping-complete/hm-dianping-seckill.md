---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: 秒杀下单：Lua 原子扣库存 + Redis Stream 异步下单
slug: hm-dianping-seckill
tags: [Java, Redis, 秒杀, Lua, RedisStream]
summary: 黑马点评秒杀的核心实现：Lua 脚本原子扣库存与判重，Redis Stream 消费者组异步建单，以及 Redisson 锁兜底一人一单。
order: 30
spec_version: v2
---

# 秒杀下单：Lua 原子扣库存 + Redis Stream 异步下单

秒杀是黑马点评的重头戏。高并发下「先查库再扣库存」会超卖，而且把建订单这种耗时操作放同步链路会拖垮接口。完整实现版用三步解决：Lua 脚本在 Redis 里原子完成「判库存、判重复、扣库存、发消息」，把订单交给 Redis Stream 消费者组，后台单线程线程池异步建单。这样接口只做轻量的标记和取号，返回订单 id。

## 整体流程

`seckillVoucher(voucherId)` 是秒杀入口，位于 `VoucherOrderServiceImpl`。

```java
public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(), userId.toString(), String.valueOf(orderId));
    int r = result.intValue();
    if (r != 0) {
        return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
    }
    return Result.ok(orderId);
}
```

先准备好订单 id（全局 ID），再用 Lua 拿资格。Lua 返回 0 表示成功，返回 1 表示库存不足，返回 2 表示已下过单。接口本身不做建单，只返回订单 id，压力被移到了后台。

## seckill.lua 逐行解读

秒杀脚本用三个参数：优惠券 id、用户 id、订单 id。脚本内所有操作在 Redis 单线程里按顺序执行，天然原子，所以不会超卖、不会重复下单。

```lua
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0
```

第 1 段取参数，第 2 段拼 key。`seckill:stock:{voucherId}` 存剩余库存，`seckill:order:{voucherId}` 是 Set，记录已经下过单的用户。

先 `get stockKey` 判库存，`tonumber` 转数字后小于等于 0 返回 1；再看 `sismember orderKey userId`，已存在说明重复下单返回 2。都通过后用 `incrby` 递减库存、`sadd` 把用户加进订单集合，最后 `xadd` 往 `stream.orders` 里写一条包含 userId、voucherId、id 的消息，返回 0。

扣库存与判重在同一次脚本里完成，避免了两步操作之间被其他请求插入的竞态。即便脚本在 Lua 里判断通过，后台建单时也还会用数据库再兜底校验一次。

## Redis Stream 消费者组

消息的接收方是 Redis Stream 的消费者组。`VoucherOrderHandler` 是一个后台 Runnable，在 `@PostConstruct` 里丢进单线程 Executor 启动。

```java
private static final ExecutorService SECKILL_ORDER_EXECUTOR =
        Executors.newSingleThreadExecutor();

@PostConstruct
private void init() {
    SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
}
```

消费用 `XREADGROUP`：指定消费者组名 `g1`、消费者名 `c1`、每次读 1 条、阻塞 2 秒，从头读 `>`（表示只读新投递、未被本组消费过的消息）。

```java
List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
        Consumer.from("g1", "c1"),
        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
        StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
```

读到消息后，先从 Map 里用 `BeanUtil.fillBeanWithMap` 还原出 `VoucherOrder`，再调用 `createVoucherOrder` 建单，最后 `XACK` 确认这条消息。

```java
VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
createVoucherOrder(voucherOrder);
stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
```

消费者组的特点是消息不会被重复消费：组内消息只投递一次，`XACK` 之前属于 pending 状态。万一处理抛异常，消息留在 pending-list 里，由 `handlePendingList` 兜底重试。

## pending-list 兜底

`XACK` 前的消息都算未确认。若消费时抛异常，消息不会丢，而是停在这位消费者的 pending-list 里。`handlePendingList` 用 `ReadOffset.from("0")` 重读该消费者的未确认消息，逐个处理、确认，直到空消息退出。

```java
List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
        Consumer.from("g1", "c1"),
        StreamReadOptions.empty().count(1),
        StreamOffset.create("stream.orders", ReadOffset.from("0")));
if (list == null || list.isEmpty()) {
    break;
}
```

主循环异常时调用 `handlePendingList`，把阻塞期间拖下的任务补上，保证「读到、处理完、一定确认」这套语义。

## createVoucherOrder：后台防重复

真正写订单的地方是 `createVoucherOrder`。虽然 Lua 已经判过重，但建单是异步的，数据库里仍然要再校验一次，并用 Redisson 锁住同一用户防止并发下单。

```java
private void createVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();
    Long voucherId = voucherOrder.getVoucherId();
    RLock redisLock = redissonClient.getLock("lock:order:" + userId);
    boolean isLock = redisLock.tryLock();
    if (!isLock) {
        return;
    }
    try {
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        if (count > 0) {
            return;
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            return;
        }
        save(voucherOrder);
    } finally {
        redisLock.unlock();
    }
}
```

`lock:order:{userId}` 保证同一用户同时只建一单；`count > 0` 是数据库层的一人一单兜底；扣库存用 `stock = stock - 1 where stock > 0` 的乐观写法，避免负库存。到这里「Lua 原子扣库存 + Stream 异步建单 + 锁防重」形成闭环。

## 小结

这套秒杀把「抢资格」（Redis 中原子完成）和「落地订单」（异步建单）拆开：Lua 用 `incrby`、`sismember`、`xadd` 一气呵成，接口瞬间返回订单 id；Redis Stream 消费者组保证消息不丢、可兜底；后台 Redisson 锁加数据库校验挡住重复下单。相比同步查库扣库存，它扛得住高并发，也更健壮。
