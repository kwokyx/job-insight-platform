<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Moon, Sun } from 'lucide-vue-next'
import logoUrl from '../logo.png'
// Ambient particles pull in Three.js (~125 KB gzip). Load them lazily
// so the main bundle / first paint isn't blocked — the particle layer
// fades in once the chunk arrives.
const AmbientParticles = defineAsyncComponent(() =>
  import('./components/common/AmbientParticles.vue')
)
import { useAuthStore } from './store/auth'
import { useThemeStore } from './store/theme'
import GlobalToast from './components/common/GlobalToast.vue'
import { getRoleLabel, hasRequiredRole, ROLE } from './utils/role'

const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()

themeStore.initTheme()

onMounted(() => {
  authStore.syncProfile()
})

const isScrolling = ref(false)
let scrollTimer = null
function handlePageScroll() {
  isScrolling.value = true
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    isScrolling.value = false
  }, 900)
}
onBeforeUnmount(() => {
  if (scrollTimer) clearTimeout(scrollTimer)
})

const currentRole = computed(() => {
  if (!authStore.isLoggedIn) return '游客'
  return getRoleLabel(authStore.user?.roleType)
})

const accountPath = computed(() => (authStore.isLoggedIn ? '/profile' : '/profile?login=true'))
const accountHint = computed(() => (authStore.isLoggedIn ? '个人主页' : '点击登录'))
const isFullBleed = computed(() => Boolean(route.meta?.fullBleed))
// Only mount the Three.js particle layer on the dashboard home page
// so the animation (and the ~125 KB three chunk being active in
// memory) is scoped to where it actually earns its keep.
const showAmbientParticles = computed(() => route.path === '/')

function isNavActive(itemPath) {
  if (itemPath === '/') return route.path === '/'
  return route.path === itemPath || route.path.startsWith(itemPath + '/')
}
// Parent nav items with a `children` list are active if any of their
// children is the current route.
function isGroupActive(item) {
  if (!item.children) return false
  return item.children.some((child) => isNavActive(child.path))
}

const navItems = computed(() => {
  // Order intent: entry → browse → analyze → personal workspace → developer.
  // 数据采集 is login-gated operator tooling, so it lives inside 工作台
  // alongside the other login-gated productivity tools instead of taking up
  // a top-level slot that's invisible to anonymous visitors.
  // Role-aware items (教师/管理员) are merged into the existing dropdown
  // groups so the wt topbar layout stays compact. Each child declares its
  // own `allowedRoles` and is filtered per the logged-in user.
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
    workbenchChildren.length
      ? {
          name: '工作台',
          icon: 'workspaces',
          children: workbenchChildren,
          key: 'workbench'
        }
      : null,
    apiChildren.length
      ? {
          name: 'API',
          icon: 'api',
          children: apiChildren,
          key: 'api'
        }
      : null
  ].filter((item) => {
    if (!item) return false
    if (item.requiresAuth && !authStore.isLoggedIn) return false
    return true
  })
})

// Dropdown state: which group is currently open, and a delayed close
// timer so the user can move from the button to the panel without the
// menu snapping shut.
//
// Desktop: hover opens, mouseleave schedules a close (120 ms grace so the
// pointer can traverse the gap into the panel).
// Touch / keyboard: click toggles, document-level listener closes on
// outside tap or Escape. The hover path still works on hybrid devices.
const openDropdownKey = ref('')
let dropdownCloseTimer = null
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
function handleDocumentClick(e) {
  if (!openDropdownKey.value) return
  const target = e.target
  if (target instanceof Element && target.closest('.nav-dropdown-wrap')) return
  closeDropdownNow()
}
function handleDocumentKey(e) {
  if (e.key === 'Escape' && openDropdownKey.value) {
    closeDropdownNow()
  }
}
onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKey)
})

