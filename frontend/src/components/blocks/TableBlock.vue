<template>
  <div class="ln-block table-block" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <div class="table-scroll" v-html="html" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderBlock } from '../../utils/markdown'

const props = defineProps({
  block: { type: Object, required: true }
})
const html = computed(() => renderBlock(props.block.raw))
</script>

<style scoped lang="scss">
.table-block {
  margin: 0 0 20px;
  .table-scroll { overflow-x: auto; }
  :deep(table) {
    border-collapse: collapse;
    width: 100%;
    th, td {
      border: 1px solid var(--doc-border-color);
      padding: 8px 12px;
      font-size: 14px;
    }
    th { background: #f5f6f8; font-weight: 600; }
    tr:nth-child(even) td { background: #fafbfc; }
  }
}
</style>
