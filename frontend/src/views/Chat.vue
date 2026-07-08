<template>
  <div class="chat-page">
    <!-- 左侧最近对话栏 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <h3 class="sidebar-title">最近对话</h3>
        <button class="new-chat-btn" @click="newChat" title="新建对话">
          <el-icon><Plus /></el-icon>
        </button>
      </div>
      <div class="session-list">
        <div v-if="sessionList.length === 0" class="session-empty">暂无对话记录</div>
        <div
          v-for="s in sessionList" :key="s.id"
          class="session-item"
          :class="{ active: s.id === chatForm.sessionId }"
          @click="loadSession(s)"
        >
          <el-icon class="session-icon"><ChatDotRound /></el-icon>
          <div class="session-info">
            <span class="session-title">{{ s.title }}</span>
            <span class="session-meta">{{ s.messageCount }} 条消息</span>
          </div>
          <el-icon class="session-delete" @click.stop="removeSession(s)"><Close /></el-icon>
        </div>
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
              <span v-if="msg.meta.intent" class="meta-item meta-intent" :class="msg.meta.intent">
                <el-icon><Aim /></el-icon> {{ intentLabel(msg.meta.intent) }}
              </span>
              <span v-if="msg.meta.modelName" class="meta-item">
                <el-icon><Cpu /></el-icon> {{ msg.meta.modelName }}
              </span>
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
          <!-- 已引用的文件列表 -->
          <div v-if="chatForm.attachments.length > 0" class="attachment-chips">
            <div v-for="(file, i) in chatForm.attachments" :key="i" class="attachment-chip">
              <el-icon><Document /></el-icon>
              <span class="attachment-name">{{ file.name }}</span>
              <span class="attachment-size">{{ formatFileSize(file.size) }}</span>
              <el-icon class="attachment-remove" @click="removeAttachment(i)"><Close /></el-icon>
            </div>
          </div>

          <!-- 输入框（上方） -->
          <el-input
            v-model="inputText" type="textarea"
            :autosize="{ minRows: 2, maxRows: 10 }"
            placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
            resize="none" @keydown.enter.exact.prevent="onEnter" :disabled="streaming"
          />

          <!-- 底部工具栏 -->
          <div class="input-bottom-bar">
            <!-- 左侧：工具按钮组 -->
            <div class="bottom-bar-tools">
              <!-- 引用文件 -->
              <el-tooltip content="引用文件" placement="top">
                <button class="tool-icon-btn" :class="{ active: chatForm.attachments.length > 0 }" @click="triggerFileUpload">
                  <el-icon><Paperclip /></el-icon>
                  <span v-if="chatForm.attachments.length > 0" class="icon-badge">{{ chatForm.attachments.length }}</span>
                </button>
              </el-tooltip>
              <input ref="fileInputRef" type="file" multiple style="display:none" @change="onFileSelected" />

              <!-- 工作模式 -->
              <el-popover v-model:visible="modePopoverVisible" trigger="click" placement="top-start" :width="160" popper-class="chat-tool-popover">
                <template #reference>
                  <button class="tool-icon-btn" :class="{ active: chatForm.mode }" title="工作模式">
                    <el-icon><component :is="currentWorkModeIcon" /></el-icon>
                  </button>
                </template>
                <div class="tool-popover-list">
                  <div
                    v-for="mode in modeList" :key="mode.value"
                    class="tool-popover-item mode-item"
                    :class="{ active: chatForm.mode === mode.value }"
                    @click="selectWorkMode(mode.value)"
                  >
                    <el-icon class="mode-item-icon"><component :is="modeIconMap[mode.value]" /></el-icon>
                    <span class="tool-item-name">{{ mode.label }}</span>
                    <el-icon v-if="chatForm.mode === mode.value" class="tool-item-check"><Select /></el-icon>
                  </div>
                </div>
              </el-popover>

              <!-- 知识库选择（仅 ask 模式） -->
              <el-popover v-if="chatForm.mode === 'ask'" v-model:visible="kbPopoverVisible" trigger="click" placement="top-start" :width="220" popper-class="chat-tool-popover">
                <template #reference>
                  <button class="tool-icon-btn" :class="{ active: chatForm.kbId }" title="选择知识库">
                    <el-icon><Notebook /></el-icon>
                    <span v-if="currentKbName" class="model-name-label">{{ currentKbName }}</span>
                    <span v-else class="model-name-label">全局</span>
                  </button>
                </template>
                <div class="tool-popover-list">
                  <div
                    class="tool-popover-item"
                    :class="{ active: !chatForm.kbId }"
                    @click="selectKb('')"
                  >
                    <el-icon><Notebook /></el-icon>
                    <span class="tool-item-name">全局检索</span>
                    <el-icon v-if="!chatForm.kbId" class="tool-item-check"><Select /></el-icon>
                  </div>
                  <div
                    v-for="kb in kbList" :key="kb.id"
                    class="tool-popover-item"
                    :class="{ active: kb.id === chatForm.kbId }"
                    @click="selectKb(kb.id)"
                  >
                    <el-icon><Notebook /></el-icon>
                    <span class="tool-item-name">{{ kb.name }}</span>
                    <el-icon v-if="kb.id === chatForm.kbId" class="tool-item-check"><Select /></el-icon>
                  </div>
                </div>
              </el-popover>

              <!-- 模型选择 -->
              <el-popover v-model:visible="modelPopoverVisible" trigger="click" placement="top-start" :width="200" popper-class="chat-tool-popover">
                <template #reference>
                  <button class="tool-icon-btn" :class="{ active: chatForm.modelId }" title="选择模型">
                    <el-icon><Cpu /></el-icon>
                    <span v-if="currentModel" class="model-name-label">{{ currentModel.modelName }}</span>
                  </button>
                </template>
                <div class="tool-popover-list">
                  <div
                    v-for="m in modelList" :key="m.id"
                    class="tool-popover-item"
                    :class="{ active: m.id === chatForm.modelId }"
                    @click="selectModel(m.id)"
                  >
                    <span class="tool-item-name">{{ m.modelName }}</span>
                    <el-icon v-if="m.id === chatForm.modelId" class="tool-item-check"><Select /></el-icon>
                  </div>
                </div>
              </el-popover>
            </div>

            <!-- 右侧：操作按钮 -->
            <div class="bottom-bar-actions">
              <!-- 思考模式 -->
              <el-tooltip :content="chatForm.thinking ? '深度思考（已开启）' : '深度思考'" placement="top">
                <button
                  class="tool-icon-btn"
                  :class="{ active: chatForm.thinking }"
                  @click="chatForm.thinking = !chatForm.thinking"
                >
                  <svg class="thinking-icon" viewBox="0 0 24 24" width="17" height="17"
                    :fill="chatForm.thinking ? 'currentColor' : 'none'"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                  >
                    <path d="M9 18h6" />
                    <path d="M10 22h4" />
                    <path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14" />
                  </svg>
                </button>
              </el-tooltip>

              <!-- 提示词增强 -->
              <el-tooltip content="优化提示词" placement="top">
                <button
                  class="tool-icon-btn"
                  :class="{ spinning: rewriting }"
                  :disabled="!inputText.trim() || streaming"
                  @click="rewriteInput"
                >
                  <el-icon><MagicStick /></el-icon>
                </button>
              </el-tooltip>

              <!-- 发送 -->
              <button v-if="streaming" class="send-btn stop" @click="stopGeneration">
                <el-icon><VideoPause /></el-icon>
              </button>
              <button v-else class="send-btn" :disabled="!inputText.trim()" @click="onSend">
                <el-icon><Promotion /></el-icon>
              </button>
            </div>
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Refresh, Timer, MagicStick, Promotion, EditPen,
  Document, Reading, CopyDocument, RefreshRight, VideoPause, Aim,
  Cpu, ChatDotRound, Notebook, Select,
  Paperclip, ChatDotSquare, Operation, Close, Plus
} from '@element-plus/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import { chatStream, rewriteQuery, getChatConfig, listSessions, deleteSession, getSessionMessages } from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledgeBase'
import { getPromptTemplateByKbId, getDefaultPromptTemplate } from '@/api/promptTemplate'

