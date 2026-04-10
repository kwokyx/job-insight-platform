<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { login, register, fetchAuthProfile } from '../api'
import { User, Mail, Lock, LogOut, Shield, Clock, Edit3, Eye, EyeOff, Camera, ArrowRight, Setting, Key } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLoginMode = ref(route.query.login !== 'false')
const authForm = ref({ username: '', password: '', email: '', nickname: '' })
const isLoading = ref(false)
const errorMsg = ref('')
const showPassword = ref(false)

// 获取用户资料
const profile = ref(null)
const loadProfile = async () => {
  if (!authStore.isLoggedIn) return
  try {
    const data = await fetchAuthProfile(authStore.token)
    profile.value = data
  } catch (e) {
    console.error('获取个人资料失败', e)
  }
}

onMounted(() => {
  if (authStore.isLoggedIn) {
    loadProfile()
  }
})

const handleAuth = async () => {
  isLoading.value = true
  errorMsg.value = ''
  
  try {
    if (isLoginMode.value) {
      // 真实登录
      const result = await login({
        username: authForm.value.username,
        password: authForm.value.password
      })
      authStore.setAuth(result.accessToken, result.user)
    } else {
      // 真实注册
      await register({
        username: authForm.value.username,
        password: authForm.value.password,
        email: authForm.value.email || undefined,
        nickname: authForm.value.nickname || undefined
      })
      // 注册成功后自动登录
      const result = await login({
        username: authForm.value.username,
        password: authForm.value.password
      })
      authStore.setAuth(result.accessToken, result.user)
    }
    router.push('/')
  } catch (error) {
    errorMsg.value = error.message || '操作失败，请稍后重试'
  } finally {
    isLoading.value = false
  }
}

const handleLogout = () => {
  authStore.logout()
  profile.value = null
  router.push('/')
}

