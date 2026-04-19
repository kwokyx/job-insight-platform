<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { changeAuthPassword, fetchAuthProfile, login, normalizeError, register, updateAuthProfile, createSubscription, fetchSubscriptions, deleteSubscription } from '../api'
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
  <div class="profile-page">
    <template v-if="!authStore.isLoggedIn">
      <section class="auth-layout glass-panel">
        <div class="auth-copy">
          <span class="hero-kicker">账号登录</span>
          <h2>登录以使用 AI 助手、报告中心和智能推荐工具。</h2>
          <p>管理员与教师账号共用此登录入口。系统将根据您的角色自动提供专属功能权限。</p>
        </div>

        <PremiumCard :title="isLoginMode ? '账号登录' : '注册账号'" glowColor="primary">
          <div class="tabs">
            <button class="tab-btn" :class="{ active: isLoginMode }" @click="isLoginMode = true">登录</button>
            <button class="tab-btn" :class="{ active: !isLoginMode }" @click="isLoginMode = false">注册</button>
          </div>

          <form @submit.prevent="handleAuth" class="form-stack">
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
        </PremiumCard>
      </section>
    </template>

    <template v-else>
      <section class="hero glass-panel">
        <div class="hero-main">
          <div class="avatar">
            <img
              :src="profile?.avatarUrl || authStore.user?.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username}`"
              alt="avatar"
            />
          </div>
          <div>
            <h2>{{ profile?.nickname || authStore.user?.nickname || authStore.user?.username }}</h2>
            <p>{{ profile?.email || '暂未设置邮箱' }}</p>
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

      <section class="grid two-col">
        <PremiumCard title="个人信息" glowColor="teal">
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
            <GlowButton variant="primary" :loading="loading" @click="saveProfile">保存修改</GlowButton>
          </div>
        </PremiumCard>

        <PremiumCard title="安全设置" glowColor="secondary">
          <div class="form-stack">
            <label class="field">
              <span><Lock :size="14" /> 当前密码</span>
              <input v-model="passwordForm.oldPassword" type="password" class="glass-input" placeholder="当前密码" />
            </label>
            <label class="field">
              <span><Lock :size="14" /> 新密码</span>
              <input v-model="passwordForm.newPassword" type="password" class="glass-input" placeholder="至少6个字符" />
            </label>
            <GlowButton variant="secondary" :loading="loading" @click="savePassword">修改密码</GlowButton>
          </div>
        </PremiumCard>

        <PremiumCard title="岗位订阅 (每日推送)" glowColor="primary">
          <div class="form-stack">
            <div class="sub-grid">
              <input v-model="subForm.city" class="glass-input" placeholder="目标城市" />
              <input v-model="subForm.industry" class="glass-input" placeholder="行业方向" />
              <input v-model="subForm.keyword" class="glass-input" placeholder="关键词 (如: Java)" />
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
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.auth-layout {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 24px;
  padding: 28px;
  background: var(--c-bg-surface-strong);
}

.auth-copy {
  display: flex;
  flex-direction: column;
  gap: 16px;
  justify-content: center;
}

.hero-kicker,
.role-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  width: fit-content;
}

.tabs,
.hero,
.hero-main,
.hero-actions {
  display: flex;
  gap: 12px;
}

.tabs {
  margin-bottom: 16px;
}

.tab-btn {
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

.tab-btn.active {
  background: rgba(30, 117, 255, 0.14);
  border-color: rgba(30, 117, 255, 0.4);
}

.hero {
  justify-content: space-between;
  align-items: center;
  padding: 24px;
}

.hero-main {
  align-items: center;
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

.grid {
  display: grid;
  gap: 24px;
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  color: var(--c-text-secondary);
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.error-banner,
.success-banner {
  padding: 12px 14px;
  border-radius: 14px;
}

.error-banner {
  color: #fecaca;
}

.success-banner {
  color: #bbf7d0;
}

@media (max-width: 960px) {
  .auth-layout,
  .two-col {
    grid-template-columns: 1fr;
  }

  .hero {
    flex-direction: column;
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
