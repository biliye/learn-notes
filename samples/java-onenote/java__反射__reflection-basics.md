---
category: Java
category_slug: java
topic: 反射
topic_slug: reflection
title: 反射基础
slug: reflection-basics
tags: [反射, 反射, 字节码, Class]
summary: 反射让程序在运行时获取类的结构并调用方法：先拿到 Class 字节码对象，再取方法对象与参数对象；setAccessible 可临时取消访问权限限制。
order: 10
---

# 反射基础

反射是 Java 的"运行时自省"能力：程序运行期间，通过 `Class` 对象动态获取一个类的结构（字段、方法、构造器）并操作它们。很多框架（Spring、MyBatis）底层都靠反射。

## 先获取字节码对象

方法、字段都定义在**字节码文件**（class 文件）里。要调用一个方法，就得先拿到这个类的**字节码对象**，也就是 `Class` 对象。

```java
Class<?> clazz = User.class;              // 方式一：类名.class
Class<?> clazz2 = Class.forName("com.demo.User");   // 方式二：字符串，运行期才知类名
Class<?> clazz3 = user.getClass();        // 方式三：对象.getClass()
```

三种方式殊途同归，都是拿到同一个 `Class` 对象。

## 拿到方法和参数对象

有了 `Class` 对象，才能拿到该类的方法对象（`Method`），再拿到方法的参数对象（`Parameter`），进而调用它。

```java
Class<?> clazz = User.class;
Method m = clazz.getMethod("getName");    // 获取方法对象
Object obj = clazz.getConstructor().newInstance();  // 无参构造创建一个实例
Object result = m.invoke(obj);            // 反射调用方法
```

流程很清晰：**获得 Class 文件 → 获得方法对象 → 获得参数对象 → 调用**。`getMethod` 只返回 public 方法，`getDeclaredMethod` 能拿到私有方法。

## 临时取消访问权限限制

反射要访问私有成员时，默认会被访问控制拦截。调用 `setAccessible(true)` 可以**临时取消访问权限限制**，让私有方法/字段也能被读写。

```java
Method privateMethod = clazz.getDeclaredMethod("secret");
privateMethod.setAccessible(true);        // 关闭访问权限检查
Object r = privateMethod.invoke(obj);
```

`setAccessible(true)` 绕过了 Java 的访问控制，常被框架用于注入字段、调用私有方法，但滥用会破坏封装。

## 小结

反射的三步走：拿 `Class` 字节码、取方法/参数对象、调用。私有成员用 `setAccessible(true)` 临时放开访问限制。理解反射，很多框架"为什么能自动注入、自动调方法"就豁然开朗了。
