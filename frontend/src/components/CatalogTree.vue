<template>
  <el-tree
    :data="tree"
    node-key="id"
    :props="{ label: 'name', children: 'children' }"
    highlight-current
    :expand-on-click-node="false"
    @node-click="onNodeClick"
  >
    <template #default="{ data }">
      <div class="tree-node">
        <el-tooltip :content="data.remark || data.name" placement="top" :disabled="!data.remark">
          <span class="node-label">{{ data.name }}</span>
        </el-tooltip>
        <el-tooltip v-if="data.autoCreated" content="导入时自动创建，待整理" placement="top">
          <span class="auto-dot" />
        </el-tooltip>
        <span class="node-count">{{ data.docCount }}</span>
      </div>
    </template>
  </el-tree>
</template>

<script setup>
import { computed } from 'vue'
import { useCatalogStore } from '../stores/catalog'

const emit = defineEmits(['select-topic'])
const catalog = useCatalogStore()

const tree = computed(() => catalog.tree)

function onNodeClick(data) {
  // 只响应小方向（有 children 的是大类）
  if (data.children && data.children.length === 0 && data.parentId !== 0) {
    emit('select-topic', data)
  } else if (data.parentId === 0 && data.slug === 'inbox') {
    emit('select-topic', data.children?.[0])
  }
}
</script>

<style scoped lang="scss">
:deep(.el-tree) {
  background: transparent;
  --el-tree-node-hover-bg-color: var(--ak-bg-3);
  .el-tree-node__content {
    height: 30px;
    border-radius: 0;
    transition: background 0.2s;
  }
  .el-tree-node.is-current > .el-tree-node__content {
    background: var(--ak-bg-4);
    box-shadow: inset 2px 0 0 var(--ak-gold);
  }
  .el-tree-node__expand-icon {
    color: var(--ak-gold-dim);
    &:hover { color: var(--ak-gold); }
  }
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  font-size: 13px;
  .node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--ak-text-2);
  }
  .is-current .node-label {
    color: var(--ak-gold-bright);
    font-weight: 600;
  }
  .auto-dot {
    width: 8px;
    height: 8px;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: var(--ak-amber);
    flex-shrink: 0;
  }
  .node-count {
    color: var(--ak-faint);
    font-family: var(--code-block-font);
    font-size: 11px;
    min-width: 18px;
    text-align: right;
  }
}

/* 移动端：增大触摸目标（桌面 30px 偏小，抽屉内 36px 更易点） */
@media (max-width: 768px) {
  :deep(.el-tree-node__content) {
    height: 36px;
  }
}
</style>
