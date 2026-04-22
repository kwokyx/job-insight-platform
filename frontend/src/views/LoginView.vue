<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, RefreshCw } from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import logoUrl from '../../logo.png'
import {
  confirmPasswordReset,
  fetchCaptcha,
  login,
  normalizeError,
  register,
  requestPasswordReset
} from '../api'
import { useAuthStore } from '../store/auth'
import { ROLE, normalizeRoleType } from '../utils/role'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const authMode = ref('login') // login | register | reset
const resetStep = ref(1) // 1: verify identity, 2: set new password
const loading = ref(false)
const formError = ref('')
const formNotice = ref('')

const showPassword = ref(false)
const showNewPassword = ref(false)

const authForm = ref({
  username: '',
  password: '',
  email: '',
  nickname: '',
  roleType: 0
})

const resetForm = ref({
  username: '',
  email: '',
  newPassword: ''
})

const resetContext = ref({
  resetToken: '',
  maskedEmail: ''
})

const captchaBuckets = ref({
  login: { id: '', prompt: '', type: 'AUTO', code: '', loading: false },
  register: { id: '', prompt: '', type: 'AUTO', code: '', loading: false },
  reset: { id: '', prompt: '', type: 'AUTO', code: '', loading: false }
})

const activeCaptchaMode = computed(() => {
  if (authMode.value === 'register') return 'register'
  if (authMode.value === 'reset' && resetStep.value === 1) return 'reset'
  return 'login'
})

const activeCaptchaBucket = computed(() => captchaBuckets.value[activeCaptchaMode.value])

function getDefaultLandingByRole(roleType) {
  const role = normalizeRoleType(roleType)
  if (role === ROLE.ADMIN) return '/admin'
  if (role === ROLE.TEACHER) return '/teacher'
  return '/recommend'
}

function resolveTarget(rawTarget, roleType) {
  const target = typeof rawTarget === 'string' ? rawTarget : ''
  if (target.startsWith('/') && !target.startsWith('/login')) return target
  return getDefaultLandingByRole(roleType)
}

async function refreshCaptcha(mode = activeCaptchaMode.value, force = false) {
  const bucket = captchaBuckets.value[mode]
  if (!bucket || bucket.loading) return
  if (!force && bucket.id && bucket.prompt) return
  bucket.loading = true
  try {
    const data = await fetchCaptcha('AUTO')
    bucket.id = data?.captchaId || ''
    bucket.prompt = data?.captchaPrompt || ''
    bucket.type = data?.captchaType || 'AUTO'
    bucket.code = ''
  } catch (err) {
    formError.value = normalizeError(err)
  } finally {
    bucket.loading = false
  }
}

function clearTips() {
  formError.value = ''
  formNotice.value = ''
}

function switchMode(mode) {
  authMode.value = mode
  resetStep.value = 1
  resetContext.value = { resetToken: '', maskedEmail: '' }
  clearTips()
  refreshCaptcha(activeCaptchaMode.value, true)
}

function ensurePasswordStrength(password) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(String(password || ''))
}

async function handleLogin() {
  const captcha = captchaBuckets.value.login
  const payload = {
    username: String(authForm.value.username || '').trim(),
    password: String(authForm.value.password || ''),
    captchaId: captcha.id,
    captchaCode: String(captcha.code || '').trim()
  }
  const session = await login(payload)
  authStore.setAuthSession(session)
  const target = resolveTarget(route.query.redirect, session?.user?.roleType ?? authStore.user?.roleType)
  router.replace(target)
}

async function handleRegister() {
  if (!ensurePasswordStrength(authForm.value.password)) {
    throw new Error('密码至少 8 位，且必须包含字母和数字')
  }
  const captcha = captchaBuckets.value.register
  await register({
    username: String(authForm.value.username || '').trim(),
    password: String(authForm.value.password || ''),
    email: String(authForm.value.email || '').trim() || undefined,
    nickname: String(authForm.value.nickname || '').trim() || undefined,
    roleType: authForm.value.roleType,
    captchaId: captcha.id,
    captchaCode: String(captcha.code || '').trim()
  })
  formNotice.value = '注册成功，请使用新账号登录'
  switchMode('login')
  authForm.value.password = ''
}

