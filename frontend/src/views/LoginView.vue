<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <h2 class="login-title">learn-notes</h2>
      <p class="login-sub">个人学习笔记</p>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" autofocus />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large"
                    show-password @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading"
                   @click="onSubmit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = route.query.redirect || '/docs'
    router.push(redirect)
  } catch (e) {
    // 错误提示由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 360px;
  padding: 12px 8px;
}
.login-title {
  text-align: center;
  margin: 8px 0 0;
}
.login-sub {
  text-align: center;
  color: #909399;
  margin: 4px 0 24px;
}
.login-btn {
  width: 100%;
}
</style>
