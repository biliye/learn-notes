import { defineStore } from 'pinia'
import { login as apiLogin, me } from '../api/auth'

const TOKEN_KEY = 'ln_token'
const USER_KEY = 'ln_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token
  },
  actions: {
    async login(username, password) {
      const data = await apiLogin(username, password)
      this.token = data.token
      this.user = { username: data.username, nickname: data.nickname }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async fetchMe() {
      try {
        this.user = await me()
      } catch (e) {
        // token 失效时由 http 拦截器统一跳登录
      }
    }
  }
})