// Prefetch target route chunks on hover / focus, so by the time the user
// actually clicks, the JS is already downloaded and the page opens
// instantly. Each import() is de-duplicated by the browser/Vite cache, so
// calling it twice is free — we still track `prefetched` to avoid
// re-running the import function itself.
const prefetchMap = {
  '/': () => import('./views/DashboardView.vue'),
  '/insights': () => import('./views/InsightsView.vue'),
  '/jobs': () => import('./views/JobsView.vue'),
  '/reports': () => import('./views/ReportCenterView.vue'),
  '/recommend': () => import('./views/RecommendView.vue'),
  '/ai': () => import('./views/AiView.vue'),
  '/crawler': () => import('./views/DataCollectorView.vue'),
  '/openapi': () => import('./views/openapi/OpenApiShell.vue'),
  '/console': () => import('./views/ConsoleView.vue')
}
const prefetched = new Set()
function prefetchRoute(path) {
  if (prefetched.has(path)) return
  const fn = prefetchMap[path]
  if (!fn) return
  prefetched.add(path)
  fn().catch(() => prefetched.delete(path))
}
function prefetchItem(item) {
  if (!item) return
  if (item.path) prefetchRoute(item.path)
  if (item.children) {
    for (const c of item.children) prefetchRoute(c.path)
  }
}

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
              <span class="brand-text"><span class="text-bold">职涯</span>OS</span>
              <span class="brand-kicker">Job Insight Platform</span>
            </div>
          </router-link>

          <nav class="topbar-nav" aria-label="主导航">
            <template v-for="item in navItems" :key="item.key || item.path">
              <!-- Dropdown parent (has children) -->
              <div
                v-if="item.children"
                class="nav-dropdown-wrap"
                :class="{ open: openDropdownKey === item.key }"
                @mouseenter="openDropdown(item.key); prefetchItem(item)"
                @mouseleave="scheduleCloseDropdown"
              >
                <button
                  type="button"
                  class="nav-item nav-item-group"
                  :class="{ active: isGroupActive(item) }"
                  :aria-expanded="openDropdownKey === item.key"
                  :aria-haspopup="true"
                  :title="item.name"
                  @click="toggleDropdown(item.key)"
                  @focus="openDropdown(item.key); prefetchItem(item)"
                  @blur="scheduleCloseDropdown"
                >
                  <span class="material-symbols-outlined nav-icon" aria-hidden="true">{{ item.icon }}</span>
                  <span class="nav-label">{{ item.name }}</span>
                  <span class="material-symbols-outlined nav-caret" aria-hidden="true">expand_more</span>
                </button>
                <div
                  class="nav-dropdown-panel"
                  role="menu"
                  :aria-hidden="openDropdownKey !== item.key"
                >
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

              <!-- Regular leaf item -->
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
                  {{ authStore.isLoggedIn ? (authStore.user?.nickname || authStore.user?.username || '?').slice(0, 1).toUpperCase() : '访' }}
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
          </div>
        </div>
      </header>

      <main
        class="main-content"
        :class="{ 'full-bleed': isFullBleed, 'is-scrolling': isScrolling }"
        @scroll.passive="handlePageScroll"
      >
        <div
          class="page-container"
          :class="{ 'full-bleed': isFullBleed }"
        >
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
  /* Respect iOS / Android notches + home indicator so content never
     slides under the system UI. Only padding-top is applied at the
     shell level because the bottom inset is consumed per-component
     (scroll container, modals). */
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
  border-bottom: 1px solid rgba(255, 255, 255, 0.58);
  background: rgba(244, 247, 252, 0.72);
  box-shadow:
    inset 0 -1px 0 rgba(255, 255, 255, 0.34),
    0 10px 28px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(26px) saturate(1.35);
  -webkit-backdrop-filter: blur(26px) saturate(1.35);
}
/* Dark-mode overrides: the light topbar uses bespoke tinted-glass
   values that don't map 1:1 to any token, so we mirror the glass
   effect with a translucent variant of the dark base. */
[data-theme="dark"] .topbar {
  border-bottom-color: var(--c-border-glass);
  background: rgba(22, 25, 34, 0.72);
  box-shadow:
    inset 0 -1px 0 rgba(255, 255, 255, 0.04),
    var(--shadow-panel);
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
  background: transparent;
  flex-shrink: 0;
}
.brand-logo-image {
  width: 30px;
  height: 30px;
  object-fit: contain;
  flex-shrink: 0;
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
  color: var(--c-text-primary);
  white-space: nowrap;
}
.brand-kicker {
  font-family: var(--font-sans);
  color: var(--c-text-muted);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  white-space: nowrap;
}
.text-bold { font-weight: 800; }

.topbar-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  /* Do NOT clip overflow here on desktop — the workbench dropdown
     panel is an absolutely-positioned descendant, and `overflow-x: auto`
     forces `overflow-y: auto` per CSS spec, which would clip the panel
     below the 56px nav row. On narrow screens we restore horizontal
     scroll to keep the nav usable (see @media below). */
  overflow: visible;
  scrollbar-width: none;
}
.topbar-nav::-webkit-scrollbar { display: none; }

