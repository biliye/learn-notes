import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, me } from '../api/auth'
import { useCatalogStore } from './catalog'

const TOKEN_KEY = 'ln_token'
const USER_KEY = 'ln_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    applyUser(data) {
      this.token = data.token
      this.user = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        role: data.role
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      // 分类树是按用户隔离的缓存：换账号必须清掉，否则会把上一个用户的分类显示给新用户
      resetCatalog()
    },
    async login(username, password) {
      const data = await apiLogin(username, password)
      this.applyUser(data)
      return data
    },
    async register(username, password, nickname) {
      const data = await apiRegister(username, password, nickname)
      this.applyUser(data)
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      resetCatalog()
    },
    async fetchMe() {
      try {
        const data = await me()
        this.user = {
          ...(this.user || {}),
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          role: data.role
        }
        localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      } catch (e) {
        // token 失效时由 http 拦截器统一跳登录
      }
    }
  }
})

function resetCatalog() {
  const catalog = useCatalogStore()
  catalog.tree = []
  catalog.loaded = false
}
