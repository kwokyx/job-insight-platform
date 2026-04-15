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
</script>

<template>
  <div class="app-layout">
    <nav class="sidebar glass-panel">
      <div class="brand">
        <div class="logo-glow"></div>
        <div class="brand-logo-icon">
          <BarChart3 :size="20" stroke-width="2.5" />
        </div>
        <div class="brand-copy">
          <span class="brand-text text-gradient text-gradient-primary">
            <span class="text-bold">职涯</span>OS
          </span>
          <span class="brand-subtitle">职业情报与分析工作区</span>
        </div>
      </div>

      <div class="nav-links">
        <template v-for="(group, gIdx) in navGroups" :key="gIdx">
          <div v-if="group.items.length" class="nav-group-title">{{ group.title }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: route.path === item.path }"
            replace
          >
            <component :is="item.icon" class="nav-icon" :size="20" stroke-width="1.5" />
            <span class="nav-label">{{ item.name }}</span>
          </router-link>
        </template>
      </div>

      <div class="nav-footer">
        <div class="user-status">
          <div class="user-main">
            <div class="avatar-ring">
              <img
                :src="authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username || 'Guest'}`"
                alt="头像"
              />
            </div>
            <div class="user-info">
              <span class="user-name">{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客' }}</span>
              <span v-if="authStore.isLoggedIn" class="user-role">已登录</span>
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
      </div>
    </nav>

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
</template>

<style scoped>
.app-layout { display: flex; height: 100vh; overflow: hidden; padding: 14px; gap: 14px; }
.sidebar {
  width: 230px; flex-shrink: 0; display: flex; flex-direction: column; padding: 14px 8px;
  background: linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface));
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  border-right: 1px solid var(--c-border-glass);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.03);
}
.brand { position: relative; display: flex; align-items: center; gap: 12px; padding: 2px 10px 18px; }
.brand-logo-icon {
  display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 10px;
  background: color-mix(in srgb, var(--c-accent-primary) 16%, var(--c-bg-surface-strong) 84%);
  color: var(--c-accent-primary);
  border: 1px solid color-mix(in srgb, var(--c-accent-primary) 22%, transparent);
}
.logo-glow { display: none; }
.brand-copy { display: flex; flex-direction: column; gap: 1px; position: relative; z-index: 1; }
.brand-text { font-family: var(--font-display); font-size: 18px; font-weight: 700; line-height: 1.1; color: var(--c-text-primary); }
.brand-subtitle { color: var(--c-text-muted); font-size: 11px; letter-spacing: 0.02em; }
.text-bold { font-weight: 800; }
.nav-links { display: flex; flex-direction: column; gap: 0; flex: 1; overflow-y: auto; overflow-x: hidden; padding-bottom: 20px; }
.nav-links::-webkit-scrollbar { width: 4px; }
.nav-links::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.06); border-radius: 4px; }
.nav-group-title { margin: 16px 10px 6px; font-size: 10px; font-weight: 700; color: var(--c-text-muted); text-transform: uppercase; letter-spacing: 0.12em; opacity: 0.78; }
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 10px;
  color: var(--c-text-secondary); font-weight: 700; transition: color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
  position: relative; overflow: hidden; font-size: 13.5px;
}
.nav-item::before {
  content: ''; position: absolute; left: 6px; top: 10px; bottom: 10px; width: 2px; background: var(--c-accent-primary);
  border-radius: 999px; transform: scaleY(0); transition: transform var(--duration-fast) var(--ease-out); transform-origin: center;
}
.nav-item::after {
  content: ''; position: absolute; inset: 0; border-radius: inherit;
  border: 1px solid var(--c-border-glass);
  background: rgba(56, 189, 248, 0.05);
  opacity: 0; transition: opacity var(--duration-fast) var(--ease-out);
  pointer-events: none;
}
.nav-item:hover {
  color: var(--c-text-primary); background: rgba(56, 189, 248, 0.05);
  transform: translateX(1px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03);
}
.nav-item:hover::after { opacity: 1; }
.nav-item.active {
  color: var(--c-text-primary);
  background: color-mix(in srgb, var(--c-accent-primary) 12%, transparent);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
.nav-item.active::before { transform: scaleY(1); }
.nav-item.active::after { opacity: 0; }
.nav-icon { flex-shrink: 0; opacity: 0.78; transition: opacity var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out); }
.nav-label { transition: color var(--duration-fast) var(--ease-out); }
.nav-item:hover .nav-icon { opacity: 0.96; color: var(--c-text-primary); }
.nav-item.active .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-footer { margin-top: auto; padding: 14px 6px 6px; border-top: 1px solid var(--c-border-glass); }
.user-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.user-main { display: flex; align-items: center; gap: 10px; flex: 1; overflow: hidden; }
.avatar-ring {
  width: 32px; height: 32px; border-radius: 50%; padding: 2px;
  background: rgba(56, 189, 248, 0.06);
  border: 1px solid var(--c-border-glass);
}
.avatar-ring img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; background: var(--c-bg-base); }
.user-info { display: flex; flex-direction: column; overflow: hidden; }
.user-name { font-size: 13px; font-weight: 600; color: var(--c-text-primary); white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.user-role { font-size: 11px; color: var(--c-text-muted); opacity: 0.9; }
.login-link { font-size: 11px; color: var(--c-accent-primary); font-weight: 600; text-decoration: none; }
.login-link:hover { text-decoration: underline; }
.footer-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 8px; flex-shrink: 0;
  background: rgba(255,255,255,0.03); color: var(--c-text-muted);
  transition: all var(--duration-fast);
}
.footer-toggle:hover { background: rgba(255,255,255,0.06); color: var(--c-text-primary); }
.main-content { flex: 1; display: flex; flex-direction: column; gap: 0; min-width: 0; padding: 14px; }
.page-container { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 0 4px 60px 0; border-radius: var(--radius-lg); }
.page-container::-webkit-scrollbar { width: 6px; }
.page-container::-webkit-scrollbar-track { background: transparent; }
.page-container::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
@media (max-width: 768px) {
  .app-layout { flex-direction: column; padding: 0; gap: 0; }
  .sidebar {
    position: fixed; bottom: 0; left: 0; right: 0; width: 100%;
    height: calc(72px + env(safe-area-inset-bottom, 0px)); padding: 0 0 env(safe-area-inset-bottom, 0px) 0;
    flex-direction: row; z-index: 1000; background: var(--c-bg-surface-strong);
    backdrop-filter: saturate(180%) blur(28px); -webkit-backdrop-filter: saturate(180%) blur(28px); border-top: none;
    box-shadow: inset 0 1.5px 0 var(--c-border-glass), 0 -8px 24px rgba(0, 0, 0, 0.08); border-radius: 0;
  }
  .brand, .nav-footer { display: none; }
  .nav-links {
    flex-direction: row; justify-content: space-around; align-items: center; width: 100%;
    padding: 0 6px; gap: 0; overflow-x: auto;
  }
  .nav-item { flex-direction: column; padding: 10px 4px 6px; gap: 4px; border-radius: 12px; width: 68px; min-width: 68px; }
  .nav-group-title { display: none; }
  .main-content { height: calc(100vh - 72px - env(safe-area-inset-bottom, 0px)); gap: 0; padding: 0; }
  .page-container { padding: 12px; }
}
</style>
