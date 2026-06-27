<template>
  <div class="kb-view">
    <div class="kb-container">
      <div class="kb-header">
        <div>
          <h2>知识库</h2>
          <p>管理文档资料，让 AI 回答更有依据</p>
        </div>
        <button class="btn btn-primary" @click="showCreateModal = true">
          <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
          新建知识库
        </button>
      </div>

      <!-- Grid -->
      <div v-if="appStore.kbList.length > 0" class="kb-grid">
        <div
          v-for="kb in appStore.kbList"
          :key="kb.id"
          class="kb-card"
          @click="$router.push(`/knowledge-bases/${kb.id}`)"
        >
          <div class="kb-card-head">
            <div class="kb-card-icon">
              <svg viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            </div>
            <button class="kb-card-menu" @click.stop="confirmDelete(kb)" aria-label="知识库操作">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/><circle cx="5" cy="12" r="1"/></svg>
            </button>
          </div>
          <div>
            <div class="kb-card-title">{{ kb.name }}</div>
            <div class="kb-card-desc">{{ kb.description || '暂无描述' }}</div>
          </div>
          <div class="kb-card-stats">
            <div class="kb-stat">
              <span class="kb-stat-value">{{ kb.docCount ?? 0 }}</span>
              <span class="kb-stat-label">文档</span>
            </div>
            <div class="kb-stat">
              <span class="kb-stat-value">{{ kb.chunkCount ?? 0 }}</span>
              <span class="kb-stat-label">分块</span>
            </div>
          </div>
          <div class="kb-card-footer">
            <span class="status-badge" :class="kb.status === 'active' ? 'ready' : 'empty'">
              <span class="dot"></span>
              {{ kb.status === 'active' ? '可用' : '空' }}
            </span>
            <span>{{ formatTime(kb.updateTime) }}</span>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-else class="kb-empty">
        <div class="kb-empty-icon">
          <svg viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        </div>
        <h3>还没有任何知识库</h3>
        <p>创建一个知识库，上传文档后即可在对话中引用。智识会自动解析、分块、向量化。</p>
        <button class="btn btn-primary" @click="showCreateModal = true">
          <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
          新建知识库
        </button>
      </div>
    </div>

    <!-- Create KB Modal -->
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal">
        <div class="modal-header">
          <h2 class="modal-title">新建知识库</h2>
          <button class="modal-close" @click="showCreateModal = false">
            <svg viewBox="0 0 24 24"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="modal-field">
            <label class="modal-label">名称<span class="required">*</span></label>
            <input class="modal-input" v-model="newKbName" placeholder="例如：产品文档库" />
            <div v-if="nameError" class="modal-error show">请输入知识库名称</div>
          </div>
          <div class="modal-field">
            <label class="modal-label">描述</label>
            <textarea class="modal-textarea" v-model="newKbDesc" placeholder="简述这个知识库的用途"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showCreateModal = false">取消</button>
          <button class="btn btn-primary" @click="createKb">创建知识库</button>
        </div>
      </div>
    </div>

    <!-- Confirm Delete Modal -->
    <div v-if="showDeleteModal" class="modal-overlay" @click.self="showDeleteModal = false">
      <div class="modal" style="max-width:400px;">
        <div class="modal-header">
          <h2 class="modal-title">确认删除</h2>
          <button class="modal-close" @click="showDeleteModal = false">
            <svg viewBox="0 0 24 24"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px;line-height:1.6;">
            删除知识库「{{ deleteTarget?.name }}」会同时删除所有文档与分块，且无法恢复。确定继续吗？
          </p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showDeleteModal = false">取消</button>
          <button class="btn btn-danger" @click="doDelete">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useAppStore } from '@/stores/app'
import { knowledgeBaseApi } from '@/api/knowledge-base'
import type { KnowledgeBase } from '@/types'

const appStore = useAppStore()
const showCreateModal = ref(false)
const showDeleteModal = ref(false)
const deleteTarget = ref<KnowledgeBase | null>(null)
const newKbName = ref('')
const newKbDesc = ref('')
const nameError = ref(false)

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 10)
}

async function createKb() {
  nameError.value = !newKbName.value.trim()
  if (nameError.value) return
  try {
    await knowledgeBaseApi.create({
      name: newKbName.value.trim(),
      description: newKbDesc.value.trim(),
    })
    showCreateModal.value = false
    newKbName.value = ''
    newKbDesc.value = ''
    await appStore.loadKbList()
  } catch (e) {
    console.error(e)
  }
}

function confirmDelete(kb: KnowledgeBase) {
  deleteTarget.value = kb
  showDeleteModal.value = true
}

