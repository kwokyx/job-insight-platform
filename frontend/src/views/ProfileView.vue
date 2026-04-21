<script setup>
// ProfileView 仅服务"已登录"用户：账号资料、密码修改、岗位订阅、收藏管理。
// 登录 / 注册 / 找回密码已迁移到独立路由 /login —— 未登录访问本页时直接跳过去。
//
// 布局：左侧固定侧边栏（用户卡 + 模块导航），右侧内容区按 activeSection 渲染对应模块。
// 模块较多时不再平铺成网格，避免视觉混乱；窄屏降级为单列 + 顶部横向标签。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import DefaultAvatarIcon from '../components/common/DefaultAvatarIcon.vue'
import EmptyState from '../components/common/EmptyState.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import {
  changeAuthPassword,
  fetchAuthProfile,
  normalizeError,
  updateAuthProfile,
  createSubscription,
  fetchSubscriptions,
  deleteSubscription,
  fetchFavorites,
  removeFavorite,
  invalidateApiCache
} from '../api'
import {
  Lock,
  LogOut,
  Mail,
  RefreshCcw,
  Settings,
  Shield,
  Sparkles,
  User,
  UserRound,
  BellRing,
  Trash2,
  Heart,
  MapPin,
  Building2,
  ArrowRight,
  LayoutDashboard
} from 'lucide-vue-next'
import { useToast } from '../composables/useToast'
import { getRoleLabel } from '../utils/role'

const router = useRouter()
const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(false)

// —— 未登录用户 → 走独立的 /login 页面 ——
// 用 replace 而不是 push，避免回退键又把用户拽回 /profile 造成死循环。
if (!authStore.isLoggedIn) {
  router.replace({ path: '/login', query: { redirect: '/profile' } })
}

// —— 已登录面板 ——
const profile = ref(null)
const profileForm = ref({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: ''
})
const passwordForm = ref({
  oldPassword: '',
  newPassword: ''
})

const subscriptions = ref([])
const subForm = ref({
  city: '',
  industry: '',
  keyword: '',
  salaryMin: ''
})
const subLoading = ref(false)

const roleLabel = computed(() => {
  const roleType = profile.value?.roleType ?? authStore.user?.roleType
  return getRoleLabel(roleType)
})

const accountFacts = computed(() => [
  { label: '角色', value: roleLabel.value },
  { label: '邮箱', value: profile.value?.email || authStore.user?.email || '未设置' },
  { label: '手机号', value: profile.value?.phone || '未设置' },
  { label: '头像', value: profile.value?.avatarUrl ? '已配置' : '未配置' }
])

// —— 左侧导航 ——
// 所有模块集中声明，后续加/删模块只动这里 + 右侧对应的 <section>
const sections = [
  { key: 'overview', label: '账户概览', icon: LayoutDashboard },
  { key: 'profile', label: '资料编辑', icon: UserRound },
  { key: 'password', label: '密码与安全', icon: Lock },
  { key: 'subscriptions', label: '岗位订阅', icon: BellRing },
  { key: 'favorites', label: '我的收藏', icon: Heart }
]
const activeSection = ref('overview')

// favorites 分页展示时计数；导航右侧小徽章用
const favoritesBadge = computed(() => (favoritesTotal.value > 0 ? favoritesTotal.value : ''))
const subsBadge = computed(() => (subscriptions.value.length > 0 ? subscriptions.value.length : ''))

function sectionBadge(key) {
  if (key === 'favorites') return favoritesBadge.value
  if (key === 'subscriptions') return subsBadge.value
  return ''
}

async function loadProfile() {
  if (!authStore.isLoggedIn) return
  try {
    profile.value = await fetchAuthProfile(authStore.token)
    authStore.setAuth(authStore.token, {
      ...(authStore.user || {}),
      ...profile.value
    })
    profileForm.value = {
      nickname: profile.value.nickname || '',
      email: profile.value.email || '',
      phone: profile.value.phone || '',
      avatarUrl: profile.value.avatarUrl || ''
    }
  } catch (e) {
    error(normalizeError(e))
  }
}

async function loadSubscriptions() {
  if (!authStore.isLoggedIn) return
  try {
    const res = await fetchSubscriptions(authStore.token)
    subscriptions.value = res?.data || []
  } catch (e) {
    console.error('Failed to load subscriptions', e)
  }
}

