<template>
  <div class="doc-edit">
    <div class="edit-bar">
      <span class="edit-bar-title">
        <span class="edit-bar-mark" aria-hidden="true"></span>
        {{ isEdit ? '编辑文档 / EDIT DOC' : '新建文档 / NEW DOC' }}
      </span>
      <span class="edit-bar-sub">AUTHORING TERMINAL</span>
    </div>
    <div class="edit-head">
      <template v-if="!isEdit">
        <el-tree-select
          v-model="form.topicId"
          :data="docTargetTree"
          node-key="id"
          :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
          check-strictly
          clearable
          filterable
          default-expand-all
          class="dir-select"
          placeholder="文档存放目录（选择无子目录的目录）"
        />
        <el-button size="small" :loading="creatingDir" @click="openDirDialog">
          <el-icon><Plus /></el-icon> 建子目录
        </el-button>
        <span v-if="targetPath" class="dir-path" :title="targetPath">{{ targetPath }}</span>
        <el-upload :show-file-list="false" accept=".zip" :auto-upload="false" :on-change="onPickZip">
          <el-button :loading="importing">
            <el-icon><Box /></el-icon> 一键导入压缩包
          </el-button>
        </el-upload>
      </template>
      <el-input v-model="form.title" placeholder="文档标题" class="title-input" maxlength="200" />
      <!-- 编辑模式下后端更新接口不接收 slug（改动会破坏已有链接/导出路径），故只读展示 -->
      <el-input v-model="form.slug" placeholder="slug（留空自动生成）" class="slug-input" maxlength="120" :disabled="isEdit" />
      <el-input v-model="changeNote" placeholder="本次更新说明（可选）" class="note-input" maxlength="200" />
      <el-button type="primary" class="save-btn ak-btn-slant" :loading="saving" @click="save">
        <el-icon><Check /></el-icon> 保存 (Ctrl+S)
      </el-button>
      <el-button @click="cancel">取消</el-button>
      <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="onPickImage">
        <el-button>
          <el-icon><Picture /></el-icon> 插入图片
        </el-button>
      </el-upload>
    </div>
    <MarkdownEditor ref="editor" v-model="form.contentMd" @save="save" />

    <!-- 快捷建子目录：用于当前没有更深目录可放文档时继续细分 -->
    <el-dialog v-model="dirDialog.visible" title="新建子目录（继续细分）" width="min(440px, 92vw)">
      <el-form label-width="88px" @submit.prevent>
        <el-form-item label="上级目录">
          <el-tree-select
            v-model="dirDialog.parentId"
            :data="expandableTree"
            node-key="id"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            check-strictly
            clearable
            filterable
            default-expand-all
            class="full-width"
            placeholder="选择要在哪个目录下新建"
          />
        </el-form-item>
        <el-form-item label="目录名称">
          <el-input v-model="dirDialog.name" maxlength="80" placeholder="新子目录名称" @keyup.enter="confirmCreateDir" />
        </el-form-item>
      </el-form>
      <div v-if="!dirDialog.parentId" class="dir-dialog-hint">先在左侧选一个还能继续细分的上级目录（含文档的目录不能细分）。</div>
      <template #footer>
        <el-button @click="dirDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="creatingDir" @click="confirmCreateDir">创建并作为文档目录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import { getDoc, createDoc, updateDoc, downloadRaw } from '../api/doc'
import { importZip } from '../api/import'
import { createNode } from '../api/catalog'
import { useCatalogStore } from '../stores/catalog'
import { findRec, isDocTarget, canAddChild, pathLabel, disabledTree, indexTree } from '../utils/catalogTree'

const route = useRoute()
const router = useRouter()
const catalog = useCatalogStore()

const isEdit = computed(() => !!route.params.id && route.params.id !== 'new')
const form = ref({
  topicId: route.query.topicId ? Number(route.query.topicId) : null,
  title: '',
  slug: '',
  contentMd: ''
})
const changeNote = ref('')
const saving = ref(false)
const editor = ref(null)
const creatingDir = ref(false)
const importing = ref(false)
const dirDialog = ref({ visible: false, parentId: null, name: '' })

/** 只能选"可放文档的叶目录"（非顶层、无子目录） */
const docTargetTree = computed(() =>
  disabledTree(catalog.tree, (n) => !isDocTarget(n)))

const targetRec = computed(() => findRec(catalog.tree, form.value.topicId))
const targetPath = computed(() => (targetRec.value ? pathLabel(targetRec.value) : ''))

/** 还能新建子目录的节点（供"建子目录"弹窗选上级） */
const recIndex = computed(() => {
  const map = new Map()
  for (const rec of indexTree(catalog.tree)) map.set(Number(rec.node.id), rec)
  return map
})
const expandableTree = computed(() =>
  disabledTree(catalog.tree, (n) => !canAddChild(recIndex.value.get(Number(n.id)))))

onMounted(async () => {
  if (!catalog.loaded) await catalog.load()
  if (isEdit.value) {
    const doc = await getDoc(route.params.id)
    const leaf = doc.breadcrumb?.[doc.breadcrumb.length - 1]
    form.value = {
      topicId: leaf?.id ?? null,
      title: doc.title,
      slug: doc.slug,
      contentMd: await rawFor(doc.id)
    }
  }
})

