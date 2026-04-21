<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import {
  changeAuthPassword,
  confirmPasswordReset,
  fetchAuthProfile,
  fetchCaptcha,
  login,
  normalizeError,
  register,
  requestPasswordReset,
  updateAuthProfile,
  createSubscription,
  fetchSubscriptions,
  deleteSubscription
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
  Trash2
} from 'lucide-vue-next'
import { useToast } from '../composables/useToast'
import { getRoleLabel } from '../utils/role'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { success, error } = useToast()

// 三种 auth 面板模式：login / register / reset
// 初始模式从 ?login=... 推断，默认 login
const initialMode = route.query.login === 'false' ? 'register' : 'login'
const authMode = ref(initialMode)
const loading = ref(false)
const formError = ref('')
const formNotice = ref('')

// 表单状态
const authForm = ref({
  username: '',
  password: '',
  email: '',
  nickname: '',
  roleType: 0
})

// 验证码状态（懒加载）
// 后端返回 { captchaId, captchaPrompt, captchaType }，captchaPrompt 是纯文本挑战
// 只有后端告知"需要验证码"或"验证码错误/失效"时才会拉取
const captcha = ref(null) // { id, prompt, type }
const captchaCode = ref('')
const captchaLoading = ref(false)
const captchaVisible = computed(() => !!captcha.value)

// 用于识别后端验证码相关错误文案（来自 CaptchaService / AuthController）
const CAPTCHA_KEYWORDS = ['验证码', '图形', 'captcha']
// 限流 / 锁定文案（AuthThrottleService、LoginAttemptService）
const THROTTLE_KEYWORDS = ['过于频繁', '失败次数过多']

function isCaptchaRelatedError(message) {
  if (!message) return false
  const lower = message.toLowerCase()
  return CAPTCHA_KEYWORDS.some((kw) =>
    kw === 'captcha' ? lower.includes(kw) : message.includes(kw)
  )
}

function isThrottleError(message) {
  if (!message) return false
  return THROTTLE_KEYWORDS.some((kw) => message.includes(kw))
}

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const raw = await fetchCaptcha()
    captcha.value = {
      id: raw?.captchaId || '',
      prompt: raw?.captchaPrompt || '',
      type: raw?.captchaType || 'MATH'
    }
    captchaCode.value = ''
  } catch (e) {
    // 拉取验证码失败时保留旧题目，避免无限循环刷新
    console.error('Failed to refresh captcha', e)
    formError.value = normalizeError(e)
  } finally {
    captchaLoading.value = false
  }
}

function resetCaptcha() {
  captcha.value = null
  captchaCode.value = ''
}

function switchMode(mode) {
  authMode.value = mode
  formError.value = ''
  formNotice.value = ''
  resetCaptcha()
  // 切换模式时清掉密码，避免意外带到下一个表单
  authForm.value.password = ''
}

// 把后端错误转成前端提示；如果是验证码相关，顺便拉一次新的
async function handleAuthFailure(e) {
  const message = normalizeError(e)
  formError.value = message

  if (isCaptchaRelatedError(message)) {
    await refreshCaptcha()
    return
  }
  if (isThrottleError(message)) {
    // 限流场景：不再刷新验证码，提示用户稍后再试
    return
  }
  // 401 / 400 其它错误：第一次失败不强制出验证码，
  // 等后端下一次返回"需要验证码"再拉；如果已经在显示验证码，
  // 刷新一张新的让用户重试。
  if (captcha.value) {
    await refreshCaptcha()
  }
}

// —— 登录 ——
async function handleLogin() {
  if (loading.value) return
  loading.value = true
  formError.value = ''
  formNotice.value = ''
  try {
    const payload = {
      username: authForm.value.username.trim(),
      password: authForm.value.password,
      ...(captcha.value
        ? { captchaId: captcha.value.id, captchaCode: captchaCode.value.trim() }
        : {})
    }
    const session = await login(payload)
    authStore.setAuthSession(session)
    resetCaptcha()
    success('登录成功')
    await loadProfile()
    const target = route.query.redirect || '/profile'
    router.push(target)
  } catch (e) {
    await handleAuthFailure(e)
  } finally {
    loading.value = false
  }
}

