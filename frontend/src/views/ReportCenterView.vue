<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ReportDetailPanel from '../components/report/ReportDetailPanel.vue'
import ReportSchedulePanel from '../components/report/ReportSchedulePanel.vue'
import ReportPublicationPanel from '../components/report/ReportPublicationPanel.vue'
import {
  batchDeleteReports,
  createReport,
  deleteReport,
  exportReportFormat,
  fetchPublicReports,
  fetchReportCenterMeta,
  fetchReportReadiness,
  fetchReportDownloadMeta,
  fetchReportDrill,
  fetchReports,
  fetchReportSchedules,
  fetchReportStatus,
  normalizeError,
  openReportPdf,
  submitReportReview
} from '../api'
import { useAuthStore } from '../store/auth'
import { getRoleLabel } from '../utils/role'
import {
  BookOpen,
  FileText,
  Globe,
  LockKeyhole,
  RefreshCw,
  Send,
  Shield,
  ShieldCheck,
  Sparkles,
  Trash2,
  UserRound
} from 'lucide-vue-next'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

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
const readiness = ref(null)
const readinessLoading = ref(false)
const exportFormat = ref('pdf')
const autoReportName = ref('')
const reportMajor = ref('')

const generateForm = ref({
  reportType: reportMeta.value.defaultReportType,
  reportName: reportMeta.value.defaultReportName
})
autoReportName.value = generateForm.value.reportName

const canManageReports = computed(() => authStore.isLoggedIn)
const currentRoleType = computed(() => authStore.user?.roleType ?? 0)
const currentRoleLabel = computed(() => getRoleLabel(currentRoleType.value))
const isAdmin = computed(() => currentRoleType.value === 1)
const currentReportTypes = computed(() => reportMeta.value?.reportTypes || [])
const currentReportTypeConfig = computed(
  () => currentReportTypes.value.find((item) => item.code === generateForm.value.reportType) || currentReportTypes.value[0] || null
)
const activeReportCode = computed(() => {
  const code = generateForm.value.reportType || reportMeta.value?.defaultReportType
  return typeof code === 'string' ? code.toUpperCase() : ''
})
const visiblePrivateReports = computed(() => {
  const code = activeReportCode.value
  if (!code) return privateReports.value
  return (privateReports.value || []).filter((item) => ((item?.reportType || '').toUpperCase() === code))
})
const visiblePublicReports = computed(() => {
  const code = activeReportCode.value
  if (!code) return publicReports.value
  return (publicReports.value || []).filter((item) => ((item?.reportType || '').toUpperCase() === code))
})

const heroStats = computed(() => [
  {
    label: canManageReports.value ? '私有报告' : '公开报告',
    value: canManageReports.value ? visiblePrivateReports.value.length : visiblePublicReports.value.length
  },
  { label: '调度计划', value: schedules.value.length },
  { label: '角色入口', value: currentReportTypes.value.length }
])
const showMajorField = computed(() => currentRoleType.value === 2)
const readinessReady = computed(() => readiness.value?.ready !== false)
const readinessMissing = computed(() => (
  Array.isArray(readiness.value?.missingRequirements) ? readiness.value.missingRequirements : []
))
const readinessPrimaryAction = computed(() => readiness.value?.primaryAction || null)

