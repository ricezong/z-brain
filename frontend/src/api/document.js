import request from './request'

/** 上传文档（异步解析） */
export function uploadDocument(formData) {
  return request.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 获取文档处理进度 */
export function getDocumentProgress(id) {
  return request.get(`/documents/${id}/progress`)
}

/** 获取文档详情 */
export function getDocumentById(id) {
  return request.get(`/documents/${id}`)
}

/** 分页查询文档 */
export function listDocuments(params) {
  return request.get('/documents', { params })
}

/** 删除文档 */
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}

/** 触发向量化 */
export function triggerEmbedding(id) {
  return request.post(`/documents/${id}/embed`)
}

/** 提交审核（批量 Diff） */
export function submitReview(id, data) {
  return request.post(`/documents/${id}/review`, data)
}
