<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import EmptyState from '../components/common/EmptyState.vue'
import {
  changeAuthPassword,
  createSubscription,
  createWebhook,
  deleteSubscription,
  deleteWebhook,
  fetchCareerProfile,
  fetchFavorites,
  fetchNotifications,
  fetchPlatformAdvisory,
  fetchSubscriptionMatches,
  fetchSubscriptions,
  fetchWebhookDeliveries,
  fetchWebhooks,
  login,
  markAllNotificationsRead,
  markNotificationRead,
  normalizeError,
  removeFavorite,
  register,
  dispatchSubscriptionMatches,
  toggleWebhook,
  updateAuthProfile
} from '../api'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { getRoleLabel } from '../utils/role'
import {
  Bell,
  BriefcaseBusiness,
  KeyRound,
  LogIn,
  Radio,
  Send,
  ShieldCheck,
  Trash2,
  UserRound,
  Webhook
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { success, error } = useToast()

const activeTab = ref(route.query.login === 'true' ? 'login' : 'register')
const authLoading = ref(false)
const profileLoading = ref(false)
const passwordLoading = ref(false)
const profileLoaded = ref(false)
const advisory = ref(null)
const favoritesLoading = ref(false)
const favoriteItems = ref([])
const subscriptionsLoading = ref(false)
const subscriptionSaving = ref(false)
const notificationsLoading = ref(false)
const webhooksLoading = ref(false)
const webhookSaving = ref(false)
const previewingSubscriptionId = ref(null)
const dispatchingSubscriptionId = ref(null)
const deliveryWebhookId = ref(null)
const unreadNotificationCount = ref(0)
const subscriptionItems = ref([])
const subscriptionMatches = ref({})
const notifications = ref([])
const webhookItems = ref([])
const webhookDeliveries = ref({})

const loginForm = ref({
  username: '',
  password: ''
})

const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  roleType: 0
})

