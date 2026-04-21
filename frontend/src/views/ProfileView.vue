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
  deleteSubscription,
  fetchAuthCaptcha,
  fetchCareerProfile,
  fetchFavorites,
  fetchNotifications,
  fetchPlatformAdvisory,
  fetchSubscriptionMatches,
  fetchSubscriptionMeta,
  fetchSubscriptions,
  login,
  markAllNotificationsRead,
  markNotificationRead,
  normalizeError,
  removeFavorite,
  requestPasswordReset,
  register,
  dispatchSubscriptionMatches,
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
  UserRound
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { success, error } = useToast()

const activeTab = ref(route.query.login === 'true' ? 'login' : 'register')
const activeProfileTab = ref('account')

const authLoading = ref(false)
const profileLoading = ref(false)
const passwordLoading = ref(false)
const resetLoading = ref(false)
const favoritesLoading = ref(false)
const subscriptionsLoading = ref(false)
const subscriptionSaving = ref(false)
const notificationsLoading = ref(false)

const profileLoaded = ref(false)
const showResetPanel = ref(false)
const resetStep = ref(1)
const previewingSubscriptionId = ref(null)
const dispatchingSubscriptionId = ref(null)
const unreadNotificationCount = ref(0)

const advisory = ref(null)
const favoriteItems = ref([])
const subscriptionItems = ref([])
const subscriptionMatches = ref({})
const notifications = ref([])
const subscriptionMeta = ref({ channels: [], mailEnabled: false })

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
  channel: 'EMAIL',
  filterConfig: '{}'
})

const subscriptionBuilder = ref({
  keyword: '',
  city: '',
  salaryMin: '',
  salaryMax: '',
  experience: '',
  skillsText: ''
})

const subscriptionTypeOptions = [
  { value: 'JOB_PUSH', label: '岗位推荐', description: '按岗位方向、城市和薪资范围订阅推荐结果。' },
  { value: 'REPORT_WEEKLY', label: '报告提醒', description: '适合接收阶段性分析报告和进度提醒。' },
  { value: 'SKILL_UPDATE', label: '技能提醒', description: '用于关注技能缺口和岗位能力变化。' }
]

const quickKeywordTags = ['前端', 'Java', '数据分析', '产品经理', '测试开发', '运营']
const quickCityTags = ['成都', '上海', '北京', '深圳', '杭州', '武汉']
const quickExperienceTags = ['应届', '1-3年', '3-5年', '5年以上']
const quickSkillTags = ['Vue', 'React', 'Spring Boot', 'Python', 'MySQL', 'Excel']

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

const profileTabs = [
  { key: 'account', label: '账户资料', icon: UserRound },
  { key: 'advisory', label: '平台建议', icon: ShieldCheck },
  { key: 'favorites', label: '我的收藏', icon: BriefcaseBusiness },
  { key: 'subscriptions', label: '岗位订阅', icon: Radio },
  { key: 'notifications', label: '邮箱通知', icon: Mail }
]

const activeProfileTabMeta = computed(() => {
  return profileTabs.find((item) => item.key === activeProfileTab.value) || profileTabs[0]
})

const profileTabDescription = computed(() => {
  if (activeProfileTab.value === 'account') return '维护基础资料、职业画像和账号安全。'
  if (activeProfileTab.value === 'advisory') return '查看平台对你当前职业画像的分析、风险和建议。'
  if (activeProfileTab.value === 'favorites') return '集中查看已收藏岗位，回到职位详情继续处理。'
  if (activeProfileTab.value === 'subscriptions') return '设置岗位订阅条件，预览匹配结果并发送邮箱通知。'
  return '查看最近生成的通知记录，以及邮箱发送结果对应的业务提醒。'
})

const profileTabStatus = computed(() => {
  if (activeProfileTab.value === 'account') return profileLoaded.value ? '已加载' : '待加载'
  if (activeProfileTab.value === 'advisory') return advisory.value ? '已生成' : '暂无数据'
  if (activeProfileTab.value === 'favorites') return `${favoriteItems.value.length} 项`
  if (activeProfileTab.value === 'subscriptions') return `${subscriptionItems.value.length} 条`
  return `${unreadNotificationCount.value} 条未读`
})

const subscriptionChannelOptions = computed(() => {
  const channels = Array.isArray(subscriptionMeta.value.channels) ? subscriptionMeta.value.channels : []
  const emailOption = channels.find((item) => item?.value === 'EMAIL')
  if (emailOption) {
    return [
      {
        value: 'EMAIL',
        label: emailOption.label || '邮箱通知',
        description: emailOption.description || '订阅命中后会发送到你当前绑定的邮箱。',
        disabled: emailOption.enabled === false
      }
    ]
  }
  return [
    {
      value: 'EMAIL',
      label: '邮箱通知',
      description: '当前还没有配置邮件服务，暂时不能发送邮箱通知。',
      disabled: true
    }
  ]
})

