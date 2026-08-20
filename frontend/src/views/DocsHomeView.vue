<template>
  <div class="docs-home">
    <template v-if="searchQuery">
      <!-- 搜索结果 -->
      <div class="page-head">
        <h3>搜索「{{ searchQuery }}」的结果</h3>
        <el-button link type="primary" @click="backToList">返回列表</el-button>
      </div>
      <div v-if="searchResults.length" class="search-results">
        <div v-for="r in searchResults" :key="r.docId" class="search-item" @click="$router.push(`/docs/${r.docId}`)">
          <div class="search-breadcrumb">
            <span v-for="(b, i) in r.breadcrumb" :key="b.id">
              <template v-if="i > 0"> / </template>{{ b.name }}
            </span>
          </div>
          <div class="search-title">{{ r.title }}</div>
          <div class="search-snippet" v-html="highlightSnippet(r.snippet)" />
        </div>
      </div>
      <el-empty v-else description="没有命中结果" />
    </template>
    <template v-else>
      <div class="page-head">
        <h3>{{ currentTopic ? currentTopic.name : '全部文档' }}</h3>
        <el-button type="primary" @click="newDoc">＋ 新建文档</el-button>
      </div>
      <DocList :topic-id="topicId" :category-id="categoryId" ref="docList" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DocList from '../components/DocList.vue'
import { search as apiSearch } from '../api/doc'
import { useCatalogStore } from '../stores/catalog'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

const topicId = ref(null)
const categoryId = ref(null)
const searchQuery = ref('')
const searchResults = ref([])
const docList = ref(null)

const currentTopic = computed(() => {
  if (!topicId.value) return null
  for (const c of catalog.tree) {
    const found = (c.children || []).find((t) => t.id === Number(topicId.value))
    if (found) return found
  }
  return null
})

watch(() => route.query, async (q) => {
  topicId.value = q.topicId || null
  categoryId.value = q.categoryId || null
  searchQuery.value = q.search || ''
  if (searchQuery.value) {
    const hits = await apiSearch(searchQuery.value, 20)
    searchResults.value = hits
  }
}, { immediate: true })

onMounted(() => {
  if (!catalog.loaded) catalog.load()
})

function newDoc() {
  if (topicId.value) {
    router.push({ path: '/docs/new', query: { topicId: topicId.value } })
  } else {
    router.push('/docs/new')
  }
}

function backToList() {
  router.push({ path: '/docs' })
}

function highlightSnippet(snippet) {
  if (!snippet) return ''
  // **词** → <mark>词</mark>
  return snippet.replace(/\*\*(.+?)\*\*/g, '<mark>$1</mark>')
}
</script>

<style scoped lang="scss">
.docs-home {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  h3 { margin: 0; }
}
.search-item {
  background: #fff;
  border: 1px solid var(--doc-border-color);
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 10px;
  cursor: pointer;
  &:hover { box-shadow: 0 2px 10px rgba(0,0,0,0.08); }
  .search-breadcrumb { color: #909399; font-size: 12px; margin-bottom: 4px; }
  .search-title { font-weight: 600; margin-bottom: 4px; }
  .search-snippet {
    color: #606266;
    font-size: 13px;
    line-height: 1.6;
    :deep(mark) {
      background: #ffe58f;
      padding: 0 2px;
      border-radius: 2px;
    }
  }
}
</style>
