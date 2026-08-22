---
category: Java
category_slug: java
topic: AOP
topic_slug: aop
title: AOP 公共字段自动填充
slug: aop-public-field-autofill
tags: [AOP, 注解, 反射, MyBatis]
summary: 用自定义注解 + 切面在 Mapper 方法执行前统一填充 create_time / update_time 等公共字段，避免每处手写。
order: 30
---

# AOP 公共字段自动填充

员工、分类、菜品等表都有 create_time、create_user、update_time、update_user 四个公共字段。如果每个 insert/update 都手动 set，既啰嗦又容易漏。苍穹外卖用自定义注解 + AOP 切面统一解决。

## 自定义注解与枚举

先定义一个注解，用 value 区分是插入还是更新操作。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    OperationType value();
}

public enum OperationType {
    UPDATE, INSERT
}
```

Mapper 方法上打上注解即可声明意图，比如 `@AutoFill(OperationType.INSERT) void insert(Employee employee);`。

## 切面实现

切面拦截所有标注了 @AutoFill 的 Mapper 方法，在方法执行前用反射给实体补值。

```java
@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut() {}

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws Exception {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill annotation = signature.getMethod().getAnnotation(AutoFill.class);
        Object entity = joinPoint.getArgs()[0];

        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        if (annotation.value() == OperationType.INSERT) {
            // 插入：四个字段都填
            entity.getClass().getMethod("setCreateTime", LocalDateTime.class).invoke(entity, now);
            entity.getClass().getMethod("setCreateUser", Long.class).invoke(entity, currentId);
            entity.getClass().getMethod("setUpdateTime", LocalDateTime.class).invoke(entity, now);
            entity.getClass().getMethod("setUpdateUser", Long.class).invoke(entity, currentId);
        } else if (annotation.value() == OperationType.UPDATE) {
            // 更新：只填 update 两个字段
            entity.getClass().getMethod("setUpdateTime", LocalDateTime.class).invoke(entity, now);
            entity.getClass().getMethod("setUpdateUser", Long.class).invoke(entity, currentId);
        }
    }
}
```

方法参数约定为第一个参数是实体对象，切面直接取 args[0] 操作。

## 这样设计的收益

新增一张业务表时，只要实体有公共字段、Mapper 方法打上注解，自动填充立刻生效，不用改任何 service 代码。

这是"横切关注点"的典型例子：审计字段填充和业务逻辑无关，从业务代码里抽出来用 AOP 统一管理，代码量明显减少。

## 小结

自定义注解声明意图 + 切面统一实现 + 反射动态赋值，三步走完一个通用的公共字段填充方案。理解这个案例，AOP 的切入、通知、切点表达式就都串起来了。
