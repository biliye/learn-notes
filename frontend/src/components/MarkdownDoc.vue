<template>
  <div class="markdown-doc" :class="{ 'pick-mode': picking }">
    <template v-for="block in blocks" :key="block.anchor">
      <div class="block-row" :class="{ 'pick-target': picking }" @click="picking && emitPick(block.anchor)">
        <div class="block-slot">
          <component :is="componentFor(block)" :block="block" />
          <!-- 悬浮 + 见解 入口 -->
          <button v-if="!picking" class="add-ann-btn" title="添加个人见解" @click="beginAdd(block.anchor)">
            ＋ 见解
          </button>
        </div>
        <AnnotationBar
          v-if="annotationsFor(block.anchor).length || addingAnchor === block.anchor"
          :annotations="annotationsFor(block.anchor)"
          :expanded="expandedSet.has(block.anchor)"
          :saving="saving"
          :auto-add="addingAnchor === block.anchor"
          @toggle="toggle(block.anchor)"
          @expand-all="expandedSet.add(block.anchor)"
          @collapse-all="expandedSet.delete(block.anchor)"
          @add="(c) => addAnnotation(block.anchor, c)"
          @edit="(a, c) => editAnnotation(a, c)"
          @delete="(a) => deleteAnnotation(a)"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import ProseBlock from './blocks/ProseBlock.vue'
import CodeBlock from './blocks/CodeBlock.vue'
import TableBlock from './blocks/TableBlock.vue'
import QuoteBlock from './blocks/QuoteBlock.vue'
import ListBlock from './blocks/ListBlock.vue'
import HeadingBlock from './blocks/HeadingBlock.vue'
import AnnotationBar from './AnnotationBar.vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] },
  annotations: { type: Array, default: () => [] },
  docId: { type: [Number, String], default: null }
})
const emit = defineEmits(['add', 'edit', 'delete', 'pick'])

const addingAnchor = ref(null)
const expandedSet = ref(new Set())
const saving = ref(false)
const picking = ref(false)

function componentFor(block) {
  switch (block.type) {
    case 'code': return CodeBlock
    case 'heading': return HeadingBlock
    case 'table': return TableBlock
    case 'quote': return QuoteBlock
    case 'list': return ListBlock
    case 'paragraph':
    default: return ProseBlock
  }
}

function annotationsFor(anchor) {
  return props.annotations.filter((a) => a.anchor === anchor)
}

function beginAdd(anchor) {
  addingAnchor.value = anchor
}

function toggle(anchor) {
  const set = new Set(expandedSet.value)
  if (set.has(anchor)) set.delete(anchor)
  else set.add(anchor)
  expandedSet.value = set
}

function addAnnotation(anchor, content) {
  // 保存后关闭该块的编辑态
  if (addingAnchor.value === anchor) addingAnchor.value = null
  emit('add', anchor, content)
}

function editAnnotation(ann, content) {
  emit('edit', ann, content)
}

function deleteAnnotation(ann) {
  emit('delete', ann)
}

function emitPick(anchor) {
  emit('pick', anchor)
  picking.value = false
}

function startPick() {
  picking.value = true
}

watch(() => props.docId, () => {
  addingAnchor.value = null
  expandedSet.value = new Set()
})

defineExpose({ startPick, stopPick: () => (picking.value = false) })
</script>

<style scoped lang="scss">
.block-row {
  position: relative;
  &.pick-target {
    .block-slot {
      border: 1px dashed var(--ak-gold);
      border-radius: 2px;
      padding: 4px 8px;
      cursor: pointer;
      transition: background 0.15s;
      &:hover {
        background: rgba(201, 168, 106, 0.08);
      }
    }
  }
}
.block-slot {
  position: relative;
}
.add-ann-btn {
  position: absolute;
  right: 8px;
  top: 4px;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
  border: 1px solid var(--ak-gold-dim);
  color: var(--ak-gold);
  background: var(--ak-bg-2);
  border-radius: 2px;
  font-family: var(--code-block-font);
  font-size: 11px;
  letter-spacing: 0.5px;
  padding: 2px 8px;
  cursor: pointer;
  z-index: 5;
  &:hover {
    background: var(--ak-gold);
    color: var(--ak-bg-0);
  }
}
.block-slot:hover .add-ann-btn {
  opacity: 1;
}

/* 移动端无 hover：见解按钮常显 */
@media (max-width: 768px) {
  .add-ann-btn {
    opacity: 1;
  }
}
</style>
