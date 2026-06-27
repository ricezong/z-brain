<template>
  <div class="app-shell">
    <!-- Sidebar -->
    <aside class="app-sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark">智</div>
        <span class="brand-name">智识</span>
        <span class="brand-version">v1.0</span>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.view"
          class="nav-item"
          :class="{ active: isActiveNav(item.view) }"
          @click="item.action"
        >
          <svg viewBox="0 0 24 24" v-html="item.icon"></svg>
          {{ item.label }}
          <span v-if="item.count !== undefined" class="count">{{ item.count }}</span>
        </button>
      </nav>

      <!-- Sidebar list area -->
      <div class="sidebar-list" id="sidebarList">
        <template v-if="currentView === 'chat' || currentView === 'settings'">
          <div class="sidebar-section-label">
            <span>历史对话</span>
            <button class="add-btn" @click="newChat" aria-label="新建对话">
              <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
            </button>
          </div>
          <button
            v-for="chat in chatHistory"
            :key="chat.id"
            class="list-item"
            :class="{ active: chat.id === currentChatId }"
            @click="loadChat(chat)"
          >
            <span class="list-item-title">{{ chat.title }}</span>
            <span class="list-item-meta">
              <span v-if="chat.kbName" class="badge">{{ chat.kbName.slice(0, 4) }}</span>
              <span>{{ chat.updated }}</span>
            </span>
          </button>
        </template>
        <template v-else>
          <div class="sidebar-section-label">
            <span>知识库</span>
            <button class="add-btn" @click="$router.push('/knowledge-bases')" aria-label="新建知识库">
              <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
            </button>
          </div>
          <button
            v-for="kb in appStore.kbList"
            :key="kb.id"
            class="list-item"
            :class="{ active: String(kb.id) === route.params.kbId }"
            @click="$router.push(`/knowledge-bases/${kb.id}`)"
          >
            <span class="list-item-title">{{ kb.name }}</span>
            <span class="list-item-meta">
              <span>{{ kb.docCount ?? 0 }} 文档</span>
              <span class="dot"></span>
              <span>{{ kb.chunkCount ?? 0 }} 分块</span>
            </span>
          </button>
        </template>
      </div>

      <div class="sidebar-footer">
        <div class="user-avatar">李</div>
        <div class="user-info">
          <div class="user-name">用户</div>
          <div class="user-email">z-brain@local</div>
        </div>
      </div>
    </aside>

    <!-- Main -->
    <main class="app-main">
      <header class="topbar">
        <div class="topbar-breadcrumb">
          <template v-if="currentView === 'kb-detail'">
            <span class="crumb-link" @click="$router.push('/knowledge-bases')">知识库</span>
            <span class="sep">/</span>
            <span class="current">{{ currentKbName }}</span>
          </template>
          <template v-else>
            <span class="current">{{ viewTitle }}</span>
          </template>
        </div>
        <div class="topbar-actions">
          <template v-if="currentView === 'chat'">
            <button class="btn btn-secondary btn-sm" @click="newChat">
              <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
              新建对话
            </button>
          </template>
        </div>
      </header>

      <div class="content">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </main>

    <!-- Toast container -->
    <div class="toast-container" id="toastContainer"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import type { ChatSession } from '@/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const currentChatId = ref('default')
const chatHistory = ref<ChatSession[]>([
  { id: 'c1', title: '新对话', kbId: null, kbName: '', updated: '刚刚', preview: '' },
])

const currentView = computed(() => {
  if (route.name === 'KnowledgeBaseDetail') return 'kb-detail'
  if (route.name === 'KnowledgeBases') return 'kb-list'
  if (route.name === 'Settings') return 'settings'
  return 'chat'
})

const viewTitle = computed(() => {
  const map: Record<string, string> = {
    chat: '对话',
    'kb-list': '知识库',
    'kb-detail': '知识库',
    settings: '设置',
  }
  return map[currentView.value] || '对话'
})

const currentKbName = computed(() => {
  const kbId = route.params.kbId
  const kb = appStore.kbList.find(k => String(k.id) === kbId)
  return kb?.name || ''
})

const navItems = computed(() => [
  {
    view: 'chat',
    label: '对话',
    icon: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    count: chatHistory.value.length,
    action: () => router.push('/chat'),
  },
  {
    view: 'kb-list',
    label: '知识库',
    icon: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
    count: appStore.kbList.length,
    action: () => router.push('/knowledge-bases'),
  },
  {
    view: 'settings',
    label: '设置',
    icon: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    action: () => router.push('/settings'),
  },
])

function isActiveNav(view: string) {
  if (view === 'kb-list') return currentView.value === 'kb-list' || currentView.value === 'kb-detail'
  return currentView.value === view
}

function newChat() {
  currentChatId.value = 'new-' + Date.now()
  router.push('/chat')
}

