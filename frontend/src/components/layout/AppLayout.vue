<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const isCollapse = ref(false)
const activeMenu = computed(() => route.path)

const menuItems = [
  { path: '/projects', title: '项目管理', icon: 'Folder' },
  { path: '/uml-editor', title: 'UML 编辑器', icon: 'Connection' },
  { path: '/reports', title: '报告历史', icon: 'Document' },
  { path: '/ai-chat', title: 'AI 对话', icon: 'ChatDotRound' },
  { path: '/settings', title: '系统设置', icon: 'Setting' },
]
</script>

<template>
  <el-container style="height:100vh">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '232px'" class="app-sidebar">
      <div class="sidebar-brand" :class="{ collapsed: isCollapse }">
        <div class="brand-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="3" width="16" height="18" rx="2" stroke="#fff" stroke-width="1.8" fill="none"/>
            <line x1="8" y1="8" x2="16" y2="8" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="8" y1="12" x2="14" y2="12" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="8" y1="16" x2="12" y2="16" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="17" cy="5" r="2.5" fill="#0070f3" stroke="#fff" stroke-width="1"/>
          </svg>
        </div>
        <span v-if="!isCollapse" class="brand-text">实验报告系统</span>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapse" router class="sidebar-menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>

      <!-- Bottom: collapse toggle -->
      <div class="sidebar-footer" :class="{ collapsed: isCollapse }">
        <button class="collapse-btn" @click="isCollapse = !isCollapse" :aria-label="isCollapse ? '展开侧栏' : '收起侧栏'">
          <svg width="16" height="16" viewBox="0 0 16 16" :style="{ transform: isCollapse ? 'rotate(180deg)' : '' }">
            <path d="M10 3L5 8l5 5" stroke="rgba(255,255,255,0.5)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          </svg>
        </button>
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <span class="header-breadcrumb">{{ route.meta.title || '' }}</span>
        </div>
        <div class="header-right">
          <span class="header-user">{{ auth.displayName || auth.username }}</span>
          <button class="btn-logout" @click="auth.logout()" aria-label="退出登录">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M6 14H3.5A1.5 1.5 0 012 12.5v-9A1.5 1.5 0 013.5 2H6M11 11l3-3-3-3M14 8H6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>退出</span>
          </button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
/* --- Sidebar --- */
.app-sidebar {
  background: #111;
  display: flex;
  flex-direction: column;
  transition: width 250ms cubic-bezier(0.4,0,0.2,1);
  overflow: hidden;
  border-right: 1px solid rgba(255,255,255,0.06);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  &.collapsed { justify-content: center; padding: 18px 0; }
}

.brand-icon {
  flex-shrink: 0;
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
}

.brand-text {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.sidebar-menu {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}

.sidebar-footer {
  padding: 8px 16px;
  border-top: 1px solid rgba(255,255,255,0.08);
  &.collapsed { display:flex; justify-content:center; padding:8px 0; }
}

.collapse-btn {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 6px;
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: all 150ms ease;
  &:hover { background: rgba(255,255,255,0.08); border-color: rgba(255,255,255,0.2); }
  &:focus-visible { outline: 2px solid #0070f3; outline-offset: 2px; }
  svg { transition: transform 250ms ease; }
}

/* --- Header --- */
.app-header {
  background: #fff;
  border-bottom: 1px solid #e5e5e5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
}

.header-left {
  display: flex; align-items: center;
}
.header-breadcrumb {
  font-size: 14px;
  color: #4a4a4a;
  font-weight: 500;
}

.header-right {
  display: flex; align-items: center; gap: 12px;
}
.header-user {
  font-size: 13px;
  color: #8a8a8a;
}

.btn-logout {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid #e5e5e5;
  background: #fff;
  color: #e5484d;
  font-size: 13px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 150ms ease;
  &:hover {
    background: #fef3f3;
    border-color: #f5c6cb;
    transform: translateY(-0.5px);
  }
  &:active { transform: translateY(0.5px); }
  &:focus-visible { outline: 2px solid #e5484d; outline-offset: 2px; }
}

.app-main {
  background: #fafafa;
  min-height: calc(100vh - 56px);
}
</style>
