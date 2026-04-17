<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowUpRight,
  BarChart3,
  Briefcase,
  CalendarDays,
  DatabaseZap,
  LayoutDashboard,
  Moon,
  ScrollText,
  Sparkles,
  Sun,
  Webhook,
  Bot
} from 'lucide-vue-next'
import logoUrl from '../logo.png'
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
const accountHint = computed(() => (authStore.isLoggedIn ? '个人资料与登录状态' : '登录后解锁完整工作区'))
const launcherPath = computed(() => (authStore.isLoggedIn ? '/ai' : '/profile?login=true'))

const navGroups = computed(() => [
  {
    title: '总览',
    items: [
      { name: '工作台', path: '/', icon: LayoutDashboard },
      { name: '洞察分析', path: '/insights', icon: BarChart3 }
    ]
  },
  {
    title: '执行',
    items: [
      { name: 'AI 助手', path: '/ai', icon: Bot, requiresAuth: true },
      { name: '分析报告', path: '/reports', icon: ScrollText, requiresAuth: true },
      { name: '智能推荐', path: '/recommend', icon: Sparkles, requiresAuth: true }
    ]
  },
  {
    title: '数据',
    items: [
      { name: '数据采集', path: '/crawler', icon: DatabaseZap, requiresAuth: true },
      { name: '职位列表', path: '/jobs', icon: Briefcase },
      { name: '开放 API', path: '/openapi', icon: Webhook }
    ]
  }
].map((group) => ({
  ...group,
  items: group.items.filter((item) => !item.requiresAuth || authStore.isLoggedIn)
})).filter((group) => group.items.length > 0))

const currentMeta = computed(() => {
  const fallback = {
    kicker: 'Workspace',
    subtitle: '把分析、推荐、报告和数据入口统一收拢到一个工作台。'
  }

  const metaMap = {
    '/': {
      kicker: 'Home',
      subtitle: '从一个任务入口发起洞察、报告和职位分析。'
    },
    '/insights': {
      kicker: 'Insights',
      subtitle: '查看岗位市场热度、城市结构和行业变化。'
    },
    '/ai': {
      kicker: 'AI',
      subtitle: '在同一个上下文里推进提问、分析和任务拆解。'
    },
    '/reports': {
      kicker: 'Reports',
      subtitle: '集中生成、查看和导出职业分析报告。'
    },
    '/recommend': {
      kicker: 'Recommend',
      subtitle: '把画像、技能差距和岗位建议串成行动方案。'
    },
    '/crawler': {
      kicker: 'Collector',
      subtitle: '维护采集任务、同步来源并更新市场样本。'
    },
    '/jobs': {
      kicker: 'Jobs',
      subtitle: '检索职位样本，快速确认城市、薪资和技能要求。'
    },
    '/openapi': {
      kicker: 'API',
      subtitle: '查看平台接口能力与接入说明。'
    },
    '/profile': {
      kicker: 'Profile',
      subtitle: '管理账号、登录状态和个人职业画像。'
    }
  }

  return metaMap[route.path] || fallback
})

const currentDateLabel = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'short'
}).format(new Date()))

const shortcutEntries = computed(() => [
  {
    name: '新建任务',
    desc: '直接进入 AI 工作台组织分析请求。',
    path: launcherPath.value,
    icon: Sparkles
  },
  {
    name: '查看洞察',
    desc: '先看市场热度，再决定下一步动作。',
    path: '/insights',
    icon: BarChart3
  },
  {
    name: '浏览岗位',
    desc: '快速确认职位样本、薪资和地点分布。',
    path: '/jobs',
    icon: Briefcase
  }
])
</script>

