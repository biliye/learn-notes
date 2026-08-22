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
    position: relative;
    border-left: 3px solid var(--ak-gold);
    background: linear-gradient(90deg, rgba(201, 168, 106, 0.10), rgba(201, 168, 106, 0.02));
    padding: 10px 14px 10px 18px;
    border-radius: 0 2px 2px 0;
    /* 菱形引号标记 */
    &::before {
      content: '';
      position: absolute;
      left: -5px;
      top: 14px;
      width: 7px;
      height: 7px;
      clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
      background: var(--ak-gold);
    }
    :deep(p) {
      margin: 0 0 8px;
      color: var(--ak-text-2);
      &:last-child { margin: 0; }
    }
    :deep(a) { color: var(--ak-gold); }
  }
}
</style>