// —— 注册 ——
// 策略：注册成功后切到登录 tab，预填账号密码，引导用户完成一次带新验证码的登录；
// 不做无提示自动登录，因为登录接口仍要验证码、直接跳转会绕过风控提示。
async function handleRegister() {
  if (loading.value) return
  loading.value = true
  formError.value = ''
  formNotice.value = ''
  try {
    // 注册必须带验证码（后端 CaptchaService.verify 强制校验）
    if (!captcha.value) {
      await refreshCaptcha()
      formNotice.value = '请先输入下方验证码再提交注册'
      return
    }
    const registerPayload = {
      username: authForm.value.username.trim(),
      password: authForm.value.password,
      email: authForm.value.email.trim(),
      nickname: authForm.value.nickname.trim(),
      roleType: authForm.value.roleType,
      captchaId: captcha.value.id,
      captchaCode: captchaCode.value.trim()
    }
    await register(registerPayload)

    // 注册成功：切回登录 tab，预填账号密码
    const username = registerPayload.username
    const password = registerPayload.password
    switchMode('login')
    authForm.value.username = username
    authForm.value.password = password
    formNotice.value = '注册成功，请继续登录（登录同样需要验证码）'
    success('注册成功，请登录')
  } catch (e) {
    await handleAuthFailure(e)
  } finally {
    loading.value = false
  }
}

// —— 忘记密码：两步 ——
const resetForm = ref({
  username: '',
  email: '',
  newPassword: ''
})
const resetStep = ref(1) // 1 = 提交用户名+邮箱+验证码；2 = 输入新密码
const resetContext = ref(null) // { resetToken, maskedEmail }

async function handleResetRequest() {
  if (loading.value) return
  loading.value = true
  formError.value = ''
  formNotice.value = ''
  try {
    if (!captcha.value) {
      await refreshCaptcha()
      formNotice.value = '请先输入下方验证码'
      return
    }
    const payload = {
      username: resetForm.value.username.trim(),
      email: resetForm.value.email.trim(),
      captchaId: captcha.value.id,
      captchaCode: captchaCode.value.trim()
    }
    const data = await requestPasswordReset(payload)
    resetContext.value = {
      resetToken: data.resetToken,
      maskedEmail: data.maskedEmail
    }
    resetStep.value = 2
    resetCaptcha()
    formNotice.value = `校验通过，已为账号 ${data.maskedEmail || ''} 颁发重置令牌，请在下方设置新密码`
    success('校验通过，请设置新密码')
  } catch (e) {
    await handleAuthFailure(e)
  } finally {
    loading.value = false
  }
}

