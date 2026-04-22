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
  fetchSubscriptionMatches,
  dispatchSubscription,
  fetchFavorites,
  removeFavorite,
  invalidateApiCache,
  fetchNotifications,
  markNotificationRead,
  markAllNotificationsRead
} from '../api'
import {
  Lock,
  LogOut,
  Mail,
  RefreshCcw,
  Settings,
  Shield,
  Sparkles,
  Send,
  Eye,
  User,
  UserRound,
  BellRing,
  Bell,
  CheckCheck,
  Trash2,
  Heart,
  MapPin,
  Building2,
  ArrowRight,
  LayoutDashboard,
  Inbox,
  Webhook
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
  salaryMin: '',
  channel: 'IN_APP'  // IN_APP / EMAIL / WEBHOOK —— 后端支持多渠道
})
const subLoading = ref(false)

// 订阅渠道选项（后端 UserSubscription.pushChannel 枚举）
const channelOptions = [
  { value: 'IN_APP', label: '站内通知', icon: Bell, desc: '推送到通知中心' },
  { value: 'EMAIL', label: '邮件', icon: Mail, desc: '发到账号绑定邮箱' },
  { value: 'WEBHOOK', label: 'Webhook', icon: Webhook, desc: '回调开放平台配置的 URL' }
]

function channelLabel(v) {
  const hit = channelOptions.find((c) => c.value === v)
  return hit ? hit.label : v || '站内通知'
}

// 订阅匹配预览 / 派发（每条订阅独立 state，避免并发时互相覆盖）
const matchesBySubId = ref({})          // { [subId]: { loading, jobs, error } }
const dispatchingSubId = ref(null)      // 正在手动派发的订阅 id

async function previewSubscriptionMatches(sub) {
  const id = sub.id
  matchesBySubId.value = {
    ...matchesBySubId.value,
    [id]: { loading: true, jobs: [], error: '' }
  }
  try {
    const data = await fetchSubscriptionMatches(authStore.token, id)
    // 后端返回 { matches: [...], subscription }，兼容也可能直接返回数组
    const jobs = Array.isArray(data) ? data : (data?.matches || data?.jobs || [])
    matchesBySubId.value = {
      ...matchesBySubId.value,
      [id]: { loading: false, jobs, error: '' }
    }
  } catch (e) {
    matchesBySubId.value = {
      ...matchesBySubId.value,
      [id]: { loading: false, jobs: [], error: normalizeError(e) }
    }
  }
}

async function handleDispatchSubscription(sub) {
  if (dispatchingSubId.value) return
  dispatchingSubId.value = sub.id
  try {
    const res = await dispatchSubscription(authStore.token, sub.id)
    const delivered = res?.deliveredCount ?? 0
    success(`已派发 ${delivered} 条匹配岗位，稍后可在通知中心查看。`)
    // 派发后刷新通知未读数
    loadNotifications()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    dispatchingSubId.value = null
  }
}

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

// —— 通知中心 ——
const notifications = ref([])
const notificationsLoading = ref(false)
const notificationsError = ref('')
const notificationsUnread = ref(0)
const notificationsMarkingAll = ref(false)

async function loadNotifications() {
  if (!authStore.isLoggedIn) return
  notificationsLoading.value = true
  notificationsError.value = ''
  try {
    const res = await fetchNotifications(authStore.token, { page: 1, pageSize: 30 })
    notifications.value = res.data
    notificationsUnread.value = res.unreadCount || 0
  } catch (e) {
    notificationsError.value = normalizeError(e)
  } finally {
    notificationsLoading.value = false
  }
}

