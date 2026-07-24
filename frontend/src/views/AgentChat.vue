<template>
  <div class="agent-chat">
    <!-- 消息区 -->
    <div class="message-area" ref="messageArea">
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">🦌</div>
        <h2>灵犀 LingXi</h2>
        <p>你的个人 AI 助手 · 可以搜索知识库、回答问题、执行多步任务</p>
        <div class="suggestions">
          <el-tag v-for="s in suggestions" :key="s" @click="sendMessage(s)" class="suggestion-tag" effect="plain">
            {{ s }}
          </el-tag>
        </div>
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="['message-row', msg.role]">
        <div class="avatar">{{ msg.role === 'user' ? '🧑' : '🦌' }}</div>
        <div class="bubble">
          <MarkdownView v-if="msg.role === 'assistant'" :content="msg.content" :isStreaming="msg.streaming" />
          <span v-else>{{ msg.content }}</span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="给灵犀发消息..."
        @keydown.enter.exact.prevent="sendMessage()"
        :disabled="streaming"
      />
      <el-button type="primary" :loading="streaming" @click="sendMessage()" :icon="Promotion">
        {{ streaming ? '思考中' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import { agentChatStream } from '@/api/agent'
import MarkdownView from '@/components/MarkdownView.vue'

const messages = ref([])
const inputText = ref('')
const streaming = ref(false)
const messageArea = ref(null)
const sessionId = ref(null)

const suggestions = [
  '现在几点了？',
  '帮我搜索知识库中关于部署的内容',
  '你能做什么？'
]

function sendMessage(text) {
  const content = (text || inputText.value).trim()
  if (!content || streaming.value) return

  // 用户消息
  messages.value.push({ role: 'user', content })
  inputText.value = ''

  // AI 占位
  const aiMsg = { role: 'assistant', content: '', streaming: true }
  messages.value.push(aiMsg)

  streaming.value = true
  scrollToBottom()

  agentChatStream(
    { sessionId: sessionId.value, message: content },
    {
      onMessage: ({ type, data }) => {
        if (type === 'session') {
          sessionId.value = data
        } else if (type === 'content') {
          aiMsg.content += data
          scrollToBottom()
        }
      },
      onDone: () => {
        aiMsg.streaming = false
        streaming.value = false
      },
      onError: (err) => {
        aiMsg.content += '\n\n**⚠️ 出错了：** ' + err.message
        aiMsg.streaming = false
        streaming.value = false
      }
    }
  )
}

function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.agent-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 900px;
  margin: 0 auto;
}

.message-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}

.welcome {
  text-align: center;
  padding: 80px 0;
}
.welcome-icon { font-size: 56px; }
.welcome h2 { margin: 16px 0 8px; font-size: 28px; }
.welcome p { color: var(--el-text-color-secondary); margin-bottom: 24px; }
.suggestions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.suggestion-tag { cursor: pointer; }

.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  padding: 0 24px;
}
.message-row.user { flex-direction: row-reverse; }

.avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  background: var(--el-fill-color-light);
  flex-shrink: 0;
}

.bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
}
.message-row.user .bubble {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
}
.message-row.assistant .bubble {
  background: var(--el-fill-color-light);
}

.input-area {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--el-border-color-lighter);
  align-items: flex-end;
}
.input-area .el-textarea { flex: 1; }
</style>
