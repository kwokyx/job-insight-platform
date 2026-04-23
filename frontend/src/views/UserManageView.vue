<script setup>
import { computed, onMounted, ref } from 'vue'
import DefaultAvatarIcon from '../components/common/DefaultAvatarIcon.vue'
import FloatingSelect from '../components/common/FloatingSelect.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminDashboard,
  fetchAdminUsers,
  invalidateApiCache,
  updateAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus
} from '../api'
import { mapErrorMessage } from '../utils/errorMap'
import { getRoleLabel } from '../utils/role'
import {
  Activity,
  ChevronLeft,
  ChevronRight,
  PencilLine,
  RefreshCw,
  Search,
  TrendingUp,
  UserPlus,
  Users,
  UserX,
  X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const { success, error } = useToast()

const DEFAULT_PAGE_SIZE = 10

function buildDefaultFilters() {
  return {
    keyword: '',
    roleType: '',
    status: '',
    page: 1,
    pageSize: DEFAULT_PAGE_SIZE
  }
}

const loading = ref(true)
const actionLoadingKey = ref('')
const editSaving = ref(false)
const dashboard = ref(null)
const users = ref([])
const totalCount = ref(0)

const filters = ref(buildDefaultFilters())
const searchInput = ref('')
const editingUser = ref(null)
const editForm = ref({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: ''
})

const roleOptions = [
  { value: 0, label: '学生' },
  { value: 1, label: '管理员' },
  { value: 2, label: '教师' }
]
const roleFilterOptions = [{ value: '', label: '全部角色' }, ...roleOptions]

const statusOptions = [
  { value: 1, label: '正常', tone: 'ok' },
  { value: 0, label: '禁用', tone: 'off' }
]
const statusFilterOptions = [{ value: '', label: '全部状态' }, ...statusOptions]

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / filters.value.pageSize)))
const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    {
      label: '总用户数',
      value: dashboard.value.totalUsers ?? '--',
      sub: `今日新增 ${dashboard.value.newUsersToday ?? 0}`,
      icon: Users,
      tone: 'primary'
    },
    {
      label: '今日活跃',
      value: dashboard.value.activeLast24h ?? dashboard.value.activeToday ?? '--',
      sub: '近 24 小时登录',
      icon: Activity,
      tone: 'primary'
    },
    {
      label: '封禁账号',
      value: dashboard.value.bannedCount ?? '--',
      sub: '建议优先核查',
      icon: UserX,
      tone: 'danger'
    }
  ]
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
const registrationTrend = computed(() => {
  const rows = dashboard.value?.registrationTrend
  if (!Array.isArray(rows)) return []
  return rows.map((row) => ({
    date: row.date || row.day || row.registerDate || row.createdAt,
    count: Number(row.count ?? row.total ?? row.newUsers ?? 0)
  })).filter((row) => row.date)
})
const trendSummary = computed(() => {
  const rows = registrationTrend.value
  if (!rows.length) return null
  const total = rows.reduce((sum, row) => sum + row.count, 0)
  const max = rows.reduce((value, row) => Math.max(value, row.count), 0)
  return { total, max }
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
const appliedFilterCount = computed(() => {
  let count = 0
  if (filters.value.keyword) count += 1
  if (filters.value.roleType !== '') count += 1
  if (filters.value.status !== '') count += 1
  return count
})

function getStatusMeta(status) {
  const normalizedStatus = Number(status) === 1 ? 1 : 0
  const matched = statusOptions.find((option) => Number(option.value) === normalizedStatus)
  if (matched) return matched
  if (status === '' || status === null || status === undefined) {
    return { value: '', label: '--', tone: 'unknown' }
  }
  return { value: normalizedStatus, label: '禁用', tone: 'off' }
}

function isUserBusy(userId) {
  return actionLoadingKey.value.startsWith(`${userId}:`)
}

function formatDate(value) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString('zh-CN')
}

function formatDateTime(value) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}

async function loadDashboard() {
  if (!authStore.token) return
  try {
    dashboard.value = await fetchAdminDashboard(authStore.token)
  } catch (e) {
    error(`加载用户概览失败：${mapErrorMessage(e)}`)
  }
}

async function loadUsers({ silent = false } = {}) {
  if (!authStore.token) return
  if (!silent) loading.value = true
  try {
    const response = await fetchAdminUsers(authStore.token, filters.value)
    users.value = Array.isArray(response?.data) ? response.data : []
    totalCount.value = Number(response?.total || 0)
  } catch (e) {
    error(`加载用户列表失败：${mapErrorMessage(e)}`)
  } finally {
    if (!silent) loading.value = false
  }
}