const profileForm = ref({
  username: '',
  email: '',
  phone: '',
  major: '',
  school: '',
  expectedCity: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const subscriptionForm = ref({
  subscriptionType: 'JOB_PUSH',
  channel: 'IN_APP',
  filterConfig: '{"keyword":"","city":"","salaryMin":""}'
})

const webhookForm = ref({
  endpointUrl: '',
  eventTypes: 'JOB_MATCH,NOTIFICATION_CREATED'
})

const currentRoleLabel = computed(() => getRoleLabel(authStore.user?.roleType ?? 0))
const redirectTarget = computed(() => String(route.query.redirect || '/'))

async function handleLogin() {
  authLoading.value = true
  try {
    const result = await login(loginForm.value)
    authStore.setAuth(result.accessToken || result.token || '', result.userInfo || result.user || result)
    await authStore.syncProfile()
    await loadCareerProfile()
    success('登录成功')
    router.push(redirectTarget.value)
  } catch (e) {
    error(normalizeError(e))
  } finally {
    authLoading.value = false
  }
}

async function handleRegister() {
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    error('两次输入的密码不一致')
    return
  }
  authLoading.value = true
  try {
    await register({
      username: registerForm.value.username,
      password: registerForm.value.password,
      email: registerForm.value.email,
      roleType: registerForm.value.roleType
    })
    success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.value.username = registerForm.value.username
    loginForm.value.password = ''
  } catch (e) {
    error(normalizeError(e))
  } finally {
    authLoading.value = false
  }
}

async function loadCareerProfile() {
  if (!authStore.token) return
  profileLoading.value = true
  try {
    const profile = await fetchCareerProfile(authStore.token)
    profileForm.value = {
      username: profile.username || authStore.user?.username || '',
      email: profile.email || authStore.user?.email || '',
      phone: profile.phone || '',
      major: profile.major || '',
      school: profile.school || '',
      expectedCity: profile.expectedCity || ''
    }
    profileLoaded.value = true
  } catch (e) {
    profileForm.value = {
      username: authStore.user?.username || '',
      email: authStore.user?.email || '',
      phone: '',
      major: '',
      school: '',
      expectedCity: ''
    }
    profileLoaded.value = true
  } finally {
    profileLoading.value = false
  }
}

async function loadPlatformAdvisory() {
  if (!authStore.token) return
  try {
    advisory.value = await fetchPlatformAdvisory(authStore.token)
  } catch {
    advisory.value = null
  }
}

async function loadFavorites() {
  if (!authStore.token) return
  favoritesLoading.value = true
  try {
    const result = await fetchFavorites(authStore.token, { page: 1, pageSize: 8 })
    favoriteItems.value = result.data || []
  } catch {
    favoriteItems.value = []
  } finally {
    favoritesLoading.value = false
  }
}

async function loadSubscriptions() {
  if (!authStore.token) return
  subscriptionsLoading.value = true
  try {
    subscriptionItems.value = await fetchSubscriptions(authStore.token)
  } catch {
    subscriptionItems.value = []
  } finally {
    subscriptionsLoading.value = false
  }
}

async function loadNotifications() {
  if (!authStore.token) return
  notificationsLoading.value = true
  try {
    const result = await fetchNotifications(authStore.token, { page: 1, pageSize: 8 })
    notifications.value = result.data || []
    unreadNotificationCount.value = Number(result.unreadCount || 0)
  } catch {
    notifications.value = []
    unreadNotificationCount.value = 0
  } finally {
    notificationsLoading.value = false
  }
}

async function loadWebhooks() {
  if (!authStore.token) return
  webhooksLoading.value = true
  try {
    webhookItems.value = await fetchWebhooks(authStore.token)
  } catch {
    webhookItems.value = []
  } finally {
    webhooksLoading.value = false
  }
}

async function handleSaveProfile() {
  profileLoading.value = true
  try {
    await updateAuthProfile(authStore.token, profileForm.value)
    await authStore.syncProfile()
    success('资料已更新')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    profileLoading.value = false
  }
}

async function handleChangePassword() {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    error('两次输入的新密码不一致')
    return
  }
  passwordLoading.value = true
  try {
    await changeAuthPassword(authStore.token, {
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    passwordForm.value = {
      oldPassword: '',
      newPassword: '',
      confirmPassword: ''
    }
    success('密码已更新')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    passwordLoading.value = false
  }
}

function handleLogout() {
  authStore.logout()
  profileLoaded.value = false
  favoriteItems.value = []
  subscriptionItems.value = []
  notifications.value = []
  webhookItems.value = []
  subscriptionMatches.value = {}
  webhookDeliveries.value = {}
  unreadNotificationCount.value = 0
  router.push('/')
}

async function handleRemoveFavorite(jobId) {
  try {
    await removeFavorite(authStore.token, jobId)
    favoriteItems.value = favoriteItems.value.filter((item) => Number(item.jobId || item.id) !== Number(jobId))
    success('已取消收藏')
  } catch (e) {
    error(normalizeError(e))
  }
}

function openFavoriteJob(item) {
  const jobId = item.jobId || item.id
  if (!jobId) return
  router.push({ path: '/jobs', query: { open: jobId } })
}

function formatDateTime(value) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function summarizeFilterConfig(raw) {
  if (!raw) return '未配置筛选条件'
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    const segments = []
    if (parsed.keyword) segments.push(`关键词 ${parsed.keyword}`)
    if (parsed.city) segments.push(`城市 ${parsed.city}`)
    if (parsed.salaryMin) segments.push(`最低薪资 ${parsed.salaryMin}`)
    return segments.join(' · ') || JSON.stringify(parsed)
  } catch {
    return String(raw)
  }
}

async function handleCreateSubscription() {
  subscriptionSaving.value = true
  try {
    await createSubscription(authStore.token, subscriptionForm.value)
    success('岗位订阅已创建')
    await loadSubscriptions()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    subscriptionSaving.value = false
  }
}

async function handleDeleteSubscription(id) {
  try {
    await deleteSubscription(authStore.token, id)
    delete subscriptionMatches.value[id]
    subscriptionItems.value = subscriptionItems.value.filter((item) => Number(item.id) !== Number(id))
    success('已删除订阅')
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handlePreviewMatches(id) {
  previewingSubscriptionId.value = id
  try {
    const result = await fetchSubscriptionMatches(authStore.token, id, 6)
    subscriptionMatches.value = {
      ...subscriptionMatches.value,
      [id]: Array.isArray(result) ? result : []
    }
  } catch (e) {
    error(normalizeError(e))
  } finally {
    previewingSubscriptionId.value = null
  }
}

async function handleDispatchSubscription(id) {
  dispatchingSubscriptionId.value = id
  try {
    const result = await dispatchSubscriptionMatches(authStore.token, id, 10)
    success(`已派发 ${result.deliveredCount || 0} 条匹配结果`)
    await loadNotifications()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    dispatchingSubscriptionId.value = null
  }
}

async function handleMarkNotificationRead(id) {
  try {
    await markNotificationRead(authStore.token, id)
    notifications.value = notifications.value.map((item) =>
      Number(item.id) === Number(id) ? { ...item, isRead: 1 } : item
    )
    unreadNotificationCount.value = Math.max(
      0,
      notifications.value.filter((item) => Number(item.isRead) !== 1).length
    )
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleMarkAllNotificationsRead() {
  try {
    await markAllNotificationsRead(authStore.token)
    notifications.value = notifications.value.map((item) => ({ ...item, isRead: 1 }))
    unreadNotificationCount.value = 0
    success('通知已全部标为已读')
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleCreateWebhook() {
  webhookSaving.value = true
  try {
    await createWebhook(authStore.token, webhookForm.value)
    success('Webhook 已创建')
    webhookForm.value.endpointUrl = ''
    await loadWebhooks()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    webhookSaving.value = false
  }
}

async function handleToggleWebhook(id) {
  try {
    await toggleWebhook(authStore.token, id)
    await loadWebhooks()
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleDeleteWebhook(id) {
  try {
    await deleteWebhook(authStore.token, id)
    delete webhookDeliveries.value[id]
    webhookItems.value = webhookItems.value.filter((item) => Number(item.id) !== Number(id))
    success('Webhook 已删除')
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleLoadWebhookDeliveries(id) {
  deliveryWebhookId.value = id
  try {
    const result = await fetchWebhookDeliveries(authStore.token, id)
    webhookDeliveries.value = {
      ...webhookDeliveries.value,
      [id]: Array.isArray(result) ? result : []
    }
  } catch (e) {
    error(normalizeError(e))
  } finally {
    deliveryWebhookId.value = null
  }
}

onMounted(async () => {
  if (authStore.isLoggedIn) {
    await Promise.all([
      loadCareerProfile(),
      loadPlatformAdvisory(),
      loadFavorites(),
      loadSubscriptions(),
      loadNotifications(),
      loadWebhooks()
    ])
  }
})
</script>

<template>
  <div class="profile-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">{{ authStore.isLoggedIn ? '个人中心' : '账号入口' }}</span>
        <h1 class="page-intro-title">{{ authStore.isLoggedIn ? '账户与职业档案' : '登录或注册平台账号' }}</h1>
        <p class="page-intro-text">
          {{ authStore.isLoggedIn ? '在这里维护基础资料、职业画像与账号安全设置。' : '完成登录后可进入报告中心、推荐中心、教师工作台和治理页面。' }}
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">当前状态</span>
          <span class="intro-metric-value">{{ authStore.isLoggedIn ? '已登录' : '未登录' }}</span>
        </div>
        <div class="intro-metric" v-if="authStore.isLoggedIn">
          <span class="intro-metric-label">角色</span>
          <span class="intro-metric-value">{{ currentRoleLabel }}</span>
        </div>
      </div>
    </section>

    <section v-if="!authStore.isLoggedIn" class="grid two-col">
      <PremiumCard title="登录" glowColor="primary">
        <div class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 用户名</span>
            <input v-model="loginForm.username" class="glass-input" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span><KeyRound :size="14" /> 密码</span>
            <input v-model="loginForm.password" type="password" class="glass-input" placeholder="请输入密码" />
          </label>
          <GlowButton variant="primary" :loading="authLoading && activeTab === 'login'" @click="activeTab = 'login'; handleLogin()">
            <LogIn :size="14" /> 登录
          </GlowButton>
        </div>
      </PremiumCard>

      <PremiumCard title="注册" glowColor="secondary">
        <div class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 用户名</span>
            <input v-model="registerForm.username" class="glass-input" placeholder="建议使用学号或工号" />
          </label>
          <label class="field">
            <span>邮箱</span>
            <input v-model="registerForm.email" class="glass-input" placeholder="请输入邮箱" />
          </label>
          <label class="field">
            <span>角色</span>
            <select v-model.number="registerForm.roleType" class="glass-input">
              <option :value="0">学生</option>
              <option :value="2">教师</option>
              <option :value="1">管理员</option>
            </select>
          </label>
          <label class="field">
            <span><KeyRound :size="14" /> 密码</span>
            <input v-model="registerForm.password" type="password" class="glass-input" placeholder="请输入密码" />
          </label>
          <label class="field">
            <span><ShieldCheck :size="14" /> 确认密码</span>
            <input v-model="registerForm.confirmPassword" type="password" class="glass-input" placeholder="请再次输入密码" />
          </label>
          <GlowButton variant="secondary" :loading="authLoading && activeTab === 'register'" @click="activeTab = 'register'; handleRegister()">
            注册账号
          </GlowButton>
        </div>
      </PremiumCard>
    </section>

    <section v-else class="grid two-col">
      <PremiumCard title="基础资料" glowColor="primary">
        <div v-if="profileLoaded" class="form-stack">
          <label class="field">
            <span>用户名</span>
            <input v-model="profileForm.username" class="glass-input" />
          </label>
          <label class="field">
            <span>邮箱</span>
            <input v-model="profileForm.email" class="glass-input" />
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model="profileForm.phone" class="glass-input" />
          </label>
          <label class="field">
            <span>院校</span>
            <input v-model="profileForm.school" class="glass-input" />
          </label>
          <label class="field">
            <span>专业</span>
            <input v-model="profileForm.major" class="glass-input" />
          </label>
          <label class="field">
            <span>目标城市</span>
            <input v-model="profileForm.expectedCity" class="glass-input" />
          </label>
          <GlowButton variant="primary" :loading="profileLoading" @click="handleSaveProfile">保存资料</GlowButton>
        </div>
        <EmptyState
          v-else-if="!profileLoading"
          icon="user"
          title="暂无档案数据"
          description="当前账号还没有职业档案，保存一次资料后将用于推荐和报告生成。"
        />
      </PremiumCard>

      <PremiumCard title="账号安全" glowColor="teal">
        <div class="form-stack">
          <label class="field">
            <span>旧密码</span>
            <input v-model="passwordForm.oldPassword" type="password" class="glass-input" />
          </label>
          <label class="field">
            <span>新密码</span>
            <input v-model="passwordForm.newPassword" type="password" class="glass-input" />
          </label>
          <label class="field">
            <span>确认新密码</span>
            <input v-model="passwordForm.confirmPassword" type="password" class="glass-input" />
          </label>
          <GlowButton variant="secondary" :loading="passwordLoading" @click="handleChangePassword">修改密码</GlowButton>
          <GlowButton variant="ghost" @click="handleLogout">退出登录</GlowButton>
        </div>
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && advisory" class="grid one-col">
      <PremiumCard title="平台建议" glowColor="secondary">
        <div class="advisory-grid">
          <div class="advisory-score">
            <div class="advisory-metric">
              <span>画像完整度</span>
              <strong>{{ advisory.profileCompletenessScore || 0 }}</strong>
            </div>
            <div class="advisory-metric">
              <span>市场对齐度</span>
              <strong>{{ advisory.marketAlignmentScore || 0 }}</strong>
            </div>
            <div class="advisory-metric">
              <span>缺口技能</span>
              <strong>{{ advisory.missingSkills?.length || 0 }}</strong>
            </div>
          </div>

          <div class="advisory-section">
            <h3>当前风险</h3>
            <div class="advisory-list">
              <div v-for="item in advisory.risks || []" :key="item" class="advisory-item">{{ item }}</div>
            </div>
          </div>

          <div class="advisory-section">
            <h3>下一步动作</h3>
            <div class="advisory-list">
              <button
                v-for="item in advisory.actions || []"
                :key="item.title"
                class="advisory-action"
                @click="router.push(item.modulePath === '/report-center' ? '/reports' : item.modulePath)"
              >
                <strong>{{ item.title }}</strong>
                <span>{{ item.detail }}</span>
              </button>
            </div>
          </div>
        </div>
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn" class="grid one-col">
      <PremiumCard title="我的收藏" glowColor="teal">
        <div v-if="favoriteItems.length" class="favorite-list">
          <div v-for="item in favoriteItems" :key="`${item.jobId || item.id}`" class="favorite-item">
            <button class="favorite-main" @click="openFavoriteJob(item)">
              <div class="favorite-head">
                <strong>{{ item.title || item.jobTitle || '岗位' }}</strong>
                <span>{{ item.salaryText || item.salary || '薪资未标注' }}</span>
              </div>
              <p>{{ item.companyName || '公司未标注' }} · {{ item.city || '城市未标注' }}</p>
            </button>
            <button class="favorite-delete" @click="handleRemoveFavorite(item.jobId || item.id)">
              <Trash2 :size="16" />
            </button>
          </div>
        </div>
        <EmptyState
          v-else-if="!favoritesLoading"
          icon="briefcase"
          title="暂无收藏职位"
          description="在职位详情里点击收藏后，这里会沉淀成你的岗位池。"
        />
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn" class="grid two-col">
      <PremiumCard title="岗位订阅" glowColor="primary">
        <div class="form-stack">
          <label class="field">
            <span><Radio :size="14" /> 订阅类型</span>
            <input v-model="subscriptionForm.subscriptionType" class="glass-input" />
          </label>
          <label class="field">
            <span><Send :size="14" /> 通知通道</span>
            <select v-model="subscriptionForm.channel" class="glass-input">
              <option value="IN_APP">站内通知</option>
              <option value="WEBHOOK">Webhook</option>
            </select>
          </label>
          <label class="field">
            <span>筛选配置 JSON</span>
            <textarea
              v-model="subscriptionForm.filterConfig"
              class="glass-input glass-textarea"
              rows="4"
              placeholder='{"keyword":"前端","city":"上海","salaryMin":"15k"}'
            />
          </label>
          <GlowButton variant="primary" :loading="subscriptionSaving" @click="handleCreateSubscription">创建订阅</GlowButton>
        </div>

        <div v-if="subscriptionItems.length" class="subscription-list">
          <article v-for="item in subscriptionItems" :key="item.id" class="manage-item">
            <div class="manage-item-head">
              <div>
                <strong>{{ item.subscriptionType || 'JOB_PUSH' }}</strong>
                <p>{{ item.channel || 'IN_APP' }} · {{ summarizeFilterConfig(item.filterConfig) }}</p>
              </div>
              <span class="meta-chip">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div class="inline-actions">
              <button class="mini-action" @click="handlePreviewMatches(item.id)">
                {{ previewingSubscriptionId === item.id ? '预览中...' : '预览匹配' }}
              </button>
              <button class="mini-action" @click="handleDispatchSubscription(item.id)">
                {{ dispatchingSubscriptionId === item.id ? '派发中...' : '派发通知' }}
              </button>
              <button class="mini-action danger" @click="handleDeleteSubscription(item.id)">删除</button>
            </div>
            <div v-if="subscriptionMatches[item.id]?.length" class="match-list">
              <div v-for="match in subscriptionMatches[item.id]" :key="`${item.id}-${match.id || match.jobId}`" class="match-item">
                <strong>{{ match.title || match.jobTitle || '匹配岗位' }}</strong>
                <p>{{ match.companyName || '企业未标注' }} · {{ match.city || '城市未标注' }}</p>
              </div>
            </div>
          </article>
        </div>
        <EmptyState
          v-else-if="!subscriptionsLoading"
          icon="briefcase"
          title="暂无岗位订阅"
          description="可先创建一个简单筛选条件，验证推送链路和匹配结果。"
        />
      </PremiumCard>

      <PremiumCard title="通知中心" glowColor="secondary">
        <div class="panel-caption">
          <span><Bell :size="14" /> 未读 {{ unreadNotificationCount }}</span>
          <button class="mini-action" @click="handleMarkAllNotificationsRead">全部已读</button>
        </div>
        <div v-if="notifications.length" class="notification-list">
          <article
            v-for="item in notifications"
            :key="item.id"
            class="manage-item"
            :class="{ unread: Number(item.isRead) !== 1 }"
          >
            <div class="manage-item-head">
              <div>
                <strong>{{ item.title || item.notifyType || '系统通知' }}</strong>
                <p>{{ item.content || item.message || '暂无内容' }}</p>
              </div>
              <span class="meta-chip">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div class="inline-actions">
              <span class="meta-chip subtle">{{ Number(item.isRead) === 1 ? '已读' : '未读' }}</span>
              <button v-if="Number(item.isRead) !== 1" class="mini-action" @click="handleMarkNotificationRead(item.id)">标记已读</button>
            </div>
          </article>
        </div>
        <EmptyState
          v-else-if="!notificationsLoading"
          icon="bell"
          title="暂无站内通知"
          description="当岗位订阅命中或系统推送到达后，这里会显示最新通知。"
        />
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn" class="grid one-col">
      <PremiumCard title="Webhook 回调" glowColor="teal">
        <div class="webhook-layout">
          <div class="form-stack">
            <label class="field">
              <span><Webhook :size="14" /> 回调地址</span>
              <input v-model="webhookForm.endpointUrl" class="glass-input" placeholder="https://example.com/hooks/job" />
            </label>
            <label class="field">
              <span>事件类型</span>
              <input v-model="webhookForm.eventTypes" class="glass-input" placeholder="JOB_MATCH,NOTIFICATION_CREATED" />
            </label>
            <GlowButton variant="secondary" :loading="webhookSaving" @click="handleCreateWebhook">创建 Webhook</GlowButton>
          </div>

          <div v-if="webhookItems.length" class="subscription-list">
            <article v-for="item in webhookItems" :key="item.id" class="manage-item">
              <div class="manage-item-head">
                <div>
                  <strong>{{ item.endpointUrl }}</strong>
                  <p>{{ item.eventTypes || '未配置事件' }} · Secret {{ item.secretKey || '****' }}</p>
                </div>
                <span class="meta-chip" :class="{ active: Number(item.isActive) === 1 }">
                  {{ Number(item.isActive) === 1 ? '启用中' : '已停用' }}
                </span>
              </div>
              <div class="inline-actions">
                <button class="mini-action" @click="handleLoadWebhookDeliveries(item.id)">
                  {{ deliveryWebhookId === item.id ? '加载中...' : '查看投递' }}
                </button>
                <button class="mini-action" @click="handleToggleWebhook(item.id)">
                  {{ Number(item.isActive) === 1 ? '停用' : '启用' }}
                </button>
                <button class="mini-action danger" @click="handleDeleteWebhook(item.id)">删除</button>
              </div>
              <div v-if="webhookDeliveries[item.id]?.length" class="delivery-list">
                <div v-for="delivery in webhookDeliveries[item.id]" :key="delivery.id" class="delivery-item">
                  <strong>{{ delivery.statusCode || delivery.status || 'UNKNOWN' }}</strong>
                  <p>{{ delivery.responseBody || delivery.errorMessage || '无返回内容' }}</p>
                  <span>{{ formatDateTime(delivery.createdAt) }}</span>
                </div>
              </div>
            </article>
          </div>
          <EmptyState
            v-else-if="!webhooksLoading"
            icon="globe"
            title="暂无 Webhook"
            description="如果你要把匹配结果推到外部系统，可以先在这里挂一个调试回调地址。"
          />
        </div>
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.profile-page,
.form-stack,
.grid {
  display: grid;
  gap: 24px;
}

.one-col {
  grid-template-columns: 1fr;
}

.two-col {
  grid-template-columns: 1fr 1fr;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.advisory-grid,
.advisory-list,
.advisory-section,
.favorite-list,
.subscription-list,
.notification-list,
.match-list,
.delivery-list,
.webhook-layout {
  display: grid;
  gap: 16px;
}

.advisory-score {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.advisory-metric,
.advisory-item,
.advisory-action {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.advisory-metric span,
.advisory-action span,
.advisory-item {
  color: var(--c-text-secondary);
}

.advisory-metric strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  line-height: 1;
}

.advisory-section h3,
.advisory-action strong {
  color: var(--c-text-primary);
}

.advisory-action {
  display: grid;
  gap: 8px;
  text-align: left;
}

.favorite-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.favorite-main {
  display: grid;
  gap: 6px;
  text-align: left;
}

.favorite-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--c-text-primary);
}

.favorite-main p {
  color: var(--c-text-secondary);
}

.favorite-delete {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.18);
  background: rgba(239, 68, 68, 0.08);
}

.glass-textarea {
  min-height: 110px;
  resize: vertical;
}

.panel-caption,
.manage-item-head,
.inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.panel-caption {
  margin-bottom: 16px;
}

.manage-item {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.manage-item.unread {
  border-color: rgba(59, 130, 246, 0.28);
  background: rgba(59, 130, 246, 0.06);
}

.manage-item p,
.delivery-item p {
  color: var(--c-text-secondary);
}

.mini-action,
.meta-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-secondary);
}

.mini-action:hover {
  color: var(--c-text-primary);
  border-color: rgba(59, 130, 246, 0.22);
}

.mini-action.danger {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.2);
}

.meta-chip {
  font-size: 12px;
}

.meta-chip.active {
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.24);
}

.meta-chip.subtle {
  opacity: 0.8;
}

.match-item,
.delivery-item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.delivery-item span {
  color: var(--c-text-muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .two-col,
  .advisory-score {
    grid-template-columns: 1fr;
  }

  .favorite-item {
    grid-template-columns: 1fr;
  }
}
</style>
