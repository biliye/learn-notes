---
category: Java
category_slug: java
topic: 面向对象
topic_slug: oop
title: 代码块、内部类、匿名内部类与 Lambda
slug: oop-codeblock-inner-lambda
tags: [面向对象, 内部类, Lambda, 代码块]
summary: 讲清 JDK8/9 接口的默认方法、静态方法与私有方法，局部/构造/静态三种代码块，成员内部类与静态内部类的创建，匿名内部类的格式，以及 Lambda 与函数式接口的关系和省略写法。
order: 20
spec_version: v1
---

# 代码块、内部类、匿名内部类与 Lambda

本篇围绕"类与代码的组织方式"展开：接口在 JDK8/9 之后的新特性、三种代码块、内部类与匿名内部类，以及用来简化匿名内部类的 Lambda 表达式。

## JDK8 接口特性：默认方法

JDK8 起允许在接口中定义非抽象方法，但需要使用 `default` 修饰，这些方法就是默认方法。作用：解决接口升级的问题。

接口中默认方法的定义格式：

```java
public default 返回值类型 方法名(参数列表) {
}
```

示例：

```java
public interface Swimmable {
    public default void show() {
        System.out.println("默认方法");
    }
}
```

注意事项：

1. 默认方法不是抽象方法，所以不强制被重写，但可以被重写，重写的时候要去掉 `default` 关键字。
2. `public` 可以省略，但是 `default` 不能省略。
3. 如果实现了多个接口，且多个接口中存在相同的方法声明，子类就必须对该方法进行重写。

## JDK8 接口特性：静态方法

接口中允许定义 `static` 静态方法，可以类名调用。既然接口已经允许方法带有方法体了，放开静态方法可以让调用更简洁，无需借助实现类。

接口中静态方法的定义格式：

```java
public static 返回值类型 方法名(参数列表) {
}
```

示例：

```java
public interface Swimmable {
    public static void show() {
        System.out.println("静态方法");
    }
}
```

注意事项：

1. 静态方法只能通过接口名调用，不能通过实现类名或者对象名调用。
2. `public` 可以省略，但是 `static` 不能省略。

## JDK9 接口特性：私有方法

JDK9 允许在接口中定义 `private` 私有方法，用于把默认方法或静态方法中重复的逻辑抽取出来复用。

两种格式：

```java
private 返回值类型 方法名(参数列表) {
}
```

```java
private static 返回值类型 方法名(参数列表) {
}
```

私有方法只能被接口内部的其他方法调用，不能被实现类调用。

## 代码块

使用 `{}` 括起来的代码被称为代码块，分为三种。

局部代码块：在方法中定义，作用是限定变量的生命周期，及早释放，提高内存利用率。

构造代码块：在类中方法外定义，特点是每次构造方法执行的时候都会执行该代码块中的代码，并且在构造方法执行前执行。作用是将多个构造方法中相同的代码抽取到构造代码块中，提高代码复用性。

静态代码块：在类中方法外定义，需要通过 `static` 关键字修饰，随着类的加载而加载，并且只执行一次。作用是在类加载的时候做一些数据初始化操作。

```java
public class Demo {
    static {
        System.out.println("静态代码块：类加载时执行一次");
    }

    {
        System.out.println("构造代码块：每次构造前执行");
    }

    public Demo() {
        System.out.println("构造方法");
    }

    public void test() {
        {
            int x = 10;
            System.out.println("局部代码块：x = " + x);
        }
    }
}
```

## 内部类

内部类就是定义在一个类里面的类。

创建对象的格式：

```java
外部类名.内部类名 对象名 = new 外部类对象().new 内部类对象();
```

示例：

```java
Outer.Inner in = new Outer().new Inner();
```

成员访问细节：

1. 内部类中访问外部类成员：直接访问，包括私有成员。
2. 外部类中访问内部类成员：需要创建对象访问。

```java
public class Outer {
    private int num = 10;

    class Inner {
        public void show() {
            System.out.println(num);
        }
    }
}
```

静态内部类是 `static` 修饰的成员内部类。

创建对象格式：

```java
外部类名.内部类名 对象名 = new 外部类名.内部类对象();
```

注意事项：静态只能访问静态。

## 匿名内部类

匿名内部类本质上是一个特殊的局部内部类（定义在方法内部），前提是需要存在一个接口或类。

格式：

```java
new 类名/接口名() {
}
```

`new 类名(){}` 代表继承这个类，`new 接口名(){}` 代表实现这个接口。匿名内部类可以使代码更加简洁，定义一个类的同时对其进行实例化。它与多态的联系在于：父类引用或接口引用指向匿名子类对象。

```java
Swimmable s = new Swimmable() {
    @Override
    public void swim() {
        System.out.println("鱼儿在游泳");
    }
};
s.swim();
```

## Lambda 表达式

Lambda 表达式是 JDK8 开始的一种新语法形式，作用是简化匿名内部类的代码写法。

Lambda 表达式的简化格式：

```java
(匿名内部类被重写方法的形参列表) -> {
    被重写方法的方法体代码
}
```

`->` 是语法形式，无实际含义。注意：Lambda 表达式只能简化函数式接口的匿名内部类写法。

什么是函数式接口？首先必须是接口，其次接口中有且仅有一个抽象方法。通常会在接口上加上 `@FunctionalInterface` 注解，标记该接口必须满足函数式接口的条件。

```java
@FunctionalInterface
public interface Calculator {
    int calc(int a, int b);
}
```

示例：用 Lambda 替代匿名内部类。

```java
public class LambdaTest {
    public static void main(String[] args) {
        useCalculator(new Calculator() {
            @Override
            public int calc(int a, int b) {
                return a + b;
            }
        });

        useCalculator((a, b) -> a - b);
    }

    public static void useCalculator(Calculator calculator) {
        int result = calculator.calc(10, 20);
        System.out.println(result);
    }
}
```

Lambda 表达式的省略写法：

1. 参数类型可以省略不写。
2. 如果只有一个参数，参数类型可以省略，同时 `()` 也可以省略。
3. 如果 Lambda 表达式的方法体代码只有一行，可以省略大括号不写，同时要省略分号。此时，如果这行代码是 `return` 语句，必须省略 `return` 不写，同时也必须省略 `;` 不写。

## 小结

JDK8 和 JDK9 让接口从"纯抽象"走向"可提供实现"，为默认方法、静态方法和私有方法提供了分工。代码块按所在位置不同承担不同的职责。内部类与匿名内部类让类与对象的组织更灵活，而 Lambda 用最简化的语法接管了函数式接口的匿名内部类写法。
