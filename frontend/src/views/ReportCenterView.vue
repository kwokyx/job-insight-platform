<script setup>
import { computed, onMounted, ref } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
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
import { CalendarClock, FileText, Globe, LockKeyhole, RefreshCw } from 'lucide-vue-next'

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

function getReportTypeLabel(type) {
  return reportTypeLabels[type] || type || '未知类型'
}

function getStatusLabel(status) {
  return statusLabels[status] || status || '未知状态'
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
  <div class="report-page">
    <div v-if="error" class="error-banner glass-panel">{{ error }}</div>
    <div v-if="success" class="success-banner glass-panel">{{ success }}</div>

    <section class="grid">
      <PremiumCard title="公开报告" glowColor="primary">
        <div class="card-list">
          <div v-for="report in publicReports" :key="report.id" class="list-item">
            <div>
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
      </PremiumCard>

      <PremiumCard title="我的报告" glowColor="secondary">
        <template #header>
          <div class="panel-header">
            <div class="title-row">
              <LockKeyhole :size="18" />
              <h2>我的报告</h2>
            </div>
            <GlowButton variant="ghost" @click="loadPage">
              <RefreshCw :size="14" />
              刷新
            </GlowButton>
          </div>
        </template>

        <div v-if="!canManageReports" class="empty-state">请先登录后再创建和查看私有报告。</div>
        <div v-else class="card-list">
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

          <div v-for="report in privateReports" :key="report.id" class="list-item clickable" @click="openReportDetail(report)">
            <div>
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
      </PremiumCard>

      <PremiumCard v-if="canManageReports" title="定时调度" glowColor="teal">
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
            <div>
              <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
              <p>{{ frequencyLabels[item.frequency] || item.cronExpr }}</p>
              <p>{{ item.cronExpr }}</p>
            </div>
            <div class="inline-actions">
              <GlowButton variant="ghost" @click="handleToggleSchedule(item.id)">
                {{ item.enabled ? '停用' : '启用' }}
              </GlowButton>
              <GlowButton variant="ghost" @click="handleDeleteSchedule(item.id)">删除</GlowButton>
            </div>
          </div>

          <div v-if="!schedules.length && !loading" class="empty-state">暂无调度任务。</div>
        </div>
      </PremiumCard>

      <PremiumCard v-if="selectedReport" title="报告详情" glowColor="primary">
        <div class="report-detail">
          <h3>{{ selectedReport.reportName || `报告 #${selectedReport.id}` }}</h3>
          <p>{{ selectedReport.summary || '暂无摘要。' }}</p>

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
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.panel-header,
.title-row,
.inline-actions,
.split-grid {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-header {
  justify-content: space-between;
}

.title-row h2,
.report-detail h3,
.report-detail h4 {
  margin: 0;
}

.card-list,
.form-grid,
.report-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.list-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
}

.list-item p,
.meta {
  margin: 0;
  color: var(--c-text-secondary);
}

.clickable {
  cursor: pointer;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.empty-state,
.error-banner,
.success-banner,
.result-box {
  padding: 14px 16px;
  border-radius: 16px;
}

.empty-state {
  border: 1px dashed var(--c-border-glass);
  color: var(--c-text-secondary);
}

.error-banner {
  color: #fecaca;
}

.success-banner {
  color: #bbf7d0;
}

.result-box {
  background: rgba(255, 255, 255, 0.04);
  white-space: pre-wrap;
  word-break: break-word;
  overflow: auto;
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
