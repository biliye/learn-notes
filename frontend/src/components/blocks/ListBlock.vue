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
    li { margin-bottom: 4px; }
    li > ul, li > ol { margin-top: 4px; }
  }
}
</style>
