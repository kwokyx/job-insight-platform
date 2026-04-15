<script setup>
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
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
  openReportPdf,
  toggleReportSchedule
} from '../api'
import { useAuthStore } from '../store/auth'
import {
  CalendarClock,
  Download,
  Eye,
  FileBarChart,
  FileText,
  Globe,
  LockKeyhole,
  MapPinned,
  RefreshCw,
  Target,
  TrendingUp
} from 'lucide-vue-next'
import { useThemeStore } from '../store/theme'

use([
  CanvasRenderer, PieChart, BarChart, LineChart, RadarChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent
])

const authStore = useAuthStore()
const themeStore = useThemeStore()

const publicReports = ref([])
const privateReports = ref([])
const schedules = ref([])
const selectedReport = ref(null)
const selectedTask = ref(null)
const loading = ref(true)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')

const generateForm = ref({
  reportName: '岗位能力分析报告',
  reportType: 'COMPREHENSIVE',
  targetRoleType: null
})

const scheduleForm = ref({
  scheduleName: '每周岗位情报简报',
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

const reportTypeOptions = [
  { label: '综合报告', value: 'COMPREHENSIVE' },
  { label: '薪资报告', value: 'SALARY' },
  { label: '技能报告', value: 'SKILL' },
  { label: '岗位方向报告', value: 'INDUSTRY' },
  { label: '供需报告', value: 'SUPPLY_DEMAND' }
]

const roleTemplateOptions = [
  { label: '学生 / 普通用户', value: 0 },
  { label: '管理员', value: 1 },
  { label: '教师', value: 2 }
]

const hourOptions = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))
const minuteOptions = Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0'))
const monthDayOptions = Array.from({ length: 31 }, (_, i) => String(i + 1))

const canManageReports = computed(() => authStore.isLoggedIn)
const isAdmin = computed(() => (authStore.user?.roleType ?? 0) === 1)
const selectedSections = computed(() => selectedReport.value?.sections || {})

const scheduleCronPreview = computed(() => {
  const minute = scheduleForm.value.minute
  const hour = scheduleForm.value.hour
  if (scheduleForm.value.frequency === 'DAILY') return `0 ${minute} ${hour} * * *`
  if (scheduleForm.value.frequency === 'MONTHLY') return `0 ${minute} ${hour} ${scheduleForm.value.monthDay} * *`
  return `0 ${minute} ${hour} * * ${scheduleForm.value.weekday}`
})

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) return []
  return [
    { label: '任务状态', value: taskStatusLabel(selectedTask.value.status) },
    { label: '任务进度', value: `${selectedTask.value.progress ?? 0}%` },
    { label: '开始时间', value: formatDateTime(selectedTask.value.startedAt) },
    { label: '完成时间', value: formatDateTime(selectedTask.value.completedAt) }
  ]
})

const reportHighlights = computed(() => {
  if (!selectedReport.value) return []
  return [
    { label: '图表卡片', value: listify(selectedReport.value.chartCards).length },
    { label: '对比项', value: listify(selectedReport.value.comparisonItems).length },
    { label: '建议数', value: listify(selectedReport.value.recommendations).length },
    { label: '行动项', value: listify(selectedReport.value.actionPlan).length }
  ]
})

const salaryTrendRows = computed(() => listify(selectedSections.value.salaryTrend).slice(-8))
const topSkills = computed(() => listify(selectedSections.value.topSkills).slice(0, 12))
const topCities = computed(() => listify(selectedSections.value.topCities).slice(0, 8))
const topIndustries = computed(() => listify(selectedSections.value.topIndustries).slice(0, 8))
const educationDist = computed(() => listify(selectedSections.value.educationDist).slice(0, 6))
const experienceDist = computed(() => listify(selectedSections.value.experienceDist).slice(0, 6))

const salaryTrendChart = computed(() => {
  const rows = salaryTrendRows.value
    .map((item) => ({
      period: item.period || '--',
      avgMin: Number(item.salaryAvg ?? item.avgSalaryMin ?? item.avgMin ?? 0),
      avgMax: Number(item.avgSalaryMax ?? item.avgMax ?? 0),
      jobCount: Number(item.jobCount ?? item.count ?? 0)
    }))
    .filter((item) => item.avgMin > 0 || item.avgMax > 0)

  if (!rows.length) return { rows: [], latest: null, changePct: null }
  const earliest = rows[0]
  const latest = rows[rows.length - 1]
  const changePct = earliest.avgMin > 0 ? ((latest.avgMin - earliest.avgMin) / earliest.avgMin) * 100 : null
  return { rows, latest, changePct }
})

