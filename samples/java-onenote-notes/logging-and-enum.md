---
category: Java
category_slug: java
topic: 日志
topic_slug: logging
title: 日志框架与枚举
slug: logging-and-enum
tags: [日志, Slf4j, Logback, 枚举]
summary: 认识主流日志框架与 Slf4j 门面，掌握 Logback 配置和日志级别，再看枚举的定义与特性。
order: 10
---

# 日志框架与枚举

日志用于记录程序运行过程中的关键信息，帮助定位问题。枚举则是一种特殊的类，用于表示一组固定的常量。这一篇先认识日志框架与用法，再介绍枚举的定义和特点。

## 常见日志框架

Java 中常用的日志框架有 JUL、Log4j、Logback 和 Slf4j。JUL（`java.util.logging`）是 JDK 官方日志框架，配置简单但不够灵活、性能较差。Log4j 配置灵活，支持多种输出目标。Logback 由 Log4j 升级而来，功能和配置选项更多，性能优于 Log4j。

Slf4j（`Simple Logging Facade for Java`）是简单日志门面，提供了一套日志操作的标准接口和抽象类，允许应用程序使用不同的底层日志框架。

## Slf4j 的使用

用 Slf4j 时，通过 `LoggerFactory.getLogger` 获取日志对象，然后按级别调用对应的方法。

```java
public class LogTest {
    public static void main(String[] args) {
        // 获取日志对象
        Logger logger = LoggerFactory.getLogger(LogTest.class);

        logger.trace("trace");
        logger.debug("debug");
        logger.info("info");
        logger.warn("warn");
        logger.error("error");
    }
}
```

## Logback 配置

Logback 使用配置文件 `logback.xml` 控制日志输出，可以配置输出的格式、位置及日志开关。常用的两种输出位置是控制台和文件。控制台输出用 `ConsoleAppender`，文件输出用 `RollingFileAppender`。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>app.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="ALL">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

## 日志级别

日志级别按优先级从低到高依次是 `trace`、`debug`、`info`、`warn`、`error`。`ALL` 表示开启所有日志，`OFF` 表示取消日志。

| 级别 | 说明 | 使用 |
|---|---|---|
| `trace` | 追踪，记录运行轨迹，使用很少 | `log.trace(...)` |
| `debug` | 调试，实际应用中一般视为最低级别，使用较多 | `log.debug(...)` |
| `info` | 记录重要信息，如数据库连接、网络连接、IO 操作，使用较多 | `log.info(...)` |
| `warn` | 警告信息，可能会发生问题，使用较多 | `log.warn(...)` |
| `error` | 错误信息，使用较多 | `log.error(...)` |

## 枚举的定义

枚举用 `enum` 关键字定义，枚举类名后跟大括号，里面是枚举项。例如表示季节的 `Season` 枚举。

```java
public enum Season {
    SPRING, SUMMER, AUTUMN, WINTER
}
```

## 枚举的特点

每一个枚举项其实就是该枚举的一个对象，通过枚举类名直接访问。所有枚举类都是 `Enum` 的子类，枚举也是类，可以定义成员变量。

```java
public enum Season {
    // 枚举项：枚举类的对象
    SPRING("春天"), SUMMER("夏天"), AUTUMN("秋天"), WINTER("冬天");

    private final String name;

    // 构造器必须是 private 的，默认也是 private
    private Season(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
```

枚举类的第一行必须是枚举项，最后一个枚举项后的分号可以省略；但如果枚举类还有其他的内容，这个分号就不能省略，建议不要省略。枚举类也可以有抽象方法，但每个枚举项必须重写该方法。

## 小结

日志框架各有侧重，Slf4j 作为门面让业务代码不依赖具体实现，Logback 与日志级别让输出来得规范和可控。枚举把一组固定的常量封装成对象，既能表达业务语义，又能携带属性和方法。
