<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import {
  createReport,
  deleteReport,
  exportReportFormat,
  fetchPublicReports,
  fetchReportCenterMeta,
  fetchReportDrill,
  fetchReports,
  fetchReportSchedules,
  fetchReportStatus,
  normalizeError,
  openReportPdf
} from '../api'
import { useAuthStore } from '../store/auth'
import { useThemeStore } from '../store/theme'
import { getRoleLabel } from '../utils/role'
import {
  BarChart3,
  BookOpen,
  CalendarClock,
  Download,
  Eye,
  FileBarChart,
  FileText,
  Globe,
  LockKeyhole,
  RefreshCw,
  Shield,
  ShieldCheck,
  Sparkles,
  Target,
  Trash2,
  TrendingUp,
  UserRound
} from 'lucide-vue-next'

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
const reportMeta = ref(buildLocalMeta(authStore.user?.roleType ?? 0))
const loading = ref(true)
const detailLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')
const exportFormat = ref('pdf')
const autoReportName = ref('')

const generateForm = ref({
  reportType: reportMeta.value.defaultReportType,
  reportName: reportMeta.value.defaultReportName
})
autoReportName.value = generateForm.value.reportName

const canManageReports = computed(() => authStore.isLoggedIn)
const currentRoleType = computed(() => authStore.user?.roleType ?? 0)
const currentRoleLabel = computed(() => getRoleLabel(currentRoleType.value))
const currentReportTypes = computed(() => reportMeta.value?.reportTypes || [])
const currentReportTypeConfig = computed(() => currentReportTypes.value.find((item) => item.code === generateForm.value.reportType) || currentReportTypes.value[0] || null)
const selectedSections = computed(() => selectedReport.value?.sections || {})
const heroStats = computed(() => [
  { label: canManageReports.value ? '私有报告' : '公开报告', value: canManageReports.value ? privateReports.value.length : publicReports.value.length },
  { label: '定时计划', value: schedules.value.length },
  { label: '角色入口', value: currentReportTypes.value.length }
])

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) return []
  return [
    { label: '任务状态', value: taskStatusLabel(selectedTask.value.status) },
    { label: '生成进度', value: `${selectedTask.value.progress ?? 0}%` },
    { label: '开始时间', value: formatDateTime(selectedTask.value.startedAt) },
    { label: '完成时间', value: formatDateTime(selectedTask.value.completedAt) }
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

watch(() => authStore.user?.roleType, (roleType) => {
  if (!authStore.isLoggedIn) {
    applyMeta(buildLocalMeta(roleType ?? 0))
  }
})

watch(() => generateForm.value.reportType, (nextType, prevType) => {
  if (!nextType) return
  const nextConfig = currentReportTypes.value.find((item) => item.code === nextType)
  const prevConfig = currentReportTypes.value.find((item) => item.code === prevType)
  const currentName = generateForm.value.reportName?.trim() || ''
  if (!currentName || currentName === autoReportName.value || currentName === prevConfig?.defaultName) {
    generateForm.value.reportName = nextConfig?.defaultName || ''
    autoReportName.value = generateForm.value.reportName
  }
})

function buildLocalMeta(roleType) {
  const roleLabel = getRoleLabel(roleType)
  if (roleType === 1) {
    return {
      roleType,
      roleLabel,
      moduleTitle: '运营分析工作台',
      moduleDescription: '管理员入口优先突出平台运营分析、供需结构和增长抓手。',
      defaultReportType: 'OPERATIONS',
      defaultReportName: '平台运营分析报告',
      privateListScope: '可查看全站私有报告',
      publicListScope: '公开报告对所有用户可见',
      reportTypes: [
        { code: 'OPERATIONS', label: '平台运营分析', defaultName: '平台运营分析报告', description: '聚焦用户分层、内容供给、转化抓手与运营优先级。', templateDescription: '适合管理员快速判断资源投向。', entryHint: '优先看低匹配用户、头部赛道和高频缺口。' },
        { code: 'SUPPLY_DEMAND', label: '平台供需分析', defaultName: '平台供需分析报告', description: '聚焦岗位需求与平台人才供给的结构关系。', templateDescription: '适合识别供需错位与内容补位方向。', entryHint: '优先看供需失衡点。' },
        { code: 'INDUSTRY', label: '行业走势观察', defaultName: '平台行业走势观察报告', description: '聚焦重点赛道与热度变化。', templateDescription: '适合跟踪热点行业变化。', entryHint: '优先看头部赛道。' },
        { code: 'COMPREHENSIVE', label: '平台综合报告', defaultName: '平台综合分析报告', description: '适合阶段复盘的综合总览。', templateDescription: '覆盖核心图表与建议。', entryHint: '适合作为管理总览入口。' }
      ]
    }
  }
  if (roleType === 2) {
    return {
      roleType,
      roleLabel,
      moduleTitle: '教学支持工作台',
      moduleDescription: '教师入口优先突出供需分析、教学建议和能力缺口观察。',
      defaultReportType: 'SUPPLY_DEMAND',
      defaultReportName: '班级供需分析报告',
      privateListScope: '仅查看本人生成的私有报告',
      publicListScope: '公开报告对所有用户可见',
      reportTypes: [
        { code: 'SUPPLY_DEMAND', label: '供需分析报告', defaultName: '班级供需分析报告', description: '聚焦学生能力供给与岗位需求之间的差距。', templateDescription: '适合教师识别班级共性短板。', entryHint: '优先看高频赛道与缺口技能。' },
        { code: 'TEACHING_ADVICE', label: '教学建议报告', defaultName: '教学建议与课程对齐报告', description: '聚焦课程设计、实训任务和求职辅导。', templateDescription: '适合把岗位要求映射到教学动作。', entryHint: '优先看课程补位点。' },
        { code: 'SKILL', label: '能力缺口观察', defaultName: '教学能力缺口观察报告', description: '聚焦岗位高频技能与教学侧差距。', templateDescription: '适合拆出训练任务。', entryHint: '优先看高频技能。' },
        { code: 'COMPREHENSIVE', label: '教学支持总览', defaultName: '教学支持综合报告', description: '适合阶段教学复盘。', templateDescription: '覆盖核心图表与建议。', entryHint: '适合作为总览入口。' }
      ]
    }
  }
  return {
    roleType: roleType ?? 0,
    roleLabel,
    moduleTitle: '个人求职工作台',
    moduleDescription: '学生入口优先突出个人求职分析、技能差距和薪资趋势。',
    defaultReportType: 'JOB_SEEKING',
    defaultReportName: '个人求职分析报告',
    privateListScope: '仅查看本人生成的私有报告',
    publicListScope: '公开报告对所有用户可见',
    reportTypes: [
      { code: 'JOB_SEEKING', label: '个人求职分析', defaultName: '个人求职分析报告', description: '聚焦岗位匹配、投递策略和目标城市机会。', templateDescription: '适合学生快速判断该补什么、该投什么。', entryHint: '优先看匹配度与岗位样本。' },
      { code: 'SKILL_GAP', label: '技能差距分析', defaultName: '个人技能差距分析报告', description: '聚焦当前技能与高频岗位要求的差距。', templateDescription: '适合识别优先补齐的核心技能。', entryHint: '优先看缺口技能排序。' },
      { code: 'SALARY', label: '薪资趋势参考', defaultName: '个人薪资趋势参考报告', description: '聚焦市场薪资区间与预期校准。', templateDescription: '适合判断目标薪资是否合理。', entryHint: '优先看薪资趋势。' },
      { code: 'COMPREHENSIVE', label: '个人综合报告', defaultName: '个人综合求职报告', description: '适合做阶段复盘的总览报告。', templateDescription: '覆盖关键图表和行动建议。', entryHint: '适合作为综合入口。' }
    ]
  }
}

function applyMeta(meta) {
  const safeMeta = meta && meta.reportTypes?.length ? meta : buildLocalMeta(currentRoleType.value)
  reportMeta.value = safeMeta
  generateForm.value.reportType = safeMeta.defaultReportType
  generateForm.value.reportName = safeMeta.defaultReportName
  autoReportName.value = safeMeta.defaultReportName
}

function reportTypeLabel(code) {
  return currentReportTypes.value.find((item) => item.code === code)?.label || code || '--'
}

function getRoleIcon() {
  if (currentRoleType.value === 1) return Shield
  if (currentRoleType.value === 2) return BookOpen
  return UserRound
}

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
  if (level === 'good') return '表现良好'
  if (level === 'warn') return '需要补位'
  if (level === 'risk') return '重点风险'
  return '中性观察'
}