<template>
  <div class="app-shell">
    <div class="app-layout">
      <aside class="sidebar" aria-label="全局导航">
        <div class="sidebar-scroll">
          <router-link to="/" class="brand-lockup">
            <div class="brand-mark">
              <img :src="logoUrl" alt="职业能力大数据平台 Logo" class="brand-logo-image" />
            </div>
            <div class="brand-copy">
              <span class="brand-kicker">Job Insight Platform</span>
              <strong class="brand-title">职涯工作台</strong>
              <span class="brand-subtitle">职位、技能、报告统一入口</span>
            </div>
          </router-link>

          <router-link :to="launcherPath" class="launch-button">
            <div class="launch-copy">
              <span class="launch-kicker">Workspace</span>
              <strong>开始新任务</strong>
              <span>进入 AI 工作台</span>
            </div>
            <ArrowUpRight :size="16" />
          </router-link>

          <nav class="sidebar-nav">
            <section
              v-for="group in navGroups"
              :key="group.title"
              class="nav-section"
            >
              <p class="sidebar-section-title">{{ group.title }}</p>
              <router-link
                v-for="item in group.items"
                :key="item.path"
                :to="item.path"
                class="nav-item"
                :class="{ active: route.path === item.path }"
                :aria-current="route.path === item.path ? 'page' : null"
              >
                <component :is="item.icon" class="nav-icon" :size="17" stroke-width="1.8" />
                <span class="nav-label">{{ item.name }}</span>
              </router-link>
            </section>
          </nav>

          <section class="shortcut-panel">
            <p class="sidebar-section-title">快捷入口</p>
            <router-link
              v-for="entry in shortcutEntries"
              :key="entry.name"
              :to="entry.path"
              class="shortcut-item"
            >
              <component :is="entry.icon" :size="16" class="shortcut-icon" />
              <div class="shortcut-copy">
                <strong>{{ entry.name }}</strong>
                <span>{{ entry.desc }}</span>
              </div>
            </router-link>
          </section>

          <div class="sidebar-footer">
            <router-link :to="accountPath" class="account-card">
              <div class="avatar-ring">
                <img
                  :src="authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username || 'Guest'}`"
                  alt="头像"
                />
              </div>
              <div class="account-copy">
                <strong>{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客模式' }}</strong>
                <span>{{ accountHint }}</span>
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
      </aside>

      <main class="main-content">
        <header class="workspace-header">
          <div class="workspace-header-copy">
            <span class="workspace-header-kicker">{{ currentMeta.kicker }}</span>
            <div class="workspace-title-row">
              <h1>{{ route.meta.title || '工作台' }}</h1>
              <span class="workspace-date">
                <CalendarDays :size="14" />
                {{ currentDateLabel }}
              </span>
            </div>
            <p>{{ currentMeta.subtitle }}</p>
          </div>

          <div class="workspace-header-actions">
            <router-link to="/insights" class="header-action">洞察分析</router-link>
            <router-link v-if="authStore.isLoggedIn" to="/reports" class="header-action">分析报告</router-link>
            <router-link :to="launcherPath" class="header-primary">AI 工作台</router-link>
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
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100dvh;
}

.app-layout {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  min-height: 100dvh;
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100dvh;
  border-right: 1px solid var(--c-border-strong);
  background: rgba(247, 246, 239, 0.92);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.sidebar-scroll {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 22px 18px 18px;
  overflow-y: auto;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 14px;
  color: inherit;
  text-decoration: none;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid var(--c-border-strong);
}

.brand-logo-image {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.brand-kicker,
.sidebar-section-title,
.workspace-header-kicker,
.launch-kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-faint);
}

.brand-title {
  font-size: 18px;
  line-height: 1.2;
  color: var(--c-text-primary);
}

.brand-subtitle {
  font-size: 12px;
  color: var(--c-text-muted);
}

.launch-button {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(41, 85, 155, 0.18);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(243, 246, 252, 0.96));
  color: var(--c-text-primary);
  text-decoration: none;
  transition:
    transform var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.launch-button:hover {
  transform: translateY(-1px);
  border-color: rgba(41, 85, 155, 0.28);
  background: #ffffff;
}

