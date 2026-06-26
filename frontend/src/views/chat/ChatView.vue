<template>
  <div class="chat-page">
    <!-- 左侧侧边栏 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <div class="header-icon">
          <el-icon size="20"><ChatDotRound /></el-icon>
        </div>
        <span class="header-title">智能问答</span>
      </div>

      <div class="kb-selector-area">
        <el-select v-model="selectedKbId" placeholder="选择知识库" style="width: 100%" @change="onKbChange">
          <template #prefix><el-icon><Collection /></el-icon></template>
          <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </div>

      <div class="chat-tips">
        <div class="tip-card">
          <div class="tip-icon">💡</div>
          <div class="tip-text">
            选择知识库后，在下方输入框提问。系统将通过 RAG 检索知识库内容并生成回答，回答中会标注引用来源。
          </div>
        </div>
      </div>

      <div class="chat-options">
        <div class="option-item">
          <span class="option-label">HyDE 增强</span>
          <el-switch v-model="enableHyde" size="small" />
        </div>
        <div class="option-item">
          <span class="option-label">Query 改写</span>
          <el-switch v-model="enableQueryRewrite" size="small" />
        </div>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div ref="messageListRef" class="message-list">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-icon">
            <svg viewBox="0 0 64 64" width="64" height="64">
              <defs>
                <linearGradient id="emptyGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" style="stop-color:#a5b4fc;stop-opacity:1" />
                  <stop offset="100%" style="stop-color:#6366f1;stop-opacity:1" />
                </linearGradient>
              </defs>
              <circle cx="32" cy="32" r="30" fill="url(#emptyGrad)" opacity="0.1"/>
              <path d="M32 16C23.16 16 16 23.16 16 32c0 3.04.85 5.88 2.32 8.32L16 48l7.68-2.32C26.12 47.15 28.96 48 32 48c8.84 0 16-7.16 16-16S40.84 16 32 16z" fill="url(#emptyGrad)"/>
            </svg>
          </div>
          <p class="empty-title">开始智能问答</p>
          <p class="empty-desc">选择知识库，输入您的问题</p>
        </div>

        <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.role">
          <div class="message-avatar">
            <el-avatar :size="36" :style="{ background: msg.role === 'user' ? 'linear-gradient(135deg, #6366f1, #818cf8)' : 'linear-gradient(135deg, #10b981, #34d399)' }">
              {{ msg.role === 'user' ? '我' : 'AI' }}
            </el-avatar>
          </div>
          <div class="message-body">
            <div class="message-content">
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
              <div v-else class="user-text">{{ msg.content }}</div>
              <div v-if="msg.loading" class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>

            <!-- 引用来源 -->
            <div v-if="msg.citations && msg.citations.length > 0" class="citations">
              <div class="citations-title">📎 引用来源</div>
              <div v-for="(cite, idx) in msg.citations" :key="idx" class="citation-item">
                <el-tag type="primary" effect="plain" round size="small">{{ cite.label }}</el-tag>
                <span class="citation-doc">{{ cite.docName }}</span>
                <span class="citation-snippet">{{ cite.snippet?.substring(0, 60) }}...</span>
              </div>
            </div>

            <!-- 元信息 -->
            <div v-if="msg.meta" class="meta-info">
              <span class="meta-tag" v-if="msg.meta.rewrittenQuery">改写: {{ msg.meta.rewrittenQuery }}</span>
              <span class="meta-tag" v-if="msg.meta.costTimeMs">耗时: {{ msg.meta.costTimeMs }}ms</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <!-- 功能按钮行 -->
        <div class="input-toolbar">
          <div class="toolbar-left">
            <el-tooltip content="Query 改写：将问题扩展为更适合检索的形式" placement="top">
              <el-button
                :type="enableQueryRewrite ? 'primary' : 'default'"
                size="small"
                :icon="MagicStick"
                @click="handleRewriteQuery"
                :loading="rewriting"
                round
                plain
              >
                改写
              </el-button>
            </el-tooltip>
            <el-tooltip content="HyDE 增强：生成假设性答案用于向量检索" placement="top">
              <el-button
                :type="enableHyde ? 'primary' : 'default'"
                size="small"
                :icon="Cpu"
                @click="enableHyde = !enableHyde"
                round
                plain
              >
                HyDE
              </el-button>
            </el-tooltip>
          </div>
          <div class="toolbar-right" v-if="rewrittenText">
            <el-tag type="success" effect="plain" closable @close="rewrittenText = ''">
              已改写：{{ rewrittenText.substring(0, 30) }}{{ rewrittenText.length > 30 ? '...' : '' }}
            </el-tag>
          </div>
        </div>

        <div class="input-box">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="2"
            placeholder="输入您的问题，按 Enter 发送..."
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
            :disabled="sending"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="sending"
            @click="handleSend"
            round
            class="send-btn"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ChatDotRound, Collection, Promotion, MagicStick, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { chatApi } from '@/api/chat'
