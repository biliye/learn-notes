<template>
  <aside class="toc-sidebar">
    <div class="toc-title">
      <span class="toc-bar" aria-hidden="true"></span>
      目录 / CONTENTS
    </div>
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
  width: 210px;
  position: sticky;
  top: 68px;
  max-height: calc(100vh - 90px);
  overflow-y: auto;
  padding-left: 16px;
  border-left: 1px solid var(--ak-border);
  flex-shrink: 0;
  .toc-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-family: var(--code-block-font);
    font-size: 12px;
    color: var(--ak-muted);
    margin-bottom: 10px;
    letter-spacing: 1px;
    .toc-bar {
      width: 12px;
      height: 2px;
      background: var(--ak-gold);
      display: inline-block;
    }
  }
  .toc-item {
    position: relative;
    padding: 4px 0 4px 10px;
    cursor: pointer;
    font-size: 13px;
    color: var(--ak-text-2);
    border-left: 2px solid transparent;
    transition: color 0.2s, border-color 0.2s;
    a { text-decoration: none; color: inherit; display: block; }
    &:hover {
      color: var(--ak-gold);
      border-left-color: var(--ak-gold-dim);
    }
    &.active {
      color: var(--ak-gold-bright);
      font-weight: 600;
      border-left-color: var(--ak-gold);
    }
    &.lv-2 { padding-left: 20px; }
    &.lv-3 { padding-left: 30px; }
    &.lv-4 { padding-left: 40px; }
  }
  .toc-empty { color: var(--ak-faint); font-size: 13px; }
}
</style>
