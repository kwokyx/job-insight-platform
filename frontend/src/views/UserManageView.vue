<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import DefaultAvatarIcon from '../components/common/DefaultAvatarIcon.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { fetchAdminDashboard, fetchAdminUsers, updateAdminUserProfile, updateAdminUserRole, updateAdminUserStatus, invalidateApiCache } from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Check,
  ChevronDown,
  Search, RefreshCw, Users, ShieldCheck, GraduationCap, UserX, UserCheck,
  ChevronLeft, ChevronRight, Activity, UserPlus, TrendingUp, PencilLine, X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(true)
const actionLoadingId = ref(null)
const editSaving = ref(false)
const dashboard = ref(null)
const users = ref([])
const totalCount = ref(0)

const filters = ref({ keyword: '', roleType: '', status: '', page: 1, pageSize: 10 })
const searchInput = ref('')
const openRoleMenuId = ref(null)
const editingUser = ref(null)
const editForm = ref({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: ''
})

const roleOptions = [
  { value: 0, label: '学生', icon: Users },
  { value: 1, label: '管理员', icon: ShieldCheck },
  { value: 2, label: '教师', icon: GraduationCap }
]

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / filters.value.pageSize)))

// KPI 仅保留 3 个核心指标：总用户 / 今日活跃 / 封禁
// 角色细分（学生/教师/管理员）已在下方"角色分布"panel 展示，避免冗余
const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '总用户数', value: dashboard.value.totalUsers ?? '--', sub: `今日新增 ${dashboard.value.newUsersToday ?? 0}`, icon: Users, tone: 'primary' },
    { label: '今日活跃', value: dashboard.value.activeToday ?? '--', sub: '最近 24 小时登录', icon: Activity, tone: 'primary' },
    { label: '封禁账号', value: dashboard.value.bannedCount ?? '--', sub: '待人工核实', icon: UserX, tone: 'danger' }
  ]
})

// 近 30 天注册趋势（来自 /admin/dashboard 的 registrationTrend 字段）
const registrationTrend = computed(() => {
  const raw = dashboard.value?.registrationTrend
  if (!Array.isArray(raw) || !raw.length) return []
  return raw.map((row) => ({
    date: row.date || row.day || row.registerDate || row.createdAt,
    count: Number(row.count ?? row.total ?? row.newUsers ?? 0)
  })).filter((r) => r.date)
})

const trendSummary = computed(() => {
  const rows = registrationTrend.value
  if (!rows.length) return null
  const total = rows.reduce((sum, r) => sum + r.count, 0)
  const max = rows.reduce((m, r) => Math.max(m, r.count), 0)
  const peak = rows.find((r) => r.count === max)
  return { total, max, peak }
})

// 折线图坐标计算：viewBox 300x100，折线 + 下方面积
const TREND_VB_W = 300
const TREND_VB_H = 100
const trendGeometry = computed(() => {
  const rows = registrationTrend.value
  const max = trendSummary.value?.max || 1
  if (!rows.length) return { linePath: '', areaPath: '', points: [] }
  const stepX = rows.length > 1 ? TREND_VB_W / (rows.length - 1) : 0
  const points = rows.map((r, i) => ({
    x: Number((i * stepX).toFixed(2)),
    y: Number((TREND_VB_H - (r.count / max) * TREND_VB_H).toFixed(2)),
    count: r.count,
    date: r.date
  }))
  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const areaPath = `${linePath} L${TREND_VB_W},${TREND_VB_H} L0,${TREND_VB_H} Z`
  return { linePath, areaPath, points }
})
// 悬停点索引（用户鼠标在哪个数据点上）
const trendHoverIdx = ref(-1)

const roleDistribution = computed(() => {
  if (!dashboard.value) return []
  const total = Number(dashboard.value.totalUsers || 1)
  return [
    { label: '学生', count: dashboard.value.studentCount ?? 0, pct: Math.round((dashboard.value.studentCount ?? 0) / total * 100) },
    { label: '教师', count: dashboard.value.teacherCount ?? 0, pct: Math.round((dashboard.value.teacherCount ?? 0) / total * 100) },
    { label: '管理员', count: dashboard.value.adminCount ?? 0, pct: Math.round((dashboard.value.adminCount ?? 0) / total * 100) },
    { label: '已封禁', count: dashboard.value.bannedCount ?? 0, pct: Math.round((dashboard.value.bannedCount ?? 0) / total * 100), danger: true }
  ]
})

