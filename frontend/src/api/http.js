import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import router from '../router'

/**
 * axios 实例（规格 §5 统一响应体 {code,msg,data}）。
 * - 请求拦截：自动带 Authorization: Bearer
 * - 响应拦截：统一拆 data；非 0 或 4xx/5xx 弹提示并 reject
 * - 401 → 清 token + 跳 /login?redirect=当前路由（R25）
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 60000
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    // 下载类（blob / text/markdown）直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'text') {
      return response
    }
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg || error.message || '网络错误'
    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      const current = router.currentRoute.value.fullPath
      if (router.currentRoute.value.name !== 'login') {
        ElMessage.warning('登录已失效，请重新登录')
        router.push({ name: 'login', query: { redirect: current } })
      }
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default http
