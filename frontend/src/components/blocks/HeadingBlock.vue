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
  margin: 26px 0 14px;
  .block-heading {
    margin: 0;
    font-family: var(--ak-font-display);
    font-weight: 700;
    letter-spacing: 0.5px;
    line-height: 1.4;
    scroll-margin-top: 68px;
  }
  :deep(h1) {
    font-size: 1.6em;
    border-bottom: 1px solid var(--ak-border);
    padding-bottom: 10px;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      left: 0;
      bottom: -1px;
      width: 56px;
      height: 2px;
      background: var(--ak-gold);
    }
  }
  :deep(h2) {
    font-size: 1.35em;
    border-bottom: 1px solid var(--ak-border);
    padding-bottom: 8px;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      left: 0;
      bottom: -1px;
      width: 42px;
      height: 2px;
      background: linear-gradient(90deg, var(--ak-gold-dim), transparent);
    }
  }
  :deep(h3) { font-size: 1.15em; color: var(--ak-gold-bright); }
  :deep(h4) { font-size: 1em; color: var(--ak-text-2); }
}
</style>
