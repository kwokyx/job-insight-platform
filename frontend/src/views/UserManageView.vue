<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import DefaultAvatarIcon from '../components/common/DefaultAvatarIcon.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminDashboard,
  fetchAdminUsers,
  updateAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus
} from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Search, RefreshCw, Users, UserX, UserCheck, ChevronLeft, ChevronRight,
  Activity, UserPlus, TrendingUp, PencilLine, Mail, Phone, Link2, X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(true)
const actionLoadingId = ref(null)
const dashboard = ref(null)
const users = ref([])
const totalCount = ref(0)

const filters = ref({ keyword: '', roleType: '', status: '', page: 1, pageSize: 10 })
const searchInput = ref('')

const showEditDialog = ref(false)
const editLoading = ref(false)
const editingUser = ref(null)
const editForm = ref(createEmptyEditForm())

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / filters.value.pageSize)))

function createEmptyEditForm() {
  return {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    avatarUrl: '',
    roleType: 0,
    status: 1
  }
}

const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '总用户数', value: dashboard.value.totalUsers ?? '--', sub: `今日新增 ${dashboard.value.newUsersToday ?? 0}`, icon: Users, tone: 'primary' },
    { label: '今日活跃', value: dashboard.value.activeToday ?? '--', sub: '近 24 小时登录', icon: Activity, tone: 'primary' },
    { label: '封禁账号', value: dashboard.value.bannedCount ?? '--', sub: '建议优先核查', icon: UserX, tone: 'danger' }
  ]
})

const registrationTrend = computed(() => {
  const raw = dashboard.value?.registrationTrend
  if (!Array.isArray(raw) || !raw.length) return []
  return raw.map((row) => ({
    date: row.date || row.day || row.registerDate || row.createdAt,
    count: Number(row.count ?? row.total ?? row.newUsers ?? 0)
  })).filter((item) => item.date)
})

const trendSummary = computed(() => {
  const rows = registrationTrend.value
  if (!rows.length) return null
  const total = rows.reduce((sum, row) => sum + row.count, 0)
  const max = rows.reduce((value, row) => Math.max(value, row.count), 0)
  const peak = rows.find((row) => row.count === max)
  return { total, max, peak }
})

const TREND_VB_W = 300
const TREND_VB_H = 100
const trendHoverIdx = ref(-1)

const trendGeometry = computed(() => {
  const rows = registrationTrend.value
  const max = trendSummary.value?.max || 1
  if (!rows.length) return { linePath: '', areaPath: '', points: [] }
  const stepX = rows.length > 1 ? TREND_VB_W / (rows.length - 1) : 0
  const points = rows.map((row, index) => ({
    x: Number((index * stepX).toFixed(2)),
    y: Number((TREND_VB_H - (row.count / max) * TREND_VB_H).toFixed(2)),
    count: row.count,
    date: row.date
  }))
  const linePath = points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join(' ')
  const areaPath = `${linePath} L${TREND_VB_W},${TREND_VB_H} L0,${TREND_VB_H} Z`
  return { linePath, areaPath, points }
})

const roleDistribution = computed(() => {
  if (!dashboard.value) return []
  const total = Number(dashboard.value.totalUsers || 1)
  return [
    { label: '学生', count: dashboard.value.studentCount ?? 0, pct: Math.round(((dashboard.value.studentCount ?? 0) / total) * 100) },
    { label: '教师', count: dashboard.value.teacherCount ?? 0, pct: Math.round(((dashboard.value.teacherCount ?? 0) / total) * 100) },
    { label: '管理员', count: dashboard.value.adminCount ?? 0, pct: Math.round(((dashboard.value.adminCount ?? 0) / total) * 100) },
    { label: '已封禁', count: dashboard.value.bannedCount ?? 0, pct: Math.round(((dashboard.value.bannedCount ?? 0) / total) * 100), danger: true }
  ]
})

function statusLabel(status) {
  const value = Number(status)
  if (value === 1) return '正常'
  if (value === 2) return '锁定'
  return '禁用'
}

function statusClass(status) {
  const value = Number(status)
  if (value === 1) return 'status-ok'
  if (value === 2) return 'status-lock'
  return 'status-off'
}

