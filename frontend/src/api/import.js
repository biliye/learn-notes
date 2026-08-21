import http from './http'

/**
 * 压缩包一键导入（§5.4 编辑器草稿流）：解压解析后返回草稿
 * {title, slug, summary, tags, contentMd, importedImages, skippedImages, warnings}，
 * 不入库，由用户在新建文档页核对后手动保存。
 */
export function importZip(file) {
  const fd = new FormData()
  fd.append('file', file)
  return http.post('/import/zip', fd)
}
