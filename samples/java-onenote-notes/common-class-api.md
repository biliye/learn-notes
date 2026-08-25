---
category: Java
category_slug: java
topic: 常用类
topic_slug: common-class
title: 常用类 API：Object、包装类、Math、System、Arrays、正则
slug: common-class-api
tags: [基础, 常用类, 正则]
summary: 梳理 Object 与 Objects 工具类、包装类与自动拆装箱、Math/System/Arrays 三个工具类，以及 BigDecimal 精确运算与正则表达式的常用用法。
order: 10
---

# 常用类 API：Object、包装类、Math、System、Arrays、正则

这篇笔记整理 Java 基础阶段最常用的几类工具：用于对象比较的 Object 与 Objects，用于基本类型转换的包装类，Math、System、Arrays 三个静态工具类，外加精确小数运算的 BigDecimal 和文本提取的正则表达式。

## Object 类的常用方法

Object 是所有类的父类，最常被重写的方法是 toString、equals 和 hashCode。

toString 默认返回类名加对象哈希值的十六进制字符串：

```java
public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
}
```

其中 getClass().getName() 得到全类名（包名加类名），Integer.toHexString() 转十六进制，hashCode() 是对象地址加哈希算法算出的整数（哈希值）。

细节：使用打印语句打印对象名时，println 方法在源码层面会自动调用该对象的 toString。

equals 默认比较的是对象内存地址：

```java
public boolean equals(Object obj) {
    return this == obj;
}
```

通常会重写 equals，让对象之间比较内容。重写一个标准流程如下：

```java
public boolean equals(Object o) {
    if (this == o) {
        return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
        return false;
    }
    Student other = (Student) o;
    return this.age == other.age && this.name.equals(other.name);
}
```

重写 equals 一定要同时重写 hashCode，否则在 HashMap 等依赖哈希的结构里行为会不一致。

## Objects 工具类

Objects 工具类封装了一些对 null 友好的静态方法，最常用的是 equals 和 isNull。

```java
public static boolean equals(Object a, Object b)
public static boolean isNull(Object obj)
```

Objects.equals 方法内部依赖我们自己所编写的 equals，好处是它内部带有非 null 判断，可以避免空指针异常：

```java
Student stu1 = null;
Student stu2 = new Student("张三", 23);
System.out.println(Objects.equals(stu1, stu2));
```

## Math 类

Math 类包含执行基本数字运算的方法，全部是静态的，直接类名调用。

| 方法 | 说明 |
|---|---|
| public static int abs(int a) | 获取参数绝对值 |
| public static double ceil(double a) | 向上取整 |
| public static double floor(double a) | 向下取整 |
| public static int round(float a) | 四舍五入 |
| public static int max(int a, int b) | 获取两个 int 值中的较大值 |
| public static double pow(double a, double b) | 返回 a 的 b 次幂 |
| public static double random() | 返回 double 随机值，范围 0.0 到 1.0（含 0 不含 1） |

```java
System.out.println(Math.abs(-3));      // 3
System.out.println(Math.ceil(3.2));    // 4.0
System.out.println(Math.floor(3.8));   // 3.0
System.out.println(Math.round(3.5));   // 4
System.out.println(Math.pow(2, 3));    // 8.0
```

## System 类

System 类的功能是静态的，都是直接用类名调用。

| 方法 | 说明 |
|---|---|
| public static void exit(int status) | 终止当前运行的 Java 虚拟机，非零表示异常终止 |
| public static long currentTimeMillis() | 返回当前系统的时间毫秒值形式 |
| public static void arraycopy(数据源数组, 起始索引, 目的地数组, 起始索引, 拷贝个数) | 数组拷贝 |

```java
long start = System.currentTimeMillis();
// 中间是一段要计时的代码
System.out.println(System.currentTimeMillis() - start);
```

## BigDecimal

BigDecimal 用于解决小数运算中出现的不精确问题。创建对象时，直接 new BigDecimal(double) 无法保证精度，不推荐使用，应优先用字符串构造或 valueOf：

```java
public BigDecimal(String val)
public static BigDecimal valueOf(double val)
```

常用成员方法：

| 方法 | 说明 |
|---|---|
| public BigDecimal add(BigDecimal b) | 加法 |
| public BigDecimal subtract(BigDecimal b) | 减法 |
| public BigDecimal multiply(BigDecimal b) | 乘法 |
| public BigDecimal divide(BigDecimal b) | 除法 |
| public BigDecimal divide(BigDecimal b, int scale, RoundingMode mode) | 除法，指定保留位数与舍入模式 |

注意：如果使用 divide 运算出现除不尽的情况，会抛出异常。此时要指定舍入模式。

```java
BigDecimal bd1 = BigDecimal.valueOf(10.0);
BigDecimal bd2 = BigDecimal.valueOf(3.0);
System.out.println(bd1.divide(bd2, 2, RoundingMode.HALF_UP)); // 3.33
System.out.println(bd1.divide(bd2, 2, RoundingMode.UP));      // 3.34
System.out.println(bd1.divide(bd2, 2, RoundingMode.DOWN));    // 3.33

BigDecimal result = bd1.divide(bd2, 2, RoundingMode.HALF_UP);
double v = result.doubleValue();
```

