---
category: Java
category_slug: java
topic: 类
topic_slug: class
title: Java 内部类
slug: inner-class
tags: [类, 内部类, 匿名类]
summary: 四种内部类与它们的典型用途。
order: 20
spec_version: v1
---

# Java 内部类

内部类是定义在另一个类内部的类，按位置与形式分为四种。

## 成员内部类

成员内部类可以访问外部类的所有成员（含私有）：

```java
public class Outer {
    private int x = 1;

    class Inner {
        public int value() {
            return x; // 直接访问外部私有字段
        }
    }
}
```

创建方式：`new Outer().new Inner()`。

## 静态内部类

用 `static` 修饰，不持有外部类引用，常用于配套的数据结构：

```java
public class MapUtil {
    public static class Entry<K, V> {
        private final K key;
        private final V value;
        // ...
    }
}
```

## 局部内部类

定义在方法体内，只在方法内可见，捕获的外部变量必须 effectively final。

## 匿名内部类

没有名字，定义即实例化，常用于一次性回调：

```java
button.addActionListener(new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("clicked");
    }
});
```

> Lambda 出现后，函数式接口的场景优先用 Lambda，代码更短。

## 四种形式对比

| 形式 | 是否持有外部引用 | 典型场景 |
|---|---|---|
| 成员内部类 | 是 | 与外部类强耦合的组件 |
| 静态内部类 | 否 | 配套数据结构（如 `Map.Entry`） |
| 局部内部类 | 是 | 方法内的一次性逻辑 |
| 匿名内部类 | 是 | 回调、事件监听 |

## 小结

优先顺序：Lambda > 匿名内部类 > 其他内部类；能静态就静态。
