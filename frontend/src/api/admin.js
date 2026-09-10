import http from './http'

/** 管理员：跨用户文档列表（含归属用户） */
export function listAllDocs(params) {
  return http.get('/admin/docs', { params })
}

/** 管理员：用户列表（含文档数） */
export function listUsers() {
  return http.get('/admin/users')
}

/** 管理员：建号（无自助注册，这是唯一创建账号的入口） */
export function createUser(payload) {
  return http.post('/admin/users', payload)
}

/** 管理员：重置普通用户口令（无需旧密码） */
export function resetUserPassword(id, newPassword) {
  return http.post(`/admin/users/${id}/password`, { newPassword })
}

/** 管理员：删除普通用户账号（连带其文档与分类，不可恢复） */
export function deleteUser(id) {
  return http.delete(`/admin/users/${id}`)
}
