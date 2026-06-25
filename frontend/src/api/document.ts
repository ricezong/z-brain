import request from '@/utils/request'
import type { Result, PageResult, Document, DocumentProgressResponse, ReviewSubmitRequest } from '@/types'

export const documentApi = {
  /** 上传文档 */
  upload(kbId: number, file: File, userId?: string) {
    const formData = new FormData()
    formData.append('kbId', String(kbId))
    formData.append('file', file)
    if (userId) formData.append('userId', userId)
    return request.post<Result<number>, Result<number>>('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },

  /** 获取文档处理进度 */
  getProgress(id: number) {
    return request.get<Result<DocumentProgressResponse>, Result<DocumentProgressResponse>>(`/documents/${id}/progress`)
  },

  /** 获取文档详情 */
  getById(id: number) {
    return request.get<Result<Document>, Result<Document>>(`/documents/${id}`)
  },

  /** 分页查询文档 */
  list(params: {
    kbId?: number
    fileName?: string
    status?: string
    pageNum?: number
    pageSize?: number
  }) {
    return request.get<Result<PageResult<Document>>, Result<PageResult<Document>>>('/documents', { params })
  },

  /** 删除文档 */
  delete(id: number) {
    return request.delete<Result<void>, Result<void>>(`/documents/${id}`)
  },

  /** 触发向量化 */
  triggerEmbedding(id: number) {
    return request.post<Result<void>, Result<void>>(`/documents/${id}/embed`)
  },

  /** 提交审核 */
  submitReview(id: number, data: ReviewSubmitRequest) {
    return request.post<Result<void>, Result<void>>(`/documents/${id}/review`, data)
  },
}
