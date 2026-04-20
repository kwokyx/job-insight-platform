<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { ShieldAlert, ArrowLeft, UserCircle2 } from 'lucide-vue-next'
import { useAuthStore } from '../store/auth'
import { getRoleLabel } from '../utils/role'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const currentRoleLabel = computed(() => getRoleLabel(authStore.user?.roleType))
const requiredRole = computed(() => route.query.required || '目标角色')
</script>

<template>
  <div class="forbidden-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">访问控制</span>
        <h1 class="page-intro-title">当前账号没有权限访问该功能</h1>
        <p class="page-intro-text">
          该页面仅面向 <strong>{{ requiredRole }}</strong> 开放。当前账号身份为
          <strong>{{ currentRoleLabel }}</strong>，请切换账号或联系管理员调整权限。
        </p>
      </div>
    </section>

    <PremiumCard title="下一步建议" glowColor="purple">
      <div class="actions">
        <div class="hint-card">
          <ShieldAlert :size="22" />
          <div>
            <strong>角色权限受限</strong>
            <p>不同角色会看到不同菜单、工作台和管理能力，未授权页面会被统一拦截。</p>
          </div>
        </div>
        <div class="hint-card">
          <UserCircle2 :size="22" />
          <div>
            <strong>前往个人中心</strong>
            <p>你可以在个人中心确认当前账号身份，或重新登录教师 / 管理员账号。</p>
          </div>
        </div>
        <div class="button-row">
          <GlowButton variant="ghost" @click="router.back()">
            <ArrowLeft :size="14" />
            返回上一页
          </GlowButton>
          <GlowButton variant="primary" @click="router.push('/profile')">前往个人中心</GlowButton>
        </div>
      </div>
    </PremiumCard>
  </div>
</template>

<style scoped>
.forbidden-page {
  max-width: 980px;
}

.actions {
  display: grid;
  gap: 16px;
}

.hint-card {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 14px;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.hint-card strong {
  display: block;
  margin-bottom: 6px;
  color: var(--c-text-primary);
}

.hint-card p {
  color: var(--c-text-secondary);
}

.button-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