async function handleResetConfirm() {
  if (loading.value) return
  loading.value = true
  formError.value = ''
  formNotice.value = ''
  try {
    const payload = {
      resetToken: resetContext.value?.resetToken || '',
      newPassword: resetForm.value.newPassword
    }
    await confirmPasswordReset(payload)
    success('密码重置成功，请使用新密码登录')
    // 回到登录界面，预填用户名
    const username = resetForm.value.username
    resetStep.value = 1
    resetContext.value = null
    resetForm.value = { username: '', email: '', newPassword: '' }
    switchMode('login')
    authForm.value.username = username
  } catch (e) {
    formError.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleAuthSubmit() {
  if (authMode.value === 'login') {
    await handleLogin()
    return
  }
  if (authMode.value === 'register') {
    await handleRegister()
    return
  }
  if (authMode.value === 'reset') {
    if (resetStep.value === 1) {
      await handleResetRequest()
    } else {
      await handleResetConfirm()
    }
  }
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
  loadProfile()
  loadSubscriptions()
})
</script>

<template>
  <div class="profile-page page-shell">
    <template v-if="!authStore.isLoggedIn">
      <section class="workspace-hero surface auth-layout">
        <div class="hero-copy auth-copy">
          <span class="eyebrow">账户访问</span>
          <h1>先登录，再接入 AI、报告和推荐能力</h1>
          <p>登录后可继续到推荐、AI 和报告页面。管理员与教师账号共用此登录入口，系统将根据角色自动提供专属功能权限。</p>
          <div class="benefit-list">
            <div class="benefit-item">
              <Shield :size="16" />
              <span>同步资料和权限</span>
            </div>
            <div class="benefit-item">
              <Sparkles :size="16" />
              <span>登录后可用 AI、推荐和报告</span>
            </div>
          </div>
        </div>

        <div class="surface auth-card workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title">
                {{ authMode === 'login' ? '登录账户' : authMode === 'register' ? '创建账户' : '找回密码' }}
              </h2>
            </div>
          </div>

          <div class="tabs">
            <button
              type="button"
              class="tab-btn"
              :class="{ active: authMode === 'login' }"
              @click="switchMode('login')"
            >登录</button>
            <button
              type="button"
              class="tab-btn"
              :class="{ active: authMode === 'register' }"
              @click="switchMode('register')"
            >注册</button>
            <button
              type="button"
              class="tab-btn"
              :class="{ active: authMode === 'reset' }"
              @click="switchMode('reset')"
            >找回密码</button>
          </div>

          <form class="form-stack" @submit.prevent="handleAuthSubmit">
            <!-- 登录表单 -->
            <template v-if="authMode === 'login'">
              <input v-model="authForm.username" class="glass-input" placeholder="用户名" autocomplete="username" required />
              <input v-model="authForm.password" type="password" class="glass-input" placeholder="密码" autocomplete="current-password" required />
            </template>

            <!-- 注册表单 -->
            <template v-else-if="authMode === 'register'">
              <input v-model="authForm.username" class="glass-input" placeholder="用户名" autocomplete="username" required />
              <input v-model="authForm.nickname" class="glass-input" placeholder="昵称" />
              <input v-model="authForm.email" type="email" class="glass-input" placeholder="邮箱（可选但推荐，用于找回密码）" />
              <input v-model="authForm.password" type="password" class="glass-input" placeholder="密码（至少 8 位，含字母和数字）" autocomplete="new-password" required />
              <div class="role-selector">
                <label><input type="radio" v-model="authForm.roleType" :value="0" /> 学生/普通用户</label>
                <label><input type="radio" v-model="authForm.roleType" :value="2" /> 教师</label>
              </div>
            </template>

            <!-- 忘记密码：第 1 步 -->
            <template v-else-if="authMode === 'reset' && resetStep === 1">
              <input v-model="resetForm.username" class="glass-input" placeholder="用户名" required />
              <input v-model="resetForm.email" type="email" class="glass-input" placeholder="注册时填写的邮箱" required />
              <p class="reset-hint">
                校验通过后将颁发 15 分钟内有效的重置令牌，当前版本会直接返回在前端使用，不会通过邮件发送。
              </p>
            </template>

            <!-- 忘记密码：第 2 步 -->
            <template v-else-if="authMode === 'reset' && resetStep === 2">
              <div v-if="resetContext?.maskedEmail" class="reset-hint">
                账号：{{ resetForm.username }}<br />
                邮箱：{{ resetContext.maskedEmail }}
              </div>
              <input
                v-model="resetForm.newPassword"
                type="password"
                class="glass-input"
                placeholder="新密码（至少 8 位，含字母和数字）"
                autocomplete="new-password"
                required
              />
            </template>

            <!-- 验证码区域：仅在拉到 captcha 时渲染（懒加载） -->
            <div v-if="captchaVisible" class="form-field captcha-field">
              <label class="field-label">图形验证码</label>
              <div class="captcha-row">
                <input
                  v-model="captchaCode"
                  class="glass-input captcha-input"
                  placeholder="请输入下方答案"
                  maxlength="8"
                  autocomplete="off"
                />
                <button
                  type="button"
                  class="captcha-visual"
                  :disabled="captchaLoading"
                  :title="captchaLoading ? '加载中…' : '点击换一张'"
                  @click="refreshCaptcha"
                >
                  <span v-if="captchaLoading" class="captcha-placeholder">加载中…</span>
                  <span v-else class="captcha-prompt">
                    <span
                      v-for="(ch, idx) in captcha?.prompt?.split('') || []"
                      :key="idx"
                      class="captcha-char"
                      :style="{
                        transform: `rotate(${(idx * 13 - 20) % 25}deg) translateY(${(idx % 2 === 0 ? -2 : 2)}px)`,
                        color: `hsl(${(idx * 47) % 360}, 60%, 45%)`
                      }"
                    >{{ ch }}</span>
                  </span>
                  <RefreshCcw :size="12" class="captcha-refresh-icon" />
                </button>
              </div>
              <p class="captcha-hint">看不清？点击图形换一张。</p>
            </div>

            <!-- 错误 / 提示横幅：显示后端原始 message -->
            <div v-if="formError" class="status-banner error-banner">{{ formError }}</div>
            <div v-else-if="formNotice" class="status-banner info-banner">{{ formNotice }}</div>

            <GlowButton variant="primary" :loading="loading" type="submit">
              <template v-if="authMode === 'login'">登录</template>
              <template v-else-if="authMode === 'register'">注册</template>
              <template v-else-if="authMode === 'reset' && resetStep === 1">下一步</template>
              <template v-else>重置密码</template>
            </GlowButton>

            <!-- 子链接：登录页面引出"忘记密码" -->
            <div v-if="authMode === 'login'" class="auth-subline">
              <button type="button" class="text-link" @click="switchMode('reset')">忘记密码？</button>
            </div>
            <div v-else-if="authMode === 'reset' && resetStep === 2" class="auth-subline">
              <button type="button" class="text-link" @click="resetStep = 1">返回上一步</button>
            </div>
          </form>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="workspace-hero surface hero-panel">
        <div class="hero-main">
          <div class="avatar">
            <img
              v-if="profile?.avatarUrl || authStore.user?.avatarUrl"
              :src="profile?.avatarUrl || authStore.user?.avatarUrl"
              alt="avatar"
            />
            <span v-else class="avatar-fallback" aria-hidden="true">
              {{ (profile?.nickname || authStore.user?.nickname || authStore.user?.username || '?').slice(0, 1).toUpperCase() }}
            </span>
          </div>
          <div class="hero-copy">
            <span class="eyebrow">账户概览</span>
            <h1>{{ profile?.nickname || authStore.user?.nickname || authStore.user?.username }}</h1>
            <p>{{ profile?.email || authStore.user?.email || '未设置邮箱' }}</p>
            <span class="role-chip">
              <Shield :size="14" />
              {{ roleLabel }}
            </span>
          </div>
        </div>

        <div class="hero-actions">
          <GlowButton variant="ghost" @click="router.push('/recommend')">
            <Sparkles :size="14" />
            智能推荐
          </GlowButton>
          <GlowButton variant="ghost" @click="logoutNow">
            <LogOut :size="14" />
            退出登录
          </GlowButton>
        </div>
      </section>

      <section class="facts-grid">
        <div v-for="item in accountFacts" :key="item.label" class="surface fact-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="workspace-grid">
        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><UserRound :size="15" /> 资料编辑</h2>
            </div>
          </div>

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
            <GlowButton variant="primary" :loading="loading" @click="saveProfile">保存资料</GlowButton>
          </div>
        </article>

        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><Lock :size="15" /> 密码与安全</h2>
            </div>
          </div>

          <div class="form-stack">
            <label class="field">
              <span><Lock :size="14" /> 当前密码</span>
              <input v-model="passwordForm.oldPassword" type="password" class="glass-input" placeholder="当前密码" />
            </label>
            <label class="field">
              <span><Lock :size="14" /> 新密码</span>
              <input v-model="passwordForm.newPassword" type="password" class="glass-input" placeholder="至少 8 位，含字母和数字" />
            </label>
            <GlowButton variant="secondary" :loading="loading" @click="savePassword">修改密码</GlowButton>
          </div>
        </article>

        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><BellRing :size="15" /> 岗位订阅（每日推送）</h2>
              <p>按条件订阅，明天起早上 9 点自动推送匹配岗位。</p>
            </div>
          </div>
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
                <div>
                  <strong>{{ sub.subscriptionType === 'JOB_PUSH' ? '自动筛选推送' : '普通订阅' }}</strong>
                  <p class="sub-config">{{ sub.filterConfig }}</p>
                </div>
                <button class="icon-btn delete" @click="handleDeleteSubscription(sub.id)">
                  <Trash2 :size="16" />
                </button>
              </div>
            </div>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-hero,