.launch-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.launch-copy strong {
  font-size: 15px;
}

.launch-copy span:last-child {
  font-size: 12px;
  color: var(--c-text-muted);
}

.sidebar-nav,
.shortcut-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  color: var(--c-text-secondary);
  text-decoration: none;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.nav-item:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.nav-item.active {
  background: #ffffff;
  border: 1px solid var(--c-border-strong);
  color: var(--c-text-primary);
}

.nav-icon {
  flex: none;
  color: var(--c-text-muted);
}

.nav-item.active .nav-icon,
.nav-item:hover .nav-icon {
  color: var(--c-accent-primary);
}

.nav-label {
  font-size: 14px;
  line-height: 1.2;
}

.shortcut-panel {
  margin-top: auto;
  padding-top: 4px;
}

.shortcut-item {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  color: inherit;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--c-border-glass);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.shortcut-item:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
}

.shortcut-icon {
  flex: none;
  margin-top: 1px;
  color: var(--c-accent-primary);
}

.shortcut-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.shortcut-copy strong {
  font-size: 13px;
  color: var(--c-text-primary);
}

.shortcut-copy span {
  font-size: 12px;
  color: var(--c-text-muted);
  line-height: 1.45;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.account-card {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  color: inherit;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid var(--c-border-glass);
}

.avatar-ring {
  width: 36px;
  height: 36px;
  overflow: hidden;
  border-radius: 999px;
  border: 1px solid var(--c-border-strong);
  background: #ffffff;
  flex-shrink: 0;
}

.avatar-ring img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.account-copy strong {
  font-size: 13px;
  color: var(--c-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.account-copy span {
  font-size: 12px;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.86);
  color: var(--c-text-secondary);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.theme-toggle:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.main-content {
  display: flex;
  min-width: 0;
  min-height: 100dvh;
  flex-direction: column;
  padding: 26px 28px 30px;
}

.workspace-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  width: min(100%, 1320px);
  margin: 0 auto 22px;
}

.workspace-header-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
}

.workspace-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.workspace-title-row h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
}

.workspace-date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  color: var(--c-text-muted);
  font-size: 12px;
}

.workspace-header-copy p {
  max-width: 640px;
  color: var(--c-text-muted);
  font-size: 14px;
}

.workspace-header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.header-action,
.header-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.header-action {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.74);
  color: var(--c-text-secondary);
}

.header-action:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.header-primary {
  background: #161616;
  color: #ffffff;
}

.header-primary:hover {
  background: #2b2b2b;
  color: #ffffff;
}

.page-container {
  width: min(100%, 1320px);
  min-height: 0;
  flex: 1;
  margin: 0 auto;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.page-container::-webkit-scrollbar {
  width: 6px;
}

.page-container::-webkit-scrollbar-track {
  background: transparent;
}

.page-container::-webkit-scrollbar-thumb {
  background: rgba(154, 148, 136, 0.45);
  border-radius: 999px;
}

@media (max-width: 1100px) {
  .app-layout {
    grid-template-columns: 244px minmax(0, 1fr);
  }

  .main-content {
    padding-inline: 22px;
  }
}

@media (max-width: 820px) {
  .app-layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: relative;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--c-border-strong);
  }

  .sidebar-scroll {
    height: auto;
    padding-bottom: 16px;
  }

  .shortcut-panel {
    margin-top: 0;
  }

  .main-content {
    min-height: auto;
    padding: 20px 18px 24px;
  }

  .workspace-header {
    margin-bottom: 18px;
  }
}

@media (max-width: 560px) {
  .brand-lockup {
    align-items: flex-start;
  }

  .brand-mark {
    width: 42px;
    height: 42px;
  }

  .workspace-title-row h1 {
    font-size: 24px;
  }

  .sidebar-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .theme-toggle {
    width: 100%;
  }
}
</style>
