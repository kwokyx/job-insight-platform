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

const accountPath = computed(() => (authStore.isLoggedIn ? '/profile' : '/profile?login=true'))
const accountHint = computed(() => (authStore.isLoggedIn ? '个人主页' : '点击登录'))

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
  }
].map((group) => ({
  ...group,
  items: group.items.filter((item) => !item.requiresAuth || authStore.isLoggedIn)
})).filter((group) => group.items.length > 0))

</script>

<template>
  <AmbientParticles />
  <div class="app-shell">
    <div class="app-layout">
      <aside class="sidebar glass-panel" aria-label="全局导航">
        <div class="sidebar-scroll">
          <div class="sidebar-header">
            <router-link to="/" class="brand-lockup sidebar-brand">
              <div class="brand-logo-shell">
                <img :src="logoUrl" alt="职业能力大数据平台 Logo" class="brand-logo-image" />
              </div>
              <div class="brand-copy">
                <span class="brand-kicker">Job Insight Platform</span>
                <span class="brand-text"><span class="text-bold">职涯</span>OS</span>
                <span class="brand-subtitle">职业能力大数据平台</span>
              </div>
            </router-link>

            <div class="sidebar-tools">
              <router-link :to="accountPath" class="user-chip account-entry">
                <div class="avatar-ring">
                  <img
                    :src="authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username || 'Guest'}`"
                    alt="头像"
                  />
                </div>
                <div class="user-info">
                  <span class="user-name">{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客' }}</span>
                  <span class="user-role">{{ accountHint }}</span>
                </div>
              </router-link>
              <button
                class="theme-toggle"
                :title="themeStore.isDark ? '切换至亮色模式' : '切换至暗色模式'"
                @click="themeStore.toggleTheme"
              >
                <Moon v-if="!themeStore.isDark" :size="16" />
                <Sun v-else :size="16" />
              </button>
            </div>
          </div>

          <section
            v-for="group in navGroups"
            :key="group.title"
            class="nav-section"
            :class="{ active: group.items.some((item) => item.path === route.path) }"
          >
            <p class="nav-group-title">{{ group.title }}</p>
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: route.path === item.path }"
              :aria-current="route.path === item.path ? 'page' : null"
            >
              <component :is="item.icon" class="nav-icon" :size="18" stroke-width="1.7" />
              <span class="nav-label">{{ item.name }}</span>
            </router-link>
          </section>
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
}
.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  color: inherit;
  text-decoration: none;
  min-width: 0;
}
.sidebar-brand {
  width: 100%;
}
.brand-logo-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 249, 252, 0.88)),
    rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(193, 198, 215, 0.44);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    0 8px 20px rgba(24, 27, 35, 0.04);
  flex-shrink: 0;
}
.brand-logo-image {
  width: 44px;
  height: 44px;
  object-fit: contain;
  flex-shrink: 0;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid rgba(193, 198, 215, 0.48);
}
.account-entry {
  color: inherit;
  text-decoration: none;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}
.account-entry:hover {
  background: rgba(255, 255, 255, 0.72);
  border-color: rgba(0, 89, 199, 0.18);
  box-shadow: 0 8px 20px rgba(24, 27, 35, 0.05);
}
.brand-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.brand-kicker {
  color: #7a8497;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  white-space: nowrap;
}
.brand-text {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.04em;
  color: #132540;
  white-space: nowrap;
}
.brand-subtitle {
  color: #5f6b7f;
  font-size: 10px;
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.text-bold { font-weight: 800; }
.app-layout {
  height: 100dvh;
  display: grid;
  grid-template-columns: clamp(220px, 17vw, 248px) minmax(0, 1fr);
  overflow: hidden;
}
.sidebar {
  position: sticky;
  top: 0;
  align-self: start;
  min-width: 0;
  height: 100dvh;
  border-radius: 0;
  border-bottom: none;
  border-left: none;
  border-right: 1px solid rgba(180, 191, 214, 0.52);
  background:
    radial-gradient(circle at 18% 10%, rgba(30, 64, 175, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(245, 248, 253, 0.86));
  box-shadow: 8px 0 24px rgba(24, 27, 35, 0.035);
}
.sidebar-scroll {
  height: 100%;
  overflow-y: auto;
  scrollbar-width: none;
  padding: 26px 16px 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overscroll-behavior: contain;
}
.sidebar-scroll::-webkit-scrollbar { display: none; }
.sidebar-header {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 0 10px 16px;
  margin-bottom: 2px;
  border-bottom: 1px solid rgba(193, 198, 215, 0.52);
}
.sidebar-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.nav-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
}
.nav-section + .nav-section {
  padding-top: 8px;
}
.nav-section.active .nav-group-title {
  color: #3557a4;
}
.nav-group-title {
  margin: 0;
  padding: 0 12px 5px;
  color: var(--c-text-faint);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
}
.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  position: relative;
  padding: 11px 13px 11px 16px;
  border-radius: 11px;
  color: #4a5568;
  font-size: 13.5px;
  font-weight: 500;
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}
.nav-item::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  border-radius: 999px;
  background: rgba(30, 64, 175, 0.86);
  opacity: 0;
  transform: scaleY(0.35);
  transition:
    opacity var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}
