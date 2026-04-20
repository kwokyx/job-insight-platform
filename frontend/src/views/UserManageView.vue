<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { fetchAdminDashboard, fetchAdminUsers, updateAdminUserRole, updateAdminUserStatus } from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Search, RefreshCw, Users, ShieldCheck, GraduationCap, UserX, UserCheck,
  ChevronLeft, ChevronRight, Activity, Briefcase, FileText
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

const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / filters.value.pageSize)))

const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '总用户数', value: dashboard.value.totalUsers ?? '--', sub: `今日新增 ${dashboard.value.newUsersToday ?? 0}`, icon: Users, tone: 'primary' },
    { label: '学生账号', value: dashboard.value.studentCount ?? '--', sub: `今日活跃 ${dashboard.value.activeToday ?? 0}`, icon: GraduationCap, tone: 'neutral' },
    { label: '教师账号', value: dashboard.value.teacherCount ?? '--', sub: '就业指导老师', icon: ShieldCheck, tone: 'neutral' },
    { label: '封禁账号', value: dashboard.value.bannedCount ?? '--', sub: '待人工核实', icon: UserX, tone: 'danger' },
    { label: '平台岗位', value: dashboard.value.totalJobs ?? '--', sub: `7 天新增 ${dashboard.value.newJobs7d ?? 0}`, icon: Briefcase, tone: 'primary' },
    { label: '分析报告', value: dashboard.value.totalReports ?? '--', sub: '报告总量', icon: FileText, tone: 'neutral' }
  ]
})

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
    success(`已将 ${user.nickname || user.username} 角色改为「${getRoleLabel(newRole)}」`)
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

    <article class="panel">
      <header class="panel-head">
        <h2 class="panel-title">用户角色分布</h2>
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
                <th>角色操作</th>
                <th>账号操作</th>
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
                    <img
                      :src="user.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${user.username}`"
                      class="user-avatar"
                      alt="avatar"
                    />
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

    <article class="panel">
      <header class="panel-head">
        <h2 class="panel-title">账号风险速查</h2>
      </header>
      <div class="panel-body">
        <div class="risk-grid">
          <div class="risk-item" :class="(dashboard?.bannedCount ?? 0) > 0 ? 'risk-warn' : 'risk-ok'">
            <strong>封禁账号 {{ dashboard?.bannedCount ?? 0 }} 个</strong>
            <p>{{ (dashboard?.bannedCount ?? 0) > 0 ? '建议定期审查封禁原因并更新处置记录。' : '系统处于健康状态。' }}</p>
          </div>
          <div class="risk-item" :class="(dashboard?.teacherCount ?? 0) === 0 ? 'risk-warn' : 'risk-ok'">
            <strong>教师账号 {{ dashboard?.teacherCount ?? 0 }} 个</strong>
            <p>{{ (dashboard?.teacherCount ?? 0) === 0 ? '教师工作台功能目前处于闲置状态。' : '教师侧已有入驻，建议持续监控使用深度。' }}</p>
          </div>
          <div class="risk-item risk-ok">
            <strong>总用户规模 {{ dashboard?.totalUsers ?? 0 }} 人</strong>
            <p>今日新增 {{ dashboard?.newUsersToday ?? 0 }} · 活跃 {{ dashboard?.activeToday ?? 0 }} · 活跃率 {{ dashboard?.totalUsers ? Math.round((dashboard.activeToday ?? 0) / dashboard.totalUsers * 100) : 0 }}%</p>
          </div>
        </div>
      </div>
    </article>
  </div>
</template>

<style scoped>
.um-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---------------- Metric strip ---------------- */
.workspace-metric-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 126px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
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
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--c-text-muted);
  pointer-events: none;
}

.search-input {
  padding-left: 34px;
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

.data-table tbody tr.row-banned td { opacity: 0.55; }
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
  flex-shrink: 0;
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

.loader-ring {
  width: 26px;
  height: 26px;
  border: 2px solid var(--c-accent-primary-glow);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

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

  .role-bar-row {
    grid-template-columns: 50px 1fr 110px;
    gap: 10px;
  }
}
</style>
