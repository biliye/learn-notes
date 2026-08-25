---
category: Java
category_slug: java
topic: 集合
topic_slug: collection
title: Map 集合常用 API 与键值对遍历
slug: collection-map
tags: [集合, Map, 键值对]
summary: 讲清 Map 集合的特点、put/remove/clear/containsKey 等常用 API，以及通过键找值与 entrySet 两种遍历方式。
order: 30
spec_version: v1
---

# Map 集合常用 API 与键值对遍历

本篇讲双列集合 Map：先明确它的特点与常用 API，再介绍两种遍历方式，即通过键找值和使用 entrySet 找键值对。

## Map 集合的特点

Map 是双列集合，存储的是键值对（Key-Value）。特点：

1. 键（Key）唯一，值（Value）可以重复。
2. 一个键只能对应一个值，存入相同的键会用新值替换旧值。
3. 键和值是一一对应的映射关系。

## Map 常用 API

| 方法 | 说明 |
|---|---|
| `V put(K key, V value)` | 添加元素；如果键已经存在，就会使用新值替换旧值，返回被覆盖掉的旧值 |
| `V remove(Object key)` | 根据键删除键值对元素，返回被删除的键所对应的值 |
| `void clear()` | 移除所有的键值对元素 |
| `boolean containsKey(Object key)` | 判断集合是否包含指定的键 |
| `boolean containsValue(Object value)` | 判断集合是否包含指定的值 |
| `boolean isEmpty()` | 判断集合是否为空 |
| `int size()` | 集合的长度，也就是集合中键值对的个数 |

```java
import java.util.HashMap;
import java.util.Map;

public class MapTest {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("01", "张三");
        map.put("02", "李四");
        String oldValue = map.put("01", "王五");
        System.out.println(oldValue);
        System.out.println(map.containsKey("01"));
        System.out.println(map.containsValue("李四"));
        System.out.println(map.size());
        map.remove("02");
    }
}
```

## 通过键找值遍历

第一种遍历方式：获取所有的键，遍历 Set 集合，再根据键用 `get` 方法查找对应的值。

用到的两个方法：`keySet()` 获取 Map 集合中所有的键，`get(Object key)` 根据键查找对应的值。

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapTravelByKey {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("01", "张三");
        map.put("02", "李四");

        Set<String> keySet = map.keySet();
        for (String key : keySet) {
            String value = map.get(key);
            System.out.println(key + "---" + value);
        }
    }
}
```

## entrySet 遍历

第二种遍历方式：直接获取所有的键值对对象（Entry），遍历后从中取出键和值。这需要用到 `entrySet()` 方法，它返回所有的键值对对象集合。

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapTravelByEntry {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("01", "张三");
        map.put("02", "李四");

        Set<Map.Entry<String, String>> entries = map.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println(key + "---" + value);
        }
    }
}
```

## 小结

Map 以键值对的形式组织数据，键唯一、值可重复。常用 API 覆盖了增删查与判断，而遍历就落在"通过键找值"与"通过 entrySet 找键值对"两条路径上。
