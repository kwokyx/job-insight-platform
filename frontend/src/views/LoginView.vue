<script setup>
// 独立登录页：从 ProfileView 抽出来的 auth 表单 + 验证码 + 找回密码两步流程。
// 路由 /login，未登录被守卫拦截时会带 ?redirect=<原路径> 跳过来；
// 登录成功后回跳到 redirect 或 /profile。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogIn, RefreshCcw } from 'lucide-vue-next'
import logoUrl from '../../logo.png'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  confirmPasswordReset,
  fetchAuthProfile,
  fetchCaptcha,
  fetchFavorites,
  fetchSubscriptions,
  login,
  normalizeError,
  register,
  requestPasswordReset
} from '../api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { success } = useToast()

// —— 已登录直接弹走，避免重复登录 ——
// 守卫已经处理了"未登录 → /login"，但用户可能手动访问 /login，
// 这种情况直接跳到目标页（redirect 优先，否则到 /profile）。
onMounted(() => {
  if (authStore.isLoggedIn) {
    const target = route.query.redirect || '/profile'
    router.replace(target)
  }
})

// 三种 auth 面板模式：login / register / reset
const initialMode = route.query.login === 'false' ? 'register' : 'login'
const authMode = ref(initialMode)
const loading = ref(false)
const formError = ref('')
const formNotice = ref('')

const authForm = ref({
  username: '',
  password: '',
  email: '',
  nickname: '',
  roleType: 0
})

// 验证码状态（懒加载）
// 后端返回 { captchaId, captchaPrompt, captchaType }，captchaPrompt 是纯文本挑战。
// 只有后端告知"需要验证码"或"验证码错误/失效"时才会拉取。
const captcha = ref(null)
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

