---
category: Java
category_slug: java
topic: IO
topic_slug: io
title: IO 流、try-with-resources 与序列化
slug: io-stream-serialization
tags: [IO, 序列化, Serializable, transient, 编码]
summary: IO 流读写文件，不存在会自动创建文件但不会建文件夹；try-with-resources 自动 close；对象序列化要实现 Serializable，transient 字段不参与序列化。
order: 10
---

# IO 流、try-with-resources 与序列化

IO 流负责读写文件、网络等数据，序列化负责把对象转成字节、或还原回来。这两块是 Java 输入输出的核心，也各有容易出错的地方。

## 读写文件

用字节流（`FileInputStream`/`FileOutputStream`）或字符流（`FileReader`/`FileWriter`）读写文件，常包一层缓冲流提升性能。

```java
try (BufferedReader br = new BufferedReader(new FileReader("a.txt"))) {
    String line;
    while ((line = br.readLine()) != null) {
        System.out.println(line);
    }
}
```

IO 流在写文件时，如果目标文件**不存在会自动创建该文件，但不能创建文件夹**——目录必须先手动建好，否则会抛 `FileNotFoundException`。

## 字符与编码

字符流会按编码格式把一个字符（如中文）换算成字节再读写。编码格式不统一（比如读用 GBK、写用 UTF-8）会导致乱码。转成字节数组时同样要指定编码，默认是平台编码，容易踩坑。

```java
byte[] bytes = "你好".getBytes(StandardCharsets.UTF_8);
String back = new String(bytes, StandardCharsets.UTF_8);
```

## try-with-resources 自动关闭

把资源创建写在 `try(...)` 的小括号里，Java 会在语句块结束时**自动调用 close**，无需手动 finally。

```java
try (BufferedReader br = Files.newBufferedReader(Path.of("a.txt"))) {
    String line = br.readLine();
    // 结束时自动 close
}
```

"这样写可以自动调用 close 的代码"，指的就是 try-with-resources。前提是资源实现了 `AutoCloseable` 接口（IO 流都是）。

## 对象序列化

把**对象**写成字节流需要让类实现 `Serializable` 接口。序列化会生成一个序列号（serialVersionUID），**建议写死**——否则类结构一改，序列号变掉，反序列化会失败。

```java
public class User implements Serializable {
    private static final long serialVersionUID = 1L;   // 建议写死
    private String name;
    private transient String secret;    // transient 字段不参与序列化
}
```

`transient` 加在字段前，这个字段就**不参与序列化**（如密码、敏感数据）。"只想读和写一次的话，可以把对象扔进集合当中"——指把多个对象放进集合再序列化整个集合，读写一次即可；追加写入则要用追加模式的输出流。

## 小结

IO 读文件自动建文件但不建目录；字符流要统一编码；`try(...)` 语法自动 close；对象实现 `Serializable` 且 `serialVersionUID` 写死；`transient` 排除敏感字段。这几条覆盖了 IO 与序列化的主要考点。