const formatDate = (dateStr) => {
  if (!dateStr) return '暂无记录'
  const d = new Date(dateStr)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 模拟交互
const changeAvatar = () => {
  alert('功能开发中：将调起操作系统的文件选择器来上传图片')
}
const changeNickname = () => {
  const newName = prompt('请输入新的昵称：', profile.value?.nickname || authStore.user?.nickname)
  if (newName) alert(`已发送至服务器更新为: ${newName}`)
}
const changeEmail = () => {
  const newEmail = prompt('请输入新的邮箱：', profile.value?.email || authStore.user?.email)
  if (newEmail) alert(`请前往新邮箱 ${newEmail} 查收验证码以确认绑定！`)
}
const changePassword = () => {
  const pwd = prompt('身份验证：请输入原密码以继续')
  if (pwd) {
    const newPwd = prompt('请输入新密码：')
    if (newPwd) alert('密码更新成功！下次请使用新密码登录。')
  }
}
</script>

<template>
  <div class="profile-layout">
    <!-- 未登录 — 登录/注册表单 -->
    <div v-if="!authStore.isLoggedIn" class="auth-container">
      <div class="auth-banner">
        <h1>智绘职涯</h1>
        <p>基于大数据与人工智能，从海量岗位数据中提炼价值。</p>
        <p>立刻加入我们，探索你的职业成长路径与能力洞察图谱。</p>
      </div>
      <div class="auth-form-wrapper">
        <div class="auth-card">
          <h2 style="font-size: 24px; font-weight: 600; margin-bottom: 24px; text-align: center;">{{ isLoginMode ? '欢迎回来' : '创建账号' }}</h2>
          <div class="auth-switch">
            <button :class="{ active: isLoginMode }" @click="isLoginMode = true; errorMsg = ''">登录</button>
            <button :class="{ active: !isLoginMode }" @click="isLoginMode = false; errorMsg = ''">注册</button>
          </div>
          
          <!-- 错误提示 -->
          <div v-if="errorMsg" class="error-banner">
            <span>{{ errorMsg }}</span>
          </div>

          <form @submit.prevent="handleAuth" class="auth-form">
            <div class="input-group">
              <User class="input-icon" :size="18" />
              <input v-model="authForm.username" type="text" placeholder="输入用户名" class="glass-input" required />
            </div>
            
            <div v-if="!isLoginMode" class="input-group">
              <Edit3 class="input-icon" :size="18" />
              <input v-model="authForm.nickname" type="text" placeholder="昵称（选填）" class="glass-input" />
            </div>

            <div v-if="!isLoginMode" class="input-group">
              <Mail class="input-icon" :size="18" />
              <input v-model="authForm.email" type="email" placeholder="邮箱地址（选填）" class="glass-input" />
            </div>
            
            <div class="input-group">
              <Lock class="input-icon" :size="18" />
              <input 
                v-model="authForm.password" 
                :type="showPassword ? 'text' : 'password'" 
                placeholder="输入密码" 
                class="glass-input" 
                required 
              />
              <button type="button" class="eye-btn" @click="showPassword = !showPassword">
                <Eye v-if="!showPassword" :size="16" />
                <EyeOff v-else :size="16" />
              </button>
            </div>
            
            <GlowButton variant="primary" :loading="isLoading" class="submit-btn" style="width: 100%">
              {{ isLoginMode ? '登录' : '注册' }}
            </GlowButton>
          </form>
          
          <p class="auth-hint">
            {{ isLoginMode ? '还没有账号？' : '已有账号？' }}
            <a href="#" @click.prevent="isLoginMode = !isLoginMode; errorMsg = ''">
              {{ isLoginMode ? '立即注册' : '返回登录' }}
            </a>
          </p>
        </div>
      </div>
    </div>

    <!-- 已登录 — 个人中心 -->
    <div v-else class="profile-dashboard">
      <PremiumCard glowColor="teal">
         <div class="profile-header">
           <div class="avatar-lg" @click="changeAvatar" title="点击更换头像">
             <img :src="profile?.avatar || `https://api.dicebear.com/7.x/notionists/svg?seed=${authStore.user?.username}`" alt="avatar" />
             <div class="avatar-overlay">
               <Camera :size="24" />
             </div>
           </div>
           <div class="profile-info">
             <h2>{{ profile?.nickname || authStore.user?.nickname || authStore.user?.username }}</h2>
             <p class="profile-email">{{ profile?.email || authStore.user?.email || '未设置邮箱' }}</p>
             <div class="profile-tags">
               <span class="role-tag" v-if="(profile?.roleType || authStore.user?.roleType) === 1">
                 <Shield :size="12" /> 管理员
               </span>
               <span class="role-tag user" v-else>
                 <User :size="12" /> 普通用户
               </span>
             </div>
           </div>
         </div>
      </PremiumCard>

      <div class="info-grid">
        <PremiumCard title="账号信息">
           <ul class="info-list">
             <li>
               <span class="info-label">用户名</span>
               <span class="info-value">{{ profile?.username || authStore.user?.username }}</span>
             </li>
             <li>
               <span class="info-label">注册时间</span>
               <span class="info-value">{{ formatDate(profile?.createdAt) }}</span>
             </li>
             <li>
               <span class="info-label">最近登录</span>
               <span class="info-value">
                 <Clock :size="14" class="inline-icon" />
                 {{ formatDate(profile?.lastLoginAt) }}
               </span>
             </li>
             <li>
               <span class="info-label">联系电话</span>
               <span class="info-value">{{ profile?.phone || '未设置' }}</span>
             </li>
           </ul>
        </PremiumCard>
        
        <PremiumCard title="账号操作">
          <div class="actions-list">
            <GlowButton variant="secondary" @click="changeNickname" class="action-btn">
              <User :size="16" /> 修改昵称
            </GlowButton>
            <GlowButton variant="secondary" @click="changeEmail" class="action-btn">
              <Mail :size="16" /> 换绑邮箱
            </GlowButton>
            <GlowButton variant="secondary" @click="changePassword" class="action-btn">
              <Lock :size="16" /> 修改密码
            </GlowButton>
            <div class="divider"></div>
            <GlowButton variant="ghost" @click="handleLogout" class="logout-btn">
              <LogOut :size="16" /> 退出当前账号
            </GlowButton>
          </div>
        </PremiumCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-layout {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 120px);
}

.auth-container {
  display: flex;
  width: 100%;
  max-width: 900px;
  min-height: 500px;
  background: var(--c-bg-surface);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-glass);
}

.auth-banner {
  flex: 1;
  background: linear-gradient(135deg, rgba(59,130,246,0.1), rgba(168,85,247,0.1));
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-right: 1px solid var(--c-border-glass);
}
.auth-banner h1 {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  margin-bottom: 16px;
}
.auth-banner p {
  color: var(--c-text-secondary);
  font-size: 16px;
  line-height: 1.6;
}

