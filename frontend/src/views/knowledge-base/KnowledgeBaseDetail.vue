<template>
  <div class="kb-detail">
    <div class="kb-detail-container">
      <!-- Header -->
      <div class="kb-detail-header">
        <button class="kb-detail-back" @click="$router.push('/knowledge-bases')" aria-label="返回知识库列表">
          <svg viewBox="0 0 24 24"><path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
        </button>
        <div class="kb-detail-info">
          <h2>{{ kb?.name }}</h2>
          <p>{{ kb?.description || '暂无描述' }}</p>
          <div class="kb-detail-meta">
            <span>创建于 {{ kb?.createTime?.slice(0, 10) }}</span>
            <span>·</span>
            <span>{{ kb?.docCount ?? 0 }} 个文档 · {{ kb?.chunkCount ?? 0 }} 个分块</span>
          </div>
        </div>
        <div class="kb-detail-actions">
          <button class="btn btn-primary" @click="startChatWithKb">
            <svg viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            开始对话
          </button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tabs">
        <button class="tab active">文档<span class="count">{{ documents.length }}</span></button>
      </div>

      <!-- Document table -->
      <div class="doc-toolbar">
        <div class="search-input">
          <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          <input type="text" placeholder="搜索文档..." v-model="searchQuery" />
        </div>
      </div>

      <div class="doc-table">
        <div class="doc-table-head">
          <div>文档名称</div>
          <div>大小</div>
          <div>分块</div>
          <div>状态</div>
          <div></div>
        </div>
        <div v-for="doc in filteredDocs" :key="doc.id" class="doc-row" @click="openChunkReview(doc)">
          <div class="doc-name">
            <div class="doc-icon" :class="getFileExt(doc.fileName)">{{ getFileExt(doc.fileName).toUpperCase() }}</div>
            <div class="doc-name-text">
              <div class="n">{{ doc.fileName }}</div>
              <div class="s">更新于 {{ doc.updateTime?.slice(0, 10) }}</div>
            </div>
          </div>
          <div class="doc-size">{{ formatSize(doc.fileSize) }}</div>
          <div class="doc-chunks">{{ doc.chunkCount ?? 0 }}</div>
          <div class="doc-status-cell">
            <span class="status-badge" :class="getStatusClass(doc.status)">
              <span class="dot"></span>
              {{ getStatusLabel(doc.status) }}
            </span>
          </div>
          <div style="display:flex;justify-content:flex-end;">
            <button class="doc-action" @click.stop="confirmDeleteDoc(doc)" aria-label="删除文档">
              <svg viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </div>
        </div>
        <div v-if="filteredDocs.length === 0" style="padding:var(--s-7) var(--s-5);text-align:center;color:var(--text-muted);font-size:13px;">
          {{ searchQuery ? '没有匹配的文档' : '此知识库还没有文档' }}
        </div>
      </div>
    </div>

    <!-- Delete confirm -->
    <div v-if="showDeleteModal" class="modal-overlay" @click.self="showDeleteModal = false">
      <div class="modal" style="max-width:400px;">
        <div class="modal-header">
          <h2 class="modal-title">确认删除</h2>
          <button class="modal-close" @click="showDeleteModal = false">
            <svg viewBox="0 0 24 24"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px;line-height:1.6;">删除文档「{{ deleteTarget?.fileName }}」会同时删除其所有分块，且无法恢复。</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showDeleteModal = false">取消</button>
          <button class="btn btn-danger" @click="doDeleteDoc">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { knowledgeBaseApi } from '@/api/knowledge-base'
import { documentApi } from '@/api/document'
import type { KnowledgeBase, Document } from '@/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const kb = ref<KnowledgeBase | null>(null)
const documents = ref<Document[]>([])
const searchQuery = ref('')
const showDeleteModal = ref(false)
const deleteTarget = ref<Document | null>(null)

const filteredDocs = computed(() => {
  if (!searchQuery.value) return documents.value
  return documents.value.filter(d => d.fileName.toLowerCase().includes(searchQuery.value.toLowerCase()))
})

function getFileExt(name: string): string {
  const ext = name.split('.').pop()?.toLowerCase() || 'txt'
  return ext
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return `${size.toFixed(i > 0 ? 1 : 0)} ${units[i]}`
}

