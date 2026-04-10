<script setup>
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { LayoutDashboard, Briefcase, BarChart3, Bot, UserCircle, Sun, Moon } from 'lucide-vue-next'
import { useAuthStore } from './store/auth'
import { useThemeStore } from './store/theme'

const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

// Initialize theme on mount
themeStore.initTheme()

const navigation = computed(() => [
  { name: '概览', path: '/', icon: LayoutDashboard },
  { name: '岗位', path: '/jobs', icon: Briefcase },
  { name: '洞察', path: '/insights', icon: BarChart3 },
  { name: 'AI助手', path: '/ai', icon: Bot, requiresAuth: true },
  { name: '我的', path: '/profile', icon: UserCircle }
])

const filteredNavigation = computed(() => {
  return navigation.value.filter(nav => !nav.requiresAuth || authStore.isLoggedIn)
})
</script>

<template>
  <div class="app-layout">
    <nav class="sidebar glass-panel">
      <div class="brand">
        <div class="logo-glow"></div>
        <div class="brand-logo-icon">
          <BarChart3 :size="22" stroke-width="2.5" />
        </div>
        <span class="brand-text text-gradient text-gradient-primary">
          <span class="text-bold">智绘</span> 职涯
        </span>
      </div>
      
      <div class="nav-links">
        <router-link 
          v-for="item in filteredNavigation" 
          :key="item.path" 
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
          replace
        >
          <component :is="item.icon" class="nav-icon" :size="20" stroke-width="1.5" />
          <span class="nav-label">{{ item.name }}</span>
        </router-link>
      </div>

      <div class="nav-footer">
        <div class="user-status" v-if="authStore.isLoggedIn">
          <div class="avatar-ring">
            <img :src="authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username || 'Guest'}`" alt="avatar" />
          </div>
          <div class="user-info">
            <span class="user-name">{{ authStore.user?.nickname || authStore.user?.username }}</span>
            <span class="user-role">当前在线</span>
          </div>
        </div>
      </div>
    </nav>
    
    <main class="main-content">
      <header class="top-header glass-panel">
        <h1 class="page-title">{{ route.meta.title || '' }}</h1>
        <div class="header-actions">
          <button @click="themeStore.toggleTheme" class="theme-toggle" :title="themeStore.isDark ? '切换至浅色模式' : '切换至深色模式'">
            <Moon v-if="!themeStore.isDark" :size="20" />
            <Sun v-else :size="20" />
          </button>
          <router-link v-if="!authStore.isLoggedIn" to="/profile?login=true" class="login-btn">
            登录 / 注册
          </router-link>
        </div>
      </header>

      <div class="page-container">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  padding: 16px;
  gap: 16px;
}

/* Sidebar */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  background: var(--c-bg-surface);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-right: 1px solid var(--c-border-glass);
}

.brand {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px 32px;
}

.brand-logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  z-index: 2;
}

.logo-glow {
  position: absolute;
  left: 12px;
  top: 0;
  width: 40px;
  height: 40px;
  background: var(--c-accent-primary);
  filter: blur(24px);
  opacity: 0.5;
}

.brand-text {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.1;
  position: relative;
  z-index: 1;
}

.text-bold {
  font-weight: 800;
}

.nav-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  color: var(--c-text-secondary);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-out);
  position: relative;
  overflow: hidden;
}

.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--c-accent-primary);
  border-radius: 0 4px 4px 0;
  transform: scaleY(0);
  transition: transform var(--duration-fast) var(--ease-out);
  transform-origin: center;
}

.nav-item:hover {
  color: var(--c-text-primary);
  background: rgba(255, 255, 255, 0.03);
}

.nav-item.active {
  color: var(--c-text-primary);
  background: linear-gradient(90deg, rgba(30, 117, 255, 0.15), rgba(30, 117, 255, 0));
}

.nav-item.active::before {
  transform: scaleY(0.7);
}

.nav-icon {
  flex-shrink: 0;
  opacity: 0.8;
}
.nav-item.active .nav-icon {
  opacity: 1;
  color: var(--c-accent-primary);
}

.nav-footer {
  margin-top: auto;
  padding-top: 24px;
  border-top: 1px solid var(--c-border-glass);
}

.user-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
}

.avatar-ring {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  padding: 2px;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
}

.avatar-ring img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: var(--c-bg-base);
}

.user-info {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-primary);
}

.user-role {
  font-size: 12px;
  color: var(--c-accent-teal);
}

/* Main Content Area */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0; /* Important for flex children to allow child truncation/scroll */
}

.top-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
  background: var(--c-bg-surface);
  border-bottom: 1px solid var(--c-border-glass);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-radius: 16px;
  margin-bottom: 8px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--c-text-primary);
  letter-spacing: 0.02em;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  transition: all var(--duration-fast);
}

.theme-toggle:hover {
  background: var(--c-bg-surface-active);
  transform: rotate(15deg);
}

.login-btn {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  background: var(--c-accent-primary);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}
.login-btn:hover {
  background: var(--c-accent-primary-hover);
  box-shadow: var(--shadow-glow);
}

.page-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 8px 32px 0;
}

/* Custom Scrollbar for page container */
.page-container::-webkit-scrollbar {
  width: 6px;
}
.page-container::-webkit-scrollbar-track {
  background: transparent;
}
.page-container::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
  border-radius: 10px;
}

/* --- Mobile Responsive Layout --- */
@media (max-width: 768px) {
  .app-layout {
    flex-direction: column;
    padding: 0;
    gap: 0;
  }
  
  .sidebar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    /* iOS Liquid Glass Effect + Home Indicator Support */
    height: calc(64px + env(safe-area-inset-bottom, 0px));
    padding: 0 0 env(safe-area-inset-bottom, 0px) 0;
    flex-direction: row;
    z-index: 1000;
    background: var(--c-bg-surface);
    /* 核心液态模糊算法 */
    backdrop-filter: saturate(180%) blur(24px);
    -webkit-backdrop-filter: saturate(180%) blur(24px);
    /* 用上边框的高光/阴影代替纯色边框，增加物理体积感 */
    border-top: none;
    box-shadow: inset 0 1.5px 0 var(--c-border-glass), 0 -8px 24px rgba(0, 0, 0, 0.05);
    border-radius: 0;
  }
  
  .brand, .nav-footer {
    display: none; /* Hide brand & user info on mobile tab bar */
  }
  
  .nav-links {
    flex-direction: row;
    justify-content: space-around;
    align-items: center;
    width: 100%;
    padding: 0 8px;
    gap: 0;
  }
  
  .nav-item {
    flex-direction: column;
    padding: 10px 4px 6px;
    gap: 4px;
    border-radius: 12px;
    width: 65px;
  }

  .nav-item::before {
    display: none; /* Remove left highlight bar */
  }

  .nav-icon {
    margin: 0;
  }

  .nav-label {
    font-size: 10px;
    line-height: 1;
  }
  
  .main-content {
    height: calc(100vh - 64px - env(safe-area-inset-bottom, 0px)); /* Leave room for bottom nav */
    gap: 0;
    padding-bottom: 0;
  }
  
  .top-header {
    height: 56px;
    padding: 0 16px;
    border-radius: 0;
    border-bottom: 1px solid var(--c-border-glass);
  }
  
  .page-container {
    padding: 12px;
  }
}
</style>
