---
category: Java
category_slug: java
topic: 常用类
topic_slug: common-class
title: Object、toString 与 equals
slug: object-tostring-equals
tags: [Object, toString, equals, Objects]
summary: 所有类都继承 Object。toString 默认打印"类名@哈希"、equals 默认按引用比较，重写才能得到预期行为；Objects 工具类提供更稳的判空比较。
order: 10
---

# Object、toString 与 equals

Java 里所有类都直接或间接继承 `Object` 类，因此 `toString`、`equals`、`hashCode` 这些方法天然存在。但它们的默认实现常常不符合业务预期，需要重写。

## 默认 toString

`Object.toString()` 默认返回 `类名 + @ + 十六进制哈希` 这样的字符串，本质是"对象在内存中的身份标识"。

```java
User u = new User("张三");
System.out.println(u);
// 没有重写 toString 时：com.demo.User@1b6d3586
```

只要类里**没有重写** `toString`，打印出来的就是这种"包名 + @ + 地址"的形式。重写之后，打印的才是自定义内容。

```java
@Override
public String toString() {
    return "User{name='" + name + "'}";
}
```

## 默认 equals 与重写

`Object.equals` 默认按**引用地址**比较，即只有同一个对象才相等。业务上通常希望"内容相同就相等"，所以要重写 equals，并同步重写 hashCode（二者的契约是"equals 相等则 hashCode 必须相等"）。

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return Objects.equals(name, user.name);
}
```

判断两个对象是否相等时，如果没重写 equals，就跟用 `==` 比较引用一样，往往得到 false。

## Objects 工具类

`Objects.equals(a, b)` 是 JDK 提供的空指针安全比较：它内部先判空，避免 `a.equals(b)` 在 a 为 null 时抛出 `NullPointerException`。

```java
Objects.equals(null, null);   // true
Objects.equals("a", "a");     // true
Objects.equals("a", null);    // false
```

用 `Objects.equals` 代替直接调用 equals，是最稳妥的写法。重写 equals 时也常配合 `Objects.equals` 比较字段。

## 小结

`Object` 是每个类的根基。判断输出是否重写了 `toString`（看有没有 `@` 地址）、判断相等是否重写了 `equals`（看是不是内容比较），是排查这类 Bug 的直觉。引用比较用 `==`、内容比较用重写的 `equals`、判空比较用 `Objects.equals`，三者别混用。
