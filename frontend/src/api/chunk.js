import request from './request'

/** 查询文档下所有分块 */
export function listChunksByDocId(docId) {
  return request.get(`/chunks/document/${docId}`)
}

/** 获取分块详情 */
export function getChunkById(id) {
  return request.get(`/chunks/${id}`)
}

/** 更新分块内容 */
export function updateChunk(data) {
  return request.put('/chunks', data)
}

/** 删除分块 */
export function deleteChunk(id) {
  return request.delete(`/chunks/${id}`)
}

/** 合并多个相邻子块 */
export function mergeChunks(data) {
  return request.post('/chunks/merge', data)
}

/** 拆分单个分块 */
export function splitChunk(data) {
  return request.post('/chunks/split', data)
}

/** 调整父子关系 */
export function adjustParent(chunkId, parentId) {
  return request.put(`/chunks/${chunkId}/parent/${parentId}`)
}
