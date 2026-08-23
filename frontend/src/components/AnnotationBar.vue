<template>
  <div class="ann-bar" :class="{ stale: hasStale }">
    <span class="bar-main" @click="$emit('toggle')">
      <el-icon class="bar-icon"><Lightbulb /></el-icon>
      我的见解 ({{ annotations.length }})
      <span v-if="hasStale" class="stale-mark">
        <el-icon><WarningFilled /></el-icon> 原文已变更，请确认
      </span>
    </span>
    <span class="bar-actions">
      <el-button v-if="!expanded" link size="small" @click.stop="$emit('expand-all')">全部展开</el-button>
      <el-button v-if="expanded" link size="small" @click.stop="$emit('collapse-all')">全部折叠</el-button>
      <el-button link size="small" type="primary" @click.stop="adding = !adding">
        {{ adding ? '取消' : '＋ 见解' }}
      </el-button>
    </span>
    <div v-if="adding" class="bar-editor">
      <AnnotationEditor :saving="saving" @save="onAdd" @cancel="adding = false" />
    </div>
    <div v-if="expanded" class="bar-list">
      <AnnotationList
        :annotations="annotations"
        :saving="saving"
        @edit="(a, c) => $emit('edit', a, c)"
        @confirm="(a) => $emit('confirm', a)"
        @delete="(a) => $emit('delete', a)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import AnnotationEditor from './AnnotationEditor.vue'
import AnnotationList from './AnnotationList.vue'

const props = defineProps({
  annotations: { type: Array, default: () => [] },
  expanded: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  /** 悬浮按钮触发：进入时直接打开内联编辑器 */
  autoAdd: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle', 'expand-all', 'collapse-all', 'add', 'edit', 'delete', 'confirm'])

const adding = ref(props.autoAdd)
const hasStale = computed(() => props.annotations.some((a) => a.status === 'STALE'))

function onAdd(content) {
  emit('add', content)
  adding.value = false
}
</script>

<style scoped lang="scss">
.ann-bar {
  margin: 10px 0 18px;
  border: 1px dashed var(--ak-gold-dim);
  border-radius: 2px;
  padding: 6px 12px;
  background: rgba(201, 168, 106, 0.05);
  border-left: 3px solid var(--ak-gold-dim);
  &.stale {
    border-color: var(--ak-amber);
    border-left-color: var(--ak-amber);
    background: rgba(201, 141, 61, 0.08);
  }
  .bar-main {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 13px;
    color: var(--ak-gold);
    font-weight: 600;
    user-select: none;
    .bar-icon {
      color: var(--ak-gold);
    }
    .stale-mark {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      margin-left: 8px;
      color: var(--ak-amber);
      font-weight: 400;
    }
  }
  .bar-actions {
    float: right;
    display: inline-flex;
    align-items: center;
  }
  .bar-editor {
    margin-top: 10px;
    clear: both;
  }
  .bar-list {
    margin-top: 8px;
    clear: both;
  }
}

/* ---------- 移动端：标题行与操作按钮垂直堆叠 ---------- */
@media (max-width: 768px) {
  .ann-bar {
    padding: 8px 10px;
  }
  .bar-actions {
    float: none;
    margin-top: 8px;
    flex-wrap: wrap;
  }
}
</style>