.surface {
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 20px;
  padding: 24px;
  border-radius: 20px;
}

.hero-copy,
.hero-actions,
.auth-copy,
.auth-card,
.section-panel {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 10px;
}

.hero-copy h1,
.panel-head h2,
.fact-card strong {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(24px, 2.4vw, 32px);
  line-height: 1.08;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.benefit-item,
.empty-state,
.field span,
.status-banner {
  color: var(--c-text-secondary);
}

.hero-actions,
.tabs,
.benefit-list,
.benefit-item {
  display: flex;
  gap: 12px;
}

.hero-actions,
.benefit-list {
  flex-wrap: wrap;
}

.role-chip,
.tab-btn,
.status-banner,
.glass-input,
.fact-card,
.benefit-item {
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
}

.role-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px 12px;
  background: var(--c-bg-surface);
  width: fit-content;
}

.auth-layout {
  align-items: stretch;
}

.auth-copy {
  gap: 12px;
  justify-content: center;
}

.auth-card {
  gap: 14px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.hero-panel {
  align-items: center;
  justify-content: space-between;
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--c-bg-surface-hover);
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
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-primary-hover));
  color: #ffffff;
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}

.facts-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.fact-card {
  padding: 16px;
  background: var(--c-bg-surface);
}

.fact-card span {
  display: block;
  margin-bottom: 8px;
  color: var(--c-text-secondary);
  font-size: 13px;
}

