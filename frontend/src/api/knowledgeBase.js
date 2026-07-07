import request from './request'

/** 创建知识库 */
export function createKnowledgeBase(data) {
  return request.post('/knowledge-bases', data)
}

/** 更新知识库 */
export function updateKnowledgeBase(id, data) {
  return request.put(`/knowledge-bases/${id}`, data)
}

/** 删除知识库（软删除） */
export function deleteKnowledgeBase(id) {
  return request.delete(`/knowledge-bases/${id}`)
}

/** 获取知识库详情 */
export function getKnowledgeBaseById(id) {
  return request.get(`/knowledge-bases/${id}`)
}

/** 分页查询知识库 */
export function listKnowledgeBases(params) {
  return request.get('/knowledge-bases', { params })
}

/** 获取知识库分类列表 */
export function listCategories() {
  return request.get('/knowledge-bases/categories')
}
