<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  BarChart3,
  Bot,
  Briefcase,
  DatabaseZap,
  LayoutDashboard,
  Moon,
  ScrollText,
  Sparkles,
  Sun,
  UserCircle,
  Webhook
} from 'lucide-vue-next'
import { useAuthStore } from './store/auth'
import { useThemeStore } from './store/theme'

const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

themeStore.initTheme()
onMounted(() => {
  authStore.syncProfile()
})

const navGroups = computed(() => [
  {
    title: '运营管理',
    items: [
      { name: '数据采集', path: '/crawler', icon: DatabaseZap, requiresAuth: true }
    ]
  },
  {
    title: '核心功能',
    items: [
      { name: '仪表盘', path: '/', icon: LayoutDashboard },
      { name: '洞察分析', path: '/insights', icon: BarChart3 }
    ]
  },
  {
    title: '工作区',
    items: [
      { name: '分析报告', path: '/reports', icon: ScrollText, requiresAuth: true },
      { name: '智能推荐', path: '/recommend', icon: Sparkles, requiresAuth: true },
      { name: 'AI 助手', path: '/ai', icon: Bot, requiresAuth: true }
    ]
  },
  {
    title: '职位',
    items: [
      { name: '职位列表', path: '/jobs', icon: Briefcase }
    ]
  },
  {
    title: '生态周边',
    items: [
      { name: '开放 API', path: '/openapi', icon: Webhook }
    ]
  },
  {
    title: '账户',
    items: [
      { name: '个人主页', path: '/profile', icon: UserCircle }
    ]
  }
].map((group) => ({
  ...group,
  items: group.items.filter((item) => !item.requiresAuth || authStore.isLoggedIn)
})))

const activeGroupIndex = computed(() => {
  const idx = navGroups.value.findIndex((group) =>
    group.items.some((item) => item.path === route.path)
  )
  return idx >= 0 ? idx : 0
})

const activeGroup = computed(() => navGroups.value[activeGroupIndex.value] || navGroups.value[0])
</script>

