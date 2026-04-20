<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import {
  changeAuthPassword,
  fetchAuthProfile,
  login,
  normalizeError,
  register,
  updateAuthProfile,
  createSubscription,
  fetchSubscriptions,
  deleteSubscription
} from '../api'
import { Lock, LogOut, Mail, Settings, Shield, Sparkles, User, UserRound, BellRing, Trash2 } from 'lucide-vue-next'
import { useToast } from '../composables/useToast'
import { getRoleLabel } from '../utils/role'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLoginMode = ref(route.query.login !== 'false')
const loading = ref(false)
const { success, error } = useToast()

const authForm = ref({
  username: '',
  password: '',
  email: '',
  nickname: '',
  roleType: 0
})

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

async function handleAuth() {
  loading.value = true

  try {
    if (isLoginMode.value) {
      const result = await login({
        username: authForm.value.username,
        password: authForm.value.password
      })
      authStore.setAuth(result.accessToken, result.user)
    } else {
      await register({
        username: authForm.value.username,
        password: authForm.value.password,
        email: authForm.value.email,
        nickname: authForm.value.nickname,
        roleType: authForm.value.roleType
      })
      const result = await login({
        username: authForm.value.username,
        password: authForm.value.password
      })
      authStore.setAuth(result.accessToken, result.user)
    }

    await loadProfile()
    success('登录成功')
    router.push(route.query.redirect || '/profile')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    loading.value = false
  }
}

async function loadSubscriptions() {
  if (!authStore.isLoggedIn) return
  try {
    subscriptions.value = await fetchSubscriptions(authStore.token)
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
              <h2 class="workspace-panel-title">{{ isLoginMode ? '登录账户' : '创建账户' }}</h2>
            </div>
          </div>

          <div class="tabs">
            <button class="tab-btn" :class="{ active: isLoginMode }" @click="isLoginMode = true">登录</button>
            <button class="tab-btn" :class="{ active: !isLoginMode }" @click="isLoginMode = false">注册</button>
          </div>

          <form class="form-stack" @submit.prevent="handleAuth">
            <input v-model="authForm.username" class="glass-input" placeholder="用户名" required />
            <input v-if="!isLoginMode" v-model="authForm.nickname" class="glass-input" placeholder="昵称" required />
            <input v-if="!isLoginMode" v-model="authForm.email" type="email" class="glass-input" placeholder="邮箱" />
            <input v-model="authForm.password" type="password" class="glass-input" placeholder="密码" required />
            <div v-if="!isLoginMode" class="role-selector">
              <label><input type="radio" v-model="authForm.roleType" :value="0" /> 学生/普通用户</label>
              <label><input type="radio" v-model="authForm.roleType" :value="2" /> 教师</label>
            </div>
            <GlowButton variant="primary" :loading="loading" type="submit">
              {{ isLoginMode ? '登录' : '注册并登录' }}
            </GlowButton>
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
              <input v-model="passwordForm.newPassword" type="password" class="glass-input" placeholder="至少 6 个字符" />
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
  background: rgba(255, 255, 255, 0.72);
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
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
}

.role-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.56);
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
  background: rgba(255, 255, 255, 0.06);
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
  background: rgba(255, 255, 255, 0.56);
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
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
}

.tab-btn {
  padding: 10px 14px;
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
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
  border-color: rgba(30, 117, 255, 0.22);
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}

.tab-btn.active {
  background: rgba(30, 117, 255, 0.12);
  border-color: rgba(30, 117, 255, 0.3);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    0 8px 18px rgba(30, 117, 255, 0.08);
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--c-text-primary);
}

.status-banner {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.64);
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
}

.success-banner {
  color: #166534;
  background: rgba(220, 252, 231, 0.84);
}

.benefit-item {
  align-items: center;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.5);
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
</style>