async function handleResetRequest() {
  const captcha = captchaBuckets.value.reset
  const data = await requestPasswordReset({
    username: String(resetForm.value.username || '').trim(),
    email: String(resetForm.value.email || '').trim(),
    captchaId: captcha.id,
    captchaCode: String(captcha.code || '').trim()
  })
  resetContext.value = {
    resetToken: data?.resetToken || '',
    maskedEmail: data?.maskedEmail || ''
  }
  resetStep.value = 2
  formNotice.value = '身份校验通过，请设置新密码'
}

async function handleResetConfirm() {
  if (!ensurePasswordStrength(resetForm.value.newPassword)) {
    throw new Error('新密码至少 8 位，且必须包含字母和数字')
  }
  await confirmPasswordReset({
    resetToken: resetContext.value.resetToken,
    newPassword: String(resetForm.value.newPassword || '')
  })
  formNotice.value = '密码重置成功，请使用新密码登录'
  switchMode('login')
  resetForm.value.newPassword = ''
}

async function handleAuthSubmit() {
  if (loading.value) return
  loading.value = true
  clearTips()
  try {
    if (authMode.value === 'login') {
      await handleLogin()
    } else if (authMode.value === 'register') {
      await handleRegister()
    } else if (resetStep.value === 1) {
      await handleResetRequest()
    } else {
      await handleResetConfirm()
    }
  } catch (err) {
    formError.value = normalizeError(err)
    await refreshCaptcha(activeCaptchaMode.value, true)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (authStore.isLoggedIn) {
    router.replace(resolveTarget(route.query.redirect, authStore.user?.roleType))
    return
  }
  await refreshCaptcha('login', true)
})
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <header class="auth-header">
        <img :src="logoUrl" alt="职业能力平台" class="logo" />
        <h1 class="title">
          <template v-if="authMode === 'login'">登录平台</template>
          <template v-else-if="authMode === 'register'">注册账号</template>
          <template v-else>找回密码</template>
        </h1>
      </header>

      <div class="mode-switch">
        <button type="button" :class="{ active: authMode === 'login' }" @click="switchMode('login')">登录</button>
        <button type="button" :class="{ active: authMode === 'register' }" @click="switchMode('register')">注册</button>
        <button type="button" :class="{ active: authMode === 'reset' }" @click="switchMode('reset')">找回密码</button>
      </div>

      <form class="auth-form" @submit.prevent="handleAuthSubmit">
        <template v-if="authMode === 'login' || authMode === 'register'">
          <label class="field">
            <span>账号</span>
            <input v-model="authForm.username" class="input" autocomplete="username" placeholder="请输入账号" required />
          </label>

          <label class="field">
            <span>密码</span>
            <div class="input-wrap">
              <input
                v-model="authForm.password"
                :type="showPassword ? 'text' : 'password'"
                class="input"
                :autocomplete="authMode === 'register' ? 'new-password' : 'current-password'"
                placeholder="至少 8 位，含字母和数字"
                required
              />
              <button type="button" class="ghost" @click="showPassword = !showPassword">
                <EyeOff v-if="showPassword" :size="14" />
                <Eye v-else :size="14" />
              </button>
            </div>
          </label>
        </template>

        <template v-if="authMode === 'register'">
          <label class="field">
            <span>邮箱（可选）</span>
            <input v-model="authForm.email" class="input" autocomplete="email" placeholder="用于找回密码" />
          </label>

          <label class="field">
            <span>昵称（可选）</span>
            <input v-model="authForm.nickname" class="input" placeholder="展示名称" />
          </label>

          <div class="field">
            <span>角色</span>
            <div class="roles">
              <label class="role-item"><input v-model="authForm.roleType" type="radio" :value="0" /> 学生</label>
              <label class="role-item"><input v-model="authForm.roleType" type="radio" :value="2" /> 教师</label>
            </div>
          </div>
        </template>

        <template v-if="authMode === 'reset'">
          <template v-if="resetStep === 1">
            <label class="field">
              <span>账号</span>
              <input v-model="resetForm.username" class="input" placeholder="请输入账号" required />
            </label>

            <label class="field">
              <span>注册邮箱</span>
              <input v-model="resetForm.email" class="input" placeholder="请输入注册邮箱" required />
            </label>
          </template>

          <template v-else>
            <div class="reset-summary">
              <span>账号：{{ resetForm.username }}</span>
              <span>邮箱：{{ resetContext.maskedEmail || '--' }}</span>
            </div>
            <label class="field">
              <span>新密码</span>
              <div class="input-wrap">
                <input
                  v-model="resetForm.newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  class="input"
                  autocomplete="new-password"
                  placeholder="至少 8 位，含字母和数字"
                  required
                />
                <button type="button" class="ghost" @click="showNewPassword = !showNewPassword">
                  <EyeOff v-if="showNewPassword" :size="14" />
                  <Eye v-else :size="14" />
                </button>
              </div>
            </label>
          </template>
        </template>

        <div v-if="authMode !== 'reset' || resetStep === 1" class="field">
          <div class="field-head">
            <span>验证码</span>
            <button type="button" class="refresh-btn" :disabled="activeCaptchaBucket.loading" @click="refreshCaptcha(activeCaptchaMode, true)">
              <RefreshCw :size="12" />
              刷新
            </button>
          </div>
          <div class="captcha-row">
            <input v-model="activeCaptchaBucket.code" class="input" placeholder="输入验证码" />
            <button type="button" class="captcha-box" :disabled="activeCaptchaBucket.loading" @click="refreshCaptcha(activeCaptchaMode, true)">
              {{ activeCaptchaBucket.prompt || '加载中...' }}
            </button>
          </div>
        </div>

        <div v-if="formError" class="error-banner">{{ formError }}</div>
        <div v-else-if="formNotice" class="notice-banner">{{ formNotice }}</div>

        <GlowButton type="submit" variant="primary" :loading="loading" class="submit-btn">
          <template v-if="authMode === 'login'">登录</template>
          <template v-else-if="authMode === 'register'">注册</template>
          <template v-else-if="resetStep === 1">验证身份</template>
          <template v-else>确认重置</template>
        </GlowButton>

        <div v-if="authMode === 'login'" class="helper-row">
          <button type="button" class="text-link" @click="switchMode('reset')">忘记密码？</button>
        </div>
        <div v-else-if="authMode === 'reset' && resetStep === 2" class="helper-row">
          <button type="button" class="text-link" @click="resetStep = 1">返回上一步</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.12), transparent 35%),
    linear-gradient(180deg, rgba(250, 252, 255, 0.9), rgba(246, 249, 255, 0.95));
}