async function loadDashboard() {
  try {
    dashboard.value = await fetchAdminDashboard(authStore.token)
  } catch (e) {
    error(`仪表盘加载失败：${e.message}`)
  }
}

async function loadUsers() {
  loading.value = true
  openRoleMenuId.value = null
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
  filters.value.keyword = searchInput.value
  filters.value.page = 1
  loadUsers()
}

function resetFilters() {
  searchInput.value = ''
  filters.value = { keyword: '', roleType: '', status: '', page: 1, pageSize: 10 }
  loadUsers()
}

function prevPage() { if (filters.value.page > 1) { filters.value.page--; loadUsers() } }
function nextPage() { if (filters.value.page < totalPages.value) { filters.value.page++; loadUsers() } }

function currentRoleOption(user) {
  return roleOptions.find((opt) => Number(opt.value) === Number(user.roleType)) || roleOptions[0]
}

function toggleRoleMenu(userId) {
  openRoleMenuId.value = openRoleMenuId.value === userId ? null : userId
}

function closeRoleMenu() {
  openRoleMenuId.value = null
}

function onDocClick(event) {
  const target = event.target
  if (target instanceof Element && target.closest('.role-select-dropdown')) return
  closeRoleMenu()
}

function onDocKey(event) {
  if (event.key === 'Escape' && editingUser.value) {
    closeEditDialog()
    return
  }
  if (event.key === 'Escape') closeRoleMenu()
}

function rowHiddenByFilter(user) {
  const f = filters.value.status
  return f !== '' && f !== null && f !== undefined && Number(f) !== Number(user.status)
}

function openEditDialog(user) {
  editingUser.value = { ...user }
  editForm.value = {
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    avatarUrl: user.avatarUrl || ''
  }
}

function closeEditDialog() {
  if (editSaving.value) return
  editingUser.value = null
  editForm.value = {
    nickname: '',
    email: '',
    phone: '',
    avatarUrl: ''
  }
}

function patchUserInList(updatedUser) {
  users.value = users.value.map((item) => (
    item.id === updatedUser.id ? { ...item, ...updatedUser } : item
  ))
}

async function handleStatusChange(user, newStatus) {
  actionLoadingId.value = user.id
  try {
    await updateAdminUserStatus(authStore.token, user.id, newStatus)
    user.status = newStatus
    success(`已${newStatus === 1 ? '启用' : '封禁'} ${user.nickname || user.username}`)
    invalidateApiCache('/admin/')
    loadDashboard()
    if (rowHiddenByFilter(user)) loadUsers()
  } catch (e) {
    error(e.message)
  } finally {
    actionLoadingId.value = null
  }
}

async function handleRoleChange(user, newRole) {
  if (Number(user.roleType) === Number(newRole)) {
    closeRoleMenu()
    return
  }
  closeRoleMenu()
  actionLoadingId.value = user.id
  try {
    await updateAdminUserRole(authStore.token, user.id, newRole)
    user.roleType = newRole
    success(`已将 ${user.nickname || user.username} 角色改为「${getRoleLabel(newRole)}」`)
    invalidateApiCache('/admin/')
    loadDashboard()
    if (filters.value.roleType !== '' && Number(filters.value.roleType) !== newRole) {
      loadUsers()
    }
  } catch (e) {
    error(e.message)
  } finally {
    actionLoadingId.value = null
  }
}

async function handleSaveUserProfile() {
  if (!editingUser.value || editSaving.value) return
  editSaving.value = true
  try {
    const updatedUser = await updateAdminUserProfile(authStore.token, editingUser.value.id, {
      nickname: editForm.value.nickname.trim(),
      email: editForm.value.email.trim(),
      phone: editForm.value.phone.trim(),
      avatarUrl: editForm.value.avatarUrl.trim()
    })
    patchUserInList(updatedUser)
    success(`已更新 ${updatedUser.nickname || updatedUser.username} 的资料`)
    invalidateApiCache('/admin/')
    closeEditDialog()
  } catch (e) {
    error(e.message)
  } finally {
    editSaving.value = false
  }
}

