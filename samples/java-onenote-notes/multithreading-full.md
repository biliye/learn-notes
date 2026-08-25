---
category: Java
category_slug: java
topic: 多线程
topic_slug: thread
title: 多线程、同步与线程池
slug: multithreading-full
tags: [线程, 同步, 线程池, 单例]
summary: 从进程线程讲起，掌握三种开启线程的方式、同步与等待唤醒、线程池七参数与单例模式。
order: 10
---

# 多线程、同步与线程池

多线程可以让一个程序同时处理多个任务，提高效率。理解进程与线程的区别，掌握线程的开启方式，再学会解决线程安全问题，是这一章的核心。

## 进程与线程

进程是程序的执行过程，它有自己的空间、动态产生消亡，可以与其他进程并发执行。线程是进程中的任务，多线程就是一个进程里同时执行多个任务。对于一个 CPU 而言，它是在多个进程或线程之间轮换执行的。

## 开启线程的三种方式

第一种方式是编写一个类继承 `Thread`，重写 `run` 方法，把线程任务代码写在 `run` 里，然后创建对象并调用 `start` 开启线程。只有调用 `start` 才会开启新线程并自动调用 `run`。

```java
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("子线程执行了");
    }
}

public static void main(String[] args) {
    MyThread t = new MyThread();
    t.start();
}
```

第二种方式是实现 `Runnable` 接口，把任务代码写在 `run` 里，然后创建任务资源对象，传入 `Thread` 构造器，再调用 `start`。

```java
class MyRunnable implements Runnable {
    @Override
    public void run() {
        System.out.println("Runnable 线程执行了");
    }
}

public static void main(String[] args) {
    MyRunnable mr = new MyRunnable();
    Thread t = new Thread(mr);
    t.start();
}
```

第三种方式是实现 `Callable` 接口，任务代码写在 `call` 里，`call` 有返回值。先创建 `FutureTask` 封装资源，再把 `FutureTask` 传给 `Thread`。线程执行完后可以通过 `task.get()` 拿到结果。

```java
class MyCallable implements Callable<Integer> {
    @Override
    public Integer call() {
        return 100;
    }
}

public static void main(String[] args) throws Exception {
    FutureTask<Integer> task = new FutureTask<>(new MyCallable());
    Thread t = new Thread(task);
    t.start();
    Integer result = task.get();
    System.out.println(result);
}
```

## 线程相关方法

`Thread` 提供了许多控制线程的方法。`getName` 返回线程名称，`setName` 设置名称，`currentThread` 获取当前线程对象，`sleep` 让线程休眠指定毫秒，`setPriority` 设置优先级，`setDaemon` 设置为守护线程。优先级范围是 `1` 到 `10`，默认为 `5`。

| 方法 | 说明 |
|---|---|
| `String getName()` | 返回线程名称 |
| `void setName(String name)` | 设置线程名称 |
| `static Thread currentThread()` | 获取当前线程对象 |
| `static void sleep(long time)` | 让线程休眠指定毫秒 |
| `void setPriority(int p)` | 设置线程优先级 |
| `final void setDaemon(boolean on)` | 设置为守护线程 |

## 线程安全问题与同步

线程安全问题出现需要三个条件：多线程环境、有共享数据、有多条语句操作共享数据。同步技术就是把这些代码锁起来，让任意时刻只有一个线程可以执行。同步有三种方式：同步代码块、同步方法、`Lock` 锁。

同步代码块用 `synchronized (锁对象)` 包围共享代码。静态方法的锁对象是字节码对象，非静态方法的锁对象是 `this`。

```java
class TicketTask extends Thread {
    private static int tickets = 100;

    @Override
    public void run() {
        while (true) {
            synchronized (TicketTask.class) {
                if (tickets == 0) {
                    break;
                }
                tickets--;
            }
        }
    }
}
```

同步方法在方法的返回值类型前加 `synchronized` 关键字。

```java
public synchronized void method() {
    // 这里的代码同一时刻只能被一个线程执行
}
```

`Lock` 是接口，通常用它的实现类 `ReentrantLock`。`lock` 加锁，`unlock` 释放锁。

```java
Lock lock = new ReentrantLock();
lock.lock();
try {
    // 共享代码
} finally {
    lock.unlock();
}
```

## 等待唤醒机制

`wait` 让当前线程等待，`notify` 随机唤醒单个等待的线程。这些方法必须用锁对象调用。使用 `ReentrantLock` 时，可以通过 `newCondition` 获取 `Condition` 对象，`await` 让线程等待，`signal` 唤醒单个等待线程。

