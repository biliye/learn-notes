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
.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  .node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .auto-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #e6a23c;
    flex-shrink: 0;
  }
  .node-count {
    color: #909399;
    font-size: 12px;
  }
}
</style>
