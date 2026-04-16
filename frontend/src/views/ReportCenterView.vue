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
const selectedReportSummary = computed(() => selectedReport.value?.summary || '当前报告未返回摘要。')

function getReportTypeLabel(type) {
  return reportTypeLabels[type] || type || '未知类型'
}

function getStatusLabel(status) {
  return statusLabels[status] || status || '未知状态'
}

function getScheduleStateLabel(item) {
  return item.enabled ? '运行中' : '已停用'
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
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <span class="eyebrow">报告中心</span>
        <h1>统一管理公开报告、私有报告和定时调度</h1>
        <p>
          把报告浏览、任务生成、调度配置和详情钻取收拢到同一处，减少跳转和重复容器，适合中屏和移动端顺着看。
        </p>
        <div class="hero-actions">
          <GlowButton variant="ghost" @click="loadPage">
            <RefreshCw :size="14" />
            刷新数据
          </GlowButton>
          <div class="hero-note">
            <ShieldCheck :size="14" />
            <span>{{ canManageReports ? '已登录，可管理私有报告' : '登录后可生成私有报告和调度任务' }}</span>
          </div>
        </div>
      </div>

      <div class="hero-aside">
        <div class="metric-grid">
          <div class="metric-tile">
            <span>公开报告</span>
            <strong>{{ publicReportCount }}</strong>
          </div>
          <div class="metric-tile">
            <span>私有报告</span>
            <strong>{{ privateReportCount }}</strong>
          </div>
          <div class="metric-tile">
            <span>调度任务</span>
            <strong>{{ scheduleCount }}</strong>
          </div>
          <div class="metric-tile">
            <span>运行中调度</span>
            <strong>{{ activeScheduleCount }}</strong>
          </div>
        </div>
        <div class="status-strip">
          <BarChart3 :size="16" />
          <div>
            <strong>{{ latestTaskSummary }}</strong>
            <p>最近一次报告任务状态会显示在这里，便于快速判断是否需要重试。</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="success" class="status-banner success-banner">{{ success }}</div>

    <section class="workspace-grid">
      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><Globe :size="13" /> 公开资料</span>
            <h2>公开报告库</h2>
            <p>浏览平台可见的报告样本，作为后续生成和调度的参考。</p>
          </div>
        </div>

        <div class="card-list">
          <div v-for="report in publicReports" :key="report.id" class="list-item">
            <div class="item-copy">
              <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
              <p>{{ getReportTypeLabel(report.reportType) }}</p>
            </div>
            <span class="pill">
              <Globe :size="14" />
              公开
            </span>
          </div>
          <div v-if="!publicReports.length && !loading" class="empty-state">暂无公开报告。</div>
        </div>
      </article>

      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><LockKeyhole :size="13" /> 私有工作区</span>
            <h2>我的报告</h2>
            <p>生成新的分析报告，并在同一区块里查看私有报告列表。</p>
          </div>
        </div>

        <div v-if="!canManageReports" class="empty-state large">请先登录后再创建和查看私有报告。</div>
        <div v-else class="panel-stack">
          <div class="form-grid">
            <input v-model="generateForm.reportName" class="glass-input" placeholder="报告名称" />
            <select v-model="generateForm.reportType" class="glass-input">
              <option value="COMPREHENSIVE">综合分析</option>
              <option value="SALARY">薪资分析</option>
              <option value="SKILL">技能分析</option>
              <option value="TREND">趋势分析</option>
            </select>
          </div>
          <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">生成报告</GlowButton>

          <div class="card-list">
            <div
              v-for="report in privateReports"
              :key="report.id"
              class="list-item clickable"
              @click="openReportDetail(report)"
            >
              <div class="item-copy">
                <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                <p>{{ getReportTypeLabel(report.reportType) }}</p>
              </div>
              <div class="inline-actions">
                <span class="pill">{{ getStatusLabel(report.status || 'READY') }}</span>
                <GlowButton variant="ghost" @click.stop="handleExport(report)">
                  <FileText :size="14" />
                  导出 PDF
                </GlowButton>
              </div>
            </div>

            <div v-if="!privateReports.length && !loading" class="empty-state">暂无私有报告。</div>
          </div>
        </div>
      </article>

      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><CalendarClock :size="13" /> 调度与详情</span>
            <h2>定时任务</h2>
            <p>用统一的时间规则维护定时生成，并在下方查看当前选中的报告详情。</p>
          </div>
        </div>

        <div v-if="canManageReports" class="panel-stack">
          <div class="form-grid">
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

          <p class="meta">Cron 预览：<code>{{ scheduleCronPreview }}</code></p>
          <GlowButton variant="secondary" :loading="actionLoading" @click="handleCreateSchedule">
            <CalendarClock :size="14" />
            保存调度
          </GlowButton>

          <div class="card-list">
            <div v-for="item in schedules" :key="item.id" class="list-item">
              <div class="item-copy">
                <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
                <p>{{ frequencyLabels[item.frequency] || item.cronExpr }}</p>
                <p>{{ item.cronExpr }}</p>
              </div>
              <div class="inline-actions">
                <span class="pill">{{ getScheduleStateLabel(item) }}</span>
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

        <div class="detail-panel">
          <div class="panel-head compact">
            <div>
              <span class="eyebrow">报告详情</span>
              <h3>{{ selectedReportTitle }}</h3>
            </div>
          </div>

          <div v-if="selectedReport" class="report-detail">
            <p class="summary">{{ selectedReportSummary }}</p>

            <section>
              <h4>图表洞察</h4>
              <p>{{ selectedReport.chartInsights || '暂无图表洞察。' }}</p>
            </section>

            <section>
              <h4>建议结论</h4>
              <p>{{ selectedReport.recommendations || '暂无建议内容。' }}</p>
            </section>

            <pre class="result-box">{{ JSON.stringify(selectedReport, null, 2) }}</pre>
          </div>

          <div v-else class="empty-state large">点击左侧私有报告后，这里会显示完整的钻取详情。</div>
        </div>
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
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 24px;
  padding: 28px;
  border-radius: 22px;
}

