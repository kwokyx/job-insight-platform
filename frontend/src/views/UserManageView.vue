<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { fetchAdminDashboard, fetchAdminUsers, updateAdminUserRole, updateAdminUserStatus } from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Search, RefreshCw, Users, ShieldCheck, GraduationCap, UserX, UserCheck,
  ChevronLeft, ChevronRight, Activity, Briefcase, FileText, AlertTriangle
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
    { label: '总用户数', value: dashboard.value.totalUsers ?? '--', sub: `今日新增 ${dashboard.value.newUsersToday ?? 0}`, icon: Users, color: 'primary' },
    { label: '学生账号', value: dashboard.value.studentCount ?? '--', sub: `今日活跃 ${dashboard.value.activeToday ?? 0}`, icon: GraduationCap, color: 'teal' },
    { label: '教师账号', value: dashboard.value.teacherCount ?? '--', sub: '就业指导老师', icon: ShieldCheck, color: 'secondary' },
    { label: '封禁账号', value: dashboard.value.bannedCount ?? '--', sub: '需要人工核实处理', icon: UserX, color: 'danger' },
    { label: '平台岗位', value: dashboard.value.totalJobs ?? '--', sub: `7天新增 ${dashboard.value.newJobs7d ?? 0}`, icon: Briefcase, color: 'primary' },
    { label: '分析报告', value: dashboard.value.totalReports ?? '--', sub: '已生成报告总量', icon: FileText, color: 'secondary' },
  ]
})