// —— 我的收藏 ——
// fetchFavorites 返回 { data, total, page, pageSize }
// data 每条后端结构参考 FavoriteController：含 jobId 以及冗余的岗位基础字段
// （title / companyName / city / salaryText 等），可直接渲染列表。
const favorites = ref([])
const favoritesLoading = ref(false)
const favoritesError = ref('')
const favoritesTotal = ref(0)
const favoritesPage = ref(1)
const favoritesPageSize = 20
const favoriteRemovingId = ref(null)

async function loadFavorites() {
  if (!authStore.isLoggedIn) return
  favoritesLoading.value = true
  favoritesError.value = ''
  try {
    const res = await fetchFavorites(authStore.token, {
      page: favoritesPage.value,
      pageSize: favoritesPageSize
    })
    favorites.value = res?.data || []
    favoritesTotal.value = res?.total || 0
  } catch (e) {
    favoritesError.value = normalizeError(e)
    favorites.value = []
  } finally {
    favoritesLoading.value = false
  }
}

// 收藏列表点开一条 → 跳 /jobs?jobId=xxx 触发详情弹窗
// （JobsView 的 route watcher 同时接受 jobId / open 两种 query，deeplink 可复用）
function openFavoriteJob(fav) {
  const id = fav?.jobId ?? fav?.id
  if (!id) return
  router.push({ path: '/jobs', query: { jobId: id } })
}

async function handleRemoveFavorite(fav) {
  const id = fav?.jobId ?? fav?.id
  if (!id || favoriteRemovingId.value) return
  favoriteRemovingId.value = id
  try {
    await removeFavorite(authStore.token, id)
    // 从本地列表里摘掉，避免整页闪烁
    favorites.value = favorites.value.filter((item) => (item.jobId ?? item.id) !== id)
    favoritesTotal.value = Math.max(0, favoritesTotal.value - 1)
    // 让 JobsView 里 JobCard 重新拉一次 check（缓存带 Bearer 的 path）
    invalidateApiCache('/favorites')
    success('已取消收藏')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    favoriteRemovingId.value = null
  }
}

async function handleAddSubscription() {
  if (subLoading.value) return
  subLoading.value = true
  try {
    const filterConfig = JSON.stringify({
      city: subForm.value.city,
      industry: subForm.value.industry,
      keyword: subForm.value.keyword,
      salaryMin: subForm.value.salaryMin ? Number(subForm.value.salaryMin) : null
    })
    await createSubscription(authStore.token, { filterConfig })
    success('岗位订阅配置成功，明天早上 9 点将为您推送。')
    subForm.value = { city: '', industry: '', keyword: '', salaryMin: '' }
    await loadSubscriptions()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    subLoading.value = false
  }
}

async function handleDeleteSubscription(id) {
  try {
    await deleteSubscription(authStore.token, id)
    success('订阅已删除。')
    await loadSubscriptions()
  } catch (e) {
    error(normalizeError(e))
  }
}

async function saveProfile() {
  loading.value = true
  try {
    await updateAuthProfile(authStore.token, profileForm.value)
    // TODO: 后端除 PUT /auth/profile（账号基础资料）外还有 PUT /profile（职业画像）。
    // 当前 ProfileView 仅使用账号资料；职业画像相关字段若要落盘，需再调 api.updateProfile。
    // 等 UI 梳理清「账号资料 vs 职业画像」后再接入，避免重复提交造成歧义。
    success('个人信息更新成功。')
    await loadProfile()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    loading.value = false
  }
}

async function savePassword() {
  loading.value = true
  try {
    await changeAuthPassword(authStore.token, passwordForm.value)
    passwordForm.value.oldPassword = ''
    passwordForm.value.newPassword = ''
    success('密码修改成功。')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    loading.value = false
  }
}

function logoutNow() {
  authStore.logout()
  profile.value = null
  router.push('/')
}

onMounted(() => {
  // 已登录态下才拉数据；未登录已经在脚本顶部 redirect 走，这里不会重复触发
  if (!authStore.isLoggedIn) return
  loadProfile()
  loadSubscriptions()
  loadFavorites()
})
</script>

