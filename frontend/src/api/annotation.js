import http from './http'

export function createAnnotation(data) {
  return http.post('/annotations', data)
}

export function updateAnnotation(id, contentMd) {
  return http.put(`/annotations/${id}`, { contentMd })
}

export function reanchorAnnotation(id, anchor) {
  return http.post(`/annotations/${id}/reanchor`, { anchor })
}

export function confirmAnnotation(id) {
  return http.post(`/annotations/${id}/confirm`)
}

export function deleteAnnotation(id) {
  return http.delete(`/annotations/${id}`)
}
