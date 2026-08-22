<template>
  <div class="ann-list">
    <div v-for="ann in annotations" :key="ann.id" class="ann-item">
      <template v-if="editingId === ann.id">
        <AnnotationEditor
          :model-value="ann.contentMd"
          :saving="saving"
          @save="(c) => onEditSave(ann, c)"
          @cancel="editingId = null"
        />
      </template>
      <template v-else>
        <div class="ann-content" v-html="renderInline(ann.contentMd)" />
        <div class="ann-meta">
          <span class="meta-time">{{ formatTime(ann.createdAt) }}</span>
          <span v-if="ann.status === 'STALE'" class="stale-tag">
            <el-icon><WarningFilled /></el-icon> 原文已变更，请确认
          </span>
          <span class="spacer" />
          <el-button v-if="ann.status === 'STALE'" link size="small" type="warning" @click="$emit('confirm', ann)">确认仍然适用</el-button>
          <el-button link size="small" @click="startEdit(ann)">编辑</el-button>
          <el-button link size="small" type="danger" @click="$emit('delete', ann)">删除</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { renderInline } from '../utils/markdown'
import AnnotationEditor from './AnnotationEditor.vue'

const props = defineProps({
  annotations: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['edit', 'delete', 'confirm'])

const editingId = ref(null)

function startEdit(ann) {
  editingId.value = ann.id
}

function onEditSave(ann, content) {
  emit('edit', ann, content)
  editingId.value = null
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.ann-list {
  .ann-item {
    padding: 8px 0;
    border-top: 1px dashed var(--ak-border-2);
    &:first-child { border-top: none; }
  }
  .ann-content {
    font-size: 14px;
    line-height: 1.6;
    color: var(--ak-text-2);
    :deep(p) { margin: 0 0 6px; &:last-child { margin: 0; } }
    :deep(code) {
      background: var(--doc-code-inline-bg);
      color: var(--doc-code-inline-color);
      border-radius: 2px;
      padding: 0.1em 0.35em;
      font-size: 0.9em;
      font-family: var(--code-block-font);
    }
  }
  .ann-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 4px;
    color: var(--ak-muted);
    font-size: 12px;
    .meta-time {
      font-family: var(--code-block-font);
      font-size: 11px;
    }
    .stale-tag {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      color: var(--ak-amber);
    }
    .spacer { flex: 1; }
  }
}
</style>
