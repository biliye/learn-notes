<template>
  <div class="md-editor">
    <div class="editor-pane">
      <div class="pane-label">
        <span class="pane-dot" aria-hidden="true"></span>
        Markdown 源码
        <span class="pane-code">// SOURCE</span>
      </div>
      <textarea
        ref="textarea"
        v-model="content"
        class="editor-textarea"
        spellcheck="false"
        @keydown.ctrl.s.prevent="save"
        @keydown.tab.prevent="insertTab"
        @paste="onPaste"
        @drop.prevent="onDrop"
      />
    </div>
    <div class="preview-pane">
      <div class="pane-label">
        <span class="pane-dot pane-dot-green" aria-hidden="true"></span>
        预览（仅供预览，非权威切块）
        <span class="pane-code">// PREVIEW</span>
      </div>
      <!-- 预览用前端本地切块渲染，不产生锚点、不作为权威（D3） -->
      <div class="preview-body prose" v-html="previewHtml" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { renderBlock } from '../utils/markdown'
import { uploadImage } from '../api/upload'

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'save'])

const content = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const textarea = ref(null)

// 预览：整篇本地渲染（仅预览用）
const previewHtml = computed(() => renderBlock(content.value))

function insertTab() {
  const el = textarea.value
  const start = el.selectionStart
  const end = el.selectionEnd
  const next = content.value.slice(0, start) + '  ' + content.value.slice(end)
  content.value = next
  requestAnimationFrame(() => {
    el.selectionStart = el.selectionEnd = start + 2
  })
}

// ---- 图片粘贴 / 拖拽上传（R30）----
async function onPaste(e) {
  const files = Array.from(e.clipboardData?.items || [])
    .filter((it) => it.kind === 'file' && it.type.startsWith('image/'))
    .map((it) => it.getAsFile())
  if (files.length) {
    e.preventDefault()
    for (const f of files) await insertImage(f)
  }
}

async function onDrop(e) {
  const files = Array.from(e.dataTransfer?.files || []).filter((f) => f.type.startsWith('image/'))
  if (files.length) {
    for (const f of files) await insertImage(f)
  }
}

/** 暴露给父组件工具栏"插入图片"按钮 */
async function insertImage(file) {
  const el = textarea.value
  const start = el?.selectionStart ?? content.value.length
  const placeholder = '![上传中...]()'
  // 光标处插入占位符
  const before = content.value.slice(0, start)
  const after = content.value.slice(el?.selectionEnd ?? start)
  content.value = before + placeholder + after
  try {
    const result = await uploadImage(file)
    const md = `![${file.name || 'image'}](${result.url})`
    // 占位符替换为真实路径
    content.value = content.value.replace(placeholder, md)
    ElMessage.success('图片已上传')
  } catch (e) {
    content.value = content.value.replace(placeholder, '')
    ElMessage.error('图片上传失败')
  }
}

defineExpose({ insertImage, save: () => emit('save', content.value) })
</script>

<style scoped lang="scss">
.md-editor {
  display: flex;
  gap: 16px;
  height: calc(100vh - 260px);
  min-height: 420px;
}
.editor-pane,
.preview-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  overflow: hidden;
  background: var(--ak-bg-2);
}
.pane-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--ak-text-2);
  border-bottom: 1px solid var(--ak-border);
  background: var(--ak-bg-3);
  .pane-dot {
    width: 8px;
    height: 8px;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: var(--ak-gold);
    &.pane-dot-green {
      background: var(--ak-green);
    }
  }
  .pane-code {
    margin-left: auto;
    font-family: var(--code-block-font);
    font-size: 10px;
    letter-spacing: 1px;
    color: var(--ak-faint);
  }
}
.editor-textarea {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 16px;
  font-family: var(--code-block-font);
  font-size: 14px;
  line-height: 1.7;
  color: var(--ak-text-2);
  caret-color: var(--ak-gold);
  background: var(--ak-bg-0);
}
.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  font-size: 15px;
  line-height: 1.75;
}
</style>
