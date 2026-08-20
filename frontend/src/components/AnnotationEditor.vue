<template>
  <div class="ann-editor">
    <el-input
      v-model="content"
      type="textarea"
      :rows="4"
      placeholder="写点个人见解（支持 Markdown）…"
      @keydown.ctrl.enter="save"
    />
    <div class="preview" v-if="content" v-html="previewHtml" />
    <div class="actions">
      <el-button size="small" type="primary" :loading="saving" @click="save">保存 (Ctrl+Enter)</el-button>
      <el-button size="small" @click="$emit('cancel')">取消</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { renderInline } from '../utils/markdown'

const props = defineProps({
  modelValue: { type: String, default: '' },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['save', 'cancel'])

const content = ref(props.modelValue || '')
const previewHtml = computed(() => (content.value ? renderInline(content.value) : ''))

function save() {
  if (!content.value.trim()) return
  emit('save', content.value)
}
</script>

<style scoped lang="scss">
.ann-editor {
  .preview {
    margin-top: 8px;
    padding: 8px 12px;
    background: #fafbfc;
    border-radius: 6px;
    border: 1px dashed var(--doc-border-color);
    font-size: 14px;
    :deep(p) { margin: 0 0 8px; &:last-child { margin: 0; } }
  }
  .actions {
    margin-top: 8px;
    display: flex;
    gap: 8px;
  }
}
</style>
