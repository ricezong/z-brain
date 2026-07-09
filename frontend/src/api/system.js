import request from './request'

/** ==================== 系统提示词 ==================== **/

/** 查询所有系统提示词 */
export function listPrompts() {
  return request.get('/system/prompts')
}

/** 获取提示词详情 */
export function getPrompt(id) {
  return request.get(`/system/prompts/${id}`)
}

/** 更新系统提示词 */
export function updatePrompt(id, data) {
  return request.put(`/system/prompts/${id}`, data)
}

/** ==================== LLM 模型配置 ==================== **/

/** 查询所有 LLM 模型配置 */
export function listLlmModels() {
  return request.get('/system/llm-models')
}

/** 按类型查询模型列表 */
export function listLlmModelsByType(modelType) {
  return request.get(`/system/llm-models/type/${modelType}`)
}

/** 获取模型配置详情 */
export function getLlmModel(id) {
  return request.get(`/system/llm-models/${id}`)
}

/** 创建模型配置 */
export function createLlmModel(data) {
  return request.post('/system/llm-models', data)
}

/** 更新模型配置 */
export function updateLlmModel(id, data) {
  return request.put(`/system/llm-models/${id}`, data)
}

/** 删除模型配置 */
export function deleteLlmModel(id) {
  return request.delete(`/system/llm-models/${id}`)
}

/** 设置默认模型 */
export function setDefaultLlmModel(id) {
  return request.put(`/system/llm-models/${id}/default`)
}

/** ==================== 外部 API 配置 ==================== **/

/** 获取指定类型的 API 配置 */
export function getApiConfig(configType) {
  return request.get(`/system/api-config/${configType}`)
}

/** 更新指定类型的 API 配置 */
export function updateApiConfig(configType, data) {
  return request.put(`/system/api-config/${configType}`, data)
}
