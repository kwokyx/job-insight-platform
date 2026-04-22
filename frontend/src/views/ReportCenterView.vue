<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ReportDetailPanel from '../components/report/ReportDetailPanel.vue'
import ReportSchedulePanel from '../components/report/ReportSchedulePanel.vue'
import ReportPublicationPanel from '../components/report/ReportPublicationPanel.vue'
import {
  createReport,
  deleteReport,
  exportReportFormat,
  fetchPublicReports,
  fetchReportCenterMeta,
  fetchReportDownloadMeta,
  fetchReportDrill,
  fetchReportReadiness,
  fetchReports,
  fetchReportSchedules,
  fetchReportStatus,
  invalidateApiCache,
  openReportPdf,
  submitReportReview
} from '../api'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { mapErrorMessage } from '../utils/errorMap'
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  FileText,
  Globe,
  LockKeyhole,
  PanelLeftClose,
  PanelLeftOpen,
  RefreshCw,
  Send,
  Sparkles,
  Trash2
} from 'lucide-vue-next'

const EMPTY_META = {
  roleType: null,
  roleLabel: '',
  moduleTitle: '',
  moduleDescription: '',
  defaultReportType: '',
  defaultReportName: '',
  privateListScope: '',
  publicListScope: '',
  reportTypes: []
}

const ROLE_TYPE_LABEL = {
  0: '学生',
  1: '管理员',
  2: '教师'
}

const REPORT_FAILURE_REASON_LABEL = {
  INPUT_MISSING: '前置资料缺失',
  DATA_SOURCE_UNAVAILABLE: '数据源不可用',
  EXPORT_FAILED: '导出失败',
  MODEL_UNAVAILABLE: '模型不可用',
  SYSTEM_ERROR: '系统异常'
}

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()

const readiness = ref({
  ready: false,
  missing: [],
  cta: null,
  source: 'backend',
  loadFailed: false,
  message: ''
})
const readinessLoading = ref(false)

const publicReports = ref([])
const privateReports = ref([])
const schedules = ref([])
const selectedReport = ref(null)
const selectedTask = ref(null)
const activeSection = ref('generate')
const listCollapsed = ref(false)
const reportMeta = ref({ ...EMPTY_META })
const loading = ref(true)
const detailLoading = ref(false)
const actionLoading = ref(false)
const error = ref('')
const success = ref('')
const exportFormat = ref('pdf')
const autoReportName = ref('')

const generateForm = ref({
  reportType: '',
  reportName: ''
})

const currentRoleType = computed(() => authStore.user?.roleType ?? 0)
const isAdmin = computed(() => currentRoleType.value === 1)
const currentReportTypes = computed(() => (
  Array.isArray(reportMeta.value?.reportTypes) ? reportMeta.value.reportTypes : []
))
const currentReportTypeConfig = computed(
  () => currentReportTypes.value.find((item) => item.code === generateForm.value.reportType) || currentReportTypes.value[0] || null
)
const selectedReportId = computed(() => selectedReport.value?.reportId || selectedReport.value?.id || null)
const activeMajor = computed(() => {
  const raw = Array.isArray(route.query.major) ? route.query.major[0] : route.query.major
  return typeof raw === 'string' ? raw.trim() : ''
})

const sidebarGroups = computed(() => {
  const libraryItems = [
    { key: 'private', label: isAdmin.value ? '全站私有' : '我的报告', count: privateReports.value.length },
    { key: 'public', label: '公开报告', count: publicReports.value.length }
  ]
  if (isAdmin.value) libraryItems.push({ key: 'review', label: '待审核' })

  return [
    { key: 'gen', label: '生成', items: [{ key: 'generate', label: '生成报告' }] },
    { key: 'lib', label: '报告库', items: libraryItems },
    { key: 'tool', label: '工具', items: [{ key: 'schedule', label: '调度计划', count: schedules.value.length }] }
  ]
})

const allSectionKeys = computed(() => sidebarGroups.value.flatMap((group) => group.items.map((item) => item.key)))

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) return []
  return [
    { label: '任务 ID', value: selectedTask.value.taskId ?? '--' },
    { label: '任务状态', value: taskStatusLabel(selectedTask.value.status) },
    { label: '生成进度', value: formatProgress(selectedTask.value.progress) },
    { label: '开始时间', value: formatDateTime(selectedTask.value.startedAt) },
    { label: '完成时间', value: formatDateTime(selectedTask.value.completedAt) }
  ]
})

const taskFailureInfo = computed(() => {
  if (selectedTask.value?.status !== 'FAILED') return null
  const failureCode = pickFirstText(selectedTask.value?.failureReasonCode, selectedTask.value?.failureReason)
  return {
    code: failureCode,
    label: REPORT_FAILURE_REASON_LABEL[failureCode] || failureCode || '生成失败',
    detail: pickFirstText(selectedTask.value?.errorMessage)
  }
})

const taskResultSummaryRows = computed(() => formatResultSummaryRows(selectedTask.value?.resultSummary))

watch(allSectionKeys, (keys) => {
  if (!keys.includes(activeSection.value)) activeSection.value = keys[0] || 'generate'
}, { immediate: true })