const trendSummaryCards = computed(() => {
  const latest = salaryTrendChart.value.latest
  if (!latest) return []
  return [
    { label: '最新薪资下限', value: formatSalaryValue(latest.avgMin) },
    { label: '最新薪资上限', value: formatSalaryValue(latest.avgMax) },
    { label: '最新岗位样本', value: formatNumber(latest.jobCount) },
    { label: '阶段变化', value: formatPercent(salaryTrendChart.value.changePct) }
  ]
})

function getEchartsTheme() {
  return themeStore.isDark ? {
    textColor: '#CBD5E1',
    splitLineColor: 'rgba(255,255,255,0.08)',
    tooltipBg: 'rgba(15, 23, 42, 0.96)',
    borderColor: 'rgba(255,255,255,0.08)'
  } : {
    textColor: '#475569',
    splitLineColor: 'rgba(15,23,42,0.08)',
    tooltipBg: 'rgba(255,255,255,0.96)',
    borderColor: 'rgba(15,23,42,0.08)'
  }
}

const reportCityPieOption = computed(() => {
  if (!topCities.value.length) return null
  const t = getEchartsTheme()
  const palette = ['#38BDF8', '#22C55E', '#F59E0B', '#A855F7', '#F97316', '#14B8A6', '#6366F1', '#EC4899']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    series: [{
      type: 'pie',
      radius: ['40%', '74%'],
      label: { color: t.textColor, formatter: '{b}\n{d}%' },
      itemStyle: { borderRadius: 10, borderColor: 'transparent', borderWidth: 4 },
      data: topCities.value.map((item, index) => ({ value: Number(item.count || 0), name: item.city, itemStyle: { color: palette[index % palette.length] } }))
    }]
  }
})

const reportIndustryPieOption = computed(() => {
  if (!topIndustries.value.length) return null
  const t = getEchartsTheme()
  const palette = ['#F97316', '#3B82F6', '#A855F7', '#10B981', '#EF4444', '#2DD4BF', '#F59E0B', '#EC4899']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    series: [{
      type: 'pie',
      radius: ['28%', '78%'],
      roseType: 'area',
      label: { color: t.textColor, formatter: '{b}' },
      itemStyle: { borderRadius: 10, borderColor: 'transparent', borderWidth: 4 },
      data: topIndustries.value.map((item, index) => ({ value: Number(item.count || 0), name: item.industry, itemStyle: { color: palette[index % palette.length] } }))
    }]
  }
})

const reportSkillBarOption = computed(() => {
  if (!topSkills.value.length) return null
  const t = getEchartsTheme()
  const rows = [...topSkills.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    grid: { left: '3%', right: '4%', top: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'category', data: rows.map((item) => item.skill), axisLabel: { color: t.textColor, width: 90, overflow: 'truncate' } },
    series: [{ type: 'bar', barWidth: '56%', data: rows.map((item) => Number(item.count || 0)), itemStyle: { borderRadius: [0, 8, 8, 0], color: '#3B82F6' } }]
  }
})

const reportEducationBarOption = computed(() => {
  if (!educationDist.value.length) return null
  const t = getEchartsTheme()
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    grid: { left: '4%', right: '4%', top: '8%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: educationDist.value.map((item) => item.education), axisLabel: { color: t.textColor } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'bar', barWidth: '52%', data: educationDist.value.map((item) => Number(item.count || 0)), itemStyle: { borderRadius: [8, 8, 0, 0], color: '#8B5CF6' } }]
  }
})

