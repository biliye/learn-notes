<template>
  <div class="ln-block code-block-wrap" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <div class="code-block">
      <div class="code-header">
        <span class="code-lang">{{ displayLang }}</span>
        <button class="copy-btn" @click="copy">
          <el-icon v-if="!copied"><CopyDocument /></el-icon>
          <el-icon v-else><Check /></el-icon>
          {{ copied ? '已复制' : '复制' }}
        </button>
      </div>
      <pre class="code-pre"><code v-html="html" /></pre>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { extractCode, highlight } from '../../utils/markdown'

const props = defineProps({
  block: { type: Object, required: true }
})

const copied = ref(false)
const displayLang = computed(() => props.block.lang || 'text')
const code = computed(() => extractCode(props.block.raw))
const html = computed(() => highlight(code.value, props.block.lang).html)

async function copy() {
  try {
    await navigator.clipboard.writeText(code.value)
    copied.value = true
    ElMessage.success('已复制代码')
    setTimeout(() => (copied.value = false), 1500)
  } catch (e) {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped lang="scss">
.code-block-wrap {
  margin: 0 0 20px;
}
.code-block {
  position: relative;
  background: var(--code-block-bg);
  border-radius: var(--code-block-radius);
  border: 1px solid #262a36;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  /* 金色角括号 */
  &::before,
  &::after {
    content: '';
    position: absolute;
    width: 8px;
    height: 8px;
    pointer-events: none;
    z-index: 2;
  }
  &::before {
    top: -1px;
    left: -1px;
    border-top: 2px solid var(--ak-gold-dim);
    border-left: 2px solid var(--ak-gold-dim);
  }
  &::after {
    bottom: -1px;
    right: -1px;
    border-bottom: 2px solid var(--ak-gold-dim);
    border-right: 2px solid var(--ak-gold-dim);
  }
}
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #20242e;
  border-bottom: 1px solid #262a36;
  .code-lang {
    color: var(--code-lang-color);
    font-family: var(--code-block-font);
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 1px;
  }
  .copy-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    background: var(--code-copy-bg);
    color: var(--code-block-text);
    border: 1px solid transparent;
    border-radius: 2px;
    padding: 2px 8px;
    font-size: 12px;
    cursor: pointer;
    transition: background 0.15s, border-color 0.15s;
    &:hover {
      background: rgba(201, 168, 106, 0.2);
      border-color: var(--ak-gold-dim);
    }
  }
}
.code-pre {
  margin: 0;
  padding: 14px 16px;
  overflow-x: auto;
  code {
    font-family: var(--code-block-font);
    font-size: 14px;
    line-height: 1.6;
    color: var(--code-block-text);
    white-space: pre;
    // highlight.js 深色主题覆盖（Arknight 战术终端色）
    :deep(.hljs-keyword) { color: #7fb3e0; }
    :deep(.hljs-string) { color: #d4b26f; }
    :deep(.hljs-comment) { color: #6a7259; font-style: italic; }
    :deep(.hljs-number) { color: #8fbf9f; }
    :deep(.hljs-title), :deep(.hljs-title.function_) { color: #e0c283; }
    :deep(.hljs-built_in) { color: #6fa8a0; }
    :deep(.hljs-attr) { color: #b6b1a3; }
    :deep(.hljs-params) { color: #9aa3b2; }
  }
}
</style>