watch(activeSection, () => {
  selectedReport.value = null
  listCollapsed.value = false
})

const listFabAttention = ref(false)
let fabAttentionTimer = null
let successTimer = null

watch(selectedReport, (now, prev) => {
  if (now && !prev) {
    listCollapsed.value = true
    listFabAttention.value = true
    toast.show('列表已收起，点左上角「展开列表」可随时回到列表', 'info', 2600)
    if (fabAttentionTimer) clearTimeout(fabAttentionTimer)
    fabAttentionTimer = setTimeout(() => { listFabAttention.value = false }, 1000)
  }
  if (!now) listCollapsed.value = false
})

watch(() => generateForm.value.reportType, (nextType, prevType) => {
  if (!nextType) return
  const nextConfig = currentReportTypes.value.find((item) => item.code === nextType)
  const prevConfig = currentReportTypes.value.find((item) => item.code === prevType)
  const currentName = generateForm.value.reportName?.trim() || ''
  if (!currentName || currentName === autoReportName.value || currentName === prevConfig?.defaultName) {
    generateForm.value.reportName = nextConfig?.defaultName || reportMeta.value.defaultReportName || ''
    autoReportName.value = generateForm.value.reportName
  }
})

watch(currentRoleType, async (next, prev) => {
  if (next === prev || !authStore.token) return
  selectedReport.value = null
  selectedTask.value = null
  await loadPage()
  await loadReadiness()
})

watch(activeMajor, () => {
  if (!authStore.token || isAdmin.value) return
  loadReadiness()
})

function pickFirstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function applyMeta(meta) {
  const reportTypes = Array.isArray(meta?.reportTypes) ? meta.reportTypes : []
  const nextMeta = {
    ...EMPTY_META,
    ...(meta || {}),
    reportTypes
  }
  reportMeta.value = nextMeta

  const nextType = reportTypes.some((item) => item.code === generateForm.value.reportType)
    ? generateForm.value.reportType
    : nextMeta.defaultReportType || reportTypes[0]?.code || ''
  const nextDefaultName = reportTypes.find((item) => item.code === nextType)?.defaultName || nextMeta.defaultReportName || ''

  generateForm.value.reportType = nextType
  if (!generateForm.value.reportName || generateForm.value.reportName === autoReportName.value) {
    generateForm.value.reportName = nextDefaultName
  }
  autoReportName.value = nextDefaultName
}

function setPageError(input) {
  error.value = typeof input === 'string' ? input : mapErrorMessage(input)
}

function flashSuccess(message) {
  success.value = message
  if (successTimer) clearTimeout(successTimer)
  successTimer = setTimeout(() => { success.value = '' }, 3000)
}

function reportTypeLabel(code) {
  return currentReportTypes.value.find((item) => item.code === code)?.label || code || '--'
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function formatProgress(value) {
  const progress = Number(value)
  return Number.isFinite(progress) ? `${progress}%` : '--'
}

function taskStatusLabel(status) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '生成中'
  if (status === 'PENDING') return '排队中'
  return status || '--'
}

function normalizeBackendReadiness(state) {
  const primaryAction = state?.primaryAction || {}
  const assistant = state?.assistant || {}
  const routeTarget = pickFirstText(primaryAction?.route, primaryAction?.path)
  return {
    ready: Boolean(state?.ready),
    missing: Array.isArray(state?.missingRequirements)
      ? state.missingRequirements
        .map((item) => pickFirstText(item?.title, item?.label, item?.detail))
        .filter(Boolean)
      : [],
    cta: state?.ready
      ? null
      : {
          label: pickFirstText(primaryAction?.label, '去完成前置准备'),
          route: routeTarget,
          missingHint: pickFirstText(primaryAction?.detail, assistant?.message, '生成报告前需要先完成前置准备。')
        },
    source: 'backend',
    loadFailed: false,
    message: ''
  }
}

function parseStructuredSummary(value) {
  if (!value) return null
  if (typeof value === 'object') return value
  if (typeof value !== 'string') return value
  const trimmed = value.trim()
  if (!trimmed) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    return trimmed
  }
}

function summaryLabel(key) {
  if (key === 'reportId') return '报告 ID'
  if (key === 'reportType') return '报告类型'
  if (key === 'targetRoleType') return '目标角色'
  if (key === 'comparisonCount') return '对比项数'
  if (key === 'recommendationCount') return '建议条数'
  return key
}