watch(() => [filters.value.roleType, filters.value.status], () => {
  filters.value.page = 1
  loadUsers()
})

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onDocKey)
  await Promise.all([loadDashboard(), loadUsers()])
})

onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onDocKey)
})
</script>

<template>
  <div class="um-page page-animate">
    <header class="workspace-page-head">
      <h1 class="workspace-page-title">用户管理</h1>
    </header>

    <!-- 顶部 grid：左侧 KPI 纵向堆叠，右侧角色分布 + 注册趋势两列 -->
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
                <div class="role-bar-fill" :class="{ 'role-bar-danger': r.danger }" :style="{ width: r.pct + '%' }"></div>
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
            <UserPlus :size="12" /> 合计 {{ trendSummary.total }}
          </span>
        </header>
        <div class="panel-body">
          <div v-if="!registrationTrend.length" class="empty-state">
            <TrendingUp :size="22" />
            <p>暂无注册数据。</p>
          </div>
          <div v-else class="trend-chart">
            <!-- 左侧刻度（最大值）-->
            <div class="trend-scale">
              <span>{{ trendSummary?.max ?? 0 }}</span>
              <span>0</span>
            </div>
            <!-- canvas：参考基线 + SVG 折线 + x 轴日期 -->
            <div class="trend-canvas">
              <div class="trend-gridlines" aria-hidden="true">
                <span></span><span></span><span></span>
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
                <!-- 面积填充 -->
                <path :d="trendGeometry.areaPath" fill="url(#trendGradient)" />
                <!-- 折线 -->
                <path
                  :d="trendGeometry.linePath"
                  fill="none"
                  stroke="var(--c-accent-primary)"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  vector-effect="non-scaling-stroke"
                />
                <!-- 数据点（所有 30 个）-->
                <circle
                  v-for="(p, i) in trendGeometry.points"
                  :key="p.date"
                  :cx="p.x"
                  :cy="p.y"
                  :r="trendHoverIdx === i ? 3 : 1.6"
                  fill="var(--c-accent-primary)"
                  stroke="var(--c-bg-base-elevated)"
                  stroke-width="1"
                  vector-effect="non-scaling-stroke"
                  @mouseenter="trendHoverIdx = i"
                />
              </svg>

              <!-- 悬停 tooltip：显示当前悬停点的日期+人数 -->
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

              <!-- x 轴日期（首/中/尾三个锚点，避免挤）-->
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
              placeholder="搜索用户名 / 昵称 / 邮箱"
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
          </select>
          <GlowButton variant="primary" @click="applySearch">
            <Search :size="14" /> 搜索
          </GlowButton>
          <button class="btn-ghost" type="button" @click="resetFilters">
            <RefreshCw :size="13" /> 重置
          </button>
        </div>

        <div v-if="loading" class="um-loading-skel">
          <SkeletonCard type="list" :lines="6" />
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
                <th>角色操作</th>
                <th>账号操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="user in users"
                :key="user.id"
                :class="{ 'row-loading': actionLoadingId === user.id }"
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
                  <span :class="['status-pill', Number(user.status) === 1 ? 'status-ok' : 'status-off']">
                    {{ Number(user.status) === 1 ? '正常' : '已封禁' }}
                  </span>
                </td>
                <td class="muted">{{ user.createdAt ? new Date(user.createdAt).toLocaleDateString('zh-CN') : '--' }}</td>
                <td class="muted">{{ user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '暂无记录' }}</td>
                <td>
                  <div
                    class="role-select-dropdown"
                    :class="{ open: openRoleMenuId === user.id }"
                  >
                    <button
                      type="button"
                      class="role-select-trigger"
                      aria-haspopup="listbox"
                      :aria-expanded="openRoleMenuId === user.id"
                      :disabled="actionLoadingId === user.id"
                      @click.stop="toggleRoleMenu(user.id)"
                    >
                      <component :is="currentRoleOption(user).icon" :size="14" class="role-select-trigger-icon" />
                      <span>{{ currentRoleOption(user).label }}</span>
                      <ChevronDown :size="14" class="role-select-caret" />
                    </button>
                    <div class="role-select-panel" role="listbox">
                      <button
                        v-for="opt in roleOptions"
                        :key="opt.value"
                        type="button"
                        class="role-select-item"
                        :class="{ active: Number(user.roleType) === Number(opt.value) }"
                        :aria-selected="Number(user.roleType) === Number(opt.value)"
                        :disabled="actionLoadingId === user.id"
                        @click.stop="handleRoleChange(user, opt.value)"
                      >
                        <component :is="opt.icon" :size="15" class="role-select-item-icon" />
                        <span class="role-select-item-label">{{ opt.label }}</span>
                        <Check
                          v-if="Number(user.roleType) === Number(opt.value)"
                          :size="14"
                          class="role-select-item-check"
                        />
                      </button>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="action-btns">
                    <button
                      class="action-btn secondary"
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

  </div>

  <Teleport to="body">
    <transition name="modal-fade">
      <div v-if="editingUser" class="user-edit-modal-backdrop" @click.self="closeEditDialog">
        <div class="user-edit-modal">
          <div class="user-edit-modal-head">
            <div class="user-edit-modal-title">
              <h2>编辑用户资料</h2>
              <p>@{{ editingUser.username }} · {{ getRoleLabel(editingUser.roleType) }}</p>
            </div>
            <button type="button" class="user-edit-close" :disabled="editSaving" @click="closeEditDialog">
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
            <button type="button" class="action-btn secondary" :disabled="editSaving" @click="closeEditDialog">取消</button>
            <GlowButton variant="primary" :loading="editSaving" @click="handleSaveUserProfile">保存资料</GlowButton>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.um-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---------------- 顶部 grid：左 KPI + 右 摘要 ---------------- */
