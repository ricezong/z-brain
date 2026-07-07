import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '知识库管理', icon: 'Collection' }
      },
      {
        path: 'documents',
        name: 'Document',
        component: () => import('@/views/Document.vue'),
        meta: { title: '文档管理', icon: 'Document' }
      },
      {
        path: 'chunks/:docId',
        name: 'ChunkReview',
        component: () => import('@/views/ChunkReview.vue'),
        meta: { title: '分块审核', icon: 'Files', hidden: true }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound' }
      },
      {
        path: 'prompt-templates',
        name: 'PromptTemplate',
        component: () => import('@/views/PromptTemplate.vue'),
        meta: { title: '提示词模板', icon: 'EditPen' }
      },
      {
        path: 'system-config',
        name: 'SystemConfig',
        component: () => import('@/views/SystemConfig.vue'),
        meta: { title: '系统配置', icon: 'Setting' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
