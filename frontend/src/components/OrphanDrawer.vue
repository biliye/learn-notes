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
      <el-icon class="pick-icon"><Aim /></el-icon>
      点击正文中的任意块完成挂载
    </div>
    <div v-if="!orphans.length" class="empty">
      <el-icon class="empty-icon"><CircleCheck /></el-icon>
      没有游离见解
    </div>
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
  font-family: var(--ak-font-display);
  letter-spacing: 1px;
  color: var(--ak-gold);
}
.pick-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(201, 168, 106, 0.08);
  color: var(--ak-gold);
  padding: 8px 12px;
  border: 1px dashed var(--ak-gold-dim);
  border-radius: 2px;
  margin-bottom: 12px;
  font-size: 13px;
  .pick-icon { color: var(--ak-gold); }
}
.empty {
  color: var(--ak-muted);
  text-align: center;
  padding: 30px 0;
  .empty-icon {
    font-size: 28px;
    color: var(--ak-green);
    display: block;
    margin-bottom: 8px;
  }
}
.orphan-item {
  border: 1px solid var(--ak-border);
  border-left: 3px solid var(--ak-amber);
  border-radius: 2px;
  background: var(--ak-bg-2);
  padding: 10px 12px;
  margin-bottom: 10px;
  &.picking {
    border-color: var(--ak-gold-dim);
    cursor: pointer;
    &:hover { background: var(--ak-bg-3); }
  }
  .orphan-content {
    font-size: 14px;
    line-height: 1.6;
    color: var(--ak-text-2);
    :deep(p) { margin: 0 0 6px; &:last-child { margin: 0; } }
  }
  .orphan-snippet {
    margin-top: 6px;
    font-size: 12px;
    color: var(--ak-muted);
    font-family: var(--code-block-font);
    background: var(--ak-bg-0);
    border: 1px solid var(--ak-border);
    padding: 4px 8px;
    border-radius: 2px;
    word-break: break-all;
    max-height: 60px;
    overflow: hidden;
  }
  .orphan-meta {
    display: flex;
    align-items: center;
    margin-top: 6px;
    color: var(--ak-muted);
    font-size: 12px;
    font-family: var(--code-block-font);
    .spacer { flex: 1; }
  }
}
</style>
