---
category: Java
category_slug: java
topic: 泛型
topic_slug: generics
title: 泛型与集合排序（Comparable/Comparator）
slug: generics-comparable-comparator
tags: [泛型, Comparable, Comparator, 排序, Map]
summary: 泛型只能装引用类型；比较用 Comparable 实现自然排序，或用 Comparator 传入比较器自定义排序；两者都要求实现 compareTo/compare。
order: 10
---

# 泛型与集合排序（Comparable/Comparator）

泛型让集合和方法能限定元素类型，但**只能传入引用数据类型**（如 `String`、`Integer`），不能是基本类型——基本类型要用包装类。排序则是泛型+集合最常见的应用：要么让类自己去比较，要么传入一个比较器。

## 泛型限定类型

`List<String>` 表示这个列表只能装 `String`。泛型用于类、接口、方法，本质是"类型参数化"。

```java
List<String> list = new ArrayList<>();   // 泛型，只装引用类型
List<Integer> nums = new ArrayList<>();  // 写 Integer，不能写 int
```

泛型只能传引用类型，所以 `int` 要写成 `Integer`。方法也可以用泛型，让参数和返回值类型保持关联。

```java
public <T> T needMatch(T value) {   // 泛型方法
    return value;
}
```

## Comparable：自然排序

**自然排序**指类自己实现 `Comparable` 接口的 `compareTo` 方法，规定"同类对象如何比较"。实现时应把 `Comparable` 的泛型指定为**本类**，表示只与同类比较。

```java
public class Student implements Comparable<Student> {
    private int score;
    @Override
    public int compareTo(Student o) {
        return this.score - o.score;   // 负数：this 小；正数：this 大
    }
}
```

用 `Collections.sort(list)` 或 `TreeSet` 时，若元素没有实现 `Comparable`，排序会直接抛 `ClassCastException`。

## Comparator：比较器排序

**比较器排序**通过传入一个 `Comparator` 来定义排序方式，不改变类本身。适合对已有类型按不同维度排序。

```java
list.sort((s1, s2) -> s1.score - s2.score);          // 升序
list.sort(Comparator.comparingInt(Student::getScore).reversed());  // 降序
```

`Comparator` 的 `compare(o1, o2)` 返回值语义与 `compareTo` 一致。Lambda 写法比匿名内部类更简洁。

## Map 的排序

`Map` 本身是键值对，需要排序时也依赖上述机制：可以让键实现 `Comparable`（自然排序），或用 `Comparator` 传入 `TreeMap` 构造器。

```java
Map<String, Integer> map = new TreeMap<>();   // 按 key 自然排序
```

## 小结

想省略类型限制与强制转型就用泛型（注意只能传引用类型、基本类型用包装类）；需要排序时二选一：让类实现 `Comparable`+`compareTo` 是自然排序，传 `Comparator`+`compare` 是比较器排序。记住"自然排序改类、比较器不改类"，用起来就不纠结。
