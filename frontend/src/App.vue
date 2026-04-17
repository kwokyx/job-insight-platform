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
import logoUrl from '../logo.png'
import AmbientParticles from './components/common/AmbientParticles.vue'
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
})).filter((group) => group.items.length > 0))

const activeGroupIndex = computed(() => {
  const idx = navGroups.value.findIndex((group) =>
    group.items.some((item) => item.path === route.path)
  )
  return idx >= 0 ? idx : 0
})

const activeGroup = computed(() => navGroups.value[activeGroupIndex.value] || navGroups.value[0])
const activeGroupCount = computed(() => activeGroup.value?.items.length || 0)
</script>

<template>
  <AmbientParticles />
  <div class="app-shell">
    <header class="topbar glass-panel">
      <div class="topbar-brand">
        <router-link to="/" class="brand-lockup">
          <div class="brand-logo-shell">
            <img :src="logoUrl" alt="职业能力大数据平台 Logo" class="brand-logo-image" />
          </div>
          <div class="brand-copy">
            <span class="brand-kicker">Job Insight Platform</span>
            <span class="brand-text"><span class="text-bold">职涯</span>OS</span>
            <span class="brand-subtitle">职业能力大数据平台</span>
          </div>
        </router-link>
      </div>
      <nav class="topbar-nav" aria-label="一级导航">
        <router-link
          v-for="(group, idx) in navGroups"
          :key="group.title"
          :to="group.items[0]?.path || '/'"
          class="topbar-link"
          :class="{ active: idx === activeGroupIndex }"
          :aria-current="idx === activeGroupIndex ? 'page' : null"
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
          <p class="sidebar-summary">{{ activeGroupCount }} 个入口</p>
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
            <p class="sidebar-note-text">左侧仅保留当前一级导航下的细分类入口，内容页结构保持不变。</p>
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
.app-shell {
  position: relative;
  z-index: 1;
  min-height: 100dvh;
  --shell-topbar-height: 96px;
  --shell-sidebar-width: clamp(228px, 18vw, 276px);
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: var(--shell-topbar-height);
  padding: 0 clamp(20px, 2.5vw, 32px);
  display: grid;
  grid-template-columns: minmax(248px, 0.96fr) minmax(0, 1.2fr) auto;
  align-items: center;
  gap: 18px;
  margin-bottom: 0;
  border-radius: 0;
  border-left: none;
  border-right: none;
  border-bottom: 1px solid rgba(188, 194, 208, 0.42);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(250, 251, 254, 0.9)),
    rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.025);
}
.topbar-brand { min-width: 0; }
.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  color: inherit;
  text-decoration: none;
  min-width: 0;
}
.brand-logo-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 249, 252, 0.88)),
    rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(193, 198, 215, 0.44);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    0 6px 18px rgba(24, 27, 35, 0.035);
  flex-shrink: 0;
}
.brand-logo-image {
  width: 50px;
  height: 50px;
  object-fit: contain;
  flex-shrink: 0;
}
.topbar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.topbar-nav::-webkit-scrollbar { display: none; }
.topbar-link {
  padding: 9px 12px;
  border-radius: 999px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}
.topbar-link:hover {
  background: rgba(0, 89, 199, 0.06);
  color: var(--c-accent-primary);
}
.topbar-link.active {
  color: var(--c-accent-primary);
  font-weight: 700;
  background: rgba(0, 89, 199, 0.08);
  box-shadow: inset 0 0 0 1px rgba(0, 89, 199, 0.12);
}
.topbar-actions { display: flex; align-items: center; gap: 12px; }
.user-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid rgba(193, 198, 215, 0.48);
}
.app-layout {
  display: grid;
  grid-template-columns: var(--shell-sidebar-width) minmax(0, 1fr);
  min-height: calc(100dvh - var(--shell-topbar-height));
  overflow: hidden;
  gap: 0;
}
.sidebar {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 22px 0 16px;
  border-radius: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
  border-right: 1px solid rgba(193, 198, 215, 0.42);
  box-shadow: 0 12px 30px rgba(24, 27, 35, 0.05);
  background: rgba(255, 255, 255, 0.72);
}
.sidebar-head { padding: 0 20px 14px; }
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
.sidebar-summary {
  margin: 8px 0 0;
  color: var(--c-text-muted);
  font-size: 12px;
  letter-spacing: 0.04em;
}
.brand-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.brand-kicker {
  color: #7f8898;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  white-space: nowrap;
}
.brand-text {
  font-family: var(--font-display);
  font-size: 23px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  color: #17263b;
  white-space: nowrap;
}
.brand-subtitle {
  color: #596477;
  font-size: 10.5px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}
