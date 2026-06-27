// ==================== 通用类型 ====================
export interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T = any> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ==================== 知识库 ====================
export interface KnowledgeBase {
  id: number
  name: string
  description: string
  category: string
  promptTemplateId: number
  status: string
  docCount: number
  chunkCount: number
  createBy: string
  updateBy: string
  createTime: string
  updateTime: string
}

export interface KnowledgeBaseCreateRequest {
  name: string
  description?: string
  category?: string
  promptTemplateId?: number
}

export interface KnowledgeBaseUpdateRequest {
  name?: string
  description?: string
  category?: string
  promptTemplateId?: number
  status?: string
}

// ==================== 文档 ====================
export interface Document {
  id: number
  kbId: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  fileHash: string
  status: string
  chunkCount: number
  parseProgress: number
  errorMessage: string
  metadata: string
  createBy: string
  createTime: string
  updateTime: string
}

export interface DocumentProgressResponse {
  documentId: number
  status: string
  progress: number
  errorMessage: string
  chunkCount: number
}

export interface ReviewSubmitRequest {
  added?: ChunkDiffItem[]
  modified?: ChunkDiffItem[]
  deleted?: number[]
}

export interface ChunkDiffItem {
  id?: number
  docId: number
  kbId: number
  parentId?: number
  chunkType?: string
  content: string
  tokenCount?: number
  metadata?: string
}

// ==================== 分块 ====================
export interface Chunk {
  id: number
  docId: number
  kbId: number
  parentId: number
  chunkType: string
  content: string
  contentVector: string
  tsv: string
  tokenCount: number
  status: string
  metadata: string
  createTime: string
  updateTime: string
}

export interface ChunkUpdateRequest {
  id: number
  content?: string
  parentId?: number
  status?: string
  metadata?: string
}

export interface ChunkMergeRequest {
  chunkIds: number[]
  parentId?: number
}

export interface ChunkSplitRequest {
  chunkId: number
  splitPosition: number
}

// ==================== 对话 ====================
export interface ChatSession {
  id: string
  title: string
  kbId: number | null
  kbName: string
  updated: string
  preview: string
}

export interface ChatRequest {
  sessionId?: string
  kbId: number
  query: string
  userId?: string
  stream?: boolean
  enableHyde?: boolean
  enableQueryRewrite?: boolean
}

export interface ChatResponse {
  sessionId: string
  query: string
  rewrittenQuery: string
  hydeAnswer: string
  answer: string
  citations: Citation[]
  hitChunkIds: number[]
  tokenUsage: TokenUsage
  costTimeMs: number
}

export interface RewriteRequest {
  query: string
  sessionId?: string
}

export interface RewriteResponse {
  originalQuery: string
  rewrittenQuery: string
}

export interface Citation {
  label: string
  chunkId: number
  docId: number
  docName: string
  snippet: string
  page: number
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

// ==================== 提示词模板 ====================
export interface PromptTemplate {
  id: number
  kbId: number
  name: string
  systemPrompt: string
  userPrompt: string
  isDefault: boolean
  createTime: string
  updateTime: string
}

export interface PromptTemplateRequest {
  kbId?: number
  name: string
  systemPrompt: string
  userPrompt: string
  isDefault?: boolean
}
