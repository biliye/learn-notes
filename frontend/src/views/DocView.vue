<template>
  <div v-if="doc" class="doc-view">
    <div class="doc-layout">
      <div class="doc-main">
        <div class="doc-header">
          <el-breadcrumb separator="◆">
            <el-breadcrumb-item v-for="(b, i) in doc.breadcrumb" :key="i">{{ b.name }}</el-breadcrumb-item>
          </el-breadcrumb>
          <h1 class="doc-title">{{ doc.title }}</h1>
          <div class="doc-sub">
            <el-tag v-for="t in doc.tags" :key="t" size="small" type="info" class="tag">{{ t }}</el-tag>
            <span class="meta meta-ver">VER. v{{ doc.currentVersion }}</span>
            <span class="meta">更新于 {{ formatTime(doc.updatedAt) }}</span>
          </div>
          <div class="doc-actions">
            <el-button size="small" class="action-btn" @click="$router.push(`/docs/${doc.id}/edit`)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button size="small" class="action-btn" @click="showVersions = true">
              <el-icon><Clock /></el-icon> 历史版本
            </el-button>
            <el-button size="small" class="action-btn" @click="downloadRaw">
              <el-icon><Download /></el-icon> 下载 md
            </el-button>
            <el-button size="small" type="danger" plain class="action-btn" @click="removeDoc">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </div>
        </div>

        <MarkdownDoc
          ref="markdownDoc"
          :doc-id="doc.id"
          :blocks="doc.blocks"
          :annotations="doc.annotations"
          @add="onAddAnnotation"
          @edit="onEditAnnotation"
          @confirm="onConfirmAnnotation"
          @delete="onDeleteAnnotation"
          @pick="onPickAnchor"
        />

        <div v-if="orphans.length" class="orphan-entry">
          <el-badge :value="orphans.length" type="danger">
            <el-button size="small" @click="showOrphans = true">
              <el-icon><WarningFilled /></el-icon> 游离见解
            </el-button>
          </el-badge>
        </div>
      </div>
      <TocSidebar :blocks="doc.blocks" />
    </div>

    <VersionDialog v-model="showVersions" :doc-id="doc.id" />
    <OrphanDrawer
      v-model="showOrphans"
      :orphans="orphans"
      @pick="startPickMode"
      @delete="onDeleteAnnotation"
    />
  </div>
  <el-skeleton v-else :rows="10" animated />
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownDoc from '../components/MarkdownDoc.vue'
import TocSidebar from '../components/TocSidebar.vue'
import VersionDialog from '../components/VersionDialog.vue'
import OrphanDrawer from '../components/OrphanDrawer.vue'
import {
  getDoc, deleteDoc, downloadRaw as apiDownloadRaw
} from '../api/doc'
import {
  createAnnotation, updateAnnotation, confirmAnnotation,
  reanchorAnnotation, deleteAnnotation
} from '../api/annotation'
import { useCatalogStore } from '../stores/catalog'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

const doc = ref(null)
const showVersions = ref(false)
const showOrphans = ref(false)
const markdownDoc = ref(null)

const orphans = computed(() => (doc.value?.annotations || []).filter((a) => a.status === 'ORPHAN'))

async function load() {
  doc.value = await getDoc(route.params.id)
}

onMounted(async () => {
  if (!catalog.loaded) catalog.load()
  await load()
})

watch(() => route.params.id, load)

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}

async function downloadRaw() {
  const raw = await apiDownloadRaw(doc.value.id)
  const blob = new Blob([raw], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${doc.value.slug}.md`
  a.click()
  URL.revokeObjectURL(url)
}

async function removeDoc() {
  try {
    await ElMessageBox.confirm('删除后版本与见解将一并删除，确认？', '删除文档', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteDoc(doc.value.id)
  ElMessage.success('已删除')
  router.push('/docs')
}

// ---- 见解操作 ----
async function onAddAnnotation(anchor, content) {
  await createAnnotation({ docId: doc.value.id, anchor, contentMd: content })
  ElMessage.success('见解已添加')
  await load()
}

async function onEditAnnotation(ann, content) {
  await updateAnnotation(ann.id, content)
  ElMessage.success('已保存')
  await load()
}

async function onConfirmAnnotation(ann) {
  await confirmAnnotation(ann.id)
  ElMessage.success('已确认，见解保持挂载')
  await load()
}

async function onDeleteAnnotation(ann) {
  await deleteAnnotation(ann.id)
  ElMessage.success('已删除见解')
  await load()
}

function startPickMode(orphan) {
  ElMessage.info('点击正文任意块完成挂载')
  markdownDoc.value?.startPick()
  showOrphans.value = false
  // 记住待挂载的见解
  pendingOrphan.value = orphan
}

const pendingOrphan = ref(null)

async function onPickAnchor(anchor) {
  if (!pendingOrphan.value) return
  await reanchorAnnotation(pendingOrphan.value.id, anchor)
  pendingOrphan.value = null
  ElMessage.success('已重新挂载')
  await load()
}
</script>

<style scoped lang="scss">
.doc-view {
  padding: 24px 0 60px;
}
.doc-layout {
  display: flex;
  gap: 24px;
  max-width: 1160px;
  margin: 0 auto;
  padding: 0 20px;
  align-items: flex-start;
}
.doc-main {
  flex: 1;
  min-width: 0;
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  padding: 28px 32px 32px;
  position: relative;
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, var(--ak-gold), transparent 45%);
  }
}
.doc-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--ak-border);
  :deep(.el-breadcrumb__inner) {
    color: var(--ak-muted);
    font-size: 13px;
  }
  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: var(--ak-gold);
  }
  .doc-title {
    margin: 12px 0 10px;
    font-family: var(--ak-font-display);
    font-size: 30px;
    font-weight: 700;
    letter-spacing: 1px;
    line-height: 1.3;
    color: var(--ak-text);
  }
  .doc-sub {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--ak-muted);
    font-size: 13px;
    margin-bottom: 16px;
    .tag {
      border-radius: 0;
      background: rgba(201, 168, 106, 0.08);
      border-color: var(--ak-gold-dim);
      color: var(--ak-gold);
    }
    .meta-ver {
      color: var(--ak-gold);
      font-family: var(--code-block-font);
    }
    .meta {
      font-family: var(--code-block-font);
      font-size: 12px;
    }
  }
  .doc-actions {
    display: flex;
    gap: 8px;
    .action-btn {
      border-radius: 2px;
      :deep(.el-icon) { margin-right: 4px; }
    }
  }
}
.orphan-entry {
  margin-top: 24px;
}
</style>