function summaryValue(key, value) {
  if (value === undefined || value === null || value === '') return '--'
  if (key === 'reportType') return reportTypeLabel(String(value))
  if (key === 'targetRoleType') return ROLE_TYPE_LABEL[Number(value)] || String(value)
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatResultSummaryRows(raw) {
  const parsed = parseStructuredSummary(raw)
  if (!parsed) return []
  if (typeof parsed === 'string') {
    return [{ label: '结果摘要', value: parsed }]
  }
  if (Array.isArray(parsed)) {
    return parsed.map((item, index) => ({
      label: `结果 ${index + 1}`,
      value: typeof item === 'object' ? JSON.stringify(item) : String(item)
    }))
  }
  return Object.entries(parsed).map(([key, value]) => ({
    label: summaryLabel(key),
    value: summaryValue(key, value)
  }))
}

async function loadPage() {
  if (!authStore.token) return
  loading.value = true
  error.value = ''

  try {
    const [publicResult, metaResult, reportsResult, schedulesResult] = await Promise.allSettled([
      fetchPublicReports({ page: 1, pageSize: 6 }),
      fetchReportCenterMeta(authStore.token),
      fetchReports(authStore.token, { page: 1, pageSize: 10 }),
      fetchReportSchedules(authStore.token)
    ])

    publicReports.value = publicResult.status === 'fulfilled' ? (publicResult.value.data || []) : []
    privateReports.value = reportsResult.status === 'fulfilled' ? (reportsResult.value.data || []) : []
    schedules.value = schedulesResult.status === 'fulfilled' ? (schedulesResult.value || []) : []
    applyMeta(metaResult.status === 'fulfilled' ? metaResult.value : null)

    const failedParts = []
    if (publicResult.status === 'rejected') failedParts.push('公开报告')
    if (metaResult.status === 'rejected') failedParts.push('中心配置')
    if (reportsResult.status === 'rejected') failedParts.push('私有报告')
    if (schedulesResult.status === 'rejected') failedParts.push('调度计划')
    if (failedParts.length) {
      error.value = `${failedParts.join('、')}加载失败，请刷新后重试。`
    }
  } catch (e) {
    setPageError(e)
  } finally {
    loading.value = false
  }
}

async function loadReadiness() {
  if (!authStore.token) return
  if (isAdmin.value) {
    readiness.value = {
      ready: true,
      missing: [],
      cta: null,
      source: 'backend',
      loadFailed: false,
      message: ''
    }
    return
  }

  readinessLoading.value = true
  try {
    const params = activeMajor.value ? { major: activeMajor.value } : {}
    const backendState = await fetchReportReadiness(authStore.token, params)
    readiness.value = normalizeBackendReadiness(backendState)
  } catch (e) {
    readiness.value = {
      ready: false,
      missing: [],
      cta: null,
      source: 'backend',
      loadFailed: true,
      message: mapErrorMessage(e)
    }
    setPageError(e)
  } finally {
    readinessLoading.value = false
  }
}

function goToPrerequisite() {
  const target = readiness.value?.cta?.route
  if (!target) return
  router.push(target === '/report-center' ? '/reports' : target)
}

async function reloadSchedules() {
  try {
    schedules.value = await fetchReportSchedules(authStore.token)
  } catch (e) {
    setPageError(e)
  }
}

async function pollTask(taskId) {
  let attempts = 0
  while (attempts < 20) {
    attempts += 1
    selectedTask.value = await fetchReportStatus(authStore.token, taskId)
    if (['SUCCESS', 'FAILED'].includes(selectedTask.value?.status)) break
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
}

async function handleCreateReport() {
  if (!generateForm.value.reportType) {
    error.value = '报告类型配置尚未加载完成，请刷新后重试。'
    return
  }
  if (!isAdmin.value) {
    if (readinessLoading.value) {
      error.value = '前置条件检查中，请稍候。'
      return
    }
    if (readiness.value.loadFailed) {
      error.value = '报告前置检查加载失败，请刷新后重试。'
      return
    }
    if (!readiness.value.ready) {
      error.value = readiness.value?.cta?.missingHint || '当前账号尚未完成前置准备，无法生成报告。'
      return
    }
  }

  actionLoading.value = true
  error.value = ''
  success.value = ''
  selectedTask.value = null

  try {
    const reportName = (generateForm.value.reportName || '').trim() || currentReportTypeConfig.value?.defaultName || reportMeta.value.defaultReportName
    const params = activeMajor.value ? { major: activeMajor.value } : {}
    const result = await createReport(authStore.token, {
      reportName,
      reportType: generateForm.value.reportType,
      params
    })

    if (result.taskId) {
      flashSuccess(`报告任务已提交，任务号 ${result.taskId}`)
      await pollTask(result.taskId)
      invalidateApiCache('/reports')
      await loadPage()
    } else {
      flashSuccess('报告请求已提交')
    }
  } catch (e) {
    setPageError(e)
  } finally {
    actionLoading.value = false
  }
}

async function openReportDetail(report) {
  if (!report?.id || detailLoading.value) return
  error.value = ''
  detailLoading.value = true
  try {
    selectedReport.value = await fetchReportDrill(authStore.token, report.id)
  } catch (e) {
    setPageError(e)
  } finally {
    detailLoading.value = false
  }
}

async function handleFormatExport(payload) {
  if (!payload?.id) return
  try {
    const meta = await fetchReportDownloadMeta(authStore.token, payload.id)
    const views = meta?.viewCount ?? meta?.downloadCount
    await exportReportFormat(authStore.token, payload.id, payload.reportName, exportFormat.value)
    const suffix = Number.isFinite(Number(views)) ? `，累计查看 ${views} 次` : ''
    flashSuccess(`报告已导出为 ${exportFormat.value.toUpperCase()}${suffix}`)
  } catch (e) {
    setPageError(e)
  }
}

async function handlePreviewPdf(id) {
  if (!id) return
  error.value = ''
  try {
    await fetchReportDownloadMeta(authStore.token, id)
    await openReportPdf(authStore.token, id)
  } catch (e) {
    setPageError(e)
  }
}

const deleteDialog = ref({ open: false, id: null, name: '', loading: false })

function requestDeleteReport(report, event) {
  if (event) event.stopPropagation()
  deleteDialog.value = {
    open: true,
    id: report.id,
    name: report.reportName || `报告 #${report.id}`,
    loading: false
  }
}

function cancelDeleteReport() {
  if (deleteDialog.value.loading) return
  deleteDialog.value.open = false
}

async function confirmDeleteReport() {
  const { id } = deleteDialog.value
  if (!id) return
  deleteDialog.value.loading = true
  error.value = ''
  try {
    await deleteReport(authStore.token, id)
    flashSuccess('报告已删除')
    if (selectedReportId.value === id) {
      selectedReport.value = null
    }
    deleteDialog.value.open = false
    invalidateApiCache('/reports')
    await loadPage()
  } catch (e) {
    setPageError(e)
  } finally {
    deleteDialog.value.loading = false
  }
}

async function handleSubmitReview(id, event) {
  if (event) event.stopPropagation()
  try {
    await submitReportReview(authStore.token, id)
    flashSuccess('已提交审核')
    await loadPage()
  } catch (e) {
    setPageError(e)
  }
}

onMounted(async () => {
  await loadPage()
  await loadReadiness()

  if (route.query.autogen === '1') {
    if (!isAdmin.value && readiness.value.ready) {
      await handleCreateReport()
    }
    const nextQuery = { ...route.query }
    delete nextQuery.autogen
    await router.replace({ path: route.path, query: nextQuery })
  }
})

onBeforeUnmount(() => {
  if (fabAttentionTimer) clearTimeout(fabAttentionTimer)
  if (successTimer) clearTimeout(successTimer)
})
</script>

<template>
  <div class="report-page page-animate">
    <div class="report-layout">
      <aside class="report-sidebar" aria-label="报告中心导航">
        <div class="report-sidebar-inner">
          <h1 class="report-hero-title">{{ reportMeta.moduleTitle || '报告分析' }}</h1>
          <nav class="report-nav" aria-label="报告中心章节导航">
            <div
              v-for="group in sidebarGroups"
              :key="group.key"
              class="report-nav-group"
            >
              <div class="report-nav-group-label">{{ group.label }}</div>
              <ul class="report-nav-list" role="tablist">
                <li v-for="item in group.items" :key="item.key">
                  <button
                    type="button"
                    class="report-nav-link"
                    :class="{ 'is-active': activeSection === item.key }"
                    role="tab"
                    :aria-selected="activeSection === item.key"
                    @click="activeSection = item.key"
                  >
                    <span class="report-nav-link-label">{{ item.label }}</span>
                    <span v-if="typeof item.count === 'number'" class="report-nav-link-count">{{ item.count }}</span>
                  </button>
                </li>
              </ul>
            </div>
          </nav>
        </div>
      </aside>

      <div class="report-content">
        <div v-if="error" class="status-banner error-banner">{{ error }}</div>
        <div v-if="success" class="status-banner success-banner">{{ success }}</div>

        <Transition name="report-section" mode="out-in">
          <section v-if="activeSection === 'generate'" key="generate" class="report-main">
            <article class="surface section-panel workspace-module-panel">
              <div class="panel-head">
                <h2 class="workspace-panel-title inline-icon"><Sparkles :size="15" /> 生成报告</h2>
                <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" />刷新</GlowButton>
              </div>

              <p class="gen-lead">
                <strong>{{ reportMeta.moduleDescription || '报告类型、默认名称与列表范围均以真实接口返回为准。' }}</strong>
                <span v-if="currentReportTypeConfig?.templateDescription">{{ currentReportTypeConfig.templateDescription }}</span>
                <span v-if="activeMajor">当前按专业视角检查并生成：{{ activeMajor }}</span>
              </p>

              <div v-if="readinessLoading && !isAdmin" class="inline-hint">正在检查生成前置条件...</div>

              <div
                v-else-if="!isAdmin && readiness.loadFailed"
                class="readiness-card readiness-error"
              >
                <div class="readiness-icon">
                  <AlertTriangle :size="22" />
                </div>
                <div class="readiness-body">
                  <h3>前置检查加载失败</h3>
                  <p>{{ readiness.message || '暂时无法确认当前账号是否满足生成条件。' }}</p>
                </div>
                <GlowButton variant="primary" @click="loadReadiness">
                  <RefreshCw :size="14" />
                  重试
                </GlowButton>
              </div>

              <div
                v-else-if="!isAdmin && !readiness.ready"
                class="readiness-card readiness-blocked"
              >
                <div class="readiness-icon">
                  <AlertTriangle :size="22" />
                </div>
                <div class="readiness-body">
                  <h3>还缺前置准备</h3>
                  <p>{{ readiness.cta?.missingHint || '生成报告前需要先完成前置准备。' }}</p>
                  <ul v-if="readiness.missing.length" class="readiness-missing">
                    <li v-for="item in readiness.missing" :key="item">{{ item }}</li>
                  </ul>
                </div>
                <GlowButton v-if="readiness.cta?.route" variant="primary" @click="goToPrerequisite">
                  {{ readiness.cta?.label || '去完成前置准备' }}
                  <ArrowRight :size="14" />
                </GlowButton>
              </div>

              <div v-else-if="!currentReportTypes.length" class="empty-state-wrapper">
                <EmptyState icon="file" title="报告配置暂未返回" description="中心配置加载失败或尚未返回，请刷新后重试。" />
              </div>

              <div v-else class="generate-box">
                <div v-if="!isAdmin" class="readiness-pill">
                  <CheckCircle2 :size="14" />
                  <span>已确认前置准备完成</span>
                </div>

                <div class="form-grid">
                  <label class="field">
                    <span class="field-label">报告类型</span>
                    <select v-model="generateForm.reportType" class="glass-input">
                      <option v-for="item in currentReportTypes" :key="item.code" :value="item.code">{{ item.label }}</option>
                    </select>
                    <span class="field-hint">{{ currentReportTypeConfig?.description || '报告类型来自 /reports/meta。' }}</span>
                  </label>

                  <label class="field">
                    <span class="field-label">报告名称</span>
                    <input
                      v-model="generateForm.reportName"
                      class="glass-input"
                      :placeholder="currentReportTypeConfig?.defaultName || reportMeta.defaultReportName || '输入报告名称'"
                      @keydown.enter="handleCreateReport"
                    />
                    <span class="field-hint">
                      {{ currentReportTypeConfig?.entryHint || `留空将使用默认名「${currentReportTypeConfig?.defaultName || reportMeta.defaultReportName || '分析报告'}」` }}
                    </span>
                  </label>
                </div>

                <div class="generate-actions">
                  <span v-if="activeMajor" class="pill scope-pill">专业视角：{{ activeMajor }}</span>
                  <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">
                    <FileText :size="14" />
                    生成报告
                  </GlowButton>
                </div>
              </div>

              <div v-if="selectedTask" class="task-panel">
                <div class="task-strip">
                  <div v-for="item in latestTaskSummary" :key="item.label" class="summary-box-mini">
                    <span>{{ item.label }}</span>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>

                <div v-if="taskFailureInfo" class="task-note is-error">
                  <strong>失败原因：{{ taskFailureInfo.label }}</strong>
                  <p v-if="taskFailureInfo.detail">{{ taskFailureInfo.detail }}</p>
                </div>

                <div v-else-if="taskResultSummaryRows.length" class="task-note">
                  <strong>结果摘要</strong>
                  <div class="task-summary-list">
                    <div v-for="item in taskResultSummaryRows" :key="item.label" class="task-summary-item">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.value }}</strong>
                    </div>
                  </div>
                </div>
              </div>
            </article>
          </section>

          <section
            v-else-if="['private','public','review'].includes(activeSection)"
            :key="activeSection"
            class="report-main library-layout"
            :class="{ 'list-collapsed': listCollapsed }"
          >
            <article class="surface section-panel workspace-module-panel list-panel">
              <div class="panel-head">
                <h2 class="workspace-panel-title inline-icon">
                  <LockKeyhole v-if="activeSection === 'private'" :size="15" />
                  <Globe v-else-if="activeSection === 'public'" :size="15" />
                  <Sparkles v-else :size="15" />
                  {{ activeSection === 'private' ? (isAdmin ? '全站私有报告' : '我的报告') : activeSection === 'public' ? '公开报告' : '待审核' }}
                </h2>
                <div class="inline-actions">
                  <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" /></GlowButton>
                  <button
                    type="button"
                    class="list-collapse-btn"
                    title="收起列表，腾出更多空间给报告详情"
                    @click="listCollapsed = true"
                  >
                    <PanelLeftClose :size="14" />
                    <span>收起列表</span>
                  </button>
                </div>
              </div>

              <p v-if="activeSection === 'private' && reportMeta.privateListScope" class="section-copy">{{ reportMeta.privateListScope }}</p>
              <p v-if="activeSection === 'public' && reportMeta.publicListScope" class="section-copy">{{ reportMeta.publicListScope }}</p>

              <div v-if="activeSection === 'private'" class="card-list scrollable-list">
                <div
                  v-for="report in privateReports"
                  :key="report.id"
                  class="list-item clickable"
                  :class="{ active: selectedReportId === report.id }"
                  @click="openReportDetail(report)"
                >
                  <div class="list-main">
                    <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                    <p>{{ reportTypeLabel(report.reportType) }} · {{ formatDateTime(report.generatedAt || report.updatedAt || report.createdAt) }}</p>
                    <p class="muted">{{ report.reportLifecycle?.stateLabel || (report.isPublic ? '已发布' : '草稿') }}</p>
                  </div>
                  <div class="inline-actions">
                    <button
                      v-if="!isAdmin && ['DRAFT','REJECTED'].includes(report.reportLifecycle?.state)"
                      class="icon-btn"
                      title="提交审核"
                      @click="handleSubmitReview(report.id, $event)"
                    >
                      <Send :size="14" />
                    </button>
                    <Trash2 class="delete-icon" :size="16" @click="requestDeleteReport(report, $event)" />
                  </div>
                </div>
                <div v-if="loading" class="skeleton-list mt-4">
                  <SkeletonCard type="list" :lines="4" />
                </div>
                <div v-if="!privateReports.length && !loading" class="empty-state-wrapper mt-4">
                  <EmptyState icon="file" title="还没有生成私有报告" description="先到「生成报告」生成第一份报告。" />
                </div>
              </div>

              <div v-else-if="activeSection === 'public'" class="card-list scrollable-list">
                <div
                  v-for="report in publicReports"
                  :key="report.id"
                  class="list-item clickable"
                  :class="{ active: selectedReportId === report.id }"
                  @click="openReportDetail(report)"
                >
                  <div class="list-main">
                    <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                    <p>{{ reportTypeLabel(report.reportType) }} · {{ formatDateTime(report.generatedAt || report.updatedAt || report.createdAt) }}</p>
                  </div>
                  <span class="pill"><Globe :size="13" />公开</span>
                </div>
                <div v-if="loading" class="skeleton-list mt-4">
                  <SkeletonCard type="list" :lines="3" />
                </div>
                <div v-if="!publicReports.length && !loading" class="empty-state-wrapper mt-4">
                  <EmptyState icon="file" title="暂无公开报告" description="当前还没有可以直接浏览的公开报告。" />
                </div>
              </div>

              <div v-else-if="activeSection === 'review' && isAdmin">
                <ReportPublicationPanel
                  embedded
                  :token="authStore.token"
                  :report-type-label="reportTypeLabel"
                  @select="openReportDetail"
                  @error="setPageError"
                  @success="flashSuccess"
                />
              </div>
            </article>

            <div class="detail-col">
              <Transition name="fab-fade">
                <button
                  v-if="listCollapsed"
                  type="button"
                  class="detail-expand-fab"
                  :class="{ 'is-attention': listFabAttention }"
                  title="展开列表"
                  @click="listCollapsed = false"
                >
                  <PanelLeftOpen :size="14" />
                  <span>展开列表</span>
                </button>
              </Transition>
              <div v-if="detailLoading" class="loading-overlay">
                <div class="detail-skel">
                  <SkeletonCard type="chart" />
                  <SkeletonCard type="list" :lines="4" />
                  <SkeletonCard type="card" :lines="3" />
                </div>
              </div>
              <ReportDetailPanel
                v-if="selectedReport"
                :report="selectedReport"
                v-model:export-format="exportFormat"
                :report-type-label="reportTypeLabel"
                @preview="handlePreviewPdf"
                @export="handleFormatExport"
              />
              <div v-else class="empty-state-card glass-panel">
                <EmptyState icon="search" title="选择一份报告" description="从左侧列表或待审核队列中选择一份报告，查看分析详情。" />
              </div>
            </div>
          </section>

          <section v-else-if="activeSection === 'schedule'" key="schedule" class="report-main">
            <ReportSchedulePanel
              :schedules="schedules"
              :token="authStore.token"
              :report-types="currentReportTypes"
              :default-report-type="reportMeta.defaultReportType || currentReportTypes[0]?.code || ''"
              :report-type-label="reportTypeLabel"
              @refresh="reloadSchedules"
              @error="setPageError"
              @success="flashSuccess"
            />
          </section>
        </Transition>
      </div>
    </div>

    <ConfirmDialog
      :open="deleteDialog.open"
      title="删除报告"
      description="删除后报告及其分析数据将无法恢复，请确认是否继续。"
      :detail="deleteDialog.name"
      confirm-text="删除"
      cancel-text="取消"
      variant="danger"
      :loading="deleteDialog.loading"
      @confirm="confirmDeleteReport"
      @cancel="cancelDeleteReport"
      @update:open="(v) => (deleteDialog.open = v)"
    />
  </div>
