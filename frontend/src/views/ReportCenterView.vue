<script setup>
import { computed, onMounted, ref } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createReport,
  createReportSchedule,
  deleteReportSchedule,
  exportReportPdf,
  fetchPublicReports,
  fetchReportDrill,
  fetchReports,
  fetchReportSchedules,
  fetchReportStatus,
  normalizeError,
  toggleReportSchedule
} from '../api'
import { useAuthStore } from '../store/auth'
import { BarChart3, CalendarClock, FileText, Globe, LockKeyhole, RefreshCw, ShieldCheck } from 'lucide-vue-next'

const authStore = useAuthStore()

const publicReports = ref([])
const privateReports = ref([])
const schedules = ref([])
const selectedReport = ref(null)
const selectedTask = ref(null)
const loading = ref(true)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')
const reportTypeLabels = {
  COMPREHENSIVE: '综合分析',
  SALARY: '薪资分析',
  SKILL: '技能分析',
  TREND: '趋势分析'
}
const frequencyLabels = {
  WEEKLY: '每周',
  DAILY: '每天',
  MONTHLY: '每月'
}
const statusLabels = {
  READY: '已生成',
  SUCCESS: '成功',
  FAILED: '失败',
  PENDING: '排队中',
  RUNNING: '生成中'
}

const generateForm = ref({
  reportName: '职业情报分析报告',
  reportType: 'COMPREHENSIVE'
})

const scheduleForm = ref({
  scheduleName: '每周情报简报',
  reportType: 'COMPREHENSIVE',
  frequency: 'WEEKLY',
  weekday: 'MON',
  monthDay: '1',
  hour: '09',
  minute: '00'
})

const weekdayOptions = [
  { label: '周一', value: 'MON' },
  { label: '周二', value: 'TUE' },
  { label: '周三', value: 'WED' },
  { label: '周四', value: 'THU' },
  { label: '周五', value: 'FRI' },
  { label: '周六', value: 'SAT' },
  { label: '周日', value: 'SUN' }
]

const hourOptions = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const minuteOptions = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0'))
const monthDayOptions = Array.from({ length: 31 }, (_, i) => String(i + 1))
const weekdayLabelMap = Object.fromEntries(weekdayOptions.map((item) => [item.value, item.label]))

const scheduleCronPreview = computed(() => {
  const minute = scheduleForm.value.minute
  const hour = scheduleForm.value.hour

  if (scheduleForm.value.frequency === 'DAILY') {
    return `0 ${minute} ${hour} * * *`
  }

  if (scheduleForm.value.frequency === 'MONTHLY') {
    return `0 ${minute} ${hour} ${scheduleForm.value.monthDay} * *`
  }

  return `0 ${minute} ${hour} * * ${scheduleForm.value.weekday}`
})

const canManageReports = computed(() => authStore.isLoggedIn)
const publicReportCount = computed(() => publicReports.value.length)
const privateReportCount = computed(() => privateReports.value.length)
const scheduleCount = computed(() => schedules.value.length)
const activeScheduleCount = computed(() => schedules.value.filter((item) => item.enabled).length)

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) {
    return '暂无最近任务'
  }

  const taskName = selectedTask.value.reportName || selectedTask.value.scheduleName || selectedTask.value.taskId || selectedTask.value.id || '最近任务'
  return `${getStatusLabel(selectedTask.value.status)} · ${taskName}`
})

