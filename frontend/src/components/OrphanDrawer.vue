<template>
  <el-drawer
    v-model="visible"
    title="游离见解（ORPHAN）"
    size="380px"
    :with-header="true"
  >
    <template #header>
      <div class="drawer-title">
        游离见解（{{ orphans.length }}）
        <el-button v-if="orphans.length" link size="small" type="primary" @click="startPick">
          {{ picking ? '取消选择' : '重新挂载' }}
        </el-button>
      </div>
    </template>
    <div v-if="picking" class="pick-hint">
      点击正文中的任意块完成挂载
    </div>
    <div v-if="!orphans.length" class="empty">🎉 没有游离见解</div>
    <div v-for="o in orphans" :key="o.id" class="orphan-item" :class="{ picking }" @click="picking && pick(o)">
      <div class="orphan-content" v-html="renderInline(o.contentMd)" />
      <div class="orphan-snippet">原文快照：{{ o.blockSnippet || '（无）' }}</div>
      <div class="orphan-meta">
        <span>{{ formatTime(o.createdAt) }}</span>
        <span class="spacer" />
        <el-button link size="small" type="primary" @click.stop="picking ? pick(o) : startPick()">重新挂载</el-button>
        <el-button link size="small" type="danger" @click="$emit('delete', o)">删除</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { renderInline } from '../utils/markdown'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orphans: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'pick', 'delete'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
const picking = ref(false)

function startPick() {
  picking.value = !picking.value
}

function pick(orphan) {
  emit('pick', orphan)
  picking.value = false
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.drawer-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.pick-hint {
  background: #f0f7ff;
  color: #409eff;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
}
.empty { color: #909399; text-align: center; padding: 30px 0; }
.orphan-item {
  border: 1px solid var(--doc-border-color);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  &.picking {
    border-color: #409eff;
    cursor: pointer;
    &:hover { background: #f0f7ff; }
  }
  .orphan-content {
    font-size: 14px;
    line-height: 1.6;
    :deep(p) { margin: 0 0 6px; &:last-child { margin: 0; } }
  }
  .orphan-snippet {
    margin-top: 6px;
    font-size: 12px;
    color: #909399;
    background: #f5f6f8;
    padding: 4px 8px;
    border-radius: 4px;
    word-break: break-all;
    max-height: 60px;
    overflow: hidden;
  }
  .orphan-meta {
    display: flex;
    align-items: center;
    margin-top: 6px;
    color: #909399;
    font-size: 12px;
    .spacer { flex: 1; }
  }
}
</style>