</template>

<style scoped>
/* ---------- 两栏布局：侧栏目录 + 主内容（仿智能推荐） ---------- */
.report-layout {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}
.report-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
  min-height: calc(100vh - 120px);
}
.report-main { min-height: 560px; }

.report-sidebar {
  position: sticky;
  top: 24px;
  align-self: start;
  min-width: 0;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  scrollbar-width: none;
}
.report-sidebar::-webkit-scrollbar { display: none; }

.report-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.report-hero-title {
  margin: 0 0 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-border-glass);
  font-family: var(--font-serif);
  font-size: clamp(20px, 1.8vw, 24px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.report-nav { display: flex; flex-direction: column; gap: 18px; }
.report-nav-group { display: flex; flex-direction: column; gap: 4px; }
.report-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.report-nav-list {
  display: flex; flex-direction: column; gap: 2px;
  margin: 0; padding: 0; list-style: none;
}
.report-nav-link {
  position: relative;
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%;
  padding: 7px 10px 7px 12px;
  border: none; border-radius: 6px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px; font-weight: 400; line-height: 1.4;
  text-align: left; cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.report-nav-link-label {
  min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.report-nav-link-count {
  flex-shrink: 0;
  min-width: 20px; padding: 0 6px;
  font-size: 11px; line-height: 18px; text-align: center;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
}
.report-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.report-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.report-nav-link.is-active .report-nav-link-count {
  background: rgba(30, 117, 255, 0.16);
  color: var(--c-accent-primary);
}
.report-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0; top: 6px; bottom: 6px;
  width: 2px; border-radius: 2px;
  background: var(--c-accent-primary);
}
.report-nav-link:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

/* ---------- 主内容 ---------- */
.report-main { display: flex; flex-direction: column; gap: 20px; }

.report-section-enter-active,
.report-section-leave-active {
  transition:
    opacity 220ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 280ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 280ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform, filter;
  transform-origin: top left;
}

.report-section-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
  filter: blur(10px);
}

.report-section-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.992);
  filter: blur(8px);
}