import { useAppStore } from '@/stores/app'
import { storeToRefs } from 'pinia'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { generateId } from '@/utils/format'
import type { ChatRequest, Citation } from '@/types'

const appStore = useAppStore()
const { kbList, currentKb } = storeToRefs(appStore)

const selectedKbId = ref<number | undefined>()
const inputText = ref('')
const sending = ref(false)
const rewriting = ref(false)
const enableHyde = ref(true)
const enableQueryRewrite = ref(true)
const rewrittenText = ref('')
const messageListRef = ref<HTMLElement>()

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  citations?: Citation[]
  meta?: { rewrittenQuery?: string; costTimeMs?: number }
}

const messages = ref<Message[]>([])

function renderMarkdown(content: string): string {
  if (!content) return ''
  const html = marked.parse(content) as string
  return DOMPurify.sanitize(html)
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function onKbChange() {
  messages.value = []
  rewrittenText.value = ''
}

/** Query 改写 */
async function handleRewriteQuery() {
  const query = inputText.value.trim()
  if (!query) {
    ElMessage.warning('请先输入问题')
    return
  }

  rewriting.value = true
  try {
    const res = await chatApi.queryRewrite({ query })
    if (res.data) {
      rewrittenText.value = res.data.rewrittenQuery
      inputText.value = res.data.rewrittenQuery
      enableQueryRewrite.value = true
      ElMessage.success('Query 改写完成')
    }
  } catch (err: any) {
    ElMessage.error('Query 改写失败：' + (err.message || '未知错误'))
  } finally {
    rewriting.value = false
  }
}

async function handleSend() {
  const query = inputText.value.trim()
  if (!query) return
  if (!selectedKbId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (sending.value) return

  // 添加用户消息
  messages.value.push({ id: generateId(), role: 'user', content: query })
  inputText.value = ''
  rewrittenText.value = ''
  scrollToBottom()

  // 添加 AI 占位消息
  const aiMsg: Message = { id: generateId(), role: 'assistant', content: '', loading: true }
  messages.value.push(aiMsg)
  scrollToBottom()

  sending.value = true

  const requestData: ChatRequest = {
    kbId: selectedKbId.value,
    query,
    stream: true,
    enableHyde: enableHyde.value,
    enableQueryRewrite: enableQueryRewrite.value,
  }

  let fullContent = ''
  let citations: Citation[] = []
  let meta: Message['meta'] = {}

  chatApi.chatStream(
    requestData,
    (event: string, data: any) => {
      const msgIdx = messages.value.findIndex((m) => m.id === aiMsg.id)
      if (msgIdx < 0) return

      if (event === 'session' || event === 'message') {
        // session 事件
      } else if (event === 'rewritten_query') {
        meta.rewrittenQuery = typeof data === 'string' ? data : data?.data || data
      } else if (event === 'hyde') {
        // HyDE 答案
      } else if (event === 'retrieval') {
        // 检索结果
      } else if (event === 'content') {
        const chunk = typeof data === 'string' ? data : data?.data || data
        fullContent += chunk
        messages.value[msgIdx].content = fullContent
        messages.value[msgIdx].loading = false
        scrollToBottom()
      } else if (event === 'citations') {
        citations = typeof data === 'string' ? [] : data?.data || data || []
        messages.value[msgIdx].citations = citations
      } else if (event === 'done') {
        messages.value[msgIdx].loading = false
        if (typeof data === 'object' && data?.costTimeMs) {
          meta.costTimeMs = data.costTimeMs
        }
        messages.value[msgIdx].meta = meta
      } else if (event === 'error') {
        messages.value[msgIdx].loading = false
        messages.value[msgIdx].content = '抱歉，处理请求时出现错误。'
      }
    },
    (err: any) => {
      const msgIdx = messages.value.findIndex((m) => m.id === aiMsg.id)
      if (msgIdx >= 0) {
        messages.value[msgIdx].loading = false
        messages.value[msgIdx].content = '抱歉，连接服务器失败，请稍后重试。'
      }
      console.error(err)
    }
  )

  sending.value = false
}

onMounted(() => {
  selectedKbId.value = currentKb.value?.id
})
</script>

<style scoped lang="scss">
.chat-page {
  height: 100%;
  display: flex;
  overflow: hidden;
}

/* ==================== 左侧侧边栏 ==================== */
.chat-sidebar {
  width: 280px;
  background: var(--surface);
  border-right: 1px solid var(--border-light);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 10px;

  .header-icon {
    width: 36px;
    height: 36px;
    border-radius: var(--radius-md);
    background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: var(--shadow-primary);
  }

  .header-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.chat-tips {
  .tip-card {
    background: var(--primary-bg);
    border-radius: var(--radius-md);
    padding: 14px;
    display: flex;
    gap: 10px;

    .tip-icon {
      font-size: 18px;
      flex-shrink: 0;
    }

    .tip-text {
      font-size: 12px;
      color: var(--text-secondary);
      line-height: 1.6;
    }
  }
}

.chat-options {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);

  .option-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 0;

    .option-label {
      font-size: 13px;
      color: var(--text-secondary);
      font-weight: 500;
    }
  }
}

/* ==================== 右侧聊天区域 ==================== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;

  .empty-icon {
    margin-bottom: 12px;
    opacity: 0.8;
  }

  .empty-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .empty-desc {
    font-size: 14px;
    color: var(--text-tertiary);
  }
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeIn 0.3s ease-out;

  &.user {
    flex-direction: row-reverse;

    .message-content {
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
      color: #fff;
      border-radius: var(--radius-lg) var(--radius-lg) 4px var(--radius-lg);
    }

    .user-text {
      color: #fff;
    }
  }

  &.assistant {
    .message-content {
      background: var(--surface);
      border: 1px solid var(--border-light);
      border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) 4px;
    }
  }

  .message-body {
    max-width: 70%;
  }

  .message-content {
    padding: 14px 18px;
    font-size: 14px;
    line-height: 1.7;
    box-shadow: var(--shadow-xs);
  }
}

.markdown-body {
  :deep(p) { margin-bottom: 8px; }
  :deep(pre) {
    background: var(--bg);
    padding: 12px;
    border-radius: var(--radius-sm);
    overflow-x: auto;
    margin: 8px 0;
  }
  :deep(code) {
    background: rgba(99, 102, 241, 0.08);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 13px;
  }
  :deep(pre code) { background: none; padding: 0; }
}

.citations {
  margin-top: 12px;
  padding: 12px;
  background: var(--primary-bg);
  border-radius: var(--radius-md);
  border: 1px solid var(--primary-lighter);

  .citations-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 8px;
    color: var(--primary-dark);
  }

  .citation-item {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
    font-size: 13px;

    .citation-doc {
      font-weight: 500;
      color: var(--text-primary);
      white-space: nowrap;
    }

    .citation-snippet {
      color: var(--text-secondary);
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.meta-info {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  .meta-tag {
    font-size: 12px;
    color: var(--text-tertiary);
    background: var(--bg);
    padding: 2px 8px;
    border-radius: var(--radius-full);
  }
}

.typing-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px 0;

  span {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--primary-light);
    animation: typing 1.4s infinite ease-in-out;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ==================== 输入区域 ==================== */
.chat-input-area {
  border-top: 1px solid var(--border-light);
  padding: 12px 32px 16px;
  background: var(--surface);

  .input-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    gap: 8px;

    .toolbar-left {
      display: flex;
      gap: 8px;

      .el-button {
        height: 28px;
        font-size: 12px;
      }
    }

    .toolbar-right {
      flex-shrink: 0;

      .el-tag {
        font-size: 12px;
        max-width: 280px;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  .input-box {
    display: flex;
    gap: 12px;
    align-items: flex-end;

    .send-btn {
      height: 52px;
    }
  }
}
</style>
