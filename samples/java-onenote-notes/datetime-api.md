---
category: Java
category_slug: java
topic: 日期时间
topic_slug: datetime
title: 日期时间：Date、SimpleDateFormat、Calendar 与 JDK8 API
slug: datetime-api
tags: [日期时间, JDK8]
summary: 梳理 Date、SimpleDateFormat、Calendar 等传统日期类，以及 JDK8 新增的 LocalDateTime、DateTimeFormatter、ZoneId、Instant 与 Period、Duration、ChronoUnit。
order: 10
---

# 日期时间：Date、SimpleDateFormat、Calendar 与 JDK8 API

Java 中处理日期时间的类经历了几代演进：早期的 Date 加 SimpleDateFormat，后来抽象出 Calendar 日历类，JDK8 又推出了全新且更清晰的 java.time 包。这篇笔记把三者串起来讲。

## Date 类

Date 代表的是日期和时间。构造方法有两种：

| 构造器 | 说明 |
|---|---|
| public Date() | 创建一个 Date 对象，代表系统当前此刻的日期时间 |
| public Date(long time) | 把时间毫秒值转换成 Date 日期对象 |

常见方法：

| 方法 | 说明 |
|---|---|
| public long getTime() | 返回从 1970 年 1 月 1 日 00:00:00 走到此刻的总毫秒数 |

```java
Date date = new Date();
System.out.println(date);         // 当前日期时间
long millis = date.getTime();
System.out.println(millis);       // 毫秒时间戳
```

## SimpleDateFormat 类

SimpleDateFormat 用于日期格式化。构造方法有两种：new SimpleDateFormat() 使用默认模式，new SimpleDateFormat(String pattern) 手动指定模式。常用方法有 format 把日期对象转成字符串，parse 把日期字符串解析回日期对象。

常用模式字母如下：

| 字母 | 含义 | 示例 |
|---|---|---|
| G | Era 标志符 | AD |
| y | 年 | 1996 |
| M | 年中的月份 | July / Jul / 07 |
| w | 年中的周数 | 27 |
| d | 月份中的天数 | 10 |
| E | 星期中的天数 | Tuesday / Tue |
| a | Am/pm 标记 | PM |
| H | 一天中的小时数（0-23） | 0 |
| h | am/pm 中的小时数（1-12） | 12 |
| m | 小时中的分钟数 | 30 |
| s | 分钟中的秒数 | 55 |
| S | 毫秒数 | 978 |

```java
public static void main(String[] args) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss E");
    Date date = new Date();
    String result = sdf.format(date);   // 日期对象转字符串
    System.out.println(result);

    String time = "2008年08月08日 12:30:00 周五";
    Date parsed = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss E").parse(time);
    System.out.println(parsed);
}
```

解析字符串用的是同一个模式，模式字母必须和字符串内容一一对应，否则会抛 ParseException。

## Calendar 类

Calendar 是一个抽象类，不能直接创建对象，需要通过静态工厂方法 getInstance() 获取当前时间的日历对象。

获取日历字段时用 get 方法，参数是 Calendar 类中的静态常量：

```java
Calendar c = Calendar.getInstance();
int year = c.get(Calendar.YEAR);
int month = c.get(Calendar.MONTH) + 1;  // 月份是 0~11，记得做 +1 操作
int day = c.get(Calendar.DAY_OF_MONTH);
int week = c.get(Calendar.DAY_OF_WEEK);  // 星期日是 1，需要提前设计一个数组
int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
```

其他常用方法：

| 方法 | 说明 |
|---|---|
| public int get(int field) | 取日历中的某个字段信息 |
| public void set(int field, int value) | 修改日历的某个字段信息 |
| public void add(int field, int amount) | 为某个字段增加或减少指定的值 |
| public final Date getTime() | 获取日期对象 |
| public final void setTime(Date date) | 给日历设置日期对象 |

```java
Calendar c = Calendar.getInstance();
c.set(Calendar.YEAR, 2026);
c.add(Calendar.MONTH, -1);      // 减一个月
```

## JDK8 时间新 API

java.time 包里的类都是不可变的，修改只会返回新对象。创建对象的两种方式为 now() 取当前时间和 of(...) 设置时间。

### LocalDateTime、LocalDate、LocalTime

