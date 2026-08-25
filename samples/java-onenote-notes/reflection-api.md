---
category: Java
category_slug: java
topic: 反射
topic_slug: reflection
title: 反射：获取构造方法、成员方法与成员变量
slug: reflection-api
tags: [反射, Class, 构造器]
summary: 理解反射的概念与三种获取 Class 的方式，掌握通过反射操作构造方法、成员方法和成员变量。
order: 10
---

# 反射：获取构造方法、成员方法与成员变量

反射允许程序在运行时对类进行编程访问，可以动态地获取类的构造方法、成员方法和成员变量，并把它们创建、调用、赋值。这种动态性让代码更加灵活，是很多框架的底层基础。

## 什么是反射

反射对成员变量、成员方法和构造方法的信息进行编程访问。通过反射可以获取修饰符、名字、类型，并创建对象、运行方法、赋值取值。

## 获取 Class 对象的三种方式

要获得一个类的结构，先要拿到代表它的 `Class` 对象。获取 `Class` 对象有三种方式：`Class.forName("全类名")`、`类名.class`、`对象.getClass()`。

```java
Class<?> c1 = Class.forName("com.example.Person");
Class<?> c2 = Person.class;
Class<?> c3 = new Person().getClass();
```

## 获取构造方法

`Class` 类中用 `getConstructors` 获取所有公共构造方法，`getDeclaredConstructors` 获取所有构造方法，`getConstructor` 获取单个公共构造方法，`getDeclaredConstructor` 获取单个构造方法。`Constructor` 类中用 `newInstance` 根据指定构造方法创建对象，`setAccessible(true)` 取消访问检查，用来访问私有构造方法。

```java
// 获取无参公共构造方法
Constructor<Person> c = clazz.getConstructor();
// 通过构造方法创建对象
Person p = c.newInstance();

// 获取私有构造方法并解除访问检查
Constructor<Person> c2 = clazz.getDeclaredConstructor(int.class);
c2.setAccessible(true);
Person p2 = c2.newInstance(20);
```

## 获取成员方法

`Class` 类中 `getMethods` 返回所有公共成员方法（包括继承的），`getDeclaredMethods` 返回所有成员方法（不包括继承的），`getMethod` 返回单个公共方法，`getDeclaredMethod` 返回单个方法。`Method` 类中用 `invoke` 运行方法，第一个参数是被调用对象的实例，第二个参数是调用方法时传入的参数，返回值就是方法的返回值。

```java
Class<?> clazz = Person.class;
Person p = new Person();

// 获取公共成员方法
Method m = clazz.getMethod("setName", String.class);
// 运行方法并传参
m.invoke(p, "张三");

// 调用私有方法同样要解除访问检查
Method m2 = clazz.getDeclaredMethod("sayHello");
m2.setAccessible(true);
Object result = m2.invoke(p);
```

## 获取成员变量

`Class` 类中 `getFields` 返回所有公共成员变量，`getDeclaredFields` 返回所有成员变量，`getField` 返回单个公共成员变量，`getDeclaredField` 返回单个成员变量。`Field` 类中用 `set` 赋值，用 `get` 取值。访问私有字段同样需要 `setAccessible(true)`。

```java
Class<?> clazz = Person.class;
Person p = new Person();

Field name = clazz.getDeclaredField("name");
name.setAccessible(true);
// 赋值
name.set(p, "李四");
// 取值
Object value = name.get(p);
```

## 小结

反射的核心是拿到 `Class` 对象，再借助 `Constructor`、`Method`、`Field` 三个类去访问类的结构。面对私有成员，调用 `setAccessible(true)` 取消访问检查。它让程序能动态加载和操作类，但也更复杂，通常只在框架和工具中大规模使用，业务代码应谨慎引入。