const selectedSubscriptionChannel = computed(() => {
  return subscriptionChannelOptions.value.find((item) => item.value === subscriptionForm.value.channel) || null
})

const subscriptionRequiresEmail = computed(() => subscriptionForm.value.channel === 'EMAIL')
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

const hasSubscriptionFilters = computed(() => Object.keys(builtSubscriptionFilter.value).length > 0)
const subscriptionSummaryCards = computed(() => [
  { label: '订阅总数', value: `${subscriptionItems.value.length}` },
  { label: '邮箱服务', value: subscriptionMeta.value.mailEnabled ? '已开启' : '未开启' },
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

function toggleSubscriptionField(field, value) {
  subscriptionBuilder.value[field] = subscriptionBuilder.value[field] === value ? '' : value
}

function skillList() {
  return trimField(subscriptionBuilder.value.skillsText)
    .split(/[\s,，、]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function toggleSkillTag(tag) {
  const selected = skillList()
  subscriptionBuilder.value.skillsText = selected.includes(tag)
    ? selected.filter((item) => item !== tag).join(' ')
    : [...selected, tag].join(' ')
}

function hasSkillTag(tag) {
  return skillList().includes(tag)
}

function formatSubscriptionTypeLabel(value) {
  return subscriptionTypeOptions.find((item) => item.value === value)?.label || value || '未设置'
}

function formatSubscriptionChannelLabel(value) {
  return subscriptionChannelOptions.value.find((item) => item.value === value)?.label || value || '未设置'
}

function formatSubscriptionFilterPreview(filters) {
  const segments = []
  if (filters.keyword) segments.push(`岗位方向：${filters.keyword}`)
  if (filters.city) segments.push(`意向城市：${filters.city}`)
  if (filters.salaryMin || filters.salaryMax) {
    const min = filters.salaryMin ? `${filters.salaryMin}K` : '不限'
    const max = filters.salaryMax ? `${filters.salaryMax}K` : '不限'
    segments.push(`薪资范围：${min} - ${max}`)
  }
  if (filters.experience) segments.push(`经验要求：${filters.experience}`)
  if (Array.isArray(filters.skills) && filters.skills.length) {
    segments.push(`技能标签：${filters.skills.join('、')}`)
  }
  return segments
}

function getNotificationTypeLabel(item) {
  const type = String(item?.notifyType || '').toUpperCase()
  if (type.includes('JOB')) return '岗位订阅'
  if (type.includes('REPORT')) return '报告提醒'
  if (type.includes('REVIEW')) return '审核提醒'
  return '系统通知'
}

function getNotificationTitle(item) {
  return trimField(item?.title) || getNotificationTypeLabel(item)
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

async function ensureLoginCaptchaReady() {
  loginForm.value.username = trimField(loginForm.value.username)
  loginForm.value.captchaCode = trimField(loginForm.value.captchaCode)

  if (!loginForm.value.username) {
    error('请输入用户名')
    return false
  }
  if (!loginForm.value.password) {
    error('请输入密码')
    return false
  }
  if (!loginForm.value.captchaId || !loginForm.value.captchaPrompt) {
    await refreshCaptcha('login')
    error('验证码未准备好，请刷新后重试')
    return false
  }
  if (!loginForm.value.captchaCode) {
    await refreshCaptcha('login')
    error('请输入验证码')
    return false
  }
  return true
}

function handleLoginCaptchaInput() {
  loginForm.value.captchaCode = trimField(loginForm.value.captchaCode)
}

async function handleLogin() {
  if (!(await ensureLoginCaptchaReady())) return
  authLoading.value = true
  try {
    const result = await login(loginForm.value)
    authStore.setAuth(result.accessToken || result.token || '', result.userInfo || result.user || result)
    await authStore.syncProfile()
    await bootstrapProfile()
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
    await Promise.all([refreshCaptcha('register'), refreshCaptcha('login')])
  } catch (e) {
    error(normalizeError(e))
    await refreshCaptcha('register')
  } finally {
    authLoading.value = false
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
    await Promise.all([refreshCaptcha('login'), refreshCaptcha('reset')])
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
  } catch {
    profileForm.value = {
      username: authStore.user?.username || '',
      email: authStore.user?.email || '',
      phone: '',
      major: '',
      school: '',
      expectedCity: ''
    }
  } finally {
    profileLoaded.value = true
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

async function loadSubscriptionMeta() {
  if (!authStore.token) return
  try {
    subscriptionMeta.value = await fetchSubscriptionMeta(authStore.token)
  } catch {
    subscriptionMeta.value = { channels: [], mailEnabled: false }
  }
  if (!subscriptionChannelOptions.value.some((item) => item.value === subscriptionForm.value.channel && !item.disabled)) {
    subscriptionForm.value.channel = 'EMAIL'
  }
}

async function loadNotifications() {
  if (!authStore.token) return
  notificationsLoading.value = true
  try {
    const result = await fetchNotifications(authStore.token, { page: 1, pageSize: 8 })
    notifications.value = (result.data || []).map((item) => ({
      ...item,
      title: getNotificationTitle(item),
      friendlyType: getNotificationTypeLabel(item)
    }))
    unreadNotificationCount.value = Number(result.unreadCount || 0)
  } catch {
    notifications.value = []
    unreadNotificationCount.value = 0
  } finally {
    notificationsLoading.value = false
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
  subscriptionMatches.value = {}
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
  router.push({ path: '/jobs', query: { open: jobId, favorited: '1' } })
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
    const segments = formatSubscriptionFilterPreview(parsed)
    return segments.join(' · ') || JSON.stringify(parsed)
  } catch {
    return String(raw)
  }
}

async function handleCreateSubscription() {
  if (subscriptionRequiresEmail.value && !subscriptionMeta.value.mailEnabled) {
    error('当前邮箱通知还没有配置完成，请先启用邮件服务')
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
    success('订阅已删除')
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
    success(`已发送 ${result.deliveredCount || 0} 条匹配结果`)
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
    unreadNotificationCount.value = Math.max(0, unreadNotificationCount.value - 1)
  } catch (e) {
    error(normalizeError(e))
  }
}

async function handleMarkAllNotificationsRead() {
  try {
    await markAllNotificationsRead(authStore.token)
    notifications.value = notifications.value.map((item) => ({ ...item, isRead: 1 }))
    unreadNotificationCount.value = 0
    success('通知已全部设为已读')
  } catch (e) {
    error(normalizeError(e))
  }
}

async function bootstrapProfile() {
  if (!authStore.isLoggedIn) return
  await Promise.all([
    loadCareerProfile(),
    loadPlatformAdvisory(),
    loadFavorites(),
    loadSubscriptions(),
    loadSubscriptionMeta(),
    loadNotifications()
  ])
}

onMounted(async () => {
  if (!authStore.isLoggedIn) {
    await Promise.all([refreshCaptcha('login'), refreshCaptcha('register'), refreshCaptcha('reset')])
    return
  }
  await bootstrapProfile()
})
</script>

<template>
  <div class="profile-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">{{ authStore.isLoggedIn ? '个人中心' : '账号入口' }}</span>
        <h1 class="page-intro-title">{{ authStore.isLoggedIn ? '账户与职业档案' : '登录或注册平台账号' }}</h1>
        <p class="page-intro-text">
          {{
            authStore.isLoggedIn
              ? '在这里维护基础资料、职业画像、岗位订阅和邮箱通知。'
              : '完成登录后可进入报告中心、推荐中心和个人中心继续操作。'
          }}
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
        <div class="intro-metric" v-if="authStore.isLoggedIn">
          <span class="intro-metric-label">邮箱通知</span>
          <span class="intro-metric-value">{{ subscriptionMeta.mailEnabled ? '已开启' : '未开启' }}</span>
        </div>
      </div>
    </section>

    <section v-if="!authStore.isLoggedIn" class="grid auth-grid">
      <PremiumCard :title="activeTab === 'login' ? '登录账号' : '注册账号'" glowColor="primary">
        <div class="auth-switch">
          <button type="button" class="mini-action" :class="{ active: activeTab === 'login' }" @click="activeTab = 'login'">登录</button>
          <button type="button" class="mini-action" :class="{ active: activeTab === 'register' }" @click="activeTab = 'register'">注册</button>
        </div>

        <div v-if="activeTab === 'login'" class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 用户名</span>
            <input v-model="loginForm.username" class="glass-input" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span><KeyRound :size="14" /> 密码</span>
            <input v-model="loginForm.password" type="password" class="glass-input" placeholder="请输入密码" />
          </label>
          <label class="field">
            <span>验证码</span>
            <div class="captcha-row">
              <input
                v-model="loginForm.captchaCode"
                class="glass-input"
                placeholder="请输入验证码"
                @input="handleLoginCaptchaInput"
              />
              <div class="captcha-visual" :class="{ 'is-empty': !captchaGlyphs(loginForm.captchaPrompt).length }">
                <span
                  v-for="(glyph, index) in captchaGlyphs(loginForm.captchaPrompt)"
                  :key="`${glyph}-${index}`"
                  class="captcha-glyph"
                  :style="captchaGlyphStyle(index)"
                >
                  {{ glyph }}
                </span>
              </div>
            </div>
            <button type="button" class="text-action" @click="refreshCaptcha('login')">看不清？刷新验证码</button>
          </label>
          <GlowButton variant="primary" :loading="authLoading" :disabled="loginSubmitDisabled" @click="handleLogin">
            <LogIn :size="14" />
            登录
          </GlowButton>
          <button type="button" class="text-action" @click="showResetPanel = !showResetPanel">
            {{ showResetPanel ? '收起找回密码' : '忘记密码？找回账号' }}
          </button>

          <div v-if="showResetPanel" class="reset-panel">
            <template v-if="resetStep === 1">
              <label class="field">
                <span>用户名</span>
                <input v-model="resetForm.username" class="glass-input" placeholder="输入要找回的用户名" />
              </label>
              <label class="field">
                <span>绑定邮箱</span>
                <input v-model="resetForm.email" class="glass-input" placeholder="输入注册时填写的邮箱" />
              </label>
              <label class="field">
                <span>验证码</span>
                <div class="captcha-row">
                  <input v-model="resetForm.captchaCode" class="glass-input" placeholder="请输入验证码" />
                  <div class="captcha-visual" :class="{ 'is-empty': !captchaGlyphs(resetForm.captchaPrompt).length }">
                    <span
                      v-for="(glyph, index) in captchaGlyphs(resetForm.captchaPrompt)"
                      :key="`reset-${glyph}-${index}`"
                      class="captcha-glyph"
                      :style="captchaGlyphStyle(index)"
                    >
                      {{ glyph }}
                    </span>
                  </div>
                </div>
                <button type="button" class="text-action" @click="refreshCaptcha('reset')">刷新验证码</button>
              </label>
              <GlowButton variant="secondary" :loading="resetLoading" @click="handleRequestPasswordReset">验证身份</GlowButton>
            </template>

            <template v-else>
              <p class="reset-hint">身份已校验{{ resetForm.maskedEmail ? `，绑定邮箱：${resetForm.maskedEmail}` : '' }}</p>
              <label class="field">
                <span>新密码</span>
                <input v-model="resetForm.newPassword" type="password" class="glass-input" placeholder="请输入新密码" />
              </label>
              <label class="field">
                <span>确认新密码</span>
                <input v-model="resetForm.confirmPassword" type="password" class="glass-input" placeholder="请再次输入新密码" />
              </label>
              <GlowButton variant="primary" :loading="resetLoading" @click="handleConfirmPasswordReset">确认重置</GlowButton>
            </template>
          </div>
        </div>

        <div v-else class="form-stack">
          <label class="field">
            <span><UserRound :size="14" /> 用户名</span>
            <input v-model="registerForm.username" class="glass-input" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span><Mail :size="14" /> 邮箱</span>
            <input v-model="registerForm.email" class="glass-input" placeholder="请输入邮箱" />
          </label>
          <div class="grid two-col">
            <label class="field">
              <span>密码</span>
              <input v-model="registerForm.password" type="password" class="glass-input" placeholder="请输入密码" />
            </label>
            <label class="field">
              <span>确认密码</span>
              <input v-model="registerForm.confirmPassword" type="password" class="glass-input" placeholder="请再次输入密码" />
            </label>
          </div>
          <label class="field">
            <span>角色</span>
            <select v-model="registerForm.roleType" class="glass-input">
              <option :value="0">学生</option>
              <option :value="1">教师</option>
              <option :value="2">企业用户</option>
              <option :value="9">管理员</option>
            </select>
          </label>
          <label class="field">
            <span>验证码</span>
            <div class="captcha-row">
              <input v-model="registerForm.captchaCode" class="glass-input" placeholder="请输入验证码" />
              <div class="captcha-visual" :class="{ 'is-empty': !captchaGlyphs(registerForm.captchaPrompt).length }">
                <span
                  v-for="(glyph, index) in captchaGlyphs(registerForm.captchaPrompt)"
                  :key="`register-${glyph}-${index}`"
                  class="captcha-glyph"
                  :style="captchaGlyphStyle(index)"
                >
                  {{ glyph }}
                </span>
              </div>
            </div>
            <button type="button" class="text-action" @click="refreshCaptcha('register')">看不清？刷新验证码</button>
          </label>
          <GlowButton variant="primary" :loading="authLoading" @click="handleRegister">注册账号</GlowButton>
        </div>
      </PremiumCard>

      <PremiumCard title="登录说明" glowColor="secondary">
        <div class="helper-panel">
          <strong>验证码使用说明</strong>
          <p>登录和注册都要求先输入验证码。验证码错误时，前端会阻止提交并强制刷新新的验证码。</p>
        </div>
        <div class="helper-panel">
          <strong>邮箱通知说明</strong>
          <p>登录后可在个人中心创建岗位订阅。订阅命中后，系统会生成通知记录，并同步发送到你绑定的邮箱。</p>
        </div>
      </PremiumCard>
    </section>

    <section v-else class="grid one-col">
      <PremiumCard :title="activeProfileTabMeta.label" glowColor="secondary">
        <div class="workspace-meta">
          <div class="workspace-meta-item">
            <span>当前模块</span>
            <strong>{{ activeProfileTabMeta.label }}</strong>
          </div>
          <div class="workspace-meta-item">
            <span>说明</span>
            <strong>{{ profileTabDescription }}</strong>
          </div>
          <div class="workspace-meta-item">
            <span>状态</span>
            <strong>{{ profileTabStatus }}</strong>
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
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'account'" class="grid two-col">
      <PremiumCard title="账户资料" glowColor="primary">
        <div class="form-stack">
          <label class="field">
            <span>用户名</span>
            <input v-model="profileForm.username" class="glass-input" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span>邮箱</span>
            <input v-model="profileForm.email" class="glass-input" placeholder="请输入邮箱" />
          </label>
          <label class="field">
            <span>手机号</span>
            <input v-model="profileForm.phone" class="glass-input" placeholder="请输入手机号" />
          </label>
          <label class="field">
            <span>专业</span>
            <input v-model="profileForm.major" class="glass-input" placeholder="如：软件工程" />
          </label>
          <label class="field">
            <span>学校</span>
            <input v-model="profileForm.school" class="glass-input" placeholder="请输入学校名称" />
          </label>
          <label class="field">
            <span>意向城市</span>
            <input v-model="profileForm.expectedCity" class="glass-input" placeholder="如：成都" />
          </label>
          <GlowButton variant="primary" :loading="profileLoading" @click="handleSaveProfile">保存资料</GlowButton>
        </div>
      </PremiumCard>

      <PremiumCard title="账号安全" glowColor="teal">
        <div class="form-stack">
          <div class="helper-panel">
            <strong>当前角色</strong>
            <p>{{ currentRoleLabel }}</p>
          </div>
          <div class="helper-panel">
            <strong>邮箱通知状态</strong>
            <p>{{ subscriptionMeta.mailEnabled ? '已开启，可以接收订阅结果。' : '未开启，订阅创建后无法实际发送邮件。' }}</p>
          </div>
          <label class="field">
            <span>旧密码</span>
            <input v-model="passwordForm.oldPassword" type="password" class="glass-input" placeholder="请输入旧密码" />
          </label>
          <label class="field">
            <span>新密码</span>
            <input v-model="passwordForm.newPassword" type="password" class="glass-input" placeholder="请输入新密码" />
          </label>
          <label class="field">
            <span>确认新密码</span>
            <input v-model="passwordForm.confirmPassword" type="password" class="glass-input" placeholder="请再次输入新密码" />
          </label>
          <div class="inline-actions">
            <GlowButton variant="secondary" :loading="passwordLoading" @click="handleChangePassword">修改密码</GlowButton>
            <button class="mini-action danger" type="button" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'advisory'" class="grid one-col">
      <PremiumCard title="平台建议" glowColor="secondary">
        <template v-if="advisory">
          <div class="overview-grid">
            <div v-for="card in subscriptionSummaryCards" :key="card.label" class="overview-item">
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
            </div>
          </div>

          <div class="advisory-score">
            <div class="advisory-metric">
              <span>匹配度</span>
              <strong>{{ advisory.matchScore || advisory.score || 0 }}</strong>
            </div>
            <div class="advisory-metric">
              <span>建议优先级</span>
              <strong>{{ advisory.priority || '中' }}</strong>
            </div>
            <div class="advisory-metric">
              <span>提醒数</span>
              <strong>{{ (advisory.risks || []).length }}</strong>
            </div>
          </div>

          <div class="advisory-grid">
            <div class="advisory-section">
              <h3>当前优势</h3>
              <div class="advisory-list">
                <div v-for="item in advisory.highlights || []" :key="item" class="advisory-item">{{ item }}</div>
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
        </template>
        <EmptyState
          v-else
          icon="shield"
          title="暂时没有生成平台建议"
          description="完善个人资料并完成一次分析后，这里会显示更具体的职业建议。"
        />
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
          title="还没有收藏岗位"
          description="在岗位详情里点击收藏后，这里会沉淀成你的岗位池。"
        />
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'subscriptions'" class="grid one-col">
      <PremiumCard title="岗位订阅" glowColor="primary">
        <div class="overview-grid">
          <div v-for="card in subscriptionSummaryCards" :key="card.label" class="overview-item">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </div>
        </div>

        <div class="helper-panel">
          <strong>订阅说明</strong>
          <p>系统会根据你选择的条件去匹配岗位。你可以先预览结果，确认无误后再立即发送邮箱通知。</p>
        </div>

        <div class="builder-grid">
          <label class="field">
            <span><Radio :size="14" /> 订阅类型</span>
            <select v-model="subscriptionForm.subscriptionType" class="glass-input">
              <option v-for="item in subscriptionTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <label class="field">
            <span><Send :size="14" /> 通知方式</span>
            <select v-model="subscriptionForm.channel" class="glass-input">
              <option
                v-for="item in subscriptionChannelOptions"
                :key="item.value"
                :value="item.value"
                :disabled="item.disabled"
              >
                {{ item.label }}
              </option>
            </select>
            <p class="field-hint">{{ selectedSubscriptionChannel?.description }}</p>
          </label>
          <label class="field">
            <span>岗位方向</span>
            <input v-model="subscriptionBuilder.keyword" class="glass-input" placeholder="如：前端 / Java / 数据分析" />
          </label>
          <label class="field">
            <span>意向城市</span>
            <input v-model="subscriptionBuilder.city" class="glass-input" placeholder="如：成都 / 上海" />
          </label>
          <label class="field">
            <span>最低薪资（K）</span>
            <input v-model="subscriptionBuilder.salaryMin" type="number" min="0" class="glass-input" placeholder="如：15" />
          </label>
          <label class="field">
            <span>最高薪资（K）</span>
            <input v-model="subscriptionBuilder.salaryMax" type="number" min="0" class="glass-input" placeholder="如：30" />
          </label>
        </div>

        <div class="selection-group">
          <strong>快捷岗位标签</strong>
          <div class="chip-row">
            <button
              v-for="tag in quickKeywordTags"
              :key="tag"
              type="button"
              class="mini-action chip-button"
              :class="{ active: subscriptionBuilder.keyword === tag }"
              @click="toggleSubscriptionField('keyword', tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <div class="selection-group">
          <strong>快捷城市标签</strong>
          <div class="chip-row">
            <button
              v-for="tag in quickCityTags"
              :key="tag"
              type="button"
              class="mini-action chip-button"
              :class="{ active: subscriptionBuilder.city === tag }"
              @click="toggleSubscriptionField('city', tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <div class="selection-group">
          <strong>经验要求</strong>
          <div class="chip-row">
            <button
              v-for="tag in quickExperienceTags"
              :key="tag"
              type="button"
              class="mini-action chip-button"
              :class="{ active: subscriptionBuilder.experience === tag }"
              @click="toggleSubscriptionField('experience', tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <div class="selection-group">
          <strong>技能标签</strong>
          <div class="chip-row">
            <button
              v-for="tag in quickSkillTags"
              :key="tag"
              type="button"
              class="mini-action chip-button"
              :class="{ active: hasSkillTag(tag) }"
              @click="toggleSkillTag(tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <label class="field">
          <span>补充技能关键词</span>
          <input
            v-model="subscriptionBuilder.skillsText"
            class="glass-input"
            placeholder="可继续输入其他技能，多个标签用空格或逗号分隔"
          />
        </label>

        <div class="helper-panel">
          <strong>本次订阅条件预览</strong>
          <div v-if="hasSubscriptionFilters" class="config-preview">
            <div v-for="item in formatSubscriptionFilterPreview(builtSubscriptionFilter)" :key="item">{{ item }}</div>
          </div>
          <p v-else class="field-hint">还没有选择具体筛选条件，当前会按订阅类型创建基础提醒。</p>
          <p v-if="subscriptionRequiresEmail" class="field-hint">
            命中结果会优先发送到你当前绑定的邮箱 {{ profileForm.email || authStore.user?.email || '（尚未填写）' }}。
          </p>
        </div>

        <GlowButton variant="primary" :loading="subscriptionSaving" @click="handleCreateSubscription">创建订阅</GlowButton>

        <div v-if="subscriptionItems.length" class="subscription-list">
          <article v-for="item in subscriptionItems" :key="item.id" class="manage-item">
            <div class="manage-item-head">
              <div>
                <strong>{{ formatSubscriptionTypeLabel(item.subscriptionType || 'JOB_PUSH') }}</strong>
                <p>{{ formatSubscriptionChannelLabel(item.channel || 'EMAIL') }} · {{ summarizeFilterConfig(item.filterConfig) }}</p>
              </div>
              <span class="meta-chip">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div class="inline-actions">
              <button class="mini-action" @click="handlePreviewMatches(item.id)">
                {{ previewingSubscriptionId === item.id ? '正在预览...' : '预览匹配结果' }}
              </button>
              <button class="mini-action" @click="handleDispatchSubscription(item.id)">
                {{ dispatchingSubscriptionId === item.id ? '正在发送...' : '立即发送邮件' }}
              </button>
              <button class="mini-action danger" @click="handleDeleteSubscription(item.id)">删除</button>
            </div>
            <div v-if="subscriptionMatches[item.id]?.length" class="match-list">
              <div v-for="match in subscriptionMatches[item.id]" :key="`${item.id}-${match.id || match.jobId}`" class="match-item">
                <strong>{{ match.title || match.jobTitle || '匹配岗位' }}</strong>
                <p>{{ match.companyName || '企业名称待补充' }} · {{ match.city || '城市待补充' }}</p>
              </div>
            </div>
          </article>
        </div>
        <EmptyState
          v-else-if="!subscriptionsLoading"
          icon="briefcase"
          title="还没有岗位订阅"
          description="先创建一条订阅，确认匹配结果和邮箱通知链路是否正常。"
        />
      </PremiumCard>
    </section>

    <section v-if="authStore.isLoggedIn && activeProfileTab === 'notifications'" class="grid one-col">
      <PremiumCard title="邮箱通知" glowColor="secondary">
        <div class="panel-caption">
          <span><Bell :size="14" /> 未读 {{ unreadNotificationCount }}</span>
          <button class="mini-action" @click="handleMarkAllNotificationsRead">全部设为已读</button>
        </div>

        <div class="helper-panel">
          <strong>这里会显示什么</strong>
          <p>岗位订阅命中后，平台会生成一条通知记录，并同步尝试发送到你的绑定邮箱。</p>
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
                <strong>{{ item.title || '邮箱通知' }}</strong>
                <p>{{ item.content || item.message || '暂无内容' }}</p>
              </div>
              <span class="meta-chip">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div class="inline-actions">
              <span class="meta-chip subtle">{{ item.friendlyType || getNotificationTypeLabel(item) }}</span>
              <span class="meta-chip subtle">{{ Number(item.isRead) === 1 ? '已读' : '未读' }}</span>
              <button class="mini-action" @click="openNotificationTarget(item)">查看相关内容</button>
              <button v-if="Number(item.isRead) !== 1" class="mini-action" @click="handleMarkNotificationRead(item.id)">设为已读</button>
            </div>
          </article>
        </div>
        <EmptyState
          v-else-if="!notificationsLoading"
          icon="bell"
          title="暂时没有新的邮箱通知"
          description="创建岗位订阅并发送后，这里会出现对应的通知记录。"
        />
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.profile-page,
.grid,
.form-stack,
.favorite-list,
.subscription-list,
.notification-list,
.match-list,
.overview-grid,
.advisory-grid,
.advisory-list,
.selection-group,
.chip-row {
  display: grid;
  gap: 20px;
}

.one-col {
  grid-template-columns: 1fr;
}

.two-col,
.builder-grid,
.auth-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.page-intro {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(280px, 1fr);
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
}

.page-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--c-accent-primary);
  font-size: 13px;
  letter-spacing: 0.08em;
}

.page-intro-title {
  margin: 0;
  font-size: clamp(28px, 3vw, 40px);
  color: var(--c-text-primary);
}

.page-intro-text {
  margin: 12px 0 0;
  max-width: 58ch;
  color: var(--c-text-secondary);
  line-height: 1.7;
}

.page-intro-meta,
.workspace-meta,
.advisory-score {
  display: grid;
  gap: 14px;
}

.page-intro-meta {
  align-content: start;
}

.intro-metric,
.workspace-meta-item,
.overview-item,
.helper-panel,
.advisory-metric,
.advisory-item,
.advisory-action,
.favorite-item,
.manage-item,
.match-item {
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface, rgba(255, 255, 255, 0.04));
  border-radius: 20px;
  padding: 16px 18px;
}

.intro-metric-label,
.workspace-meta-item span,
.overview-item span,
.field-hint,
.helper-panel p,
.advisory-metric span,
.advisory-item,
.advisory-action span,
.favorite-main p,
.manage-item p,
.config-preview,
.reset-hint {
  color: var(--c-text-secondary);
}

.intro-metric-value,
.workspace-meta-item strong,
.overview-item strong,
.advisory-metric strong,
.advisory-action strong,
.favorite-head,
.manage-item strong,
.selection-group strong,
.helper-panel strong,
.field span {
  color: var(--c-text-primary);
}

.intro-metric-value,
.overview-item strong,
.advisory-metric strong {
  display: block;
  margin-top: 6px;
  font-size: 24px;
}

.profile-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

.profile-tab,
.mini-action,
.text-action,
.favorite-main,
.favorite-delete,
.advisory-action {
  transition: 180ms ease;
}

.profile-tab,
.mini-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: color-mix(in srgb, var(--c-bg-base-elevated, rgba(255, 255, 255, 0.06)) 88%, transparent);
  color: var(--c-text-secondary);
}

.profile-tab.active,
.mini-action.active,
.chip-button.active {
  color: var(--c-text-primary);
  border-color: color-mix(in srgb, var(--c-accent-primary) 36%, var(--c-border-glass));
  background: color-mix(in srgb, var(--c-accent-primary) 14%, transparent);
}

.profile-tab:hover,
.mini-action:hover,
.favorite-item:hover,
.manage-item:hover,
.match-item:hover,
.advisory-action:hover {
  border-color: color-mix(in srgb, var(--c-accent-primary) 28%, var(--c-border-glass));
  color: var(--c-text-primary);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.glass-input {
  width: 100%;
  min-height: 46px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--c-border-glass);
  background: color-mix(in srgb, var(--c-bg-surface, rgba(255, 255, 255, 0.04)) 92%, transparent);
  color: var(--c-text-primary);
}

.glass-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--c-accent-primary) 40%, var(--c-border-glass));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--c-accent-primary) 12%, transparent);
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 12px;
  align-items: center;
}

