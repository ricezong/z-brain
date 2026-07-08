<template>
  <div class="chat-page">
    <!-- 左侧会话设置栏 -->
    <div class="chat-sidebar">
      <div class="sidebar-section">
        <h3 class="sidebar-title">问答设置</h3>
        <div class="setting-item">
          <label class="setting-label">知识库</label>
          <el-select v-model="chatForm.kbId" placeholder="全部知识库" filterable clearable style="width: 100%" @change="onKbChange">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </div>
        <div class="setting-item" v-if="currentTemplate">
          <label class="setting-label">当前提示词</label>
          <div class="template-info"><el-icon><EditPen /></el-icon><span>{{ currentTemplate.name }}</span></div>
        </div>
      </div>
      <div class="sidebar-footer-section">
        <el-button type="danger" plain round style="width: 100%" @click="clearChat">
          <el-icon><Delete /></el-icon> 清空对话
        </el-button>
      </div>
    </div>

    <!-- 右侧对话区 -->
    <div class="chat-main">
      <div class="message-list" ref="messageListRef">
        <!-- 欢迎消息 -->
        <div v-if="messages.length === 0" class="welcome-screen">
          <div class="welcome-icon">
            <svg viewBox="0 0 48 48" width="64" height="64">
              <defs><linearGradient id="welcomeG" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#6366f1"/><stop offset="100%" stop-color="#8b5cf6"/></linearGradient></defs>
              <rect width="48" height="48" rx="14" fill="url(#welcomeG)"/>
              <path d="M16 14c0-1.1.9-2 2-2h8a8 8 0 0 1 0 16h-4v6a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V14z" fill="white" opacity="0.95"/>
              <circle cx="24" cy="20" r="3" fill="#6366f1"/>
            </svg>
          </div>
          <h2 class="welcome-title">Z-Brain 智能问答</h2>
          <p class="welcome-desc">基于知识库的 RAG 智能问答，支持多路召回与引用溯源</p>
          <div class="welcome-suggestions">
            <div v-for="s in suggestions" :key="s" class="suggestion-chip" @click="sendMessage(s)">{{ s }}</div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div v-for="(msg, idx) in messages" :key="idx" class="message-row" :class="msg.role">
          <div class="message-avatar" :class="msg.role">
            <el-icon v-if="msg.role === 'user'"><User /></el-icon>
            <svg v-else viewBox="0 0 48 48" width="28" height="28">
              <defs><linearGradient :id="'aiG'+idx" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#6366f1"/><stop offset="100%" stop-color="#8b5cf6"/></linearGradient></defs>
              <rect width="48" height="48" rx="14" :fill="`url(#aiG${idx})`"/>
              <path d="M16 14c0-1.1.9-2 2-2h8a8 8 0 0 1 0 16h-4v6a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V14z" fill="white" opacity="0.95"/>
              <circle cx="24" cy="20" r="3" fill="#6366f1"/>
            </svg>
          </div>
          <div class="message-body">
            <div class="message-bubble" :class="msg.role">
              <div v-if="msg.role === 'user'" class="bubble-text">{{ msg.content }}</div>
              <!-- AI 回答：渲染 markdown，[doc_N] 变为可点击徽章 -->
              <template v-else>
                <MarkdownView
                  class="bubble-markdown"
                  :content="msg.content"
                  :citations="msg.citations"
                  :is-streaming="msg.loading"
                  @rendered="scrollToBottom"
                  @click="handleBubbleClick($event, msg.citations)"
                />
                <span v-if="msg.loading && msg.content" class="streaming-cursor"></span>
              </template>
              <div v-if="msg.loading && !msg.content" class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>

            <!-- 消息操作栏 -->
            <div v-if="msg.role === 'assistant' && !msg.loading && msg.content" class="message-actions">
              <button class="action-btn" @click="copyMessage(msg)">
                <el-icon><CopyDocument /></el-icon>
                <span>复制</span>
              </button>
              <button class="action-btn" @click="regenerate(idx)">
                <el-icon><RefreshRight /></el-icon>
                <span>重新生成</span>
              </button>
            </div>

            <!-- 元信息 -->
            <div v-if="msg.meta" class="message-meta">
              <span v-if="msg.meta.rewrittenQuery" class="meta-item">
                <el-icon><Refresh /></el-icon> 改写: {{ msg.meta.rewrittenQuery }}
              </span>
              <span v-if="msg.meta.costTimeMs" class="meta-item">
                <el-icon><Timer /></el-icon> {{ msg.meta.costTimeMs }}ms
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText" type="textarea" :rows="2"
            :autosize="{ minRows: 1, maxRows: 6 }"
            placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
            resize="none" @keydown.enter.exact.prevent="onEnter" :disabled="streaming"
          />
          <div class="input-actions">
            <el-tooltip content="优化提示词" placement="top">
              <el-button circle :icon="MagicStick" :loading="rewriting" @click="rewriteInput" :disabled="!inputText.trim() || streaming" />
            </el-tooltip>
            <el-button v-if="streaming" type="danger" :icon="VideoPause" @click="stopGeneration" round>
              停止
            </el-button>
            <el-button v-else type="primary" :icon="Promotion" @click="onSend" :disabled="!inputText.trim()" round>
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 引用详情弹窗 -->
    <el-dialog
      v-model="citationDialogVisible"
      :title="activeCitation ? `引用详情 ${activeCitation.label}` : '引用详情'"
      width="620px"
      append-to-body
      destroy-on-close
      class="citation-dialog"
    >
      <div v-if="activeCitation" class="citation-detail">
        <div class="detail-header">
          <span class="detail-badge">{{ activeCitation.label }}</span>
          <span class="detail-doc-name">
            <el-icon><Document /></el-icon>
            {{ activeCitation.docName || '未知文档' }}
          </span>
          <span v-if="activeCitation.page" class="detail-page">第 {{ activeCitation.page }} 页</span>
        </div>
        <div class="detail-meta">
          <span class="detail-meta-item">分块 ID: {{ activeCitation.chunkId }}</span>
          <span class="detail-meta-item">文档 ID: {{ activeCitation.docId }}</span>
        </div>
        <div class="detail-content-section">
          <div class="detail-content-title">
            <el-icon><Reading /></el-icon> 引用原文
          </div>
          <div class="detail-content-body">{{ activeCitation.fullContent || activeCitation.snippet || '暂无内容' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="citationDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User, Refresh, Timer, MagicStick, Promotion, Delete, EditPen,
  Document, Reading, CopyDocument, RefreshRight, VideoPause
} from '@element-plus/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { chatStream, rewriteQuery } from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledgeBase'
import { getPromptTemplateByKbId, getDefaultPromptTemplate } from '@/api/promptTemplate'

const messageListRef = ref(null)
const inputText = ref('')
const streaming = ref(false)
const rewriting = ref(false)
const kbList = ref([])
const currentTemplate = ref(null)
const abortController = ref(null)

const chatForm = reactive({
  kbId: '', sessionId: ''
})

const messages = ref([])

/** 引用详情弹窗 */
const citationDialogVisible = ref(false)
const activeCitation = ref(null)

/** 引用 label -> citation 映射缓存（用于点击时查找） */
const citationLookup = reactive({})

const suggestions = [
  '这个知识库包含哪些内容？',
  '帮我总结核心知识点',
  '有哪些关键概念需要理解？'
]

/** 处理气泡内点击（事件委托）— 引用徽章弹出详情 / 代码块复制 */
function handleBubbleClick(event) {
  const target = event.target
  // 引用徽章点击
  if (target.classList?.contains('citation-ref')) {
    const label = target.getAttribute('data-citation')
    if (label) {
      const cite = citationLookup[label]
      if (cite) {
        activeCitation.value = cite
        citationDialogVisible.value = true
      }
    }
  }
  // 代码块复制按钮
  if (target.classList?.contains('code-copy-btn')) {
    const codeEl = target.closest('pre')?.querySelector('code')
    if (codeEl) {
      navigator.clipboard.writeText(codeEl.textContent).then(() => {
        target.textContent = '已复制'
        setTimeout(() => { target.textContent = '复制' }, 2000)
      }).catch(() => {})
    }
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function onEnter() {
  if (!streaming.value) onSend()
}

function onSend() {
  const text = inputText.value.trim()
  if (!text) return
  sendMessage(text)
}

/** 启动流式问答 */
function startStreaming(query, aiMsg) {
  streaming.value = true
  const startTime = Date.now()

  abortController.value = chatStream(
    { ...chatForm, query, sessionId: chatForm.sessionId || undefined },
    {
      onMessage: (msg) => {
        aiMsg.loading = false
        const { type, data } = msg
        if (type === 'session') {
          chatForm.sessionId = data
        } else if (type === 'content') {
          aiMsg.content += data
        } else if (type === 'citations') {
          aiMsg.citations = data || []
          if (data) {
            data.forEach(c => { citationLookup[c.label] = c })
          }
        } else if (type === 'rewritten_query') {
          aiMsg.meta = { ...(aiMsg.meta || {}), rewrittenQuery: data }
        } else if (type === 'hyde') {
          aiMsg.meta = { ...(aiMsg.meta || {}), hydeAnswer: data }
        } else if (type === 'retrieval') {
          aiMsg.meta = { ...(aiMsg.meta || {}), retrieval: data }
        }
      },
      onDone: (data) => {
        aiMsg.loading = false
        const costTimeMs = typeof data === 'object' ? data?.costTimeMs : (Date.now() - startTime)
        aiMsg.meta = { ...(aiMsg.meta || {}), costTimeMs: costTimeMs || (Date.now() - startTime) }
        streaming.value = false
      },
      onError: (err) => {
        aiMsg.loading = false
        aiMsg.content += '\n\n⚠️ 生成失败：' + (err.message || '未知错误')
        streaming.value = false
      }
    }
  )
}

function sendMessage(text) {
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''

  const aiMsg = reactive({
    role: 'assistant', content: '', loading: true,
    citations: [], meta: null
  })
  messages.value.push(aiMsg)
  scrollToBottom()

  startStreaming(text, aiMsg)
}

/** 停止生成 */
function stopGeneration() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  streaming.value = false
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg?.role === 'assistant' && lastMsg.loading) {
    lastMsg.loading = false
  }
}

/** 复制消息 */
function copyMessage(msg) {
  navigator.clipboard.writeText(msg.content).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

/** 重新生成 */
function regenerate(idx) {
  if (streaming.value) return
  let userQuery = ''
  for (let i = idx - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      userQuery = messages.value[i].content
      break
    }
  }
  if (!userQuery) return

  messages.value.splice(idx, 1)
  const aiMsg = reactive({
    role: 'assistant', content: '', loading: true,
    citations: [], meta: null
  })
  messages.value.push(aiMsg)
  scrollToBottom()
  startStreaming(userQuery, aiMsg)
}

async function rewriteInput() {
  if (!inputText.value.trim()) return
  rewriting.value = true
  try {
    const res = await rewriteQuery({ query: inputText.value, sessionId: chatForm.sessionId })
    inputText.value = res.data.rewrittenQuery
    ElMessage.success('提示词已优化')
  } finally { rewriting.value = false }
}

async function onKbChange(kbId) {
  currentTemplate.value = null
  if (!kbId) return
  try {
    const res = await getPromptTemplateByKbId(kbId)
    if (res.data) {
      currentTemplate.value = res.data
    } else {
      const defaultRes = await getDefaultPromptTemplate()
      currentTemplate.value = defaultRes.data
    }
  } catch { /* ignore */ }
}

function clearChat() {
  messages.value = []
  chatForm.sessionId = ''
  Object.keys(citationLookup).forEach(k => delete citationLookup[k])
}

async function loadKbList() {
  try {
    const res = await listKnowledgeBases({ pageNum: 1, pageSize: 1000, status: 'active' })
    kbList.value = res.data?.list || []
  } catch { /* ignore */ }
}

onMounted(() => { loadKbList() })
</script>

<style scoped>
.chat-page { display: flex; height: 100%; overflow: hidden; }

/* ==================== 侧边设置栏 ==================== */
.chat-sidebar {
  width: 280px; background: var(--bg-card); border-right: 1px solid var(--border-light);
  display: flex; flex-direction: column; padding: 20px; flex-shrink: 0;
}
.sidebar-section { flex: 1; }
.sidebar-title { font-size: 14px; font-weight: 700; color: var(--text-primary); margin-bottom: 20px; }
.setting-item { margin-bottom: 24px; }
.setting-label {
  display: block; font-size: 12px; font-weight: 600; color: var(--text-secondary);
  margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px;
}
.template-info {
  display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--primary);
  font-weight: 500; background: var(--primary-gradient-soft); padding: 10px 14px; border-radius: var(--radius-sm);
}
.sidebar-footer-section { padding-top: 16px; border-top: 1px solid var(--border-light); }