.report-section-enter-to,
.report-section-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

.status-banner { padding: 12px 16px; border-radius: 14px; font-size: 13px; }
.error-banner { color: #b91c1c; background: rgba(254, 226, 226, 0.84); }
.success-banner { color: #166534; background: rgba(220, 252, 231, 0.84); }
:global([data-theme="dark"]) .error-banner { background: rgba(178, 59, 46, 0.18); color: #ffb4a6; }
:global([data-theme="dark"]) .success-banner { background: rgba(30, 138, 91, 0.18); color: #b6e8c8; }

/* 报告库：列表 + 详情 两列。折叠时列宽动画到 0，内容同步淡出 */
.library-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  transition: grid-template-columns 0.28s cubic-bezier(0.22, 0.61, 0.36, 1),
              gap 0.28s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.library-layout.list-collapsed {
  grid-template-columns: 0px minmax(0, 1fr);
  gap: 0;
}
.list-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  overflow: hidden;
  transform-origin: left center;
  transition: opacity 0.18s ease, transform 0.24s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.library-layout.list-collapsed .list-panel {
  opacity: 0;
  transform: translateX(-12px);
  pointer-events: none;
}
.detail-col { position: relative; min-width: 0; min-height: 480px; }

/* 「展开列表」按钮淡入 */
.fab-fade-enter-active,
.fab-fade-leave-active { transition: opacity 0.18s ease, transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1); }
.fab-fade-enter-from { opacity: 0; transform: translateY(-4px); }
.fab-fade-leave-to { opacity: 0; transform: translateY(-4px); }

/* 列表栏头部的「收起列表」按钮，对称于详情栏的「展开列表」 */
.list-collapse-btn,
.detail-expand-fab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px 7px 11px;
  border-radius: 999px;
  border: 1px solid rgba(30, 117, 255, 0.28);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease, transform 0.1s ease;
}
.list-collapse-btn:hover,
.detail-expand-fab:hover {
  background: rgba(30, 117, 255, 0.14);
  border-color: rgba(30, 117, 255, 0.5);
  box-shadow: 0 0 0 3px rgba(30, 117, 255, 0.08);
}
.list-collapse-btn:active,
.detail-expand-fab:active { transform: translateY(1px); }

/* 详情区那个展开按钮再给点外边距，保证视觉上浮 */
.detail-expand-fab { margin-bottom: 14px; }

/* 自动折叠后短暂的 attention 动效：呼吸式光晕 + 轻微缩放，三个循环后停止 */
.detail-expand-fab.is-attention {
  animation: fab-attention 0.9s ease-in-out 1;
}
@keyframes fab-attention {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(30, 117, 255, 0.08);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(30, 117, 255, 0.22);
    transform: scale(1.04);
  }
}

.panel-head, .inline-actions { display: flex; align-items: center; gap: 12px; }
.panel-head { justify-content: space-between; }

/* 生成报告：单表单结构 */
.gen-lead {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(30, 117, 255, 0.05);
  border: 1px dashed rgba(30, 117, 255, 0.28);
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.gen-lead strong { color: var(--c-text-primary); }

.form-grid { display: flex; flex-direction: column; gap: 14px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field-label { font-size: 12px; color: var(--c-text-muted); font-weight: 500; }
.field-hint { font-size: 11.5px; color: var(--c-text-muted); line-height: 1.5; }

@media (prefers-reduced-motion: reduce) {
  .report-section-enter-active,
  .report-section-leave-active {
    transition: opacity 120ms ease;
  }

  .report-section-enter-from,
  .report-section-leave-to,
  .report-section-enter-to,
  .report-section-leave-from {
    transform: none;
    filter: none;
  }
}

/* 前置就绪 / 未就绪卡片 */
.readiness-card {
  display: grid;
  grid-template-columns: 48px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid rgba(245, 158, 11, 0.32);
  background: rgba(245, 158, 11, 0.06);
}
.readiness-card.readiness-error {
  border-color: rgba(220, 38, 38, 0.24);
  background: rgba(220, 38, 38, 0.05);
}
.readiness-card.readiness-blocked .readiness-icon {
  width: 44px; height: 44px; border-radius: 14px;
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}
.readiness-card.readiness-error .readiness-icon {
  width: 44px; height: 44px; border-radius: 14px;
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}
.readiness-body { min-width: 0; }
.readiness-body h3 {
  margin: 0 0 6px;
  font-family: var(--font-serif); font-size: 15px; font-weight: 700;
  color: var(--c-text-primary);
}
.readiness-body p {
  margin: 0 0 6px;
  font-size: 12.5px; line-height: 1.55; color: var(--c-text-secondary);
}
.readiness-missing {
  margin: 6px 0 6px; padding: 0; list-style: none;
  display: flex; flex-wrap: wrap; gap: 6px;
}
.readiness-missing li {
  padding: 3px 10px; border-radius: 999px;
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e; font-size: 11.5px; font-weight: 500;
}

/* 就绪后的生成区 */
.generate-box {
  display: flex; flex-direction: column; gap: 10px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-strong);
}
.readiness-pill {
  align-self: flex-start;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 12px 4px 10px;
  border-radius: 999px;
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
  font-size: 11.5px; font-weight: 600;
}
.generate-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.scope-pill {
  padding: 4px 10px;
  font-size: 11.5px;
}

@media (max-width: 720px) {
  .readiness-card {
    grid-template-columns: 1fr;
    text-align: left;
  }
  .readiness-card :deep(.glow-button) { justify-self: flex-start; }
  .generate-actions { align-items: stretch; }
}

.glass-input {
  width: 100%; padding: 10px 12px; border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary); font-size: 13px;
}
.inline-hint { font-size: 12px; color: var(--c-text-muted); padding: 6px 2px; }

.task-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.task-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(136px, 1fr));
  gap: 8px;
}
.summary-box-mini {
  padding: 10px 12px; border-radius: 10px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: flex; flex-direction: column; gap: 2px;
}
.summary-box-mini span { font-size: 11px; color: var(--c-text-muted); }
.summary-box-mini strong { font-size: 13px; color: var(--c-text-primary); }
.task-note {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.task-note strong {
  color: var(--c-text-primary);
  font-size: 13px;
}
.task-note p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.task-note.is-error {
  border-color: rgba(220, 38, 38, 0.22);
  background: rgba(220, 38, 38, 0.06);
}
.task-summary-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 8px;
}
.task-summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(30, 117, 255, 0.05);
}
.task-summary-item span {
  color: var(--c-text-muted);
  font-size: 11px;
}
.task-summary-item strong {
  color: var(--c-text-primary);
  font-size: 12.5px;
}

