<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminDashboard,
  fetchAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus
} from '../api'
import { getRoleLabel } from '../utils/role'
import {
  Activity,
  AlertTriangle,
  Briefcase,
  DatabaseZap,
  FileText,
  ShieldAlert,
  TrendingUp,
  Users,
  Webhook
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

const kpiCards = computed(() => {
  if (!dashboard.value) return []
  return [
    { label: '总用户数', value: dashboard.value.totalUsers, hint: `今日新增 ${dashboard.value.newUsersToday || 0}`, icon: Users },
    { label: '岗位总量', value: dashboard.value.totalJobs, hint: `近 7 天新增 ${dashboard.value.newJobs7d || 0}`, icon: Briefcase },
    { label: '报告总量', value: dashboard.value.totalReports, hint: '平台分析产出累计值', icon: FileText },
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

const adminActions = computed(() => [
  '把运营报告模板做成管理员默认入口，强化平台数据质量、活跃度和采集健康度解读。',
  '继续把数据采集监控页接成真实任务看板，而不是停留在静态说明层。',
  '优先提升教师角色渗透率，因为教师上传课程后才能真正拉动供需分析和报告深度。'
])

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

onMounted(loadData)
</script>

<template>
  <div class="admin-view page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">管理员视角</span>
        <h1 class="page-intro-title">平台运营与权限治理中心</h1>
        <p class="page-intro-text">
          这里不只展示指标，还会把运营、内容沉淀、教师渗透和风险治理转成可执行判断，帮助管理员理解平台现在真正缺什么。
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">已封禁账号</span>
          <span class="intro-metric-value">{{ dashboard?.bannedCount || 0 }}</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">最近日志</span>
          <span class="intro-metric-value">{{ dashboard?.recentLogs?.length || 0 }}</span>
        </div>
      </div>
    </section>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载管理员工作台...</p>
    </div>

    <template v-else-if="dashboard">
      <section class="kpi-grid">
        <PremiumCard v-for="card in kpiCards" :key="card.label" class="kpi-card" glowColor="primary">
          <div class="kpi-head">
            <component :is="card.icon" :size="20" />
            <span>{{ card.label }}</span>
          </div>
          <strong class="kpi-value">{{ card.value }}</strong>
          <span class="kpi-hint">{{ card.hint }}</span>
        </PremiumCard>
      </section>

      <section class="admin-grid">
        <PremiumCard title="运营诊断" glowColor="purple">
          <div class="insight-list">
            <div v-for="item in operationInsights" :key="item.title" class="insight-card" :class="item.level">
              <div class="insight-head">
                <strong>{{ item.title }}</strong>
                <span class="pill">{{ item.summary }}</span>
              </div>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="管理员动作建议" glowColor="teal">
          <div class="feature-list">
            <button class="feature-card" @click="router.push('/crawler')">
              <DatabaseZap :size="20" />
              <div>
                <strong>数据采集监控</strong>
                <p>下一阶段重点不只是看状态，还要接成功率、失败原因和任务耗时。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/openapi')">
              <Webhook :size="20" />
              <div>
                <strong>开放平台治理</strong>
                <p>开放平台的真正短板是调用日志和凭证管理，而不是页面入口本身。</p>
              </div>
            </button>
            <button class="feature-card" @click="router.push('/reports')">
              <ShieldAlert :size="20" />
              <div>
                <strong>运营报告模板</strong>
                <p>建议把管理员报告做成默认入口，直接对接质量、活跃和采集健康度。</p>
              </div>
            </button>
          </div>

          <div class="action-note">
            <div v-for="item in adminActions" :key="item" class="note-item">
              <TrendingUp :size="16" />
              <span>{{ item }}</span>
            </div>
          </div>
        </PremiumCard>
      </section>

      <section class="admin-grid">
        <PremiumCard title="最近系统日志" glowColor="secondary">
          <div v-if="dashboard.recentLogs?.length" class="log-list">
            <div v-for="log in dashboard.recentLogs" :key="log.id" class="log-item">
              <div class="log-main">
                <strong>{{ log.operation }}</strong>
                <span>{{ log.username || '系统' }} / {{ log.ip || '未知 IP' }}</span>
              </div>
              <time>{{ new Date(log.createdAt).toLocaleString('zh-CN') }}</time>
            </div>
          </div>
          <div v-else class="empty-state">
            <FileText :size="26" />
            <p>暂无可展示的系统日志。</p>
          </div>
        </PremiumCard>

        <PremiumCard title="平台风险提醒" glowColor="secondary">
          <div class="risk-list">
            <div class="risk-item">
              <AlertTriangle :size="18" />
              <div>
                <strong>用户基数过小</strong>
                <p>当前用户总量偏小，很多指标要避免只看绝对值，更应该看角色渗透和转化链路。</p>
              </div>
            </div>
            <div class="risk-item">
              <AlertTriangle :size="18" />
              <div>
                <strong>报告与推荐转化不足</strong>
                <p>报告量和活跃量仍偏低，说明核心价值还没有稳定进入高频使用流程。</p>
              </div>
            </div>
            <div class="risk-item">
              <AlertTriangle :size="18" />
              <div>
                <strong>教师供给侧尚未做实</strong>
                <p>教师入口已经具备，但课程和供需分析要形成真实数据闭环才会放大平台价值。</p>
              </div>
            </div>
          </div>
        </PremiumCard>
      </section>

      <section>
        <div class="section-heading">
          <div>
            <h2>用户与角色管理</h2>
            <p>这里保留管理员最直接的操作能力，但上面的诊断区会告诉你哪些角色最值得优先运营。</p>
          </div>
          <GlowButton variant="ghost" @click="loadUsers">刷新列表</GlowButton>
        </div>
      </section>

      <PremiumCard glowColor="secondary">
        <div class="toolbar">
          <input v-model="userFilters.keyword" class="glass-input" placeholder="搜索用户名 / 昵称 / 邮箱" />
          <select v-model="userFilters.roleType" class="glass-input">
            <option value="">全部角色</option>
            <option :value="0">学生</option>
            <option :value="1">管理员</option>
            <option :value="2">教师</option>
          </select>
          <select v-model="userFilters.status" class="glass-input">
            <option value="">全部状态</option>
            <option :value="1">正常</option>
            <option :value="0">禁用</option>
          </select>
          <GlowButton variant="primary" @click="loadUsers">筛选</GlowButton>
        </div>

        <div class="user-table-wrap">
          <table class="user-table">
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
                <td>{{ user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString('zh-CN') : '暂无记录' }}</td>
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
      </PremiumCard>
    </template>
  </div>
</template>

<style scoped>
.kpi-grid,
.admin-grid,
.insight-list,
.feature-list,
.log-list,
.risk-list,
.action-note {
  display: grid;
  gap: 24px;
}

.kpi-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.admin-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.kpi-card,
.insight-card,
.feature-card,
.log-item,
.risk-item,
.note-item {
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.kpi-card {
  display: grid;
  gap: 14px;
}

.kpi-head {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--c-text-secondary);
}

.kpi-value {
  font-size: 34px;
  line-height: 1;
}

.kpi-hint {
  color: var(--c-text-muted);
  font-size: 13px;
}

.insight-card {
  padding: 18px;
}

.insight-card.good {
  border-color: rgba(16, 185, 129, 0.22);
}

.insight-card.warn {
  border-color: rgba(245, 158, 11, 0.22);
}

.insight-card.risk {
  border-color: rgba(239, 68, 68, 0.22);
}

.insight-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.insight-head strong,
.log-main strong,
.risk-item strong,
.feature-card strong {
  color: var(--c-text-primary);
}

.insight-card p,
.feature-card p,
.log-main span,
.log-item time,
.risk-item p,
.note-item span {
  color: var(--c-text-secondary);
}

.pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.feature-card,
.risk-item,
.note-item {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 14px;
  padding: 18px;
  color: inherit;
  text-align: left;
}

.log-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
}

.log-main {
  display: grid;
  gap: 4px;
}

.toolbar {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 12px;
  margin-bottom: 18px;
}

.glass-input,
.inline-select {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

.user-table-wrap {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
}

.user-table th,
.user-table td {
  text-align: left;
  padding: 14px 12px;
  border-bottom: 1px solid var(--c-border-glass);
  vertical-align: middle;
}

.user-table th {
  color: var(--c-text-muted);
  font-size: 13px;
  font-weight: 600;
}

.user-cell {
  display: grid;
  gap: 4px;
}

.user-cell strong {
  color: var(--c-text-primary);
}

.user-cell span,
.empty-row {
  color: var(--c-text-muted);
}

.role-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.role-pill {
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
}

.status-pill.ok {
  background: rgba(20, 184, 166, 0.12);
  color: var(--c-accent-teal);
}

.status-pill.off {
  background: rgba(244, 63, 94, 0.12);
  color: #fb7185;
}

.action-row {
  display: flex;
  gap: 10px;
}

.inline-select {
  min-width: 110px;
  padding: 8px 10px;
}

.empty-row {
  text-align: center;
  padding: 32px 0;
}

@media (max-width: 1120px) {
  .kpi-grid,
  .admin-grid,
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