async function handleMarkRead(n) {
  if (n.isRead === 1) return
  try {
    await markNotificationRead(authStore.token, n.id)
    // 本地同步状态，避免整页重新请求
    n.isRead = 1
    notificationsUnread.value = Math.max(0, notificationsUnread.value - 1)
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleMarkAllRead() {
  if (notificationsMarkingAll.value || notificationsUnread.value === 0) return
  notificationsMarkingAll.value = true
  try {
    await markAllNotificationsRead(authStore.token)
    notifications.value.forEach((n) => (n.isRead = 1))
    notificationsUnread.value = 0
    success('已全部标记为已读')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    notificationsMarkingAll.value = false
  }
}

function notifyTypeLabel(t) {
  const map = { JOB_PUSH: '岗位推送', REPORT_READY: '报告就绪', SYSTEM: '系统通知' }
  return map[t] || t || '通知'
}

// —— 左侧导航 ——
// 所有模块集中声明，后续加/删模块只动这里 + 右侧对应的 <section>
const sections = [
  { key: 'overview', label: '账户概览', icon: LayoutDashboard },
  { key: 'profile', label: '资料编辑', icon: UserRound },
  { key: 'password', label: '密码与安全', icon: Lock },
  { key: 'subscriptions', label: '岗位订阅', icon: BellRing },
  { key: 'notifications', label: '通知中心', icon: Inbox },
  { key: 'favorites', label: '我的收藏', icon: Heart }
]
const activeSection = ref('overview')

// favorites 分页展示时计数；导航右侧小徽章用
const favoritesBadge = computed(() => (favoritesTotal.value > 0 ? favoritesTotal.value : ''))
const subsBadge = computed(() => (subscriptions.value.length > 0 ? subscriptions.value.length : ''))
const notificationsBadge = computed(() =>
  notificationsUnread.value > 0 ? notificationsUnread.value : ''
)

function sectionBadge(key) {
  if (key === 'favorites') return favoritesBadge.value
  if (key === 'subscriptions') return subsBadge.value
  if (key === 'notifications') return notificationsBadge.value
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
  // 选择邮件推送时，必须先绑定邮箱——否则后端投递时会静默失败
  if (subForm.value.channel === 'EMAIL' && !(profile.value?.email || authStore.user?.email)) {
    error('请先在「资料编辑」里填写邮箱，再选择邮件推送。')
    return
  }
  subLoading.value = true
  try {
    const filterConfig = JSON.stringify({
      city: subForm.value.city,
      industry: subForm.value.industry,
      keyword: subForm.value.keyword,
      salaryMin: subForm.value.salaryMin ? Number(subForm.value.salaryMin) : null
    })
    await createSubscription(authStore.token, {
      filterConfig,
      channel: subForm.value.channel
    })
    success('岗位订阅配置成功，明天早上 9 点将为您推送。')
    subForm.value = { city: '', industry: '', keyword: '', salaryMin: '', channel: 'IN_APP' }
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
  loadNotifications()
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

          <!-- 推送渠道：IN_APP / EMAIL / WEBHOOK —— sub2api 风格的分段选择器 -->
          <div class="channel-field">
            <span class="channel-label">推送到</span>
            <div class="channel-segments" role="radiogroup" aria-label="推送渠道">
              <button
                v-for="opt in channelOptions"
                :key="opt.value"
                type="button"
                role="radio"
                :aria-checked="subForm.channel === opt.value"
                class="channel-seg"
                :class="{ active: subForm.channel === opt.value }"
                @click="subForm.channel = opt.value"
              >
                <component :is="opt.icon" :size="14" />
                <span>{{ opt.label }}</span>
              </button>
            </div>
            <p class="channel-hint">
              {{ channelOptions.find((c) => c.value === subForm.channel)?.desc }}
              <template v-if="subForm.channel === 'EMAIL'">
                <span v-if="profile?.email || authStore.user?.email">
                  （投递至 {{ profile?.email || authStore.user?.email }}）
                </span>
                <span v-else class="channel-hint-warn">
                  · 未绑定邮箱，请先在「资料编辑」补充。
                </span>
              </template>
            </p>
          </div>

          <GlowButton variant="primary" :loading="subLoading" @click="handleAddSubscription">
            <BellRing :size="14" /> 添加订阅
          </GlowButton>

          <div v-if="subscriptions.length" class="sub-list">
            <div v-for="sub in subscriptions" :key="sub.id" class="sub-item">
              <div class="sub-item-main">
                <div class="sub-item-copy">
                  <div class="sub-item-title">
                    <strong>{{ sub.subscriptionType === 'JOB_PUSH' ? '自动筛选推送' : '普通订阅' }}</strong>
                    <span class="channel-tag">
                      <component
                        :is="channelOptions.find((c) => c.value === (sub.channel || sub.pushChannel))?.icon || Bell"
                        :size="12"
                      />
                      {{ channelLabel(sub.channel || sub.pushChannel) }}
                    </span>
                  </div>
                  <p class="sub-config">{{ sub.filterConfig || sub.filterCriteria }}</p>
                </div>
                <div class="sub-item-actions">
                  <button
                    class="icon-btn secondary"
                    :aria-label="`预览订阅 ${sub.id} 的匹配岗位`"
                    title="预览匹配岗位"
                    @click="previewSubscriptionMatches(sub)"
                  >
                    <Eye :size="14" />
                  </button>
                  <button
                    class="icon-btn secondary"
                    :disabled="dispatchingSubId === sub.id"
                    :aria-label="`立即派发订阅 ${sub.id}`"
                    title="立即派发一次"
                    @click="handleDispatchSubscription(sub)"
                  >
                    <Send :size="14" />
                  </button>
                  <button
                    class="icon-btn delete"
                    :aria-label="`删除订阅 ${sub.id}`"
                    @click="handleDeleteSubscription(sub.id)"
                  >
                    <Trash2 :size="14" />
                  </button>
                </div>
              </div>

              <!-- 预览匹配列表：点"眼睛"按钮后展开 -->
              <div
                v-if="matchesBySubId[sub.id]"
                class="sub-matches"
              >
                <div v-if="matchesBySubId[sub.id].loading" class="sub-matches-loading">
                  正在计算匹配岗位……
                </div>
                <div
                  v-else-if="matchesBySubId[sub.id].error"
                  class="status-banner error-banner"
                >
                  预览失败：{{ matchesBySubId[sub.id].error }}
                </div>
                <div
                  v-else-if="!matchesBySubId[sub.id].jobs.length"
                  class="sub-matches-empty"
                >
                  暂无匹配岗位。调整订阅条件后再试一次。
                </div>
                <ul v-else class="sub-matches-list">
                  <li
                    v-for="m in matchesBySubId[sub.id].jobs.slice(0, 5)"
                    :key="m.id || m.jobId"
                    class="sub-match-item"
                    @click="router.push({ path: '/jobs', query: { jobId: m.id || m.jobId } })"
                  >
                    <span class="match-title">{{ m.title || m.jobTitle || '岗位' }}</span>
                    <span v-if="m.companyName" class="match-company">{{ m.companyName }}</span>
                    <span v-if="m.salaryText" class="match-salary">{{ m.salaryText }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
          <EmptyState
            v-else
            icon="inbox"
            title="还没有订阅任何条件"
            description="添加条件后，每天早上 9 点会把匹配岗位送到你的邮箱或通知中心。"
          />
        </div>
      </section>

      <!-- 通知中心 -->
      <section v-else-if="activeSection === 'notifications'" class="surface section-panel">
        <header class="section-head section-head--with-action">
          <div>
            <h2 class="section-title">
              <Inbox :size="18" /> 通知中心
              <span v-if="notificationsUnread" class="unread-pill">{{ notificationsUnread }} 条未读</span>
            </h2>
            <p class="section-desc">岗位推送、报告就绪、系统公告都会汇总在这里。</p>
          </div>
          <div class="section-head-actions">
            <GlowButton variant="ghost" @click="loadNotifications">
              <RefreshCcw :size="14" /> 刷新
            </GlowButton>
            <GlowButton
              v-if="notificationsUnread > 0"
              variant="secondary"
              :loading="notificationsMarkingAll"
              @click="handleMarkAllRead"
            >
              <CheckCheck :size="14" /> 全部已读
            </GlowButton>
          </div>
        </header>

        <div v-if="notificationsLoading" class="fav-skeleton">
          <SkeletonCard type="list" :lines="3" />
        </div>

        <div v-else-if="notificationsError" class="status-banner error-banner">
          通知加载失败：{{ notificationsError }}
        </div>

        <EmptyState
          v-else-if="!notifications.length"
          icon="inbox"
          title="暂无通知"
          description="订阅岗位、生成报告后，新消息会出现在这里。"
        />

        <ul v-else class="notify-list">
          <li
            v-for="n in notifications"
            :key="n.id"
            class="notify-item"
            :class="{ unread: n.isRead !== 1 }"
            @click="handleMarkRead(n)"
          >
            <div class="notify-dot" aria-hidden="true"></div>
            <div class="notify-main">
              <div class="notify-head-row">
                <h3 class="notify-title">{{ n.title || notifyTypeLabel(n.notifyType) }}</h3>
                <span class="notify-type-tag">{{ notifyTypeLabel(n.notifyType) }}</span>
              </div>
              <p v-if="n.content" class="notify-content">{{ n.content }}</p>
              <p class="notify-time">{{ n.createdAt }}</p>
            </div>
          </li>
        </ul>
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
/* —— 整体两栏布局（fullBleed 路由）——
   父级 .main-content 是 overflow: hidden + 高度固定（见 App.vue 的 full-bleed
   样式）；ProfileView 在此基础上用 flex row 把视口切成"左栏侧边（固定宽）/
   右栏内容（可滚）"。侧栏自身不滚，右栏自己 overflow: auto —— 这样切换模块
   只会改变右栏内容，侧栏作为兄弟节点完全不受影响，也就不会再上下抖动。 */
.profile-page {
  display: flex;
  height: 100%;
  min-height: 0;
  width: 100%;
}

.surface {
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-soft);
  border-radius: 20px;
}

/* —— 左侧侧边栏（一整块、贴左边、满高） —— */
.profile-sidebar {
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 28px 22px 24px;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  /* 去掉卡片感：无圆角、无阴影、只用右侧分割线 */
  background: var(--c-bg-surface);
  border: none;
  border-right: 1px solid var(--c-border-glass);
  border-radius: 0;
  box-shadow: none;
}

/* 自定义细滚动条，只在悬停时显形 */
.profile-sidebar::-webkit-scrollbar {
  width: 4px;
}
.profile-sidebar::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
}
.profile-sidebar:hover::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
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

/* —— 右侧内容区（独立滚动，最大宽约束避免在超宽屏上内容被拉散） —— */
.profile-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px 28px 32px;
}

.profile-content > * {
  width: min(100%, 1100px);
  margin-inline: 0 auto;  /* 仅右侧 auto，让内容对齐到容器左边而不跳到正中 */
}

.profile-content::-webkit-scrollbar {
  width: 6px;
}
.profile-content::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
  transition: background-color 200ms ease;
}
.profile-content:hover::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
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

/* 推送渠道分段选择器 */
.channel-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.channel-label {
  font-size: 13px;
  color: var(--c-text-secondary);
}

.channel-segments {
  display: inline-flex;
  flex-wrap: wrap;
  padding: 4px;
  gap: 4px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-strong);
  width: fit-content;
  max-width: 100%;
}

