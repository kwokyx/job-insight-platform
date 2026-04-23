<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Menu, Moon, Sun, X } from 'lucide-vue-next'
import logoUrl from '../logo.png'
import { useAuthStore } from './store/auth'
import { useThemeStore } from './store/theme'
import GlobalToast from './components/common/GlobalToast.vue'
import { getRoleLabel, hasRequiredRole, ROLE } from './utils/role'

const AmbientParticles = defineAsyncComponent(() => import('./components/common/AmbientParticles.vue'))

const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

themeStore.initTheme()

onMounted(() => {
  authStore.syncProfile()
})

const isScrolling = ref(false)
const isMobileMenuOpen = ref(false)
const openDropdownKey = ref('')

let scrollTimer = null
let dropdownCloseTimer = null

const currentRole = computed(() => {
  if (!authStore.isLoggedIn) return '访客'
  return getRoleLabel(authStore.user?.roleType)
})

const accountPath = computed(() => (authStore.isLoggedIn ? '/profile' : '/login'))
const accountHint = computed(() => (authStore.isLoggedIn ? '个人主页' : '点击登录'))
const isFullBleed = computed(() => Boolean(route.meta?.fullBleed))
const showAmbientParticles = computed(() => route.path === '/')

function handlePageScroll() {
  isScrolling.value = true
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    isScrolling.value = false
  }, 900)
}

function isNavActive(itemPath) {
  if (itemPath === '/') return route.path === '/'
  return route.path === itemPath || route.path.startsWith(`${itemPath}/`)
}

function isGroupActive(item) {
  if (!item.children) return false
  return item.children.some((child) => isNavActive(child.path))
}