const selectedReportTitle = computed(() => selectedReport.value?.reportName || (selectedReport.value?.id ? `报告 #${selectedReport.value.id}` : '报告详情'))
const selectedReportSummary = computed(() => selectedReport.value?.summary || '暂无报告摘要。')
const schedulePreviewText = computed(() => {
  const time = `${scheduleForm.value.hour}:${scheduleForm.value.minute}`

  if (scheduleForm.value.frequency === 'DAILY') {
    return `每天 ${time} 自动生成`
  }

  if (scheduleForm.value.frequency === 'MONTHLY') {
    return `每月 ${scheduleForm.value.monthDay} 日 ${time} 自动生成`
  }

  return `每周 ${weekdayLabelMap[scheduleForm.value.weekday] || scheduleForm.value.weekday} ${time} 自动生成`
})
const selectedReportMeta = computed(() => {
  const report = selectedReport.value
  if (!report) {
    return []
  }

  return [
    { label: '报告类型', value: getReportTypeLabel(report.reportType) },
    { label: '当前状态', value: getStatusLabel(report.status || 'READY') },
    { label: '可见范围', value: getReportVisibilityLabel(report) },
    { label: '更新时间', value: report.updatedAt || report.createdAt || '待补充' }
  ]
})
const selectedReportSections = computed(() => {
  const report = selectedReport.value
  if (!report) {
    return []
  }

  const sections = [
    {
      title: '摘要结论',
      content: firstText(report.summary, report.overview, report.introduction)
    },
    {
      title: '图表洞察',
      content: firstText(report.chartInsights, report.chart_insights, report.chartInsight)
    },
    {
      title: '建议动作',
      content: firstText(report.recommendations, report.recommendation, report.suggestions)
    },
    {
      title: '补充说明',
      content: firstText(report.detail, report.analysis, report.notes)
    }
  ].filter((item) => item.content)

  return sections.length
    ? sections
    : [{
        title: '报告内容',
        content: '当前接口仅返回基础结构，详细分析内容会在报告生成后补充。'
      }]
})

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function getReportTypeLabel(type) {
  return reportTypeLabels[type] || type || '未知类型'
}

function getStatusLabel(status) {
  return statusLabels[status] || status || '未知状态'
}

function getScheduleStateLabel(item) {
  return item.enabled ? '运行中' : '已停用'
}

function getReportVisibilityLabel(report) {
  if (report?.visibility === 'PUBLIC' || report?.public === true || report?.isPublic === true) {
    return '公开'
  }

  return '私有'
}

function formatScheduleSummary(item) {
  const time = [item.hour, item.minute].every((part) => part !== undefined && part !== null)
    ? `${String(item.hour).padStart(2, '0')}:${String(item.minute).padStart(2, '0')}`
    : `${scheduleForm.value.hour}:${scheduleForm.value.minute}`

  if (item.frequency === 'DAILY') {
    return `每天 ${time}`
  }

  if (item.frequency === 'MONTHLY') {
    return `每月 ${item.monthDay || '1'} 日 ${time}`
  }

  return `每周 ${weekdayLabelMap[item.weekday] || item.weekday || '固定时段'} ${time}`
}

