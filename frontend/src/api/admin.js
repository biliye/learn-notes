import http from './http'

/** 管理员：跨用户文档列表（含归属用户） */
export function listAllDocs(params) {
  return http.get('/admin/docs', { params })
}

/** 管理员：用户列表（含文档数） */
export function listUsers() {
  return http.get('/admin/users')
}
