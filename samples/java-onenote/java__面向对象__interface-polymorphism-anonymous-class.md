---
category: Java
category_slug: java
topic: 面向对象
topic_slug: oop
title: 接口、多态与匿名内部类
slug: interface-polymorphism-anonymous-class
tags: [接口, 多态, 内部类, 匿名内部类]
summary: 接口定义行为规范，多态让父类型引用指向子类对象，匿名内部类与多态结合可临时实现接口。含接口默认方法用"接口名.super"调用的细节。
order: 10
---

# 接口、多态与匿名内部类

接口与多态是 Java 面向对象的两个核心概念。接口约定"能做什么"，多态让"同一个调用在不同对象上有不同表现"。

## 接口

接口是一种完全抽象的引用类型，只定义方法的签名（抽象方法），不提供具体实现，由实现类去落地。Java 8 之后接口还能有默认方法与静态方法。

```java
public interface Payable {
    void pay(double amount);                 // 抽象方法，实现类必须重写
    default void showBalance() {             // 默认方法，实现类可选重写
        System.out.println("余额未知");
    }
}
```

一个类用 `implements` 实现接口，可以同时实现多个接口；接口之间也可以用 `extends` 继承多个接口。

## 多态

多态指"父类型引用指向子类对象"，程序在运行时调用的是子类重写后的方法。前提有三个：继承关系、方法重写、父类引用指向子类对象。

```java
Payable p = new CreditCard();   // 接口引用指向实现类对象
p.pay(99.5);                     // 实际执行 CreditCard 的 pay
```

向上转型让代码面向接口编程、解耦依赖；向下转型要先用 `instanceof` 判断，否则可能 `ClassCastException`。多态也让方法的形参写成接口类型，从而能接收任意实现类。

## 调用接口的默认方法

当子类重写了接口的默认方法后，如果还想去调用接口那份原始实现，不能用 `super`，而要"接口名 + super"。

```java
public interface A {
    default void say() { System.out.println("A"); }
}
public class B implements A {
    @Override
    public void say() {
        A.super.say();          // 调用接口 A 的默认方法，而不是父类 super
        System.out.println("B");
    }
}
```

`接口名.super.方法名` 是 Java 处理"接口默认方法冲突/回调"的专用语法，和类继承里的 `super.方法名` 是两回事。

## 内部类与匿名内部类

内部类是定义在类内部的类，按位置分成员内部类、局部内部类、匿名内部类。匿名内部类没有名字，通常用于一次性实现接口或抽象类，和多态联系紧密——当形参是接口类型时，直接传一个匿名内部类实例即可。

```java
// 形参是接口类型，传入匿名内部类实现
public void doPay(Payable p) { p.pay(10); }

doPay(new Payable() {
    @Override
    public void pay(double amount) {
        System.out.println("匿名支付 " + amount);
    }
});
```

匿名内部类只能使用一次，适合回调、事件监听这类只用一个实现的场景。Java 8 之后能用 Lambda 的场合，往往会用 Lambda 替代匿名内部类，更简洁。

## import 通配符

`import java.util.*;` 中的 `*` 表示导入 `java.util` 包下的**所有类**，但不包括子包。它只用于导入包里的类，不用于直接导入某个类。工程上更推荐逐个类显式导入，避免同名类冲突。

## 小结

接口定义契约、多态实现灵活调用、匿名内部类/内部类组织代码，三者共同支撑"面向接口编程"。记住接口默认方法用 `接口名.super` 调用、匿名内部类依赖多态的形参接口类型，是这块最容易出错的两个点。
