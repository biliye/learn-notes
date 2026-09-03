<template>
  <div class="layout">
    <!-- 移动端遮罩：抽屉展开时点击关闭 -->
    <transition name="fade">
      <div v-if="sidebarOpen" class="backdrop" @click="sidebarOpen = false" aria-hidden="true"></div>
    </transition>

    <aside class="sidebar" :class="{ open: sidebarOpen }">
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
        <el-button link class="nav-btn" @click="go('/docs')">
          <el-icon><FolderOpened /></el-icon> 文档库
        </el-button>
        <el-button link class="nav-btn" @click="go('/catalog')">
          <el-icon><Grid /></el-icon> 分类管理
        </el-button>
        <el-button link class="nav-btn" @click="go('/inbox')">
          <el-icon><Message /></el-icon> INBOX
        </el-button>
        <el-button v-if="auth.isAdmin" link class="nav-btn" @click="go('/admin/docs')">
          <el-icon><DataAnalysis /></el-icon> 全部文档
        </el-button>
        <!-- 移动端：导出备份包入口（桌面在顶栏） -->
        <el-button link class="nav-btn mobile-export" @click="downloadExport">
          <el-icon><Download /></el-icon> 导出备份包
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
          <button class="hamburger" aria-label="打开导航" @click="sidebarOpen = true">
            <el-icon :size="18"><Menu /></el-icon>
          </button>
          <span class="topbar-crumb" aria-hidden="true">//</span>
          <span class="topbar-tag">资料检索</span>
        </div>
        <el-input v-model="q" placeholder="搜索标题 / 正文…" clearable class="search-input"
                  @keyup.enter="doSearch" @clear="clearSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="topbar-right">
          <el-button class="quick-start-btn" @click="quickStartOpen = true">
            <el-icon><QuestionFilled /></el-icon><span class="qs-label">快速开始</span>
          </el-button>
          <el-button class="export-btn" @click="downloadExport">
            <el-icon><Download /></el-icon> 导出备份包
          </el-button>
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <span class="user-avatar" aria-hidden="true">{{ initial }}</span>
              <span class="user-name">{{ auth.user?.nickname || auth.user?.username }}</span>
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

    <!-- 快速开始：项目用法 + Agent 笔记写作指南下载 -->
    <el-dialog v-model="quickStartOpen" title="快速开始" width="680px"
               class="quick-start-dialog" :append-to-body="true">
      <div class="qs-intro">
        这是你的个人学习笔记网站：内容按 <b>大类 →（多级目录）→ 文档</b> 归置——
        新建大类时可指定该大类下允许几层子目录（默认两层），文档放在"没有子目录"的目录里；
        以 Markdown 呈现（代码与正文差异化渲染），并支持在任意正文块上写可折叠的个人见解。
      </div>
      <div class="qs-block">
        <div class="qs-block-title"><el-icon><Search /></el-icon> 浏览与搜索</div>
        <ul class="qs-list">
          <li>左侧目录树可展开任意深度的目录，点开"存放文档的目录"即在「文档库」阅读该目录下的文档。</li>
          <li>顶栏搜索框可同时搜标题与正文。</li>
        </ul>
      </div>
      <div class="qs-block">
        <div class="qs-block-title"><el-icon><EditPen /></el-icon> 写笔记</div>
        <ul class="qs-list">
          <li>「文档库」页点「新建文档」：手写 Markdown（源码 / 预览双栏），或点「一键导入压缩包」解析 .zip（md + 图片）。</li>
          <li>保存后按 front-matter 的 <code>path</code>（目录链）或文件名自动归入对应目录，无需手动归类。</li>
        </ul>
      </div>
      <div class="qs-block">
        <div class="qs-block-title"><el-icon><ChatDotRound /></el-icon> 个人见解</div>
        <ul class="qs-list">
          <li>阅读时可在任意正文块上写可折叠的批注；文档被重写后系统会尽力把见解重新挂回原段落。</li>
        </ul>
      </div>
      <div class="qs-block">
        <div class="qs-block-title"><el-icon><Tools /></el-icon> 管理</div>
        <ul class="qs-list">
          <li>「分类管理」维护多级目录（新建大类时选目录层级，可随时整体移动某个目录）；「INBOX」存放未归类的文档；顶栏「导出备份包」可一键全量下载。</li>
        </ul>
      </div>
      <div class="qs-block qs-agent">
        <div class="qs-block-title"><el-icon><MagicStick /></el-icon> 让 AI 帮你写笔记</div>
        <p class="qs-note">下载下面的写作指南，把这份 .md 文件交给负责写笔记的 agent：agent 按指南的格式编写笔记（front-matter + 正文），通过接口上传后系统自动归类入库。</p>
        <a class="qs-download" href="/learn-notes-agent-guide.md" download="learn-notes-agent-guide.md">
          <el-icon><Download /></el-icon> 下载 Agent 笔记写作指南（.md）
        </a>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
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
const sidebarOpen = ref(false)
const quickStartOpen = ref(false)