const messageListRef = ref(null)
const inputText = ref('')
const streaming = ref(false)
const rewriting = ref(false)
const kbList = ref([])
const modelList = ref([])
const modeList = ref([])
const modePopoverVisible = ref(false)
const modelPopoverVisible = ref(false)
const kbPopoverVisible = ref(false)
const sessionList = ref([])
/** 模式图标映射 */
const modeIconMap = { ask: ChatDotSquare, agent: Operation }
const currentTemplate = ref(null)
const abortController = ref(null)

const chatForm = reactive({
  kbId: '', sessionId: '', mode: 'ask', modelId: null,
  thinking: false, attachments: []
})

const fileInputRef = ref(null)

const messages = ref([])

/** 当前选中的模型信息（用于工具栏模型按钮展示） */
const currentModel = computed(() => {
  if (!chatForm.modelId) return null
  return modelList.value.find(m => m.id === chatForm.modelId) || null
})

/** 当前选中的知识库名称（用于工具栏展示） */
const currentKbName = computed(() => {
  if (!chatForm.kbId) return ''
  const kb = kbList.value.find(k => k.id === chatForm.kbId)
  return kb?.name || ''
})

/** 当前工作模式图标 */
const currentWorkModeIcon = computed(() => {
  return modeIconMap[chatForm.mode] || ChatDotSquare
})