<template>
  <div v-if="authStore.isLoggedIn" class="profile-page">
    <!-- —— 左侧侧边栏：用户卡 + 模块导航 —— -->
    <aside class="profile-sidebar surface">
      <div class="sidebar-user">
        <div class="avatar">
          <img
            v-if="profile?.avatarUrl || authStore.user?.avatarUrl"
            :src="profile?.avatarUrl || authStore.user?.avatarUrl"
            alt="avatar"
          />
          <span v-else class="avatar-fallback" aria-hidden="true">
            <DefaultAvatarIcon />
          </span>
        </div>
        <div class="sidebar-user-copy">
          <h2 class="sidebar-name">
            {{ profile?.nickname || authStore.user?.nickname || authStore.user?.username }}
          </h2>
          <p class="sidebar-email">
            {{ profile?.email || authStore.user?.email || '未设置邮箱' }}
          </p>
          <span class="role-chip">
            <Shield :size="12" />
            {{ roleLabel }}
          </span>
        </div>
      </div>

      <nav class="sidebar-nav" aria-label="个人中心导航">
        <button
          v-for="item in sections"
          :key="item.key"
          type="button"
          class="nav-item"
          :class="{ active: activeSection === item.key }"
          @click="activeSection = item.key"
        >
          <component :is="item.icon" :size="16" />
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="sectionBadge(item.key)" class="nav-badge">
            {{ sectionBadge(item.key) }}
          </span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <GlowButton variant="ghost" class="sidebar-action" @click="router.push('/recommend')">
          <Sparkles :size="14" />
          智能推荐
        </GlowButton>
        <GlowButton variant="ghost" class="sidebar-action" @click="logoutNow">
          <LogOut :size="14" />
          退出登录
        </GlowButton>
      </div>
    </aside>

    <!-- —— 右侧内容区：按 activeSection 切换 —— -->
    <main class="profile-content">
      <!-- 账户概览 -->
      <section v-if="activeSection === 'overview'" class="surface section-panel">
        <header class="section-head">
          <h2 class="section-title">
            <LayoutDashboard :size="18" /> 账户概览
          </h2>
          <p class="section-desc">快速查看账号关键信息。</p>
        </header>

        <div class="facts-grid">
          <div v-for="item in accountFacts" :key="item.label" class="fact-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <!-- 资料编辑 -->
      <section v-else-if="activeSection === 'profile'" class="surface section-panel">
        <header class="section-head">
          <h2 class="section-title">
            <UserRound :size="18" /> 资料编辑
          </h2>
          <p class="section-desc">更新昵称、联系方式与头像。</p>
        </header>

        <div class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 昵称</span>
            <input v-model="profileForm.nickname" class="glass-input" placeholder="昵称" />
          </label>
          <label class="field">
            <span><Mail :size="14" /> 邮箱</span>
            <input v-model="profileForm.email" class="glass-input" placeholder="邮箱" />
          </label>
          <label class="field">
            <span><User :size="14" /> 手机号</span>
            <input v-model="profileForm.phone" class="glass-input" placeholder="手机号" />
          </label>
          <label class="field">
            <span><Settings :size="14" /> 头像链接</span>
            <input v-model="profileForm.avatarUrl" class="glass-input" placeholder="https://..." />
          </label>
          <GlowButton variant="primary" :loading="loading" @click="saveProfile">
            保存资料
          </GlowButton>
        </div>
      </section>

      <!-- 密码与安全 -->
      <section v-else-if="activeSection === 'password'" class="surface section-panel">
        <header class="section-head">
          <h2 class="section-title">
            <Lock :size="18" /> 密码与安全
          </h2>
          <p class="section-desc">为了账户安全，建议定期更换密码。</p>
        </header>

        <div class="form-stack">
          <label class="field">
            <span><Lock :size="14" /> 当前密码</span>
            <input
              v-model="passwordForm.oldPassword"
              type="password"
              class="glass-input"
              placeholder="当前密码"
            />
          </label>
          <label class="field">
            <span><Lock :size="14" /> 新密码</span>
            <input
              v-model="passwordForm.newPassword"
              type="password"
              class="glass-input"
              placeholder="至少 8 位，含字母和数字"
            />
          </label>
          <GlowButton variant="secondary" :loading="loading" @click="savePassword">
            修改密码
          </GlowButton>
        </div>
      </section>

      <!-- 岗位订阅 -->
      <section v-else-if="activeSection === 'subscriptions'" class="surface section-panel">
        <header class="section-head">
          <h2 class="section-title">
            <BellRing :size="18" /> 岗位订阅（每日推送）
          </h2>
          <p class="section-desc">按条件订阅，明天起早上 9 点自动推送匹配岗位。</p>
        </header>

        <div class="form-stack">
          <div class="sub-grid">
            <input v-model="subForm.city" class="glass-input" placeholder="目标城市" />
            <input v-model="subForm.industry" class="glass-input" placeholder="行业方向" />
            <input v-model="subForm.keyword" class="glass-input" placeholder="关键词（如：Java）" />
            <input v-model="subForm.salaryMin" type="number" class="glass-input" placeholder="最低月薪" />
          </div>
          <GlowButton variant="primary" :loading="subLoading" @click="handleAddSubscription">
            <BellRing :size="14" /> 添加订阅
          </GlowButton>

          <div v-if="subscriptions.length" class="sub-list">
            <div v-for="sub in subscriptions" :key="sub.id" class="sub-item">
              <div class="sub-item-copy">
                <strong>{{ sub.subscriptionType === 'JOB_PUSH' ? '自动筛选推送' : '普通订阅' }}</strong>
                <p class="sub-config">{{ sub.filterConfig }}</p>
              </div>
              <button
                class="icon-btn delete"
                :aria-label="`删除订阅 ${sub.id}`"
                @click="handleDeleteSubscription(sub.id)"
              >
                <Trash2 :size="16" />
              </button>
            </div>
          </div>
          <EmptyState
            v-else
            icon="inbox"
            title="还没有订阅任何条件"
            description="添加条件后，每天早上 9 点会把匹配岗位送到你的邮箱。"
          />
        </div>
      </section>

      <!-- 我的收藏 -->
      <section v-else-if="activeSection === 'favorites'" class="surface section-panel">
        <header class="section-head section-head--with-action">
          <div>
            <h2 class="section-title">
              <Heart :size="18" /> 我的收藏
            </h2>
            <p class="section-desc">已收藏 {{ favoritesTotal }} 个岗位，随时回来继续跟进。</p>
          </div>
          <GlowButton
            v-if="!favoritesLoading && favorites.length"
            variant="ghost"
            @click="loadFavorites"
          >
            <RefreshCcw :size="14" /> 刷新
          </GlowButton>
        </header>

        <div v-if="favoritesLoading" class="fav-skeleton">
          <SkeletonCard type="list" :lines="3" />
        </div>

        <div v-else-if="favoritesError" class="status-banner error-banner">
          收藏列表加载失败：{{ favoritesError }}
        </div>

        <EmptyState
          v-else-if="!favorites.length"
          icon="inbox"
          title="还没有收藏的岗位"
          description="浏览岗位时点击右下角的心形图标，即可把心仪岗位收藏到这里。"
          action-text="去看看岗位"
          @action="router.push('/jobs')"
        />

        <ul v-else class="fav-list">
          <li
            v-for="fav in favorites"
            :key="fav.id || fav.jobId"
            class="fav-item"
            role="button"
            tabindex="0"
            @click="openFavoriteJob(fav)"
            @keydown.enter.prevent="openFavoriteJob(fav)"
            @keydown.space.prevent="openFavoriteJob(fav)"
          >
            <div class="fav-main">
              <div class="fav-title-row">
                <h3 class="fav-title">{{ fav.title || '未知岗位' }}</h3>
                <span v-if="fav.salaryText" class="fav-salary">{{ fav.salaryText }}</span>
              </div>
              <p class="fav-sub">
                <span v-if="fav.companyName" class="fav-sub-item">
                  <Building2 :size="12" /> {{ fav.companyName }}
                </span>
                <span v-if="fav.city" class="fav-sub-item">
                  <MapPin :size="12" /> {{ fav.city }}
                </span>
              </p>
              <p v-if="fav.favoriteTime || fav.createdAt" class="fav-time">
                收藏于 {{ fav.favoriteTime || fav.createdAt }}
              </p>
            </div>
            <div class="fav-actions">
              <button
                type="button"
                class="fav-action-btn view"
                :aria-label="`查看岗位 ${fav.title || ''}`"
                @click.stop="openFavoriteJob(fav)"
              >
                <ArrowRight :size="14" />
              </button>
              <button
                type="button"
                class="fav-action-btn remove"
                :disabled="favoriteRemovingId === (fav.jobId ?? fav.id)"
                :aria-label="`取消收藏 ${fav.title || ''}`"
                @click.stop="handleRemoveFavorite(fav)"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </li>
        </ul>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* —— 整体两栏布局 ——
   左侧 260 sidebar 固定宽，右侧内容 auto。gap 24 保持视觉呼吸。
   sidebar sticky 让长收藏列表滚动时导航一直可见。 */