// 登录成功后顺带预热 profile / subscriptions / favorites，避免回跳时再发一波串行请求。
// 任何一个失败都不影响跳转，所以这里 catch 掉，由目标页自行重试。
async function warmUpUserData() {
  if (!authStore.isLoggedIn) return
  await Promise.all([
    fetchAuthProfile(authStore.token).then((profile) => {
      authStore.setAuth(authStore.token, {
        ...(authStore.user || {}),
        ...profile
      })
    }).catch(() => {}),
    fetchSubscriptions(authStore.token).catch(() => {}),
    fetchFavorites(authStore.token, { page: 1, pageSize: 20 }).catch(() => {})
  ])
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
    await warmUpUserData()
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
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- —— 顶部品牌区：logo + 标题 + 副标题（居中） —— -->
      <header class="auth-header">
        <div class="brand-mark" aria-hidden="true">
          <img :src="logoUrl" alt="" class="brand-logo" />
        </div>
        <h2 class="auth-title">
          <template v-if="authMode === 'login'">欢迎回来</template>
          <template v-else-if="authMode === 'register'">创建账号</template>
          <template v-else>重置密码</template>
        </h2>
        <p class="auth-subtitle">
          <template v-if="authMode === 'login'">登录以继续使用职涯 OS</template>
          <template v-else-if="authMode === 'register'">注册新账号，开启职涯洞察</template>
          <template v-else-if="resetStep === 1">输入账号与邮箱校验身份</template>
          <template v-else>为你的账号设置新密码</template>
        </p>
      </header>

      <!-- —— 分段切换器：只在 login / register 之间显示 —— -->
      <!--    reset 模式下通过登录表单底部"忘记密码"链接进入，用"返回登录"链接退出 -->
      <div v-if="authMode !== 'reset'" class="auth-tabs" role="tablist">
        <button
          type="button"
          class="auth-tab"
          :class="{ active: authMode === 'login' }"
          role="tab"
          :aria-selected="authMode === 'login'"
          @click="switchMode('login')"
        >登录</button>
        <button
          type="button"
          class="auth-tab"
          :class="{ active: authMode === 'register' }"
          role="tab"
          :aria-selected="authMode === 'register'"
          @click="switchMode('register')"
        >注册</button>
      </div>

      <form class="auth-form" @submit.prevent="handleAuthSubmit" novalidate>
        <!-- —— 登录表单 —— -->
        <template v-if="authMode === 'login'">
          <div class="field">
            <label class="field-label" for="login-username">账号</label>
            <div class="input-wrap">
              <!-- user 图标 -->
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <input
                id="login-username"
                v-model="authForm.username"
                type="text"
                class="auth-input"
                placeholder="请输入用户名"
                autocomplete="username"
                :disabled="loading"
                required
              />
            </div>
          </div>

          <div class="field">
            <div class="field-label-row">
              <label class="field-label" for="login-password">密码</label>
              <button
                type="button"
                class="inline-link"
                :disabled="loading"
                @click="switchMode('reset')"
              >忘记密码？</button>
            </div>
            <div class="input-wrap">
              <!-- lock 图标 -->
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <input
                id="login-password"
                v-model="authForm.password"
                type="password"
                class="auth-input"
                placeholder="请输入密码"
                autocomplete="current-password"
                :disabled="loading"
                required
              />
            </div>
          </div>
        </template>

        <!-- —— 注册表单 —— -->
        <template v-else-if="authMode === 'register'">
          <div class="field">
            <label class="field-label" for="reg-username">账号</label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <input
                id="reg-username"
                v-model="authForm.username"
                type="text"
                class="auth-input"
                placeholder="设置一个用户名"
                autocomplete="username"
                :disabled="loading"
                required
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="reg-password">密码</label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <input
                id="reg-password"
                v-model="authForm.password"
                type="password"
                class="auth-input"
                placeholder="至少 8 位，含字母和数字"
                autocomplete="new-password"
                :disabled="loading"
                required
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="reg-email">邮箱</label>
            <div class="input-wrap">
              <!-- mail 图标 -->
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>
              <input
                id="reg-email"
                v-model="authForm.email"
                type="email"
                class="auth-input"
                placeholder="用于找回密码（推荐填写）"
                autocomplete="email"
                :disabled="loading"
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="reg-nickname">昵称<span class="field-hint"> · 可选</span></label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M6 21a6 6 0 0 1 12 0"/></svg>
              <input
                id="reg-nickname"
                v-model="authForm.nickname"
                type="text"
                class="auth-input"
                placeholder="展示用昵称"
                :disabled="loading"
              />
            </div>
          </div>

          <div class="field">
            <span class="field-label">角色</span>
            <div class="role-row">
              <label class="role-chip" :class="{ active: authForm.roleType === 0 }">
                <input type="radio" v-model="authForm.roleType" :value="0" :disabled="loading" />
                <span>学生 / 普通用户</span>
              </label>
              <label class="role-chip" :class="{ active: authForm.roleType === 2 }">
                <input type="radio" v-model="authForm.roleType" :value="2" :disabled="loading" />
                <span>教师</span>
              </label>
            </div>
          </div>
        </template>

        <!-- —— 忘记密码：第 1 步（账号 + 邮箱） —— -->
        <template v-else-if="authMode === 'reset' && resetStep === 1">
          <div class="field">
            <label class="field-label" for="reset-username">账号</label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <input
                id="reset-username"
                v-model="resetForm.username"
                type="text"
                class="auth-input"
                placeholder="要找回密码的账号"
                :disabled="loading"
                required
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="reset-email">注册邮箱</label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>
              <input
                id="reset-email"
                v-model="resetForm.email"
                type="email"
                class="auth-input"
                placeholder="注册时填写的邮箱"
                :disabled="loading"
                required
              />
            </div>
          </div>

          <p class="reset-hint">
            校验通过后将颁发 15 分钟内有效的重置令牌，当前版本会直接在前端继续下一步，不通过邮件发送。
          </p>
        </template>

        <!-- —— 忘记密码：第 2 步（设置新密码） —— -->
        <template v-else-if="authMode === 'reset' && resetStep === 2">
          <div v-if="resetContext?.maskedEmail" class="reset-info">
            <div class="reset-info-row"><span class="reset-info-label">账号</span><span>{{ resetForm.username }}</span></div>
            <div class="reset-info-row"><span class="reset-info-label">邮箱</span><span>{{ resetContext.maskedEmail }}</span></div>
          </div>

          <div class="field">
            <label class="field-label" for="reset-newpass">新密码</label>
            <div class="input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <input
                id="reset-newpass"
                v-model="resetForm.newPassword"
                type="password"
                class="auth-input"
                placeholder="至少 8 位，含字母和数字"
                autocomplete="new-password"
                :disabled="loading"
                required
              />
            </div>
          </div>
        </template>

        <!-- —— 验证码区域 —— 仅在拉到 captcha 时渲染（懒加载） -->
        <!-- 注意：captcha-prompt / captcha-char 这两个 class + 字符错位渲染逻辑保持原样 -->
        <div v-if="captchaVisible" class="field captcha-field">
          <div class="field-label-row">
            <label class="field-label" for="login-captcha">验证码</label>
            <button
              type="button"
              class="inline-link"
              :disabled="captchaLoading"
              @click="refreshCaptcha"
            >
              <RefreshCcw :size="12" />
              <span>换一张</span>
            </button>
          </div>
          <div class="captcha-row">
            <div class="input-wrap captcha-input-wrap">
              <svg class="input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 12h16"/><path d="M4 6h16"/><path d="M4 18h10"/></svg>
              <input
                id="login-captcha"
                v-model="captchaCode"
                class="auth-input"
                placeholder="请输入下方答案"
                maxlength="8"
                autocomplete="off"
                :disabled="loading"
              />
            </div>
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
            </button>
          </div>
        </div>

        <!-- —— 错误 / 通知横幅 —— -->
        <div v-if="formError" class="status-banner error-banner" role="alert">
          <svg class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>{{ formError }}</span>
        </div>
        <div v-else-if="formNotice" class="status-banner info-banner" role="status">
          <svg class="status-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>
          <span>{{ formNotice }}</span>
        </div>

        <!-- —— 主提交按钮（GlowButton 已处理 loading / 深色主色对比度） —— -->
        <GlowButton variant="primary" :loading="loading" type="submit" class="submit-btn">
          <LogIn v-if="authMode === 'login'" :size="14" />
          <template v-if="authMode === 'login'">登录</template>
          <template v-else-if="authMode === 'register'">创建账号</template>
          <template v-else-if="authMode === 'reset' && resetStep === 1">发送重置码</template>
          <template v-else>重置密码</template>
        </GlowButton>

        <!-- —— 底部 footer 链接 —— -->
        <div v-if="authMode === 'login'" class="auth-footer">
          还没有账号？
          <button type="button" class="text-link" :disabled="loading" @click="switchMode('register')">立即注册</button>
        </div>
        <div v-else-if="authMode === 'register'" class="auth-footer">
          已有账号？
          <button type="button" class="text-link" :disabled="loading" @click="switchMode('login')">直接登录</button>
        </div>
        <div v-else-if="authMode === 'reset' && resetStep === 1" class="auth-footer">
          想起密码了？
          <button type="button" class="text-link" :disabled="loading" @click="switchMode('login')">返回登录</button>
        </div>
        <div v-else-if="authMode === 'reset' && resetStep === 2" class="auth-footer">
          <button type="button" class="text-link" :disabled="loading" @click="resetStep = 1">返回上一步</button>
          <span class="footer-sep">·</span>
          <button type="button" class="text-link" :disabled="loading" @click="switchMode('login')">返回登录</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
/* —— 登录页容器 ——
   /login 路由 meta.fullBleed = true，所以 .main-content 不加 padding、
   且自身 overflow 被锁；登录卡需要在剩余高度内垂直居中，必要时自身滚动。
   顶栏约 56-64px 高，sticky 在上方，这里用 100% 高度占满 grid 的第二行。 */
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 100%;
  padding: 40px 24px;
  overflow-y: auto;
  background:
    radial-gradient(ellipse at top, var(--c-accent-primary-glow) 0%, transparent 55%),
    radial-gradient(ellipse at bottom right, rgba(190, 184, 220, 0.18) 0%, transparent 60%),
    var(--c-bg-base);
}

