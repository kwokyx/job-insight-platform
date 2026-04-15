<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import {
  changeAuthPassword,
  fetchAuthProfile,
  login,
  normalizeError,
  register,
  updateAuthProfile
} from '../api'
import { Lock, LogOut, Mail, Settings, Shield, Sparkles, User, UserRound } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLoginMode = ref(route.query.login !== 'false')
const loading = ref(false)
const error = ref('')
const success = ref('')

const authForm = ref({
  username: '',
  password: '',
  email: '',
  nickname: ''
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

const roleLabel = computed(() => {
  const roleType = profile.value?.roleType ?? authStore.user?.roleType
  if (roleType === 1) return '管理员'
  if (roleType === 2) return '教师'
  return '用户'
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
    error.value = normalizeError(e)
  }
}

async function handleAuth() {
  loading.value = true
  error.value = ''
  success.value = ''

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
        nickname: authForm.value.nickname
      })
      const result = await login({
        username: authForm.value.username,
        password: authForm.value.password
      })
      authStore.setAuth(result.accessToken, result.user)
    }

    await loadProfile()
    router.push('/profile')
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  loading.value = true
  error.value = ''
  success.value = ''

  try {
    await updateAuthProfile(authStore.token, profileForm.value)
    success.value = '个人资料已更新。'
    await loadProfile()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function savePassword() {
  loading.value = true
  error.value = ''
  success.value = ''

  try {
    await changeAuthPassword(authStore.token, passwordForm.value)
    passwordForm.value.oldPassword = ''
    passwordForm.value.newPassword = ''
    success.value = '密码已更新。'
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

function logoutNow() {
  authStore.logout()
  profile.value = null
  router.push('/')
}

onMounted(loadProfile)
</script>

<template>
  <div class="profile-page">
    <div v-if="error" class="error-banner glass-panel">{{ error }}</div>
    <div v-if="success" class="success-banner glass-panel">{{ success }}</div>

    <template v-if="!authStore.isLoggedIn">
      <section class="auth-layout glass-panel">
        <div class="auth-copy">
          <span class="hero-kicker">账户访问</span>
          <h2>提示：请先登录以使用 AI 助手、报告和推荐等核心工具。</h2>
          <p>管理员账户使用相同的登录流程。同步资料后即可获得相应角色的访问权限。</p>
        </div>

        <PremiumCard :title="isLoginMode ? '登录' : '创建账户'" glowColor="primary">
          <div class="tabs">
            <button class="tab-btn" :class="{ active: isLoginMode }" @click="isLoginMode = true">登录</button>
            <button class="tab-btn" :class="{ active: !isLoginMode }" @click="isLoginMode = false">注册</button>
          </div>

          <div class="form-stack">
            <input v-model="authForm.username" class="glass-input" placeholder="用户名" />
            <input v-if="!isLoginMode" v-model="authForm.nickname" class="glass-input" placeholder="昵称" />
            <input v-if="!isLoginMode" v-model="authForm.email" class="glass-input" placeholder="邮箱" />
            <input v-model="authForm.password" type="password" class="glass-input" placeholder="密码" />
            <GlowButton variant="primary" :loading="loading" @click="handleAuth">
              {{ isLoginMode ? '登录' : '注册并登录' }}
            </GlowButton>
          </div>
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
            <p>{{ profile?.email || '未设置邮箱' }}</p>
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
        <PremiumCard title="个人主页" glowColor="teal">
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
        </PremiumCard>

        <PremiumCard title="安全设置" glowColor="secondary">
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
</style>
