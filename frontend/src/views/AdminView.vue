<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminDashboard,
  fetchAdminLogs,
  fetchAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus
} from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Activity,
  Briefcase,
  FileText,
  RefreshCw,
  Users
} from 'lucide-vue-next'

const authStore = useAuthStore()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(true)
const dashboard = ref(null)
const users = ref([])
const userFilters = ref({
  keyword: '',
  roleType: '',
  status: '',
  page: 1,
  pageSize: 8
})

// ---------- System logs (paginated via /admin/logs) ----------
const logs = ref([])
const logsLoading = ref(false)
const logsPage = ref(1)
const logsPageSize = ref(10)
const logsTotal = ref(0)
const logsHasMore = computed(() => logs.value.length < logsTotal.value)

// Falls back to dashboard.recentLogs until fetchAdminLogs succeeds at least once.
const displayLogs = computed(() => {
  if (logs.value.length) return logs.value
  return dashboard.value?.recentLogs || []
})

const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '总用户数', value: dashboard.value.totalUsers, hint: `今日新增 ${dashboard.value.newUsersToday || 0}`, icon: Users },
    { label: '岗位总量', value: dashboard.value.totalJobs, hint: `近 7 天新增 ${dashboard.value.newJobs7d || 0}`, icon: Briefcase },
    { label: '报告总量', value: dashboard.value.totalReports, hint: '累计产出', icon: FileText },
    {
      label: '今日活跃',
      value: dashboard.value.activeToday || 0,
      hint: `学生 ${dashboard.value.studentCount || 0} / 教师 ${dashboard.value.teacherCount || 0} / 管理员 ${dashboard.value.adminCount || 0}`,
      icon: Activity
    }
  ]
})

const operationInsights = computed(() => {
  if (!dashboard.value) return []
  const totalUsers = Number(dashboard.value.totalUsers || 0)
  const activeToday = Number(dashboard.value.activeToday || 0)
  const bannedCount = Number(dashboard.value.bannedCount || 0)
  const totalJobs = Number(dashboard.value.totalJobs || 0)
  const totalReports = Number(dashboard.value.totalReports || 0)
  const teacherCount = Number(dashboard.value.teacherCount || 0)
  const userActivation = totalUsers ? ((activeToday / totalUsers) * 100).toFixed(1) : '0.0'
  const reportDensity = totalUsers ? (totalReports / totalUsers).toFixed(2) : '0.00'
  const jobDensity = totalUsers ? Math.round(totalJobs / totalUsers) : 0

  return [
    {
      title: '活跃度判断',
      level: activeToday / Math.max(totalUsers, 1) >= 0.3 ? 'good' : 'warn',
      summary: `今日活跃率约 ${userActivation}%`,
      detail: activeToday / Math.max(totalUsers, 1) >= 0.3
        ? '当前用户规模较小，但活跃占比不低，说明平台对少量种子用户已有吸引力。'
        : '活跃率偏低，建议优先通过报告、推荐和通知链路提升回访频次。'
    },
    {
      title: '内容产出密度',
      level: reportDensity >= 0.5 ? 'good' : 'warn',
      summary: `人均报告产出 ${reportDensity} 份`,
      detail: reportDensity >= 0.5
        ? '报告中心已开始形成内容沉淀，可继续放大角色化报告的使用场景。'
        : '报告产出密度偏低，说明分析结果还没有稳定进入日常使用流程。'
    },
    {
      title: '岗位供给杠杆',
      level: jobDensity > 50000 ? 'good' : 'neutral',
      summary: `单用户可触达岗位约 ${jobDensity.toLocaleString('zh-CN')} 条`,
      detail: '岗位数据储备已经足够支撑推荐、报告和趋势分析，下一步瓶颈不在数据量，而在解释力和转化链路。'
    },
    {
      title: '教师渗透情况',
      level: teacherCount > 0 ? 'warn' : 'risk',
      summary: `教师账号 ${teacherCount} 个`,
      detail: teacherCount > 0
        ? '教师角色已具备基本入口，但数量仍然偏少，后续要推动课程上传和供需分析真正使用。'
        : '教师侧尚未形成真实使用者，教师工作台虽然可访问，但还没有用户沉淀。'
    },
    {
      title: '账号风险',
      level: bannedCount > 0 ? 'warn' : 'good',
      summary: `已封禁账号 ${bannedCount} 个`,
      detail: bannedCount > 0
        ? '平台已经出现需要治理的账号样本，说明权限和风控机制开始进入真实场景。'
        : '当前没有封禁样本，更多反映用户规模较小，而不是治理压力已经消失。'
    }
  ]
})

