<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import EmptyState from '../components/common/EmptyState.vue'
import {
  changeAuthPassword,
  confirmPasswordReset,
  createSubscription,
  createWebhook,
  deleteSubscription,
  deleteWebhook,
  fetchCareerProfile,
  fetchFavorites,
  fetchNotifications,
  fetchPlatformAdvisory,
  fetchAuthCaptcha,
  fetchSubscriptionMatches,
  fetchSubscriptions,
  fetchWebhookDeliveries,
  fetchWebhooks,
  login,
  markAllNotificationsRead,
  markNotificationRead,
  normalizeError,
  requestPasswordReset,
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
  Mail,
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
  password: '',
  captchaId: '',
  captchaCode: '',
  captchaPrompt: ''
})

const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  roleType: 0,
  captchaId: '',
  captchaCode: '',
  captchaPrompt: ''
})

const resetForm = ref({
  username: '',
  email: '',
  captchaId: '',
  captchaCode: '',
  captchaPrompt: '',
  resetToken: '',
  newPassword: '',
  confirmPassword: '',
  maskedEmail: ''
})

const resetStep = ref(1)
const resetLoading = ref(false)
const showResetPanel = ref(false)

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

const subscriptionBuilder = ref({
  keyword: '',
  city: '',
  salaryMin: '',
  salaryMax: '',
  experience: '',
  skillsText: ''
})

const webhookForm = ref({
  endpointUrl: '',
  eventTypes: 'JOB_MATCH,NOTIFICATION_CREATED'
})

const subscriptionTypeOptions = [
  { value: 'JOB_PUSH', label: '岗位推送', description: '按关键词、城市和薪资筛选岗位' },
  { value: 'REPORT_WEEKLY', label: '报告提醒', description: '适合后续扩展为周期性报告提醒' },
  { value: 'SKILL_UPDATE', label: '技能变动', description: '适合后续扩展为技能缺口提醒' }
]

const subscriptionChannelOptions = [
  { value: 'IN_APP', label: '站内通知', description: '消息进入通知中心，适合个人使用' },
  { value: 'WEBHOOK', label: 'Webhook 回调', description: '命中结果推送到你的外部系统地址' }
]

const webhookEventOptions = ['JOB_MATCH', 'NOTIFICATION_CREATED', 'REPORT_READY']

const currentRoleLabel = computed(() => getRoleLabel(authStore.user?.roleType ?? 0))
const redirectTarget = computed(() => String(route.query.redirect || '/'))
const loginCaptchaReady = computed(() => Boolean(loginForm.value.captchaId && loginForm.value.captchaPrompt))
const loginSubmitDisabled = computed(() => {
  if (authLoading.value) return true
  if (!trimField(loginForm.value.username)) return true
  if (!loginForm.value.password) return true
  if (!loginCaptchaReady.value) return true
  if (!trimField(loginForm.value.captchaCode)) return true
  return false
})
const activeProfileTab = ref('account')
const profileTabs = [
  { key: 'account', label: '账户资料', icon: UserRound },
  { key: 'advisory', label: '平台建议', icon: ShieldCheck },
  { key: 'favorites', label: '我的收藏', icon: BriefcaseBusiness },
  { key: 'subscriptions', label: '岗位订阅', icon: Radio },
  { key: 'notifications', label: '通知中心', icon: Bell },
  { key: 'webhooks', label: 'Webhook', icon: Webhook }
]
const activeProfileTabMeta = computed(() => profileTabs.find((item) => item.key === activeProfileTab.value) || profileTabs[0])
const profileTabDescription = computed(() => {
  if (activeProfileTab.value === 'account') return '维护基础资料、职业画像与账号安全设置。'
  if (activeProfileTab.value === 'advisory') return '查看平台对当前职业画像的评估、风险和下一步建议。'
  if (activeProfileTab.value === 'favorites') return '集中查看已收藏岗位，快速回到职位详情继续处理。'
  if (activeProfileTab.value === 'subscriptions') return '配置岗位订阅条件，预览匹配结果并执行通知派发。'
  if (activeProfileTab.value === 'notifications') return '统一处理站内通知和未读消息。'
  return '管理回调地址、事件类型和最近的投递记录。'
})
const profileTabStatus = computed(() => {
  if (activeProfileTab.value === 'account') return profileLoaded.value ? '已加载' : '待加载'
  if (activeProfileTab.value === 'advisory') return advisory.value ? '已生成' : '暂无数据'
  if (activeProfileTab.value === 'favorites') return `${favoriteItems.value.length} 项`
  if (activeProfileTab.value === 'subscriptions') return `${subscriptionItems.value.length} 条`
  if (activeProfileTab.value === 'notifications') return `${unreadNotificationCount.value} 条未读`
  return `${webhookItems.value.length} 个`
})

