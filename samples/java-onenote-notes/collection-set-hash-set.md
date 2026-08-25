---
category: Java
category_slug: java
topic: 集合
topic_slug: collection
title: Set 集合与哈希表：HashSet、LinkedHashSet
slug: collection-set-hash-set
tags: [集合, Set, HashSet, 哈希表]
summary: 讲清 hashCode 与 equals 的配合流程、HashSet 的哈希表添加过程与扩容/链表转树，单列集合三大体系的选择，可变参数以及 Collections 工具类。
order: 20
spec_version: v1
---

# Set 集合与哈希表：HashSet、LinkedHashSet

本篇讲 Set 体系是如何去重的：先看 hashCode 与 equals 的配合流程，再看 HashSet 底层的哈希表结构、扩容与链表转红黑树，最后给出单列集合的选择建议，并补充可变参数和 Collections 工具类。

## hashCode 与 equals 的配合流程

当添加对象的时候，会先调用对象的 `hashCode` 方法计算出一个应该存入的索引位置，查看该位置上是否已经存在元素：

1. 不存在：直接存。
2. 存在：调用 `equals` 方法比较内容。
3. 内容相同（`equals` 返回 true）：不存。
4. 内容不同（`equals` 返回 false）：存。

总结：先哈希定位，再用 `equals` 判断是否重复。

## HashSet 的添加过程（JDK8 以后）

底层结构：哈希表（数组、链表、红黑树的结合体）。

1. 创建 HashSet 集合，内部会存在一个长度为 16 的数组。
2. 调用集合的添加方法，会拿着对象的 `hashCode` 方法计算出应存入的索引位置，公式为哈希值 % 数组长度。
3. 对哈希值进行扰动（二次哈希），可以一定程度减少链表挂载的数量。

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

数组初始状态里存的是 `null`。

## 扩容与链表转红黑树

查找一个元素需要逐个比对，如何提高查询性能？两条路：

扩容数组的条件：

- A：数组中的元素个数到达了 16 × 0.75（加载因子）= 12 时，扩容为原数组 2 倍的大小。
- B：链表挂载的元素超过了 8（阈值）个，并且数组长度没有超过 64。

链表转红黑树：链表挂载的元素超过了 8 个，并且数组长度到达了 64。

## 单列集合的三大体系

单列集合一次添加一个元素，根接口为 `Collection`，下分 List 与 Set。

| 接口 | 实现类 | 特点 |
|---|---|---|
| List | ArrayList、LinkedList | 存取有序，有索引，可以存储重复的 |
| Set | HashSet、TreeSet、LinkedHashSet | 存取无序，没有索引，不可以存储重复的 |

## 集合选择总结

1. 如果想要集合中的元素可重复，用 ArrayList 集合，基于数组的（用的最多）。
2. 如果想要集合中的元素可重复，而且当前的增删操作明显多于查询，用 LinkedList 集合，基于链表的。
3. 如果想对集合中的元素去重，用 HashSet 集合，基于哈希表的（用的最多）。
4. 如果想对集合中的元素去重，而且保证存取顺序，用 LinkedHashSet 集合，基于哈希表和双链表，效率低于 HashSet。
5. 如果想对集合中的元素进行排序，用 TreeSet 集合，基于红黑树。后续也可以用 List 集合实现排序。

LinkedHashSet 特点：有序、不重复、无索引。原理：底层数据结构依然是哈希表，只是每个元素额外多了一个双链表机制，用来记录存储的顺序。

## 可变参数

可变参数用在形参中，可以接收多个数据。格式为 `数据类型...参数名称`。

```java
public static int getSum(int a, int... nums) {
    int sum = 0;
    for (int num : nums) {
        sum += num;
    }
    return sum;
}
```

传输参数非常灵活：可以不传参数，可以传 1 个或多个，也可以传一个数组。可变参数在方法内部本质上就是一个数组。

注意事项：

1. 一个形参列表中可变参数只能有一个。
2. 可变参数必须放在形参列表的最后面。

```java
public static void main(String[] args) {
    int result = getSum(1, 2, 3, 4);
    System.out.println(result);
}
```

## Collections 工具类

`java.util.Collections` 是集合工具类，它并不属于集合，而是用来操作集合的工具类。

| 方法 | 说明 |
|---|---|
| `static <T> boolean addAll(Collection<? super T> c, T... elements)` | 给集合对象批量添加元素 |
| `static void shuffle(List<?> list)` | 打乱 List 集合元素的顺序 |
| `static <T> int binarySearch(List<T> list, T key)` | 以二分查找法查找元素 |
| `static <T> T max/min(Collection<T> coll)` | 根据默认的自然排序获取最大/小值 |
| `static void swap(List<?> list, int i, int j)` | 交换集合中指定位置的元素 |

Collections 的排序相关 API 只能对 List 集合使用。

排序方式 1：将集合中元素按照默认规则排序。

```java
public static <T> void sort(List<T> list)
```

注意：这种方式不可以直接对自定义类型的 List 集合排序，除非自定义类型实现了比较规则 `Comparable` 接口。

排序方式 2：将集合中元素按照指定规则排序。

```java
public static <T> void sort(List<T> list, Comparator<? super T> c)
```

示例：

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectionsTest {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        Collections.addAll(list, 3, 1, 2);
        Collections.sort(list);
        System.out.println(list);
        Collections.shuffle(list);
        int index = Collections.binarySearch(list, 2);
        System.out.println(index);
    }
}
```

## 小结

Set 依靠 hashCode 与 equals 协作来去重，底层哈希表通过扰动、扩容与链表转红黑树来平衡性能。选择集合时按"是否要可重复、是否要存取顺序、是否要排序"三个维度对照决策；可变参数与 Collections 工具类则让批量操作更简洁。