const roleDistribution = computed(() => {
  if (!dashboard.value) return []
  const total = Number(dashboard.value.totalUsers || 1)
  return [
    { label: '学生', count: dashboard.value.studentCount ?? 0, pct: Math.round((dashboard.value.studentCount ?? 0) / total * 100), color: '#38BDF8' },
    { label: '教师', count: dashboard.value.teacherCount ?? 0, pct: Math.round((dashboard.value.teacherCount ?? 0) / total * 100), color: '#34D399' },
    { label: '管理员', count: dashboard.value.adminCount ?? 0, pct: Math.round((dashboard.value.adminCount ?? 0) / total * 100), color: '#A78BFA' },
    { label: '已封禁', count: dashboard.value.bannedCount ?? 0, pct: Math.round((dashboard.value.bannedCount ?? 0) / total * 100), color: '#F87171' },
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
  <div class="um-page page-shell">

    <!-- 页头 -->
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">管理员专属模块</span>
        <h1 class="page-intro-title">用户与权限管理中心</h1>
        <p class="page-intro-text">查看平台全量用户、按角色与状态筛选，并对账号进行封禁/启用、角色变更等直接操作。所有变更均实时生效并记录操作日志。</p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">今日新增</span>
          <span class="intro-metric-value">{{ dashboard?.newUsersToday ?? '--' }}</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">封禁账号</span>
          <span class="intro-metric-value" style="color: #f87171">{{ dashboard?.bannedCount ?? '--' }}</span>
        </div>
      </div>
    </section>

    <!-- KPI 统计卡 -->
    <section class="kpi-grid">
      <div v-for="card in kpiCards" :key="card.label" class="kpi-card glass-panel" :class="`kpi-${card.color}`">
        <div class="kpi-top">
          <component :is="card.icon" :size="18" />
          <span>{{ card.label }}</span>
        </div>
        <strong class="kpi-val">{{ card.value }}</strong>
        <span class="kpi-sub">{{ card.sub }}</span>
      </div>
    </section>

    <!-- 角色分布 -->
    <PremiumCard title="用户角色分布" glowColor="primary">
      <div class="role-dist">
        <div v-for="r in roleDistribution" :key="r.label" class="role-bar-row">
          <span class="role-bar-label">{{ r.label }}</span>
          <div class="role-bar-track">
            <div class="role-bar-fill" :style="{ width: r.pct + '%', background: r.color }"></div>
          </div>
          <span class="role-bar-count">{{ r.count }} 人 ({{ r.pct }}%)</span>
        </div>
      </div>
    </PremiumCard>

    <!-- 用户表格 -->
    <PremiumCard title="用户列表管理" glowColor="secondary">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="search-wrap">
          <Search :size="16" class="search-icon" />
          <input
            v-model="searchInput"
            class="search-input"
            placeholder="搜索用户名 / 昵称 / 邮箱"
            @keydown.enter="applySearch"
          />
        </div>
        <select v-model="filters.roleType" class="filter-select">
          <option value="">全部角色</option>
          <option :value="0">学生</option>
          <option :value="1">管理员</option>
          <option :value="2">教师</option>
        </select>
        <select v-model="filters.status" class="filter-select">
          <option value="">全部状态</option>
          <option :value="1">正常</option>
          <option :value="0">禁用</option>
        </select>
        <GlowButton variant="primary" @click="applySearch">
          <Search :size="14" /> 搜索
        </GlowButton>
        <GlowButton variant="ghost" @click="resetFilters">
          <RefreshCw :size="14" /> 重置
        </GlowButton>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loader-ring"></div>
        <p>正在加载用户数据...</p>
      </div>

      <!-- 表格 -->
      <div v-else class="table-wrap">
        <table class="user-table">
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
            <tr v-for="user in users" :key="user.id" :class="{ 'row-banned': Number(user.status) === 0, 'row-loading': actionLoadingId === user.id }">
              <td>
                <div class="user-cell">
                  <img
                    :src="user.avatarUrl || `https://api.dicebear.com/7.x/notionists/svg?seed=${user.username}`"
                    class="user-avatar"
                    alt="avatar"
                  />
                  <div>
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
                    <UserX :size="14" /> 封禁
                  </button>
                  <button
                    v-else
                    class="action-btn success"
                    :disabled="actionLoadingId === user.id"
                    @click="handleStatusChange(user, 1)"
                  >
                    <UserCheck :size="14" /> 启用
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!users.length">
              <td colspan="7" class="empty-row">
                <Activity :size="28" />
                <p>未找到符合条件的用户</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="!loading && totalCount > 0">
        <span class="page-info">共 {{ totalCount }} 位用户，第 {{ filters.page }} / {{ totalPages }} 页</span>
        <div class="page-btns">
          <button class="page-btn" :disabled="filters.page === 1" @click="prevPage">
            <ChevronLeft :size="16" />
          </button>
          <span class="page-num">{{ filters.page }}</span>
          <button class="page-btn" :disabled="filters.page >= totalPages" @click="nextPage">
            <ChevronRight :size="16" />
          </button>
        </div>
      </div>
    </PremiumCard>

    <!-- 风险提示 -->
    <PremiumCard title="账号风险速查" glowColor="teal">
      <div class="risk-grid">
        <div class="risk-item" :class="(dashboard?.bannedCount ?? 0) > 0 ? 'risk-warn' : 'risk-ok'">
          <AlertTriangle :size="18" />
          <div>
            <strong>封禁账号 {{ dashboard?.bannedCount ?? 0 }} 个</strong>
            <p>{{ (dashboard?.bannedCount ?? 0) > 0 ? '存在已封禁账号，建议定期审查封禁原因并更新处置记录。' : '当前无封禁账号，系统处于健康状态。' }}</p>
          </div>
        </div>
        <div class="risk-item" :class="(dashboard?.teacherCount ?? 0) === 0 ? 'risk-warn' : 'risk-ok'">
          <GraduationCap :size="18" />
          <div>
            <strong>教师账号 {{ dashboard?.teacherCount ?? 0 }} 个</strong>
            <p>{{ (dashboard?.teacherCount ?? 0) === 0 ? '尚无教师账号，教师工作台功能目前处于闲置状态。' : '教师侧已有入驻，建议持续监控教师模块的使用深度。' }}</p>
          </div>
        </div>
        <div class="risk-item risk-ok">
          <Users :size="18" />
          <div>
            <strong>总用户规模 {{ dashboard?.totalUsers ?? 0 }} 人</strong>
            <p>今日新增 {{ dashboard?.newUsersToday ?? 0 }} 人，今日活跃 {{ dashboard?.activeToday ?? 0 }} 人，活跃率 {{ dashboard?.totalUsers ? Math.round((dashboard.activeToday ?? 0) / dashboard.totalUsers * 100) : 0 }}%。</p>
          </div>
        </div>
      </div>
    </PremiumCard>

  </div>
</template>

<style scoped>
.um-page { display: grid; gap: 24px; }

/* KPI */
.kpi-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 16px; }
.kpi-card {
  display: grid; gap: 10px; padding: 18px; border-radius: var(--radius-lg);
  border: 1px solid var(--c-border-glass); background: rgba(255,255,255,0.03);
}
.kpi-top { display: flex; align-items: center; gap: 8px; color: var(--c-text-secondary); font-size: 13px; }
.kpi-val { font-size: 32px; line-height: 1; }
.kpi-sub { font-size: 12px; color: var(--c-text-muted); }
.kpi-primary .kpi-val { color: var(--c-accent-primary); }
.kpi-teal .kpi-val { color: var(--c-accent-teal); }
.kpi-secondary .kpi-val { color: var(--c-accent-secondary); }
.kpi-danger .kpi-val { color: #f87171; }

/* Role distribution */
.role-dist { display: grid; gap: 14px; }
.role-bar-row { display: grid; grid-template-columns: 60px 1fr 110px; align-items: center; gap: 14px; }
.role-bar-label { font-size: 13px; color: var(--c-text-secondary); }
.role-bar-track { height: 8px; border-radius: 999px; background: rgba(255,255,255,0.07); overflow: hidden; }
.role-bar-fill { height: 100%; border-radius: 999px; transition: width 0.6s ease; }
.role-bar-count { font-size: 13px; color: var(--c-text-muted); text-align: right; }

/* Toolbar */
.toolbar { display: grid; grid-template-columns: 1fr 140px 140px auto auto; gap: 12px; margin-bottom: 20px; align-items: center; }
.search-wrap { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--c-text-muted); pointer-events: none; }
.search-input { width: 100%; padding: 11px 14px 11px 36px; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); background: rgba(255,255,255,0.04); color: var(--c-text-primary); }
.filter-select { width: 100%; padding: 11px 14px; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); background: rgba(255,255,255,0.04); color: var(--c-text-primary); }

