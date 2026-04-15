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
    title: 'Operations',
    items: [
      { name: 'Data Collector', path: '/crawler', icon: DatabaseZap, requiresAuth: true }
    ]
  },
  {
    title: 'Core',
    items: [
      { name: 'Dashboard', path: '/', icon: LayoutDashboard },
      { name: 'Insights', path: '/insights', icon: BarChart3 }
    ]
  },
  {
    title: 'Workspace',
    items: [
      { name: 'Reports', path: '/reports', icon: ScrollText, requiresAuth: true },
      { name: 'Recommendations', path: '/recommend', icon: Sparkles, requiresAuth: true },
      { name: 'AI Assistant', path: '/ai', icon: Bot, requiresAuth: true }
    ]
  },
  {
    title: 'Jobs',
    items: [
      { name: 'Job Board', path: '/jobs', icon: Briefcase }
    ]
  },
  {
    title: 'Ecosystem',
    items: [
      { name: 'Open API', path: '/openapi', icon: Webhook }
    ]
  },
  {
    title: 'Account',
    items: [
      { name: 'Profile', path: '/profile', icon: UserCircle }
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
            <span class="text-bold">Career</span>OS
          </span>
          <span class="brand-subtitle">Career intelligence and analytics workspace</span>
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
                alt="avatar"
              />
            </div>
            <div class="user-info">
              <span class="user-name">{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : 'Guest' }}</span>
              <span v-if="authStore.isLoggedIn" class="user-role">Signed in</span>
              <router-link v-else to="/profile?login=true" class="login-link">Sign in</router-link>
            </div>
          </div>
          <button
            class="footer-toggle"
            :title="themeStore.isDark ? 'Switch to light mode' : 'Switch to dark mode'"
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
  background: var(--c-bg-surface-strong);
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  border-right: 1px solid var(--c-border-strong);
}
.brand { position: relative; display: flex; align-items: center; gap: 10px; padding: 0 10px 16px; }
.brand-logo-icon {
  display: flex; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 10px;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple)); color: white;
  box-shadow: 0 10px 22px rgba(56, 189, 248, 0.2); z-index: 2;
}
.logo-glow { position: absolute; left: 10px; top: 0; width: 34px; height: 34px; background: var(--c-accent-primary); filter: blur(20px); opacity: 0.4; }
.brand-copy { display: flex; flex-direction: column; gap: 1px; position: relative; z-index: 1; }
.brand-text { font-family: var(--font-display); font-size: 20px; font-weight: 700; line-height: 1.1; }
.brand-subtitle { color: var(--c-text-faint); font-size: 12px; }
.text-bold { font-weight: 800; }
.nav-links { display: flex; flex-direction: column; gap: 0; flex: 1; overflow-y: auto; overflow-x: hidden; padding-bottom: 20px; }
.nav-links::-webkit-scrollbar { width: 4px; }
.nav-links::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 4px; }
.nav-group-title { margin: 12px 10px 4px; font-size: 9px; font-weight: 800; color: var(--c-text-faint); text-transform: uppercase; letter-spacing: 0.1em; opacity: 0.5; }
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px;
  color: var(--c-text-secondary); font-weight: 500; transition: all var(--duration-fast) var(--ease-out);
  position: relative; overflow: hidden; font-size: 13.5px;
}
.nav-item::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--c-accent-primary);
  border-radius: 0 4px 4px 0; transform: scaleY(0); transition: transform var(--duration-fast) var(--ease-out); transform-origin: center;
}
.nav-item:hover { color: var(--c-text-primary); background: rgba(255, 255, 255, 0.05); }
.nav-item.active {
  color: var(--c-text-primary); background: linear-gradient(90deg, rgba(56, 189, 248, 0.18), rgba(255, 255, 255, 0.03));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 10px 26px rgba(2, 8, 23, 0.18);
}
.nav-item.active::before { transform: scaleY(0.7); }
.nav-icon { flex-shrink: 0; opacity: 0.8; }
.nav-item.active .nav-icon { opacity: 1; color: var(--c-accent-primary); }
.nav-footer { margin-top: auto; padding: 14px 4px 6px; border-top: 1px solid var(--c-border-glass); }
.user-status { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.user-main { display: flex; align-items: center; gap: 10px; flex: 1; overflow: hidden; }
.avatar-ring {
  width: 32px; height: 32px; border-radius: 50%; padding: 2px;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
}
.avatar-ring img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; background: var(--c-bg-base); }
.user-info { display: flex; flex-direction: column; overflow: hidden; }
.user-name { font-size: 13px; font-weight: 600; color: var(--c-text-primary); white-space: nowrap; text-overflow: ellipsis; overflow: hidden; }
.user-role { font-size: 11px; color: var(--c-accent-teal); opacity: 0.8; }
.login-link { font-size: 11px; color: var(--c-accent-primary); font-weight: 600; text-decoration: none; }
.login-link:hover { text-decoration: underline; }
.footer-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 8px; flex-shrink: 0;
  background: rgba(255,255,255,0.05); color: var(--c-text-muted);
  transition: all var(--duration-fast);
}
.footer-toggle:hover { background: rgba(255,255,255,0.1); color: var(--c-text-primary); transform: rotate(15deg); }
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