const navItems = computed(() => {
  const user = authStore.user

  const byRole = (item) => {
    if (item.requiresAuth && !authStore.isLoggedIn) return false
    if (item.allowedRoles?.length && !hasRequiredRole(user, item.allowedRoles)) return false
    return true
  }

  const workbenchChildren = [
    { name: '分析报告', path: '/reports', icon: 'description', requiresAuth: true },
    { name: '智能推荐', path: '/recommend', icon: 'auto_awesome', requiresAuth: true },
    { name: 'AI 助手', path: '/ai', icon: 'smart_toy', requiresAuth: true },
    { name: '课程与供需', path: '/teacher', icon: 'school', requiresAuth: true, allowedRoles: [ROLE.TEACHER, ROLE.ADMIN] },
    { name: '运营面板', path: '/admin', icon: 'admin_panel_settings', requiresAuth: true, allowedRoles: [ROLE.ADMIN] },
    { name: '用户管理', path: '/admin/users', icon: 'group', requiresAuth: true, allowedRoles: [ROLE.ADMIN] },
    { name: '数据采集', path: '/crawler', icon: 'cloud_download', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  ].filter(byRole)

  const apiChildren = [
    { name: 'API 文档', path: '/openapi', icon: 'menu_book', requiresAuth: true, allowedRoles: [ROLE.ADMIN] },
    { name: 'API 控制台', path: '/console', icon: 'terminal', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  ].filter(byRole)

  return [
    { name: '首页', path: '/', icon: 'dashboard' },
    { name: '职位列表', path: '/jobs', icon: 'work' },
    { name: '洞察分析', path: '/insights', icon: 'insights' },
    { name: '工作台', path: workbenchChildren[0]?.path || '/reports', icon: 'workspaces', children: workbenchChildren, key: 'workbench' },
    apiChildren.length
      ? { name: 'API', path: apiChildren[0]?.path || '/openapi', icon: 'api', children: apiChildren, key: 'api' }
      : null
  ].filter(Boolean)
})

function openDropdown(key) {
  if (dropdownCloseTimer) {
    clearTimeout(dropdownCloseTimer)
    dropdownCloseTimer = null
  }
  openDropdownKey.value = key
}

function scheduleCloseDropdown() {
  if (dropdownCloseTimer) clearTimeout(dropdownCloseTimer)
  dropdownCloseTimer = setTimeout(() => {
    openDropdownKey.value = ''
    dropdownCloseTimer = null
  }, 120)
}

function closeDropdownNow() {
  if (dropdownCloseTimer) {
    clearTimeout(dropdownCloseTimer)
    dropdownCloseTimer = null
  }
  openDropdownKey.value = ''
}

function toggleDropdown(key) {
  if (openDropdownKey.value === key) {
    closeDropdownNow()
  } else {
    openDropdown(key)
  }
}

function handleDocumentClick(event) {
  if (openDropdownKey.value) {
    const target = event.target
    if (!(target instanceof Element && target.closest('.nav-dropdown-wrap'))) {
      closeDropdownNow()
    }
  }

  if (isMobileMenuOpen.value) {
    const target = event.target
    if (!(target instanceof Element && target.closest('.mobile-nav-sheet, .mobile-menu-toggle'))) {
      isMobileMenuOpen.value = false
    }
  }
}

function handleDocumentKey(event) {
  if (event.key === 'Escape') {
    closeDropdownNow()
    isMobileMenuOpen.value = false
  }
}

const prefetchMap = {
  '/': () => import('./views/DashboardView.vue'),
  '/jobs': () => import('./views/JobsView.vue'),
  '/insights': () => import('./views/InsightsView.vue'),
  '/reports': () => import('./views/ReportCenterView.vue'),
  '/recommend': () => import('./views/RecommendView.vue'),
  '/ai': () => import('./views/AiView.vue'),
  '/openapi': () => import('./views/openapi/OpenApiShell.vue'),
  '/console': () => import('./views/ConsoleView.vue'),
  '/admin': () => import('./views/AdminView.vue'),
  '/admin/users': () => import('./views/UserManageView.vue'),
  '/teacher': () => import('./views/TeacherView.vue'),
  '/crawler': () => import('./views/DataCollectorView.vue')
}

const prefetched = new Set()

function prefetchRoute(path) {
  if (prefetched.has(path)) return
  const importer = prefetchMap[path]
  if (!importer) return
  prefetched.add(path)
  importer().catch(() => prefetched.delete(path))
}

function prefetchItem(item) {
  if (item.path) prefetchRoute(item.path)
  if (item.children) {
    item.children.forEach((child) => prefetchRoute(child.path))
  }
}

function toggleMobileMenu() {
  isMobileMenuOpen.value = !isMobileMenuOpen.value
}

function closeMobileMenu() {
  isMobileMenuOpen.value = false
}

watch(
  () => route.fullPath,
  () => {
    closeDropdownNow()
    closeMobileMenu()
  }
)

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKey)
})

onBeforeUnmount(() => {
  if (scrollTimer) clearTimeout(scrollTimer)
  if (dropdownCloseTimer) clearTimeout(dropdownCloseTimer)
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKey)
})
</script>