function loadChat(chat: ChatSession) {
  currentChatId.value = chat.id
  router.push('/chat')
}

onMounted(() => {
  appStore.loadKbList()
})

// Toast utility
function showToast(message: string, type: string = 'default') {
  const container = document.getElementById('toastContainer')
  if (!container) return
  const toast = document.createElement('div')
  toast.className = 'toast ' + type
  toast.textContent = message
  container.appendChild(toast)
  setTimeout(() => {
    toast.style.opacity = '0'
    toast.style.transform = 'translateY(8px)'
    toast.style.transition = 'opacity 0.2s, transform 0.2s'
    setTimeout(() => toast.remove(), 220)
  }, 2400)
}

defineExpose({ showToast })
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ==================== Sidebar ==================== */
.app-sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
  background: var(--bg-subtle);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.sidebar-brand {
  height: var(--topbar-h);
  padding: 0 var(--s-5);
  display: flex;
  align-items: center;
  gap: var(--s-2);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.brand-mark {
  width: 28px;
  height: 28px;
  background: var(--primary);
  color: var(--primary-foreground);
  border-radius: var(--r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  letter-spacing: -0.02em;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.brand-version {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  background: var(--bg-muted);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  margin-left: auto;
}

.sidebar-nav {
  padding: var(--s-3);
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  padding: var(--s-2) var(--s-3);
  border-radius: var(--r-md);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  width: 100%;
  text-align: left;
  transition: background 0.12s, color 0.12s;
}

.nav-item:hover { background: var(--bg-hover); color: var(--text); }
.nav-item.active { background: var(--bg-active); color: var(--text); }

.nav-item svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  stroke: currentColor;
  stroke-width: 1.75;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.nav-item .count {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.sidebar-section-label {
  padding: var(--s-4) var(--s-5) var(--s-2);
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-section-label .add-btn {
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}
.sidebar-section-label .add-btn:hover { background: var(--bg-hover); color: var(--text); }
.sidebar-section-label .add-btn svg { width: 14px; height: 14px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--s-3) var(--s-3);
}

.list-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: var(--s-2) var(--s-3);
  border-radius: var(--r-md);
  color: var(--text-secondary);
  margin-bottom: 1px;
  transition: background 0.12s, color 0.12s;
}

.list-item:hover { background: var(--bg-hover); color: var(--text); }
.list-item.active { background: var(--bg-active); color: var(--text); }

.list-item-title {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.list-item-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: var(--s-2);
}

.list-item-meta .dot {
  width: 4px;
  height: 4px;
  background: var(--text-muted);
  border-radius: 50%;
  display: inline-block;
}

.list-item-meta .badge {
  background: var(--accent-soft);
  color: var(--accent);
  padding: 1px 6px;
  border-radius: var(--r-sm);
  font-size: 10px;
  font-weight: 500;
}

.sidebar-footer {
  padding: var(--s-3);
  border-top: 1px solid var(--border);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--s-2);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-info { flex: 1; min-width: 0; }
.user-name { font-size: 13px; font-weight: 500; }
.user-email { font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* ==================== Main ==================== */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
}

.topbar {
  height: var(--topbar-h);
  border-bottom: 1px solid var(--border);
  padding: 0 var(--s-5);
  display: flex;
  align-items: center;
  gap: var(--s-4);
  flex-shrink: 0;
  background: var(--bg);
}

.topbar-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  font-size: 13px;
  color: var(--text-secondary);
}
.topbar-breadcrumb .sep { color: var(--text-muted); }
.topbar-breadcrumb .current { color: var(--text); font-weight: 500; font-size: 15px; }
.topbar-breadcrumb .crumb-link { cursor: pointer; }
.topbar-breadcrumb .crumb-link:hover { color: var(--text); }

.topbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--s-2);
}

.content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  position: relative;
  background: var(--bg);
}

/* ==================== Buttons ==================== */
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

.btn-secondary { background: var(--bg); color: var(--text); border-color: var(--border); }
.btn-secondary:hover { background: var(--bg-hover); }

.btn-sm { padding: 5px 10px; font-size: 12px; }

/* ==================== Toast ==================== */
.toast-container {
  position: fixed;
  bottom: var(--s-5);
  right: var(--s-5);
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
  pointer-events: none;
}

.toast {
  background: var(--text);
  color: var(--text-inverse);
  padding: 10px 14px;
  border-radius: var(--r-md);
  font-size: 13px;
  box-shadow: var(--shadow-lg);
  pointer-events: auto;
  min-width: 240px;
  max-width: 360px;
  animation: toast-in 0.2s ease;
}
@keyframes toast-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.toast.success { background: var(--success); }
.toast.error { background: var(--danger); }
.toast.warning { background: var(--warning); }
</style>
