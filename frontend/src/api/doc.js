import http from './http'

export function listDocs(params) {
  return http.get('/docs', { params })
}

export function getDoc(id) {
  return http.get(`/docs/${id}`)
}

export function createDoc(data) {
  return http.post('/docs', data)
}

export function updateDoc(id, data) {
  return http.put(`/docs/${id}`, data)
}

export function moveDoc(id, topicId) {
  return http.put(`/docs/${id}/move`, { topicId })
}

export function deleteDoc(id) {
  return http.delete(`/docs/${id}`)
}

export function getVersions(id) {
  return http.get(`/docs/${id}/versions`)
}

export function getVersion(id, version) {
  return http.get(`/docs/${id}/versions/${version}`)
}

/** 原始 Markdown 下载（R11） */
export async function downloadRaw(id) {
  const resp = await http.get(`/docs/${id}/raw`, { responseType: 'text' })
  return resp.data
}

/** 全量导出 zip（R31） */
export async function downloadExportZip() {
  const resp = await http.get('/export/all', { responseType: 'blob' })
  const blob = new Blob([resp.data], { type: 'application/zip' })
  const disposition = resp.headers['content-disposition'] || ''
  const match = disposition.match(/filename="?([^";]+)"?/)
  const filename = match ? match[1] : 'learn-notes-export.zip'
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export function search(q, size) {
  return http.get('/search', { params: { q, size } })
}