function getStatusClass(status: string): string {
  if (status === 'success' || status === 'active') return 'ready'
  if (status === 'embedding' || status === 'parsing') return 'processing'
  return 'empty'
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    success: '可用', active: '可用', embedding: '处理中', parsing: '处理中',
    pending_review: '待审核', draft: '草稿', failed: '失败',
  }
  return map[status] || status
}

async function loadKbDetail() {
  const kbId = Number(route.params.kbId)
  if (!kbId) return
  try {
    const res = await knowledgeBaseApi.getById(kbId)
    kb.value = res.data
    const docRes = await documentApi.list({ kbId, pageNum: 1, pageSize: 100 })
    documents.value = docRes.data.list
  } catch (e) {
    console.error(e)
  }
}

function openChunkReview(doc: Document) {
  router.push(`/documents/${doc.id}/chunks`)
}

function confirmDeleteDoc(doc: Document) {
  deleteTarget.value = doc
  showDeleteModal.value = true
}

async function doDeleteDoc() {
  if (!deleteTarget.value) return
  try {
    await documentApi.delete(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadKbDetail()
  } catch (e) {
    console.error(e)
  }
}

function startChatWithKb() {
  if (kb.value) appStore.setCurrentKb(kb.value)
  router.push('/chat')
}

onMounted(loadKbDetail)
onActivated(loadKbDetail)

watch(() => route.params.kbId, (newId, oldId) => {
  if (newId && newId !== oldId) {
    searchQuery.value = ''
    loadKbDetail()
  }
})
</script>

<style scoped>
.kb-detail {
  height: 100%;
  overflow-y: auto;
  padding: var(--s-6);
}

.kb-detail-container { max-width: 1100px; margin: 0 auto; }

.kb-detail-header {
  display: flex;
  align-items: flex-start;
  gap: var(--s-4);
  margin-bottom: var(--s-6);
  padding-bottom: var(--s-5);
  border-bottom: 1px solid var(--border);
}

.kb-detail-back {
  width: 32px; height: 32px;
  border-radius: var(--r-md);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.kb-detail-back:hover { background: var(--bg-hover); color: var(--text); }
.kb-detail-back svg { width: 18px; height: 18px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.kb-detail-info { flex: 1; }
.kb-detail-info h2 { font-size: 22px; font-weight: 600; letter-spacing: -0.01em; margin-bottom: 4px; }
.kb-detail-info p { font-size: 13px; color: var(--text-secondary); margin-bottom: var(--s-3); }
.kb-detail-meta { display: flex; gap: var(--s-4); font-size: 12px; color: var(--text-muted); }
.kb-detail-meta span { display: flex; align-items: center; gap: 4px; }

.kb-detail-actions { display: flex; gap: var(--s-2); }

.btn {
  display: inline-flex; align-items: center; gap: var(--s-2);
  padding: 8px 14px; border-radius: var(--r-md);
  font-size: 13px; font-weight: 500;
  transition: background 0.12s, border-color 0.12s, color 0.12s;
  border: 1px solid transparent;
}
.btn svg { width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.btn-primary { background: var(--primary); color: var(--primary-foreground); }
.btn-primary:hover { background: var(--primary-hover); }
.btn-secondary { background: var(--bg); color: var(--text); border-color: var(--border); }
.btn-secondary:hover { background: var(--bg-hover); }
.btn-ghost { background: transparent; color: var(--text-secondary); }
.btn-ghost:hover { background: var(--bg-hover); color: var(--text); }
.btn-danger { background: var(--danger); color: white; }
.btn-danger:hover { background: #B91C1C; }
.btn-sm { padding: 5px 10px; font-size: 12px; }

/* Tabs */
.tabs {
  display: flex; gap: 2px;
  border-bottom: 1px solid var(--border);
  margin-bottom: var(--s-5);
}
.tab {
  padding: var(--s-3) var(--s-4);
  font-size: 13px; font-weight: 500;
  color: var(--text-muted);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab.active { color: var(--text); border-bottom-color: var(--text); }
.tab .count {
  margin-left: 6px; font-size: 11px; color: var(--text-muted);
  background: var(--bg-muted); padding: 1px 6px; border-radius: var(--r-sm);
}

/* Doc toolbar */
.doc-toolbar {
  display: flex; align-items: center; gap: var(--s-3);
  margin-bottom: var(--s-4);
}
.search-input { flex: 1; max-width: 320px; position: relative; }
.search-input svg {
  position: absolute; left: 10px; top: 50%; transform: translateY(-50%);
  width: 14px; height: 14px; color: var(--text-muted);
  stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round;
}
.search-input input {
  width: 100%; padding: 7px 12px 7px 32px;
  border: 1px solid var(--border); border-radius: var(--r-md);
  font-size: 13px; background: var(--bg);
  transition: border-color 0.12s;
}
.search-input input:focus { border-color: var(--primary); outline: none; }
.search-input input::placeholder { color: var(--text-muted); }

/* Doc table */
.doc-table {
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.doc-table-head {
  display: grid;
  grid-template-columns: 1fr 120px 100px 140px 80px;
  padding: var(--s-3) var(--s-4);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border);
  font-size: 12px; font-weight: 600;
  color: var(--text-muted); letter-spacing: 0.02em;
}
.doc-row {
  display: grid;
  grid-template-columns: 1fr 120px 100px 140px 80px;
  padding: var(--s-3) var(--s-4);
  border-bottom: 1px solid var(--border);
  font-size: 13px; align-items: center;
  transition: background 0.12s; cursor: pointer;
}
.doc-row:last-child { border-bottom: none; }
.doc-row:hover { background: var(--bg-subtle); }

.doc-name { display: flex; align-items: center; gap: var(--s-3); min-width: 0; }
.doc-icon {
  width: 28px; height: 28px; border-radius: var(--r-sm);
  background: var(--bg-muted); color: var(--text-secondary);
  display: flex; align-items: center; justify-content: center;
  font-size: 9px; font-weight: 700; letter-spacing: 0.02em; flex-shrink: 0;
}
.doc-icon.pdf { background: #FEE2E2; color: #B91C1C; }
.doc-icon.md { background: #DBEAFE; color: #1E40AF; }
.doc-icon.docx { background: #DBEAFE; color: #1E40AF; }
.doc-icon.txt { background: var(--bg-muted); color: var(--text-secondary); }

.doc-name-text { min-width: 0; }
.doc-name-text .n { font-weight: 500; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.doc-name-text .s { font-size: 11px; color: var(--text-muted); }

.doc-size, .doc-chunks { font-variant-numeric: tabular-nums; color: var(--text-secondary); font-size: 12px; }
.doc-status-cell .status-badge { font-size: 10px; padding: 2px 6px; }

.doc-action {
  width: 28px; height: 28px; border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-muted); margin-left: auto;
}
.doc-action:hover { background: var(--bg-hover); color: var(--text); }
.doc-action svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.status-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: var(--r-sm);
  font-size: 11px; font-weight: 500;
}
.status-badge.ready { background: var(--success-soft); color: var(--success); }
.status-badge.processing { background: var(--warning-soft); color: var(--warning); }
.status-badge.empty { background: var(--bg-muted); color: var(--text-muted); }
.status-badge .dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(9, 9, 11, 0.4);
  z-index: 100; display: flex; align-items: center; justify-content: center; padding: var(--s-6);
}
.modal {
  background: var(--bg); border-radius: var(--r-xl); box-shadow: var(--shadow-pop);
  max-width: 480px; width: 100%; max-height: 90vh;
  overflow: hidden; display: flex; flex-direction: column;
}
.modal-header {
  padding: var(--s-5) var(--s-5) var(--s-4); border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
}
.modal-title { font-size: 16px; font-weight: 600; }
.modal-close {
  width: 28px; height: 28px; border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center; color: var(--text-muted);
}
.modal-close:hover { background: var(--bg-hover); color: var(--text); }
.modal-close svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.modal-body { padding: var(--s-5); overflow-y: auto; flex: 1; }
.modal-footer {
  padding: var(--s-4) var(--s-5); border-top: 1px solid var(--border);
  display: flex; justify-content: flex-end; gap: var(--s-2); background: var(--bg-subtle);
}
</style>