// ---------- Sidebar navigation ----------
// Two logical groups covering the five admin panels. The order here is the
// order they render in the right column and the order they appear in the
// sidebar.
const navGroups = [
  {
    title: '概览',
    items: [
      { id: 'section-diagnostics', label: '运营诊断' },
      { id: 'section-actions', label: '管理员动作' }
    ]
  },
  {
    title: '治理',
    items: [
      { id: 'section-logs', label: '系统日志' },
      { id: 'section-risks', label: '平台风险' },
      { id: 'section-users', label: '用户管理' }
    ]
  }
]

const activeSection = ref('section-diagnostics')
let observer = null

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  if (!element) return

  // Match DashboardView's pattern: prefer .main-content scroll container, then
  // fall back to the document/window scroll.
  const scrollContainer = document.querySelector('.main-content')

  if (scrollContainer instanceof HTMLElement) {
    const containerRect = scrollContainer.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const targetTop = scrollContainer.scrollTop + elementRect.top - containerRect.top - 12

    scrollContainer.scrollTo({
      top: Math.max(targetTop, 0),
      behavior: 'smooth'
    })
    activeSection.value = sectionId
    return
  }

  const targetTop = element.getBoundingClientRect().top + window.scrollY - 12
  window.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
  activeSection.value = sectionId
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  if (observer) {
    observer.disconnect()
    observer = null
  }

  const sections = document.querySelectorAll('.admin-section')
  if (!sections.length) return

  const root = document.querySelector('.main-content') || null

  observer = new IntersectionObserver(
    (entries) => {
      // Prefer the topmost currently-intersecting section. Falling back to
      // isIntersecting alone can flicker when two sections overlap the band;
      // sorting by boundingClientRect.top keeps the "current" one stable.
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)

      if (visible[0]) activeSection.value = visible[0].target.id
    },
    {
      root,
      rootMargin: '-20% 0px -60% 0px',
      threshold: 0
    }
  )

  sections.forEach((section) => observer.observe(section))
}

async function loadDashboard() {
  dashboard.value = await fetchAdminDashboard(authStore.token)
}