/* ==================== 对话主区 ==================== */
.chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.message-list { flex: 1; overflow-y: auto; padding: 32px; scroll-behavior: smooth; }

/* ==================== 欢迎屏 ==================== */
.welcome-screen { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; text-align: center; }
.welcome-icon { margin-bottom: 24px; }
.welcome-title {
  font-size: 28px; font-weight: 800;
  background: var(--primary-gradient); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
  margin-bottom: 12px;
}
.welcome-desc { font-size: 14px; color: var(--text-secondary); max-width: 440px; line-height: 1.7; margin-bottom: 32px; }
.welcome-suggestions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; max-width: 600px; }
.suggestion-chip {
  padding: 8px 18px; background: var(--bg-card); border: 1px solid var(--border-base);
  border-radius: 20px; font-size: 13px; color: var(--text-regular); cursor: pointer; transition: all 0.2s;
}
.suggestion-chip:hover { border-color: var(--primary); color: var(--primary); background: var(--primary-gradient-soft); }

/* ==================== 消息行 ==================== */
.message-row { display: flex; gap: 14px; margin-bottom: 28px; max-width: 900px; margin-left: auto; margin-right: auto; }
.message-row.user { flex-direction: row-reverse; }
.message-avatar {
  flex-shrink: 0; width: 40px; height: 40px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; font-size: 20px;
}
.message-avatar.user { background: linear-gradient(135deg, #06b6d4, #3b82f6); color: #fff; }
.message-avatar.assistant { overflow: hidden; }
.message-body { flex: 1; min-width: 0; }
.message-row.user .message-body { display: flex; flex-direction: column; align-items: flex-end; }
.message-bubble { border-radius: var(--radius-md); padding: 14px 18px; max-width: 100%; word-break: break-word; }
.message-bubble.user { background: var(--primary-gradient); color: #fff; }
.message-bubble.assistant { background: var(--bg-card); border: 1px solid var(--border-light); box-shadow: var(--shadow-sm); }
.bubble-text { font-size: 14px; line-height: 1.7; }

/* ==================== Markdown 渲染 ==================== */
.bubble-markdown { font-size: 14px; line-height: 1.8; color: var(--text-primary); }
.bubble-markdown :deep(h1), .bubble-markdown :deep(h2), .bubble-markdown :deep(h3) { font-weight: 700; margin: 16px 0 8px; }
.bubble-markdown :deep(h1) { font-size: 20px; }
.bubble-markdown :deep(h2) { font-size: 17px; }
.bubble-markdown :deep(h3) { font-size: 15px; }
.bubble-markdown :deep(p) { margin: 8px 0; }
.bubble-markdown :deep(ul), .bubble-markdown :deep(ol) { padding-left: 20px; margin: 8px 0; }
.bubble-markdown :deep(li) { margin: 4px 0; }
.bubble-markdown :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 13px; font-family: 'Fira Code', 'Consolas', monospace; }
.bubble-markdown :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 14px 18px; border-radius: 8px; overflow-x: auto; margin: 10px 0; }
.bubble-markdown :deep(pre code) { background: none; padding: 0; color: inherit; font-size: 13px; }
.bubble-markdown :deep(pre.hljs) { background: #0d1117; border: 1px solid #30363d; }
.bubble-markdown :deep(.hljs) { background: #0d1117; }

/* 代码块增强：语言标签 + 复制按钮 */
.bubble-markdown :deep(.code-block-wrapper) { padding: 0; }
.bubble-markdown :deep(.code-block-header) {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 14px; background: #161b22; border-bottom: 1px solid #30363d;
  border-radius: 8px 8px 0 0;
}
.bubble-markdown :deep(.code-lang) {
  font-size: 12px; color: #8b949e; font-family: 'Fira Code', 'Consolas', monospace;
}
.bubble-markdown :deep(.code-copy-btn) {
  font-size: 12px; color: #8b949e; background: transparent; border: 1px solid #30363d;
  border-radius: 4px; padding: 2px 10px; cursor: pointer; transition: all 0.2s;
}
.bubble-markdown :deep(.code-copy-btn:hover) {
  color: #e2e8f0; border-color: #8b949e; background: rgba(255,255,255,0.05);
}
.bubble-markdown :deep(.code-block-wrapper code) { display: block; padding: 14px 18px; overflow-x: auto; }
.bubble-markdown :deep(blockquote) { border-left: 3px solid var(--primary-lighter); padding-left: 14px; margin: 10px 0; color: var(--text-secondary); }
.bubble-markdown :deep(table) { border-collapse: collapse; width: 100%; margin: 10px 0; }
.bubble-markdown :deep(th), .bubble-markdown :deep(td) { border: 1px solid var(--border-base); padding: 8px 12px; text-align: left; font-size: 13px; }
.bubble-markdown :deep(th) { background: #f8fafc; font-weight: 600; }
.bubble-markdown :deep(strong) { font-weight: 700; }
.bubble-markdown :deep(hr) { border: none; border-top: 1px solid var(--border-light); margin: 16px 0; }
.bubble-markdown :deep(a) { color: var(--primary); text-decoration: none; }
.bubble-markdown :deep(a:hover) { text-decoration: underline; }

/* ==================== 正文内引用徽章 ==================== */
.bubble-markdown :deep(.citation-ref) {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  margin: 0 2px;
  font-size: 12px;
  font-weight: 600;
  font-family: 'Fira Code', 'Consolas', monospace;
  color: var(--primary);
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 10px;
  text-decoration: none !important;
  cursor: pointer;
  transition: all 0.2s;
  vertical-align: baseline;
  line-height: 1.5;
  position: relative;
}
.bubble-markdown :deep(.citation-ref::before) {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--primary);
  margin-right: 4px;
}
.bubble-markdown :deep(.citation-ref:hover) {
  background: var(--primary);
  color: #fff;
  border-color: var(--primary);
  transform: translateY(-1px);
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.35);
}
.bubble-markdown :deep(.citation-ref:hover::before) {
  background: #fff;
}

/* ==================== 流式光标 ==================== */
.streaming-cursor {
  display: inline-block;
  width: 8px;
  height: 18px;
  background: var(--primary);
  border-radius: 2px;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: blink-cursor 1s infinite step-end;
}
@keyframes blink-cursor {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ==================== 消息操作栏 ==================== */
.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s;
}
.message-row:hover .message-actions { opacity: 1; }
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--text-placeholder);
  background: transparent;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.action-btn:hover {
  color: var(--primary);
  background: var(--primary-gradient-soft);
}
.action-btn .el-icon { font-size: 14px; }

/* ==================== 任务列表 ==================== */
.bubble-markdown :deep(.task-list-item) {
  list-style: none;
}
.bubble-markdown :deep(.task-list-item input[type="checkbox"]) {
  margin-right: 6px;
  accent-color: var(--primary);
  cursor: default;
}

/* ==================== 打字指示器 ==================== */
.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator span {
  width: 8px; height: 8px; border-radius: 50%; background: var(--primary-lighter);
  animation: typing 1.4s infinite ease-in-out;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { transform: scale(0.8); opacity: 0.5; }
  30% { transform: scale(1.2); opacity: 1; }
}

/* ==================== 元信息 ==================== */
.message-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 8px; }
.meta-item { display: flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-placeholder); }

