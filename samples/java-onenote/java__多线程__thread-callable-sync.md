---
category: Java
category_slug: java
topic: 多线程
topic_slug: thread
title: 多线程、Callable 与同步
slug: thread-callable-sync
tags: [多线程, Callable, FutureTask, 同步, CountDownLatch]
summary: Callable 用泛型指定返回值的类型，配 FutureTask 取线程结果；synchronized 锁对象要保证多线程用的是同一个；CountDownLatch.await 阻塞当前线程。
order: 10
---

# 多线程、Callable 与同步

多线程让程序并行执行，也引出"如何拿返回结果"和"如何保证共享数据安全"两个问题。前者用 Callable + FutureTask，后者用 synchronized 锁。

## Callable 与 FutureTask

`Runnable` 没有返回值，想要带返回值的任务用 `Callable`。`Callable` 的**泛型就是返回值的类型**——返回值是什么类型，泛型就写什么类型。

```java
Callable<Integer> task = () -> 42;
```

要通过 `FutureTask` 才能拿到线程运行的返回值。`FutureTask` 实现了 `Future`，`get()` 方法阻塞直到任务完成并返回结果。

```java
FutureTask<Integer> ft = new FutureTask<>(task);
new Thread(ft).start();
int result = ft.get();   // 拿到返回值 42
```

## 线程类型：守护线程

线程分用户线程和守护线程。**当所有非守护线程（用户线程）都结束时，守护线程也会随之结束**，只不过关闭会慢一些。常用 `setDaemon(true)` 把线程设为守护线程，适合后台任务。

```java
Thread t = new Thread(...);
t.setDaemon(true);   // 守护线程，随主线程结束而结束
```

## synchronized 与锁对象

`synchronized` 同步用的锁对象**可以是任意对象**，但关键是**多条线程必须用同一个对象**才有互斥效果。可以用 `static` 修饰一个成员当锁，最稳妥的是用这个类本身的字节码对象（`类名.class`）。

```java
synchronized (MyClass.class) {     // 锁对象：类字节码，全局唯一
    // 临界区
}
```

锁要作用于同一个对象，否则各用各的锁，等于没锁。

## wait / notify 要在同步代码块里

`Object.wait()` / `notify()` 的调用**前提是当前线程已持有该对象的锁**，即必须在 `synchronized` 块内。不满足会抛 `IllegalMonitorStateException`。所以"无等待的线程也可以执行并不报错，前提是得同步"指向的正是这一点。

## CountDownLatch 与 await

`CountDownLatch` 的 `await()` 让**调用它的当前线程阻塞等待**，直到计数归零。它只绑定调用时的那一个线程等待，计数减满（`countDown`）后放行；多个等待线程用的是同一个计数器。

```java
CountDownLatch latch = new CountDownLatch(3);
// 每个任务完成时 latch.countDown();
latch.await();   // 当前线程阻塞，直到计数为 0
```

## 线程池提交任务

用线程池提交带返回值的任务用 `submit`，返回一个 `Future`；提交无返回值的用 `execute`。

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
Future<Integer> f = pool.submit(() -> 42);   // submit 返回 Future 可拿结果
pool.shutdown();
```

## 小结

要返回结果就用 `Callable`（泛型=返回类型）+ `FutureTask`/`Future.get`；目标锁对象多线程必须共用（`类名.class` 最稳）；`wait/await` 要么在同步块里、要么阻塞当前线程。这几条把多线程的"取结果"与"锁"两个核心讲清楚了。