<template>
  <AmbientParticles v-if="showAmbientParticles" />
  <div class="app-shell">
    <GlobalToast />
    <div class="app-layout">
      <header class="topbar glass-panel" aria-label="全局导航">
        <div class="topbar-inner">
          <router-link to="/" class="brand-lockup">
            <div class="brand-logo-shell">
              <img :src="logoUrl" alt="职业能力大数据平台 Logo" class="brand-logo-image" />
            </div>
            <div class="brand-copy">
              <span class="brand-text"><span class="text-bold">职途</span>OS</span>
              <span class="brand-kicker">Job Insight Platform</span>
            </div>
          </router-link>

          <nav class="topbar-nav" aria-label="主导航">
            <template v-for="item in navItems" :key="item.key || item.path">
              <div
                v-if="item.children"
                class="nav-dropdown-wrap"
                :class="{ open: openDropdownKey === item.key }"
                @mouseenter="openDropdown(item.key); prefetchItem(item)"
                @mouseleave="scheduleCloseDropdown"
              >
                <div
                  class="nav-item nav-item-group"
                  :class="{ active: isGroupActive(item) }"
                  :title="item.name"
                >
                  <router-link
                    :to="item.path"
                    class="nav-group-link"
                    @mouseenter="prefetchRoute(item.path)"
                    @focus="openDropdown(item.key); prefetchItem(item)"
                  >
                    <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ item.icon }}</span>
                    <span class="nav-label">{{ item.name }}</span>
                  </router-link>
                  <button
                    type="button"
                    class="nav-caret-button"
                    :aria-expanded="openDropdownKey === item.key"
                    :aria-label="`展开${item.name}菜单`"
                    @click="toggleDropdown(item.key)"
                    @focus="openDropdown(item.key); prefetchItem(item)"
                    @blur="scheduleCloseDropdown"
                  >
                    <span class="material-symbols-outlined nav-caret" aria-hidden="true">expand_more</span>
                  </button>
                </div>
                <div class="nav-dropdown-panel" role="menu" :aria-hidden="openDropdownKey !== item.key">
                  <router-link
                    v-for="child in item.children"
                    :key="child.path"
                    :to="child.path"
                    class="nav-dropdown-item"
                    :class="{ active: isNavActive(child.path) }"
                    role="menuitem"
                    @click="closeDropdownNow"
                    @mouseenter="prefetchRoute(child.path)"
                    @focus="prefetchRoute(child.path)"
                  >
                    <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ child.icon }}</span>
                    <span class="nav-label">{{ child.name }}</span>
                  </router-link>
                </div>
              </div>

              <router-link
                v-else
                :to="item.path"
                class="nav-item"
                :class="{ active: isNavActive(item.path) }"
                :aria-current="isNavActive(item.path) ? 'page' : null"
                :title="item.name"
                @mouseenter="prefetchRoute(item.path)"
                @focus="prefetchRoute(item.path)"
              >
                <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ item.icon }}</span>
                <span class="nav-label">{{ item.name }}</span>
              </router-link>
            </template>
          </nav>

          <div class="topbar-tools">
            <router-link :to="accountPath" class="user-chip account-entry" :title="accountHint">
              <div class="avatar-ring">
                <img v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" alt="头像" />
                <span v-else class="avatar-fallback" aria-hidden="true">
                  {{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username || '?').slice(0, 1).toUpperCase() : '访客' }}
                </span>
              </div>
              <div class="user-info">
                <span class="user-name">{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客' }}</span>
                <span class="user-role">{{ authStore.isLoggedIn ? currentRole : accountHint }}</span>
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

            <button
              class="mobile-menu-toggle"
              :aria-expanded="isMobileMenuOpen"
              aria-label="打开移动端导航"
              @click="toggleMobileMenu"
            >
              <X v-if="isMobileMenuOpen" :size="18" />
              <Menu v-else :size="18" />
            </button>
          </div>
        </div>
      </header>

      <transition name="mobile-nav-fade">
        <div v-if="isMobileMenuOpen" class="mobile-nav-overlay">
          <aside class="mobile-nav-sheet glass-panel">
            <div class="mobile-nav-handle"></div>
            <div class="mobile-nav-head">
              <div>
                <strong>{{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username) : '访客' }}</strong>
                <p>{{ authStore.isLoggedIn ? currentRole : '未登录，可浏览公开模块' }}</p>
              </div>
              <button class="theme-toggle mobile-theme-toggle" @click="themeStore.toggleTheme">
                <Moon v-if="!themeStore.isDark" :size="16" />
                <Sun v-else :size="16" />
              </button>
            </div>

            <nav class="mobile-nav-list" aria-label="移动端导航">
              <template v-for="item in navItems" :key="`mobile-${item.key || item.path}`">
                <router-link
                  v-if="!item.children"
                  :to="item.path"
                  class="mobile-nav-item"
                  :class="{ active: isNavActive(item.path) }"
                  @click="closeMobileMenu"
                >
                  <span class="mobile-nav-item-main">
                    <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ item.icon }}</span>
                    <span>{{ item.name }}</span>
                  </span>
                </router-link>

                <div v-else class="mobile-nav-group">
                  <div class="mobile-nav-group-title">
                    <span class="mobile-nav-item-main">
                      <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ item.icon }}</span>
                      <span>{{ item.name }}</span>
                    </span>
                  </div>
                  <router-link
                    v-for="child in item.children"
                    :key="`mobile-${child.path}`"
                    :to="child.path"
                    class="mobile-nav-subitem"
                    :class="{ active: isNavActive(child.path) }"
                    @click="closeMobileMenu"
                  >
                    <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ child.icon }}</span>
                    <span>{{ child.name }}</span>
                  </router-link>
                </div>
              </template>
            </nav>
          </aside>
        </div>
      </transition>

      <main
        class="main-content"
        :class="{ 'full-bleed': isFullBleed, 'is-scrolling': isScrolling }"
        @scroll.passive="handlePageScroll"
      >
        <div class="page-container" :class="{ 'full-bleed': isFullBleed }">
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
  padding-top: env(safe-area-inset-top, 0);
}

