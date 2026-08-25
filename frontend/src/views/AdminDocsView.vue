<template>
  <div class="admin-docs">
    <div class="page-head ak-page-head">
      <h3>全部文档</h3>
      <span class="ak-head-sub">ALL USERS ARCHIVE</span>
      <el-input v-model="keyword" placeholder="搜索标题 / 正文…" clearable class="admin-search"
                @keyup.enter="load(1)" @clear="load(1)">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 用户概览 -->
    <div v-if="users.length" class="user-strip">
      <div v-for="u in users" :key="u.id" class="user-card">
        <div class="user-role" :class="{ admin: u.role === 'ADMIN' }">
          {{ u.role === 'ADMIN' ? 'ADMIN' : 'USER' }}
        </div>
        <div class="user-name">{{ u.nickname || u.username }}</div>
        <div class="user-meta">
          <span>@{{ u.username }}</span>
          <span>{{ u.docCount }} 篇</span>
        </div>
      </div>
    </div>

    <el-table :data="items" v-loading="loading" class="admin-table" :empty-text="'没有文档'">
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" class="doc-title" @click="openDoc(row.id)">
            {{ row.title }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="归属用户" width="140">
        <template #default="{ row }">
          <div class="owner-cell">
            <span class="owner-name">{{ row.ownerNickname || row.ownerUsername }}</span>
            <span class="owner-uname">@{{ row.ownerUsername }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分类" min-width="140">
        <template #default="{ row }">
          <span class="crumb">{{ row.categoryName }}<i class="sep">◆</i>{{ row.topicName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="字数" width="80" align="right">
        <template #default="{ row }">{{ row.wordCount }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="prev, pager, next, total" :total="total"
                     :page-size="size" :current-page="page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listAllDocs, listUsers } from '../api/admin'

const router = useRouter()

const keyword = ref('')
const items = ref([])
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)

async function load(p = 1) {
  page.value = p
  loading.value = true
  try {
    const data = await listAllDocs({ keyword: keyword.value || undefined, page: p, size })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  const data = await listUsers()
  users.value = data.items || []
}

function openDoc(id) {
  router.push(`/docs/${id}`)
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  load(1)
  loadUsers()
})
</script>

<style scoped lang="scss">
.admin-docs {
  max-width: 1080px;
  margin: 0 auto;
  padding: 20px;
}
.admin-search {
  width: 280px;
  margin-left: auto;
  :deep(.el-input__wrapper) {
    background: var(--ak-bg-2);
    box-shadow: 0 0 0 1px var(--ak-border) inset;
    border-radius: 2px;
  }
}
.user-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0 18px;
}
.user-card {
  flex: 0 0 auto;
  min-width: 150px;
  background: var(--ak-bg-2);
  border: 1px solid var(--ak-border);
  border-left: 3px solid var(--ak-border-2);
  border-radius: 2px;
  padding: 10px 14px;
  .user-role {
    font-family: var(--code-block-font);
    font-size: 10px;
    letter-spacing: 1px;
    color: var(--ak-muted);
    &.admin {
      color: var(--ak-gold);
    }
  }
  .user-name {
    font-weight: 600;
    font-size: 14px;
    margin: 3px 0;
    color: var(--ak-text);
  }
  .user-meta {
    display: flex;
    gap: 10px;
    font-family: var(--code-block-font);
    font-size: 11px;
    color: var(--ak-faint);
  }
}
.admin-table {
  background: transparent;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--ak-bg-2);
  --el-table-border-color: var(--ak-border);
  --el-table-header-text-color: var(--ak-muted);
  --el-table-text-color: var(--ak-text-2);
  --el-table-row-hover-bg-color: var(--ak-bg-3);
  border: 1px solid var(--ak-border);
  border-radius: 2px;
  .doc-title {
    font-size: 13px;
  }
  .owner-cell {
    display: flex;
    flex-direction: column;
    .owner-name {
      color: var(--ak-text);
      font-size: 13px;
    }
    .owner-uname {
      font-family: var(--code-block-font);
      font-size: 11px;
      color: var(--ak-faint);
    }
  }
  .crumb {
    color: var(--ak-muted);
    font-size: 12px;
    .sep {
      color: var(--ak-gold-dim);
      font-size: 8px;
      margin: 0 6px;
      vertical-align: middle;
    }
  }
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .admin-docs {
    padding: 14px 12px;
  }
  .admin-search {
    width: 100%;
    margin-left: 0;
    margin-top: 10px;
  }
  :deep(.page-head) {
    flex-wrap: wrap;
  }
}
</style>
