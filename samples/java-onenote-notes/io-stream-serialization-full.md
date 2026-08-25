---
category: Java
category_slug: java
topic: IO
topic_slug: io
title: IO 流体系与序列化
slug: io-stream-serialization-full
tags: [IO, 流, 序列化, Properties]
summary: 梳理字节流与字符流的体系结构，掌握文件流、缓冲流、转换流、打印流与对象序列化。
order: 20
---

# IO 流体系与序列化

IO 流是 Java 读写数据的通道。它按方向分为输入流和输出流，按单位分为字节流和字符流。字节流以字节为单位，字符流以字符为单位，字符流是专门为解决中文乱码而提供的。

## IO 流体系结构

字节流的抽象父类是 `InputStream` 和 `OutputStream`，字符流的抽象父类是 `Reader` 和 `Writer`。常用的子类分别是 `FileInputStream`、`FileOutputStream`、`FileReader`、`FileWriter`。

```text
字节流
  抽象类 InputStream          字节输入流
    FileInputStream           文件字节输入流
  抽象类 OutputStream         字节输出流
    FileOutputStream          文件字节输出流
字符流
  抽象类 Reader               字符输入流
    FileReader                文件字符输入流
  抽象类 Writer               字符输出流
    FileWriter                文件字符输出流
```

## 字节输出流 FileOutputStream

`FileOutputStream` 关联文件时，如果文件不存在会自动创建；如果存在会清空现有内容再开始写入。构造方法中提供第二个 `boolean` 参数作为追加写入的开关。

```java
public static void main(String[] args) throws IOException {
    // true 表示追加写入
    FileOutputStream fos = new FileOutputStream("D:\\A.txt", true);
    fos.write("你好".getBytes());
    fos.close();
}
```

常用方法有 `write(int b)` 写出单个字节，`write(byte[] b)` 写出一个字节数组，`write(byte[] b, int off, int len)` 写出字节数组的一部分。

## 字节输入流 FileInputStream

`FileInputStream` 用于读取字节。常见写法是准备一个字节数组作为缓冲区，循环读取，`read` 返回 `-1` 表示读到末尾，再用 `new String(byte[], 0, len)` 把读到的字节转成字符。

```java
public static void main(String[] args) throws IOException {
    FileInputStream fis = new FileInputStream("D:\\A.txt");
    byte[] bys = new byte[1024];
    int len;
    while ((len = fis.read(bys)) != -1) {
        // 每次只把实际读到的 len 个字节转为字符串
        System.out.print(new String(bys, 0, len));
    }
    fis.close();
}
```

## 字符输入流 FileReader

`FileReader` 用于读取纯文本文件，解决中文乱码问题。`read()` 读取单个字符，`read(char[] cbuf)` 读取一个字符数组并返回有效字符个数。中文字符通常由负数的字节组成，且第一个字节肯定是负数。

```java
public static void main(String[] args) throws IOException {
    FileReader fr = new FileReader("D:\\A.txt");
    int b;
    while ((b = fr.read()) != -1) {
        System.out.print((char) b);
    }
    fr.close();
}
```

## 字符输出流 FileWriter

`FileWriter` 用于写出字符，构造方法支持追加写入的开关。写出数据后必须调用 `flush` 或 `close` 数据才会真正写出：`flush` 刷出后还能继续写出，`close` 关闭流并顺便刷出，关闭后不能再写出。

```java
public static void main(String[] args) throws IOException {
    FileWriter fw = new FileWriter("D:\\B.txt", true);
    fw.write("你好");
    fw.close();
}
```

## 缓冲流

缓冲流在源代码中内置了字节或字符数组，可以提高读写效率。但缓冲流本身不具备读写功能，它只是对普通的流对象进行包装，真正和文件建立关联的还是普通的流对象。构造时传入一个普通流即可。

```java
BufferedReader br = new BufferedReader(new FileReader("D:\\a.txt"));
BufferedWriter bw = new BufferedWriter(new FileWriter("D:\\b.txt"));
```

缓冲流有各自的独有方法：`BufferedReader.readLine()` 读取一行字符串，读到末尾返回 `null`；`BufferedWriter.newLine()` 写出换行符，并且具有跨平台性。

```java
BufferedReader br = new BufferedReader(new FileReader("D:\\a.txt"));
String line;
while ((line = br.readLine()) != null) {
    bw.write(line);
    bw.newLine();
}
```

## try-with-resources

从 JDK 7 开始，可以把流的创建写在 `try` 后面的括号里，这样流用完会自动关闭，不需要手动写 `close`。

```java
public static void main(String[] args) {
    // try 括号中创建的流会自动关闭
    try (FileOutputStream fos = new FileOutputStream("D:\\B.txt")) {
        fos.write("abc".getBytes());
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

## 转换流

转换流 `InputStreamReader` 和 `OutputStreamWriter` 可以在字节流与字符流之间转换，并按指定的字符编码读写。

```java
// 按照 UTF-8 编码读取
InputStreamReader isr = new InputStreamReader(new FileInputStream("D:\\a.txt"), "UTF-8");

// 按照 UTF-8 编码写出
OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("D:\\b.txt"), "UTF-8");
```

## 序列化与反序列化

序列化就是把对象以字节的形式直接写出到流中，反序列化就是从流中把对象读回来。用来序列化的是 `ObjectOutputStream.writeObject`，用来反序列化的是 `ObjectInputStream.readObject`。

```java
public static void main(String[] args) throws Exception {
    // 序列化：把对象写到文件
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("D:\\obj.txt"));
    oos.writeObject(new Person("张三", 20));
    oos.close();

    // 反序列化：从文件读取对象
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream("D:\\obj.txt"));
    Person p = (Person) ois.readObject();
    ois.close();
}
```

被序列化的类必须实现 `Serializable` 接口。实现后推荐手动加入 `serialVersionUID`，这样即使类发生了变化，之前序列化的数据也能正常读取。被 `transient` 修饰的成员变量不会被序列化。

```java
public class Person implements Serializable {
    // 手动指定版本号
    private static final long serialVersionUID = 1L;
    private String name;
    private int age;

    // transient 修饰的成员变量不会被序列化
    private transient String password;
}
```

## 打印流

打印流可以实现方便、高效地打印数据到文件，并且可以指定字符编码。它打印什么数据就是什么数据，例如打印整数 `97` 写出去就是 `97`。`PrintStream` 按字节输出，`PrintWriter` 按字符输出。常用 `print` 原样写入无换行，`println` 原样写入并带换行。

```java
PrintStream ps = new PrintStream("D:\\a.txt");
ps.println("hello");
ps.close();

PrintWriter pw = new PrintWriter("D:\\b.txt");
pw.println(97);
pw.close();
```

## Properties 集合

`Properties` 本质是一个 Map 集合，内部键和值都是 `String` 字符串，专门用来处理与 IO 有关的配置。常用方法有 `setProperty` 添加键值对、`getProperty` 根据键取值、`load` 从流中加载键值对、`store` 把键值对写出到文件。

```java
Properties prop = new Properties();
prop.setProperty("name", "张三");
prop.setProperty("age", "20");
// 把键值对写出到文件
prop.store(new FileOutputStream("D:\\config.properties"), "配置");

// 从文件读取键值对
Properties p = new Properties();
p.load(new FileInputStream("D:\\config.properties"));
System.out.println(p.getProperty("name"));
```

## 小结

字节流与字符流分别适合处理二进制和文本数据，缓冲流和转换流提升了效率与编码灵活性，try-with-resources 简化了资源关闭。面向对象层面，序列化与打印流让对象和数据能够直接落盘。