function applySearch() {
  filters.value.keyword = searchInput.value.trim()
  filters.value.page = 1
  void loadUsers()
}

function applySelectFilters() {
  filters.value.page = 1
  void loadUsers()
}

function resetFilters() {
  searchInput.value = ''
  filters.value = buildDefaultFilters()
  void loadUsers()
}

function prevPage() {
  if (filters.value.page <= 1) return
  filters.value.page -= 1
  void loadUsers()
}

function nextPage() {
  if (filters.value.page >= totalPages.value) return
  filters.value.page += 1
  void loadUsers()
}

function openEditDialog(user) {
  editingUser.value = {
    ...user,
    roleType: Number(user.roleType ?? 0),
    status: Number(user.status ?? 1)
  }
  editForm.value = {
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    avatarUrl: user.avatarUrl || ''
  }
}

function closeEditDialog(force = false) {
  if (editSaving.value && !force) return
  editingUser.value = null
  editForm.value = {
    nickname: '',
    email: '',
    phone: '',
    avatarUrl: ''
  }
}

async function handleRoleChange(user, newRole) {
  if (Number(user.roleType) === Number(newRole)) return
  actionLoadingKey.value = `${user.id}:role`
  try {
    await updateAdminUserRole(authStore.token, user.id, Number(newRole))
    success(`已将 ${user.nickname || user.username} 角色改为「${getRoleLabel(Number(newRole))}」`)
    invalidateApiCache('/admin/')
    if (editingUser.value?.id === user.id) {
      editingUser.value.roleType = Number(newRole)
    }
    await Promise.all([loadUsers({ silent: true }), loadDashboard()])
  } catch (e) {
    error(`更新角色失败：${mapErrorMessage(e)}`)
  } finally {
    actionLoadingKey.value = ''
  }
}

async function handleStatusChange(user, newStatus) {
  if (Number(user.status) === Number(newStatus)) return
  actionLoadingKey.value = `${user.id}:status`
  try {
    await updateAdminUserStatus(authStore.token, user.id, Number(newStatus))
    success(`已将 ${user.nickname || user.username} 状态改为「${getStatusMeta(Number(newStatus)).label}」`)
    invalidateApiCache('/admin/')
    if (editingUser.value?.id === user.id) {
      editingUser.value.status = Number(newStatus)
    }
    await Promise.all([loadUsers({ silent: true }), loadDashboard()])
  } catch (e) {
    error(`更新账号状态失败：${mapErrorMessage(e)}`)
  } finally {
    actionLoadingKey.value = ''
  }
}

async function handleSaveUserProfile() {
  if (!editingUser.value || editSaving.value) return
  const currentUser = editingUser.value
  editSaving.value = true
  try {
    await updateAdminUser(authStore.token, currentUser.id, {
      nickname: editForm.value.nickname.trim(),
      email: editForm.value.email.trim(),
      phone: editForm.value.phone.trim(),
      avatarUrl: editForm.value.avatarUrl.trim(),
      roleType: Number(currentUser.roleType),
      status: Number(currentUser.status)
    })
    success(`已更新 ${currentUser.nickname || currentUser.username} 的资料`)
    invalidateApiCache('/admin/')
    closeEditDialog(true)
    await Promise.all([loadUsers({ silent: true }), loadDashboard()])
  } catch (e) {
    error(`更新用户资料失败：${mapErrorMessage(e)}`)
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  void Promise.all([loadDashboard(), loadUsers()])
})
</script>