.profile-page {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.surface {
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-soft);
  border-radius: 20px;
}

/* —— 左侧侧边栏 —— */
.profile-sidebar {
  display: flex;
  flex-direction: column;
  padding: 20px;
  gap: 18px;
  position: sticky;
  top: 24px;
}

.sidebar-user {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--c-border-glass);
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--c-bg-surface-hover);
  flex-shrink: 0;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar .avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 10px;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.34), transparent 36%),
    linear-gradient(135deg, rgba(0, 87, 194, 0.18), rgba(0, 110, 242, 0.34));
  color: var(--c-accent-primary);
}

.sidebar-user-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.sidebar-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-email {
  margin: 0;
  font-size: 12px;
  color: var(--c-text-secondary);
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 4px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
  width: fit-content;
}

/* —— 导航列表 —— */
.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-out, ease),
    color 150ms var(--ease-out, ease),
    border-color 150ms var(--ease-out, ease);
}

.nav-item :deep(svg) {
  flex-shrink: 0;
  opacity: 0.9;
}

.nav-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-badge {
  flex-shrink: 0;
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
}

.nav-item:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.nav-item.active {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.35);
  color: var(--c-accent-primary);
  font-weight: 600;
}

.nav-item.active .nav-badge {
  background: rgba(0, 87, 194, 0.14);
  border-color: rgba(0, 87, 194, 0.3);
  color: var(--c-accent-primary);
}

