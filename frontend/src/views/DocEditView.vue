<template>
  <div class="doc-edit">
    <div class="edit-head">
      <el-select v-if="!isEdit" v-model="form.topicId" placeholder="选择小方向" class="topic-select" filterable>
        <el-option-group v-for="c in catalog.tree" :key="c.id" :label="c.name">
          <el-option v-for="t in c.children" :key="t.id" :label="t.name" :value="t.id" />
        </el-option-group>
      </el-select>
      <el-input v-model="form.title" placeholder="文档标题" class="title-input" maxlength="200" />
      <el-input v-model="form.slug" placeholder="slug（留空自动生成）" class="slug-input" maxlength="120" />
      <el-input v-model="changeNote" placeholder="本次更新说明（可选）" class="note-input" maxlength="200" />
      <el-button type="primary" :loading="saving" @click="save">保存 (Ctrl+S)</el-button>
      <el-button @click="cancel">取消</el-button>
      <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="onPickImage">
        <el-button>插入图片</el-button>
      </el-upload>
    </div>
    <MarkdownEditor ref="editor" v-model="form.contentMd" @save="save" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import { getDoc, createDoc, updateDoc, downloadRaw } from '../api/doc'
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

onMounted(async () => {
  if (!catalog.loaded) catalog.load()
  if (isEdit.value) {
    const doc = await getDoc(route.params.id)
    form.value = {
      topicId: doc.breadcrumb[1]?.id,
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
    ElMessage.warning('请选择小方向')
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
.edit-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  .topic-select { width: 200px; }
  .title-input { width: 280px; }
  .slug-input { width: 200px; }
  .note-input { width: 220px; }
}
</style>