.login-card {
  width: 100%;
  max-width: 460px;
  padding: 22px;
  border-radius: 16px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-soft);
}

.auth-header {
  display: grid;
  justify-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.logo {
  width: 54px;
  height: 54px;
  object-fit: contain;
}

.title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 24px;
  color: var(--c-text-primary);
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.mode-switch button {
  height: 34px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.mode-switch button.active {
  border-color: rgba(37, 99, 235, 0.45);
  background: rgba(37, 99, 235, 0.12);
  color: var(--c-text-primary);
}

.auth-form {
  display: grid;
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.field > span,
.field-head > span {
  font-size: 12px;
  color: var(--c-text-secondary);
}

.input-wrap {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.input {
  width: 100%;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  padding: 0 12px;
  font-size: 14px;
  color: var(--c-text-primary);
  background: #fff;
}

.input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.12);
}

.ghost {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.roles {
  display: flex;
  gap: 12px;
}

.role-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-primary);
}

.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.refresh-btn {
  border: none;
  background: transparent;
  color: var(--c-accent-primary);
  font-size: 12px;
  display: inline-flex;
  gap: 4px;
  align-items: center;
  cursor: pointer;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.captcha-box {
  min-width: 136px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  cursor: pointer;
  padding: 0 10px;
}

.reset-summary {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  font-size: 12px;
  color: var(--c-text-secondary);
}

.error-banner,
.notice-banner {
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 12px;
}

.error-banner {
  border: 1px solid rgba(220, 38, 38, 0.28);
  background: rgba(248, 113, 113, 0.14);
  color: #991b1b;
}

.notice-banner {
  border: 1px solid rgba(16, 185, 129, 0.28);
  background: rgba(16, 185, 129, 0.14);
  color: #065f46;
}

.submit-btn {
  width: 100%;
}

.helper-row {
  display: flex;
  justify-content: center;
}

.text-link {
  border: none;
  background: transparent;
  color: var(--c-accent-primary);
  cursor: pointer;
  font-size: 13px;
}
</style>