<template>
  <div class="um-page page-animate">
    <header class="workspace-page-head um-head">
      <h1 class="workspace-page-title">用户管理</h1>
      <span class="um-badge">
        <Users :size="14" />
        共 {{ totalCount }} 位用户
      </span>
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
          <header class="panel-head panel-head-row">
            <h2 class="panel-title">角色分布</h2>
          </header>
          <div class="panel-body">
            <div v-if="!roleDistribution.length" class="empty-state">
              <Users :size="22" />
              <p>暂无用户概览数据。</p>
            </div>
            <div v-else class="role-dist">
              <div v-for="role in roleDistribution" :key="role.label" class="role-bar-row">
                <span class="role-bar-label">{{ role.label }}</span>
                <div class="role-bar-track">
                  <div
                    class="role-bar-fill"
                    :class="{ 'role-bar-danger': role.danger }"
                    :style="{ width: `${role.pct}%` }"
                  ></div>
                </div>
                <span class="role-bar-count">{{ role.count }} 人 · {{ role.pct }}%</span>
              </div>
            </div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head panel-head-row">
            <h2 class="panel-title">近 30 天注册趋势</h2>
            <span v-if="trendSummary" class="panel-badge">
              <UserPlus :size="12" />
              累计 {{ trendSummary.total }}
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
                    <linearGradient id="userTrendGradient" x1="0" x2="0" y1="0" y2="1">
                      <stop offset="0%" stop-color="rgba(0,87,194,0.35)" />
                      <stop offset="100%" stop-color="rgba(0,87,194,0)" />
                    </linearGradient>
                  </defs>
                  <path :d="trendGeometry.areaPath" fill="url(#userTrendGradient)" />
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
                  <span v-if="registrationTrend.length > 10">
                    {{ registrationTrend[Math.floor(registrationTrend.length / 2)]?.date }}
                  </span>
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
        <h2 class="panel-title">用户列表</h2>
        <span class="panel-badge">
          {{ appliedFilterCount ? `已应用 ${appliedFilterCount} 个筛选` : '未应用筛选' }}
        </span>
      </header>

      <div class="panel-body">
        <div class="toolbar">
          <div class="search-wrap">
            <Search :size="15" class="search-icon" />
            <input
              v-model="searchInput"
              class="panel-input search-input"
              placeholder="搜索用户名 / 昵称 / 邮箱"
              @keydown.enter="applySearch"
            />
          </div>

          <FloatingSelect
            class="toolbar-select"
            :model-value="filters.roleType"
            :options="roleFilterOptions"
            placeholder="全部角色"
            aria-label="按角色筛选"
            @update:model-value="
              (value) => {
                filters.roleType = value
                applySelectFilters()
              }
            "
          />

          <FloatingSelect
            class="toolbar-select"
            :model-value="filters.status"
            :options="statusFilterOptions"
            placeholder="全部状态"
            aria-label="按状态筛选"
            @update:model-value="
              (value) => {
                filters.status = value
                applySelectFilters()
              }
            "
          />

          <GlowButton variant="primary" @click="applySearch">
            <Search :size="14" />
            搜索
          </GlowButton>

          <button class="btn-ghost" type="button" @click="resetFilters">
            <RefreshCw :size="13" />
            重置
          </button>
        </div>

        <div class="list-meta">
          <span>当前第 {{ filters.page }} / {{ totalPages }} 页</span>
          <span>本页 {{ users.length }} 条</span>
          <span>每页 {{ filters.pageSize }} 条</span>
        </div>

        <div v-if="loading" class="um-loading-skel">
          <SkeletonCard type="list" :lines="6" />
        </div>

        <div v-else class="table-wrap">
          <table class="data-table">
            <colgroup>
              <col class="col-user-info" />
              <col class="col-role" />
              <col class="col-status" />
              <col class="col-created-at" />
              <col class="col-last-login" />
              <col class="col-actions" />
            </colgroup>
            <thead>
              <tr>
                <th>用户信息</th>
                <th>当前角色</th>
                <th>账号状态</th>
                <th>注册时间</th>
                <th>最近登录</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
                :class="{ 'row-loading': isUserBusy(user.id) }"
              >
                <td>
                  <div class="user-cell">
                    <img
                      v-if="user.avatarUrl"
                      :src="user.avatarUrl"
                      class="user-avatar"
                      alt="avatar"
                    />
                    <span v-else class="user-avatar user-avatar-fallback" aria-hidden="true">
                      <DefaultAvatarIcon />
                    </span>
                    <div class="user-cell-text">
                      <strong :title="user.nickname || user.username || `用户 #${user.id}`">
                        {{ user.nickname || user.username || `用户 #${user.id}` }}
                      </strong>
                      <span class="muted" :title="user.username ? `@${user.username}` : '--'">
                        @{{ user.username || '--' }}
                      </span>
                      <span class="muted" :title="user.email || '未设置邮箱'">
                        {{ user.email || '未设置邮箱' }}
                      </span>
                    </div>
                  </div>
                </td>

                <td>
                  <span :class="['role-pill', `role-${Number(user.roleType)}`]">
                    {{ getRoleLabel(Number(user.roleType)) }}
                  </span>
                </td>

                <td>
                  <span :class="['status-pill', `status-${getStatusMeta(user.status).tone}`]">
                    {{ getStatusMeta(user.status).label }}
                  </span>
                </td>

                <td class="muted">{{ formatDate(user.createdAt) }}</td>
                <td class="muted">{{ formatDateTime(user.lastLoginAt) }}</td>

                <td class="operations-cell">
                  <div class="action-stack">
                    <label class="action-select">
                      <span>角色</span>
                      <FloatingSelect
                        class="action-dropdown"
                        size="sm"
                        :model-value="Number(user.roleType ?? 0)"
                        :options="roleOptions"
                        :disabled="isUserBusy(user.id)"
                        aria-label="修改用户角色"
                        @update:model-value="(value) => handleRoleChange(user, Number(value))"
                      />
                    </label>

                    <label class="action-select">
                      <span>状态</span>
                      <FloatingSelect
                        class="action-dropdown"
                        size="sm"
                        :model-value="Number(user.status ?? 1)"
                        :options="statusOptions"
                        :disabled="isUserBusy(user.id)"
                        aria-label="修改用户状态"
                        @update:model-value="(value) => handleStatusChange(user, Number(value))"
                      />
                    </label>

                    <button
                      type="button"
                      class="action-btn secondary"
                      :disabled="isUserBusy(user.id)"
                      @click="openEditDialog(user)"
                    >
                      <PencilLine :size="13" />
                      编辑资料
                    </button>
                  </div>
                </td>
              </tr>

              <tr v-if="!users.length">
                <td colspan="6" class="empty-row">
                  <Users :size="22" />
                  <p>未找到符合条件的用户</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!loading && totalCount > 0" class="pagination">
          <span class="page-info">共 {{ totalCount }} 条记录</span>
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

    <Teleport to="body">
    <transition name="modal-fade">
      <div v-if="editingUser" class="user-edit-modal-backdrop" @click.self="closeEditDialog()">
        <div class="user-edit-modal">
          <div class="user-edit-modal-head">
            <div class="user-edit-modal-title">
              <h2>编辑用户资料</h2>
              <p>
                @{{ editingUser.username || '--' }}
                · {{ getRoleLabel(Number(editingUser.roleType)) }}
                · {{ getStatusMeta(editingUser.status).label }}
              </p>
            </div>

            <button
              type="button"
              class="user-edit-close"
              :disabled="editSaving"
              @click="closeEditDialog()"
            >
              <X :size="16" />
            </button>
          </div>

          <div class="user-edit-modal-body">
            <div class="user-edit-preview">
              <img
                v-if="editForm.avatarUrl"
                :src="editForm.avatarUrl"
                class="user-edit-avatar"
                alt="avatar preview"
              />
              <span v-else class="user-edit-avatar user-edit-avatar-fallback" aria-hidden="true">
                <DefaultAvatarIcon />
              </span>
              <div class="user-edit-preview-copy">
                <strong>{{ editForm.nickname || editingUser.nickname || editingUser.username }}</strong>
                <span>{{ editForm.email || '未设置邮箱' }}</span>
              </div>
            </div>

            <div class="user-edit-grid">
              <label class="user-edit-field">
                <span>昵称</span>
                <input v-model="editForm.nickname" class="panel-input" placeholder="请输入昵称" />
              </label>

              <label class="user-edit-field">
                <span>邮箱</span>
                <input v-model="editForm.email" class="panel-input" placeholder="请输入邮箱" />
              </label>

              <label class="user-edit-field">
                <span>手机号</span>
                <input v-model="editForm.phone" class="panel-input" placeholder="请输入手机号" />
              </label>

              <label class="user-edit-field user-edit-field-full">
                <span>头像地址</span>
                <input v-model="editForm.avatarUrl" class="panel-input" placeholder="请输入头像 URL" />
              </label>
            </div>
          </div>

          <div class="user-edit-modal-actions">
            <button
              type="button"
              class="action-btn secondary"
              :disabled="editSaving"
              @click="closeEditDialog()"
            >
              取消
            </button>
            <GlowButton variant="primary" :loading="editSaving" @click="handleSaveUserProfile">
              保存资料
            </GlowButton>
          </div>
        </div>
      </div>
    </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.um-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.um-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.um-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.top-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  align-items: stretch;
  gap: 16px;
}