/* —— 卡片：居中，限宽，阴影 —— */
.login-card {
  width: 100%;
  max-width: 440px;
  padding: 40px 36px 32px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 20px;
  box-shadow: var(--shadow-card-raised);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* —— 头部：logo + 标题 + 副标题（居中堆叠） —— */
.auth-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--c-accent-primary-glow);
  border: 1px solid var(--c-border-glass);
}

.brand-logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.auth-title {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
  line-height: 1.2;
  text-align: center;
}

.auth-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-muted);
  line-height: 1.5;
  text-align: center;
}

/* —— 分段切换器 login / register —— */
.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0;
  padding: 4px;
  background: var(--c-bg-surface-hover);
  border-radius: 10px;
}

.auth-tab {
  appearance: none;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--c-text-muted);
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 160ms var(--ease-out),
    color 160ms var(--ease-out),
    box-shadow 160ms var(--ease-out);
}

.auth-tab:hover:not(.active) {
  color: var(--c-text-secondary);
}

.auth-tab.active {
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

/* —— 表单栈 —— */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-secondary);
  letter-spacing: 0.01em;
  line-height: 1.3;
}

.field-hint {
  font-weight: 400;
  color: var(--c-text-muted);
}

/* —— 输入框 + 左图标 —— */
.input-wrap {
  position: relative;
  display: block;
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: var(--c-text-muted);
  pointer-events: none;
  transition: color 160ms var(--ease-out);
}

