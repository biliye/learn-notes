---
category: Java
category_slug: java
topic: 流
topic_slug: stream
title: Stream 流与收集
slug: stream-and-collect
tags: [流, JDK8]
summary: 讲解 Stream 流如何从集合、数组、零散数据获取，中间操作 filter、limit、skip、distinct、concat、map 与终止操作 forEach、count，以及 collect 收进 List、Set、Map。
order: 10
---

# Stream 流与收集

Stream 把数据放在流水线的传送带上，把一整串集合操作串起来写，不用再写一长串循环和中间集合。

## 获取 Stream 流

Stream 流有三种来源：集合、数组、零散数据。

```java
// 1. 集合获取，使用 Collection 接口中的默认方法
ArrayList<String> list = new ArrayList<>();
list.add("林青霞");
list.add("张曼玉");
Stream<String> s1 = list.stream();

// Map 集合获取需要间接获取
map.entrySet().stream();

// 2. 数组获取，使用 Arrays 工具类中的静态方法
String[] arr = {"a", "b"};
Stream<String> s2 = Arrays.stream(arr);

// 3. 零散数据获取，使用 Stream 类中的静态方法
Stream<String> s3 = Stream.of("张三", "李四");
```

## 流的一次性

注意事项：流对象已经被消费过（使用过），就不允许再次消费了。中间操作返回的仍是 Stream，可以继续链接；但一个流只能被终止一次。

```java
Stream<String> stream = list.stream();
stream.forEach(s -> System.out.println(s));
stream.forEach(s -> System.out.println(s));   // 报错，流已被消费
```

## 中间操作

中间操作执行后返回 Stream 对象，可以继续操作。

| 方法 | 说明 |
|---|---|
| `Stream<T> filter(Predicate<? super T> predicate)` | 过滤数据 |
| `Stream<T> limit(long maxSize)` | 获取前几个元素 |
| `Stream<T> skip(long n)` | 跳过前几个元素 |
| `Stream<T> distinct()` | 去重，依赖 hashCode 和 equals 方法 |
| `static <T> Stream<T> concat(Stream a, Stream b)` | 合并 a 和 b 两个流为一个流 |
| `Stream<R> map(Function<T, R> mapper)` | 转换流中的数据类型 |

```java
ArrayList<String> list = new ArrayList<>();
list.add("林青霞");
list.add("张曼玉");
list.add("王祖贤");
list.add("柳岩");
list.add("张敏");
list.add("张无忌");

// 将集合中以「张」开头的数据过滤出来并打印
list.stream()
    .filter(s -> s.startsWith("张"))
    .forEach(s -> System.out.println(s));

// 取前 3 个数据
list.stream().limit(3).forEach(System.out::println);

// 跳过前 3 个数据
list.stream().skip(3).forEach(System.out::println);

// 把每个字符串转换成长度
list.stream().map(String::length).forEach(System.out::println);

// 合并两个流
Stream<String> s1 = list.stream().limit(2);
Stream<String> s2 = list.stream().skip(2);
Stream.concat(s1, s2).forEach(System.out::println);
```

## 终止操作

终止操作返回的不是 Stream 对象，而是最终结果。

| 方法 | 说明 |
|---|---|
| `void forEach(Consumer<T> action)` | 遍历消费每个元素 |
| long count() | 流中的元素个数 |

```java
long count = list.stream().filter(s -> s.startsWith("张")).count();
System.out.println(count);
```

## collect 收集

collect 把 Stream 流操作后的结果数据转回到集合。R collect(Collector collector) 开始收集 Stream 流、指定收集器，Collectors 工具类提供了具体的收集方式。

| 方法 | 说明 |
|---|---|
| `public static <T> Collector toList()` | 把元素收集到 List 集合中 |
| `public static <T> Collector toSet()` | 把元素收集到 Set 集合中 |
| `public static Collector toMap(Function keyMapper, Function valueMapper)` | 把元素收集到 Map 集合中 |

```java
// 收集到 List
List<String> names = list.stream()
        .filter(s -> s.startsWith("张"))
        .collect(Collectors.toList());

// 收集到 Set
Set<String> nameSet = list.stream().collect(Collectors.toSet());

// 收集到 Map，以姓名为 key、长度为 value
Map<String, Integer> nameMap = list.stream()
        .collect(Collectors.toMap(s -> s, String::length));
```

## 小结

Stream 从集合、数组、of 三种途径获取，是一条一次性的流水线。中间操作 filter、limit、skip、distinct、concat、map 做链式转换且延迟求值，最终由一个终止操作触发，常见的终止操作有 forEach、count。collect 配合 Collectors 的 toList、toSet、toMap 把处理结果收进集合。

