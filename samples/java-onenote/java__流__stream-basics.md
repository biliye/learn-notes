---
category: Java
category_slug: java
topic: Stream
topic_slug: stream
title: Stream 流基础
slug: stream-basics
tags: [Stream, forEach, concat, 集合]
summary: Stream 是对集合/数组元素的操作流，支持链式调用；像 forEach、concat 这类是终止/中间操作，流使用一次后就不能再复用。
order: 10
---

# Stream 流基础

Stream 不是集合，而是对集合、数组、I/O 等数据源上的**元素操作流**。它推荐"链式"写法：中间操作筛选/转换，终止操作触发执行，一次流只消费一遍。

## 基本写法

流通常从集合的 `stream()` 或 `Arrays.stream` 获取，再一步步链式操作，最后用一个终止操作结束。

```java
List<String> names = Arrays.asList("tom", "jack", "jerry");
names.stream()                    // 得到流
     .filter(s -> s.length() > 3) // 中间操作：筛选
     .map(String::toUpperCase)    // 中间操作：转换
     .forEach(System.out::println);   // 终止操作：遍历输出
```

中间操作返回的是新流，不会立即执行；终止操作（`forEach`、`collect`、`count` 等）才真正触发整个链条的计算。

## 流的一次性

一个流**使用过后就不能再使用**了。像 `forEach`、`concat` 这类操作一旦执行，流就已被消费，继续对这个流再调用方法会抛 `IllegalStateException: stream has already been operated upon or closed`。

```java
Stream<String> s = names.stream();
s.filter(x -> true).forEach(System.out::println);
s.count();   // 报错：这个流已经用过了
```

`concat(a, b)` 用来把两个流**合并**成一个新流，同样是一次性生成、只能消费一次。

## 常用操作

| 类型 | 方法 | 作用 |
|---|---|---|
| 中间 | `filter` | 按条件筛选 |
| 中间 | `map` | 元素转换 |
| 中间 | `distinct` / `sorted` | 去重 / 排序 |
| 终止 | `forEach` | 遍历 |
| 终止 | `collect(Collectors.toList())` | 收集回集合 |
| 终止 | `count` / `anyMatch` | 计数 / 判断 |

## 小结

Stream 的核心是"一次流、一次消费"：中间操作惰性、终止操作触发、流用后即废。想再次处理就得重新 `stream()` 拿一个新流，这是最容易撞的坑。
