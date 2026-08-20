<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-title">
        <span class="brand">📚 learn-notes</span>
        <el-button link type="primary" size="small" @click="$router.push('/catalog')">分类管理</el-button>
      </div>
      <CatalogTree class="sidebar-tree" @select-topic="onSelectTopic" />
    </aside>
    <div class="main">
      <header class="topbar">
        <el-input v-model="q" placeholder="搜索标题 / 正文…" clearable class="search-input"
                  @keyup.enter="doSearch" @clear="clearSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="topbar-right">
          <el-button link @click="downloadExport">导出备份包</el-button>
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-icon><User /></el-icon> {{ auth.user?.nickname || auth.user?.username }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CatalogTree from '../components/CatalogTree.vue'
import { useAuthStore } from '../stores/auth'
import { useCatalogStore } from '../stores/catalog'
import { search as apiSearch, downloadExportZip } from '../api/doc'

const router = useRouter()
const auth = useAuthStore()
const catalog = useCatalogStore()

const q = ref('')

onMounted(() => {
  if (!catalog.loaded) catalog.load()
})

function onSelectTopic(node) {
  router.push({ path: '/docs', query: { topicId: node.id } })
}

async function doSearch() {
  if (!q.value.trim()) return
  const hits = await apiSearch(q.value.trim(), 20)
  // 搜索结果走独立路由参数
  router.push({ path: '/docs', query: { search: q.value.trim() } })
}

function clearSearch() {
  router.push({ path: '/docs' })
}

async function downloadExport() {
  try {
    await downloadExportZip()
    ElMessage.success('导出包已下载')
  } catch (e) {
    // 拦截器已提示
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100%;
}
.sidebar {
  width: var(--sidebar-width);
  background: #fff;
  border-right: 1px solid var(--doc-border-color);
  display: flex;
  flex-direction: column;
}
.sidebar-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
  .brand {
    font-weight: 700;
    font-size: 15px;
  }
}
.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: var(--topbar-height);
  background: #fff;
  border-bottom: 1px solid var(--doc-border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 16px;
}
.search-input {
  width: 320px;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
}
.content {
  flex: 1;
  overflow-y: auto;
}
</style>