/** 将前端工作模式映射为后端 mode */
function getBackendMode() {
  if (chatForm.mode === 'agent') return 'auto'
  if (chatForm.kbId) return 'rag'
  return 'auto'
}

/** 选择工作模式 */
function selectWorkMode(mode) {
  chatForm.mode = mode
  if (mode === 'agent') {
    chatForm.kbId = ''
    currentTemplate.value = null
    modePopoverVisible.value = false
  }
}

/** 选择知识库 */
function selectKb(kbId) {
  chatForm.kbId = kbId
  onKbChange(kbId)
  kbPopoverVisible.value = false
}

/** 选择模型 */
function selectModel(modelId) {
  chatForm.modelId = modelId
  onModelChange()
  modelPopoverVisible.value = false
}

/** 触发文件上传 */
function triggerFileUpload() {
  fileInputRef.value?.click()
}

/** 文件选择回调 */
function onFileSelected(e) {
  const files = Array.from(e.target.files || [])
  files.forEach(f => {
    chatForm.attachments.push({ name: f.name, size: f.size, file: f })
  })
  e.target.value = ''
}

/** 移除附件 */
function removeAttachment(idx) {
  chatForm.attachments.splice(idx, 1)
}

/** 格式化文件大小 */
function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

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

  // 记录当前选择的模型名称
  if (currentModel.value) {
    aiMsg.meta = { ...(aiMsg.meta || {}), modelName: currentModel.value.name }
  }

  abortController.value = chatStream(
    { ...chatForm, mode: getBackendMode(), query, sessionId: chatForm.sessionId || undefined },
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
        } else if (type === 'intent') {
          aiMsg.meta = { ...(aiMsg.meta || {}), intent: data }
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
        loadSessionList()
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
    const rewritten = res.data?.rewrittenQuery
    if (rewritten && rewritten.trim() && rewritten.trim() !== inputText.value.trim()) {
      inputText.value = rewritten.trim()
      ElMessage.success('提示词已优化')
    } else {
      ElMessage.info('当前提示词已足够清晰，无需优化')
    }
  } catch (e) {
    ElMessage.error('提示词优化失败：' + (e.message || '未知错误'))
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

/** 模型切换 */
function onModelChange() {
  // 下次发送消息时自动携带新 modelId
}

/** 意图标签文案 */
function intentLabel(intent) {
  const labels = { chitchat: '闲聊', rag: '知识库问答', search: '联网搜索', tool: '工具调用' }
  return labels[intent] || intent
}

function clearChat() {
  messages.value = []
  chatForm.sessionId = ''
  Object.keys(citationLookup).forEach(k => delete citationLookup[k])
}

/** 新建对话 */
function newChat() {
  clearChat()
}

/** 加载会话列表 */
async function loadSessionList() {
  try {
    const res = await listSessions({ pageNum: 1, pageSize: 50 })
    sessionList.value = res.data || []
  } catch { /* ignore */ }
}

/** 点击会话项，加载历史消息 */
async function loadSession(session) {
  if (streaming.value) return
  clearChat()
  chatForm.sessionId = session.id
  // 恢复该会话的知识库选择
  chatForm.kbId = session.kbId || ''
  if (session.kbId) {
    onKbChange(session.kbId)
  } else {
    currentTemplate.value = null
  }
  try {
    const res = await getSessionMessages(session.id)
    const logs = res.data || []
    logs.forEach(log => {
      messages.value.push({ role: 'user', content: log.query })
      messages.value.push({
        role: 'assistant', content: log.answer || '', loading: false,
        citations: [], meta: log.rewrittenQuery ? { rewrittenQuery: log.rewrittenQuery } : null
      })
    })
    scrollToBottom()
  } catch { /* ignore */ }
}

/** 删除会话 */
async function removeSession(session) {
  try {
    await ElMessageBox.confirm(
      `确定删除对话「${session.title}」吗？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await deleteSession(session.id)
    sessionList.value = sessionList.value.filter(s => s.id !== session.id)
    if (chatForm.sessionId === session.id) {
      clearChat()
    }
    ElMessage.success('已删除')
  } catch { /* ignore */ }
}

async function loadKbList() {
  try {
    const res = await listKnowledgeBases({ pageNum: 1, pageSize: 1000, status: 'active' })
    kbList.value = res.data?.list || []
  } catch { /* ignore */ }
}

async function loadChatConfig() {
  try {
    const res = await getChatConfig()
    const cfg = res.data || {}
    modeList.value = cfg.modes || []
    modelList.value = cfg.models || []
    if (cfg.defaultModelId) {
      chatForm.modelId = cfg.defaultModelId
    }
  } catch { /* ignore */ }
}

onMounted(() => {
  loadKbList()
  loadChatConfig()
  loadSessionList()
})
</script>

<style scoped>
.chat-page { display: flex; height: 100%; overflow: hidden; }

/* ==================== 侧边会话栏 ==================== */
.chat-sidebar {
  width: 280px; background: var(--bg-card); border-right: 1px solid var(--border-light);
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 20px 12px;
}
.sidebar-title { font-size: 14px; font-weight: 700; color: var(--text-primary); margin: 0; }
.new-chat-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 8px;
  color: var(--text-secondary); background: transparent;
  border: none; cursor: pointer; transition: all 0.2s;
}
.new-chat-btn:hover { color: var(--primary); background: var(--primary-gradient-soft); }
.session-list { flex: 1; overflow-y: auto; padding: 0 12px 12px; }
.session-empty {
  text-align: center; font-size: 13px; color: var(--text-placeholder);
  padding: 40px 0;
}
.session-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: var(--radius-sm);
  cursor: pointer; transition: all 0.15s; margin-bottom: 2px;
}
.session-item:hover { background: var(--bg-hover); }
.session-item.active { background: var(--primary-gradient-soft); }
.session-icon { font-size: 16px; color: var(--text-secondary); flex-shrink: 0; }
.session-item.active .session-icon { color: var(--primary); }
.session-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.session-title {
  font-size: 13px; font-weight: 500; color: var(--text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.session-item.active .session-title { color: var(--primary); }
.session-meta { font-size: 11px; color: var(--text-placeholder); }
.session-delete {
  font-size: 14px; color: var(--text-placeholder); flex-shrink: 0;
  opacity: 0; transition: all 0.15s; cursor: pointer;
}
.session-item:hover .session-delete { opacity: 1; }
.session-delete:hover { color: var(--danger); }

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
.meta-intent { font-weight: 600; }
.meta-intent.chitchat { color: #f59e0b; }
.meta-intent.rag { color: var(--primary); }

/* ==================== 输入区 ==================== */
.input-area {
  padding: 12px 32px 20px; background: var(--bg-page);
  border-top: 1px solid var(--border-light);
}
.input-wrapper {
  max-width: 900px; margin: 0 auto; background: var(--bg-card);
  border: 1px solid var(--border-base); border-radius: var(--radius-lg);
  padding: 14px 16px 10px; box-shadow: var(--shadow-md);
  transition: border-color 0.2s;
}
.input-wrapper:focus-within { border-color: var(--primary-light); }
.input-wrapper :deep(.el-textarea__inner) {
  border: none; box-shadow: none; background: transparent;
  padding: 0; font-size: 14px; line-height: 1.6;
}
.input-wrapper :deep(.el-textarea__inner:focus) { box-shadow: none; }

/* ==================== 附件标签 ==================== */
.attachment-chips {
  display: flex; flex-wrap: wrap; gap: 6px;
  margin-bottom: 8px;
}
.attachment-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px; border-radius: 6px;
  background: var(--bg-hover); font-size: 12px;
  color: var(--text-regular); max-width: 220px;
}
.attachment-chip .el-icon { font-size: 13px; color: var(--text-secondary); flex-shrink: 0; }
.attachment-name {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.attachment-size { font-size: 11px; color: var(--text-placeholder); flex-shrink: 0; }
.attachment-remove {
  cursor: pointer; color: var(--text-placeholder);
  transition: color 0.15s; flex-shrink: 0;
}
.attachment-remove:hover { color: var(--danger); }

/* ==================== 底部工具栏 ==================== */
.input-bottom-bar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; margin-top: 8px;
}
.bottom-bar-tools {
  display: flex; align-items: center; gap: 2px;
}
.bottom-bar-actions {
  display: flex; align-items: center; gap: 2px; flex-shrink: 0;
}

/* --- 通用图标按钮 --- */
.tool-icon-btn {
  position: relative;
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 32px; height: 32px; padding: 0 6px; border-radius: 8px;
  color: var(--text-secondary); background: transparent;
  border: none; cursor: pointer; transition: all 0.2s;
}
.tool-icon-btn:hover:not(:disabled) {
  color: var(--text-primary); background: var(--bg-hover);
}
.tool-icon-btn.active {
  color: var(--primary); background: var(--primary-gradient-soft);
}
.tool-icon-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.tool-icon-btn .el-icon { font-size: 17px; }
.model-name-label {
  font-size: 12px; font-weight: 500; margin-left: 2px;
  white-space: nowrap;
}
.tool-icon-btn.spinning .el-icon { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 图标上的小徽章（附件数量） */
.icon-badge {
  position: absolute; top: -2px; right: -2px;
  min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: 8px; font-size: 10px; font-weight: 600;
  color: #fff; background: var(--primary);
  display: flex; align-items: center; justify-content: center;
  line-height: 1;
}

/* 图标上的小圆点（已配置知识库提示） */
.icon-dot {
  position: absolute; bottom: 2px; right: 2px;
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--primary);
  border: 2px solid var(--bg-card);
}

/* --- 发送按钮（圆形渐变） --- */
.send-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border-radius: 50%;
  color: #fff; background: var(--primary-gradient);
  border: none; cursor: pointer; transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  margin-left: 4px;
}
.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
}
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; box-shadow: none; }
.send-btn .el-icon { font-size: 16px; }
.send-btn.stop {
  background: var(--danger);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}
.send-btn.stop:hover { box-shadow: 0 4px 14px rgba(239, 68, 68, 0.4); }

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

<style>
/* Popover 内容样式（全局，因为 popover 被 teleport 到 body） */
.chat-tool-popover.el-popover.el-popper {
  padding: 6px !important;
}
.tool-popover-list {
  display: flex; flex-direction: column; gap: 2px;
}
.tool-popover-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border-radius: 8px; cursor: pointer;
  transition: background 0.15s;
}
.tool-popover-item:hover { background: #f1f5f9; }
.tool-popover-item.active { background: var(--primary-gradient-soft); }
.tool-item-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.tool-popover-item.active .tool-item-name { color: var(--primary); }
.tool-item-check { color: var(--primary); font-size: 16px; margin-left: auto; }

/* 工作模式弹窗 */
.mode-item-icon { font-size: 17px; color: var(--text-secondary); flex-shrink: 0; }
.mode-item.active .mode-item-icon { color: var(--primary); }
</style>