.channel-seg {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: none;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 140ms var(--ease-out, ease), color 140ms var(--ease-out, ease);
}

.channel-seg:hover:not(.active) {
  color: var(--c-text-primary);
  background: var(--c-bg-surface-hover);
}

.channel-seg.active {
  background: var(--c-accent-primary);
  color: #fff;
  box-shadow: 0 2px 6px rgba(0, 87, 194, 0.25);
}

.channel-hint {
  margin: 0;
  font-size: 12px;
  color: var(--c-text-muted);
  line-height: 1.5;
}

.channel-hint-warn {
  color: #ef4444;
  font-weight: 500;
}

.sub-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
}

.sub-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
}

.sub-item-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sub-item-copy {
  min-width: 0;
  flex: 1;
}

.sub-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sub-item-copy strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.channel-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  border: 1px solid rgba(0, 87, 194, 0.28);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
}

.channel-tag :deep(svg) {
  opacity: 0.85;
}

.sub-config {
  font-family: monospace;
  font-size: 12px;
  color: var(--c-text-muted);
  margin: 4px 0 0;
  word-break: break-all;
}

.sub-item-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color 150ms ease, color 150ms ease, border-color 150ms ease;
  flex-shrink: 0;
}

.icon-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.32);
  color: var(--c-accent-primary);
}

