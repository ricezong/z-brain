<template>
  <div class="layout-wrapper">
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="navbar-inner">
        <!-- 左侧：Logo（首页入口） -->
        <div class="navbar-left">
          <router-link to="/dashboard" class="navbar-logo">
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
          </router-link>
        </div>

        <!-- 中间：导航菜单 -->
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

        <!-- 右侧：系统管理下拉 + 用户头像 -->
        <div class="navbar-right">
          <el-dropdown trigger="click" placement="bottom-end" popper-class="avatar-dropdown" @command="handleCommand">
            <div class="avatar-wrapper">
              <div class="avatar">Z</div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>系统管理</el-dropdown-item>
                <el-dropdown-item :command="'/prompt-templates'">
                  <el-icon><EditPen /></el-icon>
                  提示词模板
                </el-dropdown-item>
                <el-dropdown-item :command="'/system-config'">
                  <el-icon><Setting /></el-icon>
                  系统配置
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
import { useRoute, useRouter } from 'vue-router'
import { EditPen, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { path: '/chat', title: '智能问答', icon: 'ChatDotRound' },
  { path: '/knowledge-bases', title: '知识库管理', icon: 'DataBoard' },
  { path: '/documents', title: '文档管理', icon: 'Document' }
]

const currentTitle = computed(() => {
  return route.meta.title || 'Z-Brain'
})

function isActive(path) {
  return route.path.startsWith(path)
}

function handleCommand(path) {
  router.push(path)
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
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 24px;
}

.navbar-left {
  display: flex;
  align-items: center;
  justify-self: start;
}

.navbar-menu {
  justify-self: center;
}

/* Logo */
.navbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  cursor: pointer;
  transition: opacity 0.2s;
}
.navbar-logo:hover {
  opacity: 0.8;
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
  justify-self: end;
  gap: 16px;
}
.avatar-wrapper {
  display: flex;
  align-items: center;
  cursor: pointer;
  outline: none;
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
  transition: transform 0.2s;
}
.avatar-wrapper:hover .avatar {
  transform: scale(1.05);
}

/* ==================== 主内容区 ==================== */
.content-area {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-page);
}
</style>

<!-- 非 scoped 样式：控制 teleport 到 body 的下拉菜单 -->
<style>
.avatar-dropdown .el-popper__arrow {
  display: none !important;
}
</style>