/* ==================== 输入区 ==================== */
.input-area { padding: 16px 32px 24px; background: var(--bg-page); border-top: 1px solid var(--border-light); }
.input-wrapper {
  max-width: 900px; margin: 0 auto; background: var(--bg-card);
  border: 1px solid var(--border-base); border-radius: var(--radius-lg);
  padding: 12px 16px; box-shadow: var(--shadow-md); transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: var(--primary-light); }
.input-wrapper :deep(.el-textarea__inner) {
  border: none; box-shadow: none; background: transparent; padding: 0; font-size: 14px; line-height: 1.6;
}
.input-wrapper :deep(.el-textarea__inner:focus) { box-shadow: none; }
.input-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-top: 8px; }

/* ==================== 引用详情弹窗 ==================== */
.citation-detail {
  display: flex; flex-direction: column; gap: 16px;
}
.detail-header {
  display: flex; align-items: center; gap: 10px;
  padding-bottom: 16px; border-bottom: 1px solid var(--border-light);
}
.detail-badge {
  background: var(--primary-gradient); color: #fff;
  padding: 4px 12px; border-radius: 6px; font-weight: 700; font-size: 13px;
  font-family: 'Fira Code', 'Consolas', monospace;
}
.detail-doc-name {
  display: flex; align-items: center; gap: 4px;
  font-size: 15px; font-weight: 600; color: var(--text-primary);
}
.detail-page {
  font-size: 12px; color: var(--text-secondary);
  background: #f1f5f9; padding: 2px 8px; border-radius: 4px;
}
.detail-meta {
  display: flex; flex-wrap: wrap; gap: 16px;
}
.detail-meta-item {
  font-size: 12px; color: var(--text-secondary);
}
.detail-content-section {
  display: flex; flex-direction: column; gap: 10px;
}
.detail-content-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--text-secondary);
  text-transform: uppercase; letter-spacing: 0.5px;
}
.detail-content-body {
  background: var(--bg-page); border: 1px solid var(--border-light);
  border-radius: var(--radius-sm); padding: 16px;
  font-size: 13px; line-height: 1.8; color: var(--text-regular);
  max-height: 360px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-word;
}
</style>
