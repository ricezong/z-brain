import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { KnowledgeBase } from '@/types'
import { knowledgeBaseApi } from '@/api/knowledge-base'

export const useAppStore = defineStore('app', () => {
  // 当前选中的知识库
  const currentKb = ref<KnowledgeBase | null>(null)
  // 知识库列表
  const kbList = ref<KnowledgeBase[]>([])
  // 侧边栏折叠状态
  const sidebarCollapsed = ref(false)

  /** 加载知识库列表 */
  async function loadKbList() {
    try {
      const res = await knowledgeBaseApi.list({ pageNum: 1, pageSize: 100 })
      kbList.value = res.data.list
      if (kbList.value.length > 0 && !currentKb.value) {
        currentKb.value = kbList.value[0]
      }
    } catch (e) {
      console.error('加载知识库列表失败', e)
    }
  }

  /** 设置当前知识库 */
  function setCurrentKb(kb: KnowledgeBase) {
    currentKb.value = kb
    localStorage.setItem('z-brain-current-kb-id', String(kb.id))
  }

  /** 切换侧边栏 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    currentKb,
    kbList,
    sidebarCollapsed,
    loadKbList,
    setCurrentKb,
    toggleSidebar,
  }
})
