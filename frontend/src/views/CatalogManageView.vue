<template>
  <div class="catalog-manage">
    <div class="page-head ak-page-head">
      <h3>分类管理</h3>
      <span class="ak-head-sub">CATALOG CONTROL</span>
      <el-button type="primary" class="head-add ak-btn-slant" @click="openCreate(null)">
        <el-icon><Plus /></el-icon> 新增大类
      </el-button>
    </div>
    <el-table :data="flatRows" row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="name" label="名称" min-width="160">
        <template #default="{ row }">
          <span v-if="row.autoCreated" class="auto-tag" title="导入时自动创建，待整理">●</span>
          {{ row.name }}
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" prop="slug" label="slug" min-width="140" />
      <el-table-column v-if="!isMobile" prop="remark" label="注释" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="!isMobile" prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="docCount" label="文档数" width="80" />
      <el-table-column label="操作" width="260" :fixed="isMobile ? false : 'right'">
        <template #default="{ row }">
          <el-button v-if="!row.children" link size="small" type="primary" @click="$router.push({ path: '/docs', query: { topicId: row.id } })">查看</el-button>
          <el-button link size="small" @click="openCreate(row)">＋ 小方向</el-button>
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="openMove(row)">移动</el-button>
          <el-button v-if="!isProtected(row)" link size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="min(480px, 92vw)">
      <el-form label-width="80px">
        <el-form-item label="父节点">
          <el-select v-model="dialog.parentId" placeholder="选择大类（留空为新建大类）" clearable filterable>
            <el-option v-for="c in catalog.tree" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="dialog.name" /></el-form-item>
        <el-form-item label="slug"><el-input v-model="dialog.slug" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="注释"><el-input v-model="dialog.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dialog.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog.visible" title="移动小方向" width="min(400px, 92vw)">
      <el-select v-model="moveDialog.targetCategoryId" placeholder="选择目标大类" style="width: 100%">
        <el-option v-for="c in catalog.tree" :key="c.id" :label="c.name" :value="c.id" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { createNode, updateNode, moveNode, deleteNode } from '../api/catalog'
import { useCatalogStore } from '../stores/catalog'
import { useIsMobile } from '../composables/useIsMobile'

const catalog = useCatalogStore()

// 移动端：精简表格列（隐藏 slug/注释/排序）
const isMobile = useIsMobile()

const flatRows = computed(() => catalog.tree)

const dialog = ref({ visible: false, title: '', parentId: null, name: '', slug: '', remark: '', sortOrder: 100, saving: false, id: null })
const moveDialog = ref({ visible: false, id: null, targetCategoryId: null })

onMounted(() => catalog.load())

function openCreate(parent) {
  dialog.value = {
    visible: true,
    title: parent ? `在「${parent.name}」下新增小方向` : '新增大类',
    parentId: parent ? parent.id : null,
    name: '', slug: '', remark: '', sortOrder: 100, saving: false, id: null
  }
}

function openEdit(row) {
  dialog.value = {
    visible: true,
    title: '编辑「' + row.name + '」',
    parentId: row.parentId,
    name: row.name, slug: row.slug, remark: row.remark, sortOrder: row.sortOrder, saving: false, id: row.id
  }
}

async function saveDialog() {
  const d = dialog.value
  if (!d.name.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  d.saving = true
  try {
    if (d.id) {
      await updateNode(d.id, {
        name: d.name || undefined,
        remark: d.remark,
        sortOrder: d.sortOrder
      })
    } else {
      await createNode({
        parentId: d.parentId || 0,
        name: d.name,
        slug: d.slug || undefined,
        remark: d.remark,
        sortOrder: d.sortOrder
      })
    }
    ElMessage.success('已保存')
    dialog.value.visible = false
    await catalog.refresh()
  } catch (e) {
    // 拦截器已提示（409 时后端 msg 原样展示）
  } finally {
    d.saving = false
  }
}

function openMove(row) {
  moveDialog.value = { visible: true, id: row.id, targetCategoryId: null }
}

async function doMove() {
  if (!moveDialog.value.targetCategoryId) {
    ElMessage.warning('请选择目标大类')
    return
  }
  await moveNode(moveDialog.value.id, { parentId: moveDialog.value.targetCategoryId })
  ElMessage.success('已移动')
  moveDialog.value.visible = false
  await catalog.refresh()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除分类', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteNode(row.id)
    ElMessage.success('已删除')
    await catalog.refresh()
  } catch (e) {
    // 409 时后端 msg 原样展示（拦截器已弹）
  }
}

function isProtected(row) {
  return row.slug === 'inbox' || row.slug === 'uncategorized'
}
</script>

<style scoped lang="scss">
.catalog-manage {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}
.head-add {
  margin-left: auto;
  :deep(.el-icon) { margin-right: 4px; }
}
.auto-tag {
  color: var(--ak-amber);
  margin-right: 4px;
}
:deep(.el-table) {
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  --el-table-border-color: var(--ak-border);
  --el-table-header-bg-color: var(--ak-bg-3);
  --el-table-tr-bg-color: var(--ak-bg-2);
  --el-table-row-hover-bg-color: var(--ak-bg-3);
  --el-table-header-text-color: var(--ak-gold);
  --el-table-text-color: var(--ak-text-2);
  --el-table-expanded-cell-bg-color: var(--ak-bg-2);
  th.el-table__cell {
    font-weight: 600;
    font-family: var(--ak-font-display);
    letter-spacing: 0.5px;
  }
}
</style>
