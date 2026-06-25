import dayjs from 'dayjs'

/** 格式化日期时间 */
export function formatDateTime(date: string | Date | null | undefined): string {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

/** 格式化文件大小 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 文档状态映射 */
export function documentStatusMap(status: string): { label: string; type: 'info' | 'warning' | 'success' | 'danger' | 'primary' } {
  const map: Record<string, { label: string; type: any }> = {
    pending: { label: '待解析', type: 'info' },
    parsing: { label: '解析中', type: 'warning' },
    pending_review: { label: '待审核', type: 'primary' },
    embedding: { label: '向量化中', type: 'warning' },
    success: { label: '已完成', type: 'success' },
    failed: { label: '失败', type: 'danger' },
  }
  return map[status] || { label: status, type: 'info' }
}

/** 分块状态映射 */
export function chunkStatusMap(status: string): { label: string; type: 'info' | 'success' } {
  const map: Record<string, { label: string; type: any }> = {
    draft: { label: '草稿', type: 'info' },
    active: { label: '激活', type: 'success' },
  }
  return map[status] || { label: status, type: 'info' }
}

/** 分块类型映射 */
export function chunkTypeMap(type: string): { label: string; color: string } {
  const map: Record<string, { label: string; color: string }> = {
    parent: { label: '父块', color: '#409EFF' },
    child: { label: '子块', color: '#67C23A' },
  }
  return map[type] || { label: type, color: '#909399' }
}

/** 解析 JSON 元数据 */
export function parseMetadata(metadata: string | null): Record<string, any> {
  if (!metadata) return {}
  try {
    return JSON.parse(metadata)
  } catch {
    return {}
  }
}

/** 生成唯一 ID */
export function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2)
}
