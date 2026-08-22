<template>
  <el-dialog v-model="visible" title="历史版本" width="640px">
    <el-table :data="versions" size="small" highlight-current-row @row-click="showVersion">
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column prop="changeNote" label="更新说明" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!versions.length" description="暂无历史版本" />

    <el-dialog v-model="showContent" width="720px" append-to-body :title="`版本 v${currentVersion} 正文`">
      <div class="version-content" v-html="contentHtml" />
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { getVersions, getVersion } from '../api/doc'
import { renderBlock } from '../utils/markdown'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  docId: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const versions = ref([])
const showContent = ref(false)
const currentVersion = ref(null)
const contentHtml = ref('')

watch(() => props.modelValue, async (v) => {
  if (v && props.docId) {
    versions.value = await getVersions(props.docId)
  }
})

async function showVersion(row) {
  currentVersion.value = row.version
  const data = await getVersion(props.docId, row.version)
  // 旧版正文本地渲染，仅供查看（注释：非权威，不产生锚点）
  contentHtml.value = renderBlock(data.contentMd)
  showContent.value = true
}

function formatTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.version-content {
  max-height: 60vh;
  overflow-y: auto;
  font-size: 15px;
  line-height: 1.7;
  color: var(--ak-text);
  :deep(pre) {
    background: var(--code-block-bg);
    padding: 12px;
    border-radius: 2px;
    border: 1px solid var(--ak-border);
    overflow-x: auto;
    color: var(--code-block-text);
    font-family: var(--code-block-font);
  }
  :deep(code) {
    background: var(--doc-code-inline-bg);
    color: var(--doc-code-inline-color);
    border-radius: 2px;
    padding: 0.1em 0.35em;
    font-family: var(--code-block-font);
    font-size: 0.9em;
  }
  :deep(a) { color: var(--ak-gold); }
}
</style>
