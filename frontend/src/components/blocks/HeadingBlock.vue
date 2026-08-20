<template>
  <div class="ln-block heading-block" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <component :is="'h' + (block.level || 2)" class="block-heading" v-html="html" />
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
.heading-block {
  margin: 24px 0 12px;
  .block-heading {
    margin: 0;
    font-weight: 700;
    line-height: 1.4;
    scroll-margin-top: 68px;
  }
  :deep(h1) { font-size: 1.6em; border-bottom: 1px solid var(--doc-border-color); padding-bottom: 8px; }
  :deep(h2) { font-size: 1.35em; border-bottom: 1px solid var(--doc-border-color); padding-bottom: 6px; }
  :deep(h3) { font-size: 1.15em; }
  :deep(h4) { font-size: 1em; }
}
</style>