async function loadPage() {
  loading.value = true
  error.value = ''

  try {
    const publicResult = await fetchPublicReports({ page: 1, pageSize: 6 })
    publicReports.value = publicResult.data || []

    if (!canManageReports.value) {
      privateReports.value = []
      schedules.value = []
      selectedReport.value = null
      selectedTask.value = null
      return
    }

    const [reportsResult, schedulesResult] = await Promise.all([
      fetchReports(authStore.token, { page: 1, pageSize: 10 }),
      fetchReportSchedules(authStore.token)
    ])
    privateReports.value = reportsResult.data || []
    schedules.value = schedulesResult || []
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function pollTask(taskId) {
  let attempts = 0
  while (attempts < 10) {
    attempts += 1
    selectedTask.value = await fetchReportStatus(authStore.token, taskId)
    if (['SUCCESS', 'FAILED'].includes(selectedTask.value?.status)) {
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 1200))
  }
}

async function handleCreateReport() {
  actionLoading.value = true
  error.value = ''
  success.value = ''

  try {
    const result = await createReport(authStore.token, {
      reportName: generateForm.value.reportName.trim(),
      reportType: generateForm.value.reportType,
      params: {}
    })

    if (result.taskId) {
      success.value = `报告任务已提交：${result.taskId}`
      await pollTask(result.taskId)
      await loadPage()
    } else {
      success.value = '报告生成请求已提交。'
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function handleCreateSchedule() {
  actionLoading.value = true
  error.value = ''
  success.value = ''

  try {
    await createReportSchedule(authStore.token, {
      scheduleName: scheduleForm.value.scheduleName.trim(),
      reportType: scheduleForm.value.reportType,
      cronExpr: scheduleCronPreview.value,
      params: {}
    })
    success.value = '调度任务已创建。'
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function openReportDetail(report) {
  error.value = ''
  try {
    selectedReport.value = await fetchReportDrill(authStore.token, report.id)
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handleExport(report) {
  error.value = ''
  success.value = ''
  try {
    await exportReportPdf(authStore.token, report.id, `${report.reportName || `report-${report.id}`}.pdf`)
    success.value = 'PDF 导出已开始。'
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handleToggleSchedule(id) {
  error.value = ''
  success.value = ''
  try {
    await toggleReportSchedule(authStore.token, id)
    success.value = '调度状态已更新。'
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handleDeleteSchedule(id) {
  error.value = ''
  success.value = ''
  try {
    await deleteReportSchedule(authStore.token, id)
    success.value = '调度任务已删除。'
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  }
}

onMounted(loadPage)
</script>

<template>
  <div class="report-page page-shell">
    <section class="workspace-hero surface report-hero">
      <div class="hero-copy">
        <h1>报告中心</h1>
        <p>公开报告、我的报告和调度都收在这里。</p>
        <div class="hero-actions">
          <GlowButton variant="ghost" @click="loadPage">
            <RefreshCw :size="14" />
            刷新数据
          </GlowButton>
          <GlowButton
            v-if="canManageReports"
            variant="primary"
            :loading="actionLoading"
            @click="handleCreateReport"
          >
            <FileText :size="14" />
            立即生成
          </GlowButton>
          <div class="hero-note">
            <ShieldCheck :size="14" />
            <span>{{ canManageReports ? '已登录，可管理报告与调度' : '登录后可生成私有报告与定时任务' }}</span>
          </div>
        </div>
      </div>

      <div class="hero-aside">
        <div class="metric-grid compact">
          <div class="metric-tile">
            <span>公开报告</span>
            <strong>{{ publicReportCount }}</strong>
          </div>
          <div class="metric-tile">
            <span>运行中调度</span>
            <strong>{{ activeScheduleCount }}</strong>
          </div>
          <div class="metric-tile">
            <span>我的报告</span>
            <strong>{{ privateReportCount }}</strong>
          </div>
        </div>
        <div class="status-strip">
          <BarChart3 :size="16" />
          <div>
            <strong>{{ latestTaskSummary }}</strong>
            <p>{{ canManageReports ? '这里会显示最近任务状态。' : '登录后可查看任务与调度状态。' }}</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="success" class="status-banner success-banner">{{ success }}</div>

    <section class="report-layout">
      <div class="report-main-stack">
        <article class="surface section-panel report-library-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title"><Globe :size="15" /> 报告库</h2>
              <p>公开报告与我的报告。</p>
            </div>
          </div>

          <div class="report-columns">
            <section class="library-section public-section">
              <div class="section-subhead">
                <span>公开报告</span>
                <strong>{{ publicReportCount }}</strong>
              </div>

              <div class="row-list">
                <div v-for="report in publicReports" :key="report.id" class="report-row light">
                  <div class="row-main">
                    <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                    <p>{{ getReportTypeLabel(report.reportType) }}</p>
                  </div>
                  <span class="pill subtle">
                    <Globe :size="14" />
                    公开
                  </span>
                </div>
                <div v-if="!publicReports.length && !loading" class="empty-state">暂无公开报告。</div>
              </div>
            </section>

            <section class="library-section private-section">
              <div class="section-subhead">
                <span>我的报告</span>
                <strong>{{ privateReportCount }}</strong>
              </div>

              <div v-if="!canManageReports" class="empty-state large">请先登录后再创建和查看私有报告。</div>
              <div v-else class="private-stack">
                <div class="report-create-row">
                  <input v-model="generateForm.reportName" class="glass-input" placeholder="报告名称" />
                  <select v-model="generateForm.reportType" class="glass-input">
                    <option value="COMPREHENSIVE">综合分析</option>
                    <option value="SALARY">薪资分析</option>
                    <option value="SKILL">技能分析</option>
                    <option value="TREND">趋势分析</option>
                  </select>
                  <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">生成报告</GlowButton>
                </div>

                <div class="row-list">
                  <div
                    v-for="report in privateReports"
                    :key="report.id"
                    class="report-row actionable"
                    @click="openReportDetail(report)"
                  >
                    <div class="row-main">
                      <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                      <p>{{ getReportTypeLabel(report.reportType) }}</p>
                    </div>
                    <div class="row-side">
                      <span class="pill">{{ getStatusLabel(report.status || 'READY') }}</span>
                      <GlowButton variant="ghost" @click.stop="handleExport(report)">
                        <FileText :size="14" />
                        导出
                      </GlowButton>
                    </div>
                  </div>

                  <div v-if="!privateReports.length && !loading" class="empty-state">暂无私有报告。</div>
                </div>
              </div>
            </section>
          </div>
        </article>

        <article class="surface section-panel schedule-panel">
          <div class="panel-head">
            <div>
              <h2 class="panel-title"><CalendarClock :size="15" /> 调度中心</h2>
              <p>配置定时生成。</p>
            </div>
          </div>

          <div v-if="canManageReports" class="panel-stack">
            <div class="schedule-form-grid">
              <input v-model="scheduleForm.scheduleName" class="glass-input" placeholder="调度名称" />
              <select v-model="scheduleForm.reportType" class="glass-input">
                <option value="COMPREHENSIVE">综合分析</option>
                <option value="SALARY">薪资分析</option>
                <option value="SKILL">技能分析</option>
                <option value="TREND">趋势分析</option>
              </select>
              <select v-model="scheduleForm.frequency" class="glass-input">
                <option value="WEEKLY">每周</option>
                <option value="DAILY">每天</option>
                <option value="MONTHLY">每月</option>
              </select>
              <select v-if="scheduleForm.frequency === 'WEEKLY'" v-model="scheduleForm.weekday" class="glass-input">
                <option v-for="item in weekdayOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <select v-if="scheduleForm.frequency === 'MONTHLY'" v-model="scheduleForm.monthDay" class="glass-input">
                <option v-for="item in monthDayOptions" :key="item" :value="item">{{ item }}</option>
              </select>
              <div class="split-grid">
                <select v-model="scheduleForm.hour" class="glass-input">
                  <option v-for="item in hourOptions" :key="item" :value="item">{{ item }}</option>
                </select>
                <select v-model="scheduleForm.minute" class="glass-input">
                  <option v-for="item in minuteOptions" :key="item" :value="item">{{ item }}</option>
                </select>
              </div>
            </div>

            <div class="schedule-summary">
              <div>
                <span class="meta-label">执行预览</span>
                <strong>{{ schedulePreviewText }}</strong>
              </div>
              <code>{{ scheduleCronPreview }}</code>
            </div>

            <GlowButton variant="secondary" :loading="actionLoading" @click="handleCreateSchedule">
              <CalendarClock :size="14" />
              保存调度
            </GlowButton>

            <div class="row-list">
              <div v-for="item in schedules" :key="item.id" class="schedule-row">
                <div class="row-main">
                  <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
                  <p>{{ frequencyLabels[item.frequency] || item.frequency || '周期任务' }}</p>
                  <small>{{ item.cronExpr || formatScheduleSummary(item) }}</small>
                </div>
                <div class="row-side">
                  <span class="pill" :class="{ active: item.enabled }">{{ getScheduleStateLabel(item) }}</span>
                  <GlowButton variant="ghost" @click="handleToggleSchedule(item.id)">
                    {{ item.enabled ? '停用' : '启用' }}
                  </GlowButton>
                  <GlowButton variant="ghost" @click="handleDeleteSchedule(item.id)">删除</GlowButton>
                </div>
              </div>

              <div v-if="!schedules.length && !loading" class="empty-state">暂无调度任务。</div>
            </div>
          </div>

          <div v-else class="empty-state large">请先登录后再配置调度任务。</div>
        </article>
      </div>

      <aside class="surface section-panel detail-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><LockKeyhole :size="13" /> 报告详情</span>
            <h2>{{ selectedReportTitle }}</h2>
            <p>{{ selectedReport ? '当前报告内容。' : '从左侧选择一个报告。' }}</p>
          </div>
        </div>

        <div v-if="selectedReport" class="report-detail">
          <div class="detail-summary-card">
            <span class="detail-kicker">当前查看</span>
            <h3>{{ selectedReportTitle }}</h3>
            <p>{{ selectedReportSummary }}</p>
          </div>

          <div class="detail-meta-grid">
            <div v-for="item in selectedReportMeta" :key="item.label" class="detail-meta-card">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <div class="detail-section-list">
            <section v-for="item in selectedReportSections" :key="item.title" class="detail-section">
              <h3>{{ item.title }}</h3>
              <p>{{ item.content }}</p>
            </section>
          </div>

          <GlowButton variant="ghost" @click="handleExport(selectedReport)">
            <FileText :size="14" />
            导出当前报告
          </GlowButton>
        </div>

        <div v-else class="empty-state large">选择左侧报告后，这里显示详情。</div>
      </aside>
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
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 20px;
  padding: 22px;
  border-radius: 18px;
}

.hero-copy,
.hero-aside,
.section-panel,
.panel-stack,
.report-detail,
.report-main-stack,
.library-section,
.private-stack {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 10px;
}

.hero-copy h1,
.panel-title,
.panel-head h2,
.detail-summary-card h3,
.detail-section h3 {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(22px, 1.95vw, 27px);
  line-height: 1.12;
  letter-spacing: -0.05em;
}

.panel-title,
.panel-head h2 {
  font-size: clamp(18px, 1.35vw, 21px);
  line-height: 1.18;
  letter-spacing: -0.03em;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-accent-primary);
}

.panel-title :deep(svg) {
  color: inherit;
  flex: none;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.empty-state,
.row-main p,
.row-main small,
.detail-summary-card p,
.detail-section p,
.meta-label,
.detail-meta-card span {
  margin: 0;
  font-size: 0.93rem;
  line-height: 1.55;
  color: var(--c-text-secondary);
}

.hero-actions,
.hero-note,
.status-strip,
.row-side,
.pill,
.split-grid {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.hero-actions {
  flex-wrap: wrap;
}

.hero-note,
.status-strip,
.metric-tile,
.empty-state,
.detail-summary-card,
.detail-meta-card,
.detail-section,
.schedule-summary {
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 14px;
}

.hero-note {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.54);
}

.hero-aside,
.report-main-stack {
  gap: 16px;
}

.metric-grid {
  display: grid;
  gap: 10px;
}

.metric-grid.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
  background: rgba(255, 255, 255, 0.52);
}

.metric-tile span {
  color: var(--c-text-secondary);
  font-size: 12px;
}

.metric-tile strong {
  font-size: 20px;
  line-height: 1.1;
  letter-spacing: -0.04em;
}

.status-strip {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.56);
}

.status-strip strong {
  display: block;
  margin-bottom: 4px;
}

.report-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 20px;
  align-items: start;
}

.section-panel {
  gap: 18px;
  min-width: 0;
  padding: 20px;
  border-radius: 16px;
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

.report-columns {
  display: grid;
  grid-template-columns: minmax(0, 0.72fr) minmax(0, 1.28fr);
  gap: 20px;
}

.library-section {
  gap: 14px;
}

.section-subhead {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.section-subhead span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.section-subhead strong {
  color: var(--c-text-primary);
  font-size: 22px;
  letter-spacing: -0.04em;
}

.private-stack {
  gap: 16px;
}

.report-create-row,
.schedule-form-grid {
  display: grid;
  gap: 12px;
}

.report-create-row {
  grid-template-columns: minmax(0, 1.3fr) minmax(180px, 0.8fr) auto;
  align-items: center;
}

.schedule-form-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(193, 198, 215, 0.62);
  color: var(--c-text-primary);
}

.split-grid {
  width: 100%;
}

.split-grid > * {
  flex: 1;
}

.row-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid rgba(193, 198, 215, 0.48);
}

.report-row,
.schedule-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid rgba(193, 198, 215, 0.48);
}

.report-row.light {
  align-items: center;
}

.report-row.actionable {
  cursor: pointer;
}

.report-row.actionable:hover .row-main strong {
  color: var(--c-accent-primary);
}

.row-main {
  min-width: 0;
}

.row-main strong {
  display: block;
  margin-bottom: 4px;
  color: var(--c-text-primary);
}

.row-main small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
}

.row-side {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pill {
  padding: 6px 11px;
  border-radius: 999px;
  white-space: nowrap;
  background: rgba(242, 244, 250, 0.96);
  color: var(--c-text-secondary);
}

.pill.subtle {
  background: rgba(247, 249, 252, 0.94);
}

.pill.active {
  background: rgba(0, 89, 199, 0.08);
  color: var(--c-accent-primary);
}

.schedule-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.54);
}

.schedule-summary strong {
  display: block;
  margin-top: 4px;
  color: var(--c-text-primary);
}

.schedule-summary code {
  color: #4f637c;
  white-space: nowrap;
}

.empty-state {
  padding: 16px;
  background: rgba(255, 255, 255, 0.36);
}

.empty-state.large {
  min-height: 132px;
  display: flex;
  align-items: center;
}

.detail-panel {
  position: sticky;
  top: 18px;
}

.report-detail {
  gap: 14px;
}

.detail-summary-card {
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(0, 89, 199, 0.045), transparent 48%),
    rgba(255, 255, 255, 0.56);
}

.detail-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  color: #5f7391;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.detail-summary-card h3 {
  font-size: 24px;
  line-height: 1.14;
  letter-spacing: -0.04em;
}

.detail-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail-meta-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
  background: rgba(255, 255, 255, 0.52);
}

.detail-meta-card strong {
  font-size: 15px;
  color: var(--c-text-primary);
}

.detail-section-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.48);
}

.detail-section h3 {
  font-size: 15px;
  color: var(--c-text-primary);
}

.detail-section p {
  line-height: 1.7;
}

.status-banner {
  padding: 14px 16px;
  border-radius: 16px;
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
}

.success-banner {
  color: #166534;
  background: rgba(220, 252, 231, 0.84);
}

@media (max-width: 1180px) {
  .workspace-hero,
  .report-layout,
  .report-columns {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    position: static;
  }
}

@media (max-width: 860px) {
  .metric-grid.compact,
  .detail-meta-grid,
  .schedule-form-grid {
    grid-template-columns: 1fr 1fr;
  }

  .report-create-row {
    grid-template-columns: 1fr;
  }

  .schedule-summary {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .section-panel {
    padding: 18px;
    border-radius: 16px;
  }

  .metric-grid.compact,
  .detail-meta-grid,
  .schedule-form-grid {
    grid-template-columns: 1fr;
  }

  .report-row,
  .schedule-row {
    flex-direction: column;
  }

  .row-side {
    justify-content: flex-start;
  }

  .split-grid {
    flex-direction: column;
  }
}
</style>
