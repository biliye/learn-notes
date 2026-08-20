---
category: Java
category_slug: java
topic: 函数
topic_slug: function
title: Java 方法与函数式接口
slug: lambda-basics
tags: [基础, lambda, 函数式接口]
summary: 讲清方法定义、可变参数，以及 Lambda 与函数式接口的关系。
order: 10
spec_version: v1
---

# Java 方法与函数式接口

方法是 Java 里一段可复用、带名字的逻辑单元。本文从方法定义讲到 Lambda 与函数式接口的关系。

## 方法定义

方法由修饰符、返回类型、方法名、参数列表与方法体组成：

```java
public int add(int a, int b) {
    return a + b;
}
```

- 方法名使用小驼峰命名
- 参数列表里的每个参数都必须声明类型
- 无返回值时用 `void`

## 可变参数

调用时可以传任意个 `int`，编译器把它们装进数组：

```java
public static int sum(int... nums) {
    int total = 0;
    for (int n : nums) {
        total += n;
    }
    return total;
}
```

可变参数必须是参数列表的**最后一个**。

## Lambda 与函数式接口

函数式接口是**只有一个抽象方法**的接口，可以用 Lambda 表达式实现：

```java
@FunctionalInterface
public interface Calculator {
    int calculate(int a, int b);
}

Calculator add = (a, b) -> a + b;
```

常见内置函数式接口：

| 接口 | 抽象方法 | 用途 |
|---|---|---|
| `Function<T, R>` | `R apply(T)` | 转换 |
| `Consumer<T>` | `void accept(T)` | 消费 |
| `Predicate<T>` | `boolean test(T)` | 判断 |
| `Supplier<T>` | `T get()` | 供给 |

> 小结论：Lambda 的语法糖背后就是函数式接口的匿名实现。

## 常见坑

1. Lambda 捕获的外部变量必须是 `final` 或 effectively final。
2. 接口有多个抽象方法时不能用 Lambda（那不是函数式接口）。
3. `this` 在 Lambda 里指向**外层对象**，与匿名内部类不同。
