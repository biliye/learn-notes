---
category: Java
category_slug: java
topic: 异常
topic_slug: exception
title: 异常处理与 finally
slug: exception-and-finally
tags: [异常, 异常类, finally, try-catch]
summary: 异常在 Java 里都是类。try-catch-finally 结构里 finally 无论如何都会执行，常用于释放资源；也可以手动创建并抛出异常对象。
order: 10
---

# 异常处理与 finally

程序运行中出现的意外情况用**异常**来描述。Java 把异常建模成对象——所有的异常都是某个异常类的实例，这些类都继承自 `Throwable`。

## 异常都是类

异常体系以 `Throwable` 为根基，下分 `Error`（严重错误，一般无法也不该捕获）和 `Exception`（程序可处理的异常）。`Exception` 又分受检异常（编译期要求处理）与运行时异常（`RuntimeException`）。

```java
try {
    int[] a = new int[3];
    a[5] = 10;              // ArrayIndexOutOfBoundsException，运行时异常
} catch (IndexOutOfBoundsException e) {
    System.out.println("数组越界");
}
```

捕获用 `try-catch`，按异常类型从上到下匹配，子类要在前面；`catch` 的类型越具体越好。

## finally 总是执行

`try-catch-finally` 中，`finally` 块表示**无论是否出现异常、是否 return，都会执行**的语句，最适合放释放资源（关闭流、释放锁）的代码。

```java
try {
    int r = 10 / 0;
} finally {
    System.out.println("这里的代码无论如何都会执行");
}
```

即使 `try` 里抛异常，或 `catch` 里 return，`finally` 仍然会先执行。只有当 `finally` 里本身也抛出异常，或调用 `System.exit` 时，`finally` 才会中断。

## 手动抛异常

除了 JVM 抛出，也可以**自己创建并抛出异常对象**，用于主动提示业务上的非法情况。

```java
if (balance < amount) {
    throw new RuntimeException("余额不足");   // 手动创建异常对象并抛出
}
```

`throw` 抛出一个异常实例，`throws` 声明方法可能抛出的异常类型（交给调用者处理）。自定义异常类通常继承 `Exception` 或 `RuntimeException`，带一个 `message` 构造即可。

## 小结

异常是对象，捕获取决于类型；`finally` 保证兜底逻辑无论如何都执行（资源释放）；业务非法情况用 `throw` 主动抛异常提示。掌握"异常都是类、finally 必执行、可自建异常对象"这三点，异常部分就抓住了主干。