async function doDelete() {
  if (!deleteTarget.value) return
  try {
    await knowledgeBaseApi.delete(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await appStore.loadKbList()
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  appStore.loadKbList()
})

onActivated(() => {
  appStore.loadKbList()
})
</script>

<style scoped>
.kb-view {
  height: 100%;
  overflow-y: auto;
  padding: var(--s-6);
}

.kb-container {
  max-width: 1100px;
  margin: 0 auto;
}

.kb-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--s-6);
}
.kb-header h2 { font-size: 22px; font-weight: 600; letter-spacing: -0.01em; margin-bottom: 4px; }
.kb-header p { font-size: 13px; color: var(--text-secondary); }

.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--s-2);
  padding: 8px 14px;
  border-radius: var(--r-md);
  font-size: 13px;
  font-weight: 500;
  transition: background 0.12s, border-color 0.12s, color 0.12s;
  border: 1px solid transparent;
}
.btn svg { width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.btn-primary { background: var(--primary); color: var(--primary-foreground); }
.btn-primary:hover { background: var(--primary-hover); }
.btn-ghost { background: transparent; color: var(--text-secondary); }
.btn-ghost:hover { background: var(--bg-hover); color: var(--text); }
.btn-danger { background: var(--danger); color: white; }
.btn-danger:hover { background: #B91C1C; }

/* KB Grid */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--s-4);
}

.kb-card {
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--bg);
  padding: var(--s-4);
  transition: border-color 0.12s, box-shadow 0.12s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
}
.kb-card:hover { border-color: var(--border-strong); box-shadow: var(--shadow-md); }

.kb-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--s-2); }

.kb-card-icon {
  width: 36px;
  height: 36px;
  background: var(--accent-soft);
  color: var(--accent);
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kb-card-icon svg { width: 18px; height: 18px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.kb-card-menu {
  color: var(--text-muted);
  width: 24px;
  height: 24px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kb-card-menu:hover { background: var(--bg-hover); color: var(--text); }
.kb-card-menu svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.kb-card-title { font-size: 15px; font-weight: 600; letter-spacing: -0.01em; margin-bottom: 2px; }
.kb-card-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-card-stats {
  display: flex;
  gap: var(--s-4);
  padding-top: var(--s-3);
  border-top: 1px solid var(--border);
}
.kb-stat { display: flex; flex-direction: column; gap: 2px; }
.kb-stat-value { font-size: 16px; font-weight: 600; color: var(--text); font-variant-numeric: tabular-nums; }
.kb-stat-label { font-size: 11px; color: var(--text-muted); }

.kb-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: var(--r-sm);
  font-size: 11px;
  font-weight: 500;
}
.status-badge.ready { background: var(--success-soft); color: var(--success); }
.status-badge.empty { background: var(--bg-muted); color: var(--text-muted); }
.status-badge .dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }

/* Empty state */
.kb-empty {
  text-align: center;
  padding: var(--s-7) var(--s-6);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-lg);
  background: var(--bg-subtle);
}
.kb-empty-icon {
  width: 48px; height: 48px;
  margin: 0 auto var(--s-4);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kb-empty-icon svg { width: 24px; height: 24px; stroke: currentColor; stroke-width: 1.5; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.kb-empty h3 { font-size: 16px; font-weight: 600; margin-bottom: var(--s-2); }
.kb-empty p { font-size: 13px; color: var(--text-secondary); margin-bottom: var(--s-5); max-width: 360px; margin-left: auto; margin-right: auto; line-height: 1.6; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(9, 9, 11, 0.4);
  z-index: 100;
  display: flex; align-items: center; justify-content: center;
  padding: var(--s-6);
}
.modal {
  background: var(--bg);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-pop);
  max-width: 480px; width: 100%;
  max-height: 90vh;
  overflow: hidden;
  display: flex; flex-direction: column;
}
.modal-header {
  padding: var(--s-5) var(--s-5) var(--s-4);
  border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
}
.modal-title { font-size: 16px; font-weight: 600; }
.modal-close {
  width: 28px; height: 28px;
  border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-muted);
}
.modal-close:hover { background: var(--bg-hover); color: var(--text); }
.modal-close svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }
.modal-body { padding: var(--s-5); overflow-y: auto; flex: 1; }
.modal-footer {
  padding: var(--s-4) var(--s-5);
  border-top: 1px solid var(--border);
  display: flex; justify-content: flex-end; gap: var(--s-2);
  background: var(--bg-subtle);
}
.modal-field { margin-bottom: var(--s-4); }
.modal-field:last-child { margin-bottom: 0; }
.modal-label { font-size: 12px; font-weight: 500; color: var(--text); margin-bottom: 6px; display: block; }
.modal-label .required { color: var(--danger); margin-left: 2px; }
.modal-input, .modal-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  font-size: 13px;
  background: var(--bg);
  transition: border-color 0.12s, box-shadow 0.12s;
  font-family: inherit;
}
.modal-input:focus, .modal-textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(24,24,27,0.06);
  outline: none;
}
.modal-textarea { min-height: 80px; resize: vertical; line-height: 1.5; }
.modal-error { font-size: 11px; color: var(--danger); margin-top: 4px; display: none; }
.modal-error.show { display: block; }
</style>