<template>
  <div class="app-shell">
    <header class="topbar glass-panel">
      <div class="topbar-brand">
        <router-link to="/" class="brand-lockup">
          <div class="brand-logo-icon">
            <BarChart3 :size="20" stroke-width="2.5" />
          </div>
          <div class="brand-copy">
            <span class="brand-text"><span class="text-bold">职涯</span>OS</span>
            <span class="brand-subtitle">职业能力大数据平台</span>
          </div>
        </router-link>
      </div>
      <nav class="topbar-nav">
        <router-link
          v-for="(group, idx) in navGroups"
          :key="group.title"
          :to="group.items[0]?.path || '/'"
          class="topbar-link"
          :class="{ active: idx === activeGroupIndex }"
        >
          {{ group.title }}
        </router-link>
      </nav>
      <div class="topbar-actions">
        <div class="user-chip">
          <div class="avatar-ring">
            <img
              :src="authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username || 'Guest'}`"
              alt="头像"
            />
          </div>
          <div class="user-info">
            <span class="user-name">{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客' }}</span>
            <span v-if="authStore.isLoggedIn" class="user-role">Academic Curator</span>
            <router-link v-else to="/profile?login=true" class="login-link">立即登录</router-link>
          </div>
        </div>
        <button
          class="footer-toggle"
          :title="themeStore.isDark ? '切换至亮色模式' : '切换至暗色模式'"
          @click="themeStore.toggleTheme"
        >
          <Moon v-if="!themeStore.isDark" :size="16" />
          <Sun v-else :size="16" />
        </button>
      </div>
    </header>

    <div class="app-layout">
      <aside class="sidebar glass-panel">
        <div class="sidebar-head">
          <p class="sidebar-eyebrow">Current Section</p>
          <h2 class="sidebar-title">{{ activeGroup?.title }}</h2>
        </div>

        <div class="nav-links">
          <router-link
            v-for="item in activeGroup?.items || []"
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
          <div class="sidebar-note">
            <p class="sidebar-note-title">目录说明</p>
            <p class="sidebar-note-text">左侧展示当前一级导航下的细分类目录，内容页结构保持不变。</p>
          </div>
        </div>
      </aside>

      <main class="main-content">
        <div class="page-container">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 80px;
  padding: 0 32px;
  display: grid;
  grid-template-columns: 260px 1fr auto;
  align-items: center;
  gap: 20px;
  margin-bottom: 0;
  border-radius: 0;
  border-left: none;
  border-right: none;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 12px 40px rgba(24, 27, 35, 0.06);
}
.topbar-brand { min-width: 0; }
.brand-lockup { display: inline-flex; align-items: center; gap: 14px; color: inherit; text-decoration: none; }
.topbar-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow-x: auto;
}
.topbar-link {
  padding: 8px 12px;
  border-radius: 10px;
  color: var(--c-text-muted);
  font-family: var(--font-display);
  font-size: 18px;
  line-height: 1;
  white-space: nowrap;
}
.topbar-link:hover { background: rgba(0, 89, 199, 0.05); color: var(--c-accent-primary); }
.topbar-link.active {
  color: var(--c-accent-primary);
  font-weight: 700;
  border-bottom: 2px solid var(--c-accent-primary);
  border-radius: 0;
}
.topbar-actions { display: flex; align-items: center; gap: 12px; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid var(--c-border-glass);
}
.app-layout {
  display: flex;
  min-height: calc(100vh - 80px);
  overflow: hidden;
  gap: 0;
}
.sidebar {
  width: 244px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 0 18px;
  border-radius: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
  box-shadow: 0 18px 40px rgba(24, 27, 35, 0.08);
}
.sidebar-head { padding: 0 20px 16px; }
.sidebar-eyebrow {
  margin: 0 0 6px;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: var(--c-text-faint);
}
.sidebar-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.1;
  color: var(--c-text-primary);
}
.brand-logo-icon {
  display: flex; align-items: center; justify-content: center; width: 42px; height: 42px; border-radius: 14px;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-primary-hover));
  color: white;
  box-shadow: 0 10px 24px rgba(0, 89, 199, 0.18);
}
.brand-copy { display: flex; flex-direction: column; gap: 2px; }
.brand-text { font-family: var(--font-display); font-size: 22px; font-weight: 700; line-height: 1; color: var(--c-accent-primary); }
.brand-subtitle { color: var(--c-text-muted); font-size: 10px; letter-spacing: 0.18em; text-transform: uppercase; }
.text-bold { font-weight: 800; }
.nav-links { display: flex; flex-direction: column; gap: 2px; flex: 1; overflow-y: auto; overflow-x: hidden; padding: 0 12px 20px; }
.nav-links::-webkit-scrollbar { width: 4px; }
.nav-links::-webkit-scrollbar-thumb { background: rgba(193, 198, 215, 0.84); border-radius: 4px; }
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 13px 14px; border-radius: 14px;
  color: var(--c-text-secondary); font-weight: 500; transition: color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
  position: relative; overflow: hidden; font-size: 14px;
}
.nav-item::before {
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  border: 1px solid rgba(255, 255, 255, 0.42);
  opacity: 0; transition: opacity var(--duration-fast) var(--ease-out);
}
.nav-item::after {
  display: none;
}
.nav-item:hover {
  color: var(--c-accent-primary); background: rgba(0, 89, 199, 0.04);
  transform: translateX(1px);
  box-shadow: none;
}
.nav-item:hover::before { opacity: 1; }
.nav-item.active {
  color: var(--c-accent-primary);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 8px rgba(24, 27, 35, 0.04);
  font-weight: 600;
}
.nav-item.active::before { opacity: 1; }
.nav-icon { flex-shrink: 0; opacity: 0.88; transition: opacity var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out); }
.nav-label { transition: color var(--duration-fast) var(--ease-out); }
.nav-item:hover .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-item.active .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-footer { margin-top: auto; padding: 18px 12px 6px; }
.sidebar-note {
  padding: 16px;
  border-radius: 14px;
  background: rgba(242, 243, 255, 0.88);
  border: 1px solid var(--c-border-glass);
}
.sidebar-note-title {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--c-text-primary);
}
.sidebar-note-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-muted);
}
.avatar-ring {
  width: 38px; height: 38px; border-radius: 50%; padding: 2px;
  background: rgba(217, 226, 255, 1);
  border: 2px solid rgba(0, 110, 242, 0.18);
}
.avatar-ring img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; background: var(--c-bg-base); }
.user-info { display: flex; flex-direction: column; overflow: hidden; }
.user-name { font-size: 13px; font-weight: 600; color: var(--c-text-primary); white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.user-role { font-size: 10px; color: var(--c-text-muted); letter-spacing: 0.12em; text-transform: uppercase; }
.login-link { font-size: 11px; color: var(--c-accent-primary); font-weight: 600; text-decoration: none; }
.login-link:hover { text-decoration: underline; }
.footer-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 34px; height: 34px; border-radius: 999px; flex-shrink: 0;
  background: rgba(242, 243, 255, 0.95); color: var(--c-text-muted);
  border: 1px solid var(--c-border-glass);
  transition: all var(--duration-fast);
}
.footer-toggle:hover { background: rgba(255,255,255,1); color: var(--c-accent-primary); }
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  padding: 48px 40px 72px;
}
.page-container { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 0; }
.page-container::-webkit-scrollbar { width: 6px; }
.page-container::-webkit-scrollbar-track { background: transparent; }
.page-container::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
@media (max-width: 768px) {
  .topbar {
    position: static;
    grid-template-columns: 1fr auto;
    height: auto;
    padding: 16px 20px;
  }
  .topbar-nav {
    grid-column: 1 / -1;
    order: 3;
    padding-top: 8px;
  }
  .app-layout { flex-direction: column; min-height: auto; gap: 12px; }
  .sidebar {
    width: 100%;
    padding-top: 18px;
    border-right: none;
    box-shadow: none;
  }
  .sidebar-head { padding-bottom: 12px; }
  .nav-footer { display: none; }
  .main-content { min-height: 0; }
  .main-content { padding: 12px 20px 72px; }
  .page-container { padding: 0; }
}
</style>