.workspace-metric-strip {
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 0;
  align-self: stretch;
  min-height: 100%;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(320px, 1.3fr);
  align-items: stretch;
  gap: 16px;
  min-width: 0;
}

.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 0;
  padding: 16px 16px 16px 22px;
  border: 0;
  border-radius: 0;
  background: transparent;
  overflow: hidden;
  transition: border-color var(--duration-fast), transform var(--duration-fast);
}

.metric-card + .metric-card {
  border-top: 1px solid var(--c-border-glass);
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
  background: rgba(0, 87, 194, 0.03);
  transform: none;
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

.metric-danger .metric-icon,
.metric-danger .metric-value {
  color: #d45d4a;
}

.metric-value {
  margin-top: 0;
  font-family: var(--font-serif);
  font-size: clamp(22px, 1.9vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
}

.metric-note {
  font-size: 12px;
  line-height: 1.4;
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
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}

.panel-badge {
  display: inline-flex;
  align-items: center;
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
  position: relative;
  flex: 1;
  min-width: 0;
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

:global([data-theme='dark']) .trend-gridlines span {
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
  transition: r var(--duration-fast);
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

.empty-state p {
  margin: 0;
}

.empty-state :deep(svg) {
  color: var(--c-text-faint);
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

.toolbar-select {
  width: 100%;
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
  font-family: var(--font-sans);
  font-size: 13.5px;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}

.search-wrap .search-input {
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
  gap: 5px;
  padding: 8px 14px;
  border-radius: 9px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast), color var(--duration-fast);
}

.btn-ghost:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.list-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--c-text-muted);
}

.table-wrap {
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-family: var(--font-sans);
}

.col-user-info {
  width: 25%;
}

.col-role {
  width: 10%;
}

.col-status {
  width: 10%;
}

.col-created-at {
  width: 12%;
}

.col-last-login {
  width: 15%;
}

.col-actions {
  width: 28%;
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
  white-space: nowrap;
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

.data-table tbody tr.row-loading td {
  opacity: 0.6;
  pointer-events: none;
}

.user-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
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

:global([data-theme='dark']) .user-avatar-fallback {
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

.user-cell-text > * {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  background: rgba(140, 90, 180, 0.14);
  color: #8c5ab4;
}

.role-2 {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-ok {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-off {
  background: rgba(178, 59, 46, 0.12);
  color: #b23b2e;
}

.status-warn {
  background: rgba(164, 94, 5, 0.14);
  color: #8d5a06;
}

.status-unknown {
  background: rgba(92, 104, 123, 0.12);
  color: var(--c-text-secondary);
}

.operations-cell {
  min-width: 0;
}

.action-stack {
  display: flex;
  flex-wrap: nowrap;
  align-items: flex-end;
  gap: 6px;
}

.action-select {
  display: grid;
  gap: 4px;
  min-width: 88px;
}

.action-select span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.action-dropdown {
  width: 100%;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background-color var(--duration-fast), border-color var(--duration-fast);
  white-space: nowrap;
}

.action-btn.secondary {
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.18);
}

.action-btn.secondary:hover:not(:disabled) {
  background: rgba(0, 87, 194, 0.14);
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
  transition: background-color var(--duration-fast), border-color var(--duration-fast), color var(--duration-fast);
}

.page-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
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

.um-loading-skel {
  padding: 8px 0 4px;
}

.user-edit-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.44);
  backdrop-filter: blur(6px);
}

.user-edit-modal {
  width: min(640px, 100%);
  border-radius: 18px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-raised);
  overflow: hidden;
}

.user-edit-modal-head,
.user-edit-modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
}

.user-edit-modal-head {
  border-bottom: 1px solid var(--c-border-glass);
}

.user-edit-modal-title h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.user-edit-modal-title p {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

.user-edit-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.user-edit-modal-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px;
}

.user-edit-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.user-edit-avatar {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
}

.user-edit-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-muted);
}

