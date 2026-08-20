<template>
  <aside class="toc-sidebar">
    <div class="toc-title">目录</div>
    <div v-if="items.length" class="toc-list">
      <div
        v-for="item in items"
        :key="item.anchor"
        class="toc-item"
        :class="itemClass(item)"
        @click="scrollTo(item.anchor)"
      >
        <a>{{ item.title }}</a>
      </div>
    </div>
    <div v-else class="toc-empty">无标题</div>
  </aside>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] }
})

const items = ref([])
const activeAnchor = ref('')

watch(() => props.blocks, buildItems, { immediate: true })

function buildItems() {
  items.value = props.blocks
    .filter((b) => b.type === 'heading' && b.level >= 1 && b.level <= 4)
    .map((b) => ({
      anchor: b.anchor,
      level: b.level,
      title: b.raw.replace(/^#{1,6}\s*/, '').replace(/\s*#+$/, '').trim()
    }))
}

function itemClass(item) {
  const cls = { active: activeAnchor.value === item.anchor }
  cls['lv-' + item.level] = true
  return cls
}

function scrollTo(anchor) {
  const el = document.getElementById('blk-' + anchor)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function onScroll() {
  let current = ''
  for (const item of items.value) {
    const el = document.getElementById('blk-' + item.anchor)
    if (el && el.getBoundingClientRect().top <= 90) {
      current = item.anchor
    }
  }
  activeAnchor.value = current
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped lang="scss">
.toc-sidebar {
  width: 200px;
  position: sticky;
  top: 68px;
  max-height: calc(100vh - 90px);
  overflow-y: auto;
  padding-left: 16px;
  border-left: 1px solid var(--doc-border-color);
  flex-shrink: 0;
  .toc-title {
    font-size: 12px;
    color: #909399;
    margin-bottom: 8px;
    letter-spacing: 1px;
  }
  .toc-item {
    padding: 3px 0;
    cursor: pointer;
    font-size: 13px;
    color: #606266;
    a { text-decoration: none; color: inherit; display: block; }
    &:hover { color: #409eff; }
    &.active { color: #409eff; font-weight: 600; }
    &.lv-2 { padding-left: 12px; }
    &.lv-3 { padding-left: 24px; }
    &.lv-4 { padding-left: 36px; }
  }
  .toc-empty { color: #c0c4cc; font-size: 13px; }
}
</style>
