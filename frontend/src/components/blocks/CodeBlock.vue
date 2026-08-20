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
  background: var(--code-block-bg);
  border-radius: var(--code-block-radius);
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.06);
  .code-lang {
    color: var(--code-lang-color);
    font-family: var(--code-block-font);
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  .copy-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    background: var(--code-copy-bg);
    color: var(--code-block-text);
    border: none;
    border-radius: 4px;
    padding: 2px 8px;
    font-size: 12px;
    cursor: pointer;
    &:hover { background: rgba(255, 255, 255, 0.2); }
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
    // highlight.js 深色主题覆盖
    :deep(.hljs-keyword) { color: #569cd6; }
    :deep(.hljs-string) { color: #ce9178; }
    :deep(.hljs-comment) { color: #6a9955; font-style: italic; }
    :deep(.hljs-number) { color: #b5cea8; }
    :deep(.hljs-title), :deep(.hljs-title.function_) { color: #dcdcaa; }
    :deep(.hljs-built_in) { color: #4ec9b0; }
  }
}
</style>
