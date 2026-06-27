import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/ChatView.vue'),
      },
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBases',
        component: () => import('@/views/knowledge-base/KnowledgeBaseList.vue'),
      },
      {
        path: 'knowledge-bases/:kbId',
        name: 'KnowledgeBaseDetail',
        component: () => import('@/views/knowledge-base/KnowledgeBaseDetail.vue'),
      },
      {
        path: 'documents/:docId/chunks',
        name: 'ChunkReview',
        component: () => import('@/views/chunk/ChunkReview.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  document.title = '智识 · AI 对话与知识库平台'
  next()
})

export default router