.icon-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.icon-btn.delete {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.22);
}

.icon-btn.delete:hover {
  background: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

/* 订阅匹配预览 */
.sub-matches {
  border-top: 1px dashed var(--c-border-glass);
  padding-top: 10px;
  font-size: 13px;
}

.sub-matches-loading,
.sub-matches-empty {
  color: var(--c-text-muted);
  font-size: 12.5px;
}

.sub-matches-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sub-match-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--c-bg-surface);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition: background-color 140ms ease, border-color 140ms ease;
}

.sub-match-item:hover {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.32);
}

.match-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  color: var(--c-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.match-company {
  color: var(--c-text-secondary);
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
}

.match-salary {
  color: var(--c-accent-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* —— 通知中心 —— */
.section-head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.unread-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  color: #fff;
  font-size: 11.5px;
  font-weight: 600;
  margin-left: 4px;
}

.notify-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notify-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  cursor: pointer;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.notify-item:hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
}

.notify-item.unread {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.35);
}

.notify-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 8px;
  background: var(--c-border-glass);
  flex-shrink: 0;
}

.notify-item.unread .notify-dot {
  background: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(0, 87, 194, 0.15);
}

.notify-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.notify-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notify-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notify-item.unread .notify-title {
  font-weight: 700;
}

.notify-type-tag {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 500;
}

.notify-content {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.notify-time {
  margin: 2px 0 0;
  color: var(--c-text-muted);
  font-size: 11.5px;
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
   窄屏 (≤ 960) 折回单列：侧边栏变成顶部横向标签条，内容区在下方独立滚。 */
@media (max-width: 960px) {
  .profile-page {
    flex-direction: column;
  }

  .profile-sidebar {
    flex: 0 0 auto;
    height: auto;
    overflow-y: visible;
    border-right: none;
    border-bottom: 1px solid var(--c-border-glass);
    padding: 16px 20px;
  }

  .profile-content {
    padding: 20px 18px 28px;
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