.auth-input {
  width: 100%;
  height: 44px;
  padding: 0 14px 0 40px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 14px;
  line-height: 1.4;
  font-family: inherit;
  transition:
    border-color 160ms var(--ease-out),
    box-shadow 160ms var(--ease-out),
    background-color 160ms var(--ease-out);
}

.auth-input::placeholder {
  color: var(--c-text-muted);
  opacity: 0.85;
}

.auth-input:hover:not(:disabled):not(:focus) {
  border-color: var(--c-border-glass-hover);
}

.auth-input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}

.auth-input:focus + /* noop */ * ,
.input-wrap:focus-within .input-icon {
  color: var(--c-accent-primary);
}

.auth-input:disabled {
  background: var(--c-bg-surface-hover);
  cursor: not-allowed;
  opacity: 0.75;
}

/* —— 角色选择（chip 风格） —— */
.role-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.role-chip {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color 160ms var(--ease-out),
    color 160ms var(--ease-out),
    background-color 160ms var(--ease-out);
}

.role-chip input[type="radio"] {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.role-chip:hover {
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.role-chip.active {
  border-color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}

/* —— 验证码区域 ——
   保留原有 captcha-prompt / captcha-char 的错位字符渲染逻辑，
   只调整外层排版、输入框样式和图形题容器的视觉。 */
.captcha-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.captcha-input-wrap {
  flex: 1 1 auto;
  min-width: 0;
}

.captcha-visual {
  position: relative;
  flex: 0 0 auto;
  width: 132px;
  height: 44px;
  padding: 4px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: repeating-linear-gradient(
    45deg,
    var(--c-bg-surface-hover) 0 10px,
    var(--c-bg-base-elevated) 10px 20px
  );
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 160ms var(--ease-out);
}

.captcha-visual:hover:not(:disabled) {
  border-color: var(--c-border-glass-hover);
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

/* —— 状态横幅（错误 / 通知） —— */
.status-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
  animation: banner-fade 160ms var(--ease-out);
}

.status-icon {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  margin-top: 2px;
}

.error-banner {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.22);
  color: #c53030;
}

.info-banner {
  background: var(--c-accent-primary-glow);
  border: 1px solid rgba(0, 89, 199, 0.22);
  color: var(--c-accent-primary);
}

[data-theme="dark"] .error-banner {
  color: #fca5a5;
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.32);
}

[data-theme="dark"] .info-banner {
  background: var(--c-accent-primary-glow);
  border-color: rgba(175, 198, 255, 0.35);
  color: var(--c-text-primary);
}

@keyframes banner-fade {
  from { opacity: 0; transform: translateY(-2px); }
  to { opacity: 1; transform: translateY(0); }
}

/* —— 重置流程提示块 —— */
.reset-hint {
  margin: 0;
  padding: 10px 12px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 10px;
  font-size: 12.5px;
  color: var(--c-text-muted);
  line-height: 1.6;
}

.reset-info {
  padding: 12px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--c-text-primary);
}

.reset-info-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.reset-info-label {
  flex: 0 0 48px;
  font-size: 12px;
  color: var(--c-text-muted);
  font-weight: 600;
}

/* —— 主提交按钮：全宽 44px —— */
.submit-btn {
  width: 100%;
  height: 44px;
  margin-top: 2px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
}

/* —— 内联链接（label 行右侧 / 按钮旁） —— */
.inline-link {
  appearance: none;
  background: none;
  border: none;
  padding: 2px 0;
  color: var(--c-accent-primary);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: color 160ms var(--ease-out), opacity 160ms var(--ease-out);
}

.inline-link:hover:not(:disabled) {
  color: var(--c-accent-primary-hover);
}

.inline-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* —— 底部 footer —— */
.auth-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--c-text-muted);
}

.text-link {
  appearance: none;
  background: none;
  border: none;
  padding: 0;
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 160ms var(--ease-out);
}

.text-link:hover:not(:disabled) {
  color: var(--c-accent-primary-hover);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.text-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.footer-sep {
  color: var(--c-text-muted);
  opacity: 0.6;
}

/* —— 响应式：窄屏撑满 —— */
@media (max-width: 520px) {
  .login-page {
    padding: 24px 14px;
    align-items: flex-start;
  }

  .login-card {
    max-width: none;
    padding: 28px 22px 24px;
    border-radius: 18px;
    margin-top: 12px;
  }

  .auth-title {
    font-size: 22px;
  }

  .brand-mark {
    width: 52px;
    height: 52px;
  }

  .captcha-visual {
    width: 120px;
  }
}
</style>