.app-layout {
  height: calc(100dvh - env(safe-area-inset-top, 0px));
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  width: 100%;
  border-radius: 0;
  border-top: none;
  border-left: none;
  border-right: none;
  border-bottom: 1px solid var(--c-topbar-border);
  background: var(--c-topbar-bg);
  box-shadow:
    inset 0 -1px 0 var(--c-glass-panel-highlight),
    0 10px 28px var(--c-topbar-shadow);
  backdrop-filter: blur(26px) saturate(1.35);
  -webkit-backdrop-filter: blur(26px) saturate(1.35);
}

.topbar-inner {
  display: flex;
  align-items: center;
  gap: clamp(16px, 2vw, 28px);
  height: 56px;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 clamp(20px, 2.5vw, 36px);
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
  flex-shrink: 0;
}

.brand-logo-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.brand-logo-image {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1px;
  min-width: 0;
  line-height: 1;
}

.brand-text {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.01em;
  color: var(--c-topbar-brand);
  white-space: nowrap;
}

.brand-kicker {
  font-family: var(--font-sans);
  color: var(--c-topbar-brand-muted);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  white-space: nowrap;
}

.text-bold {
  font-weight: 800;
}

.topbar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow: visible;
  scrollbar-width: none;
}

.topbar-nav::-webkit-scrollbar {
  display: none;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 12px;
  color: var(--c-topbar-nav);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  text-decoration: none;
  background: transparent;
  transition:
    background-color 140ms var(--ease-out),
    color 140ms var(--ease-out),
    box-shadow 180ms var(--ease-out);
}

.nav-icon {
  flex: none;
  line-height: 1;
  color: var(--c-topbar-nav-muted);
  font-size: 20px;
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  transition:
    font-variation-settings 220ms var(--ease-out),
    color 180ms var(--ease-out);
}

.nav-item:hover {
  background: var(--c-topbar-nav-hover);
  color: var(--c-accent-primary);
}

.nav-item:hover .nav-icon,
.nav-item.active .nav-icon {
  color: var(--c-accent-primary);
}

.nav-item.active {
  background: var(--c-topbar-nav-active-bg);
  color: var(--c-accent-primary);
  font-weight: 600;
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.07);
}

.nav-item.active .nav-icon {
  font-variation-settings: 'FILL' 1, 'wght' 500, 'GRAD' 0, 'opsz' 24;
}

.nav-label {
  min-width: 0;
  line-height: 1;
}

.nav-dropdown-wrap {
  position: relative;
  display: inline-flex;
}

.nav-item-group {
  display: inline-flex;
  align-items: center;
  border: none;
  min-width: 144px;
  padding: 0;
  overflow: hidden;
}

.nav-dropdown-wrap.open .nav-item-group {
  background: var(--c-glass-panel-bg);
  color: var(--c-accent-primary);
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  padding-bottom: 9px;
  box-shadow: inset 0 1px 0 var(--c-glass-panel-highlight);
}

.nav-item-group.active {
  background: var(--c-topbar-nav-active-bg);
}

.nav-group-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  padding: 8px 10px 8px 14px;
  color: inherit;
  text-decoration: none;
}

.nav-caret-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: stretch;
  min-width: 30px;
  padding: 0 8px 0 4px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.nav-caret-button:hover {
  color: var(--c-accent-primary);
}