.sidebar-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px dashed var(--c-border-glass);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-action {
  width: 100%;
  justify-content: center;
}

/* —— 右侧内容区 —— */
.profile-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.section-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px 26px;
  min-width: 0;
}

.section-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.section-head--with-action {
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-head--with-action > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.section-title {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.02em;
}

.section-title :deep(svg) {
  color: var(--c-accent-primary);
}

.section-desc {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

/* —— facts-grid —— */
.facts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.fact-card {
  padding: 16px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
}

.fact-card span {
  display: block;
  margin-bottom: 8px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.fact-card strong {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.02em;
  color: var(--c-text-primary);
}

/* —— 表单 —— */
.form-stack,
.field {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field span {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: var(--c-text-secondary);
  font-size: 13px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  color: var(--c-text-primary);
}

.status-banner {
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--c-bg-surface);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
  border-color: rgba(185, 28, 28, 0.3);
}

[data-theme="dark"] .avatar .avatar-fallback {
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.12), transparent 36%),
    linear-gradient(135deg, rgba(175, 198, 255, 0.22), rgba(82, 106, 184, 0.46));
  color: #eef3ff;
}

[data-theme="dark"] .error-banner {
  color: #fecaca;
  background: rgba(127, 29, 29, 0.45);
}

/* —— 订阅模块 —— */
.sub-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.sub-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.sub-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
}

.sub-item-copy {
  min-width: 0;
  flex: 1;
}

.sub-item-copy strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.sub-config {
  font-family: monospace;
  font-size: 12px;
  color: var(--c-text-muted);
  margin: 4px 0 0;
  word-break: break-all;
}

.icon-btn.delete {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border: none;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  flex-shrink: 0;
}

.icon-btn.delete:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* —— 收藏列表 —— */
.fav-skeleton {
  padding: 4px 0;
}

.fav-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.fav-item:hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px var(--c-accent-primary-glow);
}

.fav-item:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

.fav-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fav-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.fav-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.fav-salary {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.fav-sub {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.fav-sub-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.fav-sub-item :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.75;
}

.fav-time {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.fav-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.fav-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-strong);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease,
    transform 150ms ease;
}

.fav-action-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.fav-action-btn.view:hover {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.35);
  color: var(--c-accent-primary);
}

.fav-action-btn.remove:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

.fav-action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* —— 响应式 ——
   窄屏 (≤ 960) 折回单列：侧边栏变成顶部横向标签条，内容区紧随其后。 */
@media (max-width: 960px) {
  .profile-page {
    grid-template-columns: 1fr;
  }

  .profile-sidebar {
    position: static;
    top: auto;
  }

  .sidebar-user {
    flex-direction: row;
    text-align: left;
    align-items: center;
    padding-bottom: 14px;
  }

  .sidebar-user-copy {
    align-items: flex-start;
  }

  .sidebar-nav {
    flex-direction: row;
    overflow-x: auto;
    gap: 6px;
    padding-bottom: 4px;
    scrollbar-width: none;
  }

  .sidebar-nav::-webkit-scrollbar {
    display: none;
  }

  .nav-item {
    flex-shrink: 0;
  }

  .sidebar-footer {
    flex-direction: row;
    border-top: none;
    padding-top: 0;
  }
}

@media (max-width: 560px) {
  .section-panel {
    padding: 20px;
  }

  .sub-grid {
    grid-template-columns: 1fr;
  }

  .facts-grid {
    grid-template-columns: 1fr 1fr;
  }

  .section-head--with-action {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