const latestTaskSummary = computed(() => {
  if (!selectedTask.value) return []
  return [
    { label: '任务状态', value: taskStatusLabel(selectedTask.value.status) },
    { label: '生成进度', value: `${selectedTask.value.progress ?? 0}%` },
    { label: '开始时间', value: formatDateTime(selectedTask.value.startedAt) },
    { label: '完成时间', value: formatDateTime(selectedTask.value.completedAt) }
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

watch(reportMajor, () => {
  if (showMajorField.value) {
    loadReadiness()
  }
})

function buildLocalMeta(roleType) {
  const roleLabel = getRoleLabel(roleType)
  if (roleType === 1) {
    return {
      roleType, roleLabel,
      moduleTitle: '运营分析工作台',
      moduleDescription: '管理员入口优先突出平台运营分析、供需结构和增长抓手。',
      defaultReportType: 'OPERATIONS',
      defaultReportName: '平台运营分析报告',
      privateListScope: '可查看全站私有报告',
      publicListScope: '公开报告对所有用户可见',
      reportTypes: [
        { code: 'OPERATIONS', label: '平台运营分析', defaultName: '平台运营分析报告', description: '聚焦用户分层、内容供给、转化抓手与运营优先级。', templateDescription: '适合管理员快速判断资源投向。', entryHint: '优先看低匹配用户、头部赛道和高频缺口。' }
      ]
    }
  }
  if (roleType === 2) {
    return {
      roleType, roleLabel,
      moduleTitle: '教学支持工作台',
      moduleDescription: '教师入口优先突出供需分析、教学建议和能力缺口观察。',
      defaultReportType: 'TEACHING_ADVICE',
      defaultReportName: '教学建议与课程对齐报告',
      privateListScope: '仅查看本人生成的私有报告',
      publicListScope: '公开报告对所有用户可见',
      reportTypes: [
        { code: 'TEACHING_ADVICE', label: '教学建议报告', defaultName: '教学建议与课程对齐报告', description: '聚焦课程设计、实训任务和求职辅导。', templateDescription: '适合把岗位要求映射到教学动作。', entryHint: '优先看课程补位点。' }
      ]
    }
  }
  return {
    roleType: roleType ?? 0, roleLabel,
    moduleTitle: '个人求职工作台',
    moduleDescription: '学生入口优先突出个人求职分析、技能差距和薪资趋势。',
    defaultReportType: 'JOB_SEEKING',
    defaultReportName: '个人求职分析报告',
    privateListScope: '仅查看本人生成的私有报告',
    publicListScope: '公开报告对所有用户可见',
    reportTypes: [
      { code: 'JOB_SEEKING', label: '个人求职分析', defaultName: '个人求职分析报告', description: '聚焦岗位匹配、投递策略和目标城市机会。', templateDescription: '适合学生快速判断该补什么、该投什么。', entryHint: '优先看匹配度与岗位样本。' }
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

function applyRoutePreset() {
  const queryType = typeof route.query.reportType === 'string' ? route.query.reportType.toUpperCase() : ''
  if (queryType && currentReportTypes.value.some((item) => item.code === queryType)) {
    generateForm.value.reportType = queryType
  }
  reportMajor.value = typeof route.query.major === 'string' ? route.query.major.trim() : ''
}

function reportTypeLabel(code) {
  return currentReportTypes.value.find((item) => item.code === code)?.label || code || '--'
}

function getRoleIcon() {
  if (currentRoleType.value === 1) return Shield
  if (currentRoleType.value === 2) return BookOpen
  return UserRound
}

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

function taskStatusLabel(status) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '生成中'
  if (status === 'PENDING') return '排队中'
  return status || '--'
}

function flashSuccess(msg) {
  success.value = msg
  setTimeout(() => { success.value = '' }, 3000)
}

async function loadReadiness() {
  if (!canManageReports.value || !authStore.token) {
    readiness.value = null
    return
  }
  readinessLoading.value = true
  try {
    readiness.value = await fetchReportReadiness(authStore.token, {
      major: showMajorField.value ? reportMajor.value.trim() : ''
    })
  } catch (e) {
    readiness.value = null
    error.value = normalizeError(e)
  } finally {
    readinessLoading.value = false
  }
}

function goToPath(path) {
  if (!path) return
  router.push(path)
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
      readiness.value = null
      applyMeta(buildLocalMeta(currentRoleType.value))
      return
    }

    const [metaResult, reportsResult, schedulesResult] = await Promise.allSettled([
      fetchReportCenterMeta(authStore.token),
      fetchReports(authStore.token, { page: 1, pageSize: 10 }),
      fetchReportSchedules(authStore.token)
    ])

    applyMeta(metaResult.status === 'fulfilled' ? metaResult.value : buildLocalMeta(currentRoleType.value))
    applyRoutePreset()
    privateReports.value = reportsResult.status === 'fulfilled' ? (reportsResult.value.data || []) : []
    schedules.value = schedulesResult.status === 'fulfilled' ? (schedulesResult.value || []) : []
    if (reportsResult.status === 'rejected' || schedulesResult.status === 'rejected') {
      error.value = '部分报告数据加载失败，已展示当前可用内容。'
    }
    await loadReadiness()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function reloadSchedules() {
  try {
    schedules.value = await fetchReportSchedules(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
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
  if (!readinessReady.value) {
    const action = readinessPrimaryAction.value
    const actionLabel = action?.label || '完成前置数据准备'
    const actionPath = action?.path || '/recommend'
    error.value = `当前前置数据未就绪，请先执行：${actionLabel}`
    if (actionPath) {
      setTimeout(() => goToPath(actionPath), 300)
    }
    return
  }
  actionLoading.value = true
  error.value = ''
  success.value = ''
  try {
    const params = { targetRoleType: currentRoleType.value }
    if (showMajorField.value && reportMajor.value.trim()) {
      params.major = reportMajor.value.trim()
    }
    const result = await createReport(authStore.token, {
      reportName: generateForm.value.reportName.trim() || currentReportTypeConfig.value?.defaultName || reportMeta.value.defaultReportName,
      reportType: generateForm.value.reportType,
      params
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

async function handleFormatExport(payload) {
  if (!payload?.id) return
  try {
    // 先拿下载元数据：服务端据此校验访问权限并累加 viewCount。失败即中止导出。
    const meta = await fetchReportDownloadMeta(authStore.token, payload.id).catch((e) => {
      throw new Error(normalizeError(e) || '无法获取下载元数据')
    })
    const views = meta?.viewCount ?? meta?.downloadCount
    await exportReportFormat(authStore.token, payload.id, payload.reportName, exportFormat.value)
    const suffix = Number.isFinite(Number(views)) ? `，累计查看 ${views} 次` : ''
    flashSuccess(`报告已导出为 ${exportFormat.value.toUpperCase()}${suffix}`)
  } catch (e) {
    error.value = normalizeError(e)
  }
}

async function handlePreviewPdf(id) {
  error.value = ''
  try {
    // 预览也先走 download 元数据，触发权限校验 + 访问计数。
    await fetchReportDownloadMeta(authStore.token, id).catch((e) => {
      throw new Error(normalizeError(e) || '无法获取下载元数据')
    })
    await openReportPdf(authStore.token, id)
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
    flashSuccess('报告已删除')
    if (selectedReport.value?.reportId === id || selectedReport.value?.id === id) {
      selectedReport.value = null
    }
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function handleBatchDeleteReports() {
  if (!authStore.token || actionLoading.value) return
  const ids = visiblePrivateReports.value.map((item) => item.id).filter((id) => typeof id === 'number')
  if (!ids.length) {
    error.value = '当前没有可删除的私有报告'
    return
  }
  if (!confirm(`确定一键删除当前角色下的 ${ids.length} 份私有报告吗？删除后无法恢复。`)) return
  actionLoading.value = true
  error.value = ''
  try {
    await batchDeleteReports(authStore.token, ids)
    selectedReport.value = null
    flashSuccess(`已删除 ${ids.length} 份报告`)
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    actionLoading.value = false
  }
}

async function handleSubmitReview(id, event) {
  if (event) event.stopPropagation()
  try {
    await submitReportReview(authStore.token, id)
    flashSuccess('已提交审核')
    await loadPage()
  } catch (e) {
    error.value = normalizeError(e)
  }
}

onMounted(() => { loadPage() })
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
          <GlowButton v-if="canManageReports" variant="primary" :loading="actionLoading" @click="handleCreateReport">
            <FileText :size="14" />
            {{ readinessReady ? '立即生成' : '先去补齐数据' }}
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
    <div v-if="canManageReports && readiness && !readinessReady" class="status-banner warning-banner">
      <div class="readiness-head">报告功能尚未解锁，请先完成前置分析</div>
      <ul class="readiness-list">
        <li v-for="item in readinessMissing" :key="item.key">
          <strong>{{ item.title }}</strong>
          <span>{{ item.detail }}</span>
        </li>
      </ul>
      <button
        v-if="readinessPrimaryAction?.path"
        type="button"
        class="readiness-action"
        @click="goToPath(readinessPrimaryAction.path)"
      >
        {{ readinessPrimaryAction.label || '前往处理' }}
      </button>
    </div>

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
              <div v-if="readinessLoading" class="hint-box">
                <strong>正在校验前置条件...</strong>
                <p>请稍候，系统正在检查角色数据是否满足生成报告条件。</p>
              </div>
              <div v-else-if="!readinessReady" class="hint-box warn">
                <strong>当前不可生成报告</strong>
                <p>请先完成上方提示的前置步骤，完成后再生成角色专属报告。</p>
              </div>
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
              <input
                v-if="showMajorField"
                v-model="reportMajor"
                class="glass-input"
                placeholder="专业范围（可选，例如：计算机科学与技术）"
                @keydown.enter="handleCreateReport"
              />
              <div class="hint-box">
                <strong>{{ currentReportTypeConfig?.label }}</strong>
                <p>{{ currentReportTypeConfig?.templateDescription }}</p>
              </div>
              <GlowButton variant="primary" :loading="actionLoading" @click="handleCreateReport">
                {{ readinessReady ? '生成角色专属报告' : '先完成前置步骤' }}
              </GlowButton>
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
              <h2 class="workspace-panel-title inline-icon">
                <LockKeyhole :size="15" /> {{ isAdmin ? '私有报告总览' : '我的报告' }}
              </h2>
            </div>
            <div class="inline-actions">
              <GlowButton variant="ghost" @click="loadPage"><RefreshCw :size="14" />刷新</GlowButton>
              <GlowButton
                v-if="canManageReports && visiblePrivateReports.length"
                variant="ghost"
                :loading="actionLoading"
                @click="handleBatchDeleteReports"
              >
                <Trash2 :size="14" />一键删除
              </GlowButton>
            </div>
          </div>
          <div v-if="!canManageReports" class="empty-state-wrapper">
            <EmptyState icon="inbox" title="暂不可查看私有报告" description="登录后可查看并管理你自己的角色化报告。" />
          </div>
          <div v-else class="card-list scrollable-list">
            <div
              v-for="report in visiblePrivateReports"
              :key="report.id"
              class="list-item clickable"
              :class="{ active: selectedReport?.reportId === report.id }"
              @click="openReportDetail(report)"
            >
              <div class="list-main">
                <strong>{{ report.reportName || `报告 #${report.id}` }}</strong>
                <p>{{ reportTypeLabel(report.reportType) }} · {{ formatDateTime(report.generatedAt) }}</p>
                <p class="muted">状态：{{ report.reportLifecycle?.stateLabel || '草稿' }}</p>
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
                <Trash2 class="delete-icon" :size="16" @click="handleDeleteReport(report.id, $event)" />
              </div>
            </div>
            <div v-if="loading" class="skeleton-list mt-4">
              <SkeletonCard type="list" :lines="4" />
            </div>
            <div v-if="!visiblePrivateReports.length && !loading" class="empty-state-wrapper mt-4">
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
              v-for="report in visiblePublicReports"
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
            <div v-if="!visiblePublicReports.length && !loading" class="empty-state-wrapper mt-4">
              <EmptyState icon="file" title="暂无公开报告" description="当前还没有可以直接浏览的公开报告。" />
            </div>
          </div>
        </article>

        <ReportSchedulePanel
          v-if="canManageReports"
          :schedules="schedules"
          :token="authStore.token"
          :report-types="currentReportTypes"
          :default-report-type="reportMeta.defaultReportType"
          :report-type-label="reportTypeLabel"
          @refresh="reloadSchedules"
          @error="(msg) => (error = msg)"
          @success="flashSuccess"
        />

        <ReportPublicationPanel
          v-if="isAdmin"
          :token="authStore.token"
          :report-type-label="reportTypeLabel"
          @select="openReportDetail"
          @error="(msg) => (error = msg)"
          @success="flashSuccess"
        />
      </div>

      <div class="main-content">
        <div v-if="detailLoading" class="loading-overlay">
          <RefreshCw class="spinning" :size="32" style="color: var(--c-accent-primary)" />
          <div style="margin-top: 12px; color: var(--c-text-muted); font-size: 14px;">正在加载报告详情...</div>
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
          <EmptyState icon="search" title="选择一份报告" description="可从左侧私有报告或公开报告列表中选择，查看角色化分析详情。" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 24px; }

.hero-badges { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 6px 12px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(193, 198, 215, 0.5);
  font-size: 12px; color: var(--c-text-secondary);
}

.status-banner { padding: 14px 16px; border-radius: 16px; }
.error-banner { color: #b91c1c; background: rgba(254, 226, 226, 0.84); }
.success-banner { color: #166534; background: rgba(220, 252, 231, 0.84); }
.warning-banner {
  color: #92400e;
  background: rgba(254, 243, 199, 0.9);
  border: 1px solid rgba(245, 158, 11, 0.38);
}
.readiness-head {
  font-weight: 700;
  margin-bottom: 8px;
}
.readiness-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
}
.readiness-list li {
  display: grid;
  gap: 2px;
}
.readiness-list span {
  font-size: 13px;
  color: #7c2d12;
}
.readiness-action {
  margin-top: 10px;
  border: 1px solid rgba(217, 119, 6, 0.4);
  background: rgba(255, 255, 255, 0.72);
  color: #9a3412;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
}
.hint-box.warn {
  border-color: rgba(245, 158, 11, 0.44);
  background: rgba(254, 243, 199, 0.72);
}
[data-theme="dark"] .error-banner { background: rgba(178, 59, 46, 0.18); color: #ffb4a6; }
[data-theme="dark"] .success-banner { background: rgba(30, 138, 91, 0.18); color: #b6e8c8; }
[data-theme="dark"] .warning-banner {
  color: #fbbf24;
  background: rgba(120, 53, 15, 0.35);
  border-color: rgba(245, 158, 11, 0.48);
}
[data-theme="dark"] .readiness-list span { color: #fdba74; }
[data-theme="dark"] .readiness-action {
  color: #fbbf24;
  border-color: rgba(245, 158, 11, 0.48);
  background: rgba(30, 41, 59, 0.4);
}
[data-theme="dark"] .hint-box.warn {
  border-color: rgba(245, 158, 11, 0.48);
  background: rgba(120, 53, 15, 0.3);
}

.master-detail-layout {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 24px;
  align-items: start;
}
.sidebar { display: flex; flex-direction: column; gap: 24px; }

.panel-head, .inline-actions, .role-meta-top, .detail-header, .section-head {
  display: flex; align-items: center; gap: 12px;
}
.panel-head, .role-meta-top { justify-content: space-between; }

.card-list, .form-grid, .action-list { display: flex; flex-direction: column; gap: 14px; }

.scrollable-list { max-height: 400px; overflow-y: auto; padding-right: 8px; display: flex; flex-direction: column; gap: 10px; }
.scrollable-list-small { max-height: 250px; overflow-y: auto; padding-right: 8px; display: flex; flex-direction: column; gap: 10px; }

.list-item {
  display: flex; justify-content: space-between; gap: 12px;
  padding: 14px 16px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
  min-width: 0; transition: all .2s;
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
.list-main { min-width: 0; }
.list-item p, .role-meta-text, .entry-card p, .hint-box p {
  margin: 0; color: var(--c-text-secondary);
}
.list-item p.muted { color: var(--c-text-muted); font-size: 12px; margin-top: 2px; }
.clickable, .entry-card { cursor: pointer; }

.pill {
  padding: 6px 11px; border-radius: 999px; white-space: nowrap;
  background: var(--c-bg-surface-hover); color: var(--c-text-secondary);
}

.entry-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.entry-card {
  text-align: left; padding: 16px; border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.entry-card strong { display: block; margin-bottom: 8px; }
.entry-card span { font-size: 12px; color: var(--c-text-muted); }

.role-meta-card {
  padding: 16px; border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.mini-label { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 8px; }
.hint-box {
  padding: 16px; border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}

.glass-input {
  width: 100%; padding: 12px 14px; border-radius: 12px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.task-strip { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.summary-box-mini {
  padding: 10px 12px; border-radius: 12px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: flex; flex-direction: column; gap: 4px;
}
.summary-box-mini span { font-size: 11px; color: var(--c-text-muted); }
.summary-box-mini strong { font-size: 14px; color: var(--c-text-primary); }

.icon-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 8px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary); cursor: pointer;
}
.icon-btn:hover { color: var(--c-accent-primary); border-color: rgba(30, 117, 255, 0.4); }
.delete-icon { cursor: pointer; color: var(--c-text-muted); }

.empty-state-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 620px; border-radius: 24px; color: var(--c-text-muted);
}
.empty-state-wrapper {
  min-height: 180px; display: flex; align-items: center; justify-content: center;
}
.loading-overlay {
  position: absolute; inset: 0;
  background: rgba(15, 23, 42, .35);
  backdrop-filter: blur(4px);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-radius: 24px; z-index: 10;
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.mt-4 { margin-top: 16px; }

@media (max-width: 1100px) {
  .master-detail-layout { grid-template-columns: 1fr; }
  .entry-grid { grid-template-columns: 1fr; }
  .scrollable-list { max-height: none; }
}
</style>