.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 12px;
  color: var(--c-text-secondary);
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
  color: var(--c-text-muted);
  font-size: 20px;
  font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
  transition:
    font-variation-settings 220ms var(--ease-out),
    color 180ms var(--ease-out);
}
.nav-item:hover {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.nav-item:hover .nav-icon {
  color: var(--c-accent-primary);
}
.nav-item.active {
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  font-weight: 600;
  box-shadow: var(--shadow-card-quiet);
}
.nav-item.active .nav-icon {
  color: var(--c-accent-primary);
  font-variation-settings: 'FILL' 1, 'wght' 500, 'GRAD' 0, 'opsz' 24;
}
.nav-label {
  min-width: 0;
  line-height: 1;
}

/* Grouped / dropdown nav item */
.nav-dropdown-wrap {
  position: relative;
  display: inline-flex;
}
.nav-item-group {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 10px 8px 14px;
  /* Keep the button wide enough to match the widest child label in
     the dropdown (currently "API 控制台" ≈ 140px including icon and
     padding), so the pill extends straight down instead of flaring
     wider at the bottom. */
  min-width: 144px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 140ms var(--ease-out),
    color 140ms var(--ease-out);
}
.nav-item-group:hover {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
/* Open state: button becomes the top of a single tall pill — same
   frosted background as the panel, square bottom corners so it
   visually continues into the dropdown below. */
.nav-dropdown-wrap.open .nav-item-group {
  background: rgba(255, 255, 255, 0.82);
  color: var(--c-accent-primary);
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  /* Nudge the bottom edge 1px into the panel so there's no hairline
     of page background showing through at the seam. */
  padding-bottom: 9px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
}
[data-theme="dark"] .nav-dropdown-wrap.open .nav-item-group {
  background: rgba(29, 33, 44, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
.nav-item-group.active {
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  font-weight: 600;
  box-shadow: var(--shadow-card-quiet);
}
.nav-dropdown-wrap.open .nav-item-group.active {
  /* Keep the active-route look even while open, but still square the
     bottom corners to meet the panel. */
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
  box-shadow: var(--shadow-card-quiet), inset 0 1px 0 rgba(255, 255, 255, 0.5);
}
[data-theme="dark"] .nav-dropdown-wrap.open .nav-item-group.active {
  box-shadow: var(--shadow-card-quiet), inset 0 1px 0 rgba(255, 255, 255, 0.04);
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
  /* Panel width follows the button width so the pill looks like a
     straight vertical extension (not a wide drawer under a narrow
     button). Item labels are compact enough to still fit. */
  min-width: 100%;
  width: max-content;
  max-width: 180px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(26px) saturate(1.35);
  -webkit-backdrop-filter: blur(26px) saturate(1.35);
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-top: none;
  border-radius: 0 0 12px 12px;
  box-shadow: var(--shadow-panel);
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
/* Dark-mode dropdown panel glass. Keeps the same frosted look but with
   a translucent dark base so it doesn't wash out on #161922. */
[data-theme="dark"] .nav-dropdown-panel {
  background: rgba(29, 33, 44, 0.92);
  border-color: var(--c-border-glass);
}

/* Dropdown items reuse .nav-icon and .nav-label from the top-nav
   items so the typography (14 / 500), icon size (20 + FILL 0 wght
   400), and color hierarchy all match the trigger above. */
.nav-dropdown-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 9px;
  color: var(--c-text-secondary);
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
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.nav-dropdown-item.active {
  background: var(--c-bg-surface-strong);
  color: var(--c-accent-primary);
  font-weight: 600;
  box-shadow: var(--shadow-card-quiet);
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
  background: rgba(255, 255, 255, 0.34);
  border: 1px solid rgba(255, 255, 255, 0.52);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.46);
  backdrop-filter: blur(16px) saturate(1.15);
  -webkit-backdrop-filter: blur(16px) saturate(1.15);
}
/* Dark override: white glass + white inner highlight bake to grey on
   dark topbar. Use a translucent dark surface + subtle border token. */
[data-theme="dark"] .user-chip {
  background: rgba(49, 54, 68, 0.5);
  border-color: var(--c-border-glass);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
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
  background: rgba(0, 122, 255, 0.09);
  border-color: rgba(0, 122, 255, 0.22);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.56),
    0 8px 20px rgba(15, 23, 42, 0.045);
}
[data-theme="dark"] .account-entry:hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.04),
    var(--shadow-card-quiet);
}
.account-entry:hover .user-name,
.account-entry:hover .user-role {
  color: var(--c-accent-primary);
}
.avatar-ring {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  padding: 2px;
  background: rgba(217, 226, 255, 1);
  border: 2px solid rgba(0, 110, 242, 0.18);
  flex-shrink: 0;
}
[data-theme="dark"] .avatar-ring {
  background: rgba(49, 54, 68, 0.9);
  border-color: var(--c-border-glass-hover);
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
  line-height: 1;
}
/* Dark accent is pale lavender → white initial disappears. Use a dark
   ink tone so the initial stays legible. */
[data-theme="dark"] .avatar-fallback {
  color: #0f1420;
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
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.34);
  color: var(--c-text-muted);
  border: 1px solid rgba(255, 255, 255, 0.54);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.48);
  backdrop-filter: blur(16px) saturate(1.15);
  -webkit-backdrop-filter: blur(16px) saturate(1.15);
  transition: all var(--duration-fast);
}
[data-theme="dark"] .theme-toggle {
  background: rgba(49, 54, 68, 0.5);
  border-color: var(--c-border-glass);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
.theme-toggle:hover {
  background: rgba(0, 122, 255, 0.1);
  color: var(--c-accent-primary);
  border-color: rgba(0, 122, 255, 0.22);
}
[data-theme="dark"] .theme-toggle:hover {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  border-color: var(--c-border-glass-hover);
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
  padding: 0;
}
.page-container.full-bleed {
  width: 100%;
  max-width: none;
  height: 100%;
  display: flex;
  flex-direction: column;
}
/* Scrollbar is on .main-content now so it docks to the viewport's right edge */
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
  .brand-kicker { display: none; }
  .topbar-nav { gap: 2px; }
  .nav-item { padding: 8px 12px; }
}
@media (max-width: 1024px) {
  .topbar-nav {
    justify-content: flex-start;
    /* Narrow screens may overflow horizontally; allow scroll there.
       The dropdown still works because on touch the menu is summoned
       by tap/focus rather than hover. */
    overflow-x: auto;
  }
  .nav-item { padding: 8px 10px; gap: 0; }
  .nav-item-group { padding: 8px 6px 8px 10px; min-width: 0; }
  .nav-caret { margin-left: -2px; }
  .nav-label { display: none; }
  .user-info { display: none; }
  .user-chip {
    padding: 3px;
    border-radius: 999px;
  }
  /* On touch devices, icon-only dropdown triggers need a larger hit
     area; bump padding to hit the 44px target recommendation. */
  .nav-icon { font-size: 22px; }
}
@media (max-width: 768px) {
  .app-layout {
    grid-template-rows: auto minmax(0, 1fr);
  }
  .topbar-inner {
    height: 52px;
    gap: 10px;
    padding: 0 14px;
  }
  .brand-copy { display: none; }
  .brand-logo-shell {
    width: 36px;
    height: 36px;
    border-radius: 11px;
  }
  .brand-logo-image {
    width: 26px;
    height: 26px;
  }
  .nav-item { padding: 8px 9px; }
  .main-content {
    /* Bottom padding uses safe-area so the last card doesn't sit
       underneath the iOS home indicator. */
    padding: 16px 14px calc(40px + env(safe-area-inset-bottom, 0px));
  }
  .main-content.full-bleed {
    padding: 0;
  }
  /* Dropdown panels: align to the button's left edge instead of
     centering, so a narrow trigger near the right side of a 375px
     viewport doesn't push the panel off-screen. */
  .nav-dropdown-panel {
    left: 0;
    transform: translateX(0) translateY(-4px);
    max-width: min(220px, calc(100vw - 24px));
  }
  .nav-dropdown-wrap.open .nav-dropdown-panel {
    transform: translateX(0) translateY(0);
  }
  /* Last dropdown hugs the right edge instead of overflowing viewport */
  .nav-dropdown-wrap:last-of-type .nav-dropdown-panel {
    left: auto;
    right: 0;
  }
}
@media (max-width: 560px) {
  .topbar-inner {
    gap: 6px;
    padding: 0 10px;
  }
  .topbar-nav { gap: 0; }
  .nav-item { padding: 8px 6px; }
  .theme-toggle {
    width: 34px;
    height: 34px;
  }
  .avatar-ring {
    width: 28px;
    height: 28px;
  }
}
@media (max-width: 380px) {
  /* Extreme narrow: drop the brand logo, keep nav + avatar */
  .brand-logo-shell { display: none; }
}
</style>