RoundingMode 的 HALF_UP 表示四舍五入，UP 向远离零方向进位，DOWN 向零方向截断。

## 包装类与自动拆装箱

包装类把基本数据类型包装成类，变成引用数据类型。

手动装箱：调用方法把基本数据类型包装成类，例如 new Integer(int)（不推荐）或 Integer.valueOf(int)。

手动拆箱：调用方法把包装类转回基本数据类型，例如 intValue()。

JDK5 开始出现自动拆装箱，基本类型可以直接赋给包装类变量，反之亦然：

```java
int i = 10;
Integer ii = i;        // 自动装箱
int j = ii;            // 自动拆箱
```

Integer 还有一些常用方法：toBinaryString 转二进制、toOctalString 转八进制、toHexString 转十六进制、parseInt 把数字字符串转成数字。

## IntegerCache 缓存

自动装箱时，如果装箱的数据范围是 -128 到 127，用 == 比较的结果就是 true，反之是 false。

自动装箱的本质是自动调用 Integer.valueOf(i)。Integer 底层存在一个长度为 256 的数组 Integer[] cache，存储了 -128 到 127 共 256 个预创建的 Integer 对象：

```java
public static Integer valueOf(int i) {
    if (i >= -128 && i <= 127) {
        return IntegerCache.cache[i + 128];
    }
    return new Integer(i);
}
```

如果装箱的数据在 -128 到 127 之间，不会创建新对象，而是从底层数组取出提前创建好的对象返回；不在这个范围会重新创建新对象。

## Arrays 类

Arrays 是数组操作工具类，专门用于操作数组元素。

| 方法 | 说明 |
|---|---|
| public static String toString(类型[] a) | 将数组元素拼接为带格式的字符串 |
| public static boolean equals(类型[] a, 类型[] b) | 比较两个数组内容是否相同 |
| public static int binarySearch(int[] a, int key) | 查找元素在数组中的索引（二分查找法，需先排序） |
| public static void sort(类型[] a) | 对数组进行默认升序排序 |

```java
int[] arr = {3, 1, 4, 1, 5};
Arrays.sort(arr);
System.out.println(Arrays.toString(arr));     // [1, 1, 3, 4, 5]
int index = Arrays.binarySearch(arr, 4);
System.out.println(index);                    // 3
```

## 正则表达式

预定义字符类默认匹配一个字符。字符类用中括号表示，中括号内以脱字符开头表示取反（排除某集合），非数字、非空白、非单词常用 \D、\S、\W 简写表示。贪婪的量词配合匹配多个字符：

```text
[abc]         只能是 a、b 或 c
[a-zA-Z]      a 到 z、A 到 Z（范围）
[a-d[m-p]]    a 到 d 或 m 到 p（联合）
[a-z&&[def]]  d、e 或 f（交集）
\d            一个数字，即 [0-9]
\D            非数字
\s            一个空白字符
\S            非空白字符
\w            英文、数字、下划线，即 [a-zA-Z_0-9]
\W            非单词字符
X?            X 一次或根本不
X*            X 零次或多次（任意次数）
X+            X 一次或多次
X{n}          X 正好 n 次
X{n,}         X 至少 n 次
X{n,m}        X 至少 n 但不超过 m 次
```

### Pattern 与 Matcher 爬取信息

使用正则表达式爬取信息时，先编译正则得到 Pattern 对象，再用 matcher 获取匹配器，通过 find 与 group 提取内容。

下面把文本中的手机号、邮箱、座机、热线都爬取出来：

```java
public static void main(String[] args) {
    String data = "来黑马程序员学习Java，电话：18666668888，18699997777" +
            "邮箱：boniu@itcast.cn 邮箱：bozai@itcast.cn" +
            "座机电话：010-36517895，010-98951256" +
            "热线电话：400-618-9090，400-618-4000，4006184000，4006189090";

    String regex = "(1[3-9]\\d{9})"                                  // 手机号
            + "|([a-zA-Z0-9]+@[a-zA-Z0-9]+(\\.[a-zA-Z]+)+)"          // 邮箱
            + "|(0\\d{2,3}-?\\d{7,8})"                                // 座机
            + "|(400-?\\d{3}-?\\d{4})";                               // 热线

    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(data);
    while (matcher.find()) {
        System.out.println(matcher.group());
    }
}
```

## 小结

Object 是根类，equals 与 hashCode 应成对重写，Objects.equals 自带非空判断防灾空指针。包装类让基本类型能进集合，但要留意 IntegerCache 的 -128 到 127 边界。Math、System、Arrays 是静态工具类直接类名调用。BigDecimal 用于金融级精确运算，除不尽时指定舍入模式。正则搭配 Pattern 与 Matcher 可以高效地从文本中提取目标信息。