/* KPI 纵向堆叠占左侧 240px，右侧填满放角色分布+趋势图 */
.top-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
}

/* KPI 三张：纵向堆叠（在 top-grid 里） */
.workspace-metric-strip {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 右侧摘要：角色分布 + 趋势图并排 */
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
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--c-accent-primary);
  opacity: 0.85;
}
.metric-card.metric-danger::before {
  background: #e06060;
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
  font-family: var(--font-sans);
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
  color: #b23b2e;
  opacity: 0.85;
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
  color: #b23b2e;
}

.metric-note {
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

/* ---------------- Panel ---------------- */
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

/* ---------------- Role distribution ---------------- */
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
  background: #b23b2e;
}

.role-bar-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* ---------------- Toolbar ---------------- */
.toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 140px 140px auto auto;
  gap: 10px;
  align-items: center;
}

.search-wrap {
  position: relative;
  flex: 1;
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
  font-family: var(--font-sans);
  font-size: 13.5px;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}

/* 更高特异度，避免被 .panel-input 的 shorthand padding 覆盖 */
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

/* ---------------- Table ---------------- */
.table-wrap {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-sans);
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

.data-table tbody tr.row-loading td { opacity: 0.6; pointer-events: none; }

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

.role-0 { background: var(--c-accent-primary-glow); color: var(--c-accent-primary); }
.role-1 { background: rgba(140, 90, 180, 0.14); color: #8c5ab4; }
.role-2 { background: rgba(30, 138, 91, 0.12); color: #1e8a5b; }

.status-ok { background: rgba(30, 138, 91, 0.12); color: #1e8a5b; }
.status-off { background: rgba(178, 59, 46, 0.12); color: #b23b2e; }

.role-select-dropdown {
  position: relative;
  display: inline-flex;
  align-items: stretch;
}

.role-select-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 128px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid rgba(193, 198, 215, 0.55);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.role-select-trigger > span {
  flex: 1;
  text-align: center;
}

.role-select-trigger:hover:not(:disabled) {
  color: var(--c-accent-primary);
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(30, 117, 255, 0.06);
}

.role-select-dropdown.open .role-select-trigger {
  color: var(--c-accent-primary);
  border-color: rgba(30, 117, 255, 0.34);
  background: rgba(30, 117, 255, 0.08);
}

.role-select-trigger:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.role-select-trigger-icon {
  flex-shrink: 0;
  color: var(--c-text-muted);
}

.role-select-dropdown.open .role-select-trigger-icon,
.role-select-trigger:hover:not(:disabled) .role-select-trigger-icon {
  color: var(--c-accent-primary);
}

.role-select-caret {
  flex-shrink: 0;
  color: var(--c-text-muted);
  transition: transform 0.18s ease, color 0.15s ease;
}

.role-select-dropdown.open .role-select-caret {
  transform: rotate(180deg);
  color: var(--c-accent-primary);
}

.role-select-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 160px;
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: #ffffff;
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.14),
    0 2px 6px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-direction: column;
  gap: 1px;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(-4px);
  transition:
    opacity 140ms ease,
    transform 140ms ease,
    visibility 0s linear 140ms;
  z-index: 30;
}

.role-select-dropdown.open .role-select-panel {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
  transition:
    opacity 140ms ease,
    transform 140ms ease,
    visibility 0s linear 0s;
}

:global([data-theme="dark"]) .role-select-panel {
  background: #1a1f2d;
  border-color: var(--c-border-glass);
  box-shadow:
    0 16px 36px rgba(0, 0, 0, 0.45),
    0 2px 6px rgba(0, 0, 0, 0.35);
}

.role-select-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}

.role-select-item:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.role-select-item.active {
  background: var(--c-bg-surface-strong);
  color: var(--c-accent-primary);
  box-shadow: var(--shadow-card-quiet);
}

:global([data-theme="dark"]) .role-select-item.active {
  background: rgba(30, 117, 255, 0.18);
}

.role-select-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.role-select-item-icon {
  flex-shrink: 0;
  color: var(--c-text-muted);
}

.role-select-item:hover:not(:disabled) .role-select-item-icon,
.role-select-item.active .role-select-item-icon {
  color: var(--c-accent-primary);
}

.role-select-item-label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  text-align: left;
}