/* Table */
.table-wrap { overflow-x: auto; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); }
.user-table { width: 100%; border-collapse: collapse; }
.user-table th { padding: 12px 14px; text-align: left; color: var(--c-text-muted); font-size: 12px; font-weight: 600; letter-spacing: 0.05em; text-transform: uppercase; background: rgba(255,255,255,0.02); border-bottom: 1px solid var(--c-border-glass); }
.user-table td { padding: 14px; border-bottom: 1px solid rgba(255,255,255,0.04); vertical-align: middle; }
.user-table tr:last-child td { border-bottom: none; }
.user-table tr.row-banned td { opacity: 0.55; }
.user-table tr.row-loading td { opacity: 0.6; pointer-events: none; }
.user-table tr:hover td { background: rgba(255,255,255,0.02); }

.user-cell { display: flex; align-items: flex-start; gap: 12px; }
.user-avatar { width: 36px; height: 36px; border-radius: 999px; border: 1px solid var(--c-border-glass); flex-shrink: 0; }
.user-cell div { display: grid; gap: 2px; }
.user-cell strong { color: var(--c-text-primary); font-size: 14px; }
.muted { color: var(--c-text-muted); font-size: 12px; }

.role-pill { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.role-0 { background: rgba(56, 189, 248, 0.12); color: var(--c-accent-primary); }
.role-1 { background: rgba(167, 139, 250, 0.15); color: #a78bfa; }
.role-2 { background: rgba(52, 211, 153, 0.12); color: var(--c-accent-teal); }

.status-pill { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.status-ok { background: rgba(52, 211, 153, 0.12); color: var(--c-accent-teal); }
.status-off { background: rgba(248, 113, 113, 0.12); color: #f87171; }

.inline-select { padding: 7px 10px; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); background: rgba(255,255,255,0.05); color: var(--c-text-primary); font-size: 13px; min-width: 100px; }

.action-btns { display: flex; gap: 8px; }
.action-btn { display: flex; align-items: center; gap: 5px; padding: 6px 12px; border-radius: var(--radius-md); font-size: 12px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; }
.action-btn.danger { background: rgba(248, 113, 113, 0.1); color: #f87171; border-color: rgba(248, 113, 113, 0.2); }
.action-btn.danger:hover { background: rgba(248, 113, 113, 0.2); }
.action-btn.success { background: rgba(52, 211, 153, 0.1); color: var(--c-accent-teal); border-color: rgba(52, 211, 153, 0.2); }
.action-btn.success:hover { background: rgba(52, 211, 153, 0.2); }
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.empty-row { text-align: center; padding: 48px 0; color: var(--c-text-muted); }
.empty-row p { margin-top: 10px; }

/* Pagination */
.pagination { display: flex; justify-content: space-between; align-items: center; margin-top: 18px; }
.page-info { font-size: 13px; color: var(--c-text-muted); }
.page-btns { display: flex; align-items: center; gap: 8px; }
.page-btn { display: grid; place-items: center; width: 32px; height: 32px; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); background: rgba(255,255,255,0.04); color: var(--c-text-primary); cursor: pointer; transition: all 0.2s; }
.page-btn:hover:not(:disabled) { background: rgba(56, 189, 248, 0.12); border-color: rgba(56, 189, 248, 0.3); }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-num { font-size: 14px; font-weight: 600; color: var(--c-accent-primary); min-width: 24px; text-align: center; }

/* Risk */
.risk-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.risk-item { display: grid; grid-template-columns: 24px 1fr; gap: 12px; padding: 18px; border-radius: var(--radius-md); border: 1px solid var(--c-border-glass); }
.risk-item strong { display: block; color: var(--c-text-primary); margin-bottom: 4px; }
.risk-item p { font-size: 13px; color: var(--c-text-secondary); }
.risk-warn { border-color: rgba(245, 158, 11, 0.25); }
.risk-warn > svg { color: #f59e0b; }
.risk-ok > svg { color: var(--c-accent-teal); }

@media (max-width: 1200px) {
  .kpi-grid { grid-template-columns: repeat(3, 1fr); }
  .risk-grid { grid-template-columns: 1fr; }
  .toolbar { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 768px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .toolbar { grid-template-columns: 1fr; }
}
</style>