const reportExperienceRadarOption = computed(() => {
  if (!experienceDist.value.length) return null
  const t = getEchartsTheme()
  const rows = experienceDist.value.slice(0, 6)
  const maxVal = Math.max(...rows.map((item) => Number(item.count || 0)), 1)
  return {
    tooltip: { backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    radar: { indicator: rows.map((item) => ({ name: item.experience, max: maxVal * 1.2 })), axisName: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'radar', data: [{ value: rows.map((item) => Number(item.count || 0)), name: '岗位数量' }], lineStyle: { color: '#14B8A6', width: 2 }, itemStyle: { color: '#14B8A6' }, areaStyle: { color: 'rgba(45,212,191,0.18)' } }]
  }
})

const reportSalaryTrendOption = computed(() => {
  if (!salaryTrendChart.value.rows.length) return null
  const t = getEchartsTheme()
  const rows = salaryTrendChart.value.rows
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    legend: { top: 0, textStyle: { color: t.textColor }, data: ['平均薪资上限', '平均薪资下限'] },
    grid: { left: '3%', right: '4%', top: '16%', bottom: '4%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: rows.map((item) => item.period), axisLabel: { color: t.textColor }, axisLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor, formatter: '{value}K' }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [
      { name: '平均薪资上限', type: 'line', smooth: true, showSymbol: false, data: rows.map((item) => item.avgMax), itemStyle: { color: '#F97316' }, areaStyle: { color: 'rgba(249,115,22,0.10)' } },
      { name: '平均薪资下限', type: 'line', smooth: true, showSymbol: false, data: rows.map((item) => item.avgMin), itemStyle: { color: '#3B82F6' }, areaStyle: { color: 'rgba(59,130,246,0.14)' } }
    ]
  }
})

function listify(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function formatNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '--'
}

function formatSalaryValue(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '--'
  return `${num.toFixed(2)}K`
}

