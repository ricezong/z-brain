import request from './request'

/** 创建提示词模板 */
export function createPromptTemplate(data) {
  return request.post('/prompt-templates', data)
}

/** 更新提示词模板 */
export function updatePromptTemplate(id, data) {
  return request.put(`/prompt-templates/${id}`, data)
}

/** 删除提示词模板 */
export function deletePromptTemplate(id) {
  return request.delete(`/prompt-templates/${id}`)
}

/** 获取提示词模板详情 */
export function getPromptTemplateById(id) {
  return request.get(`/prompt-templates/${id}`)
}

/** 根据知识库 ID 获取提示词模板 */
export function getPromptTemplateByKbId(kbId) {
  return request.get(`/prompt-templates/kb/${kbId}`)
}

/** 获取默认提示词模板 */
export function getDefaultPromptTemplate() {
  return request.get('/prompt-templates/default')
}

/** 查询提示词模板列表 */
export function listPromptTemplates(params) {
  return request.get('/prompt-templates', { params })
}