/* 列表项 */
.card-list { display: flex; flex-direction: column; gap: 8px; }
/* 列表区永远撑满 640px：内容少时不滚动（overflow:auto 按需出滚动条），
   内容多时自动出滚动条。避免"只有 1 条报告时面板变得很矮" */
.scrollable-list {
  height: 640px; overflow-y: auto; padding-right: 6px;
  display: flex; flex-direction: column; gap: 8px;
  align-content: flex-start;
}

.list-item {
  display: flex; justify-content: space-between; gap: 10px;
  padding: 11px 13px; border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
  min-width: 0; transition: all .18s;
}
.list-item:hover {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(255, 255, 255, 0.9);
}
.list-item.active {
  border-color: rgba(30, 117, 255, 0.6);
  background: rgba(30, 117, 255, 0.08);
  box-shadow: 0 0 14px rgba(30, 117, 255, 0.08);
}
.list-item .list-main { min-width: 0; flex: 1; }
.list-item strong { font-size: 13px; display: block; }
.list-item p {
  margin: 2px 0 0; color: var(--c-text-secondary); font-size: 11.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.list-item p.muted { color: var(--c-text-muted); font-size: 11px; }
.clickable { cursor: pointer; }
.section-copy {
  margin: -4px 0 4px;
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.pill {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 999px; white-space: nowrap;
  background: var(--c-bg-surface-hover); color: var(--c-text-secondary);
  font-size: 11px;
}

.icon-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: 7px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary); cursor: pointer;
}
.icon-btn:hover { color: var(--c-accent-primary); border-color: rgba(30, 117, 255, 0.4); }
.delete-icon { cursor: pointer; color: var(--c-text-muted); }

