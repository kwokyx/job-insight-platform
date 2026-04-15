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
    <nav class="sidebar">
      <div class="brand">
        <div class="brand-logo-icon">
          <BarChart3 :size="20" stroke-width="2.5" />
        </div>
        <div class="brand-copy">
          <span class="brand-text"><span class="text-bold">职涯</span>OS</span>
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
        <div class="user-status glass-panel">
          <div class="user-main">
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
.app-layout { display: flex; min-height: 100vh; overflow: hidden; padding: 20px; gap: 20px; }
.sidebar {
  width: 288px; flex-shrink: 0; display: flex; flex-direction: column; padding: 22px 0 18px;
  background: rgba(250, 251, 255, 0.72);
  backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 32px;
  box-shadow: var(--shadow-glass);
}
.brand { display: flex; align-items: center; gap: 14px; padding: 4px 24px 22px; margin-bottom: 8px; }
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
.nav-links { display: flex; flex-direction: column; gap: 2px; flex: 1; overflow-y: auto; overflow-x: hidden; padding: 0 16px 20px; }
.nav-links::-webkit-scrollbar { width: 4px; }
.nav-links::-webkit-scrollbar-thumb { background: rgba(193, 198, 215, 0.84); border-radius: 4px; }
.nav-group-title { margin: 14px 12px 8px; font-size: 10px; font-weight: 700; color: var(--c-text-faint); text-transform: uppercase; letter-spacing: 0.16em; opacity: 1; }
.nav-item {
  display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: 18px;
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
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.05);
  font-weight: 600;
}
.nav-item.active::before { opacity: 1; }
.nav-icon { flex-shrink: 0; opacity: 0.88; transition: opacity var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out); }
.nav-label { transition: color var(--duration-fast) var(--ease-out); }
.nav-item:hover .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-item.active .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-footer { margin-top: auto; padding: 18px 16px 6px; }
.user-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.user-main { display: flex; align-items: center; gap: 12px; flex: 1; overflow: hidden; }
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
.main-content { flex: 1; display: flex; flex-direction: column; gap: 0; min-width: 0; padding: 0; }
.page-container { flex: 1; overflow-y: auto; overflow-x: hidden; padding: 0 8px 72px 0; border-radius: var(--radius-xl); }
.page-container::-webkit-scrollbar { width: 6px; }
.page-container::-webkit-scrollbar-track { background: transparent; }
.page-container::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
@media (max-width: 768px) {
  .app-layout { flex-direction: column; padding: 0; gap: 0; }
  .sidebar {
    position: fixed; bottom: 0; left: 0; right: 0; width: 100%;
    height: calc(72px + env(safe-area-inset-bottom, 0px)); padding: 0 0 env(safe-area-inset-bottom, 0px) 0;
    flex-direction: row; z-index: 1000; background: rgba(255, 255, 255, 0.84);
    backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px); border: none;
    box-shadow: 0 -12px 40px rgba(24, 27, 35, 0.06); border-radius: 24px 24px 0 0;
  }
  .brand, .nav-footer { display: none; }
  .nav-links {
    flex-direction: row; justify-content: space-around; align-items: center; width: 100%;
    padding: 0 8px; gap: 0; overflow-x: auto;
  }
  .nav-item { flex-direction: column; padding: 10px 6px 6px; gap: 4px; border-radius: 16px; width: 72px; min-width: 72px; }
  .nav-group-title { display: none; }
  .main-content { height: calc(100vh - 72px - env(safe-area-inset-bottom, 0px)); gap: 0; padding: 0; }
  .page-container { padding: 12px; }
}
</style>