async function rawFor(id) {
  return downloadRaw(id)
}

async function save() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!isEdit.value && !form.value.topicId) {
    ElMessage.warning('请选择文档存放目录（需是大类下没有子目录的目录）；目录不够深时可点「建子目录」继续细分')
    return
  }
  saving.value = true
  try {
    let id
    if (isEdit.value) {
      await updateDoc(route.params.id, {
        title: form.value.title,
        slug: form.value.slug || undefined,
        contentMd: form.value.contentMd,
        changeNote: changeNote.value || undefined
      })
      id = route.params.id
    } else {
      const data = await createDoc({
        topicId: form.value.topicId,
        title: form.value.title,
        slug: form.value.slug || undefined,
        contentMd: form.value.contentMd
      })
      id = data.docId
    }
    ElMessage.success('已保存')
    router.push(`/docs/${id}`)
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function cancel() {
  if (isEdit.value) router.push(`/docs/${route.params.id}`)
  else router.push('/docs')
}

function openDirDialog() {
  // 默认在已选的叶目录下再细分（如果它还没放文档且未到最大层级）
  const rec = targetRec.value
  dirDialog.value = {
    visible: true,
    parentId: rec && canAddChild(rec) ? rec.node.id : null,
    name: ''
  }
}

/** 创建子目录并把新建的目录作为文档存放位置（可反复细分） */
async function confirmCreateDir() {
  const name = dirDialog.value.name.trim()
  if (!name) {
    ElMessage.warning('请输入目录名称')
    return
  }
  if (!dirDialog.value.parentId) {
    ElMessage.warning('请选择上级目录')
    return
  }
  creatingDir.value = true
  try {
    const created = await createNode({ parentId: dirDialog.value.parentId, name })
    await catalog.refresh()
    if (created?.id) form.value.topicId = created.id
    dirDialog.value.visible = false
    dirDialog.value.name = ''
    ElMessage.success('子目录已创建，已选为文档存放目录')
  } catch (e) {
    // 拦截器已提示
  } finally {
    creatingDir.value = false
  }
}

/** 一键导入压缩包（§5.4 编辑器草稿流）：解析后填入源码/预览，由用户核对后保存 */
async function onPickZip(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (!/\.zip$/i.test(file.name)) {
    ElMessage.warning('请选择 .zip 压缩包')
    return
  }
  if (form.value.contentMd && form.value.contentMd.trim()) {
    try {
      await ElMessageBox.confirm('导入压缩包会覆盖当前编辑器内容，是否继续？', '导入确认', {
        type: 'warning',
        confirmButtonText: '继续',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
  }
  importing.value = true
  try {
    const data = await importZip(file)
    if (data.title) form.value.title = data.title
    if (data.slug) form.value.slug = data.slug
    form.value.contentMd = data.contentMd || ''
    if (data.warnings && data.warnings.length) {
      ElMessageBox.alert(data.warnings.map(escapeHtml).join('<br/>'), '导入提示', {
        type: 'warning',
        dangerouslyUseHTMLString: true,
        confirmButtonText: '知道了'
      })
    }
    ElMessage.success(`已导入「${data.title || file.name}」，请核对后保存` + (data.importedImages ? `（图片 ${data.importedImages} 张已上传）` : ''))
  } catch (e) {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function onPickImage(uploadFile) {
  const file = uploadFile.raw
  if (file) editor.value?.insertImage(file)
}
</script>

<style scoped lang="scss">
.doc-edit {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.edit-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  .edit-bar-title {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-family: var(--ak-font-display);
    font-weight: 600;
    font-size: 16px;
    letter-spacing: 1px;
    color: var(--ak-text);
    .edit-bar-mark {
      width: 10px;
      height: 10px;
      clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
      background: var(--ak-gold);
    }
  }
  .edit-bar-sub {
    font-family: var(--code-block-font);
    font-size: 11px;
    letter-spacing: 1px;
    color: var(--ak-faint);
  }
}
.edit-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  padding: 10px 12px;
  .dir-select { width: 300px; }
  .dir-path {
    color: var(--ak-faint);
    font-size: 12px;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .title-input { width: 280px; }
  .slug-input { width: 200px; }
  .note-input { width: 220px; }
  :deep(.el-input__wrapper) {
    border-radius: 2px;
  }
  .save-btn {
    :deep(.el-icon) { margin-right: 4px; }
  }
}
.dir-dialog-hint {
  color: var(--ak-faint);
  font-size: 12px;
  margin-top: -8px;
  margin-bottom: 8px;
}
.full-width {
  width: 100%;
}

/* ---------- 移动端：工具栏控件整行排布 ---------- */
@media (max-width: 768px) {
  .doc-edit {
    padding: 12px 10px;
  }
  .edit-bar {
    flex-wrap: wrap;
  }
  .edit-head {
    padding: 10px 8px;
    .dir-select,
    .title-input,
    .slug-input,
    .note-input {
      width: 100%;
    }
    .dir-path {
      width: 100%;
      max-width: none;
    }
    .save-btn {
      flex: 1;
    }
  }
}
</style>