function taskStatusLabel(status) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '生成中'
  if (status === 'PENDING') return '排队中'
  return status || '--'
}

function scheduleFrequencyLabel(frequency) {
  if (frequency === 'DAILY') return '每天'
  if (frequency === 'MONTHLY') return '每月'
  if (frequency === 'WEEKLY') return '每周'
  return frequency || '周期任务'
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
      applyMeta(buildLocalMeta(currentRoleType.value))
      return
    }

    const [metaResult, reportsResult, schedulesResult] = await Promise.allSettled([
      fetchReportCenterMeta(authStore.token),
      fetchReports(authStore.token, { page: 1, pageSize: 10 }),
      fetchReportSchedules(authStore.token)
    ])

    if (metaResult.status === 'fulfilled') {
      applyMeta(metaResult.value)
    } else {
      applyMeta(buildLocalMeta(currentRoleType.value))
    }

    privateReports.value = reportsResult.status === 'fulfilled' ? (reportsResult.value.data || []) : []
    schedules.value = schedulesResult.status === 'fulfilled' ? (schedulesResult.value || []) : []
    if (reportsResult.status === 'rejected' || schedulesResult.status === 'rejected') {
      error.value = '部分报告数据加载失败，已展示当前可用内容。'
    }
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
  if (!canManageReports.value) {
    error.value = '请先登录后再生成角色专属报告。'
    return
  }
  actionLoading.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await createReport(authStore.token, {
      reportName: generateForm.value.reportName.trim() || currentReportTypeConfig.value?.defaultName || reportMeta.value.defaultReportName,
      reportType: generateForm.value.reportType,
      params: {
        targetRoleType: currentRoleType.value
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

async function openReportDetail(report) {
  if (detailLoading.value) return
  error.value = ''
  detailLoading.value = true
  try {
    selectedReport.value = await fetchReportDrill(authStore.token, report.id)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    detailLoading.value = false
  }
}

async function handleFormatExport(report) {
  if (!report?.id && !report?.reportId) return
  try {
    await exportReportFormat(authStore.token, report.reportId || report.id, report.reportName, exportFormat.value)
    success.value = `报告已导出为 ${exportFormat.value.toUpperCase()}`
    setTimeout(() => { success.value = '' }, 3000)
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

async function handleDeleteReport(id, event) {
  if (event) event.stopPropagation()
  if (!confirm('确定要删除这份报告吗？删除后无法恢复。')) return
  actionLoading.value = true
  error.value = ''
  try {
    await deleteReport(authStore.token, id)
    success.value = '报告已删除'
    if (selectedReport.value?.reportId === id || selectedReport.value?.id === id) {
      selectedReport.value = null
    }
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
    setTimeout(() => { success.value = '' }, 3000)
  }
}

onMounted(() => {
  loadPage()
})
</script>

<template>
  <div class="report-page page-shell">
    <section class="report-hero workspace-page-head">
      <div class="workspace-page-copy">
        <h1 class="workspace-page-title">{{ reportMeta.moduleTitle || '报告中心' }}</h1>
        <p class="workspace-page-subtitle">{{ reportMeta.moduleDescription || '报告库、私有报告与角色化分析都在这里。' }}</p>
        <div class="hero-badges">
          <span class="hero-badge">
            <component :is="getRoleIcon()" :size="14" />
            {{ currentRoleLabel }}角色入口
          </span>
          <span class="hero-badge">
            <Sparkles :size="14" />
            {{ reportMeta.privateListScope }}
          </span>
        </div>
      </div>

      <div class="workspace-page-strip">
        <div class="workspace-page-actions">
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
        </div>

        <div class="workspace-page-pills">
          <div v-for="item in heroStats" :key="item.label" class="workspace-page-pill">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <div class="workspace-page-pill">
            <ShieldCheck :size="14" />
            <span>{{ canManageReports ? '已登录，可管理角色化报告' : '登录后生成私有报告' }}</span>
          </div>
        </div>
      </div>
    </section>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="success" class="status-banner success-banner">{{ success }}</div>

    <section class="master-detail-layout">
      <div class="sidebar">
        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><Sparkles :size="15" /> 角色化入口</h2>
            </div>
          </div>
          <div class="card-list">
            <div class="role-meta-card">
              <div class="role-meta-top">
                <div>
                  <p class="mini-label">当前角色</p>
                  <h3>{{ reportMeta.roleLabel }}</h3>
                </div>
                <span class="pill">{{ reportMeta.publicListScope }}</span>
              </div>
              <p class="role-meta-text">{{ currentReportTypeConfig?.templateDescription || reportMeta.moduleDescription }}</p>
            </div>

            <div class="entry-grid">
              <button
                v-for="item in currentReportTypes"
                :key="item.code"
                class="entry-card"
                :class="{ active: generateForm.reportType === item.code }"
                @click="generateForm.reportType = item.code"
              >
                <strong>{{ item.label }}</strong>
                <p>{{ item.description }}</p>
                <span>{{ item.entryHint }}</span>
              </button>
            </div>

            <div v-if="canManageReports" class="form-grid">
              <select v-model="generateForm.reportType" class="glass-input">
                <option v-for="item in currentReportTypes" :key="item.code" :value="item.code">
                  {{ item.label }}
                </option>
              </select>
              <input
                v-model="generateForm.reportName"
                class="glass-input"
                :placeholder="currentReportTypeConfig?.defaultName || '输入报告名称'"
                @keydown.enter="handleCreateReport"
              />
              <div class="hint-box">
                <strong>{{ currentReportTypeConfig?.label }}</strong>
                <p>{{ currentReportTypeConfig?.templateDescription }}</p>
              </div>
              <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">生成角色专属报告</GlowButton>
            </div>

            <div v-else class="empty-state-wrapper">
              <EmptyState icon="file" title="登录后可生成报告" description="登录后即可使用学生、教师或管理员专属入口生成对应角色的报告。" />
            </div>

            <div v-if="selectedTask" class="task-strip">
              <div v-for="item in latestTaskSummary" :key="item.label" class="summary-box-mini">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>
        </article>

        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><LockKeyhole :size="15" /> {{ currentRoleType === 1 ? '私有报告总览' : '我的报告' }}</h2>
            </div>
            <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" />刷新</GlowButton>
          </div>
          <div v-if="!canManageReports" class="empty-state-wrapper">
            <EmptyState icon="inbox" title="暂不可查看私有报告" description="登录后可查看并管理你自己的角色化报告。" />
          </div>
          <div v-else class="card-list scrollable-list">
            <div
              v-for="report in privateReports"
              :key="report.id"
              class="list-item clickable"
              :class="{ active: selectedReport?.reportId === report.id }"
              @click="openReportDetail(report)"
            >
              <div class="list-main">
                <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                <p>{{ reportTypeLabel(report.reportType) }} · {{ formatDateTime(report.generatedAt) }}</p>
              </div>
              <div class="inline-actions">
                <span class="pill good">{{ taskStatusLabel(report.status || 'SUCCESS') }}</span>
                <Trash2 class="delete-icon" :size="16" @click="handleDeleteReport(report.id, $event)" />
              </div>
            </div>
            <div v-if="loading" class="skeleton-list mt-4">
              <SkeletonCard type="list" :lines="4" />
            </div>
            <div v-if="!privateReports.length && !loading" class="empty-state-wrapper mt-4">
              <EmptyState icon="file" title="还没有生成私有报告" description="先从左侧选择一个角色入口，再生成第一份报告。" />
            </div>
          </div>
        </article>

        <article class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><Globe :size="15" /> 公开报告</h2>
            </div>
          </div>
          <div class="card-list scrollable-list-small">
            <div
              v-for="report in publicReports"
              :key="report.id"
              class="list-item clickable"
              :class="{ active: selectedReport?.reportId === report.id }"
              @click="openReportDetail(report)"
            >
              <div class="list-main">
                <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                <p>{{ reportTypeLabel(report.reportType) }}</p>
              </div>
              <span class="pill"><Globe :size="14" />公开</span>
            </div>
            <div v-if="loading" class="skeleton-list mt-4">
              <SkeletonCard type="list" :lines="3" />
            </div>
            <div v-if="!publicReports.length && !loading" class="empty-state-wrapper mt-4">
              <EmptyState icon="file" title="暂无公开报告" description="当前还没有可以直接浏览的公开报告。" />
            </div>
          </div>
        </article>

        <!-- TODO: 集成后复核 调度创建/启停/删除接口未提供，暂显示只读列表 -->
        <article v-if="canManageReports && schedules.length" class="surface section-panel workspace-module-panel">
          <div class="panel-head workspace-panel-head">
            <div class="workspace-panel-copy">
              <h2 class="workspace-panel-title inline-icon"><CalendarClock :size="15" /> 调度任务</h2>
            </div>
          </div>
          <div class="card-list">
            <div v-for="item in schedules" :key="item.id" class="list-item">
              <div class="list-main">
                <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
                <p>{{ scheduleFrequencyLabel(item.frequency) }} · {{ item.cronExpr || '--' }}</p>
              </div>
              <span class="pill" :class="{ good: item.enabled }">{{ item.enabled ? '运行中' : '已停用' }}</span>
            </div>
          </div>
        </article>
      </div>

      <div class="main-content">
        <div v-if="detailLoading" class="loading-overlay">
          <RefreshCw class="spinning" :size="32" style="color: var(--c-accent-primary)" />
          <div style="margin-top: 12px; color: var(--c-text-muted); font-size: 14px;">正在加载报告详情...</div>
        </div>

        <PremiumCard v-if="selectedReport" title="报告详情" glowColor="primary" class="detail-card">
          <div class="report-detail">
            <div class="detail-header">
              <div class="detail-main">
                <h3>{{ selectedReport.reportName || `报告 #${selectedReport.id}` }}</h3>
                <p>{{ selectedReport.summary || '暂无摘要。' }}</p>
                <p class="template-copy">{{ selectedReport.templateDescription || selectedReport.reportMeta?.templateDescription }}</p>
              </div>
              <div class="inline-actions" style="gap: 8px;">
                <GlowButton variant="ghost" @click="handlePreviewPdf({ id: selectedReport.reportId || selectedReport.id })"><Eye :size="14" />预览 PDF</GlowButton>
                <select v-model="exportFormat" class="glass-input compact-input">
                  <option value="pdf">PDF</option>
                  <option value="md">Markdown</option>
                  <option value="html">HTML</option>
                </select>
                <GlowButton variant="primary" style="height: 36px;" @click="handleFormatExport({ id: selectedReport.reportId || selectedReport.id, reportName: selectedReport.reportName })"><Download :size="14" />导出</GlowButton>
              </div>
            </div>

            <div class="summary-strip">
              <div class="summary-box">
                <span>目标读者</span>
                <strong>{{ selectedReport.targetAudience || '报告使用者' }}</strong>
              </div>
              <div class="summary-box">
                <span>报告重点</span>
                <strong>{{ selectedReport.reportFocus || '--' }}</strong>
              </div>
              <div class="summary-box">
                <span>报告类型</span>
                <strong>{{ reportTypeLabel(selectedReport.reportType) }}</strong>
              </div>
            </div>

            <section v-if="salaryTrendChart.rows.length" class="report-section">
              <div class="section-head"><TrendingUp :size="16" /><h4>薪资趋势图</h4></div>
              <div class="insight-grid insight-grid-salary">
                <div class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>阶段薪资区间</h5>
                    <p>按时间回看岗位平均薪资上下限变化</p>
                  </div>
                  <div class="report-chart-box report-chart-box-wide">
                    <VChart v-if="reportSalaryTrendOption" class="chart" :option="reportSalaryTrendOption" autoresize />
                  </div>
                </div>
                <div class="metric-stack">
                  <div v-for="item in trendSummaryCards" :key="item.label" class="trend-stat">
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </div>
            </section>

            <section v-if="topSkills.length || topCities.length || topIndustries.length" class="report-section">
              <div class="section-head"><FileBarChart :size="16" /><h4>市场结构图表</h4></div>
              <div class="insight-grid insight-grid-structure">
                <div v-if="reportSkillBarOption" class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>高频技能热度</h5>
                    <p>岗位样本中最常出现的能力标签</p>
                  </div>
                  <div class="report-chart-box report-chart-box-tall"><VChart class="chart" :option="reportSkillBarOption" autoresize /></div>
                </div>
                <div v-if="reportCityPieOption" class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>城市机会分布</h5>
                    <p>岗位需求更集中的城市</p>
                  </div>
                  <div class="report-chart-box"><VChart class="chart" :option="reportCityPieOption" autoresize /></div>
                </div>
                <div v-if="reportIndustryPieOption" class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>重点岗位赛道</h5>
                    <p>当前值得持续跟踪的行业或岗位方向</p>
                  </div>
                  <div class="report-chart-box"><VChart class="chart" :option="reportIndustryPieOption" autoresize /></div>
                </div>
              </div>
            </section>

            <section v-if="educationDist.length || experienceDist.length" class="report-section">
              <div class="section-head"><Target :size="16" /><h4>岗位门槛分布</h4></div>
              <div class="insight-grid insight-grid-distribution">
                <div v-if="reportEducationBarOption" class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>学历要求分布</h5>
                    <p>不同学历要求对应的岗位数量</p>
                  </div>
                  <div class="report-chart-box"><VChart class="chart" :option="reportEducationBarOption" autoresize /></div>
                </div>
                <div v-if="reportExperienceRadarOption" class="chart-surface">
                  <div class="chart-surface-head">
                    <h5>经验要求重心</h5>
                    <p>不同经验阶段的市场吸纳强度</p>
                  </div>
                  <div class="report-chart-box"><VChart class="chart" :option="reportExperienceRadarOption" autoresize /></div>
                </div>
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
                  <p>目标：{{ item.market || '--' }}</p>
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
              <div class="section-head"><Sparkles :size="16" /><h4>建议清单</h4></div>
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

        <div v-else class="empty-state-card glass-panel">
          <EmptyState icon="search" title="选择一份报告" description="可从左侧私有报告或公开报告列表中选择，查看角色化分析详情。" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 18px;
  padding: 18px 20px;
  border-radius: 16px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-soft);
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
  gap: 8px;
}

.hero-copy h1,
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
  font-size: 0.9rem;
  line-height: 1.5;
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
  border: 1px solid var(--c-border-glass);
  border-radius: 13px;
}

.hero-note {
  padding: 10px 12px;
  background: var(--c-bg-surface);
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
  background: var(--c-bg-surface);
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
  background: var(--c-bg-surface);
}

.status-strip strong {
  display: block;
  margin-bottom: 4px;
}

.report-layout {
  display: block;
}

.section-panel {
  gap: 16px;
}

.report-library-panel,
.detail-panel,
.schedule-panel {
  border-color: var(--c-border-glass);
  background:
    radial-gradient(circle at top right, var(--c-accent-primary-glow), transparent 34%),
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.report-library-panel .workspace-panel-title,
.detail-panel .workspace-panel-title,
.schedule-panel .workspace-panel-title {
  color: var(--c-accent-primary);
}

.report-columns {
  display: grid;
  grid-template-columns: minmax(260px, 0.48fr) minmax(0, 1fr);
  align-items: start;
  gap: 18px;
}

.library-section {
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.public-section {
  background:
    radial-gradient(circle at top left, var(--c-accent-primary-glow), transparent 36%),
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.private-section {
  background:
    radial-gradient(circle at top right, var(--c-accent-primary-glow), transparent 34%),
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.section-subhead {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.section-subhead .workspace-subsection-title {
  color: var(--c-text-primary);
}

.section-subhead .workspace-subsection-count {
  color: var(--c-accent-primary);
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
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
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
  gap: 10px;
}

.report-row,
.schedule-row {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  border-bottom: none;
}

.report-row.light {
  align-items: stretch;
}

.report-row.actionable {
  cursor: pointer;
}

.report-row.actionable:hover .workspace-item-title {
  color: var(--c-accent-primary);
}

.row-main {
  min-width: 0;
}

.report-row.workspace-item-card {
  border-color: var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
  box-shadow:
    var(--shadow-card-soft),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.report-row.workspace-item-card::before {
  background:
    radial-gradient(circle at var(--card-mx) var(--card-my), var(--c-accent-primary-glow), transparent 34%),
    linear-gradient(126deg, rgba(255, 255, 255, 0.82), transparent 42%),
    repeating-linear-gradient(135deg, rgba(30, 64, 175, 0.012) 0 1px, transparent 1px 12px);
}

.report-row.workspace-item-card:hover,
.report-row.workspace-item-card:focus-visible {
  border-color: var(--c-border-glass-hover);
  box-shadow:
    var(--shadow-card-raised),
    0 0 0 1px var(--c-accent-primary-glow);
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
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.pill.subtle {
  background: var(--c-bg-surface);
}

.pill.active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.schedule-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px 14px;
  border-color: var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.schedule-summary strong {
  display: block;
  margin-top: 4px;
  color: var(--c-text-primary);
}

.schedule-summary code {
  color: var(--c-text-muted);
  white-space: nowrap;
}

.empty-state {
  padding: 16px;
  border-color: var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.empty-state.large {
  min-height: 132px;
  display: flex;
  align-items: center;
}

.detail-panel {
  position: static;
}

.report-detail {
  gap: 14px;
}

.detail-summary-card {
  padding: 16px;
  background:
    radial-gradient(circle at top right, var(--c-accent-primary-glow), transparent 36%),
    linear-gradient(135deg, var(--c-accent-primary-glow), transparent 48%),
    var(--c-bg-surface);
  border-color: var(--c-border-glass);
}

.detail-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--c-accent-primary);
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-meta-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
  border-color: var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.detail-meta-card strong {
  font-size: 15px;
  color: var(--c-text-primary);
}

.detail-section-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-color: var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
}

.schedule-row {
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background:
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface)),
    var(--c-bg-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.schedule-row:hover {
  border-color: var(--c-border-glass-hover);
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

/* Dark mode overrides — many panels here stack a semi-opaque light
   gradient on top of --c-bg-surface. Even after swapping the literals
   to tokens, the inset `rgba(255,255,255,0.7)` highlight and the
   warm-pink error/success banners still glow on a dark panel. Drop
   the inset highlight and swap the semantic banner backgrounds. */
[data-theme="dark"] .library-section,
[data-theme="dark"] .report-row.workspace-item-card,
[data-theme="dark"] .schedule-row {
  box-shadow: none;
}
[data-theme="dark"] .report-row.workspace-item-card {
  box-shadow: var(--shadow-card-soft);
}
[data-theme="dark"] .error-banner {
  background: rgba(178, 59, 46, 0.18);
  color: #ffb4a6;
}
[data-theme="dark"] .success-banner {
  background: rgba(30, 138, 91, 0.18);
  color: #b6e8c8;
}

@media (max-width: 1180px) {
  .workspace-hero,
  .report-columns {
    grid-template-columns: 1fr;
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

  .detail-section-list {
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

/* Styles from main (charts + role-aware sidebar layout) */
.master-detail-layout {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 24px;
  align-items: start;
}
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.scrollable-list {
  max-height: 400px;
  overflow-y: auto;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.scrollable-list-small {
  max-height: 250px;
  overflow-y: auto;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 620px;
  border-radius: 24px;
  color: var(--c-text-muted);
}
.empty-state-wrapper {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, .35);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 24px;
  z-index: 10;
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.mt-4 { margin-top: 16px; }
.panel-header, .title-row, .inline-actions, .detail-header, .section-head, .comparison-head, .role-meta-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.panel-header, .detail-header, .comparison-head, .role-meta-top { justify-content: space-between; }
.title-row h2, .report-detail h3, .report-detail h4, .role-meta-card h3 { margin: 0; }
.card-list, .form-grid, .report-detail, .action-list {
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
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
  min-width: 0;
  transition: all .2s;
}
.list-item:hover, .entry-card:hover {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(255, 255, 255, 0.9);
}
.list-item.active, .entry-card.active {
  border-color: rgba(30, 117, 255, 0.6);
  background: rgba(30, 117, 255, 0.08);
  box-shadow: 0 0 16px rgba(30, 117, 255, 0.08);
}
.list-main, .detail-main { min-width: 0; }
.list-item p, .report-detail p, .job-sample p, .role-meta-text, .entry-card p, .hint-box p {
  margin: 0;
  color: var(--c-text-secondary);
}
.clickable, .entry-card { cursor: pointer; }
.pill.good {
  background: rgba(34, 197, 94, .15);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, .3);
}
.compact-input { width: 110px; padding: 6px 10px; height: 36px; border-radius: 8px; }
.detail-card { grid-column: 1 / -1; }
.summary-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.task-strip { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.report-section { display: grid; gap: 12px; }
.insight-grid { display: grid; gap: 14px; }
.insight-grid-salary { grid-template-columns: minmax(0, 1.8fr) 280px; }
.insight-grid-structure, .insight-grid-distribution, .comparison-list, .job-sample-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.comparison-list, .job-sample-list, .entry-grid { display: grid; gap: 14px; }
.entry-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.entry-card {
  text-align: left;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.entry-card strong { display: block; margin-bottom: 8px; }
.entry-card span { font-size: 12px; color: var(--c-text-muted); }
.chart-surface {
  padding: 16px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: grid;
  gap: 14px;
}
.chart-surface-head h5, .chart-surface-head p { margin: 0; }
.chart-surface-head p { color: var(--c-text-secondary); }
.report-chart-box {
  height: 320px;
  overflow: hidden;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(30, 117, 255, .08), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, .7), rgba(244, 248, 255, .8));
}
.report-chart-box-wide { height: 340px; }
.report-chart-box-tall { height: 390px; }
.chart { width: 100%; height: 100%; }
.metric-stack { display: grid; gap: 12px; }
.comparison-item, .action-item, .job-sample {
  padding: 16px;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.comparison-badge {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(242, 244, 250, 0.96);
}
.comparison-good { border-color: rgba(34, 197, 94, .35); background: rgba(34, 197, 94, .08); }
.comparison-warn { border-color: rgba(245, 158, 11, .35); background: rgba(245, 158, 11, .08); }
.comparison-risk { border-color: rgba(239, 68, 68, .35); background: rgba(239, 68, 68, .08); }
.bullet-list { margin: 0; padding-left: 20px; color: var(--c-text-secondary); }
.bullet-list li { margin-bottom: 8px; }
.action-item { display: grid; grid-template-columns: 40px 1fr; gap: 12px; }
.priority {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(30, 117, 255, .12);
  color: var(--c-accent-primary);
  font-weight: 800;
}
.delete-icon { cursor: pointer; color: var(--c-text-muted); }
.template-copy { font-size: 13px; line-height: 1.7; }
.role-meta-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.mini-label { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 8px; }
.summary-box {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.summary-box span { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 8px; }
.summary-box strong { font-size: 18px; color: var(--c-text-primary); }
.summary-box-mini {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-box-mini span { font-size: 11px; color: var(--c-text-muted); }
.summary-box-mini strong { font-size: 14px; color: var(--c-text-primary); }
.trend-stat {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.trend-stat span { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 6px; }
.trend-stat strong { font-size: 18px; color: var(--c-text-primary); }
.hint-box {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.hero-badges { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(193, 198, 215, 0.5);
  font-size: 12px;
  color: var(--c-text-secondary);
}

@media (max-width: 1100px) {
  .master-detail-layout {
    grid-template-columns: 1fr;
  }
  .summary-strip,
  .task-strip,
  .insight-grid-salary,
  .insight-grid-structure,
  .insight-grid-distribution,
  .comparison-list,
  .job-sample-list,
  .entry-grid {
    grid-template-columns: 1fr;
  }
  .scrollable-list { max-height: none; }
}
</style>
