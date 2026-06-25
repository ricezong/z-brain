import request from '@/utils/request'
import type { Result, Chunk, ChunkUpdateRequest, ChunkMergeRequest, ChunkSplitRequest } from '@/types'

export const chunkApi = {
  /** 查询文档下所有分块 */
  listByDocId(docId: number) {
    return request.get<Result<Chunk[]>, Result<Chunk[]>>(`/chunks/document/${docId}`)
  },

  /** 获取分块详情 */
  getById(id: number) {
    return request.get<Result<Chunk>, Result<Chunk>>(`/chunks/${id}`)
  },

  /** 更新分块内容 */
  update(data: ChunkUpdateRequest) {
    return request.put<Result<void>, Result<void>>('/chunks', data)
  },

  /** 删除分块 */
  delete(id: number) {
    return request.delete<Result<void>, Result<void>>(`/chunks/${id}`)
  },

  /** 合并多个相邻子块 */
  merge(data: ChunkMergeRequest) {
    return request.post<Result<Chunk>, Result<Chunk>>('/chunks/merge', data)
  },

  /** 拆分单个分块 */
  split(data: ChunkSplitRequest) {
    return request.post<Result<Chunk[]>, Result<Chunk[]>>('/chunks/split', data)
  },

  /** 调整父子关系 */
  adjustParent(chunkId: number, parentId: number) {
    return request.put<Result<void>, Result<void>>(`/chunks/${chunkId}/parent/${parentId}`)
  },
}
