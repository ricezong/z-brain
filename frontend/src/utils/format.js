/** 格式化文件大小 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}

/** 格式化日期时间 */
export function formatDateTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 文档状态映射 */
export const docStatusMap = {
  pending: { label: '待解析', type: 'info' },
  parsing: { label: '解析中', type: 'warning' },
  pending_review: { label: '待审核', type: 'primary' },
  embedding: { label: '向量化中', type: 'warning' },
  success: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' }
}

/** 知识库状态映射 */
export const kbStatusMap = {
  active: { label: '启用', type: 'success' },
  inactive: { label: '停用', type: 'info' }
}

/** 分块状态映射 */
export const chunkStatusMap = {
  draft: { label: '草稿', type: 'warning' },
  active: { label: '生效', type: 'success' }
}

/** 分块类型映射 */
export const chunkTypeMap = {
  parent: { label: '父块', type: 'primary' },
  child: { label: '子块', type: '' }
}

/** 获取状态标签信息 */
export function getDocStatus(status) {
  return docStatusMap[status] || { label: status || '未知', type: 'info' }
}

export function getKbStatus(status) {
  return kbStatusMap[status] || { label: status || '未知', type: 'info' }
}

export function getChunkStatus(status) {
  return chunkStatusMap[status] || { label: status || '未知', type: 'info' }
}

export function getChunkType(type) {
  return chunkTypeMap[type] || { label: type || '未知', type: 'info' }
}
