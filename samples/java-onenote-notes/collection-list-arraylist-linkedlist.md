---
category: Java
category_slug: java
topic: 集合
topic_slug: collection
title: Collection 与 List：ArrayList、LinkedList
slug: collection-list-arraylist-linkedlist
tags: [集合, List, ArrayList, LinkedList]
summary: 讲清 Collection 常用方法、迭代器遍历、List 特点与索引 API、五种遍历方式、并发修改异常，以及 ArrayList 与 LinkedList 的底层结构。
order: 10
spec_version: v1
---

# Collection 与 List：ArrayList、LinkedList

本篇聚焦单列集合中最常用的 List 体系：先讲 Collection 根接口的公共方法，再讲 List 的特点、遍历与索引 API、并发修改异常，最后对比 ArrayList 与 LinkedList 的底层实现。

## Collection 常用方法

Collection 是单列集合的顶层接口，常用方法如下：

| 方法 | 说明 |
|---|---|
| `boolean add(E e)` | 把给定的对象添加到当前集合中 |
| `void clear()` | 清空集合中所有的元素 |
| `boolean remove(E e)` | 把给定的对象在当前集合中删除 |
| `boolean contains(Object obj)` | 判断当前集合中是否包含给定的对象 |
| `boolean isEmpty()` | 判断当前集合是否为空 |
| `int size()` | 返回集合中元素的个数 / 长度 |

注意事项：`remove()`、`contains()` 底层依赖对象的 `equals` 方法。

```java
import java.util.ArrayList;
import java.util.Collection;

public class CollectionTest {
    public static void main(String[] args) {
        Collection<String> coll = new ArrayList<>();
        coll.add("张三");
        coll.add("李四");
        coll.remove("张三");
        System.out.println(coll.contains("李四"));
        System.out.println(coll.isEmpty());
        System.out.println(coll.size());
    }
}
```

## 迭代器遍历

迭代器的两个核心方法：

- `hasNext()`：判断集合中是否还有元素。
- `next()`：取出集合元素，并将指针向后移动。

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IteratorTest {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            String s = it.next();
            System.out.println(s);
        }
    }
}
```

注意：在循环过程中 `next` 方法最好只调用一次。

## List 接口的特点与索引 API

List 接口的特点：存取有序，有索引，可以存储重复的。

和索引有关的 API：

```java
void add(int index, E element)
E remove(int index)
E set(int index, E element)
E get(int index)
```

示例：

```java
import java.util.ArrayList;
import java.util.List;

public class ListIndexTest {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(111);
        list.add(222);
        list.add(333);
        list.remove(Integer.valueOf(222));
        Integer e = 111;
        System.out.println(e);
    }
}
```

`remove` 的关键：`list.remove(222)` 是按索引删除；要删除元素本身需传对象，写成 `list.remove(Integer.valueOf(222))`。

## List 的五种遍历方式

List 集合的遍历方式：

1. 迭代器遍历。
2. 增强 for 循环。
3. `forEach` 方法。
4. 普通 for 循环。
5. `ListIterator`（List 集合特有的迭代器）。

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class ListTravel {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");

        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }

        for (String s : list) {
            System.out.println(s);
        }

        list.forEach(s -> System.out.println(s));

        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.get(i));
        }

        ListIterator<String> listIt = list.listIterator();
        while (listIt.hasNext()) {
            System.out.println(listIt.next());
        }
    }
}
```

## 并发修改异常

并发修改异常：`ConcurrentModificationException`。场景是使用迭代器遍历集合的过程中，调用了集合对象的添加、删除方法，就会出现此异常。

解决方案：迭代器遍历过程中不允许使用集合对象的添加或删除，那就使用迭代器自己的添加或删除方法。

```java
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class ConcurrentTest {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("温油");
        list.add("哈哈");
        list.add("伤心的人别听慢歌");

        ListIterator<String> it = list.listIterator();
        while (it.hasNext()) {
            String s = it.next();
            if ("温油".equals(s)) {
                it.add("哈哈哈哈");
            }
        }
        System.out.println(list);
    }
}
```

## ArrayList 源码解析

`ArrayList` 底层基于数组。如果只是创建了集合容器，没有进行过添加操作，底层数组默认长度为 0。

使用空参构造器创建的集合，底层创建一个默认长度为 0 的数组；添加第一个元素时，底层会创建一个新的长度为 10 的数组；存满时，会扩容 1.5 倍。

## LinkedList 类

`LinkedList` 底层基于双链表实现，查询元素慢，增删首尾元素非常快。

特有方法：

| 方法 | 说明 |
|---|---|
| `void addFirst(E e)` | 在该列表开头插入指定的元素 |
| `void addLast(E e)` | 将指定的元素追加到此列表的末尾 |
| `E getFirst()` | 返回此列表中的第一个元素 |
| `E getLast()` | 返回此列表中的最后一个元素 |
| `E removeFirst()` | 从此列表中删除并返回第一个元素 |
| `E removeLast()` | 从此列表中删除并返回最后一个元素 |

`LinkedList` 底层是双向链表结构，查找元素时从头部或尾部逐个查找。但它属于 List 体系中的集合，也可以使用 `get` 方法根据索引直接获取元素。

```java
import java.util.LinkedList;

public class LinkedListTest {
    public static void main(String[] args) {
        LinkedList<String> list = new LinkedList<>();
        list.addFirst("a");
        list.addLast("b");
        System.out.println(list.getFirst());
        System.out.println(list.getLast());
        list.removeFirst();
    }
}
```

## 小结

Collection 是单列集合的根接口，List 在它的基础上增加了索引、有序与可重复。ArrayList 用数组换来快速随机访问，LinkedList 用双链表换来高效的首尾增删；遍历时要注意迭代器与集合自身修改的冲突。
