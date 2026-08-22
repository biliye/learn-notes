<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-mark" aria-hidden="true">
          <span class="brand-diamond"></span>
        </div>
        <div class="brand-text">
          <div class="brand-name">learn-notes</div>
          <div class="brand-sub">TACTICAL ARCHIVE</div>
        </div>
      </div>
      <div class="sidebar-nav">
        <el-button link class="nav-btn" @click="$router.push('/docs')">
          <el-icon><FolderOpened /></el-icon> 文档库
        </el-button>
        <el-button link class="nav-btn" @click="$router.push('/catalog')">
          <el-icon><Grid /></el-icon> 分类管理
        </el-button>
        <el-button link class="nav-btn" @click="$router.push('/inbox')">
          <el-icon><Message /></el-icon> INBOX
        </el-button>
      </div>
      <div class="sidebar-label">/ 分类目录</div>
      <CatalogTree class="sidebar-tree" @select-topic="onSelectTopic" />
      <div class="sidebar-status">
        <span class="status-dot" aria-hidden="true"></span>
        <span class="status-text">SYS.ONLINE</span>
        <span class="status-ver">v0.1.0</span>
      </div>
    </aside>
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <span class="topbar-crumb" aria-hidden="true">//</span>
          <span class="topbar-tag">资料检索</span>
        </div>
        <el-input v-model="q" placeholder="搜索标题 / 正文…" clearable class="search-input"
                  @keyup.enter="doSearch" @clear="clearSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="topbar-right">
          <el-button class="export-btn" @click="downloadExport">
            <el-icon><Download /></el-icon> 导出备份包
          </el-button>
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <span class="user-avatar" aria-hidden="true">{{ initial }}</span>
              {{ auth.user?.nickname || auth.user?.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
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
import { ref, computed, onMounted } from 'vue'
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

const initial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '?'
  return String(name).charAt(0).toUpperCase()
})

onMounted(() => {
  if (!catalog.loaded) catalog.load()
})

function onSelectTopic(node) {
  router.push({ path: '/docs', query: { topicId: node.id } })
}

async function doSearch() {
  if (!q.value.trim()) return
  await apiSearch(q.value.trim(), 20)
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
/* ---- 侧栏：深色战术面板 ---- */
.sidebar {
  width: var(--sidebar-width);
  background: var(--ak-bg-2);
  border-right: 1px solid var(--ak-border);
  display: flex;
  flex-direction: column;
  position: relative;
  /* 侧栏顶部金色刻线 */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, var(--ak-gold), transparent 70%);
  }
}
.brand-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px 12px;
  .brand-mark {
    width: 34px;
    height: 34px;
    display: flex;
    align-items: center;
    justify-content: center;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: linear-gradient(135deg, var(--ak-gold-bright), var(--ak-gold-dim));
    flex-shrink: 0;
  }
  .brand-diamond {
    width: 14px;
    height: 14px;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: var(--ak-bg-2);
  }
  .brand-text {
    min-width: 0;
  }
  .brand-name {
    font-family: var(--ak-font-display);
    font-weight: 700;
    font-size: 18px;
    letter-spacing: 1px;
    line-height: 1.1;
    color: var(--ak-text);
    text-transform: uppercase;
  }
  .brand-sub {
    font-family: var(--code-block-font);
    font-size: 10px;
    letter-spacing: 2px;
    color: var(--ak-gold-dim);
    text-transform: uppercase;
  }
}
.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 12px 10px;
  border-bottom: 1px solid var(--ak-border);
  .nav-btn {
    justify-content: flex-start;
    gap: 8px;
    color: var(--ak-text-2);
    font-size: 13px;
    padding: 8px 10px;
    border-radius: 0;
    transition: background 0.2s, color 0.2s;
    &:hover {
      background: var(--ak-bg-3);
      color: var(--ak-gold);
    }
  }
}
.sidebar-label {
  padding: 14px 16px 6px;
  font-family: var(--code-block-font);
  font-size: 11px;
  color: var(--ak-faint);
  letter-spacing: 1px;
  text-transform: uppercase;
}
.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
}
.sidebar-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--ak-border);
  font-family: var(--code-block-font);
  font-size: 11px;
  .status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--ak-green);
    box-shadow: 0 0 6px rgba(94, 138, 109, 0.8);
  }
  .status-text {
    color: var(--ak-text-2);
    letter-spacing: 1px;
  }
  .status-ver {
    margin-left: auto;
    color: var(--ak-faint);
  }
}

/* ---- 主区 ---- */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: var(--topbar-height);
  background: rgba(27, 30, 39, 0.85);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--ak-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 16px;
  .topbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    .topbar-crumb {
      font-family: var(--code-block-font);
      color: var(--ak-gold-dim);
      font-size: 13px;
    }
    .topbar-tag {
      font-family: var(--ak-font-display);
      font-size: 14px;
      letter-spacing: 1px;
      color: var(--ak-muted);
      text-transform: uppercase;
    }
  }
}
.search-input {
  width: 320px;
  :deep(.el-input__wrapper) {
    background: var(--ak-bg-2);
    box-shadow: 0 0 0 1px var(--ak-border) inset;
    border-radius: 2px;
  }
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.export-btn {
  :deep(.el-icon) { margin-right: 4px; }
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--ak-text-2);
  font-size: 13px;
  outline: none;
  .user-avatar {
    width: 26px;
    height: 26px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    clip-path: polygon(25% 0, 100% 0, 75% 100%, 0 100%);
    background: linear-gradient(135deg, var(--ak-gold), var(--ak-gold-dim));
    color: var(--ak-bg-0);
    font-family: var(--ak-font-display);
    font-weight: 700;
    font-size: 14px;
  }
  &:hover {
    color: var(--ak-gold);
  }
}
.content {
  flex: 1;
  overflow-y: auto;
}

@media (max-width: 900px) {
  .sidebar {
    width: 220px;
  }
  .search-input {
    width: 200px;
  }
  .brand-sub {
    display: none;
  }
}
</style>
