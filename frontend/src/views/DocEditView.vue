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
        <el-select v-model="form.categoryId" placeholder="选择大类" class="category-select" filterable clearable @change="onCategoryChange">
          <el-option v-for="c in catalog.tree" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="form.topicId" placeholder="选择小方向" class="topic-select" filterable clearable :disabled="!form.categoryId">
          <el-option v-for="t in topicsOfCategory" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <template v-if="form.categoryId && !topicsOfCategory.length">
          <span class="empty-topic-hint">该大类下暂无小方向</span>
          <el-input v-model="newTopicName" placeholder="新小方向名称" class="new-topic-input" maxlength="80" @keyup.enter="quickCreateTopic" />
          <el-button :loading="creatingTopic" size="small" @click="quickCreateTopic">
            <el-icon><Plus /></el-icon> 新建小方向
          </el-button>
        </template>
        <el-upload :show-file-list="false" accept=".zip" :auto-upload="false" :on-change="onPickZip">
          <el-button :loading="importing">
            <el-icon><Box /></el-icon> 一键导入压缩包
          </el-button>
        </el-upload>
      </template>
      <el-input v-model="form.title" placeholder="文档标题" class="title-input" maxlength="200" />
      <el-input v-model="form.slug" placeholder="slug（留空自动生成）" class="slug-input" maxlength="120" />
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
const newTopicName = ref('')
const creatingTopic = ref(false)
const importing = ref(false)

/** 所选大类下的小方向列表（未选大类时为空） */
const topicsOfCategory = computed(() => {
  if (!form.value.categoryId) return []
  const cat = catalog.tree.find((c) => c.id === form.value.categoryId)
  return cat?.children || []
})

onMounted(async () => {
  if (!catalog.loaded) await catalog.load()
  if (isEdit.value) {
    const doc = await getDoc(route.params.id)
    form.value = {
      topicId: doc.breadcrumb[1]?.id,
      title: doc.title,
      slug: doc.slug,
      contentMd: await rawFor(doc.id)
    }
  } else if (form.value.topicId) {
    // 从左侧树带 topicId 进入：反查其父大类并回填，保证大类/小类分开选中
    for (const c of catalog.tree) {
      const found = (c.children || []).find((t) => t.id === Number(form.value.topicId))
      if (found) {
        form.value.categoryId = c.id
        break
      }
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
    ElMessage.warning(form.value.categoryId ? '请选择小方向（该大类下暂无小方向时可点击「＋ 新建小方向」）' : '请先选择大类与小方向')
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

function onCategoryChange() {
  // 切换大类后清空已选小方向，避免小方向与大类不匹配
  form.value.topicId = null
}

/** 空大类下的内联快捷新建小方向（复用 POST /api/catalog，规格 §5.2） */
async function quickCreateTopic() {
  const name = newTopicName.value.trim()
  if (!name) {
    ElMessage.warning('请输入小方向名称')
    return
  }
  creatingTopic.value = true
  try {
    await createNode({ parentId: form.value.categoryId, name })
    await catalog.refresh()
    const cat = catalog.tree.find((c) => c.id === form.value.categoryId)
    const created = (cat?.children || []).find((t) => t.name === name)
    if (created) form.value.topicId = created.id
    newTopicName.value = ''
    ElMessage.success('小方向已创建')
  } catch (e) {
    // 拦截器已提示
  } finally {
    creatingTopic.value = false
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
  .category-select { width: 150px; }
  .topic-select { width: 200px; }
  .new-topic-input { width: 160px; }
  .empty-topic-hint {
    color: var(--ak-muted);
    font-size: 12px;
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
</style>
