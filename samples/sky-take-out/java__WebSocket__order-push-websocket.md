---
category: Java
category_slug: java
topic: WebSocket
topic_slug: websocket
title: WebSocket 订单状态推送与心跳
slug: websocket-order-push
tags: [WebSocket, 定时任务, 即时通讯]
summary: 用户下单后商家端实时收到提醒：WebSocketServer 维护会话集合群发消息，WebSocketTask 定时发心跳保活。
order: 40
---

# WebSocket 订单状态推送与心跳

用户在小程序点餐下单后，商家管理端要立刻弹出新订单提醒。HTTP 是"一问一答"，服务端没法主动推，所以这里用 WebSocket 长连接实现服务端推送。

## 服务端会话管理

用 @ServerEndpoint 声明端点，把每个连接进来的客户端 session 存进 Map，按 sid 区分来源。

```java
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    private static Map<String, Session> sessionMap = new HashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        sessionMap.put(sid, session);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        sessionMap.remove(sid);
    }

    public void sendToAllClient(String message) {
        for (Session session : sessionMap.values()) {
            session.getBasicRemote().sendText(message);
        }
    }
}
```

管理端页面登录后建立连接，sid 传商家端标识，服务端据此知道要推给谁。

## 下单触发推送

订单服务在下单成功后构造一条 JSON 消息，注入 WebSocketServer 群发给所有在线的商家端。

```java
@Autowired
private WebSocketServer webSocketServer;

// 下单成功：
String json = "{\"type\":1,\"orderId\":" + orders.getId() + ",\"content\":\"您有新的订单待处理\"}";
webSocketServer.sendToAllClient(json);
```

商家端收到消息后弹出订单卡片，点击即可接单。

## 定时任务心跳保活

WebSocket 连接长时间空闲可能被中间设备断开，项目用定时任务周期性向客户端发心跳。

```java
@Component
public class WebSocketTask {
    @Autowired
    private WebSocketServer webSocketServer;

    @Scheduled(cron = "0/5 * * * * ?")
    public void sendHeartbeat() {
        webSocketServer.sendToAllClient("ping");
    }
}
```

每隔 5 秒群发一次 ping，客户端收到后回 pong，连接保持在活跃状态。

## 小结

WebSocket 的核心是"建立连接后双向随时通信"。本项目用法很朴素：一个端点、一张会话表、一个群发方法，再配定时任务保活，就把"服务端主动推"这件事做成了。相比轮询，实时性和开销都更好。