.hero-copy,
.hero-aside,
.section-panel,
.panel-stack,
.report-detail {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 14px;
}

.hero-copy h1,
.panel-head h2,
.panel-head h3,
.report-detail h4 {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.08;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.empty-state,
.meta,
.report-detail p {
  color: var(--c-text-secondary);
}

.hero-actions,
.inline-actions,
.split-grid,
.pill {
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
.list-item,
.empty-state,
.result-box {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
}

.hero-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  color: var(--c-text-secondary);
  background: rgba(255, 255, 255, 0.54);
}

.hero-aside {
  gap: 14px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.56);
}

.metric-tile span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.metric-tile strong {
  font-size: 26px;
  letter-spacing: -0.04em;
}

.status-strip {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.58);
}

.status-strip strong {
  display: block;
  margin-bottom: 4px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.section-panel {
  gap: 18px;
  min-width: 0;
  padding: 24px;
  border-radius: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.panel-head.compact h3 {
  font-size: 18px;
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

.card-list,
.form-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-grid {
  margin-bottom: 4px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(193, 198, 215, 0.62);
  color: var(--c-text-primary);
}

.list-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.5);
}

.item-copy {
  min-width: 0;
}

.item-copy strong {
  display: block;
  margin-bottom: 4px;
}

.item-copy p,
.meta,
.report-detail p {
  margin: 0;
}

.inline-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pill {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(242, 244, 250, 0.96);
  color: var(--c-text-secondary);
  white-space: nowrap;
}

.clickable {
  cursor: pointer;
}

.split-grid {
  width: 100%;
}

.split-grid > * {
  flex: 1;
}

.empty-state {
  padding: 16px;
  background: rgba(255, 255, 255, 0.38);
}

.empty-state.large {
  min-height: 124px;
  display: flex;
  align-items: center;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 6px;
}

.report-detail {
  gap: 16px;
}

.report-detail section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.report-detail h4 {
  font-size: 15px;
}

.summary {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(193, 198, 215, 0.5);
}

.result-box {
  margin: 0;
  padding: 16px;
  background: rgba(255, 255, 255, 0.46);
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
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
  .workspace-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .section-panel,
  .workspace-hero {
    padding: 20px;
    border-radius: 18px;
  }

  .metric-grid {
    grid-template-columns: 1fr 1fr;
  }

  .list-item {
    flex-direction: column;
  }

  .inline-actions {
    justify-content: flex-start;
  }

  .split-grid {
    flex-direction: column;
  }
}
</style>
