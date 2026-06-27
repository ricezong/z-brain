<template>
  <div class="chat-view">
    <!-- Scroll area -->
    <div class="chat-scroll" ref="chatScrollRef">
      <!-- Empty state -->
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="chat-empty-icon">智</div>
        <h1>开始一次新的对话</h1>
        <p>向智识提问，或选择一个知识库让回答更有依据。下方是一些常见用法，点击即可开始。</p>
        <div class="chat-suggestions">
          <button
            v-for="(s, i) in suggestions"
            :key="i"
            class="suggestion-card"
            @click="sendSampleMessage(s.text)"
          >
            <span class="s-title">{{ s.title }}</span>
            <span class="s-desc">{{ s.desc }}</span>
            <span v-if="s.tag" class="s-tag">{{ s.tag }}</span>
          </button>
        </div>
      </div>

      <!-- Messages -->
      <div v-else class="chat-container">
        <div v-for="(msg, idx) in messages" :key="idx" class="msg" :class="msg.role">
          <div class="msg-avatar">{{ msg.role === 'user' ? '你' : '智' }}</div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="name">{{ msg.role === 'user' ? '你' : '智识' }}</span>
              <span>·</span>
              <span>{{ msg.time }}</span>
            </div>
            <div class="msg-content" v-html="renderContent(msg.content)"></div>
            <div v-if="msg.citations && msg.citations.length" class="citations">
              <div class="citations-label">引用来源</div>
              <div class="citation-list">
                <button
                  v-for="(c, ci) in msg.citations"
                  :key="ci"
                  class="citation-chip"
                >
                  <span class="num">{{ ci + 1 }}</span>
                  <span>{{ c.docName }}</span>
                </button>
              </div>
            </div>
            <div v-if="msg.role === 'ai'" class="msg-actions">
              <button class="msg-action" @click="copyText(msg.content)">
                <svg viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                复制
              </button>
            </div>
          </div>
        </div>
        <!-- Thinking indicator -->
        <div v-if="isThinking" class="msg ai">
          <div class="msg-avatar">智</div>
          <div class="msg-body">
            <div class="msg-meta">
              <span class="name">智识</span>
              <span>·</span>
              <span>正在思考</span>
            </div>
            <div class="msg-content">
              <div class="thinking-dots"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="chat-input-wrap">
      <div class="chat-input-container">
        <div class="chat-input-box">
          <div class="chat-input-toolbar">
            <button
              v-if="appStore.currentKb"
              class="toolbar-kb"
              @click="openKbPicker"
            >
              <svg viewBox="0 0 24 24"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
              <span>{{ appStore.currentKb.name }}</span>
              <span class="x" @click.stop="clearKb">
                <svg viewBox="0 0 24 24"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
              </span>
            </button>
            <button v-else class="toolbar-kb-empty" @click="openKbPicker">
              <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
              关联知识库
            </button>
            <div class="toolbar-spacer"></div>
            <span class="toolbar-mode">
              <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
              DeepSeek
            </span>
          </div>
          <div class="chat-input-area">
            <textarea
              ref="inputRef"
              class="chat-input"
              v-model="inputText"
              placeholder="输入问题，按 Enter 发送，Shift + Enter 换行"
              rows="1"
              @input="autoGrow"
              @keydown="handleKeydown"
            ></textarea>
            <button class="send-btn" :disabled="!inputText.trim() || isThinking" @click="sendMessage">
              <svg viewBox="0 0 24 24"><path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/></svg>
            </button>
          </div>
        </div>
        <div class="chat-input-hint">智识可能出错，请核验重要信息。回答基于知识库时将标注引用来源。</div>
      </div>
    </div>

    <!-- KB Picker Modal -->
    <div v-if="showKbPicker" class="modal-overlay" @click.self="showKbPicker = false">
      <div class="modal">
        <div class="modal-header">
          <h2 class="modal-title">关联知识库</h2>
          <button class="modal-close" @click="showKbPicker = false">
            <svg viewBox="0 0 24 24"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="modal-body">
          <button
            v-for="kb in appStore.kbList"
            :key="kb.id"
            class="list-item"
            style="padding: var(--s-3) var(--s-4); margin-bottom: 4px;"
            @click="pickKb(kb)"
          >
            <span class="list-item-title">{{ kb.name }}</span>
            <span class="list-item-meta">
              <span>{{ kb.docCount ?? 0 }} 文档</span>
              <span class="dot" style="width:4px;height:4px;background:var(--text-muted);border-radius:50%;display:inline-block;"></span>
              <span>{{ kb.chunkCount ?? 0 }} 分块</span>
            </span>
          </button>
          <div v-if="appStore.kbList.length === 0" style="text-align:center;padding:var(--s-5);color:var(--text-muted);font-size:13px;">
            暂无知识库，请先创建
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { chatApi } from '@/api/chat'
import type { KnowledgeBase, Citation } from '@/types'

