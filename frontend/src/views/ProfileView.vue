<script setup>
// ProfileView 仅服务"已登录"用户：账号资料、密码修改、岗位订阅、收藏管理。
// 登录 / 注册 / 找回密码已迁移到独立路由 /login —— 未登录访问本页时直接跳过去。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import EmptyState from '../components/common/EmptyState.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import {
  changeAuthPassword,
  fetchAuthProfile,
  normalizeError,
  updateAuthProfile,
  createSubscription,
  fetchSubscriptions,
  deleteSubscription,
  fetchFavorites,
  removeFavorite,
  invalidateApiCache
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
  Trash2,
  Heart,
  MapPin,
  Building2,
  ArrowRight
} from 'lucide-vue-next'
import { useToast } from '../composables/useToast'
import { getRoleLabel } from '../utils/role'

const router = useRouter()
const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(false)

// —— 未登录用户 → 走独立的 /login 页面 ——
// 用 replace 而不是 push，避免回退键又把用户拽回 /profile 造成死循环。
if (!authStore.isLoggedIn) {
  router.replace({ path: '/login', query: { redirect: '/profile' } })
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

// —— 我的收藏 ——
// fetchFavorites 返回 { data, total, page, pageSize }
// data 每条后端结构参考 FavoriteController：含 jobId 以及冗余的岗位基础字段
// （title / companyName / city / salaryText 等），可直接渲染列表。
const favorites = ref([])
const favoritesLoading = ref(false)
const favoritesError = ref('')
const favoritesTotal = ref(0)
const favoritesPage = ref(1)
const favoritesPageSize = 20
const favoriteRemovingId = ref(null)

async function loadFavorites() {
  if (!authStore.isLoggedIn) return
  favoritesLoading.value = true
  favoritesError.value = ''
  try {
    const res = await fetchFavorites(authStore.token, {
      page: favoritesPage.value,
      pageSize: favoritesPageSize
    })
    favorites.value = res?.data || []
    favoritesTotal.value = res?.total || 0
  } catch (e) {
    favoritesError.value = normalizeError(e)
    favorites.value = []
  } finally {
    favoritesLoading.value = false
  }
}

// 收藏列表点开一条 → 跳 /jobs?jobId=xxx 触发详情弹窗
// （JobsView 的 route watcher 同时接受 jobId / open 两种 query，deeplink 可复用）
function openFavoriteJob(fav) {
  const id = fav?.jobId ?? fav?.id
  if (!id) return
  router.push({ path: '/jobs', query: { jobId: id } })
}

async function handleRemoveFavorite(fav) {
  const id = fav?.jobId ?? fav?.id
  if (!id || favoriteRemovingId.value) return
  favoriteRemovingId.value = id
  try {
    await removeFavorite(authStore.token, id)
    // 从本地列表里摘掉，避免整页闪烁
    favorites.value = favorites.value.filter((item) => (item.jobId ?? item.id) !== id)
    favoritesTotal.value = Math.max(0, favoritesTotal.value - 1)
    // 让 JobsView 里 JobCard 重新拉一次 check（缓存带 Bearer 的 path）
    invalidateApiCache('/favorites')
    success('已取消收藏')
  } catch (e) {
    error(normalizeError(e))
  } finally {
    favoriteRemovingId.value = null
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
    // TODO: 后端除 PUT /auth/profile（账号基础资料）外还有 PUT /profile（职业画像）。
    // 当前 ProfileView 仅使用账号资料；职业画像相关字段若要落盘，需再调 api.updateProfile。
    // 等 UI 梳理清「账号资料 vs 职业画像」后再接入，避免重复提交造成歧义。
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
  // 已登录态下才拉数据；未登录已经在脚本顶部 redirect 走，这里不会重复触发
  if (!authStore.isLoggedIn) return
  loadProfile()
  loadSubscriptions()
  loadFavorites()
})
</script>

<template>
  <div v-if="authStore.isLoggedIn" class="profile-page page-shell">
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

      <!-- 我的收藏：跨两列展示，与订阅板块同级 -->
      <article class="surface section-panel workspace-module-panel favorites-panel">
        <div class="panel-head workspace-panel-head">
          <div class="workspace-panel-copy">
            <h2 class="workspace-panel-title inline-icon">
              <Heart :size="15" /> 我的收藏
            </h2>
            <p>已收藏 {{ favoritesTotal }} 个岗位，随时回来继续跟进。</p>
          </div>
          <GlowButton
            v-if="!favoritesLoading && favorites.length"
            variant="ghost"
            @click="loadFavorites"
          >
            <RefreshCcw :size="14" /> 刷新
          </GlowButton>
        </div>

        <div v-if="favoritesLoading" class="fav-skeleton">
          <SkeletonCard type="list" :lines="3" />
        </div>

        <div v-else-if="favoritesError" class="status-banner error-banner">
          收藏列表加载失败：{{ favoritesError }}
        </div>

        <EmptyState
          v-else-if="!favorites.length"
          icon="inbox"
          title="还没有收藏的岗位"
          description="浏览岗位时点击右下角的心形图标，即可把心仪岗位收藏到这里。"
          action-text="去看看岗位"
          @action="router.push('/jobs')"
        />

        <ul v-else class="fav-list">
          <li
            v-for="fav in favorites"
            :key="fav.id || fav.jobId"
            class="fav-item"
            role="button"
            tabindex="0"
            @click="openFavoriteJob(fav)"
            @keydown.enter.prevent="openFavoriteJob(fav)"
            @keydown.space.prevent="openFavoriteJob(fav)"
          >
            <div class="fav-main">
              <div class="fav-title-row">
                <h3 class="fav-title">{{ fav.title || '未知岗位' }}</h3>
                <span v-if="fav.salaryText" class="fav-salary">{{ fav.salaryText }}</span>
              </div>
              <p class="fav-sub">
                <span v-if="fav.companyName" class="fav-sub-item">
                  <Building2 :size="12" /> {{ fav.companyName }}
                </span>
                <span v-if="fav.city" class="fav-sub-item">
                  <MapPin :size="12" /> {{ fav.city }}
                </span>
              </p>
              <p v-if="fav.favoriteTime || fav.createdAt" class="fav-time">
                收藏于 {{ fav.favoriteTime || fav.createdAt }}
              </p>
            </div>
            <div class="fav-actions">
              <button
                type="button"
                class="fav-action-btn view"
                :aria-label="`查看岗位 ${fav.title || ''}`"
                @click.stop="openFavoriteJob(fav)"
              >
                <ArrowRight :size="14" />
              </button>
              <button
                type="button"
                class="fav-action-btn remove"
                :disabled="favoriteRemovingId === (fav.jobId ?? fav.id)"
                :aria-label="`取消收藏 ${fav.title || ''}`"
                @click.stop="handleRemoveFavorite(fav)"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </li>
        </ul>
      </article>
    </section>
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
.empty-state,
.field span,
.status-banner {
  color: var(--c-text-secondary);
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.role-chip,
.status-banner,
.glass-input,
.fact-card {
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

[data-theme="dark"] .avatar .avatar-fallback {
  color: #0f1420;
}
[data-theme="dark"] .error-banner {
  color: #fecaca;
  background: rgba(127, 29, 29, 0.45);
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
  .section-panel {
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

/* —— 我的收藏板块 ——
   在 workspace-grid 里跨整行（左右两列），这样 20 条一页的收藏有
   足够宽度一行一条展示，不会被挤成拥挤的双列。 */
.favorites-panel {
  grid-column: 1 / -1;
}

.fav-skeleton {
  padding: 4px 0;
}

.fav-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.fav-item:hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px var(--c-accent-primary-glow);
}

.fav-item:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

.fav-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fav-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.fav-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.fav-salary {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.fav-sub {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.fav-sub-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.fav-sub-item :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.75;
}

.fav-time {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.fav-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.fav-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-strong);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease,
    transform 150ms ease;
}

.fav-action-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.fav-action-btn.view:hover {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.35);
  color: var(--c-accent-primary);
}

.fav-action-btn.remove:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.35);
  color: #ef4444;
}

.fav-action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