const activeWebhookCount = computed(() => webhookItems.value.filter((item) => Number(item.isActive) === 1).length)
const subscriptionRequiresWebhook = computed(() => subscriptionForm.value.channel === 'WEBHOOK')
const hasActiveWebhook = computed(() => activeWebhookCount.value > 0)
const builtSubscriptionFilter = computed(() => {
  const filters = {}
  if (trimField(subscriptionBuilder.value.keyword)) filters.keyword = trimField(subscriptionBuilder.value.keyword)
  if (trimField(subscriptionBuilder.value.city)) filters.city = trimField(subscriptionBuilder.value.city)
  if (trimField(subscriptionBuilder.value.experience)) filters.experience = trimField(subscriptionBuilder.value.experience)
  if (trimField(subscriptionBuilder.value.skillsText)) {
    filters.skills = trimField(subscriptionBuilder.value.skillsText)
      .split(/[\s,，、]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }

  const salaryMin = Number(subscriptionBuilder.value.salaryMin)
  const salaryMax = Number(subscriptionBuilder.value.salaryMax)
  if (!Number.isNaN(salaryMin) && salaryMin > 0) filters.salaryMin = salaryMin
  if (!Number.isNaN(salaryMax) && salaryMax > 0) filters.salaryMax = salaryMax
  return filters
})
const subscriptionSummaryCards = computed(() => [
  { label: '订阅总数', value: `${subscriptionItems.value.length}` },
  { label: '活跃 Webhook', value: `${activeWebhookCount.value}` },
  { label: '未读通知', value: `${unreadNotificationCount.value}` }
])

function trimField(value) {
  return `${value || ''}`.trim()
}

function syncSubscriptionFilterConfig() {
  subscriptionForm.value.filterConfig = JSON.stringify(builtSubscriptionFilter.value)
}

function resetSubscriptionBuilder() {
  subscriptionBuilder.value = {
    keyword: '',
    city: '',
    salaryMin: '',
    salaryMax: '',
    experience: '',
    skillsText: ''
  }
  syncSubscriptionFilterConfig()
}

function formatSubscriptionTypeLabel(value) {
  return subscriptionTypeOptions.find((item) => item.value === value)?.label || value || '未设置'
}

function formatSubscriptionChannelLabel(value) {
  return subscriptionChannelOptions.find((item) => item.value === value)?.label || value || '未设置'
}

function formatWebhookEvents(value) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function openNotificationTarget(item) {
  if (!item) return
  const refId = Number(item.refId || item.bizId || 0)
  const type = String(item.notifyType || '').toUpperCase()
  if (type.includes('REPORT')) {
    router.push('/reports')
    return
  }
  if (refId > 0) {
    router.push({ path: '/jobs', query: { open: refId } })
    return
  }
  router.push('/reports')
}

watch(subscriptionBuilder, () => {
  syncSubscriptionFilterConfig()
}, { deep: true, immediate: true })

async function ensureLoginCaptchaReady() {
  loginForm.value.username = trimField(loginForm.value.username)
  loginForm.value.captchaCode = trimField(loginForm.value.captchaCode)

  if (!loginForm.value.username) {
    error('璇疯緭鍏ョ敤鎴峰悕')
    return false
  }

  if (!loginForm.value.password) {
    error('璇疯緭鍏ュ瘑鐮?')
    return false
  }

  if (!loginForm.value.captchaId || !loginForm.value.captchaPrompt) {
    await refreshCaptcha('login')
    error('楠岃瘉鐮佹湭灏辩华锛岃鍒锋柊鍚庨噸璇?')
    return false
  }

  if (!loginForm.value.captchaCode) {
    await refreshCaptcha('login')
    error('璇疯緭鍏ラ獙璇佺爜')
    return false
  }

  return true
}

function handleLoginCaptchaInput() {
  loginForm.value.captchaCode = trimField(loginForm.value.captchaCode)
}

async function handleLogin() {
  if (!(await ensureLoginCaptchaReady())) {
    return
  }

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
    await refreshCaptcha('login')
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
      roleType: registerForm.value.roleType,
      captchaId: registerForm.value.captchaId,
      captchaCode: registerForm.value.captchaCode
    })
    success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.value.username = registerForm.value.username
    loginForm.value.password = ''
    await refreshCaptcha('register')
    await refreshCaptcha('login')
  } catch (e) {
    error(normalizeError(e))
    await refreshCaptcha('register')
  } finally {
    authLoading.value = false
  }
}