.nav-caret {
  font-size: 16px !important;
  opacity: 0.6;
  transition: transform 180ms var(--ease-out), opacity 140ms var(--ease-out);
}

.nav-dropdown-wrap.open .nav-caret {
  transform: rotate(180deg);
  opacity: 1;
}

.nav-dropdown-panel {
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%) translateY(-4px);
  min-width: 100%;
  width: max-content;
  max-width: 180px;
  padding: 4px;
  background: var(--c-glass-panel-bg);
  backdrop-filter: blur(26px) saturate(1.35);
  -webkit-backdrop-filter: blur(26px) saturate(1.35);
  border: 1px solid var(--c-topbar-border);
  border-top: none;
  border-radius: 0 0 12px 12px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 1px;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition:
    opacity 140ms var(--ease-out),
    transform 140ms var(--ease-out),
    visibility 0s linear 140ms;
  z-index: 60;
}

.nav-dropdown-wrap.open .nav-dropdown-panel {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateX(-50%) translateY(0);
  transition:
    opacity 140ms var(--ease-out),
    transform 140ms var(--ease-out),
    visibility 0s linear 0s;
}

.nav-dropdown-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 9px;
  color: var(--c-topbar-nav);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  text-decoration: none;
  transition:
    background-color 140ms var(--ease-out),
    color 140ms var(--ease-out);
}

.nav-dropdown-item:hover {
  background: var(--c-topbar-nav-hover);
  color: var(--c-accent-primary);
}

.nav-dropdown-item.active {
  background: var(--c-topbar-nav-active-bg);
  color: var(--c-accent-primary);
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(24, 27, 35, 0.04);
}

.nav-dropdown-item.active .nav-icon,
.nav-dropdown-item:hover .nav-icon {
  color: var(--c-accent-primary);
  font-variation-settings: 'FILL' 1, 'wght' 500, 'GRAD' 0, 'opsz' 24;
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 3px 10px 3px 3px;
  border-radius: 999px;
  background: var(--c-chip-bg);
  border: 1px solid var(--c-chip-border);
  box-shadow: inset 0 1px 0 var(--c-chip-highlight);
  backdrop-filter: blur(16px) saturate(1.15);
  -webkit-backdrop-filter: blur(16px) saturate(1.15);
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
  background: var(--c-chip-hover-bg);
  border-color: var(--c-chip-hover-border);
  box-shadow:
    inset 0 1px 0 var(--c-glass-panel-highlight),
    0 8px 20px rgba(15, 23, 42, 0.045);
}

.avatar-ring {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  padding: 2px;
  background: var(--c-avatar-ring-bg);
  border: 2px solid var(--c-avatar-ring-border);
  flex-shrink: 0;
}

.avatar-ring img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: var(--c-bg-base);
}

.avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-primary-hover));
  color: #ffffff;
  font-family: var(--font-serif);
  font-size: 13px;
  font-weight: 700;
}

.user-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  line-height: 1.1;
}

.user-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--c-text-primary);
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.user-role {
  font-size: 9px;
  color: var(--c-text-muted);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.theme-toggle,
.mobile-menu-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  flex-shrink: 0;
  background: var(--c-chip-bg);
  color: var(--c-text-muted);
  border: 1px solid var(--c-chip-border);
  box-shadow: inset 0 1px 0 var(--c-chip-highlight);
  backdrop-filter: blur(16px) saturate(1.15);
  -webkit-backdrop-filter: blur(16px) saturate(1.15);
  transition: all var(--duration-fast);
}

.theme-toggle:hover,
.mobile-menu-toggle:hover {
  background: var(--c-chip-hover-bg);
  color: var(--c-accent-primary);
  border-color: var(--c-chip-hover-border);
}

.mobile-menu-toggle {
  display: none;
}