function formatPercent(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  return `${num >= 0 ? '+' : ''}${num.toFixed(1)}%`
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function comparisonLevelLabel(level) {
  if (level === 'good') return '健康'
  if (level === 'warn') return '待补齐'
  if (level === 'risk') return '高风险'
  return '中性'
}

function taskStatusLabel(status) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '生成中'
  if (status === 'PENDING') return '排队中'
  return status || '--'
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
  while (attempts < 12) {
    attempts += 1
    selectedTask.value = await fetchReportStatus(authStore.token, taskId)
    if (['SUCCESS', 'FAILED'].includes(selectedTask.value?.status)) break
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
      params: {
        ...(isAdmin.value && generateForm.value.targetRoleType !== null ? { targetRoleType: Number(generateForm.value.targetRoleType) } : {})
      }
    })
    if (result.taskId) {
      success.value = `报告任务已提交，任务号 ${result.taskId}`
      await pollTask(result.taskId)
      await loadPage()
    } else {
      success.value = '报告请求已提交'
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
    success.value = '定时任务已创建'
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
    success.value = 'PDF 已开始下载'
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handlePreviewPdf(report) {
  error.value = ''
  try {
    await openReportPdf(authStore.token, report.id)
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

    <section class="hero glass-panel">
      <div>
        <p class="eyebrow">Report Center</p>
        <h1>把岗位数据整理成能看、能比、能执行的可视化报告</h1>
        <p class="hero-text">报告中心会按不同角色生成内容，并直接展示薪资趋势、市场结构和关键对比。</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat"><span>我的报告</span><strong>{{ privateReports.length }}</strong></div>
        <div class="hero-stat"><span>定时任务</span><strong>{{ schedules.length }}</strong></div>
      </div>
    </section>

    <section class="grid">
      <PremiumCard title="公开报告" glowColor="primary">
        <div class="card-list">
          <div v-for="report in publicReports" :key="report.id" class="list-item">
            <div class="list-main"><strong>{{ report.reportName || `报告 #${report.id}` }}</strong><p>{{ report.reportType || '未知类型' }}</p></div>
            <span class="pill"><Globe :size="14" /> 公开</span>
          </div>
          <div v-if="!publicReports.length && !loading" class="empty-state">暂无公开报告。</div>
        </div>
      </PremiumCard>

      <PremiumCard title="我的报告" glowColor="secondary">
        <template #header>
          <div class="panel-header">
            <div class="title-row"><LockKeyhole :size="18" /><h2>我的报告</h2></div>
            <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" />刷新</GlowButton>
          </div>
        </template>
        <div v-if="!canManageReports" class="empty-state">登录后可生成、查看并导出个人报告。</div>
        <div v-else class="card-list">
          <div class="form-grid">
            <input v-model="generateForm.reportName" class="glass-input" placeholder="输入报告名称" />
            <select v-model="generateForm.reportType" class="glass-input"><option v-for="item in reportTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select>
          </div>
          <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">生成报告</GlowButton>
          <div v-if="selectedTask" class="task-strip"><div v-for="item in latestTaskSummary" :key="item.label" class="summary-box"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div>
          <div v-for="report in privateReports" :key="report.id" class="list-item clickable" @click="openReportDetail(report)">
            <div class="list-main"><strong>{{ report.reportName || `报告 #${report.id}` }}</strong><p>{{ report.reportType || '未知类型' }} · {{ formatDateTime(report.generatedAt) }}</p></div>
            <div class="inline-actions">
              <span class="pill">{{ taskStatusLabel(report.status || 'SUCCESS') }}</span>
              <GlowButton variant="ghost" @click.stop="handlePreviewPdf(report)"><Eye :size="14" />预览</GlowButton>
              <GlowButton variant="ghost" @click.stop="handleExport(report)"><Download :size="14" />PDF</GlowButton>
            </div>
          </div>
          <div v-if="!privateReports.length && !loading" class="empty-state">暂无个人报告。</div>
        </div>
      </PremiumCard>

      <PremiumCard v-if="selectedReport" title="报告详情" glowColor="primary" class="detail-card">
        <div class="report-detail">
          <div class="detail-header">
            <div class="detail-main"><h3>{{ selectedReport.reportName || `报告 #${selectedReport.id}` }}</h3><p>{{ selectedReport.summary || '暂无摘要。' }}</p></div>
            <div class="inline-actions">
              <GlowButton variant="ghost" @click="handlePreviewPdf({ id: selectedReport.reportId, reportName: selectedReport.reportName })"><Eye :size="14" />预览 PDF</GlowButton>
              <GlowButton variant="ghost" @click="handleExport({ id: selectedReport.reportId, reportName: selectedReport.reportName })"><Download :size="14" />下载 PDF</GlowButton>
            </div>
          </div>

          <div class="summary-strip">
            <div class="summary-box"><span>目标读者</span><strong>{{ selectedReport.targetAudience || '普通用户' }}</strong></div>
            <div class="summary-box"><span>报告重点</span><strong>{{ selectedReport.reportFocus || '--' }}</strong></div>
            <div class="summary-box"><span>报告类型</span><strong>{{ selectedReport.reportType }}</strong></div>
          </div>

          <section v-if="salaryTrendChart.rows.length" class="report-section">
            <div class="section-head"><TrendingUp :size="16" /><h4>薪资趋势图</h4></div>
            <div class="insight-grid insight-grid-salary">
              <div class="chart-surface"><div class="chart-surface-head"><h5>阶段薪资区间</h5><p>按月份回看岗位平均薪资上下限变化</p></div><div class="report-chart-box report-chart-box-wide"><VChart v-if="reportSalaryTrendOption" class="chart" :option="reportSalaryTrendOption" autoresize /></div></div>
              <div class="metric-stack"><div v-for="item in trendSummaryCards" :key="item.label" class="trend-stat"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div>
            </div>
          </section>

          <section v-if="topSkills.length || topCities.length || topIndustries.length" class="report-section">
            <div class="section-head"><FileBarChart :size="16" /><h4>市场结构图表</h4></div>
            <div class="insight-grid insight-grid-structure">
              <div v-if="reportSkillBarOption" class="chart-surface"><div class="chart-surface-head"><h5>热门技能热度</h5><p>市场中最常出现的能力标签</p></div><div class="report-chart-box report-chart-box-tall"><VChart class="chart" :option="reportSkillBarOption" autoresize /></div></div>
              <div v-if="reportCityPieOption" class="chart-surface"><div class="chart-surface-head"><h5>城市机会分布</h5><p>岗位需求主要集中区域</p></div><div class="report-chart-box"><VChart class="chart" :option="reportCityPieOption" autoresize /></div></div>
              <div v-if="reportIndustryPieOption" class="chart-surface"><div class="chart-surface-head"><h5>行业方向占比</h5><p>当前值得重点跟进的行业</p></div><div class="report-chart-box"><VChart class="chart" :option="reportIndustryPieOption" autoresize /></div></div>
            </div>
          </section>

          <section v-if="educationDist.length || experienceDist.length" class="report-section">
            <div class="section-head"><MapPinned :size="16" /><h4>要求结构分布</h4></div>
            <div class="insight-grid insight-grid-distribution">
              <div v-if="reportEducationBarOption" class="chart-surface"><div class="chart-surface-head"><h5>学历门槛分布</h5><p>不同学历要求对应的岗位数量</p></div><div class="report-chart-box"><VChart class="chart" :option="reportEducationBarOption" autoresize /></div></div>
              <div v-if="reportExperienceRadarOption" class="chart-surface"><div class="chart-surface-head"><h5>经验要求重心</h5><p>不同经验段的市场吸纳强度</p></div><div class="report-chart-box"><VChart class="chart" :option="reportExperienceRadarOption" autoresize /></div></div>
            </div>
          </section>

          <section v-if="listify(selectedReport.comparisonItems).length" class="report-section">
            <div class="section-head"><Target :size="16" /><h4>关键对比项</h4></div>
            <div class="comparison-list">
              <div
                v-for="item in listify(selectedReport.comparisonItems)"
                :key="item.label"
                class="comparison-item"
                :class="`comparison-${item.level || 'neutral'}`"
              >
                <div class="comparison-head">
                  <strong>{{ item.label }}</strong>
                  <span class="comparison-badge">{{ comparisonLevelLabel(item.level) }}</span>
                </div>
                <p>当前：{{ item.mine || '--' }}</p>
                <p>市场 / 目标：{{ item.market || '--' }}</p>
                <p>{{ item.insight || '--' }}</p>
              </div>
            </div>
          </section>

          <section v-if="listify(selectedReport.chartInsights).length" class="report-section">
            <div class="section-head"><TrendingUp :size="16" /><h4>图表洞察</h4></div>
            <ul class="bullet-list">
              <li v-for="item in listify(selectedReport.chartInsights)" :key="item">{{ item }}</li>
            </ul>
          </section>

          <section v-if="listify(selectedReport.recommendations).length" class="report-section">
            <div class="section-head"><Target :size="16" /><h4>建议清单</h4></div>
            <ul class="bullet-list">
              <li v-for="item in listify(selectedReport.recommendations)" :key="item">{{ item }}</li>
            </ul>
          </section>

          <section v-if="listify(selectedReport.actionPlan).length" class="report-section">
            <div class="section-head"><FileText :size="16" /><h4>行动计划</h4></div>
            <div class="action-list">
              <div v-for="item in listify(selectedReport.actionPlan)" :key="`${item.priority}-${item.title}`" class="action-item">
                <span class="priority">{{ item.priority || 'P' }}</span>
                <div class="detail-main">
                  <strong>{{ item.title || '行动项' }}</strong>
                  <p>{{ item.detail || '--' }}</p>
                </div>
              </div>
            </div>
          </section>

          <section v-if="listify(selectedReport.jobSamples).length" class="report-section">
            <div class="section-head"><FileBarChart :size="16" /><h4>岗位样本</h4></div>
            <div class="job-sample-list">
              <div v-for="item in listify(selectedReport.jobSamples)" :key="item.id || item.title" class="job-sample">
                <strong>{{ item.title || '--' }}</strong>
                <p>{{ item.companyName || '--' }} / {{ item.city || '--' }}</p>
                <p>{{ item.salaryText || '--' }}</p>
              </div>
            </div>
          </section>
        </div>
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.report-page{display:flex;flex-direction:column;gap:24px}.hero{display:grid;grid-template-columns:minmax(0,1.5fr) 280px;gap:20px;padding:28px}.eyebrow{margin:0 0 10px;font-size:12px;letter-spacing:.14em;text-transform:uppercase;color:var(--c-text-faint)}.hero h1{margin:0;font-size:clamp(28px,4vw,42px);line-height:1.08;max-width:14ch}.hero-text{margin:14px 0 0;color:var(--c-text-secondary);line-height:1.7}.hero-side{display:grid;gap:12px}.hero-stat,.summary-box,.chart-surface,.trend-stat,.comparison-item,.action-item,.job-sample{border:1px solid var(--c-border-glass);background:rgba(255,255,255,.04)}.hero-stat,.summary-box,.trend-stat{padding:16px;border-radius:18px}.hero-stat span,.summary-box span,.trend-stat span{display:block;font-size:12px;color:var(--c-text-faint);margin-bottom:8px}.hero-stat strong,.summary-box strong,.trend-stat strong{font-size:20px;color:var(--c-text-primary)}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}.panel-header,.title-row,.inline-actions,.detail-header,.section-head,.comparison-head{display:flex;align-items:center;gap:12px}.panel-header,.detail-header,.comparison-head{justify-content:space-between}.title-row h2,.report-detail h3,.report-detail h4{margin:0}.card-list,.form-grid,.report-detail,.action-list{display:flex;flex-direction:column;gap:14px}.list-item{display:flex;justify-content:space-between;gap:12px;padding:14px 16px;border-radius:16px;background:rgba(255,255,255,.04);border:1px solid var(--c-border-glass);min-width:0}.list-main,.detail-main{min-width:0}.list-item p,.report-detail p,.job-sample p{margin:0;color:var(--c-text-secondary)}.clickable{cursor:pointer}.pill{display:inline-flex;align-items:center;gap:8px;width:fit-content;padding:6px 12px;border-radius:999px;background:rgba(255,255,255,.08)}.glass-input{width:100%;padding:12px 14px;border-radius:14px;background:rgba(255,255,255,.04);border:1px solid var(--c-border-glass);color:var(--c-text-primary)}.empty-state,.error-banner,.success-banner{padding:14px 16px;border-radius:16px}.empty-state{border:1px dashed var(--c-border-glass);color:var(--c-text-secondary)}.error-banner{color:#fecaca}.success-banner{color:#bbf7d0}.detail-card{grid-column:1/-1}.summary-strip,.task-strip{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.report-section{display:grid;gap:12px}.insight-grid{display:grid;gap:14px}.insight-grid-salary{grid-template-columns:minmax(0,1.8fr) 280px}.insight-grid-structure,.insight-grid-distribution,.comparison-list,.job-sample-list{grid-template-columns:repeat(2,minmax(0,1fr))}.chart-surface,.comparison-list,.job-sample-list{display:grid;gap:14px}.chart-surface{padding:16px;border-radius:20px;overflow:hidden}.chart-surface-head h5,.chart-surface-head p{margin:0}.chart-surface-head p{color:var(--c-text-secondary)}.report-chart-box{height:320px;overflow:hidden;border-radius:18px;background:radial-gradient(circle at top left,rgba(56,189,248,.12),transparent 38%),linear-gradient(180deg,rgba(255,255,255,.02),rgba(255,255,255,.04))}.report-chart-box-wide{height:340px}.report-chart-box-tall{height:390px}.chart{width:100%;height:100%}.metric-stack{display:grid;gap:12px}.comparison-item,.action-item,.job-sample{padding:16px;border-radius:18px;overflow:hidden}.comparison-badge{display:inline-flex;padding:4px 10px;border-radius:999px;font-size:12px;font-weight:700;background:rgba(255,255,255,.08)}.comparison-good{border-color:rgba(34,197,94,.35);background:rgba(34,197,94,.08)}.comparison-warn{border-color:rgba(245,158,11,.35);background:rgba(245,158,11,.08)}.comparison-risk{border-color:rgba(239,68,68,.35);background:rgba(239,68,68,.08)}.bullet-list{margin:0;padding-left:20px;color:var(--c-text-secondary)}.bullet-list li{margin-bottom:8px}.action-item{display:grid;grid-template-columns:40px 1fr;gap:12px}.priority{width:32px;height:32px;border-radius:999px;display:flex;align-items:center;justify-content:center;background:rgba(59,130,246,.18);color:var(--c-text-primary);font-weight:800}@media (max-width:1100px){.grid,.hero,.summary-strip,.task-strip,.insight-grid-salary,.insight-grid-structure,.insight-grid-distribution,.comparison-list,.job-sample-list{grid-template-columns:1fr}}
</style>
