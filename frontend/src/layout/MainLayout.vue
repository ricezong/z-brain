<template>
  <div class="layout-wrapper">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-logo">
        <div class="logo-icon">
          <svg viewBox="0 0 48 48" width="36" height="36">
            <defs>
              <linearGradient id="logoG" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#6366f1"/>
                <stop offset="100%" stop-color="#8b5cf6"/>
              </linearGradient>
            </defs>
            <rect width="48" height="48" rx="12" fill="url(#logoG)"/>
            <path d="M16 14c0-1.1.9-2 2-2h8a8 8 0 0 1 0 16h-4v6a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V14z" fill="white" opacity="0.95"/>
            <circle cx="24" cy="20" r="3" fill="#6366f1"/>
          </svg>
        </div>
        <transition name="fade">
          <span v-show="!isCollapsed" class="logo-text">Z-Brain</span>
        </transition>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <transition name="fade">
            <span v-show="!isCollapsed" class="nav-text">{{ item.title }}</span>
          </transition>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="footer-card" v-show="!isCollapsed">
          <div class="footer-card-icon">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="footer-card-text">
            <div class="footer-card-title">智能 RAG 引擎</div>
            <div class="footer-card-desc">HyDE · 多路召回 · Rerank</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-area">
      <!-- 顶部栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <el-icon class="collapse-btn" @click="isCollapsed = !isCollapsed">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <div class="breadcrumb">
            <span class="breadcrumb-main">{{ currentTitle }}</span>
          </div>
        </div>
        <div class="topbar-right">
          <el-tooltip content="智能问答" placement="bottom">
            <el-icon class="topbar-icon" @click="$router.push('/chat')">
              <ChatDotRound />
            </el-icon>
          </el-tooltip>
          <el-tooltip content="GitHub" placement="bottom">
            <el-icon class="topbar-icon"><Link /></el-icon>
          </el-tooltip>
          <div class="avatar-wrapper">
            <div class="avatar">Z</div>
          </div>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="slide-up" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isCollapsed = ref(false)

const menuItems = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/knowledge-bases', title: '知识库管理', icon: 'Collection' },
  { path: '/documents', title: '文档管理', icon: 'Document' },
  { path: '/chat', title: '智能问答', icon: 'ChatDotRound' },
  { path: '/prompt-templates', title: '提示词模板', icon: 'EditPen' }
]

const currentTitle = computed(() => {
  return route.meta.title || 'Z-Brain'
})

function isActive(path) {
  return route.path.startsWith(path)
}
</script>

<style scoped>
.layout-wrapper {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ==================== 侧边栏 ==================== */
.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  z-index: 100;
}
.sidebar.collapsed {
  width: 72px;
}

.sidebar-logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 12px;
  border-bottom: 1px solid var(--border-light);
}
.logo-icon {
  flex-shrink: 0;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  color: var(--text-regular);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  white-space: nowrap;
  gap: 12px;
}
.nav-item:hover {
  background: var(--bg-hover);
  color: var(--primary);
}
.nav-item.active {
  background: var(--primary-gradient);
  color: #fff;
  box-shadow: var(--shadow-primary);
}
.nav-icon {
  font-size: 20px;
  flex-shrink: 0;
}
.nav-text {
  overflow: hidden;
}

.sidebar-footer {
  padding: 16px;
}
.footer-card {
  background: var(--primary-gradient-soft);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.footer-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--primary-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.footer-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.footer-card-desc {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* ==================== 主内容区 ==================== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  height: var(--header-height);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
  z-index: 50;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  font-size: 20px;
  color: var(--text-regular);
  cursor: pointer;
  transition: color 0.2s;
}
.collapse-btn:hover {
  color: var(--primary);
}
.breadcrumb-main {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.topbar-icon {
  font-size: 20px;
  color: var(--text-regular);
  cursor: pointer;
  transition: color 0.2s;
}
.topbar-icon:hover {
  color: var(--primary);
}
.avatar-wrapper {
  display: flex;
  align-items: center;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s;
}
.avatar:hover {
  transform: scale(1.05);
}

.content-area {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-page);
}
</style>
