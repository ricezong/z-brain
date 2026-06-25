import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/knowledge-bases',
    children: [
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBases',
        component: () => import('@/views/knowledge-base/KnowledgeBaseList.vue'),
        meta: { title: '知识库管理', icon: 'Collection' },
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/document/DocumentList.vue'),
        meta: { title: '文档管理', icon: 'Document' },
      },
      {
        path: 'documents/:docId/chunks',
        name: 'ChunkReview',
        component: () => import('@/views/chunk/ChunkReview.vue'),
        meta: { title: '分块审核', icon: 'Files', hidden: true },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound' },
      },
      {
        path: 'prompt-templates',
        name: 'PromptTemplates',
        component: () => import('@/views/prompt-template/PromptTemplateList.vue'),
        meta: { title: '提示词模板', icon: 'EditPen' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const title = (to.meta.title as string) || '智多星知识库系统'
  document.title = `${title} - 智多星知识库`
  next()
})

export default router
