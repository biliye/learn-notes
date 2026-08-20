<template>
  <div class="doc-list">
    <el-empty v-if="!items.length" description="该方向下还没有文档" />
    <div v-for="doc in items" :key="doc.id" class="doc-card" @click="$router.push(`/docs/${doc.id}`)">
      <div class="doc-title">{{ doc.title }}</div>
      <div v-if="doc.summary" class="doc-summary">{{ doc.summary }}</div>
      <div class="doc-meta">
        <el-tag v-for="t in doc.tags" :key="t" size="small" type="info" class="tag">{{ t }}</el-tag>
        <span class="meta-item">v{{ doc.currentVersion }}</span>
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
  background: #fff;
  border: 1px solid var(--doc-border-color);
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  &:hover {
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  }
  .doc-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 6px;
  }
  .doc-summary {
    color: #606266;
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
    .tag { margin-right: 2px; }
    .meta-item {
      color: #909399;
      font-size: 12px;
    }
    .spacer { flex: 1; }
  }
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
