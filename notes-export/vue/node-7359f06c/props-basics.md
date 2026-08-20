---
category: vue
category_slug: vue
topic: 组件
topic_slug: node-7359f06c
title: props 基础
slug: props-basics
---

# props 基础

Vue 组件的 `props` 是父组件向子组件传数据的方式。

## 声明方式

```javascript
// 数组写法（不推荐，缺少类型与校验）
props: ['title', 'count']

// 对象写法（推荐）
props: {
  title: {
    type: String,
    required: true
  },
  count: {
    type: Number,
    default: 0
  }
}
```

## 单向数据流

子组件**不能直接修改** props 的值，应该通过事件通知父组件：

```vue
<script setup>
const props = defineProps({
  modelValue: String
})
const emit = defineEmits(['update:modelValue'])

function update() {
  emit('update:modelValue', 'new value')
}
</script>
```

## 常见坑

- props 是只读的，直接改会触发警告且不生效
- 引用类型（对象/数组）的默认值必须用工厂函数返回
- 用 `v-model` 时需要 `modelValue` 这个固定名字

> 组件通信顺序：props 下行，events 上行。