.fact-card strong {
  font-size: 20px;
  letter-spacing: -0.03em;
}

.section-panel {
  gap: 18px;
  min-width: 0;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

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
}

.tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 2px;
  padding: 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  background: var(--c-bg-surface);
}

.tab-btn {
  padding: 10px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-surface-strong);
  color: var(--c-text-secondary);
  font-size: 13.5px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.tab-btn:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}

.tab-btn.active {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    0 8px 18px var(--c-accent-primary-glow);
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  background: var(--c-bg-surface-strong);
  color: var(--c-text-primary);
}

.status-banner {
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--c-bg-surface);
  font-size: 13px;
  line-height: 1.5;
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
  border-color: rgba(185, 28, 28, 0.3);
}

.info-banner {
  color: #1d4ed8;
  background: rgba(219, 234, 254, 0.84);
  border-color: rgba(29, 78, 216, 0.25);
}

.benefit-item {
  align-items: center;
  padding: 10px 12px;
  background: var(--c-bg-surface);
}

[data-theme="dark"] .tab-btn {
  box-shadow: none;
}
[data-theme="dark"] .tab-btn.active {
  box-shadow: 0 8px 18px var(--c-accent-primary-glow);
}
[data-theme="dark"] .avatar .avatar-fallback {
  color: #0f1420;
}
[data-theme="dark"] .error-banner {
  color: #fecaca;
  background: rgba(127, 29, 29, 0.45);
}
[data-theme="dark"] .info-banner {
  color: #bfdbfe;
  background: rgba(30, 58, 138, 0.45);
}

@media (max-width: 1100px) {
  .workspace-hero,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .facts-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-panel {
    align-items: flex-start;
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .section-panel,
  .auth-card {
    padding: 20px;
    border-radius: 18px;
  }

  .facts-grid {
    grid-template-columns: 1fr 1fr;
  }

  .hero-panel {
    gap: 20px;
  }

  .hero-main {
    align-items: flex-start;
  }

  .hero-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}

.role-selector {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  margin-bottom: 8px;
  color: var(--c-text-secondary);
}

.role-selector label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
}

.role-selector input[type="radio"] {
  accent-color: var(--c-accent-primary);
}

.sub-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.sub-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}
.sub-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
}
.sub-config {
  font-family: monospace;
  font-size: 12px;
  color: var(--c-text-muted);
  margin-top: 4px;
}
.icon-btn.delete {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border: none;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.icon-btn.delete:hover {
  background: rgba(239, 68, 68, 0.2);
}

/* —— 验证码可视化 ——
   后端返回的是纯文本题面（如 "3 + 5 = ?" / "A 7 C 2"），
   不是图片流，所以这里把字符拆开 + 轻微旋转/错位渲染成"图形验证码"观感。 */
.captcha-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.field-label {
  font-size: 13px;
  color: var(--c-text-secondary);
}

.captcha-row {
  display: flex;
  gap: 8px;
  align-items: stretch;
}

.captcha-input {
  flex: 1;
}

.captcha-visual {
  position: relative;
  width: 160px;
  min-height: 46px;
  padding: 4px 8px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: repeating-linear-gradient(
    45deg,
    var(--c-bg-surface-strong) 0 10px,
    var(--c-bg-surface) 10px 20px
  );
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.captcha-visual:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.captcha-prompt {
  display: inline-flex;
  gap: 2px;
  font-family: var(--font-display, 'Georgia', serif);
  font-weight: 800;
  font-size: 18px;
  letter-spacing: 2px;
}

.captcha-char {
  display: inline-block;
  transform-origin: center;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.captcha-placeholder {
  font-size: 12px;
  color: var(--c-text-muted);
}

.captcha-refresh-icon {
  position: absolute;
  top: 4px;
  right: 6px;
  opacity: 0.6;
}

.captcha-hint {
  margin: 4px 0 0;
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.reset-hint {
  padding: 10px 12px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  font-size: 12.5px;
  color: var(--c-text-secondary);
  line-height: 1.6;
}

.auth-subline {
  display: flex;
  justify-content: flex-end;
}

.text-link {
  background: none;
  border: none;
  padding: 4px 0;
  color: var(--c-accent-primary);
  font-size: 13px;
  cursor: pointer;
}
.text-link:hover {
  text-decoration: underline;
}
</style>