.mobile-nav-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  background: var(--c-mobile-overlay);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.mobile-nav-sheet {
  width: min(92vw, 360px);
  min-height: 100dvh;
  padding: 18px 16px calc(24px + env(safe-area-inset-bottom, 0px));
  border-radius: 0;
  border-top: none;
  border-right: none;
  border-bottom: none;
  background: var(--c-mobile-drawer-bg);
  border-left: 1px solid var(--c-mobile-drawer-border);
}

.mobile-nav-handle {
  width: 44px;
  height: 4px;
  border-radius: 999px;
  margin: 0 auto 16px;
  background: var(--c-mobile-handle);
}

.mobile-nav-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--c-mobile-drawer-border);
}

.mobile-nav-head strong {
  display: block;
  color: var(--c-text-primary);
  font-size: 15px;
}

.mobile-nav-head p {
  margin-top: 4px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.mobile-nav-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 18px;
}

.mobile-nav-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mobile-nav-item,
.mobile-nav-group-title,
.mobile-nav-subitem {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  color: var(--c-text-secondary);
  text-decoration: none;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.mobile-nav-item-main {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.mobile-nav-group-title {
  color: var(--c-text-primary);
  font-weight: 700;
}

.mobile-nav-subitem {
  margin-left: 12px;
  padding-left: 16px;
  font-size: 13.5px;
}

.mobile-nav-item:hover,
.mobile-nav-subitem:hover {
  background: var(--c-mobile-nav-hover);
  color: var(--c-accent-primary);
}

.mobile-nav-item.active,
.mobile-nav-subitem.active {
  background: var(--c-mobile-nav-active);
  color: var(--c-accent-primary);
}

.mobile-nav-fade-enter-active,
.mobile-nav-fade-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out);
}

.mobile-nav-fade-enter-from,
.mobile-nav-fade-leave-to {
  opacity: 0;
}

.main-content {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  min-height: 0;
  padding: 16px 24px 28px;
  overflow-y: auto;
  overflow-x: hidden;
}

.main-content.full-bleed {
  padding: 0;
  overflow-y: hidden;
}

.page-container {
  flex: 1;
  min-height: 0;
  width: min(100%, 1360px);
  margin: 0 auto;
}

.page-container.full-bleed {
  width: 100%;
  max-width: none;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.main-content::-webkit-scrollbar {
  width: 6px;
  background: transparent;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}

.main-content::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
  transition: background-color 260ms var(--ease-out);
}

.main-content:hover::-webkit-scrollbar-thumb,
.main-content.is-scrolling::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
}

.main-content::-webkit-scrollbar-thumb:hover {
  background: var(--c-text-faint);
}

@media (max-width: 1200px) {
  .brand-kicker {
    display: none;
  }

  .topbar-nav {
    gap: 2px;
  }

  .nav-item {
    padding: 8px 12px;
  }
}

@media (max-width: 1024px) {
  .topbar-nav {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .nav-item {
    padding: 8px 10px;
    gap: 0;
  }

  .nav-item-group {
    min-width: 0;
    padding: 8px 6px 8px 10px;
  }

  .nav-caret {
    margin-left: -2px;
  }

  .nav-label,
  .user-info {
    display: none;
  }

  .user-chip {
    padding: 3px;
  }

  .nav-icon {
    font-size: 22px;
  }
}

@media (max-width: 768px) {
  .topbar-inner {
    height: 52px;
    gap: 10px;
    padding: 0 14px;
  }

  .brand-copy,
  .topbar-nav,
  .account-entry {
    display: none;
  }

  .brand-logo-shell {
    width: 36px;
    height: 36px;
  }

  .brand-logo-image {
    width: 26px;
    height: 26px;
  }

  .topbar-tools {
    margin-left: auto;
  }

  .mobile-menu-toggle {
    display: flex;
  }

  .main-content {
    padding: 16px 14px calc(40px + env(safe-area-inset-bottom, 0px));
  }

  .main-content.full-bleed {
    padding: 0;
  }
}

@media (max-width: 560px) {
  .topbar-inner {
    gap: 6px;
    padding: 0 10px;
  }

  .mobile-nav-sheet {
    width: 100vw;
  }
}

@media (max-width: 380px) {
  .brand-logo-shell {
    display: none;
  }
}
</style>


