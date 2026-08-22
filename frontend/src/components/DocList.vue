<template>
  <div class="doc-list">
    <el-empty v-if="!items.length" description="该方向下还没有文档" />
    <div v-for="doc in items" :key="doc.id" class="doc-card" @click="$router.push(`/docs/${doc.id}`)">
      <div class="doc-ribbon" aria-hidden="true"></div>
      <div class="doc-title">{{ doc.title }}</div>
      <div v-if="doc.summary" class="doc-summary">{{ doc.summary }}</div>
      <div class="doc-meta">
        <el-tag v-for="t in doc.tags" :key="t" size="small" type="info" class="tag">{{ t }}</el-tag>
        <span class="meta-ver">v{{ doc.currentVersion }}</span>
        <span class="meta-item">{{ formatTime(doc.updatedAt) }}</span>
        <span class="spacer" />
        <slot name="actions" :doc="doc" />
      </div>
    </div>
    <el-pagination
      v-if="total > size"
      class="pager"
      layout="prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPage"
    />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { listDocs } from '../api/doc'

const props = defineProps({
  topicId: { type: [Number, String], default: null },
  categoryId: { type: [Number, String], default: null }
})

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

async function load() {
  const data = await listDocs({
    topicId: props.topicId || undefined,
    categoryId: props.categoryId || undefined,
    page: page.value,
    size: size.value
  })
  items.value = data.items || []
  total.value = data.total || 0
}

function onPage(p) {
  page.value = p
  load()
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}

watch(() => props.topicId, () => { page.value = 1; load() })
onMounted(load)

defineExpose({ load })
</script>

<style scoped lang="scss">
.doc-card {
  position: relative;
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  padding: 14px 16px 14px 18px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  overflow: hidden;
  &:hover {
    background: var(--ak-bg-3);
    border-color: var(--ak-border-2);
  }
  /* 悬浮左侧金条 */
  .doc-ribbon {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: linear-gradient(180deg, var(--ak-gold-bright), var(--ak-gold-dim));
    opacity: 0;
    transition: opacity 0.2s;
  }
  &:hover .doc-ribbon {
    opacity: 1;
  }
  .doc-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 6px;
    color: var(--ak-text);
  }
  .doc-summary {
    color: var(--ak-text-2);
    font-size: 13px;
    margin-bottom: 8px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .doc-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    .tag {
      margin-right: 2px;
      border-radius: 0;
    }
    .meta-ver {
      color: var(--ak-gold);
      font-family: var(--code-block-font);
      font-size: 12px;
    }
    .meta-item {
      color: var(--ak-muted);
      font-family: var(--code-block-font);
      font-size: 12px;
    }
    .spacer { flex: 1; }
  }
}
.pager {
  margin-top: 16px;
  justify-content: center;
  :deep(.el-pager li) {
    background: var(--ak-bg-2);
    border: 1px solid var(--ak-border);
    border-radius: 2px;
    margin: 0 3px;
    &.is-active {
      background: var(--ak-gold);
      color: var(--ak-bg-0);
      font-weight: 700;
    }
  }
  :deep(button.btn-prev),
  :deep(button.btn-next) {
    background: var(--ak-bg-2);
    border: 1px solid var(--ak-border);
    border-radius: 2px;
  }
}
</style>
