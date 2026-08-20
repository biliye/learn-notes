<template>
  <div class="ln-block quote-block" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <div class="quote-body" v-html="html" />
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
.quote-block {
  margin: 0 0 16px;
  .quote-body {
    border-left: 4px solid #409eff;
    background: #f0f7ff;
    padding: 10px 14px;
    border-radius: 0 6px 6px 0;
    :deep(p) { margin: 0 0 8px; &:last-child { margin: 0; } }
  }
}
</style>
