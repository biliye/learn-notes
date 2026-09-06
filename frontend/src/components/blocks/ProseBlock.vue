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
// 约定：整段恰好只有一个链接（无其他文字）→ 居中展示，用作图下的"交互版"等入口行。
// 站点禁原始 HTML（html:false + DOMPurify），纯 markdown 无居中语法，故在渲染层识别。
const html = computed(() => {
  const raw = renderBlock(props.block.raw)
  if (/^<p>\s*<a href="[^"]*">[\s\S]*?<\/a>\s*<\/p>\n?$/.test(raw)) {
    return raw.replace('<p>', '<p class="fig-link">')
  }
  return raw
})
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
  :deep(p.fig-link) {
    text-align: center;
    margin: 2px 0 12px;
    font-size: 13px;
    letter-spacing: 0.5px;
  }
  :deep(strong) {
    color: var(--ak-gold-bright);
    font-weight: 600;
  }
}
</style>
