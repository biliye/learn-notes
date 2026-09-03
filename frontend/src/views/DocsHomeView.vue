<template>
  <div class="docs-home">
    <template v-if="searchQuery">
      <!-- 搜索结果 -->
      <div class="page-head ak-page-head">
        <h3>搜索「{{ searchQuery }}」</h3>
        <span class="ak-head-sub">SEARCH RESULTS</span>
        <el-button link type="primary" class="head-action" @click="backToList">← 返回列表</el-button>
      </div>
      <div v-if="searchResults.length" class="search-results">
        <div v-for="r in searchResults" :key="r.docId" class="search-item" @click="$router.push(`/docs/${r.docId}`)">
          <div class="search-breadcrumb">
            <span v-for="(b, i) in r.breadcrumb" :key="b.id">
              <template v-if="i > 0"><span class="crumb-sep" aria-hidden="true">◆</span></template>{{ b.name }}
            </span>
          </div>
          <div class="search-title">{{ r.title }}</div>
          <div class="search-snippet" v-html="highlightSnippet(r.snippet)" />
          <div class="search-go" aria-hidden="true">▸</div>
        </div>
      </div>
      <el-empty v-else description="没有命中结果" />
    </template>
    <template v-else>
      <div class="page-head ak-page-head">
        <h3>{{ currentTopic ? currentTopic.name : '全部文档' }}</h3>
        <span class="ak-head-sub">{{ currentTopic ? 'TOPIC DIRECTORY' : 'MASTER INDEX' }}</span>
        <el-button type="primary" class="head-action ak-btn-slant" @click="newDoc">
          <el-icon><Plus /></el-icon> 新建文档
        </el-button>
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
import { findRec } from '../utils/catalogTree'

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
  return findRec(catalog.tree, topicId.value)?.node || null
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
.head-action {
  margin-left: auto;
  :deep(.el-icon) { margin-right: 4px; }
}
.search-item {
  position: relative;
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-left: 3px solid var(--ak-border-2);
  border-radius: 2px;
  padding: 12px 40px 12px 16px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  &:hover {
    background: var(--ak-bg-3);
    border-left-color: var(--ak-gold);
  }
  .search-breadcrumb {
    color: var(--ak-muted);
    font-size: 12px;
    margin-bottom: 4px;
    .crumb-sep {
      color: var(--ak-gold-dim);
      font-size: 8px;
      margin: 0 6px;
      vertical-align: middle;
    }
  }
  .search-title {
    font-weight: 600;
    margin-bottom: 4px;
    color: var(--ak-text);
  }
  .search-snippet {
    color: var(--ak-text-2);
    font-size: 13px;
    line-height: 1.6;
    :deep(mark) {
      background: var(--ak-gold-glow);
      color: var(--ak-gold-bright);
      padding: 0 2px;
      border-radius: 2px;
    }
  }
  .search-go {
    position: absolute;
    right: 14px;
    top: 50%;
    transform: translateY(-50%);
    color: var(--ak-gold-dim);
    font-size: 14px;
    opacity: 0;
    transition: opacity 0.2s, transform 0.2s;
  }
  &:hover .search-go {
    opacity: 1;
    transform: translateY(-50%) translateX(3px);
  }
}

/* ---------- 移动端：页头可换行、搜索结果卡片触屏箭头常显 ---------- */
@media (max-width: 768px) {
  .docs-home {
    padding: 14px 12px;
  }
  :deep(.page-head) {
    flex-wrap: wrap;
  }
  .search-go {
    opacity: 1;
    transform: translateY(-50%);
  }
}
</style>
