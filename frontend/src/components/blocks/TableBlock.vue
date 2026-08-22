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
    border: 1px solid var(--ak-border);
    th, td {
      border: 1px solid var(--ak-border);
      padding: 8px 12px;
      font-size: 14px;
    }
    th {
      background: var(--ak-bg-3);
      color: var(--ak-gold);
      font-weight: 600;
      font-family: var(--ak-font-display);
      letter-spacing: 0.5px;
      border-top: 2px solid var(--ak-gold-dim);
    }
    tr:nth-child(even) td { background: rgba(255, 255, 255, 0.015); }
    tr:hover td { background: rgba(201, 168, 106, 0.06); }
  }
}
</style>