const initial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '?'
  return String(name).charAt(0).toUpperCase()
})

// 路由变化时自动收起移动端抽屉
watch(() => router.currentRoute.value.fullPath, () => {
  sidebarOpen.value = false
})

onMounted(() => {
  if (!catalog.loaded) catalog.load()
})

function go(path) {
  sidebarOpen.value = false
  router.push(path)
}

function onSelectTopic(node) {
  sidebarOpen.value = false
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
/* 移动端专用入口：桌面隐藏 */
.mobile-export {
  display: none;
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
/* 移动端汉堡按钮：桌面隐藏 */
.hamburger {
  display: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--ak-border);
  background: var(--ak-bg-2);
  color: var(--ak-gold);
  border-radius: 2px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  &:hover {
    background: var(--ak-bg-3);
    color: var(--ak-gold-bright);
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
.quick-start-btn {
  :deep(.el-icon) { margin-right: 4px; }
}
/* 快速开始弹窗 */
.quick-start-dialog {
  :deep(.el-dialog) {
    background: var(--ak-bg-2);
    border: 1px solid var(--ak-border-2);
    border-radius: 2px;
    color: var(--ak-text);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
  }
  :deep(.el-dialog__title) {
    color: var(--ak-text);
    font-family: var(--ak-font-display);
    letter-spacing: 1px;
  }
  :deep(.el-dialog__header) {
    border-bottom: 1px solid var(--ak-border);
  }
  :deep(.el-dialog__body) {
    padding: 18px 20px 20px;
    max-height: 70vh;
    overflow-y: auto;
  }
  .qs-intro {
    font-size: 13px;
    line-height: 1.7;
    color: var(--ak-text-2);
    padding: 4px 2px 12px;
    border-bottom: 1px dashed var(--ak-border);
    b {
      color: var(--ak-gold);
      font-weight: 600;
    }
  }
  .qs-block {
    margin-top: 14px;
    .qs-block-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-family: var(--ak-font-display);
      font-size: 14px;
      letter-spacing: 0.5px;
      color: var(--ak-gold);
      .el-icon {
        font-size: 15px;
      }
    }
    .qs-list {
      margin: 8px 0 0;
      padding-left: 18px;
      font-size: 13px;
      line-height: 1.75;
      color: var(--ak-text-2);
    }
    .qs-note {
      margin: 8px 0 12px;
      font-size: 13px;
      line-height: 1.75;
      color: var(--ak-text-2);
    }
  }
  .qs-agent {
    margin-top: 16px;
    padding: 12px 14px;
    background: var(--ak-bg-3);
    border: 1px solid var(--ak-border);
    border-left: 2px solid var(--ak-gold);
  }
  .qs-download {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 14px;
    font-size: 13px;
    color: var(--ak-bg-0);
    background: linear-gradient(135deg, var(--ak-gold-bright), var(--ak-gold-dim));
    border-radius: 2px;
    text-decoration: none;
    transition: filter 0.2s;
    &:hover {
      filter: brightness(1.1);
    }
    .el-icon {
      font-size: 14px;
    }
  }
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
  .user-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &:hover {
    color: var(--ak-gold);
  }
}
.content {
  flex: 1;
  overflow-y: auto;
}

/* 移动端遮罩 + 抽屉过渡 */
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(8, 10, 14, 0.6);
  z-index: 1190;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
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

/* ---------- 移动端：侧栏变抽屉，顶栏重排 ---------- */
@media (max-width: 768px) {
  .layout {
    display: block;
    height: 100%;
  }
  .main {
    height: 100%;
  }
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: var(--sidebar-width-mobile);
    max-width: 82vw;
    z-index: 1200;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.45);
    &.open {
      transform: translateX(0);
    }
  }
  .hamburger {
    display: inline-flex;
  }
  .mobile-export {
    display: flex;
    margin-top: 2px;
  }
  .export-btn {
    display: none;
  }
  .quick-start-btn {
    .qs-label {
      display: none;
    }
  }
  .topbar {
    padding: 0 10px;
    gap: 8px;
  }
  .topbar-left {
    flex-shrink: 0;
  }
  .topbar-tag {
    display: none;
  }
  .search-input {
    flex: 1;
    width: auto;
    min-width: 0;
  }
  .topbar-right {
    gap: 6px;
    flex-shrink: 0;
  }
  .user-name {
    max-width: 72px;
  }
}

@media (max-width: 480px) {
  .user-name,
  .topbar-crumb {
    display: none;
  }
}
</style>