```java
LocalDateTime now = LocalDateTime.now();
LocalDate date = LocalDate.now();
LocalTime time = LocalTime.now();

LocalDate custom = LocalDate.of(2023, 2, 4);
LocalDateTime dt = LocalDateTime.of(2023, 2, 4, 0, 0, 0);

// LocalDateTime 转换成 LocalDate、LocalTime
LocalDate d = now.toLocalDate();
LocalTime t = now.toLocalTime();
```

LocalDateTime、LocalDate、LocalTime 都是不可变的，下列方法返回一个新的对象：

| 方法 | 说明 |
|---|---|
| withHour、withMinute、withSecond、withNano | 修改时间，返回新时间对象 |
| plusHours、plusMinutes、plusSeconds、plusNanos | 把某个信息加多少，返回新时间对象 |
| minusHours、minusMinutes、minusSeconds、minusNanos | 把某个信息减多少，返回新时间对象 |
| equals、isBefore、isAfter | 判断两个时间对象是否相等、在前还是在后 |

### DateTimeFormatter

DateTimeFormatter 用于时间的格式化和解析，通过 ofPattern(格式) 获取格式对象，再用 format(时间对象) 按指定方式格式化。

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
String result = formatter.format(now);
System.out.println(result);

LocalDateTime parsed = LocalDateTime.parse("2008年08月08日 12:30:00", formatter);
```

### ZoneId 时区

ZoneId 是时区类，常见方法有 getAvailableZoneIds 获取 Java 支持的所有时区，systemDefault 获取系统默认时区，of(String zoneId) 获取一个指定时区。

### Instant 时间戳

Instant 用于表示时间的对象，类似之前的 Date。now() 获取当前标准时间，atZone(ZoneId) 指定时区转为 ZonedDateTime，plusXxx、minusXxx 做增减。

### ZonedDateTime 带时区时间

ZonedDateTime 是带时区的时间对象，now() 获取当前时间的 ZonedDateTime，ofXxx(...) 指定时间，withXxx、minusXxx、plusXxx 做修改。

```java
Instant instant = Instant.now();
ZonedDateTime zdt = instant.atZone(ZoneId.of("Asia/Shanghai"));
System.out.println(zdt);
```

## Period、Duration 与 ChronoUnit

Period 对象表示时间间隔，between(开始, 结束) 中第二个参数减第一个参数，适用于年月日。

```java
LocalDate today = LocalDate.now();
LocalDate other = LocalDate.of(2023, 2, 4);
Period period = Period.between(other, today);
System.out.println(period.getYears());
System.out.println(period.getMonths());
System.out.println(period.getDays());
System.out.println(period.toTotalMonths());
```

Duration 适用于时分秒毫秒纳秒的时间间隔。

```java
LocalDateTime today = LocalDateTime.now();
LocalDateTime other = LocalDateTime.of(2023, 2, 4, 0, 0, 0);
Duration duration = Duration.between(other, today);   // 第二个参数减第一个参数
System.out.println(duration.toDays());
System.out.println(duration.toHours());
System.out.println(duration.toMinutes());
System.out.println(duration.toMillis());
System.out.println(duration.toNanos());
```

ChronoUnit 是枚举，用 between 计算任意时间单位之差。

```java
LocalDateTime birth = LocalDateTime.of(2023, 2, 4, 0, 0, 0);
LocalDateTime nowTime = LocalDateTime.now();
System.out.println(ChronoUnit.YEARS.between(birth, nowTime));
System.out.println(ChronoUnit.MONTHS.between(birth, nowTime));
System.out.println(ChronoUnit.WEEKS.between(birth, nowTime));
System.out.println(ChronoUnit.DAYS.between(birth, nowTime));
System.out.println(ChronoUnit.HOURS.between(birth, nowTime));
```

## 小结

老 API 里 Date 用长毫秒值表示，SimpleDateFormat 负责文本与日期互转，Calendar 是抽象类靠 getInstance 获取。要留意 Calendar 月份是 0~11、星期日是 1 这两个坑。JDK8 的 java.time 不可变且更清晰：Period 管年月日、Duration 管时分秒，ChronoUnit 枚举做任意单位差值，ZoneId、Instant、ZonedDateTime 处理时区。

