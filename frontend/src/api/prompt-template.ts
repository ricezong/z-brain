import request from '@/utils/request'
import type { Result, PromptTemplate, PromptTemplateRequest } from '@/types'

export const promptTemplateApi = {
  /** 创建提示词模板 */
  create(data: PromptTemplateRequest) {
    return request.post<Result<number>, Result<number>>('/prompt-templates', data)
  },

  /** 更新提示词模板 */
  update(id: number, data: PromptTemplateRequest) {
    return request.put<Result<void>, Result<void>>(`/prompt-templates/${id}`, data)
  },

  /** 删除提示词模板 */
  delete(id: number) {
    return request.delete<Result<void>, Result<void>>(`/prompt-templates/${id}`)
  },

  /** 获取提示词模板详情 */
  getById(id: number) {
    return request.get<Result<PromptTemplate>, Result<PromptTemplate>>(`/prompt-templates/${id}`)
  },

  /** 根据知识库 ID 获取提示词模板 */
  getByKbId(kbId: number) {
    return request.get<Result<PromptTemplate>, Result<PromptTemplate>>(`/prompt-templates/kb/${kbId}`)
  },

  /** 获取默认提示词模板 */
  getDefault() {
    return request.get<Result<PromptTemplate>, Result<PromptTemplate>>('/prompt-templates/default')
  },

  /** 查询提示词模板列表 */
  list(kbId?: number) {
    return request.get<Result<PromptTemplate[]>, Result<PromptTemplate[]>>('/prompt-templates', { params: { kbId } })
  },
}
