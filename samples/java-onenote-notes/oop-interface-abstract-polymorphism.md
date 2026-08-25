---
category: Java
category_slug: java
topic: 面向对象
topic_slug: oop
title: 接口、抽象类与多态
slug: oop-interface-abstract-polymorphism
tags: [面向对象, 抽象类, 接口, 多态]
summary: 讲清包与导包，抽象类与抽象方法的定义和使用，interface 的成员特点，抽象类与接口的对比，以及多态的前提、成员访问与 instanceof 转型。
order: 10
spec_version: v1
---

# 接口、抽象类与多态

本篇梳理面向对象中支撑"面向抽象编程"的三个核心：包与导包、抽象类、接口，以及在此基础上形成的多态。

## 包与导包

包本质上就是文件夹，用来管理类文件，避免类名冲突。建包语法格式为 `package 公司域名倒写.技术名称`，包名建议全部英文小写且具备意义。建包语句必须放在文件第一行，一般 IDEA 工具会帮助创建。

```java
package com.itheima.domain;

public class Student {
}
```

相同包下的类可以直接访问，不同包下的类必须导包才可以使用。导包格式为 `import 包名.类名`。

```java
import com.itheima.domain.Student;

Student s = new Student();
```

假如一个类中需要用到两个同名类，那么默认只能导入一个，另一个要带包名访问。

## 抽象方法与抽象类

将共性的行为（方法）抽取到父类之后，如果该方法的实现逻辑无法在父类中给出具体明确，就应该定义为抽象方法。如果一个类中存在抽象方法，那么该类就必须声明为抽象类。

抽象方法的定义格式：

```java
public abstract void eat();
```

抽象类的定义格式：

```java
public abstract class Animal {
    public abstract void eat();
}
```

抽象类的注意事项：

1. 抽象类不能实例化：如果允许创建对象，就可以调用内部没有方法体的抽象方法了。
2. 抽象类存在构造方法：交给子类，通过 super 进行访问。
3. 抽象类中可以存在普通方法：让子类继承后继续使用。
4. 抽象类的子类：要么重写抽象类中的所有抽象方法，要么也是抽象类。

## abstract 关键字的冲突

`abstract` 强制要求子类重写，因此与下面几个关键字冲突：

| 关键字 | 冲突原因 |
|---|---|
| `final` | 被 `abstract` 修饰的方法强制子类重写，被 `final` 修饰的方法子类不能重写 |
| `private` | 被 `abstract` 修饰的方法强制子类重写，被 `private` 修饰的方法子类不能重写 |
| `static` | 被 `static` 修饰的方法可以类名调用，类名调用抽象方法没有意义 |

## 接口

接口用关键字 `interface` 定义，不能实例化。接口和类之间是实现关系，通过 `implements` 关键字表示。

```java
public interface Swimmable {
    void swim();
}
```

```java
public class Fish implements Swimmable {
    @Override
    public void swim() {
        System.out.println("鱼在游");
    }
}
```

接口的子类（实现类）：要么重写接口中的所有抽象方法，要么是抽象类。

接口的成员特点：

| 内容 | 特点 |
|---|---|
| 成员变量 | 只能定义常量，系统默认加入 `public static final`，这三个关键字没有顺序关系 |
| 成员方法 | 只能是抽象方法，系统默认加入 `public abstract` |
| 构造方法 | 没有 |

接口和类之间的各种关系：

1. 类与类之间：继承关系，只支持单继承，不支持多继承，但可以多层继承。
2. 类与接口之间：实现关系，可以单实现，也可以多实现，甚至可以在继承一个类的同时实现多个接口。
3. 接口与接口之间：继承关系，可以单继承，也可以多继承。

## 抽象类和接口的对比

| 成员 | 抽象类 | 接口 |
|---|---|---|
| 成员变量 | 可以定义变量，也可以定义常量 | 只能定义常量 |
| 成员方法 | 可以定义具体方法，也可以定义抽象方法 | 只能定义抽象方法 |
| 构造方法 | 有 | 没有 |

## 多态

多态是指同一个行为具有多个不同表现形式或形态的能力。

多态的前提：

1. 有继承 / 实现关系。
2. 有方法重写。
3. 有父类引用指向子类对象。

业务层用一个 `OrderService` 类型引用，分别指向国内订单与国外订单的实现类，运行时再决定实际调用的对象。

```java
import java.util.Scanner;

public class Test {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入：1.国内订单  2.国外订单");
        OrderService orderService = null;
        int choice = sc.nextInt();
        switch (choice) {
            case 1:
                orderService = new OrderServiceImpl();
                break;
            case 2:
                orderService = new OverseasServiceImpl();
                break;
        }
        orderService.create();
        orderService.findOne();
        orderService.findList();
        orderService.cancel();
        orderService.finish();
        orderService.paid();
    }
}
```

多态的成员访问特点：

1. 成员变量：编译看左边（父类），运行看左边（父类）。
2. 成员方法：编译看左边（父类），运行看右边（子类）。

编译的时候会检查父类中有没有这个方法：没有则编译出错，有则编译通过，但运行时一定执行子类的方法逻辑。原因是担心你调用的方法在父类中是一个抽象方法。

多态创建对象后调用静态成员时，推荐用类名调用。静态成员也可以用对象名调用，但这只是一种假象：生成字节码文件后，会自动把对象名调用改成类名调用。

多态中的转型判断，用关键字 `instanceof`：

```java
对象名 instanceof 类型
```

它判断一个对象是否是一个类的实例，通俗地理解就是判断左边的对象是否是右边的类型，返回 boolean 类型结果。

## 小结

抽象类和接口是面向抽象编程的基石：抽象类更适合"是"的关系和共享代码，接口更适合能力约定。多态让父类引用指向子类对象，从而在运行时动态调用子类的实现。
