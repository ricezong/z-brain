<template>
  <div class="layout-wrapper">
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="navbar-inner">
        <!-- 左侧：Logo + 导航菜单 -->
        <div class="navbar-left">
          <div class="navbar-logo">
            <svg viewBox="0 0 48 48" width="32" height="32">
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
            <span class="logo-text">Z-Brain</span>
          </div>

          <nav class="navbar-menu">
            <router-link
              v-for="item in menuItems"
              :key="item.path"
              :to="item.path"
              class="menu-item"
              :class="{ active: isActive(item.path) }"
            >
              <el-icon class="menu-icon"><component :is="item.icon" /></el-icon>
              <span class="menu-text">{{ item.title }}</span>
            </router-link>
          </nav>
        </div>

        <!-- 右侧：用户头像 -->
        <div class="navbar-right">
          <div class="avatar-wrapper">
            <div class="avatar">Z</div>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="content-area">
      <router-view v-slot="{ Component }">
        <transition name="slide-up" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const menuItems = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/knowledge-bases', title: '知识库管理', icon: 'Collection' },
  { path: '/documents', title: '文档管理', icon: 'Document' },
  { path: '/chat', title: '智能问答', icon: 'ChatDotRound' },
  { path: '/prompt-templates', title: '提示词模板', icon: 'EditPen' },
  { path: '/system-config', title: '系统配置', icon: 'Setting' }
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
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* ==================== 顶部导航栏 ==================== */
.navbar {
  height: var(--header-height);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
  z-index: 100;
}

.navbar-inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

/* Logo */
.navbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo-text {
  font-size: 18px;
  font-weight: 800;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  white-space: nowrap;
}

/* 导航菜单 */
.navbar-menu {
  display: flex;
  align-items: center;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  color: var(--text-regular);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  white-space: nowrap;
  position: relative;
}
.menu-item:hover {
  background: var(--bg-hover);
  color: var(--primary);
}
.menu-item.active {
  color: var(--primary);
  background: var(--primary-gradient-soft);
}
.menu-item.active::after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 3px;
  border-radius: 2px;
  background: var(--primary-gradient);
}
.menu-icon {
  font-size: 18px;
  flex-shrink: 0;
}

/* 右侧 */
.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
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

/* ==================== 主内容区 ==================== */
.content-area {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-page);
}
</style>