```java
ReentrantLock lock = new ReentrantLock();
Condition c1 = lock.newCondition();
// 在某个线程中等待
c1.await();
// 唤醒
c1.signal();
```

## 线程生命周期

线程从创建到结束共有六种状态。`NEW` 是创建线程对象，`RUNNABLE` 是 `start` 方法被调用但还没抢到 CPU 执行权，`BLOCKED` 是线程运行中但没拿到锁对象，`WAITING` 是通过 `wait` 等待，`TIMED_WAITING` 是通过 `sleep` 计时等待，`TERMINATED` 是代码全部运行完毕。

| 状态 | 含义 |
|---|---|
| `NEW` | 创建线程对象 |
| `RUNNABLE` | 调用了 `start`，但还没抢到 CPU 执行权 |
| `BLOCKED` | 线程开始运行，但没获取到锁对象 |
| `WAITING` | 调用了 `wait` 方法 |
| `TIMED_WAITING` | 调用了 `sleep` 方法 |
| `TERMINATED` | 代码全部运行完毕 |

## 线程池

创建和销毁线程会消耗时间和系统资源，线程池可以复用线程，从而提高性能。`Executors` 提供了静态方法创建线程池，`newFixedThreadPool` 创建指定最多线程数量的线程池，`newCachedThreadPool` 创建一个默认的线程池。使用 `pool.submit` 提交任务，`pool.shutdown` 关闭线程池。规范要求线程资源必须通过线程池提供，不允许在应用中自行显式创建线程。

```java
ExecutorService pool = Executors.newFixedThreadPool(10);
for (int i = 1; i <= 100; i++) {
    pool.submit(new Runnable() {
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + " 提交了线程任务");
        }
    });
}
pool.shutdown();
```

`ThreadPoolExecutor` 是最完整的线程池构造，有七个参数。核心线程数量相当于正式员工，最大线程数量是正式员工加临时工，空闲时间是要保持的空闲时长，任务队列指定排队人数，线程工厂用来创建线程对象，拒绝策略处理队列满时的任务。

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2,                              // 核心线程数量
    5,                              // 最大线程数量
    60, TimeUnit.SECONDS,           // 空闲时间与单位
    new ArrayBlockingQueue<>(10),   // 有界任务队列
    Executors.defaultThreadFactory(),
    new ThreadPoolExecutor.AbortPolicy()
);
```

临时线程在任务数大于核心线程数加任务队列数量时创建。当任务数大于最大线程数加任务队列数量时，会触发拒绝策略。四种拒绝策略：`AbortPolicy` 丢弃并抛异常（默认），`DiscardPolicy` 丢弃但不抛异常（不推荐），`DiscardOldestPolicy` 抛弃等待最久的任务，`CallerRunsPolicy` 由调用线程直接执行任务。

| 拒绝策略 | 说明 |
|---|---|
| `AbortPolicy` | 丢弃任务并抛异常，默认策略 |
| `DiscardPolicy` | 丢弃任务但不抛异常 |
| `DiscardOldestPolicy` | 抛弃队列中等待最久的任务 |
| `CallerRunsPolicy` | 由调用线程直接执行任务 |

## 单例设计模式

单例保证类的对象在内存中只有一份。当创建一个对象消耗资源过多，并且这个对象可以复用时，可以把它设计成单例。分为饿汉式和懒汉式。饿汉式在类加载时就创建对象，懒汉式在第一次调用时才创建。

```java
// 饿汉式：类加载时就创建对象
class Single1 {
    private Single1() {
    }

    public static final Single1 s = new Single1();
}
```

懒汉式用 `getInstance` 返回唯一实例，为了保证线程安全，结合双重检查锁，在锁内再判断一次是否为空。

```java
// 懒汉式：第一次调用时才创建
class Single2 {
    private Single2() {
    }

    private static Single2 s;

    public static Single2 getInstance() {
        if (s == null) {
            synchronized (Single2.class) {
                if (s == null) {
                    s = new Single2();
                }
            }
        }
        return s;
    }
}
```

## 小结

多线程提升了程序效率，但也引入了线程安全问题。通过同步代码块、同步方法或 `Lock` 锁保证共享数据的一致性，配合等待唤醒机制实现线程协作，再用线程池管理线程生命周期，是工程实践中安全并发的基础。