async function refreshCaptcha(target = 'register') {
  try {
    const data = await fetchAuthCaptcha()
    if (target === 'login') {
      loginForm.value.captchaId = data.captchaId || ''
      loginForm.value.captchaPrompt = data.captchaPrompt || ''
      loginForm.value.captchaCode = ''
      return
    }
    if (target === 'reset') {
      resetForm.value.captchaId = data.captchaId || ''
      resetForm.value.captchaPrompt = data.captchaPrompt || ''
      resetForm.value.captchaCode = ''
      return
    }
    registerForm.value.captchaId = data.captchaId || ''
    registerForm.value.captchaPrompt = data.captchaPrompt || ''
    registerForm.value.captchaCode = ''
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleRequestPasswordReset() {
  resetLoading.value = true
  try {
    const result = await requestPasswordReset({
      username: resetForm.value.username,
      email: resetForm.value.email,
      captchaId: resetForm.value.captchaId,
      captchaCode: resetForm.value.captchaCode
    })
    resetForm.value.resetToken = result.resetToken || ''
    resetForm.value.maskedEmail = result.maskedEmail || ''
    resetStep.value = 2
    success(`身份校验通过${result.maskedEmail ? `，已绑定邮箱 ${result.maskedEmail}` : ''}`)
    await refreshCaptcha('reset')
  } catch (e) {
    error(normalizeError(e))
    await refreshCaptcha('reset')
  } finally {
    resetLoading.value = false
  }
}

async function handleConfirmPasswordReset() {
  if (resetForm.value.newPassword !== resetForm.value.confirmPassword) {
    error('两次输入的新密码不一致')
    return
  }
  resetLoading.value = true
  try {
    await confirmPasswordReset({
      resetToken: resetForm.value.resetToken,
      newPassword: resetForm.value.newPassword
    })
    success('密码重置成功，请使用新密码登录')
    loginForm.value.username = resetForm.value.username
    loginForm.value.password = ''
    showResetPanel.value = false
    resetStep.value = 1
    resetForm.value = {
      username: '',
      email: '',
      captchaId: '',
      captchaCode: '',
      captchaPrompt: '',
      resetToken: '',
      newPassword: '',
      confirmPassword: '',
      maskedEmail: ''
    }
    await refreshCaptcha('login')
    await refreshCaptcha('reset')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    resetLoading.value = false
  }
}

function captchaGlyphs(prompt) {
  const raw = String(prompt || '').replace(/\s+/g, ' ').trim()
  if (!raw) return []
  const body = raw.includes(':') ? raw.split(':').slice(1).join(':').trim() : raw
  return body.split(' ').filter(Boolean)
}

function captchaGlyphStyle(index) {
  const palette = ['#2563eb', '#0891b2', '#7c3aed', '#ea580c', '#16a34a']
  return {
    color: palette[index % palette.length],
    transform: `rotate(${index % 2 === 0 ? -8 : 7}deg) translateY(${index % 3 === 0 ? -2 : 2}px)`
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
  if (subscriptionRequiresWebhook.value && !hasActiveWebhook.value) {
    error('当前选择了 Webhook 推送，但还没有可用的 Webhook 地址')
    activeProfileTab.value = 'webhooks'
    return
  }
  subscriptionSaving.value = true
  try {
    await createSubscription(authStore.token, {
      ...subscriptionForm.value,
      filterConfig: JSON.stringify(builtSubscriptionFilter.value)
    })
    success('岗位订阅已创建')
    resetSubscriptionBuilder()
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
  webhookForm.value.endpointUrl = trimField(webhookForm.value.endpointUrl)
  webhookForm.value.eventTypes = formatWebhookEvents(webhookForm.value.eventTypes).join(',')
  if (!/^https?:\/\//i.test(webhookForm.value.endpointUrl)) {
    error('Webhook 地址必须以 http:// 或 https:// 开头')
    return
  }
  webhookSaving.value = true
  try {
    await createWebhook(authStore.token, webhookForm.value)
    success('Webhook 已创建')
    webhookForm.value.endpointUrl = ''
    webhookForm.value.eventTypes = 'JOB_MATCH,NOTIFICATION_CREATED'
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
  if (!authStore.isLoggedIn) {
    await Promise.all([refreshCaptcha('login'), refreshCaptcha('register'), refreshCaptcha('reset')])
    return
  }
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

    <section v-if="authStore.isLoggedIn" class="profile-workspace glass-panel">
      <div class="profile-workspace-head">
        <div class="profile-workspace-copy">
          <h2 class="profile-workspace-title">{{ activeProfileTabMeta.label }}</h2>
          <p class="profile-workspace-sub">{{ profileTabDescription }}</p>
        </div>
        <div class="profile-workspace-meta">
          <div class="workspace-meta-item">
            <span>当前功能</span>
            <strong>{{ activeProfileTabMeta.label }}</strong>
          </div>
          <div class="workspace-meta-item">
            <span>状态</span>
            <strong>{{ profileTabStatus }}</strong>
          </div>
        </div>
      </div>

      <div class="profile-tabs" role="tablist">
        <button
          v-for="tab in profileTabs"
          :key="tab.key"
          class="profile-tab"
          :class="{ active: activeProfileTab === tab.key }"
          type="button"
          role="tab"
          :aria-selected="activeProfileTab === tab.key"
          @click="activeProfileTab = tab.key"
        >
          <component :is="tab.icon" :size="14" />
          <span>{{ tab.label }}</span>
        </button>
      </div>
    </section>

    <section v-if="!authStore.isLoggedIn" class="grid two-col">
      <PremiumCard title="登录" glowColor="primary">
        <div class="overview-grid">
          <div v-for="item in subscriptionSummaryCards" :key="item.label" class="overview-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="helper-panel">
          <strong>订阅说明</strong>
          <p>订阅会根据你配置的条件筛选岗位。你可以先预览匹配结果，再决定是否立即推送。</p>
          <pre class="config-preview">{{ subscriptionForm.filterConfig }}</pre>
        </div>
        <div class="grid two-col">
          <label class="field">
            <span>关键词</span>
            <input v-model="subscriptionBuilder.keyword" class="glass-input" placeholder="如：前端 / Java / 数据分析" />
          </label>
          <label class="field">
            <span>城市</span>
            <input v-model="subscriptionBuilder.city" class="glass-input" placeholder="如：成都 / 上海" />
          </label>
          <label class="field">
            <span>最低薪资</span>
            <input v-model="subscriptionBuilder.salaryMin" type="number" min="0" class="glass-input" placeholder="例如 15" />
          </label>
          <label class="field">
            <span>最高薪资</span>
            <input v-model="subscriptionBuilder.salaryMax" type="number" min="0" class="glass-input" placeholder="例如 30" />
          </label>
          <label class="field">
            <span>经验要求</span>
            <input v-model="subscriptionBuilder.experience" class="glass-input" placeholder="如：应届 / 1-3年" />
          </label>
          <label class="field">
            <span>技能关键词</span>
            <input v-model="subscriptionBuilder.skillsText" class="glass-input" placeholder="多个技能用空格或逗号分隔" />
          </label>
        </div>
        <div class="overview-grid">
          <div v-for="item in subscriptionSummaryCards" :key="item.label" class="overview-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="helper-panel">
          <strong>订阅说明</strong>
          <p>系统会根据你的筛选条件去匹配岗位。预览只看结果，立即推送会写入通知中心，并在启用 Webhook 时同步回调到外部系统。</p>
          <pre class="config-preview">{{ subscriptionForm.filterConfig }}</pre>
        </div>
        <div class="grid two-col">
          <label class="field">
            <span>关键词</span>
            <input v-model="subscriptionBuilder.keyword" class="glass-input" placeholder="如：前端 / Java / 数据分析" />
          </label>
          <label class="field">
            <span>城市</span>
            <input v-model="subscriptionBuilder.city" class="glass-input" placeholder="如：成都 / 上海" />
          </label>
          <label class="field">
            <span>最低薪资</span>
            <input v-model="subscriptionBuilder.salaryMin" type="number" min="0" class="glass-input" placeholder="例如 15" />
          </label>
          <label class="field">
            <span>最高薪资</span>
            <input v-model="subscriptionBuilder.salaryMax" type="number" min="0" class="glass-input" placeholder="例如 30" />
          </label>
          <label class="field">
            <span>经验要求</span>
            <input v-model="subscriptionBuilder.experience" class="glass-input" placeholder="如：应届 / 1-3年" />
          </label>
          <label class="field">
            <span>技能关键词</span>
            <input v-model="subscriptionBuilder.skillsText" class="glass-input" placeholder="多个技能用空格或逗号分隔" />
          </label>
        </div>
        <div class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 用户名</span>
            <input v-model="loginForm.username" class="glass-input" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span><KeyRound :size="14" /> 密码</span>
            <input v-model="loginForm.password" type="password" class="glass-input" placeholder="请输入密码" />
          </label>
          <label class="field">
            <span><ShieldCheck :size="14" /> 验证码</span>
            <div class="captcha-row">
              <div class="captcha-visual" :class="{ 'is-empty': !loginCaptchaReady }" aria-hidden="true">
                <span
                  v-for="(glyph, index) in captchaGlyphs(loginForm.captchaPrompt)"
                  :key="`login-${index}-${glyph}`"
                  class="captcha-glyph"
                  :style="captchaGlyphStyle(index)"
                >
                  {{ glyph }}
                </span>
              </div>
              <button class="mini-action" type="button" @click="refreshCaptcha('login')">刷新</button>
            </div>
            <input v-model="loginForm.captchaCode" class="glass-input" :placeholder="loginForm.captchaPrompt || '请输入验证码结果'" />
            <p class="field-hint" :class="{ 'is-danger': !loginCaptchaReady || !trimField(loginForm.captchaCode) }">
              {{
                !loginCaptchaReady
                  ? '验证码未就绪，请先刷新获取后再登录'
                  : !trimField(loginForm.captchaCode)
                    ? '验证码必填，输入错误后系统会刷新新的验证码'
                    : '请输入当前验证码结果，登录失败后会强制刷新验证码'
              }}
            </p>
          </label>
          <GlowButton variant="primary" :loading="authLoading && activeTab === 'login'" :disabled="loginSubmitDisabled" @click="activeTab = 'login'; handleLogin()">
            <LogIn :size="14" /> 登录
          </GlowButton>
          <button class="text-action" type="button" @click="showResetPanel = !showResetPanel">
            {{ showResetPanel ? '收起找回密码' : '忘记密码？找回密码' }}
          </button>
          <div v-if="showResetPanel" class="reset-panel">
            <div class="panel-caption">
              <span><Mail :size="14" /> {{ resetStep === 1 ? '身份校验' : '设置新密码' }}</span>
            </div>
            <template v-if="resetStep === 1">
              <label class="field">
                <span>用户名</span>
                <input v-model="resetForm.username" class="glass-input" placeholder="请输入注册用户名" />
              </label>
              <label class="field">
                <span>注册邮箱</span>
                <input v-model="resetForm.email" class="glass-input" placeholder="请输入注册邮箱" />
              </label>
              <label class="field">
                <span><ShieldCheck :size="14" /> 验证码</span>
                <div class="captcha-row">
                  <div class="captcha-visual" aria-hidden="true">
                    <span
                      v-for="(glyph, index) in captchaGlyphs(resetForm.captchaPrompt)"
                      :key="`reset-${index}-${glyph}`"
                      class="captcha-glyph"
                      :style="captchaGlyphStyle(index)"
                    >
                      {{ glyph }}
                    </span>
                  </div>
                  <button class="mini-action" type="button" @click="refreshCaptcha('reset')">刷新</button>
                </div>
                <input v-model="resetForm.captchaCode" class="glass-input" :placeholder="resetForm.captchaPrompt || '请输入验证码结果'" />
              </label>
              <GlowButton variant="ghost" :loading="resetLoading" @click="handleRequestPasswordReset">
                校验身份并获取重置令牌
              </GlowButton>
            </template>
            <template v-else>
              <div class="reset-hint">
                已通过邮箱校验<span v-if="resetForm.maskedEmail">：{{ resetForm.maskedEmail }}</span>
              </div>
              <label class="field">
                <span>新密码</span>
                <input v-model="resetForm.newPassword" type="password" class="glass-input" placeholder="至少 8 位，包含字母和数字" />
              </label>
              <label class="field">
                <span>确认新密码</span>
                <input v-model="resetForm.confirmPassword" type="password" class="glass-input" placeholder="请再次输入新密码" />
              </label>
              <div class="inline-actions">
                <GlowButton variant="secondary" :loading="resetLoading" @click="handleConfirmPasswordReset">
                  提交新密码
                </GlowButton>
                <button class="mini-action" type="button" @click="resetStep = 1">返回上一步</button>
              </div>
            </template>
          </div>
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
          <label class="field">
            <span><ShieldCheck :size="14" /> 验证码</span>
            <div class="captcha-row">
              <div class="captcha-visual" aria-hidden="true">
                <span
                  v-for="(glyph, index) in captchaGlyphs(registerForm.captchaPrompt)"
                  :key="`register-${index}-${glyph}`"
                  class="captcha-glyph"
                  :style="captchaGlyphStyle(index)"
                >
                  {{ glyph }}
                </span>
              </div>
              <button class="mini-action" type="button" @click="refreshCaptcha('register')">刷新</button>
            </div>
            <input
              v-model="registerForm.captchaCode"
              class="glass-input"
              :placeholder="registerForm.captchaPrompt || '请输入验证码结果'"
            />
          </label>
          <GlowButton variant="secondary" :loading="authLoading && activeTab === 'register'" @click="activeTab = 'register'; handleRegister()">
            注册账号
          </GlowButton>
        </div>
      </PremiumCard>
    </section>

    <section v-else-if="activeProfileTab === 'account'" class="grid two-col">
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

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'advisory'" class="grid one-col">
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

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'favorites'" class="grid one-col">
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

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'subscriptions'" class="grid one-col">
      <PremiumCard title="岗位订阅" glowColor="primary">
        <div class="form-stack">
          <label class="field">
            <span><Radio :size="14" /> 订阅类型</span>
            <select v-model="subscriptionForm.subscriptionType" class="glass-input">
              <option v-for="item in subscriptionTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
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

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'notifications'" class="grid one-col">
      <PremiumCard title="通知中心" glowColor="secondary">
        <div class="panel-caption">
          <span><Bell :size="14" /> 未读 {{ unreadNotificationCount }}</span>
          <button class="mini-action" @click="handleMarkAllNotificationsRead">全部已读</button>
        </div>
        <div class="helper-panel">
          <strong>通知会从哪里来</strong>
          <p>岗位订阅命中、报告生成完成，以及后续扩展的系统提醒，都会进入这里。</p>
        </div>
        <div v-if="notifications.length" class="notification-list">
          <article
            v-for="item in notifications"
            :key="`tab-${item.id}`"
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
              <button class="mini-action" @click="openNotificationTarget(item)">查看关联内容</button>
              <button v-if="Number(item.isRead) !== 1" class="mini-action" @click="handleMarkNotificationRead(item.id)">标记已读</button>
            </div>
          </article>
        </div>
        <EmptyState
          v-else-if="!notificationsLoading"
          icon="bell"
          title="暂无站内通知"
          description="当岗位订阅命中或报告生成完成后，这里会显示最新通知。"
        />
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'webhooks'" class="grid one-col">
      <PremiumCard title="Webhook 回调" glowColor="teal">
        <div class="webhook-layout">
          <div class="form-stack">
            <div class="helper-panel">
              <strong>Webhook 是什么</strong>
              <p>Webhook 是系统主动把事件结果推送到你提供的 URL。适合把岗位命中、通知事件同步到企业系统、自动化平台或自己的服务端。</p>
              <div class="chip-row">
                <span v-for="event in webhookEventOptions" :key="event" class="meta-chip subtle">{{ event }}</span>
              </div>
            </div>
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

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.captcha-visual {
  min-height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px dashed color-mix(in srgb, var(--c-accent-primary) 30%, transparent);
  background:
    radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--c-accent-secondary, #38bdf8) 18%, transparent), transparent 30%),
    radial-gradient(circle at 80% 30%, color-mix(in srgb, var(--c-accent-primary, #a855f7) 14%, transparent), transparent 25%),
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--c-bg-surface-strong, #0f172a) 76%, transparent),
      color-mix(in srgb, var(--c-bg-surface, #1e293b) 38%, transparent)
    );
  overflow: hidden;
}

.captcha-visual.is-empty {
  border-style: solid;
  border-color: color-mix(in srgb, #f97316 36%, var(--c-border-glass));
  background:
    linear-gradient(
      135deg,
      color-mix(in srgb, #f97316 10%, var(--c-bg-surface-strong, #0f172a)),
      color-mix(in srgb, #fb923c 6%, var(--c-bg-surface, #1e293b))
    );
}

.captcha-glyph {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-shadow: 0 6px 18px color-mix(in srgb, var(--c-text-primary, #0f172a) 24%, transparent);
}

.text-action {
  justify-self: start;
  color: var(--c-accent-primary);
  font-size: 13px;
}

.reset-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--c-accent-primary) 24%, transparent);
  background: color-mix(in srgb, var(--c-accent-primary) 8%, var(--c-bg-surface, transparent));
}

.reset-hint {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.04));
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.glass-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--c-accent-primary) 32%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-accent-primary) 12%, transparent);
}

.glass-input:disabled {
  cursor: not-allowed;
  opacity: 0.7;
  background: color-mix(in srgb, var(--c-bg-surface, rgba(255, 255, 255, 0.04)) 84%, transparent);
}

.input-invalid {
  border-color: color-mix(in srgb, #f97316 34%, var(--c-border-glass));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #f97316 8%, transparent);
}

.field-hint {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.field-hint.is-danger {
  color: #c2410c;
}

.advisory-grid,
.advisory-list,
.advisory-section,
.favorite-list,
.subscription-list,
.notification-list,
.match-list,
.delivery-list,
.webhook-layout,
.overview-grid,
.chip-row {
  display: grid;
  gap: 16px;
}

.overview-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.overview-item,
.helper-panel {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.03));
}

.overview-item span,
.helper-panel p,
.config-preview {
  color: var(--c-text-secondary);
}

.overview-item strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.helper-panel {
  display: grid;
  gap: 10px;
}

.helper-danger {
  margin: 0;
  color: #c2410c;
  font-size: 13px;
}

.chip-row {
  grid-template-columns: repeat(auto-fit, minmax(120px, max-content));
}

.config-preview {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--c-bg-base-elevated, rgba(255,255,255,0.04)) 92%, transparent);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}

.meta-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
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
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.03));
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

.advisory-action:hover,
.favorite-item:hover,
.manage-item:hover,
.match-item:hover,
.delivery-item:hover {
  border-color: color-mix(in srgb, var(--c-accent-primary) 22%, var(--c-border-glass));
}

.favorite-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.03));
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
  border: 1px solid color-mix(in srgb, #ef4444 24%, transparent);
  background: color-mix(in srgb, #ef4444 12%, transparent);
}

.favorite-delete:hover {
  background: color-mix(in srgb, #ef4444 18%, transparent);
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
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.03));
}

.manage-item.unread {
  border-color: color-mix(in srgb, var(--c-accent-primary) 28%, transparent);
  background: color-mix(in srgb, var(--c-accent-primary) 8%, var(--c-bg-surface, transparent));
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
  background: var(--c-bg-base-elevated, rgba(255, 255, 255, 0.04));
  color: var(--c-text-secondary);
}

.mini-action:hover {
  color: var(--c-text-primary);
  border-color: color-mix(in srgb, var(--c-accent-primary) 24%, transparent);
  background: color-mix(in srgb, var(--c-accent-primary) 8%, transparent);
}

.mini-action.danger {
  color: #ef4444;
  border-color: color-mix(in srgb, #ef4444 24%, transparent);
}

.meta-chip {
  font-size: 12px;
}

.meta-chip.active {
  color: #10b981;
  border-color: color-mix(in srgb, #10b981 28%, transparent);
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
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.03));
}

.delivery-item span {
  color: var(--c-text-muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .two-col,
  .advisory-score,
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .favorite-item {
    grid-template-columns: 1fr;
  }
}
</style>
