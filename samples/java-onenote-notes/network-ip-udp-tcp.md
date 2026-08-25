---
category: Java
category_slug: java
topic: 网络编程
topic_slug: network
title: 网络编程：IP、UDP 与 TCP
slug: network-ip-udp-tcp
tags: [网络, UDP, TCP, IP]
summary: 认识 IP 与 InetAddress，掌握 UDP 无连接通信和 TCP 面向连接通信的收发流程。
order: 10
---

# 网络编程：IP、UDP 与 TCP

网络编程要让不同设备上的程序互相通信。首先要标识设备的位置，也就是 IP 地址；然后按一定的协议传输数据，主要有面向无连接的 UDP 和面向连接的 TCP 两种。

## IP 地址

IP 是设备在网络中的地址，是唯一的标识。IPv4 是当前主流方案，最多只有 `2^32` 个地址，目前已经用完了；IPv6 是为了解决 IPv4 不够用而出现的，最多有 `2^128` 个地址。现在解决 IPv4 不够用的办法是利用局域网 IP。

`127.0.0.1` 永远表示本机。常用的两个命令是 `ipconfig` 查看本机 IP 地址，`ping` 检查网络是否连通。

## InetAddress

为了方便对 IP 地址的获取和操作，Java 提供了 `InetAddress` 类，它表示互联网协议地址。

| 方法 | 说明 |
|---|---|
| `static InetAddress getByName(String host)` | 确定主机名称的 IP 地址，主机名可以是机器名或 IP 地址 |
| `String getHostName()` | 获取此 IP 地址的主机名 |
| `String getHostAddress()` | 返回文本显示的 IP 地址字符串 |

```java
import java.net.InetAddress;

public static void main(String[] args) throws Exception {
    InetAddress address = InetAddress.getByName("127.0.0.1");
    System.out.println(address.getHostName());
    System.out.println(address.getHostAddress());
}
```

## UDP 无连接通信

UDP 用户数据报协议是面向无连接的通信协议，速度快，一次最多发送 `64K`，数据不安全、易丢失。UDP 通信用 `DatagramSocket` 发送和接收，用 `DatagramPacket` 打包和解包数据。

发送数据四步：创建发送端 `DatagramSocket`，把数据打包成 `DatagramPacket` 并指定目标 IP 和端口，`send` 发送数据，释放资源。

```java
public class Send {
    public static void main(String[] args) throws Exception {
        // 1. 创建 DatagramSocket，指定发送端口
        DatagramSocket socket = new DatagramSocket(8888);

        // 2. 创建 DatagramPacket 数据包，指定目标 IP 与端口
        byte[] bytes = "你好".getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
                InetAddress.getByName("127.0.0.1"), 9999);

        // 3. 发送数据
        socket.send(packet);

        // 4. 释放资源
        socket.close();
    }
}
```

接收数据：创建接收端 `DatagramSocket` 指定端口，准备一个字节数组打包成 `DatagramPacket`，`receive` 接收数据，再从数据包中取数据和来源地址。

```java
public class Receive {
    public static void main(String[] args) throws Exception {
        // 1. 创建接收端 DatagramSocket，指定监听端口
        DatagramSocket socket = new DatagramSocket(9999);

        // 2. 准备接收数据的 DatagramPacket
        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);

        // 3. 接收数据
        socket.receive(packet);

        // 4. 拆包，取数据、来源地址
        byte[] data = packet.getData();
        String msg = new String(data, 0, packet.getLength());
        String ip = packet.getAddress().getHostName();
        System.out.println("接收到 " + ip + " 发送过来的消息为：" + msg);

        // 5. 释放资源
        socket.close();
    }
}
```

## TCP 面向连接通信

TCP 是面向连接的通信协议，速度慢，没有大小限制，数据安全。TCP 通信分为客户端和服务端。

客户端用 `Socket` 连接指定 IP 和端口，通过 `getOutputStream` 写出数据、`getInputStream` 读取数据。这里的流对象读写的是网络数据，不是读写文件中的数据。

```java
public class Client {
    public static void main(String[] args) throws Exception {
        // 1. 创建 Socket 指定 IP 和端口
        Socket socket = new Socket("127.0.0.1", 10086);

        // 2. 获取输出流写出数据
        OutputStream os = socket.getOutputStream();
        os.write("你好".getBytes());

        // 3. 释放资源
        socket.close();
    }
}
```

服务端用 `ServerSocket` 指定端口，通过 `accept` 响应客户端连接并拿到 `Socket`，再用这个 `Socket` 收发数据。

```java
public class Server {
    public static void main(String[] args) throws Exception {
        // 1. 创建 ServerSocket 指定端口
        ServerSocket serverSocket = new ServerSocket(10086);

        // 2. 响应客户端请求，拿到 Socket
        Socket socket = serverSocket.accept();

        // 3. 获取输入流读取数据
        InputStream is = socket.getInputStream();
        byte[] bys = new byte[1024];
        int len = is.read(bys);
        System.out.println(new String(bys, 0, len));

        // 4. 释放资源
        socket.close();
        serverSocket.close();
    }
}
```

## 小结

IP 负责标识设备，`InetAddress` 帮我们在 Java 中获取和操作地址。UDP 面向无连接、速度快但不安全，适合实时性高、允许丢包的场景；TCP 面向连接、可靠但稍慢，适合传输数据不能出错的场景。