.text-bold { font-weight: 800; }
.nav-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 12px 20px;
}
.nav-links::-webkit-scrollbar { width: 4px; }
.nav-links::-webkit-scrollbar-thumb { background: rgba(193, 198, 215, 0.84); border-radius: 4px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  color: var(--c-text-secondary);
  font-weight: 500;
  transition: color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
  position: relative;
  overflow: hidden;
  font-size: 14px;
}
.nav-item:hover {
  color: var(--c-accent-primary);
  background: rgba(0, 89, 199, 0.04);
  transform: translateX(1px);
  box-shadow: none;
}
.nav-item.active {
  color: var(--c-accent-primary);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 3px 0 0 var(--c-accent-primary), 0 2px 8px rgba(24, 27, 35, 0.04);
  font-weight: 600;
}
.nav-icon { flex-shrink: 0; opacity: 0.88; transition: opacity var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out); }
.nav-label { transition: color var(--duration-fast) var(--ease-out); }
.nav-item:hover .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-item.active .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-footer { margin-top: auto; padding: 18px 16px 4px; }
.sidebar-note {
  padding: 14px 2px 0;
  border-top: 1px solid rgba(193, 198, 215, 0.65);
}
.sidebar-note-title {
  margin: 0 0 6px;
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
  width: 36px; height: 36px; border-radius: 50%; padding: 2px;
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
  background: rgba(242, 243, 255, 0.9); color: var(--c-text-muted);
  border: 1px solid rgba(193, 198, 215, 0.55);
  transition: all var(--duration-fast);
}
.footer-toggle:hover { background: rgba(255,255,255,1); color: var(--c-accent-primary); }
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  padding: clamp(32px, 3.5vw, 48px) clamp(24px, 3vw, 40px) clamp(56px, 4vw, 72px);
}
.page-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0;
  scrollbar-gutter: stable;
}
.page-container::-webkit-scrollbar { width: 6px; }
.page-container::-webkit-scrollbar-track { background: transparent; }
.page-container::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
@media (max-width: 1200px) {
  .topbar {
    grid-template-columns: minmax(220px, 0.95fr) minmax(0, 1fr) auto;
    gap: 16px;
  }
  .brand-logo-shell {
    width: 62px;
    height: 62px;
  }
  .brand-logo-image {
    width: 48px;
    height: 48px;
  }
  .brand-text {
    font-size: 20px;
  }
  .sidebar-title {
    font-size: 22px;
  }
}
@media (max-width: 960px) {
  .app-layout {
    grid-template-columns: minmax(220px, 240px) minmax(0, 1fr);
  }
  .main-content {
    padding-inline: 20px;
  }
  .nav-links {
    padding-inline: 10px;
  }
  .sidebar-note-text {
    font-size: 11px;
  }
}
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
    padding-top: 10px;
  }
  .topbar-link {
    font-size: 13px;
    padding: 8px 11px;
  }
  .app-layout {
    grid-template-columns: 1fr;
    min-height: auto;
    gap: 12px;
  }
  .sidebar {
    width: 100%;
    padding-top: 18px;
    border-right: none;
    box-shadow: none;
    border-bottom: 1px solid rgba(193, 198, 215, 0.4);
  }
  .sidebar-head { padding-bottom: 12px; }
  .nav-links {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    padding-bottom: 10px;
  }
  .nav-item {
    min-height: 48px;
  }
  .nav-footer { display: none; }
  .main-content { min-height: 0; }
  .main-content { padding: 0 20px 72px; }
  .page-container { padding: 0; }
}
@media (max-width: 560px) {
  .topbar {
    padding: 14px 16px 12px;
  }
  .brand-lockup {
    gap: 10px;
  }
  .brand-logo-shell {
    width: 54px;
    height: 54px;
    border-radius: 16px;
  }
  .brand-logo-image {
    width: 42px;
    height: 42px;
  }
  .brand-text {
    font-size: 18px;
  }
  .brand-subtitle {
    font-size: 10px;
  }
  .brand-kicker {
    font-size: 8px;
    letter-spacing: 0.14em;
  }
  .topbar-actions {
    gap: 8px;
  }
  .user-chip {
    gap: 10px;
    padding: 7px 10px;
  }
  .user-role {
    display: none;
  }
  .sidebar-head {
    padding-inline: 16px;
  }
  .nav-links {
    grid-template-columns: 1fr;
    padding-inline: 12px;
  }
  .sidebar-summary {
    font-size: 11px;
  }
  .main-content {
    padding-inline: 16px;
  }
}
</style>
