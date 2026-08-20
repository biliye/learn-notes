import http from './http'

export function getTree() {
  return http.get('/catalog/tree')
}

export function createNode(data) {
  return http.post('/catalog', data)
}

export function updateNode(id, data) {
  return http.put(`/catalog/${id}`, data)
}

export function moveNode(id, data) {
  return http.put(`/catalog/${id}/move`, data)
}

export function deleteNode(id) {
  return http.delete(`/catalog/${id}`)
}