.user-edit-preview-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.user-edit-preview-copy strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.user-edit-preview-copy span {
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.user-edit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.user-edit-field {
  display: grid;
  gap: 8px;
}

.user-edit-field span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.user-edit-field-full {
  grid-column: 1 / -1;
}

.user-edit-modal-actions {
  border-top: 1px solid var(--c-border-glass);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 180ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 1080px) {
  .top-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .workspace-metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    grid-template-rows: 1fr;
  }

  .workspace-metric-strip .metric-card {
    min-height: 128px;
  }

  .workspace-metric-strip .metric-card + .metric-card {
    border-top: 0;
    border-left: 1px solid var(--c-border-glass);
  }

  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .table-wrap {
    overflow-x: auto;
    overflow-y: hidden;
  }

  .data-table {
    min-width: 860px;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }

  .workspace-metric-strip {
    grid-template-columns: 1fr;
    grid-template-rows: repeat(3, minmax(104px, auto));
  }

  .workspace-metric-strip .metric-card {
    min-height: 104px;
  }

  .workspace-metric-strip .metric-card + .metric-card {
    border-left: 0;
    border-top: 1px solid var(--c-border-glass);
  }

  .role-bar-row {
    grid-template-columns: 1fr;
    gap: 7px;
  }

  .role-bar-count {
    text-align: left;
  }

  .pagination {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .user-edit-modal-backdrop {
    padding: 14px;
  }

  .user-edit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
