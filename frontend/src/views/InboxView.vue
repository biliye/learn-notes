<template>
  <div class="inbox-view">
    <div class="page-head">
      <h3>INBOX · 未归类文档</h3>
      <el-button link type="primary" @click="$router.push('/docs')">← 返回</el-button>
    </div>
    <DocList ref="docList" :category-id="inboxId">
      <template #actions="{ doc }">
        <el-button link size="small" type="primary" @click.stop="openMove(doc)">移动到…</el-button>
      </template>
    </DocList>
    <el-dialog v-model="moveDialog.visible" title="移动到小方向" width="400px">
      <el-select v-model="moveDialog.topicId" placeholder="选择目标小方向" style="width: 100%" filterable>
        <el-option-group v-for="c in catalog.tree" :key="c.id" :label="c.name">
          <el-option v-for="t in c.children" :key="t.id" :label="t.name" :value="t.id" />
        </el-option-group>
      </el-select>
      <template #footer>
        <el-button @click="moveDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="doMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import DocList from '../components/DocList.vue'
import { moveDoc } from '../api/doc'
import { useCatalogStore } from '../stores/catalog'

const catalog = useCatalogStore()
const docList = ref(null)
const moveDialog = ref({ visible: false, docId: null, topicId: null })

const inboxId = computed(() => {
  const inbox = catalog.tree.find((n) => n.slug === 'inbox')
  return inbox?.id
})

function openMove(doc) {
  moveDialog.value = { visible: true, docId: doc.id, topicId: null }
}

onMounted(() => catalog.load())

async function doMove() {
  if (!moveDialog.value.topicId) {
    ElMessage.warning('请选择目标小方向')
    return
  }
  await moveDoc(moveDialog.value.docId, moveDialog.value.topicId)
  ElMessage.success('已移动')
  moveDialog.value.visible = false
  await catalog.refresh()
  docList.value?.load()
}
</script>

<style scoped lang="scss">
.inbox-view {
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
</style>