.captcha-visual {
  min-height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 16px;
  border: 1px dashed color-mix(in srgb, var(--c-accent-primary) 40%, transparent);
  background:
    radial-gradient(circle at 20% 20%, color-mix(in srgb, var(--c-accent-secondary, #0ea5e9) 18%, transparent), transparent 30%),
    radial-gradient(circle at 80% 30%, color-mix(in srgb, var(--c-accent-primary, #f97316) 18%, transparent), transparent 25%),
    linear-gradient(135deg, color-mix(in srgb, var(--c-bg-surface-strong, #132238) 84%, transparent), color-mix(in srgb, var(--c-bg-surface, #20374f) 72%, transparent));
}

.captcha-visual.is-empty {
  border-style: solid;
}

.captcha-glyph {
  font-size: 20px;
  font-weight: 800;
  text-shadow: 0 4px 12px rgba(15, 23, 42, 0.25);
}

.text-action {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--c-accent-primary);
}

.reset-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--c-accent-primary) 24%, var(--c-border-glass));
  background: color-mix(in srgb, var(--c-accent-primary) 8%, var(--c-bg-surface, transparent));
}

.overview-grid,
.advisory-score {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.config-preview {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--c-bg-base-elevated, rgba(255, 255, 255, 0.04)) 92%, transparent);
  font-size: 13px;
}

.chip-row {
  grid-template-columns: repeat(auto-fit, minmax(100px, max-content));
}

.chip-button {
  cursor: pointer;
}

.favorite-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 14px;
  align-items: center;
}

.favorite-main {
  display: grid;
  gap: 8px;
  text-align: left;
}

.favorite-head,
.panel-caption,
.manage-item-head,
.inline-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.favorite-delete {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, #ef4444 28%, transparent);
  background: color-mix(in srgb, #ef4444 12%, transparent);
  color: #ef4444;
}

.manage-item.unread {
  border-color: color-mix(in srgb, var(--c-accent-primary) 28%, var(--c-border-glass));
  background: color-mix(in srgb, var(--c-accent-primary) 8%, var(--c-bg-surface, transparent));
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: color-mix(in srgb, var(--c-bg-base-elevated, rgba(255, 255, 255, 0.04)) 92%, transparent);
  color: var(--c-text-secondary);
  font-size: 12px;
}

.meta-chip.subtle {
  opacity: 0.9;
}

.mini-action.danger {
  color: #ef4444;
  border-color: color-mix(in srgb, #ef4444 30%, transparent);
}

.advisory-action {
  display: grid;
  gap: 8px;
  text-align: left;
}

@media (max-width: 960px) {
  .page-intro,
  .two-col,
  .builder-grid,
  .auth-grid,
  .overview-grid,
  .advisory-score {
    grid-template-columns: 1fr;
  }

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .favorite-item {
    grid-template-columns: 1fr;
    align-items: start;
  }
}
</style>