async function loadDashboard() {
  try {
    dashboard.value = await fetchAdminDashboard(authStore.token)
  } catch (e) {
    error(`仪表盘加载失败：${e.message}`)
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const response = await fetchAdminUsers(authStore.token, filters.value)
    users.value = response.data || []
    totalCount.value = response.total || 0
  } catch (e) {
    error(`加载用户列表失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function applySearch() {
  filters.value.keyword = searchInput.value.trim()
  filters.value.page = 1
  loadUsers()
}

function resetFilters() {
  searchInput.value = ''
  filters.value = { keyword: '', roleType: '', status: '', page: 1, pageSize: 10 }
  loadUsers()
}

function prevPage() {
  if (filters.value.page > 1) {
    filters.value.page -= 1
    loadUsers()
  }
}

function nextPage() {
  if (filters.value.page < totalPages.value) {
    filters.value.page += 1
    loadUsers()
  }
}

function openEditDialog(user) {
  editingUser.value = user
  editForm.value = {
    username: user.username || '',
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    avatarUrl: user.avatarUrl || '',
    roleType: Number(user.roleType ?? 0),
    status: Number(user.status ?? 1)
  }
  showEditDialog.value = true
}

function closeEditDialog() {
  if (editLoading.value) return
  showEditDialog.value = false
  editingUser.value = null
  editForm.value = createEmptyEditForm()
}

function normalizePayload() {
  return {
    nickname: editForm.value.nickname.trim(),
    email: editForm.value.email.trim(),
    phone: editForm.value.phone.trim(),
    avatarUrl: editForm.value.avatarUrl.trim(),
    roleType: Number(editForm.value.roleType),
    status: Number(editForm.value.status)
  }
}

async function submitEdit() {
  if (!editingUser.value) return
  if (!editForm.value.email.trim()) {
    error('邮箱不能为空')
    return
  }

  editLoading.value = true
  try {
    await updateAdminUser(authStore.token, editingUser.value.id, normalizePayload())
    success(`已更新 ${editingUser.value.nickname || editingUser.value.username} 的资料`)
    closeEditDialog()
    await Promise.all([loadUsers(), loadDashboard()])
  } catch (e) {
    error(e.message)
  } finally {
    editLoading.value = false
  }
}

async function handleStatusChange(user, newStatus) {
  actionLoadingId.value = user.id
  try {
    await updateAdminUserStatus(authStore.token, user.id, newStatus)
    success(`已${newStatus === 1 ? '启用' : '封禁'} ${user.nickname || user.username}`)
    await Promise.all([loadUsers(), loadDashboard()])
  } catch (e) {
    error(e.message)
  } finally {
    actionLoadingId.value = null
  }
}

async function handleRoleChange(user, event) {
  const newRole = Number(event.target.value)
  actionLoadingId.value = user.id
  try {
    await updateAdminUserRole(authStore.token, user.id, newRole)
    success(`已将 ${user.nickname || user.username} 的角色更新为${getRoleLabel(newRole)}`)
    await Promise.all([loadUsers(), loadDashboard()])
  } catch (e) {
    error(e.message)
    event.target.value = user.roleType
  } finally {
    actionLoadingId.value = null
  }
}

watch(() => [filters.value.roleType, filters.value.status], () => {
  filters.value.page = 1
  loadUsers()
})

onMounted(async () => {
  await Promise.all([loadDashboard(), loadUsers()])
})
</script>

<template>
  <div class="um-page page-animate">
    <header class="workspace-page-head">
      <h1 class="workspace-page-title">用户管理</h1>
    </header>

    <section class="top-grid">
      <section class="workspace-metric-strip">
        <article
          v-for="card in kpiCards"
          :key="card.label"
          class="metric-card"
          :class="`metric-${card.tone}`"
        >
          <div class="metric-head">
            <span class="metric-label">{{ card.label }}</span>
            <component :is="card.icon" :size="16" class="metric-icon" />
          </div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-note">{{ card.sub }}</div>
        </article>
      </section>

      <section class="summary-grid">
        <article class="panel">
          <header class="panel-head">
            <h2 class="panel-title">角色分布</h2>
          </header>
          <div class="panel-body">
            <div class="role-dist">
              <div v-for="r in roleDistribution" :key="r.label" class="role-bar-row">
                <span class="role-bar-label">{{ r.label }}</span>
                <div class="role-bar-track">
                  <div class="role-bar-fill" :class="{ 'role-bar-danger': r.danger }" :style="{ width: `${r.pct}%` }"></div>
                </div>
                <span class="role-bar-count">{{ r.count }} 人 · {{ r.pct }}%</span>
              </div>
            </div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head panel-head-row">
            <h2 class="panel-title">近 30 天注册趋势</h2>
            <span v-if="trendSummary" class="panel-badge">
              <UserPlus :size="12" /> 累计 {{ trendSummary.total }}
            </span>
          </header>
          <div class="panel-body">
            <div v-if="!registrationTrend.length" class="empty-state">
              <TrendingUp :size="22" />
              <p>暂无注册数据。</p>
            </div>
            <div v-else class="trend-chart">
              <div class="trend-scale">
                <span>{{ trendSummary?.max ?? 0 }}</span>
                <span>0</span>
              </div>
              <div class="trend-canvas">
                <div class="trend-gridlines" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>

                <svg
                  class="trend-svg"
                  :viewBox="`0 0 ${TREND_VB_W} ${TREND_VB_H}`"
                  preserveAspectRatio="none"
                  @mouseleave="trendHoverIdx = -1"
                >
                  <defs>
                    <linearGradient id="trendGradient" x1="0" x2="0" y1="0" y2="1">
                      <stop offset="0%" stop-color="rgba(0,87,194,0.35)" />
                      <stop offset="100%" stop-color="rgba(0,87,194,0)" />
                    </linearGradient>
                  </defs>
                  <path :d="trendGeometry.areaPath" fill="url(#trendGradient)" />
                  <path
                    :d="trendGeometry.linePath"
                    fill="none"
                    stroke="var(--c-accent-primary)"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    vector-effect="non-scaling-stroke"
                  />
                  <circle
                    v-for="(point, index) in trendGeometry.points"
                    :key="point.date"
                    :cx="point.x"
                    :cy="point.y"
                    :r="trendHoverIdx === index ? 3 : 1.6"
                    fill="var(--c-accent-primary)"
                    stroke="var(--c-bg-base-elevated)"
                    stroke-width="1"
                    vector-effect="non-scaling-stroke"
                    @mouseenter="trendHoverIdx = index"
                  />
                </svg>

                <div
                  v-if="trendHoverIdx >= 0 && trendGeometry.points[trendHoverIdx]"
                  class="trend-tooltip"
                  :style="{
                    left: `${(trendGeometry.points[trendHoverIdx].x / TREND_VB_W) * 100}%`,
                    top: `${(trendGeometry.points[trendHoverIdx].y / TREND_VB_H) * 100}%`
                  }"
                >
                  <strong>{{ trendGeometry.points[trendHoverIdx].count }}</strong>
                  <span>{{ trendGeometry.points[trendHoverIdx].date }}</span>
                </div>

                <div class="trend-axis">
                  <span>{{ registrationTrend[0]?.date }}</span>
                  <span v-if="registrationTrend.length > 10">{{ registrationTrend[Math.floor(registrationTrend.length / 2)]?.date }}</span>
                  <span>{{ registrationTrend[registrationTrend.length - 1]?.date }}</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </section>
    </section>

    <article class="panel">
      <header class="panel-head panel-head-row">
        <h2 class="panel-title">用户列表管理</h2>
        <span class="panel-badge">共 {{ totalCount }} 位用户</span>
      </header>
      <div class="panel-body">
        <div class="toolbar">
          <div class="search-wrap">
            <Search :size="15" class="search-icon" />
            <input
              v-model="searchInput"
              class="panel-input search-input"
              placeholder="搜索用户名、昵称或邮箱"
              @keydown.enter="applySearch"
            />
          </div>
          <select v-model="filters.roleType" class="panel-input">
            <option value="">全部角色</option>
            <option :value="0">学生</option>
            <option :value="1">管理员</option>
            <option :value="2">教师</option>
          </select>
          <select v-model="filters.status" class="panel-input">
            <option value="">全部状态</option>
            <option :value="1">正常</option>
            <option :value="0">禁用</option>
            <option :value="2">锁定</option>
          </select>
          <GlowButton variant="primary" @click="applySearch">
            <Search :size="14" /> 搜索
          </GlowButton>
          <button class="btn-ghost" type="button" @click="resetFilters">
            <RefreshCw :size="13" /> 重置
          </button>
        </div>

        <div v-if="loading" class="loading-state">
          <div class="loader-ring"></div>
          <p>正在加载用户数据...</p>
        </div>

        <div v-else class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>用户信息</th>
                <th>当前角色</th>
                <th>账号状态</th>
                <th>注册时间</th>
                <th>最近登录</th>
                <th>角色切换</th>
                <th>管理操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
                :class="{ 'row-banned': Number(user.status) === 0, 'row-loading': actionLoadingId === user.id }"
              >
                <td>
                  <div class="user-cell">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" class="user-avatar" alt="avatar" />
                    <span v-else class="user-avatar user-avatar-fallback" aria-hidden="true">
                      <DefaultAvatarIcon />
                    </span>
                    <div class="user-cell-text">
                      <strong>{{ user.nickname || user.username }}</strong>
                      <span class="muted">@{{ user.username }}</span>
                      <span class="muted">{{ user.email || '未设置邮箱' }}</span>
                    </div>
                  </div>
                </td>
                <td>
                  <span :class="['role-pill', `role-${user.roleType}`]">{{ getRoleLabel(user.roleType) }}</span>
                </td>
                <td>
                  <span :class="['status-pill', statusClass(user.status)]">
                    {{ statusLabel(user.status) }}
                  </span>
                </td>
                <td class="muted">{{ user.createdAt ? new Date(user.createdAt).toLocaleDateString('zh-CN') : '--' }}</td>
                <td class="muted">{{ user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '暂无记录' }}</td>
                <td>
                  <select
                    class="inline-select"
                    :value="user.roleType"
                    :disabled="actionLoadingId === user.id"
                    @change="handleRoleChange(user, $event)"
                  >
                    <option :value="0">学生</option>
                    <option :value="1">管理员</option>
                    <option :value="2">教师</option>
                  </select>
                </td>
                <td>
                  <div class="action-btns">
                    <button
                      class="action-btn neutral"
                      :disabled="actionLoadingId === user.id"
                      @click="openEditDialog(user)"
                    >
                      <PencilLine :size="13" /> 编辑
                    </button>
                    <button
                      v-if="Number(user.status) === 1"
                      class="action-btn danger"
                      :disabled="actionLoadingId === user.id"
                      @click="handleStatusChange(user, 0)"
                    >
                      <UserX :size="13" /> 封禁
                    </button>
                    <button
                      v-else
                      class="action-btn success"
                      :disabled="actionLoadingId === user.id"
                      @click="handleStatusChange(user, 1)"
                    >
                      <UserCheck :size="13" /> 启用
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!users.length">
                <td colspan="7" class="empty-row">
                  <Activity :size="22" />
                  <p>未找到符合条件的用户</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!loading && totalCount > 0" class="pagination">
          <span class="page-info">第 {{ filters.page }} / {{ totalPages }} 页</span>
          <div class="page-btns">
            <button class="page-btn" :disabled="filters.page === 1" @click="prevPage">
              <ChevronLeft :size="15" />
            </button>
            <span class="page-num">{{ filters.page }}</span>
            <button class="page-btn" :disabled="filters.page >= totalPages" @click="nextPage">
              <ChevronRight :size="15" />
            </button>
          </div>
        </div>
      </div>
    </article>

    <teleport to="body">
      <div v-if="showEditDialog" class="dialog-mask" @click.self="closeEditDialog">
        <div class="dialog-card">
          <header class="dialog-head">
            <div>
              <p class="dialog-eyebrow">用户资料维护</p>
              <h3 class="dialog-title">编辑 {{ editingUser?.nickname || editingUser?.username }}</h3>
            </div>
            <button class="dialog-close" type="button" :disabled="editLoading" @click="closeEditDialog">
              <X :size="16" />
            </button>
          </header>

          <div class="dialog-body">
            <label class="form-item">
              <span>登录账号</span>
              <input :value="editForm.username" class="panel-input" readonly />
            </label>

            <div class="form-grid">
              <label class="form-item">
                <span>昵称</span>
                <input v-model="editForm.nickname" class="panel-input" placeholder="输入展示昵称" />
              </label>
              <label class="form-item">
                <span>邮箱</span>
                <div class="input-icon-wrap">
                  <Mail :size="15" />
                  <input v-model="editForm.email" class="panel-input icon-input" placeholder="如 user@example.com" />
                </div>
              </label>
              <label class="form-item">
                <span>手机号</span>
                <div class="input-icon-wrap">
                  <Phone :size="15" />
                  <input v-model="editForm.phone" class="panel-input icon-input" placeholder="可留空" />
                </div>
              </label>
              <label class="form-item">
                <span>头像地址</span>
                <div class="input-icon-wrap">
                  <Link2 :size="15" />
                  <input v-model="editForm.avatarUrl" class="panel-input icon-input" placeholder="https://..." />
                </div>
              </label>
              <label class="form-item">
                <span>角色</span>
                <select v-model="editForm.roleType" class="panel-input">
                  <option :value="0">学生</option>
                  <option :value="1">管理员</option>
                  <option :value="2">教师</option>
                </select>
              </label>
              <label class="form-item">
                <span>状态</span>
                <select v-model="editForm.status" class="panel-input">
                  <option :value="1">正常</option>
                  <option :value="0">禁用</option>
                  <option :value="2">锁定</option>
                </select>
              </label>
            </div>
          </div>

          <footer class="dialog-foot">
            <button class="btn-ghost" type="button" :disabled="editLoading" @click="closeEditDialog">取消</button>
            <GlowButton variant="primary" :disabled="editLoading" @click="submitEdit">
              {{ editLoading ? '保存中...' : '保存资料' }}
            </GlowButton>
          </footer>
        </div>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.um-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.top-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}

.workspace-metric-strip {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(320px, 1.3fr);
  gap: 16px;
  min-width: 0;
}

@media (max-width: 960px) {
  .top-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workspace-metric-strip {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .workspace-metric-strip .metric-card {
    flex: 1 1 180px;
  }

  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px 14px 20px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  overflow: hidden;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.metric-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--c-accent-primary);
  opacity: 0.85;
}

.metric-card.metric-danger::before {
  background: #d45d4a;
}

.metric-card:hover {
  border-color: rgba(0, 87, 194, 0.22);
  transform: translateY(-1px);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.metric-label {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.metric-icon {
  color: var(--c-accent-primary);
  opacity: 0.75;
}

.metric-danger .metric-icon {
  color: #d45d4a;
}

.metric-value {
  margin-top: auto;
  font-family: var(--font-serif);
  font-size: clamp(22px, 2vw, 26px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-primary .metric-value {
  color: var(--c-accent-primary);
}

.metric-danger .metric-value {
  color: #d45d4a;
}

.metric-note {
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

.panel {
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.panel-head {
  padding: 16px 22px 12px;
  border-bottom: 1px solid var(--c-border-glass);
}

.panel-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
}

.panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.role-dist {
  display: grid;
  gap: 12px;
}

.role-bar-row {
  display: grid;
  grid-template-columns: 60px 1fr 140px;
  align-items: center;
  gap: 14px;
}

.role-bar-label {
  font-size: 13px;
  color: var(--c-text-secondary);
  font-weight: 600;
}

.role-bar-track {
  height: 6px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  overflow: hidden;
}

.role-bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--c-accent-primary);
  transition: width 0.6s ease;
}

.role-bar-fill.role-bar-danger {
  background: #d45d4a;
}

.role-bar-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  text-align: right;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 140px 140px auto auto;
  gap: 10px;
  align-items: center;
}

.search-wrap {
  position: relative;
  min-width: 180px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--c-text-muted);
  pointer-events: none;
  z-index: 1;
}

.panel-input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 13.5px;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}

.search-wrap .search-input,
.icon-input {
  padding-left: 38px;
}

.panel-input::placeholder {
  color: var(--c-text-faint);
}

.panel-input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 9px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast), color var(--duration-fast);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table-wrap {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  padding: 10px 14px;
  text-align: left;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  background: var(--c-bg-surface-hover);
  border-bottom: 1px solid var(--c-border-glass);
}

.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  font-size: 13px;
  vertical-align: middle;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr.row-banned td {
  opacity: 0.58;
}

.data-table tbody tr.row-loading td {
  opacity: 0.6;
  pointer-events: none;
}

.user-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  flex-shrink: 0;
}

.user-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.3), transparent 34%),
    linear-gradient(135deg, rgba(0, 87, 194, 0.18), rgba(0, 110, 242, 0.34));
  color: var(--c-accent-primary);
}

[data-theme='dark'] .user-avatar-fallback {
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(175, 198, 255, 0.22), rgba(82, 106, 184, 0.46));
  color: #eef3ff;
}

.user-cell-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.user-cell-text strong {
  color: var(--c-text-primary);
  font-size: 13.5px;
  font-weight: 600;
}

.muted {
  color: var(--c-text-muted);
  font-size: 11.5px;
}

.role-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.role-0 {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.role-1 {
  background: rgba(180, 113, 55, 0.16);
  color: #b36b2d;
}

.role-2 {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-ok {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-lock {
  background: rgba(180, 113, 55, 0.16);
  color: #b36b2d;
}

.status-off {
  background: rgba(212, 93, 74, 0.14);
  color: #c14f3f;
}

.inline-select {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 12.5px;
  min-width: 96px;
}

.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background-color var(--duration-fast), border-color var(--duration-fast);
}

.action-btn.neutral {
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.18);
}

.action-btn.neutral:hover:not(:disabled) {
  background: rgba(0, 87, 194, 0.14);
}

.action-btn.danger {
  background: rgba(212, 93, 74, 0.08);
  color: #c14f3f;
  border-color: rgba(212, 93, 74, 0.18);
}

.action-btn.danger:hover:not(:disabled) {
  background: rgba(212, 93, 74, 0.14);
}

.action-btn.success {
  background: rgba(30, 138, 91, 0.1);
  color: #1e8a5b;
  border-color: rgba(30, 138, 91, 0.22);
}

.action-btn.success:hover:not(:disabled) {
  background: rgba(30, 138, 91, 0.18);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty-row {
  text-align: center;
  padding: 36px 0;
  color: var(--c-text-muted);
}

.empty-row p {
  margin: 8px 0 0;
}

.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.page-info {
  font-size: 12.5px;
  color: var(--c-text-muted);
}

.page-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-accent-primary);
  min-width: 22px;
  text-align: center;
}

.trend-chart {
  display: flex;
  gap: 10px;
  padding: 4px 0;
}

.trend-scale {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4px 0 20px;
  font-size: 11px;
  color: var(--c-text-muted);
  min-width: 22px;
  text-align: right;
}

.trend-canvas {
  flex: 1;
  min-width: 0;
  position: relative;
}

.trend-gridlines {
  position: absolute;
  inset: 4px 0 20px 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  pointer-events: none;
}

.trend-gridlines span {
  display: block;
  height: 1px;
  background: rgba(24, 27, 35, 0.06);
}

[data-theme='dark'] .trend-gridlines span {
  background: rgba(255, 255, 255, 0.08);
}

.trend-svg {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 140px;
  display: block;
  padding: 4px 0;
}

.trend-svg circle {
  cursor: pointer;
  transition: r 0.15s ease;
}

.trend-tooltip {
  position: absolute;
  transform: translate(-50%, calc(-100% - 8px));
  background: var(--c-text-primary);
  color: var(--c-bg-base);
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.trend-tooltip strong {
  font-family: var(--font-serif);
  font-size: 12.5px;
  font-weight: 700;
}

.trend-tooltip span {
  opacity: 0.7;
  font-size: 10.5px;
}

.trend-axis {
  display: flex;
  justify-content: space-between;
  padding: 6px 2px 0;
  font-size: 10.5px;
  color: var(--c-text-muted);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  color: var(--c-text-muted);
  font-size: 13px;
}

.empty-state :deep(svg) {
  color: var(--c-text-faint);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 36px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.loader-ring {
  width: 26px;
  height: 26px;
  border: 2px solid var(--c-accent-primary-glow);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(9, 15, 26, 0.42);
  backdrop-filter: blur(8px);
}

.dialog-card {
  width: min(720px, 100%);
  border-radius: 20px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: 0 30px 80px rgba(12, 24, 44, 0.22);
  overflow: hidden;
}

.dialog-head,
.dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
}

.dialog-head {
  border-bottom: 1px solid var(--c-border-glass);
}

.dialog-foot {
  border-top: 1px solid var(--c-border-glass);
}

.dialog-eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--c-text-muted);
}

.dialog-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  color: var(--c-text-primary);
}

.dialog-close {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: transparent;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.dialog-close:hover:not(:disabled) {
  background: var(--c-bg-surface-hover);
}

.dialog-body {
  padding: 20px 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item span {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--c-text-secondary);
}

.input-icon-wrap {
  position: relative;
}

.input-icon-wrap svg {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--c-text-muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1200px) {
  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .toolbar {
    grid-template-columns: 1fr;
  }

  .role-bar-row {
    grid-template-columns: 50px 1fr 110px;
    gap: 10px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .dialog-head,
  .dialog-foot,
  .dialog-body {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
