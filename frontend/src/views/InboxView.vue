<template>
  <div class="inbox-view">
    <div class="page-head ak-page-head">
      <h3>INBOX · 未归类文档</h3>
      <span class="ak-head-sub">PENDING CLASSIFICATION</span>
      <el-button link type="primary" class="head-back" @click="$router.push('/docs')">← 返回</el-button>
    </div>
    <DocList ref="docList" :category-id="inboxId">
      <template #actions="{ doc }">
        <el-button link size="small" type="primary" @click.stop="openMove(doc)">移动到…</el-button>
      </template>
    </DocList>
    <el-dialog v-model="moveDialog.visible" title="移动到目录" width="min(440px, 92vw)">
      <el-tree-select
        v-model="moveDialog.topicId"
        :data="docTargetTree"
        node-key="id"
        :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
        check-strictly
        filterable
        default-expand-all
        placeholder="选择目标目录（只能选大类下没有子目录的目录）"
        style="width: 100%"
      />
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
import { isDocTarget, disabledTree } from '../utils/catalogTree'

const catalog = useCatalogStore()
const docList = ref(null)
const moveDialog = ref({ visible: false, docId: null, topicId: null })

const inboxId = computed(() => {
  const inbox = catalog.tree.find((n) => n.slug === 'inbox')
  return inbox?.id
})

/** 目标只允许"可放文档的叶目录" */
const docTargetTree = computed(() =>
  disabledTree(catalog.tree, (n) => !isDocTarget(n)))

function openMove(doc) {
  moveDialog.value = { visible: true, docId: doc.id, topicId: null }
}

onMounted(() => catalog.load())

async function doMove() {
  if (!moveDialog.value.topicId) {
    ElMessage.warning('请选择目标目录')
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
.head-back {
  margin-left: auto;
}
</style>