.nav-item:hover {
  color: #23438a;
  background: rgba(30, 64, 175, 0.04);
  transform: translateX(2px);
}
.nav-item.active {
  color: #183775;
  background:
    linear-gradient(90deg, rgba(30, 64, 175, 0.13), rgba(255, 255, 255, 0.84) 68%);
  box-shadow: inset 0 0 0 1px rgba(30, 64, 175, 0.08);
}
.nav-item:hover::before,
.nav-item.active::before {
  opacity: 1;
  transform: scaleY(1);
}
.nav-icon {
  flex: none;
  opacity: 0.82;
  color: #6b7280;
  transition:
    opacity var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}
.nav-item:hover .nav-icon,
.nav-item.active .nav-icon {
  opacity: 1;
  color: #23438a;
  transform: translateX(1px);
}
.nav-label {
  min-width: 0;
  line-height: 1.25;
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
.theme-toggle {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 999px; flex-shrink: 0;
  background: rgba(242, 243, 255, 0.9); color: var(--c-text-muted);
  border: 1px solid rgba(193, 198, 215, 0.55);
  transition: all var(--duration-fast);
}
.theme-toggle:hover { background: rgba(255,255,255,1); color: var(--c-accent-primary); }
.main-content {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  min-height: 0;
  height: 100dvh;
  padding: clamp(32px, 3.5vw, 48px) clamp(24px, 3vw, 40px) clamp(56px, 4vw, 72px);
}
.page-container {
  flex: 1;
  min-height: 0;
  width: min(100%, 1360px);
  margin: 0 auto;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0;
  scrollbar-gutter: stable;
}
.page-container::-webkit-scrollbar { width: 6px; }
.page-container::-webkit-scrollbar-track { background: transparent; }
.page-container::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
@media (max-width: 1200px) {
  .brand-logo-shell {
    width: 54px;
    height: 54px;
  }
  .brand-logo-image {
    width: 40px;
    height: 40px;
  }
  .brand-text {
    font-size: 20px;
  }
}
@media (max-width: 960px) {
  .app-layout {
    grid-template-columns: clamp(196px, 24vw, 228px) minmax(0, 1fr);
  }
  .sidebar-scroll {
    padding-inline: 14px;
  }
  .nav-group-title {
    font-size: 9px;
  }
  .nav-item {
    padding-right: 11px;
    font-size: 13px;
  }
  .main-content {
    padding-inline: 20px;
  }
}
@media (max-width: 768px) {
  .app-layout {
    grid-template-columns: 1fr;
    min-height: auto;
    height: auto;
    overflow: visible;
  }
  .sidebar {
    position: relative;
    height: auto;
    border-right: none;
    border-bottom: 1px solid rgba(193, 198, 215, 0.4);
    box-shadow: none;
  }
  .sidebar-scroll {
    gap: 16px;
    padding: 18px 20px 16px;
  }
  .sidebar-header {
    padding-inline: 0;
  }
  .sidebar-tools {
    justify-content: space-between;
  }
  .nav-section + .nav-section {
    padding-top: 0;
  }
  .main-content {
    height: auto;
    padding: 18px 20px 72px;
  }
  .page-container { padding: 0; }
}
@media (max-width: 560px) {
  .brand-lockup {
    gap: 10px;
  }
  .brand-logo-shell {
    width: 50px;
    height: 50px;
    border-radius: 16px;
  }
  .brand-logo-image {
    width: 38px;
    height: 38px;
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
  .user-chip {
    gap: 10px;
    padding: 7px 10px;
  }
  .sidebar-scroll {
    padding-inline: 16px;
  }
  .sidebar-tools {
    align-items: stretch;
  }
  .nav-item {
    padding-left: 15px;
  }
  .main-content {
    padding-inline: 16px;
  }
}
</style>
