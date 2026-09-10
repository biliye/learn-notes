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
        <!-- 管理员账号不提供改密/删除：不支持管理员之间互相改号，也没有删自己这条自锁死路 -->
        <div v-if="u.role !== 'ADMIN'" class="user-actions">
          <button type="button" class="card-act" @click="openReset(u)">重置密码</button>
          <button type="button" class="card-act danger" @click="confirmDelete(u)">删除</button>
        </div>
      </div>
      <button type="button" class="user-card user-card-add" @click="openCreate">
        <span class="add-plus" aria-hidden="true">+</span>
        <span class="add-text">新建用户</span>
      </button>
    </div>

    <el-dialog v-model="reset.visible" title="重置密码" width="min(420px, 92vw)">
      <div class="reset-target">
        <span class="reset-who">{{ reset.user?.nickname || reset.user?.username }}</span>
        <span class="reset-uname">@{{ reset.user?.username }}</span>
      </div>
      <el-form label-width="84px" @submit.prevent>
        <el-form-item label="新密码">
          <el-input v-model="reset.password" type="password" show-password placeholder="至少 8 位"
                    @input="reset.error = ''" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="reset.confirm" type="password" show-password placeholder="再输一遍"
                    @input="reset.error = ''" />
        </el-form-item>
      </el-form>
      <div v-if="reset.error" class="create-error">{{ reset.error }}</div>
      <div class="create-tip">改完请告知本人。对方已登录的页面不会被踢下线（旧 token 到期前仍有效）。</div>
      <template #footer>
        <el-button @click="reset.visible = false">取消</el-button>
        <el-button type="primary" :loading="reset.saving" @click="submitReset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="create.visible" title="新建用户" width="min(440px, 92vw)">
      <el-form label-width="84px" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="create.username" placeholder="3~32 位字母/数字/下划线/连字符" @input="create.error = ''" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="create.nickname" placeholder="可留空，默认同用户名" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="create.password" type="password" show-password placeholder="至少 8 位"
                    @input="create.error = ''" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="create.role">
            <el-radio value="USER">普通用户</el-radio>
            <el-radio value="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div v-if="create.error" class="create-error">{{ create.error }}</div>
      <div class="create-tip">初始密码只在此刻设置，请转告本人并让他登录后自行修改。</div>
      <template #footer>
        <el-button @click="create.visible = false">取消</el-button>
        <el-button type="primary" :loading="create.saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-table :data="items" v-loading="loading" class="admin-table" :empty-text="'没有文档'">
      <el-table-column label="标题" :min-width="isMobile ? 100 : 220">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" class="doc-title" @click="openDoc(row.id)">
            {{ row.title }}
          </el-link>
          <div v-if="isMobile && row.categoryName" class="crumb crumb-inline">
            {{ row.categoryName }}<i class="sep">/</i>{{ row.topicName }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="归属用户" :min-width="isMobile ? 84 : 140">
        <template #default="{ row }">
          <div class="owner-cell">
            <span class="owner-name">{{ row.ownerNickname || row.ownerUsername }}</span>
            <span class="owner-uname">@{{ row.ownerUsername }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="分类" min-width="180">
        <template #default="{ row }">
          <span class="crumb">
            <template v-if="row.categoryName">{{ row.categoryName }}<i class="sep">/</i></template>{{ row.topicName }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="!isMobile" label="字数" width="80" align="right">
        <template #default="{ row }">{{ row.wordCount }}</template>
      </el-table-column>
      <el-table-column label="更新时间" :min-width="isMobile ? 100 : 160">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="isMobile ? 68 : 88" align="right">
        <template #default="{ row }">
          <button type="button" class="card-act danger" @click="confirmDeleteDoc(row)">删除</button>
        </template>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllDocs, listUsers, createUser, resetUserPassword, deleteUser } from '../api/admin'
import { deleteDoc } from '../api/doc'
import { useIsMobile } from '../composables/useIsMobile'

const router = useRouter()
const isMobile = useIsMobile()

const keyword = ref('')
const items = ref([])
const users = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)

const create = ref({ visible: false, username: '', nickname: '', password: '', role: 'USER', saving: false, error: '' })
const reset = ref({ visible: false, user: null, password: '', confirm: '', saving: false, error: '' })

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

function openCreate() {
  create.value = { visible: true, username: '', nickname: '', password: '', role: 'USER', saving: false, error: '' }
}

async function submitCreate() {
  const { username, password, nickname, role } = create.value
  if (!username.trim()) {
    create.value.error = '请填写用户名'
    return
  }
  if (!/^[A-Za-z0-9_-]{3,32}$/.test(username.trim())) {
    create.value.error = '用户名需为 3~32 位字母/数字/下划线/连字符'
    return
  }
  if (password.length < 8) {
    create.value.error = '密码至少 8 位'
    return
  }
  create.value.error = ''
  create.value.saving = true
  try {
    const created = await createUser({ username: username.trim(), password, nickname: nickname.trim(), role })
    create.value.visible = false
    ElMessage.success(`已创建 ${created.nickname || created.username}（@${created.username}）`)
    await loadUsers()
  } catch (e) {
    create.value.error = e.response?.data?.msg || e.message || '创建失败，请重试'
  } finally {
    create.value.saving = false
  }
}

function openDoc(id) {
  router.push(`/docs/${id}`)
}

function openReset(u) {
  reset.value = { visible: true, user: u, password: '', confirm: '', saving: false, error: '' }
}

async function submitReset() {
  const { user, password, confirm } = reset.value
  if (password.length < 8) {
    reset.value.error = '密码至少 8 位'
    return
  }
  if (password !== confirm) {
    reset.value.error = '两次输入的密码不一致'
    return
  }
  reset.value.error = ''
  reset.value.saving = true
  try {
    await resetUserPassword(user.id, password)
    reset.value.visible = false
    ElMessage.success(`已重置 @${user.username} 的密码`)
  } catch (e) {
    reset.value.error = e.response?.data?.msg || e.message || '重置失败，请重试'
  } finally {
    reset.value.saving = false
  }
}

async function confirmDeleteDoc(row) {
  try {
    await ElMessageBox.confirm(
      `将删除「${row.title}」（@${row.ownerUsername}）及其全部历史版本与见解，不可恢复。正文里不再被引用的图片会一并清理。`,
      '删除文档',
      { confirmButtonText: '永久删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 取消
  }
  try {
    await deleteDoc(row.id)
    ElMessage.success(`已删除「${row.title}」`)
    await load(page.value)
  } catch {
    // 失败信息已由 http 拦截器提示
  }
}

async function confirmDelete(u) {
  const content = `将永久删除 @${u.username} 及其 ${u.docCount} 篇笔记、整个分类树与本人上传的图片，不可恢复。`
  try {
    await ElMessageBox.prompt(content, '删除账号', {
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
      type: 'warning',
      inputPlaceholder: `输入 ${u.username} 以确认`,
      inputValidator: (v) => (v === u.username ? true : `请完整输入用户名 ${u.username}`)
    })
  } catch {
    return // 取消
  }
  try {
    const res = await deleteUser(u.id)
    ElMessage.success(`已删除 @${u.username}（清除 ${res.deletedDocs} 篇文档、${res.deletedCatalogNodes} 个目录、${res.deletedImages} 张图片）`)
    await Promise.all([loadUsers(), load(1)])
  } catch {
    // 失败信息已由 http 拦截器提示
  }
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
  .user-actions {
    display: flex;
    gap: 6px;
    margin-top: 8px;
  }
}
.card-act {
  flex: 1;
  min-width: 52px;
  padding: 4px 10px;
  border: 1px solid var(--ak-border);
  border-radius: 6px;
  background: transparent;
  color: var(--ak-muted);
  font-family: var(--ak-font-body);
  font-size: 11px;
  cursor: pointer;
  &:hover {
    color: var(--ak-text);
    border-color: var(--ak-border-2);
    background: var(--ak-bg-3);
  }
  &.danger:hover {
    color: #c62828;
    border-color: rgba(255, 59, 48, 0.35);
    background: rgba(255, 59, 48, 0.08);
  }
}
.reset-target {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--ak-border);
  .reset-who {
    color: var(--ak-text);
    font-weight: 600;
    font-size: 14px;
  }
  .reset-uname {
    font-family: var(--code-block-font);
    font-size: 11px;
    color: var(--ak-faint);
  }
}
.user-card-add {
  display: flex;
  align-items: center;
  gap: 7px;
  border-style: dashed;
  color: var(--ak-muted);
  font-family: var(--ak-font-body);
  font-size: 13px;
  cursor: pointer;
  &:hover {
    color: var(--ak-gold);
    border-color: var(--ak-border-2);
    background: var(--ak-bg-3);
  }
  .add-plus {
    font-size: 16px;
    line-height: 1;
  }
}
.create-error {
  margin: 0 0 10px;
  padding: 7px 11px;
  border: 1px solid rgba(255, 59, 48, 0.22);
  border-radius: 8px;
  background: rgba(255, 59, 48, 0.09);
  color: #c62828;
  font-size: 12px;
  line-height: 1.6;
}
.create-tip {
  color: var(--ak-muted);
  font-size: 12px;
  line-height: 1.6;
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
      font-size: 11px;
      margin: 0 5px;
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
  /* 小屏折叠"分类/字数"列后，分类并入标题格；单元格按单位换行不越界 */
  .crumb-inline {
    margin-top: 2px;
    font-size: 11px;
  }
  :deep(.el-table .cell) {
    white-space: normal;
    word-break: normal;
    padding: 0 8px;
  }
}
</style>
