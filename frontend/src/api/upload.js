import http from './http'

/** 图片上传（R30、§5.6），返回 {url,width,height,bytes,dedup} */
export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/uploads/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