.auth-form-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
  background: rgba(255, 255, 255, 0.02);
}

.auth-card {
  width: 100%;
  max-width: 360px;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

@media (max-width: 768px) {
  .auth-banner { display: none; }
  .auth-container { border-radius: var(--radius-lg); }
}

.auth-switch {
  display: flex;
  margin-bottom: 24px;
  background: rgba(255,255,255,0.05);
  border-radius: var(--radius-sm);
  padding: 4px;
}

.auth-switch button {
  flex: 1;
  padding: 10px;
  border-radius: var(--radius-sm);
  color: var(--c-text-secondary);
  font-weight: 600;
  transition: all var(--duration-fast);
}

.auth-switch button.active {
  background: rgba(255,255,255,0.1);
  color: var(--c-text-primary);
  box-shadow: 0 2px 8px rgba(0,0,0,0.2);
}

.error-banner {
  margin-bottom: 16px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #FCA5A5;
  font-size: 14px;
  animation: shakeX 0.4s ease;
}

@keyframes shakeX {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-6px); }
  40% { transform: translateX(6px); }
  60% { transform: translateX(-4px); }
  80% { transform: translateX(4px); }
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: var(--c-text-muted);
  pointer-events: none;
}

.glass-input {
  width: 100%;
  padding: 14px 44px 14px 44px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-md);
  color: var(--c-text-primary);
  font-size: 15px;
  transition: all var(--duration-fast) var(--ease-out);
}

.glass-input:focus {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.5);
  box-shadow: 0 0 0 3px rgba(30, 117, 255, 0.1);
}

.eye-btn {
  position: absolute;
  right: 14px;
  color: var(--c-text-muted);
  display: flex;
  align-items: center;
  padding: 4px;
}
.eye-btn:hover { color: var(--c-text-primary); }

.submit-btn {
  margin-top: 8px;
  padding: 14px;
  width: 100%;
}

.auth-hint {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--c-text-muted);
}
.auth-hint a {
  color: var(--c-accent-primary);
  font-weight: 600;
}
.auth-hint a:hover {
  text-decoration: underline;
}

/* 个人中心 */
.profile-dashboard {
  width: 100%;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar-lg {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
  padding: 4px;
  flex-shrink: 0;
  cursor: pointer;
  overflow: hidden;
}

.avatar-lg img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: #FFF;
  transition: filter var(--duration-normal);
}

.avatar-overlay {
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: opacity var(--duration-fast);
}

.avatar-lg:hover img {
  filter: blur(2px) brightness(0.7);
}

.avatar-lg:hover .avatar-overlay {
  opacity: 1;
}

.profile-info {
  flex: 1;
}

.profile-info h2 {
  margin: 0 0 4px;
  font-size: 28px;
}

.profile-email {
  color: var(--c-text-secondary);
  margin-bottom: 10px;
  font-size: 15px;
}

.profile-tags {
  display: flex;
  gap: 8px;
}

.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(249, 115, 22, 0.12);
  color: var(--c-accent-secondary);
  font-size: 12px;
  font-weight: 600;
}
.role-tag.user {
  background: rgba(0, 229, 255, 0.1);
  color: var(--c-accent-teal);
}

.info-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 24px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--c-border-glass);
  padding-bottom: 12px;
}
.info-list li:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.info-label {
  color: var(--c-text-muted);
  font-size: 14px;
}

.info-value {
  color: var(--c-text-primary);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.inline-icon {
  color: var(--c-text-muted);
}

.actions-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-btn {
  width: 100%;
  justify-content: flex-start;
  background: rgba(255,255,255,0.03) !important;
  border: 1px solid var(--c-border-glass) !important;
  color: var(--c-text-primary) !important;
}

.action-btn:hover {
  background: rgba(255,255,255,0.08) !important;
  border-color: var(--c-accent-primary) !important;
}

.divider {
  height: 1px;
  background: var(--c-border-glass);
  margin: 8px 0;
}

.logout-btn {
  width: 100%;
  justify-content: center;
  color: #FCA5A5;
  border-color: rgba(239, 68, 68, 0.25);
  margin-top: 4px;
}
.logout-btn:hover {
  background: rgba(239, 68, 68, 0.12) !important;
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }
  .avatar-lg {
    width: 80px;
    height: 80px;
  }
  .profile-info h2 {
    font-size: 22px;
  }
  .info-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .profile-tags {
    justify-content: center;
  }
}
</style>
