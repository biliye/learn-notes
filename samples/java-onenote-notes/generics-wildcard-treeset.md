---
category: Java
category_slug: java
topic: 泛型
topic_slug: generics
title: 泛型、通配符与 TreeSet 排序
slug: generics-wildcard-treeset
tags: [泛型, 集合]
summary: 讲解泛型方法与泛型通配符、类实现泛型接口的两种方式，以及 TreeSet 依托红黑树的自然排序与比较器排序，并说明 Collections.sort 的用法。
order: 10
---

# 泛型、通配符与 TreeSet 排序

泛型让类型参数化，通配符让泛型更灵活；TreeSet 则利用红黑树加排序规则实现自动去重与有序存储。

## 泛型方法

泛型方法的类型确定时机：在调用方法、传入实际参数的时候，才确定具体的类型。

非静态方法：内部的泛型会根据类的泛型去匹配。静态方法：静态方法中如果加入泛型，必须声明出自己独立的泛型，因为静态方法不依赖对象，拿不到类的泛型。

```java
public class MyUtil {
    // 静态方法需要声明出自己独立的泛型 <T>
    public static <T> void printArray(T[] arr) {
        System.out.print("[");
        for (int i = 0; i < arr.length - 1; i++) {
            System.out.print(arr[i] + ",");
        }
        System.out.println(arr[arr.length - 1] + "]");
    }
}
```

## 泛型通配符

通配符用来限制泛型可接受的范围，常见三种写法：

| 写法 | 可接收类型 |
|---|---|
| ? | 任意类型 |
| ? extends E | E 或 E 的子类 |
| ? super E | E 或 E 的父类 |

```java
public static void main(String[] args) {
    ArrayList<Coder> list1 = new ArrayList<>();
    list1.add(new Coder());
    ArrayList<Manager> list2 = new ArrayList<>();
    list2.add(new Manager());
    method(list1);    // Coder 是 Employee 的子类
    method(list2);    // Manager 是 Employee 的子类
}

public static void method(ArrayList<? extends Employee> list) {
    for (Object o : list) {
        Employee e = (Employee) o;
        e.work();
    }
}
```

这里方法接收 ArrayList<? extends Employee>，所以只能传入 Employee 或其子类类型的集合。

## 类实现泛型接口

当接口带有泛型时，类实现它有两种操作方式：一种在类实现接口时直接确定类型，另一种延续接口的泛型，等创建对象的时候再确定。

```java
public interface List<E> {
}

// 方式一：类实现接口的时候直接确定类型
public class StringList implements List<String> {
}

// 方式二：延续接口的泛型，等创建对象的时候再确定
public class ArrayList<E> implements List<E> {
}
```

## 红黑树添加节点规则

TreeSet 底层是红黑树。红黑树在添加节点的时候，添加的节点默认是红色的，再按规则调整：

- 根节点：直接变为黑色。
- 父节点是黑色：不需要任何操作。
- 叔叔节点是红色：把父设为黑色、叔叔设为黑色、祖父设为红色；若祖父是根再变回黑色，若祖父非根则把祖父设置为当前节点再做其他判断。
- 叔叔节点是黑色、父节点是红色：当前节点是父的右孩子时，把父设置为当前节点并左旋再做判断；当前节点是父的左孩子时，把父设为黑色、祖父设为红色，以祖父为支点右旋。

添加节点后取出的顺序是左、中、右，保证整体有序。

## TreeSet 排序

### 自然排序

自然排序分三步：类实现 Comparable 接口，重写 compareTo 方法，根据方法返回值组织排序规则。

```java
public class Student implements Comparable<Student> {
    public String name;
    public int age;

    @Override
    public int compareTo(Student o) {
        return this.age - o.age;    // 正序（升序）
        // return o.age - this.age; // 倒序（降序）
    }
}
```

当我们调用 add 方法向 TreeSet 添加元素时，内部会自动调用 compareTo 方法，根据这个方法的返回值来决定节点怎么走：负数左边走、正数右边走、0 不存。所以即使添加两遍同一个学生（姓名年龄都相同），也只会存一个。

### 比较器排序

重点：如果同时具备自然排序和比较器排序，会优先按照比较器进行排序操作。我们可以直接给 TreeSet 传入一个 Comparator。

Java 已经写好的类（String、Integer、Double 等）大多数都具有自然排序规则，这些规则放在源代码中无法修改。

- String：默认是字典顺序排序。
- Integer：默认是升序排序。
- Double：默认是升序排序。

如果我们要实现的需求，其排序规则跟已经具备的自然排序不一样，这时候就要使用比较器排序。

```java
TreeSet<String> ts = new TreeSet<>(new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
        return o2.compareTo(o1);   // 倒序
        // return 0;                // 所有元素视为相同，只会存一个
    }
});
```

## Collections.sort

对 List 排序可以用 Collections.sort(list)，它会调用元素自身的自然排序；也可以传入一个 Comparator 指定排序规则。

```java
ArrayList<Integer> list = new ArrayList<>();
list.add(3);
list.add(1);
list.add(2);
Collections.sort(list);                 // 自然排序升序 [1, 2, 3]
Collections.sort(list, (a, b) -> b - a); // 比较器倒序 [3, 2, 1]
```

## 小结

泛型方法里静态方法必须声明自己独立的泛型。通配符配合 extends 与 super 限制类型范围，类实现泛型接口时可在类层面或对象层面确定类型。TreeSet 基于红黑树，靠 compareTo 或 Comparator 判断节点去向，返回 0 视为重复不存储。比较器优先于自然排序，Collections.sort 可对 List 排序并指定规则。