async function loadUsers() {
  const response = await fetchAdminUsers(authStore.token, userFilters.value)
  users.value = response.data || []
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([loadDashboard(), loadUsers()])
  } catch (e) {
    error(`管理员工作台加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function loadLogs(append = false) {
  logsLoading.value = true
  try {
    const res = await fetchAdminLogs(authStore.token, {
      page: logsPage.value,
      pageSize: logsPageSize.value
    })
    // Be defensive about the response shape: api.js wrapper yields
    // { data, total, page, pageSize }, but tolerate Java-style `records`
    // or a plain array if something upstream changes.
    const rawItems = Array.isArray(res)
      ? res
      : (res?.data ?? res?.records ?? [])
    const items = Array.isArray(rawItems) ? rawItems : []
    if (append) {
      logs.value = logs.value.concat(items)
    } else {
      logs.value = items
    }
    const totalFromRes = Number(res?.total)
    logsTotal.value = Number.isFinite(totalFromRes) && totalFromRes > 0
      ? totalFromRes
      : logs.value.length
  } catch (e) {
    error('加载日志失败：' + e.message)
  } finally {
    logsLoading.value = false
  }
}

function refreshLogs() {
  logsPage.value = 1
  loadLogs(false)
}

function loadMoreLogs() {
  if (logsLoading.value || !logsHasMore.value) return
  logsPage.value += 1
  loadLogs(true)
}

async function handleStatusChange(user, event) {
  try {
    await updateAdminUserStatus(authStore.token, user.id, Number(event.target.value))
    success(`已更新 ${user.username} 的状态。`)
    await loadUsers()
  } catch (e) {
    error(e.message)
  }
}

async function handleRoleChange(user, event) {
  try {
    await updateAdminUserRole(authStore.token, user.id, Number(event.target.value))
    success(`已更新 ${user.username} 的角色。`)
    await loadUsers()
  } catch (e) {
    error(e.message)
  }
}

onMounted(async () => {
  // Fire the paginated logs fetch in parallel with the dashboard/user load so
  // the logs panel has its own data source (instead of capped recentLogs).
  const logsPromise = loadLogs(false)
  await loadData()
  await nextTick()
  setupObserver()
  // Don't block mount on logs; just swallow any stray rejection (loadLogs
  // already surfaces errors via toast).
  logsPromise.catch(() => {})
})

// Sections mount/unmount as `loading` flips, so rewire the observer whenever
// the rendered section set changes.
watch(loading, async () => {
  await nextTick()
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<template>
  <div class="admin-view page-animate">
    <header class="workspace-page-head">
      <h1 class="workspace-page-title">运营面板</h1>
    </header>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载管理员工作台...</p>
    </div>

    <template v-else-if="dashboard">
      <section class="workspace-metric-strip">
        <article v-for="card in kpiCards" :key="card.label" class="metric-card">
          <div class="metric-head">
            <span class="metric-label">{{ card.label }}</span>
            <component :is="card.icon" :size="16" class="metric-icon" />
          </div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-note">{{ card.hint }}</div>
        </article>
      </section>

      <div class="admin-shell">
        <aside class="admin-sidebar" aria-label="运营面板目录">
          <div class="admin-sidebar-inner">
            <nav
              v-for="group in navGroups"
              :key="group.title"
              class="admin-nav-group"
              :aria-label="group.title"
            >
              <div class="admin-nav-group-label">{{ group.title }}</div>
              <ul class="admin-nav-list">
                <li v-for="item in group.items" :key="item.id">
                  <a
                    :href="`#${item.id}`"
                    class="admin-nav-link"
                    :class="{ 'is-active': activeSection === item.id }"
                    @click.prevent="scrollToSection(item.id)"
                  >
                    <span class="admin-nav-link-label">{{ item.label }}</span>
                  </a>
                </li>
              </ul>
            </nav>
          </div>
        </aside>

        <div class="admin-main">
          <article id="section-diagnostics" class="admin-section panel">
            <header class="panel-head">
              <h2 class="panel-title">运营诊断</h2>
            </header>
            <div class="panel-body">
              <div class="insight-list">
                <div v-for="item in operationInsights" :key="item.title" class="insight-card" :class="item.level">
                  <div class="insight-head">
                    <strong>{{ item.title }}</strong>
                    <span class="pill">{{ item.summary }}</span>
                  </div>
                  <p>{{ item.detail }}</p>
                </div>
              </div>
            </div>
          </article>

          <article id="section-actions" class="admin-section panel">
            <header class="panel-head">
              <h2 class="panel-title">管理员动作</h2>
            </header>
            <div class="panel-body">
              <div class="feature-list">
                <button class="feature-card" @click="router.push('/crawler')">
                  <strong>数据采集监控</strong>
                </button>
                <button class="feature-card" @click="router.push('/openapi')">
                  <strong>开放平台治理</strong>
                </button>
                <button class="feature-card" @click="router.push('/reports')">
                  <strong>运营报告模板</strong>
                </button>
              </div>
            </div>
          </article>

          <article id="section-logs" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">最近系统日志</h2>
              <button
                class="btn-ghost"
                type="button"
                :disabled="logsLoading"
                @click="refreshLogs"
              >
                <RefreshCw :size="14" />
                <span>刷新</span>
              </button>
            </header>
            <div class="panel-body">
              <div v-if="logsLoading && !displayLogs.length" class="empty-state">
                <div class="loader-ring"></div>
                <p>正在加载日志…</p>
              </div>
              <div v-else-if="displayLogs.length" class="log-list">
                <div v-for="log in displayLogs" :key="log.id" class="log-item">
                  <div class="log-main">
                    <strong>{{ log.operation }}</strong>
                    <span>{{ log.username || '系统' }} / {{ log.ipAddress || log.ip || '未知 IP' }}</span>
                  </div>
                  <time>{{ new Date(log.createdAt).toLocaleString('zh-CN') }}</time>
                </div>
              </div>
              <div v-else class="empty-state">
                <FileText :size="24" />
                <p>暂无可展示的系统日志。</p>
              </div>
              <div v-if="logs.length && logsHasMore" class="log-loadmore">
                <button
                  class="btn-ghost"
                  type="button"
                  :disabled="logsLoading"
                  @click="loadMoreLogs"
                >
                  {{ logsLoading ? '加载中…' : '加载更多' }}
                </button>
              </div>
            </div>
          </article>

          <article id="section-risks" class="admin-section panel">
            <header class="panel-head">
              <h2 class="panel-title">平台风险提醒</h2>
            </header>
            <div class="panel-body">
              <div class="risk-list">
                <div class="risk-item">
                  <strong>用户基数过小</strong>
                  <p>指标避免只看绝对值，应关注角色渗透和转化链路。</p>
                </div>
                <div class="risk-item">
                  <strong>报告与推荐转化不足</strong>
                  <p>核心价值尚未稳定进入高频使用流程。</p>
                </div>
                <div class="risk-item">
                  <strong>教师供给侧尚未做实</strong>
                  <p>课程与供需分析尚未形成真实数据闭环。</p>
                </div>
              </div>
            </div>
          </article>

          <article id="section-users" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">用户与角色管理</h2>
              <button class="btn-ghost" type="button" @click="loadUsers">刷新列表</button>
            </header>
            <div class="panel-body">
              <div class="toolbar">
                <input v-model="userFilters.keyword" class="panel-input" placeholder="搜索用户名 / 昵称 / 邮箱" />
                <select v-model="userFilters.roleType" class="panel-input">
                  <option value="">全部角色</option>
                  <option :value="0">学生</option>
                  <option :value="1">管理员</option>
                  <option :value="2">教师</option>
                </select>
                <select v-model="userFilters.status" class="panel-input">
                  <option value="">全部状态</option>
                  <option :value="1">正常</option>
                  <option :value="0">禁用</option>
                </select>
                <GlowButton variant="primary" @click="loadUsers">筛选</GlowButton>
              </div>

              <div class="table-wrap">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>账号</th>
                      <th>角色</th>
                      <th>状态</th>
                      <th>最近登录</th>
                      <th>管理操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="user in users" :key="user.id">
                      <td>
                        <div class="user-cell">
                          <strong>{{ user.nickname || user.username }}</strong>
                          <span>{{ user.email || '未设置邮箱' }}</span>
                        </div>
                      </td>
                      <td><span class="role-pill">{{ getRoleLabel(user.roleType) }}</span></td>
                      <td>
                        <span :class="['status-pill', Number(user.status) === 1 ? 'ok' : 'off']">
                          {{ Number(user.status) === 1 ? '正常' : '禁用' }}
                        </span>
                      </td>
                      <td class="cell-muted">{{ user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '暂无记录' }}</td>
                      <td>
                        <div class="action-row">
                          <select class="inline-select" :value="user.roleType" @change="handleRoleChange(user, $event)">
                            <option :value="0">学生</option>
                            <option :value="1">管理员</option>
                            <option :value="2">教师</option>
                          </select>
                          <select class="inline-select" :value="user.status" @change="handleStatusChange(user, $event)">
                            <option :value="1">正常</option>
                            <option :value="0">禁用</option>
                          </select>
                        </div>
                      </td>
                    </tr>
                    <tr v-if="!users.length">
                      <td colspan="5" class="empty-row">暂无用户数据</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </article>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---------------- Metric strip ---------------- */
.workspace-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 134px;
  padding: 18px 20px;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.metric-icon {
  color: var(--c-accent-primary);
  opacity: 0.75;
}

.metric-value {
  margin-top: auto;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-note {
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

/* ---------------- Shell: sidebar + main column ---------------- */
.admin-shell {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

/* ---------------- Sidebar (cloned from OpenApiSidebar visuals) ---------------- */
.admin-sidebar {
  position: sticky;
  top: 0;
  align-self: start;
  /* Keep the sidebar's inner list scrollable if it ever overflows the viewport,
     without creating an outer scrollbar on the shell. */
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  border-right: 1px solid var(--c-border-glass);
  background: transparent;
  scrollbar-width: none;
}
.admin-sidebar::-webkit-scrollbar {
  display: none;
}

.admin-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.admin-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.admin-nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.admin-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px 7px 12px;
  border-radius: 6px;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 400;
  line-height: 1.4;
  text-decoration: none;
  transition: background-color 140ms ease, color 140ms ease;
  cursor: pointer;
}
.admin-nav-link-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.admin-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.admin-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

/* ---------------- Main column ---------------- */
.admin-main {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.admin-section {
  /* Leave a little room below sticky headers when anchors scroll into view. */
  scroll-margin-top: 16px;
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
  line-height: 1.25;
  color: var(--c-text-primary);
}

.panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------------- Insight list ---------------- */
.insight-list {
  display: grid;
  gap: 10px;
}

.insight-card {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.insight-card.good {
  border-color: rgba(30, 138, 91, 0.28);
}

.insight-card.warn {
  border-color: rgba(164, 94, 5, 0.28);
}

.insight-card.risk {
  border-color: rgba(178, 59, 46, 0.28);
}

.insight-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.insight-head strong {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.insight-card p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 600;
}

/* ---------------- Feature list ---------------- */
.feature-list {
  display: grid;
  gap: 10px;
}

.feature-card {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.feature-card:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.feature-card strong {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.feature-card:hover strong {
  color: var(--c-accent-primary);
}

/* ---------------- Log + risk ---------------- */
.log-list {
  display: grid;
  gap: 8px;
}

.log-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}

.log-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.log-main strong {
  color: var(--c-text-primary);
  font-size: 13.5px;
  font-weight: 600;
}

.log-main span {
  color: var(--c-text-muted);
  font-size: 12px;
}

.log-item time {
  color: var(--c-text-muted);
  font-family: var(--font-mono);
  font-size: 11.5px;
  flex-shrink: 0;
}

.risk-list {
  display: grid;
  gap: 10px;
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
  font-size: 13px;
  line-height: 1.55;
}

/* ---------------- Empty ---------------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  color: var(--c-text-muted);
  font-size: 13px;
}

.empty-state :deep(svg) {
  color: var(--c-text-faint);
}

/* ---------------- Toolbar ---------------- */
.toolbar {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 10px;
  margin-bottom: 4px;
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
  transition:
    border-color var(--duration-fast),
    box-shadow var(--duration-fast);
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
  gap: 6px;
  padding: 7px 12px;
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

.btn-ghost:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.log-loadmore {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

/* ---------------- Table (aligned with .console-table) ---------------- */
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

.user-cell {
  display: grid;
  gap: 2px;
}

.user-cell strong {
  color: var(--c-text-primary);
  font-size: 13.5px;
  font-weight: 600;
}

.user-cell span {
  color: var(--c-text-muted);
  font-size: 12px;
}

.cell-muted {
  color: var(--c-text-muted);
  font-size: 12.5px;
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

.role-pill {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.status-pill.ok {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.status-pill.off {
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
}

.action-row {
  display: flex;
  gap: 8px;
}

.inline-select {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 12.5px;
  min-width: 100px;
}

.empty-row {
  text-align: center;
  padding: 32px 0;
  color: var(--c-text-muted);
}

/* ---------------- Loading ---------------- */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.loader-ring {
  width: 28px;
  height: 28px;
  border: 2px solid var(--c-accent-primary-glow);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---------------- Responsive ---------------- */
@media (max-width: 1120px) {
  .workspace-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

/* Sidebar collapses into a horizontal chip strip at the top of the main
   column. The shell flattens to a single column so the chip strip sits
   directly above the content. */
@media (max-width: 900px) {
  .admin-shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .admin-sidebar {
    position: sticky;
    top: 0;
    z-index: 5;
    max-height: none;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: none;
    border-bottom: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated);
  }

  .admin-sidebar-inner {
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 18px;
    padding: 10px 12px;
    min-width: max-content;
  }

  .admin-nav-group {
    flex-direction: row;
    align-items: center;
    gap: 6px;
  }

  .admin-nav-group-label {
    padding: 0 4px 0 0;
    white-space: nowrap;
    font-size: 10px;
  }

  .admin-nav-list {
    flex-direction: row;
    gap: 6px;
  }

  .admin-nav-link {
    padding: 6px 12px;
    border-radius: 999px;
    border: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated);
    white-space: nowrap;
  }

  /* On mobile/pill layout swap the left-bar for a bottom-bar indicator so the
     indicator reads naturally along the horizontal axis. */
  .admin-nav-link.is-active {
    border-color: var(--c-accent-primary);
  }
  .admin-nav-link.is-active::before {
    left: 10px;
    right: 10px;
    top: auto;
    bottom: 2px;
    width: auto;
    height: 2px;
  }
}

@media (max-width: 640px) {
  .workspace-metric-strip {
    grid-template-columns: 1fr;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
