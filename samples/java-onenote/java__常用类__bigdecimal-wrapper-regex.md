---
category: Java
category_slug: java
topic: 常用类
topic_slug: common-class
title: BigDecimal、包装类与正则表达式
slug: bigdecimal-wrapper-regex
tags: [BigDecimal, 包装类, 正则表达式, double]
summary: BigDecimal 解决 double 精度丢失、doubleValue 做类型转换；包装类让基本类型能进集合；正则表达式匹配字符与分组。
order: 20
---

# BigDecimal、包装类与正则表达式

处理浮点、进制转换与文本规则匹配，是 Java 常用类里最实用的三块：小数用 `BigDecimal`，基本类型进集合靠包装类，规则匹配用正则表达式。

## BigDecimal

`double` 是二进制浮点，某些十进制小数无法精确表示（如 0.1），计算会出现精度丢失。`BigDecimal` 用高精度十进制表示小数，适合金额等要求精确的场景。

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal sum = a.add(b);          // 0.3，而不是 double 的 0.30000000000000004
System.out.println(sum.doubleValue());   // 转回 double：0.3
```

`doubleValue()` 可以把 `BigDecimal` 转回 `double` 类型。构造时建议用**字符串**（`new BigDecimal("0.1")`）而不是 `new BigDecimal(0.1)`，后者仍会用 double 的近似值导致误差。

## 包装类

基本类型（int、double、char 等）不是对象，不能直接放进集合。包装类（Integer、Double、Character）把基本类型包装成对象，让它们能当对象用、也能进集合。

```java
List<Integer> list = new ArrayList<>();
list.add(10);              // 自动装箱：int -> Integer
int n = list.get(0);       // 自动拆箱：Integer -> int
```

自动装箱发生在"基本类型赋给包装类型"时，自动拆箱发生在"包装类型赋给基本类型"时。拆箱时如果包装对象为 null 会抛 `NullPointerException`，这是常见坑。

## 正则表达式

正则表达式（Regex）用于匹配文本规则。`Pattern` 编译规则、`Matcher` 执行匹配。

```java
Pattern p = Pattern.compile("\\d{3,5}");   // 匹配 3~5 位数字
Matcher m = p.matcher("abc123456def");
System.out.println(m.find());              // true，能找到匹配片段
```

正则可匹配多个字符，匹配不成功会**自动移到下一个位置继续匹配**（`find()` 的语义）。匹配到的既可以是单个字符，也可以是正则里的字符组或分组（用 `()` 捕获，`Matcher.group()` 取值）。

## 小结

金额计算用 `BigDecimal`（且用字符串构造）；基本类型进集合靠包装类的自动装箱/拆箱（小心 null 拆箱）；文本规则校验用正则（`find` 逐位置匹配、括号分组捕获）。这三个工具都是 API 学习里的高频考点。
