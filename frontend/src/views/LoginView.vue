<template>
  <div class="login-page">
    <!-- 顶部扫描线 -->
    <div class="scan-line" aria-hidden="true"></div>
    <!-- 左侧装饰斜切 -->
    <div class="deco deco-left" aria-hidden="true"></div>
    <!-- 右侧装饰菱形阵列 -->
    <div class="deco deco-diamonds" aria-hidden="true">
      <i v-for="n in 5" :key="n"></i>
    </div>

    <div class="login-panel ak-frame">
      <div class="panel-top">
        <div class="login-mark" aria-hidden="true">
          <span class="login-diamond"></span>
        </div>
        <div class="login-title">learn-notes</div>
        <div class="login-sub">个人学习笔记 · 战术档案系统</div>
        <div class="login-code">ACCESS // AUTH TERMINAL</div>
      </div>

      <el-form class="login-form" @submit.prevent="onSubmit">
        <el-form-item v-if="mode === 'register'">
          <el-input v-model="nickname" placeholder="昵称（可选，默认同用户名）" size="large">
            <template #prefix><el-icon><Postcard /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="username" placeholder="用户名 / USERNAME" size="large" autofocus @input="errorMsg = ''">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" :placeholder="mode === 'register' ? '密码（至少 6 位）' : '密码 / PASSWORD'" size="large"
                    show-password @keyup.enter="onSubmit" @input="errorMsg = ''">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <div v-if="errorMsg" class="login-error" role="alert">
          <span class="login-error-mark" aria-hidden="true">!</span>
          <span>{{ errorMsg }}</span>
        </div>
        <el-button type="primary" size="large" class="login-btn ak-btn-slant" :loading="loading"
                   @click="onSubmit">{{ mode === 'register' ? '注 册' : '登 录' }}</el-button>
        <div class="mode-switch">
          <template v-if="mode === 'login'">
            还没有账号？
            <el-link type="primary" :underline="false" class="switch-link" @click="switchMode('register')">立即注册</el-link>
          </template>
          <template v-else>
            已有账号？
            <el-link type="primary" :underline="false" class="switch-link" @click="switchMode('login')">直接登录</el-link>
          </template>
        </div>
      </el-form>

      <div class="panel-foot">
        <span>LEARN-NOTES // ARCHIVE SYSTEM</span>
        <span class="foot-ver">v0.1.0</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const errorMsg = ref('')

function switchMode(next) {
  mode.value = next
  nickname.value = ''
  password.value = ''
  errorMsg.value = ''
}

async function onSubmit() {
  if (!username.value || !password.value) return
  if (mode.value === 'register' && password.value.length < 6) return
  errorMsg.value = ''
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.register(username.value, password.value, nickname.value)
    } else {
      await auth.login(username.value, password.value)
    }
    const redirect = route.query.redirect || '/docs'
    router.push(redirect)
  } catch (e) {
    // 后端错误信息（含剩余可尝试次数）由拦截器弹出 toast，这里同步内联展示
    errorMsg.value = e.response?.data?.msg || e.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  /* 底部暗化，突出登录面板 */
  background:
    linear-gradient(180deg, rgba(20, 22, 29, 0.2), rgba(20, 22, 29, 0.65)),
    linear-gradient(90deg, rgba(201, 168, 106, 0.04), transparent 40%);
}
/* 顶部扫描线 */
.scan-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--ak-gold), transparent);
  animation: scan 4s ease-in-out infinite;
  pointer-events: none;
}
@keyframes scan {
  0%, 100% { opacity: 0.15; }
  50% { opacity: 0.7; }
}
/* 左侧斜切装饰板 */
.deco-left {
  position: absolute;
  left: -60px;
  bottom: -60px;
  width: 320px;
  height: 320px;
  background: linear-gradient(135deg, var(--ak-bg-3), transparent 70%);
  clip-path: polygon(0 30%, 100% 100%, 0 100%);
  opacity: 0.5;
  pointer-events: none;
}
/* 右侧菱形阵列 */
.deco-diamonds {
  position: absolute;
  top: 12%;
  right: 8%;
  display: flex;
  flex-direction: column;
  gap: 18px;
  pointer-events: none;
  i {
    width: 12px;
    height: 12px;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: var(--ak-gold-dim);
    opacity: 0.35;
    &:nth-child(even) { margin-left: 20px; opacity: 0.2; }
  }
}

/* ---- 登录面板 ---- */
.login-panel {
  width: min(380px, calc(100vw - 40px));
  background: linear-gradient(180deg, var(--ak-bg-3), var(--ak-bg-2));
  border: 1px solid var(--ak-border-2);
  border-radius: 2px;
  padding: 36px 34px 20px;
  position: relative;
  z-index: 2;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.55);
}
.panel-top {
  text-align: center;
  margin-bottom: 28px;
}
.login-mark {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
  background: linear-gradient(135deg, var(--ak-gold-bright), var(--ak-gold-dim));
  .login-diamond {
    width: 20px;
    height: 20px;
    clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
    background: var(--ak-bg-2);
  }
}
.login-title {
  font-family: var(--ak-font-display);
  font-weight: 700;
  font-size: 30px;
  letter-spacing: 3px;
  color: var(--ak-text);
  text-transform: uppercase;
  margin: 0 0 6px;
}
.login-sub {
  color: var(--ak-muted);
  font-size: 13px;
  margin: 0 0 10px;
}
.login-code {
  display: inline-block;
  font-family: var(--code-block-font);
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--ak-gold);
  border: 1px solid var(--ak-border-2);
  padding: 3px 10px;
  border-radius: 2px;
  background: rgba(201, 168, 106, 0.06);
}

.login-form {
  :deep(.el-input__wrapper) {
    background: var(--ak-bg-0);
    box-shadow: 0 0 0 1px var(--ak-border-2) inset;
    border-radius: 2px;
  }
  :deep(.el-input__inner::placeholder) {
    font-family: var(--code-block-font);
    font-size: 12px;
    letter-spacing: 1px;
  }
}
.login-btn {
  width: 100%;
  font-family: var(--ak-font-display);
  font-weight: 600;
  letter-spacing: 4px;
  font-size: 16px;
}
.login-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
  padding: 8px 12px;
  border: 1px solid var(--ak-red);
  border-radius: 2px;
  background: rgba(166, 58, 65, 0.14);
  color: #e0848c;
  font-size: 12px;
  line-height: 1.6;
  .login-error-mark {
    flex: none;
    width: 16px;
    height: 16px;
    margin-top: 1px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: var(--ak-red);
    color: var(--ak-bg-0);
    font-family: var(--ak-font-display);
    font-weight: 700;
    font-size: 11px;
    border-radius: 2px;
  }
}
.mode-switch {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--ak-muted);
  .switch-link {
    font-size: 12px;
  }
}
.panel-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid var(--ak-border);
  font-family: var(--code-block-font);
  font-size: 10px;
  letter-spacing: 1px;
  color: var(--ak-faint);
}

@media (prefers-reduced-motion: reduce) {
  .scan-line { animation: none; }
}

/* ---------- 移动端：面板与装饰压缩 ---------- */
@media (max-width: 768px) {
  .login-panel {
    padding: 28px 22px 16px;
  }
  .deco-diamonds {
    display: none;
  }
  .panel-foot {
    flex-wrap: wrap;
    gap: 4px;
  }
}
</style>
