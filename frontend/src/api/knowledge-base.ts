import request from '@/utils/request'
import type { Result, PageResult, KnowledgeBase, KnowledgeBaseCreateRequest, KnowledgeBaseUpdateRequest } from '@/types'

export const knowledgeBaseApi = {
  /** 创建知识库 */
  create(data: KnowledgeBaseCreateRequest) {
    return request.post<Result<number>, Result<number>>('/knowledge-bases', data)
  },

  /** 更新知识库 */
  update(id: number, data: KnowledgeBaseUpdateRequest) {
    return request.put<Result<void>, Result<void>>(`/knowledge-bases/${id}`, data)
  },

  /** 删除知识库 */
  delete(id: number) {
    return request.delete<Result<void>, Result<void>>(`/knowledge-bases/${id}`)
  },

  /** 获取知识库详情 */
  getById(id: number) {
    return request.get<Result<KnowledgeBase>, Result<KnowledgeBase>>(`/knowledge-bases/${id}`)
  },

  /** 分页查询知识库 */
  list(params: {
    name?: string
    category?: string
    status?: string
    pageNum?: number
    pageSize?: number
  }) {
    return request.get<Result<PageResult<KnowledgeBase>>, Result<PageResult<KnowledgeBase>>>('/knowledge-bases', { params })
  },

  /** 获取分类列表 */
  categories() {
    return request.get<Result<string[]>, Result<string[]>>('/knowledge-bases/categories')
  },
}
