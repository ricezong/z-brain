import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { KnowledgeBase, ChatSession } from '@/types'
import { knowledgeBaseApi } from '@/api/knowledge-base'

export const useAppStore = defineStore('app', () => {
  const currentKb = ref<KnowledgeBase | null>(null)
  const kbList = ref<KnowledgeBase[]>([])
  const currentChatSession = ref<string>('')
  const chatSessions = ref<ChatSession[]>([])

  async function loadKbList() {
    try {
      const res = await knowledgeBaseApi.list({ pageNum: 1, pageSize: 100 })
      kbList.value = res.data.list
    } catch (e) {
      console.error('加载知识库列表失败', e)
    }
  }

  function setCurrentKb(kb: KnowledgeBase | null) {
    currentKb.value = kb
    if (kb) {
      localStorage.setItem('z-brain-current-kb-id', String(kb.id))
    } else {
      localStorage.removeItem('z-brain-current-kb-id')
    }
  }

  function setChatSession(sessionId: string) {
    currentChatSession.value = sessionId
  }

  return {
    currentKb,
    kbList,
    currentChatSession,
    chatSessions,
    loadKbList,
    setCurrentKb,
    setChatSession,
  }
})
