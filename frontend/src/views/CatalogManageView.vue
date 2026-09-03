<template>
  <div class="catalog-manage">
    <div class="page-head ak-page-head">
      <h3>分类管理</h3>
      <span class="ak-head-sub">CATALOG CONTROL</span>
      <span class="depth-tip">目录层级=该大类最多能往下建几层（含大类）</span>
      <el-button type="primary" class="head-add ak-btn-slant" @click="openCreate(null)">
        <el-icon><Plus /></el-icon> 新增大类
      </el-button>
    </div>
    <el-table :data="catalog.tree" row-key="id" :tree-props="{ children: 'children' }" default-expand-all>
      <el-table-column prop="name" label="名称" min-width="180">
        <template #default="{ row }">
          <span v-if="row.autoCreated" class="auto-tag" title="导入时自动创建，待整理">●</span>
          {{ row.name }}
          <span v-if="row.parentId === 0 || !row.parentId" class="depth-tag" :title="'该大类下可建目录层级：' + (row.maxLevel || 2) + ' 级'">
            L{{ row.maxLevel || 2 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" prop="slug" label="slug" min-width="140" />
      <el-table-column v-if="!isMobile" prop="remark" label="注释" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="!isMobile" prop="sortOrder" label="排序" width="70" />
      <el-table-column prop="docCount" label="文档数" width="80" />
      <el-table-column label="操作" width="330" :fixed="isMobile ? false : 'right'">
        <template #default="{ row }">
          <el-button v-if="viewable(row)" link size="small" type="primary" @click="$router.push({ path: '/docs', query: { topicId: row.id } })">查看</el-button>
          <el-button v-if="canAddDir(row)" link size="small" @click="openCreate(row)">＋ 子目录</el-button>
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="movable(row)" link size="small" @click="openMove(row)">移动</el-button>
          <el-button v-if="!isProtected(row)" link size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog.visible" :title="dialog.title" width="min(480px, 92vw)">
      <el-form label-width="96px">
        <el-form-item label="上级目录">
          <span class="parent-text">{{ dialog.parentLabel }}</span>
        </el-form-item>
        <el-form-item v-if="isTopLevel(dialog)" label="目录层级">
          <el-select v-model="dialog.maxLevel" style="width: 200px">
            <el-option v-for="lv in levelOptions" :key="lv" :label="levelLabel(lv)" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="dialog.name" maxlength="80" /></el-form-item>
        <el-form-item v-if="!dialog.id" label="slug"><el-input v-model="dialog.slug" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="注释"><el-input v-model="dialog.remark" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dialog.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog.visible" :title="`移动「${moveDialog.name}」到…`" width="min(460px, 92vw)">
      <el-tree-select
        v-model="moveDialog.targetId"
        :data="moveCandidates"
        node-key="id"
        :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
        check-strictly
        default-expand-all
        clearable
        filterable
        placeholder="选择新上级目录（叶子/目录均可，可容纳即可）"
        style="width: 100%"
      />
      <div class="move-tip">目标不能是自身或自己的子目录；含文档的目录不能再往下挂。</div>
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
import {
  findRec, canAddChild, canMoveNode, isDocTarget, isProtectedNode, disabledTree
} from '../utils/catalogTree'

const catalog = useCatalogStore()

// 移动端：精简表格列（隐藏 slug/注释/排序）
const isMobile = useIsMobile()

const levelOptions = Array.from({ length: 9 }, (_, i) => i + 2) // 2..10

function levelLabel(lv) {
  let label = '大类'
  for (let i = 0; i < lv - 2; i++) label += ' → 目录'
  label += ' → 文档目录'
  return `${lv} 级：${label}` + (lv === 2 ? '（现状）' : '')
}

const dialog = ref({
  visible: false, title: '', id: null, parentId: null, parentLabel: '',
  name: '', slug: '', remark: '', sortOrder: 100, maxLevel: 2, saving: false
})
const moveDialog = ref({ visible: false, id: null, name: '', targetId: null, subtreeIds: [] })

onMounted(() => catalog.load())

function recOf(row) {
  return findRec(catalog.tree, row.id)
}

function isTopLevel(d) {
  return !d.parentId || d.parentId === 0
}

/** 该行是可放文档的叶目录（可查看它的文档） */
function viewable(row) {
  return isDocTarget(row)
}

/** 该行还能不能再建子目录 */
function canAddDir(row) {
  const rec = recOf(row)
  return !!rec && canAddChild(rec) && !isProtectedNode(row)
}

/** 非顶层大类、非兜底路径可整体移动 */
function movable(row) {
  const rec = recOf(row)
  return !!rec && canMoveNode(rec)
}

function openCreate(parent) {
  const isRoot = !parent
  dialog.value = {
    visible: true,
    title: isRoot ? '新增大类' : `在「${parent.name}」下新增子目录`,
    id: null,
    parentId: isRoot ? null : parent.id,
    parentLabel: isRoot ? '顶层（成为新的大类）' : parent.name,
    name: '', slug: '', remark: '', sortOrder: 100,
    maxLevel: 2,
    saving: false
  }
}

function openEdit(row) {
  const root = isTopLevel(row)
  dialog.value = {
    visible: true,
    title: '编辑「' + row.name + '」',
    id: row.id,
    parentId: row.parentId,
    parentLabel: root ? '顶层大类' : (recOf(row)?.parent?.node?.name || '—'),
    name: row.name,
    slug: row.slug,
    remark: row.remark,
    sortOrder: row.sortOrder,
    maxLevel: row.maxLevel || 2,
    saving: false
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
    const common = {
      name: d.name,
      remark: d.remark,
      sortOrder: d.sortOrder
    }
    if (d.id) {
      await updateNode(d.id, isTopLevel(d) ? { ...common, maxLevel: d.maxLevel } : common)
    } else {
      await createNode({
        parentId: d.parentId || 0,
        ...common,
        slug: d.slug || undefined,
        maxLevel: isTopLevel(d) ? d.maxLevel : undefined
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
  const subtreeIds = collectSubtree(row)
  moveDialog.value = {
    visible: true,
    id: row.id,
    name: row.name,
    targetId: null,
    subtreeIds
  }
}

/** 行自身的子树 id（含自身） */
function collectSubtree(row) {
  const ids = []
  const stack = [row]
  while (stack.length) {
    const n = stack.pop()
    ids.push(Number(n.id))
    if (n.children) stack.push(...n.children)
  }
  return ids
}

const moveCandidates = computed(() => {
  const d = moveDialog.value
  if (!d.id) return []
  return disabledTree(catalog.tree, (n) =>
    d.subtreeIds.includes(Number(n.id)) || (n.docCount || 0) > 0 || isProtectedNode(n))
})

async function doMove() {
  if (!moveDialog.value.targetId) {
    ElMessage.warning('请选择目标上级目录')
    return
  }
  await moveNode(moveDialog.value.id, { parentId: moveDialog.value.targetId })
  ElMessage.success('已移动')
  moveDialog.value.visible = false
  await catalog.refresh()
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？该目录若有子目录或文档将被拒绝。`, '删除分类', { type: 'warning' })
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
  return isProtectedNode(row)
}
</script>

<style scoped lang="scss">
.catalog-manage {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px;
}
.head-add {
  margin-left: auto;
  :deep(.el-icon) { margin-right: 4px; }
}
.depth-tip {
  color: var(--ak-faint);
  font-size: 12px;
  margin-left: auto;
  margin-right: 12px;
}
.parent-text {
  color: var(--ak-text-2);
}
.depth-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 0 5px;
  font-family: var(--code-block-font);
  font-size: 11px;
  line-height: 16px;
  color: var(--ak-gold-dim);
  border: 1px solid var(--ak-border-2);
  border-radius: 2px;
  vertical-align: 1px;
}
.auto-tag {
  color: var(--ak-amber);
  margin-right: 4px;
}
.move-tip {
  margin-top: 8px;
  color: var(--ak-faint);
  font-size: 12px;
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
