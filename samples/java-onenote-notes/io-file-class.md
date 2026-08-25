---
category: Java
category_slug: java
topic: IO
topic_slug: io
title: File 类与文件操作
slug: io-file-class
tags: [文件, File, 递归]
summary: 用 File 定位文件和文件夹，掌握三种构造、常用属性方法、创建删除与递归遍历。
order: 10
---

# File 类与文件操作

`File` 是 Java 中用来表示文件或文件夹路径的对象。它封装的是一个路径名，这个路径可以是存在的，也可以是不存在的，所以只创建 `File` 对象并不会真正在磁盘上生成文件。

## 三种构造方式

`File` 提供了三种构造方法，可以传入完整路径，也可以把父路径和子路径分开传入。

```java
public static void main(String[] args) throws IOException {
    // 直接传入完整的路径字符串
    File f1 = new File("D:\\A.txt");
    f1.createNewFile();

    // 父路径和子路径都用字符串给出
    File f2 = new File("D:\\", "test");
    System.out.println(f2.exists());

    // 父路径用 File 对象，子路径用字符串
    File f3 = new File(new File("D:\\"), "test");
    System.out.println(f3.exists());
}
```

## 常用属性方法

`File` 的常用方法用来判断路径是文件还是文件夹，以及读取文件的大小、名称和最后修改时间等。

| 方法 | 说明 |
|---|---|
| `boolean isDirectory()` | 判断是否为文件夹 |
| `boolean isFile()` | 判断是否为文件 |
| `boolean exists()` | 判断路径是否存在 |
| `long length()` | 返回文件大小，单位为字节 |
| `String getAbsolutePath()` | 返回绝对路径 |
| `String getPath()` | 返回定义时使用的路径 |
| `String getName()` | 返回文件名称，带后缀 |
| `long lastModified()` | 返回最后修改时间，毫秒值 |

## 创建与删除

`createNewFile` 创建新的空文件，`mkdir` 只能创建一级文件夹，`mkdirs` 可以创建多级文件夹。删除时 `delete` 只能删除空文件夹，并且不会进入回收站。

```java
File f1 = new File("D:\\a.txt");
f1.createNewFile();                  // 创建空文件

File dir = new File("D:\\test");
dir.mkdir();                         // 只创建一级文件夹

new File("D:\\a\\b").mkdirs();       // 创建多级文件夹

File old = new File("D:\\test");
old.delete();                        // 只能删除空文件夹，不走回收站
```

## 遍历与递归查找

`listFiles` 返回当前目录下所有的一级文件和文件夹对象。当路径不存在、路径是文件、或没有访问权限时返回 `null`；当目录是空文件夹时返回长度为 `0` 的数组。

要查找某个文件夹下所有的 `.java` 文件，可以先遍历一层，如果遇到文件夹就递归继续遍历。

```java
public static void printJavaFile(File dir) {
    File[] files = dir.listFiles();
    for (File file : files) {
        if (file.isFile()) {
            if (file.getName().endsWith(".java")) {
                System.out.println(file);
            }
        } else {
            // 是文件夹，递归进入继续找 .java 文件
            printJavaFile(file);
        }
    }
}
```

## 小结

`File` 对象只是一个路径的封装，它既不代表文件内容，也不会自动创建文件。用它判断类型、读取属性、创建删除，再配合 `listFiles` 与递归，就能完成对文件系统的遍历与查找。
