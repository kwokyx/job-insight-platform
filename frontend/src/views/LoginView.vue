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
    <div class="login-card surface">
      <header class="login-header">
        <div class="brand-mark">
          <img :src="logoUrl" alt="职涯 OS Logo" class="brand-logo" />
        </div>
        <div class="brand-copy">
          <h1 class="brand-title">
            <span class="brand-strong">职涯</span>OS · 登录
          </h1>
          <p class="brand-subtitle">使用账号继续，或注册新账号开启职涯洞察。</p>
        </div>
      </header>

      <div class="tabs" role="tablist">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: authMode === 'login' }"
          role="tab"
          :aria-selected="authMode === 'login'"
          @click="switchMode('login')"
        >登录</button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: authMode === 'register' }"
          role="tab"
          :aria-selected="authMode === 'register'"
          @click="switchMode('register')"
        >注册</button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: authMode === 'reset' }"
          role="tab"
          :aria-selected="authMode === 'reset'"
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
          <LogIn v-if="authMode === 'login'" :size="14" />
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
  </div>
</template>

<style scoped>
/* —— 登录页布局 ——
   /login 路由声明了 fullBleed:true，所以 main-content 不会再加 padding，
   这里靠 100% 高度的容器把卡片在视口里垂直 + 水平居中。
   背景用 token 加一层极淡的径向晕染，营造"登录场景"的呼吸感而不抢戏。 */
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100%;
  padding: 48px 24px;
  background:
    radial-gradient(ellipse at top, var(--c-accent-primary-glow) 0%, transparent 55%),
    radial-gradient(ellipse at bottom right, rgba(190, 184, 220, 0.18) 0%, transparent 60%),
    var(--c-bg-base);
}

.login-card {
  width: 100%;
  max-width: 440px;
  padding: 36px 32px 32px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  border-radius: 20px;
  box-shadow: var(--shadow-card-soft);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.login-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 4px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  flex-shrink: 0;
}

.brand-logo {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.brand-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--c-text-primary);
  line-height: 1.15;
}

.brand-strong {
  font-weight: 800;
}

.brand-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-secondary);
  line-height: 1.45;
}

.tabs {
  display: flex;
  gap: 6px;
  padding: 6px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface-strong);
}

.tab-btn {
  flex: 1;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.tab-btn:hover {
  color: var(--c-accent-primary);
}

.tab-btn.active {
  background: var(--c-bg-surface);
  border-color: var(--c-border-glass);
  color: var(--c-accent-primary);
  box-shadow: var(--shadow-card-quiet);
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-strong);
  color: var(--c-text-primary);
  font-size: 14px;
}

.glass-input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}

.role-selector {
  display: flex;
  gap: 16px;
  margin-top: 4px;
  margin-bottom: 4px;
  color: var(--c-text-secondary);
}

.role-selector label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13.5px;
}

.role-selector input[type="radio"] {
  accent-color: var(--c-accent-primary);
}

.status-banner {
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
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

[data-theme="dark"] .error-banner {
  color: #fecaca;
  background: rgba(127, 29, 29, 0.45);
}
[data-theme="dark"] .info-banner {
  color: #bfdbfe;
  background: rgba(30, 58, 138, 0.45);
}

/* —— 验证码可视化 ——
   后端返回的是纯文本题面（如 "3 + 5 = ?" / "A 7 C 2"），
   不是图片流，所以这里把字符拆开 + 轻微旋转/错位渲染成"图形验证码"观感。 */
.captcha-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 2px;
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

/* —— 响应式：移动端撑满宽度，缩小内边距 —— */
@media (max-width: 520px) {
  .login-page {
    padding: 24px 12px;
    align-items: flex-start;
  }

  .login-card {
    max-width: none;
    padding: 24px 20px 22px;
    border-radius: 18px;
    margin-top: 16px;
  }

  .brand-title {
    font-size: 18px;
  }

  .brand-subtitle {
    font-size: 12.5px;
  }
}
</style>