.role-select-item.active .role-select-item-label {
  font-weight: 700;
}

.role-select-item-check {
  flex-shrink: 0;
  color: var(--c-accent-primary);
}

.action-btns {
  display: flex;
  gap: 6px;
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

.action-btn.secondary {
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.18);
}

.action-btn.secondary:hover:not(:disabled) {
  background: rgba(0, 87, 194, 0.14);
}

.action-btn.danger {
  background: rgba(178, 59, 46, 0.08);
  color: #b23b2e;
  border-color: rgba(178, 59, 46, 0.22);
}

.action-btn.danger:hover:not(:disabled) {
  background: rgba(178, 59, 46, 0.14);
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

.empty-row p {
  margin: 8px 0 0;
}

/* ---------------- Pagination ---------------- */
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

/* ---------------- Risk cards ---------------- */
.risk-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.risk-item {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.risk-item strong {
  display: block;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.risk-item p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
}

.risk-warn {
  border-color: rgba(164, 94, 5, 0.28);
}

/* ---------------- Trend bars ---------------- */
/* ---------- 趋势图（左刻度 + canvas + x 轴）---------- */
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
  font-family: var(--font-sans);
  font-size: 11px;
  color: var(--c-text-muted);
  font-variant-numeric: tabular-nums;
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
/* SVG 折线 + 面积 */
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

/* 悬停 tooltip（日期 + 人数），绝对定位到对应数据点上方 */
.trend-tooltip {
  position: absolute;
  transform: translate(-50%, calc(-100% - 8px));
  background: var(--c-text-primary);
  color: var(--c-bg-base);
  padding: 5px 9px;
  border-radius: 6px;
  font-family: var(--font-sans);
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

/* x 轴日期 */
.trend-axis {
  display: flex;
  justify-content: space-between;
  padding: 6px 2px 0;
  font-family: var(--font-sans);
  font-size: 10.5px;
  color: var(--c-text-muted);
  font-variant-numeric: tabular-nums;
}

.empty-state {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 24px 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px; color: var(--c-text-muted); font-size: 13px;
}
.empty-state :deep(svg) { color: var(--c-text-faint); }

/* ---------------- Loading ---------------- */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 36px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.um-loading-skel { padding: 8px 0 4px; }

/* ---------------- Responsive ---------------- */
@media (max-width: 1200px) {
  .workspace-metric-strip {
    grid-template-columns: repeat(3, 1fr);
  }

  .risk-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .workspace-metric-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .toolbar {
    grid-template-columns: 1fr;
  }

  .user-edit-modal-backdrop {
    padding: 14px;
  }

  .user-edit-grid {
    grid-template-columns: 1fr;
  }

  .role-bar-row {
    grid-template-columns: 50px 1fr 110px;
    gap: 10px;
  }
}
</style>
