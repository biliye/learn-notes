<template>
  <div class="ln-block list-block" :data-anchor="block.anchor" :id="'blk-' + block.anchor">
    <div class="list-body" v-html="html" />
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
.list-block {
  margin: 0 0 16px;
  :deep(ul), :deep(ol) {
    margin: 0 0 12px;
    padding-left: 1.6em;
    li { margin-bottom: 5px; }
    li > ul, li > ol { margin-top: 5px; }
  }
  /* 无序列表 → 金色菱形标记 */
  :deep(ul) {
    list-style: none;
    padding-left: 1.1em;
    & > li {
      position: relative;
      &::before {
        content: '';
        position: absolute;
        left: -1.1em;
        top: 0.55em;
        width: 7px;
        height: 7px;
        clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
        background: var(--ak-gold-dim);
      }
    }
    ul > li::before {
      width: 5px;
      height: 5px;
      background: var(--ak-faint);
    }
  }
  /* 有序列表 → 金色数字 */
  :deep(ol) {
    & > li {
      color: var(--ak-text);
      &::marker {
        color: var(--ak-gold);
        font-family: var(--code-block-font);
      }
    }
  }
}
</style>