interface ChatMessage {
  role: 'user' | 'ai'
  content: string
  time: string
  citations?: Citation[]
}

const appStore = useAppStore()
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const isThinking = ref(false)
const showKbPicker = ref(false)
const chatScrollRef = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()

const sessionId = ref('')

const suggestions = [
  { title: '总结文档要点', desc: '从已上传的文档中提取关键结论', tag: '知识库', text: '请总结知识库中的核心要点' },
  { title: '撰写 FAQ 初稿', desc: '基于已有文档生成可发布版本', tag: '知识库', text: '帮我写一段 FAQ，覆盖常见问题' },
  { title: '数据对比分析', desc: '从数据报告中提取并横向比较', tag: '知识库', text: '对比不同版本的数据差异' },
  { title: '日常写作辅助', desc: '无需知识库，自由提问', tag: '', text: '帮我写一段关于智能体协作的周报片段' },
]

function formatNow() {
  const d = new Date()
  return `今天 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function renderContent(content: string): string {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

function scrollToBottom() {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

function autoGrow() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function sendSampleMessage(text: string) {
  inputText.value = text
  sendMessage()
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isThinking.value) return

  messages.value.push({
    role: 'user',
    content: text,
    time: formatNow(),
  })
  inputText.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'
  scrollToBottom()

  isThinking.value = true

  try {
    const kbId = appStore.currentKb?.id || 0
    const res = await chatApi.chatSync({
      query: text,
      kbId,
      sessionId: sessionId.value || undefined,
      stream: false,
    })
    const data = res.data
    sessionId.value = data.sessionId

    messages.value.push({
      role: 'ai',
      content: data.answer,
      time: formatNow(),
      citations: data.citations || [],
    })
  } catch (e: any) {
    messages.value.push({
      role: 'ai',
      content: '抱歉，发生了错误：' + (e.message || '请稍后重试'),
      time: formatNow(),
    })
  } finally {
    isThinking.value = false
    scrollToBottom()
  }
}

function openKbPicker() {
  showKbPicker.value = true
}

function pickKb(kb: KnowledgeBase) {
  appStore.setCurrentKb(kb)
  showKbPicker.value = false
}

function clearKb() {
  appStore.setCurrentKb(null)
}

function copyText(text: string) {
  navigator.clipboard.writeText(text)
}

onMounted(() => {
  appStore.loadKbList()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--s-6) 0;
}

.chat-container {
  max-width: 760px;
  margin: 0 auto;
  padding: 0 var(--s-6);
}

/* Empty state */
.chat-empty {
  max-width: 560px;
  margin: 0 auto;
  padding: var(--s-7) var(--s-6);
  text-align: left;
}

.chat-empty-icon {
  width: 48px;
  height: 48px;
  background: var(--primary);
  color: var(--primary-foreground);
  border-radius: var(--r-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  margin-bottom: var(--s-5);
  letter-spacing: -0.02em;
}

.chat-empty h1 {
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.3;
  margin-bottom: var(--s-3);
}

.chat-empty p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: var(--s-6);
}

.chat-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--s-3);
}

.suggestion-card {
  text-align: left;
  padding: var(--s-4);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--bg);
  transition: border-color 0.12s, background 0.12s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.suggestion-card:hover { border-color: var(--border-strong); background: var(--bg-subtle); }
.suggestion-card .s-title { font-size: 13px; font-weight: 500; color: var(--text); }
.suggestion-card .s-desc { font-size: 12px; color: var(--text-muted); line-height: 1.5; }
.suggestion-card .s-tag {
  font-size: 10px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 1px 6px;
  border-radius: var(--r-sm);
  align-self: flex-start;
  margin-top: var(--s-2);
  font-weight: 500;
}

/* Messages */
.msg {
  margin-bottom: var(--s-6);
  display: flex;
  gap: var(--s-3);
}

.msg.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 13px;
}

.msg.user .msg-avatar { background: var(--primary); color: var(--primary-foreground); }
.msg.ai .msg-avatar { background: var(--accent-soft); color: var(--accent); border: 1px solid var(--accent); }

.msg-body { flex: 1; min-width: 0; }

.msg.user .msg-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: var(--s-2);
}
.msg-meta .name { color: var(--text); font-weight: 500; }

.msg.user .msg-meta { justify-content: flex-end; }

.msg-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}

.msg.user .msg-content {
  background: var(--primary);
  color: var(--primary-foreground);
  padding: 10px 14px;
  border-radius: 12px 12px 2px 12px;
  max-width: 80%;
  word-break: break-word;
}

.msg.ai .msg-content {
  max-width: 100%;
}

.citations {
  margin-top: var(--s-3);
  padding-top: var(--s-3);
  border-top: 1px dashed var(--border);
}
.citations-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-bottom: var(--s-2);
}
.citation-list { display: flex; flex-wrap: wrap; gap: var(--s-2); }
.citation-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--bg-muted);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  font-size: 12px;
  color: var(--text-secondary);
  transition: border-color 0.12s, background 0.12s;
}
.citation-chip:hover { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); cursor: pointer; }
.citation-chip .num {
  width: 18px;
  height: 18px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: var(--text);
}

/* Thinking dots */
.thinking-dots { display: inline-flex; gap: 4px; padding: var(--s-2) 0; }
.thinking-dots span {
  width: 6px;
  height: 6px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: dot-pulse 1.4s infinite ease-in-out;
}
.thinking-dots span:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-pulse {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-2px); }
}

/* Message actions */
.msg-actions {
  margin-top: var(--s-2);
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.12s;
}
.msg:hover .msg-actions { opacity: 1; }
.msg.user .msg-actions { justify-content: flex-end; }
.msg-action {
  padding: 4px 8px;
  font-size: 11px;
  color: var(--text-muted);
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  gap: 4px;
}
.msg-action:hover { background: var(--bg-hover); color: var(--text); }
.msg-action svg { width: 12px; height: 12px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }

/* Input area */
.chat-input-wrap {
  flex-shrink: 0;
  border-top: 1px solid var(--border);
  background: var(--bg);
  padding: var(--s-4) var(--s-6);
}

.chat-input-container {
  max-width: 760px;
  margin: 0 auto;
}

.chat-input-box {
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  background: var(--bg);
  transition: border-color 0.12s, box-shadow 0.12s;
}
.chat-input-box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(24,24,27,0.06);
}

.chat-input-toolbar {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  padding: var(--s-2) var(--s-3);
  border-bottom: 1px solid var(--border);
}

.toolbar-kb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--accent-soft);
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  font-size: 12px;
  color: var(--accent);
  font-weight: 500;
  transition: background 0.12s;
}
.toolbar-kb:hover { background: var(--accent); color: var(--bg); }
.toolbar-kb .x { width: 14px; height: 14px; opacity: 0.7; }
.toolbar-kb .x:hover { opacity: 1; }
.toolbar-kb svg, .toolbar-kb-empty svg { width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.toolbar-kb-empty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: var(--bg-muted);
  border-radius: var(--r-sm);
  font-size: 12px;
  color: var(--text-muted);
}
.toolbar-kb-empty:hover { background: var(--bg-active); color: var(--text-secondary); }

.toolbar-spacer { flex: 1; }

.toolbar-mode {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--text-secondary);
  border-radius: var(--r-sm);
}
.toolbar-mode svg { width: 14px; height: 14px; stroke: currentColor; stroke-width: 1.75; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.chat-input-area {
  display: flex;
  align-items: flex-end;
  padding: var(--s-3);
  gap: var(--s-2);
}

.chat-input {
  flex: 1;
  resize: none;
  border: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.55;
  min-height: 24px;
  max-height: 200px;
  padding: 4px 0;
  font-family: inherit;
  color: var(--text);
}
.chat-input::placeholder { color: var(--text-muted); }
.chat-input:focus { outline: none; }

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
  background: var(--primary);
  color: var(--primary-foreground);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.12s, opacity 0.12s;
}
.send-btn:hover { background: var(--primary-hover); }
.send-btn:disabled { background: var(--bg-muted); color: var(--text-muted); cursor: not-allowed; }
.send-btn svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.chat-input-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: var(--s-2);
  text-align: center;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(9, 9, 11, 0.4);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-6);
}

.modal {
  background: var(--bg);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-pop);
  max-width: 480px;
  width: 100%;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: var(--s-5) var(--s-5) var(--s-4);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.modal-title { font-size: 16px; font-weight: 600; letter-spacing: -0.01em; }
.modal-close {
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}
.modal-close:hover { background: var(--bg-hover); color: var(--text); }
.modal-close svg { width: 16px; height: 16px; stroke: currentColor; stroke-width: 2; fill: none; stroke-linecap: round; stroke-linejoin: round; }

.modal-body {
  padding: var(--s-5);
  overflow-y: auto;
  flex: 1;
}

/* List items in modal */
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
.list-item-title { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: block; }
.list-item-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; display: flex; align-items: center; gap: var(--s-2); }

@media (max-width: 1024px) {
  .chat-container, .chat-input-container { max-width: 100%; }
}
</style>
