<template>
  <el-container class="main-layout">
    <!-- 侧边栏 -->
    <el-aside :width="sidebarCollapsed ? '72px' : '240px'" class="sidebar">
      <div class="logo-area">
        <div class="logo-icon">
          <svg viewBox="0 0 32 32" width="28" height="28">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#818cf8;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#6366f1;stop-opacity:1" />
              </linearGradient>
            </defs>
            <rect width="32" height="32" rx="8" fill="url(#logoGrad)"/>
            <text x="16" y="22" font-size="16" font-weight="bold" fill="white" text-anchor="middle" font-family="Inter,sans-serif">Z</text>
          </svg>
        </div>
        <transition name="fade">
          <span v-show="!sidebarCollapsed" class="logo-text">智多星</span>
        </transition>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.55)"
        active-text-color="#ffffff"
        router
        class="sidebar-menu"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
          class="sidebar-item"
        >
          <el-icon size="18"><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <!-- 底部折叠按钮 -->
      <div class="sidebar-footer" @click="toggleSidebar">
        <el-icon size="18" color="rgba(255,255,255,0.4)">
          <Fold v-if="!sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
        <transition name="fade">
          <span v-show="!sidebarCollapsed" class="collapse-text">收起菜单</span>
        </transition>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-select
            v-model="selectedKbId"
            placeholder="选择知识库"
            class="kb-selector"
            @change="onKbChange"
          >
            <template #prefix>
              <el-icon><Collection /></el-icon>
            </template>
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-avatar :size="36" class="user-avatar">
            <el-icon><User /></el-icon>
          </el-avatar>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { storeToRefs } from 'pinia'

const route = useRoute()
const appStore = useAppStore()
const { currentKb, kbList, sidebarCollapsed } = storeToRefs(appStore)

const selectedKbId = ref<number | undefined>()

const menuItems = [
  { path: '/knowledge-bases', title: '知识库管理', icon: 'Collection' },
  { path: '/documents', title: '文档管理', icon: 'Document' },
  { path: '/chat', title: '智能问答', icon: 'ChatDotRound' },
  { path: '/prompt-templates', title: '提示词模板', icon: 'EditPen' },
]

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/documents')) return '/documents'
  return path
})

const currentTitle = computed(() => {
  return (route.meta.title as string) || '智多星知识库'
})

function toggleSidebar() {
  appStore.toggleSidebar()
}

function onKbChange(kbId: number) {
  const kb = kbList.value.find((k) => k.id === kbId)
  if (kb) {
    appStore.setCurrentKb(kb)
  }
}

watch(currentKb, (val) => {
  selectedKbId.value = val?.id
}, { immediate: true })

onMounted(() => {
  appStore.loadKbList()
})
</script>

<style scoped lang="scss">
.main-layout {
  height: 100vh;
}

/* ==================== 侧边栏 ==================== */
.sidebar {
  background: var(--sidebar-bg);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 10;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: radial-gradient(circle at 30% 20%, rgba(99, 102, 241, 0.15) 0%, transparent 50%);
    pointer-events: none;
  }

  .logo-area {
    height: var(--header-height);
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    position: relative;
    z-index: 1;

    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .logo-text {
      color: #fff;
      font-size: 18px;
      font-weight: 700;
      letter-spacing: 0.02em;
      white-space: nowrap;
    }
  }

  .sidebar-menu {
    flex: 1;
    border-right: none !important;
    padding: 16px 12px;
    position: relative;
    z-index: 1;
    overflow-y: auto;
    overflow-x: hidden;

    &::-webkit-scrollbar {
      width: 0;
    }
  }

  :deep(.sidebar-item) {
    height: 44px;
    line-height: 44px;
    margin-bottom: 4px;
    border-radius: var(--radius-sm) !important;
    padding-left: 16px !important;
    transition: all 0.2s ease;
    position: relative;

    &:hover {
      background: rgba(255, 255, 255, 0.08) !important;
      color: #fff !important;
    }

    &.is-active {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.3) 0%, rgba(129, 140, 248, 0.2) 100%) !important;
      color: #fff !important;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 20px;
        background: var(--primary-light);
        border-radius: 0 3px 3px 0;
      }
    }
  }

  .sidebar-footer {
    height: 48px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 20px;
    cursor: pointer;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    transition: background 0.2s;
    position: relative;
    z-index: 1;

    &:hover {
      background: rgba(255, 255, 255, 0.05);
    }

    .collapse-text {
      color: rgba(255, 255, 255, 0.4);
      font-size: 13px;
      white-space: nowrap;
    }
  }
}

/* ==================== 顶部导航 ==================== */
.header {
  height: var(--header-height);
  background: var(--surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  box-shadow: var(--shadow-xs);
  z-index: 9;

  .header-left {
    .breadcrumb {
      :deep(.el-breadcrumb__item) {
        .el-breadcrumb__inner {
          color: var(--text-tertiary);
          font-weight: 400;
        }
        &:last-child .el-breadcrumb__inner {
          color: var(--text-primary);
          font-weight: 600;
        }
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .kb-selector {
      width: 200px;

      :deep(.el-select__wrapper) {
        background: var(--bg);
        box-shadow: none !important;
        border: 1px solid var(--border) !important;
      }
    }

    .user-avatar {
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
      color: #fff;
      cursor: pointer;
      transition: var(--transition);

      &:hover {
        transform: scale(1.05);
        box-shadow: var(--shadow-primary);
      }
    }
  }
}

/* ==================== 主内容区 ==================== */
.main-content {
  background: var(--bg);
  padding: 0;
  overflow: hidden;
}

/* ==================== 过渡动画 ==================== */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.page-enter-active {
  transition: all 0.3s ease;
}
.page-leave-active {
  transition: all 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
