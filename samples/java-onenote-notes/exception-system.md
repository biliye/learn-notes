---
category: Java
category_slug: java
topic: 异常
topic_slug: exception
title: 异常体系与处理
slug: exception-system
tags: [异常]
summary: 梳理 Throwable、Error、Exception 的体系与分类，讲解 try-catch 执行流程、throws 声明、throw 与 throws 的区别、自定义异常的写法和重写方法的异常限制。
order: 10
---

# 异常体系与处理

异常是代码在编译或者执行的过程中可能出现的错误。Java 用一套面向对象的体系来描述它，学习的重点是理解异常的结构分类，以及捕获和抛出异常的方式。

## 异常体系结构

顶层是 Throwable，分为 Error 与 Exception 两大分支。

| 类别 | 划分 | 特点 |
|---|---|---|
| Error | 严重级别问题，通常跟系统有关 | 常见栈内存溢出 StackOverflowError、堆内存溢出 OutOfMemoryError，通常无法靠代码恢复 |
| Exception | RuntimeException 及其子类 | 运行时异常，编译阶段不报错，运行可能报错 |
| Exception | 除 RuntimeException 之外的所有异常 | 编译时异常，没有继承 RuntimeException，编译阶段就会出错 |

## 编译时异常与运行时异常

编译时异常：没有继承 RuntimeException 的异常，编译阶段就会出错，必须处理。

运行时异常：继承自 RuntimeException 的异常或其子类，编译阶段不报错，运行期才可能抛错。

## try-catch 捕获异常

try-catch 的好处是异常对象可以被捕获，后续的代码可以继续执行。

```java
try {
    // 可能会出现异常的代码
} catch (异常类型 对象名) {
    // 异常的处理方案
}
```

执行流程：

1. 执行 try 中的代码，看是否有异常对象产生。
2. 没有异常：catch 就不会捕获，后续代码继续执行。
3. 有异常：catch 捕获异常对象，执行 catch 中的处理方案，后续代码继续执行。

可以搭配 finally 块，无论是否发生异常都会执行：

```java
try {
    int a = 10 / 0;
} catch (ArithmeticException e) {
    e.printStackTrace();
} finally {
    System.out.println("无论是否异常都会执行");
}
```

## throws 声明

throws 用在方法名后面，起到声明作用，声明此方法中存在异常，调用者需要进行处理。

```java
public void setAge(int age) throws Exception {
    if (age >= 0 && age <= 120) {
        this.age = age;
    } else {
        throw new Exception("年龄范围有误，需要0~120之间的年龄");
    }
}
```

## throw 与 throws 的区别

throw 用在方法中，后面跟的是异常对象，作用是抛出异常对象。

throws 用在方法名后面，起到声明作用，声明此方法中存在异常，调用者需要处理。

细节：抛出的异常对象如果是编译时异常，必须使用 throws 声明；如果是运行时异常，则不需要写 throws。

```java
while (true) {
    try {
        age = Integer.parseInt(sc.nextLine());
        stu.setAge(age);
        break;
    } catch (NumberFormatException e) {
        System.out.println("年龄输入有误，请重新输入整数年龄：");
    } catch (Exception e) {
        String message = e.getMessage();
        System.out.println(message);
    }
}
```

## Throwable 的常用方法

| 方法 | 说明 |
|---|---|
| public String getMessage() | 获取异常的错误原因 |
| public void printStackTrace() | 展示完整的异常错误信息 |

## 自定义异常

自定义编译时异常：定义一个异常类继承 Exception，并重写构造器。

自定义运行时异常：定义一个异常类继承 RuntimeException，并重写构造器。

```java
// 自定义编译时异常
public class AgeOutOfBoundsException extends Exception {
    public AgeOutOfBoundsException() {
    }
    public AgeOutOfBoundsException(String message) {
        super(message);
    }
}

// 自定义运行时异常
public class AgeIllegalException extends RuntimeException {
    public AgeIllegalException(String message) {
        super(message);
    }
}
```

## 重写方法的异常限制

子类重写父类方法时，不能抛出父类没有的异常，或者比父类更大的异常，即子类声明的异常列表不能超出父类声明范围，更不能抛出父类方法没有抛出的检查异常。

## 小结

异常体系以 Throwable 为根，Error 表示严重系统问题，Exception 又分编译时异常与运行时异常。try-catch 捕获让代码继续运行，finally 无论是否异常都会执行。throw 抛对象、throws 做声明，两者常搭配使用，注意编译时异常必须写在方法签名上。自定义异常继承 Exception 或 RuntimeException 重写构造器，且重写父类方法时不能抛出更大范围的异常。

