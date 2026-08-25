---
category: Java
category_slug: java
topic: 集合
topic_slug: collection
title: ArrayList 与自动装箱
slug: arraylist-and-autoboxing
tags: [ArrayList, 集合, 自动装箱, 装箱]
summary: ArrayList 底层是数组，按索引存删会自动搬运元素；删除整数元素要装箱成对象；它按内容查找而非直接按索引取。
order: 10
---

# ArrayList 与自动装箱

`ArrayList` 是 Java 最常用的集合，底层用数组实现，支持动态扩容。但它有几个和"索引""装箱"相关的细节，容易踩坑。

## 底层是数组

`ArrayList` 内部维护一个 `Object[]`，按索引读写是 O(1)。当元素数超过数组容量时自动扩容（通常扩到原来的 1.5 倍）。

```java
List<String> list = new ArrayList<>();
list.add("a");
list.add("b");
list.add(1, "x");        // 在索引 1 插入 "x"
```

## 插入会把元素往后移

在某个位置插入时，**如果该位置已有元素，会把这个元素及其后面的所有元素整体往后挪一位**，腾出空位。这是数组插入的固有代价。

```java
list.add(1, "x");
// 原来 [a, b]，插入索引 1 后 -> [a, x, b]，b 被往后挪了
```

所以频繁在中间插入用 `ArrayList` 效率低，更适合用 `LinkedList`，或者把插入放尾部。

## 删除整数要装箱

`remove` 有两个重载：`remove(int index)` 按索引删、`remove(Object o)` 按对象删。当集合是 `Integer` 时，想删"等于某值的元素"必须**装箱**成对象，否则会误当索引删除。

```java
List<Integer> nums = new ArrayList<>(Arrays.asList(10, 20, 30));
nums.remove(20);     // 错误：20 被当作索引，越界异常
nums.remove(Integer.valueOf(20));   // 正确：装箱成对象，删除值为 20 的元素
```

这就是笔记里"删除整数要通过装箱"的含义——`Integer` 传入时若直接写数字，会被当成索引。

## 取值按内容查找

`ArrayList` 的检索（如 `contains`、`indexOf`）是**按内容线性查找**，而不是直接通过索引取。它遍历底层数组，用 `equals` 逐个比较，直到找到为止。

```java
nums.contains(30);        // 线性扫描，返回 boolean
int i = nums.indexOf(20); // 找到返回下标，找不到返回 -1
```

## 小结

`ArrayList` 数组实现、中间插入会搬元素、`remove` 的数字会误作索引（要装箱成对象）、取值按内容线性查找。这四个点记住了，集合相关的小坑基本都能避开。
