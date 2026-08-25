---
category: Java
category_slug: java
topic: 日期时间
topic_slug: datetime
title: Calendar 与 JDK8 日期时间 API
slug: calendar-and-jdk8-time
tags: [Calendar, LocalDateTime, DateTimeFormatter, Instant]
summary: Calendar 是遗留的日期类，用静态方法创建且是多态实例；JDK8 的 LocalDateTime 配合 DateTimeFormatter 格式化，Instant 处理时区。
order: 10
---

# Calendar 与 JDK8 日期时间 API

Java 的日期时间 API 经历过一次大换血：旧版 `Calendar` 设计别扭，JDK8 推出了 `LocalDateTime` 这一套不可变的、线程安全的新 API。两者都值得了解。

## Calendar

`Calendar` 是个抽象类，通过**静态方法** `getInstance()` 创建对象，返回的是其子类实例，这本身就是一种多态。

```java
Calendar cal = Calendar.getInstance();   // 静态方法创建，实际是子类对象
int day = cal.get(Calendar.DAY_OF_MONTH);
cal.set(Calendar.YEAR, 2026);            // 也可以直接改年月日
```

`Calendar` 里星期的取值比较反直觉：**星期日返回 1，星期六返回 7**，从周日开始编号，而不是从周一开始。

```java
int dow = cal.get(Calendar.DAY_OF_WEEK);   // 周日=1 ... 周六=7
```

## JDK8 新 API：LocalDateTime / LocalDate

`LocalDateTime` 包含年月日时分秒，`LocalDate` 只包含年月日。它们用**类名直接创建实例**，配合 `DateTimeFormatter` 做格式化。

```java
LocalDateTime now = LocalDateTime.now();
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
System.out.println(now.format(fmt));      // 格式化：时间对象 -> 字符串
```

要把**字符串变回对象**，调用 `LocalDateTime.parse`，同样需要传入 `DateTimeFormatter` 格式化对象。解析时**必须包含年月日时分秒**；`LocalDate` 则只需年月日。

```java
LocalDateTime t = LocalDateTime.parse("2026-08-25 12:00:00", fmt);
LocalDate d = LocalDate.parse("2026-08-25", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
```

## Instant 与时区

`Instant` 表示时间轴上的一个瞬间，只描述一个时间点，**本身不携带时区**。若想获取某个时区下的时间，要调用 `Instant` 的 `atZone`（笔记里记为 ztZone）方法，传入时区得到 `ZonedDateTime`。

```java
Instant now = Instant.now();
ZonedDateTime z = now.atZone(ZoneId.of("Asia/Shanghai"));   // 拿到上海时区的时间
```

## 小结

旧 `Calendar` 用静态工厂方法创建子类对象、周日从 1 开始，是它的两个记忆点；新 API 用 `LocalDateTime` + `DateTimeFormatter` 做格式化与解析，解析时必须含年月日时分秒，`Instant` 表示瞬间、配 `atZone` 转时区。新老 API 勿混用。