.empty-state-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 480px; height: 100%; border-radius: 20px; color: var(--c-text-muted);
}
.empty-state-wrapper {
  min-height: 160px; display: flex; align-items: center; justify-content: center;
}
.loading-overlay {
  position: absolute; inset: 0;
  background: var(--c-bg-base);
  display: flex; align-items: stretch; justify-content: stretch;
  border-radius: 20px; z-index: 10;
  overflow: hidden;
}
.detail-skel {
  width: 100%; padding: 24px;
  display: flex; flex-direction: column; gap: 14px;
}
.mt-4 { margin-top: 16px; }

/* 响应式 */
@media (max-width: 1180px) {
  .library-layout { grid-template-columns: 1fr; }
  .detail-col { min-height: auto; }
}
@media (max-width: 900px) {
  .report-layout { grid-template-columns: 1fr; gap: 16px; }
  .report-sidebar {
    position: relative; top: 0; width: 100%; max-height: none; overflow: visible;
  }
  .report-sidebar-inner {
    flex-direction: row; gap: 18px; padding: 4px 2px 6px;
    overflow-x: auto; overflow-y: hidden;
  }
  .report-hero-title { flex-shrink: 0; padding-bottom: 0; border-bottom: none; }
  .report-nav { flex-direction: row; gap: 18px; flex: 1; }
  .scrollable-list { height: auto; }
  .task-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
