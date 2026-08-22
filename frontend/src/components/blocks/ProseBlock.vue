<template>
  <div class="ln-block prose prose-block" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <div class="prose-body" v-html="html" />
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
.prose-block {
  margin: 0 0 16px;
  :deep(p) {
    margin: 0 0 12px;
  }
  :deep(img) {
    max-width: 100%;
    display: block;
    margin: 8px 0;
    border: 1px solid var(--ak-border);
    border-radius: 2px;
  }
  :deep(a) {
    color: var(--ak-gold);
  }
  :deep(strong) {
    color: var(--ak-gold-bright);
    font-weight: 600;
  }
}
</style>
