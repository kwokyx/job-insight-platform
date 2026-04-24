<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  Bot,
  CheckCircle2,
  Database,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  Settings2,
  ShieldCheck,
  TerminalSquare
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createCrawlTask,
  fetchCrawlAutomationStatus,
  fetchCrawlLiveOverview,
  fetchCrawlQuality,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  fetchPageSnapshotStatus,
  normalizeError,
  queueCrawlAuthSync,
  queueCrawlWatchdog,
  refreshPageSnapshots,
  syncCrawlTaskData,
  triggerCrawlAutomation,
  updateCrawlAutomationConfig,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()

const loading = ref(false)
const creating = ref(false)
const syncing = ref(false)
const snapshotLoading = ref(false)
const automationSaving = ref(false)
const automationTriggering = ref(false)
const watchdogLoading = ref(false)
const authSyncLoading = ref(false)
const updatingTaskId = ref('')
const logsLoading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')
const pollTimer = ref(null)

const tasks = ref([])
const logs = ref([])
const quality = ref({})
const liveOverview = ref({
  summary: {},
  runningTasks: [],
  failedTasks: [],
  latestLogs: [],
  latestTask: null,
  activeProgress: null
})
const snapshotStatus = ref({ pages: [], nextScheduledAt: '' })
const automationStatus = ref({
  settings: {},
  execution: {},
  agent: {},
  pendingCommands: [],
  lastResults: [],
  authStatus: {}
})
const totalTasks = ref(0)
const activeTaskId = ref('')

const filters = ref({
  channel: '',
  status: ''
})

const taskForm = ref({
  taskName: '',
  channel: 'zhaopin',
  keywords: 'Python',
  city: '成都',
  priority: 5,
  pageCount: 3,
  scheduleMode: 'IMMEDIATE',
  scheduleTime: '09:00',
  incremental: true,
  incrementalPageLimit: 2,
  stalePageThreshold: 1,
  lookbackHours: 72
})

const automationForm = ref({
  enabled: false,
  cron: '0 0 7,13,19 * * ?',
  watchdogEnabled: true,
  channel: 'zhaopin',
  taskNamePrefix: '智联定时采集',
  keywordsText: 'Python',
  citiesText: '成都',
  pageCount: 3,
  priority: 5,
  incremental: true,
  incrementalPageLimit: 2,
  stalePageThreshold: 1,
  lookbackHours: 72,
  createUser: 'backend-scheduler',
  pythonCommand: 'py -3',
  watchdogScript: 'scripts/watch_zhaopin_auth.py',
  syncScript: 'scripts/sync_zhaopin_auth_snapshot.py',
  workspace: ''
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待执行', value: '0' },
  { label: '执行中', value: '1' },
  { label: '已完成', value: '2' },
  { label: '失败/暂停', value: '3' }
]

const channelOptions = [
  { label: '全部渠道', value: '' },
  { label: '智联招聘', value: 'zhaopin' },
  { label: 'BOSS 直聘', value: 'boss' },
  { label: '前程无忧', value: '51job' },
  { label: '拉勾', value: 'lagou' }
]

const scheduleModeOptions = [
  { label: '立即采集', value: 'IMMEDIATE' },
  { label: '定时模板', value: 'SCHEDULED' }
]

function showMessage(type, message) {
  if (type === 'error') {
    errorMsg.value = message
    successMsg.value = ''
    return
  }
  successMsg.value = message
  errorMsg.value = ''
}

function clearMessage() {
  errorMsg.value = ''
  successMsg.value = ''
}

function formatTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function splitMultiValue(value) {
  return String(value || '')
    .split(/[,，、\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function displayMultiValue(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join(' / ') || '--'
  if (value === null || value === undefined || value === '') return '--'
  return String(value)
}

function getStatusMeta(status) {
  const map = {
    0: { label: '待执行', tone: 'idle' },
    1: { label: '执行中', tone: 'running' },
    2: { label: '已完成', tone: 'done' },
    3: { label: '失败/暂停', tone: 'danger' }
  }
  return map[Number(status)] || { label: '未知', tone: 'idle' }
}

function progressPercent(task) {
  const total = Number(task?.totalCount || 0)
  const finished = Number(task?.finishedCount || 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((finished / total) * 100)))
}

function normalizePercent(value) {
  const num = Number(value || 0)
  if (!Number.isFinite(num)) return '0%'
  return num > 1 ? `${num.toFixed(1)}%` : `${(num * 100).toFixed(1)}%`
}

function buildTaskName(baseName) {
  const now = new Date()
  const timestamp = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
    String(now.getHours()).padStart(2, '0'),
    String(now.getMinutes()).padStart(2, '0'),
    String(now.getSeconds()).padStart(2, '0')
  ].join('')
  return `${baseName || '采集任务'}-${timestamp}`
}

const qualityCards = computed(() => {
  const completeness = quality.value?.completeness || {}
  const syncState = quality.value?.syncState || {}
  const summary = liveOverview.value?.summary || {}
  return [
    { label: '采集库记录数', value: summary.crawlRows ?? syncState.crawlRows ?? '--', note: 'crawl_job_posting' },
    { label: '业务库记录数', value: summary.bizRows ?? syncState.bizRows ?? '--', note: 'biz_job_posting' },
    { label: '今日新采集', value: summary.todayRows ?? '--', note: '今日写入 crawl_job_posting' },
    { label: '今日入业务库', value: summary.todayBizRows ?? '--', note: '今日同步 biz_job_posting' },
    { label: '标题完整率', value: normalizePercent(completeness.titleRate), note: '职位标题可用性' },
    { label: '薪资完整率', value: normalizePercent(completeness.salaryRate), note: '薪资字段可用性' }
  ]
})

const selectedTask = computed(() => tasks.value.find((item) => String(item.taskId) === String(activeTaskId.value)) || null)
const snapshotPages = computed(() => snapshotStatus.value?.pages || [])
const nextSnapshotTime = computed(() => formatTime(snapshotStatus.value?.nextScheduledAt))
const pendingCommands = computed(() => automationStatus.value?.pendingCommands || [])
const automationResults = computed(() => automationStatus.value?.lastResults || [])
const agentStatus = computed(() => automationStatus.value?.agent || {})
const authStatus = computed(() => automationStatus.value?.authStatus || {})
const executionStatus = computed(() => automationStatus.value?.execution || {})
const taskPreview = computed(() => automationStatus.value?.taskPreview || {})
const automationNotes = computed(() => executionStatus.value?.notes || [])
const recentJobs = computed(() => liveOverview.value?.summary?.recentJobs || [])
const runningTasks = computed(() => liveOverview.value?.runningTasks || [])
const failedTasks = computed(() => liveOverview.value?.failedTasks || [])
const latestLiveLogs = computed(() => liveOverview.value?.latestLogs || [])
const latestTask = computed(() => liveOverview.value?.latestTask || null)
const activeProgress = computed(() => liveOverview.value?.activeProgress || null)
const runningQueueTasks = computed(() => tasks.value.filter((item) => Number(item.status) === 1 || Number(item.status) === 0))
const completedQueueTasks = computed(() => tasks.value.filter((item) => Number(item.status) === 2))
const failedQueueTasks = computed(() => tasks.value.filter((item) => Number(item.status) === 3))
const taskSections = computed(() => [
  {
    key: 'running',
    title: '运行中 / 待执行',
    subtitle: '优先看正在跑和即将跑的任务',
    emptyText: '当前没有运行中或待执行的任务',
    items: runningQueueTasks.value
  },
  {
    key: 'completed',
    title: '最近完成',
    subtitle: '优先展示最近成功完成并可同步结果的任务',
    emptyText: '当前没有已完成任务',
    items: completedQueueTasks.value
  },
  {
    key: 'failed',
    title: '失败 / 暂停',
    subtitle: '保留失败与暂停任务，便于直接重跑和排障',
    emptyText: '当前没有失败或暂停任务',
    items: failedQueueTasks.value
  }
])
const authReadableStatus = computed(() => {
  if (authStatus.value?.cookie_present) return '可用'
  if (authStatus.value?.status) return String(authStatus.value.status)
  return '待刷新'
})
const runtimeHint = computed(() => {
  if (runningTasks.value.length > 0) {
    return `正在运行 ${runningTasks.value.length} 个任务`
  }
  if (failedTasks.value.length > 0) {
    return `最近有 ${failedTasks.value.length} 个失败/暂停任务`
  }
  return '当前没有运行中的采集任务'
})
const latestAutomationResult = computed(() =>
  (automationResults.value || []).find((item) => item?.action === 'WATCHDOG' || item?.action === 'SYNC_AUTH_SNAPSHOT') || null
)
const pendingAutomationCommand = computed(() =>
  (pendingCommands.value || []).find((item) => item?.action === 'WATCHDOG' || item?.action === 'SYNC_AUTH_SNAPSHOT') || null
)
const automationStage = computed(() => {
  const currentAction = String(agentStatus.value?.currentAction || '').toUpperCase()
  const pendingAction = String(pendingAutomationCommand.value?.action || '').toUpperCase()
  const latest = latestAutomationResult.value

  if (currentAction === 'WATCHDOG') {
    return {
      key: 'auth_running',
      label: '鉴权中',
      detail: '宿主机代理正在刷新智联鉴权，轮询会自动更新结果。'
    }
  }
  if (currentAction === 'SYNC_AUTH_SNAPSHOT') {
    return {
      key: 'auth_syncing',
      label: '同步中',
      detail: '正在把最新鉴权快照同步到采集节点。'
    }
  }
  if (pendingAction === 'WATCHDOG') {
    return {
      key: 'auth_queued',
      label: '鉴权排队中',
      detail: '鉴权命令已进入队列，等待宿主机代理执行。'
    }
  }
  if (pendingAction === 'SYNC_AUTH_SNAPSHOT') {
    return {
      key: 'sync_queued',
      label: '同步排队中',
      detail: '鉴权同步命令已进入队列，等待宿主机代理执行。'
    }
  }
  if (latest?.status === 'FAILED') {
    return {
      key: 'auth_failed',
      label: '鉴权异常',
      detail: latest.message || '最近一次鉴权执行失败，请查看自动化结果。'
    }
  }
  if (authStatus.value?.cookie_present) {
    const latestMessage = String(latest?.message || '')
    return {
      key: 'auth_ready',
      label: '鉴权可用',
      detail: latestMessage.includes('fallback to sync current snapshot')
        ? `本机 Edge 刷新失败，已回退为快照同步。最近状态更新时间：${formatTime(authStatus.value?.updated_at)}`
        : `鉴权凭证可用。最近状态更新时间：${formatTime(authStatus.value?.updated_at)}`
    }
  }
  return {
    key: 'auth_unknown',
    label: '待鉴权',
    detail: '当前没有可用鉴权状态，请先执行鉴权看门狗或鉴权同步。'
  }
})
const realtimeTimeline = computed(() => {
  const crawlStage = activeProgress.value?.stageLabel || (runningTasks.value.length ? '等待分片' : '未开始')
  return [
    { label: '鉴权', value: automationStage.value.label },
    { label: '采集', value: crawlStage },
    { label: '结果', value: recentJobs.value.length ? `已抓到 ${recentJobs.value.length} 条最新岗位` : '等待新结果' }
  ]
})
const realtimeTipLevel = computed(() => activeProgress.value?.latestLog?.level || latestAutomationResult.value?.status || 'INFO')
const realtimeTipText = computed(() => {
  if (activeProgress.value?.latestLog?.message) {
    return activeProgress.value.latestLog.message
  }
  const message = String(latestAutomationResult.value?.message || '').trim()
  if (!message) {
    return '当前没有新的错误日志，继续轮询中。'
  }
  if (message.includes('fallback to sync current snapshot')) {
    return '本机 Edge 刷新失败，已自动回退为鉴权快照同步。'
  }
  if (message.includes('auth snapshot synchronized')) {
    return '鉴权快照已同步到采集节点。'
  }
  if (message.includes('trying local Edge refresh')) {
    return '宿主机代理正在尝试刷新智联鉴权。'
  }
  return message.split(/\r?\n/)[0]
})

function syncAutomationForm(data = {}) {
  automationForm.value = {
    enabled: !!data.enabled,
    cron: data.cron || '0 0 7,13,19 * * ?',
    watchdogEnabled: data.watchdogEnabled !== false,
    channel: data.channel || 'zhaopin',
    taskNamePrefix: data.taskNamePrefix || '智联定时采集',
    keywordsText: Array.isArray(data.keywords) ? data.keywords.join(', ') : 'Python',
    citiesText: Array.isArray(data.cities) ? data.cities.join(', ') : '成都',
    pageCount: Number(data.pageCount || 3),
    priority: Number(data.priority || 5),
    incremental: data.incremental !== false,
    incrementalPageLimit: Number(data.incrementalPageLimit || 2),
    stalePageThreshold: Number(data.stalePageThreshold || 1),
    lookbackHours: Number(data.lookbackHours || 72),
    createUser: data.createUser || 'backend-scheduler',
    pythonCommand: data.pythonCommand || 'py -3',
    watchdogScript: data.watchdogScript || 'scripts/watch_zhaopin_auth.py',
    syncScript: data.syncScript || 'scripts/sync_zhaopin_auth_snapshot.py',
    workspace: data.workspace || ''
  }
}

async function loadLogs(taskId) {
  if (!taskId || !authStore.token) return
  activeTaskId.value = String(taskId)
  logsLoading.value = true
  try {
    const payload = await fetchCrawlTaskLogs(authStore.token, taskId, { page: 1, pageSize: 20 })
    logs.value = Array.isArray(payload?.data) ? payload.data : []
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    logsLoading.value = false
  }
}

async function loadDashboard() {
  if (!authStore.token) return
  loading.value = true
  try {
    const [taskResult, qualityResult, snapshotResult, automationResult, liveResult] = await Promise.all([
      fetchCrawlTasks(authStore.token, {
        channel: filters.value.channel || undefined,
        status: filters.value.status === '' ? undefined : Number(filters.value.status),
        page: 1,
        pageSize: 20
      }),
      fetchCrawlQuality(authStore.token),
      fetchPageSnapshotStatus(authStore.token),
      fetchCrawlAutomationStatus(authStore.token),
      fetchCrawlLiveOverview(authStore.token)
    ])

    tasks.value = Array.isArray(taskResult?.data) ? taskResult.data : []
    totalTasks.value = Number(taskResult?.total || tasks.value.length)
    quality.value = qualityResult || {}
    snapshotStatus.value = snapshotResult || { pages: [], nextScheduledAt: '' }
    automationStatus.value = automationResult || { settings: {}, execution: {}, pendingCommands: [], lastResults: [] }
    liveOverview.value = liveResult || { summary: {}, runningTasks: [], failedTasks: [], latestLogs: [] }
    syncAutomationForm(automationStatus.value.settings || {})

    if (tasks.value.length) {
      const matched = tasks.value.some((item) => String(item.taskId) === String(activeTaskId.value))
      await loadLogs(matched ? activeTaskId.value : tasks.value[0].taskId)
    } else {
      activeTaskId.value = ''
      logs.value = []
    }
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    loading.value = false
  }
}

async function handleCreateTask() {
  if (!authStore.token || creating.value) return
  if (!taskForm.value.taskName.trim()) {
    showMessage('error', '请先填写任务名称')
    return
  }
  creating.value = true
  clearMessage()
  try {
    const result = await createCrawlTask(authStore.token, {
      taskName: taskForm.value.taskName.trim(),
      channel: taskForm.value.channel,
      keywords: taskForm.value.keywords.trim(),
      city: taskForm.value.city.trim(),
      priority: Number(taskForm.value.priority) || 5,
      pageCount: Number(taskForm.value.pageCount) || 3,
      scheduleMode: taskForm.value.scheduleMode,
      schedulePreset: taskForm.value.scheduleMode === 'SCHEDULED' ? 'DAILY' : undefined,
      scheduleTime: taskForm.value.scheduleMode === 'SCHEDULED' ? taskForm.value.scheduleTime : undefined,
      incremental: !!taskForm.value.incremental,
      incrementalPageLimit: Number(taskForm.value.incrementalPageLimit) || 2,
      stalePageThreshold: Number(taskForm.value.stalePageThreshold) || 1,
      lookbackHours: Number(taskForm.value.lookbackHours) || 72
    })
    taskForm.value.taskName = ''
    showMessage(
      'success',
      result?.scheduleType === 'SCHEDULED_TEMPLATE'
        ? '定时采集模板已创建，后续会由调度中心自动触发。'
        : '采集任务已提交到调度中心，页面将实时显示进度和结果。'
    )
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    creating.value = false
  }
}

async function handleUpdateTask(task, status) {
  if (!authStore.token || !task?.taskId || updatingTaskId.value) return
  updatingTaskId.value = String(task.taskId)
  clearMessage()
  try {
    if ((Number(task.status) === 2 || Number(task.status) === 3) && status === 1) {
      await createCrawlTask(authStore.token, {
        taskName: buildTaskName(task.taskName || '重跑采集'),
        channel: task.channel || 'zhaopin',
        keywords: displayMultiValue(task.keywords),
        city: displayMultiValue(task.city),
        priority: Number(task.priority) || 5,
        pageCount: Number(task.pageCount) || 3,
        scheduleMode: 'IMMEDIATE',
        incremental: !!task.incremental,
        incrementalPageLimit: Number(task.incrementalPageLimit) || 2,
        stalePageThreshold: Number(task.stalePageThreshold) || 1,
        lookbackHours: Number(task.lookbackHours) || 72
      })
      showMessage('success', '已基于当前任务配置重新创建采集任务。')
    } else {
      await updateCrawlTaskStatus(authStore.token, task.taskId, { status })
      showMessage('success', status === 3 ? '任务已暂停。' : status === 2 ? '任务结果已同步入库。' : '任务已启动。')
    }
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    updatingTaskId.value = ''
  }
}

async function handleSyncData() {
  if (!authStore.token || syncing.value) return
  syncing.value = true
  clearMessage()
  try {
    const payload = await syncCrawlTaskData(authStore.token)
    const etlStatus = payload?.etl?.status || 'SUCCESS'
    showMessage(
      'success',
      etlStatus === 'DEGRADED'
        ? '采集数据已同步入库，但 ETL 以降级模式完成，页面仍会展示最新数据。'
        : '采集数据已同步入库并完成快照刷新。'
    )
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    syncing.value = false
  }
}

async function handleRefreshSnapshots() {
  if (!authStore.token || snapshotLoading.value) return
  snapshotLoading.value = true
  clearMessage()
  try {
    await refreshPageSnapshots(authStore.token, { runIncrementalEtl: true })
    showMessage('success', '页面快照已刷新。')
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    snapshotLoading.value = false
  }
}

async function handleSaveAutomation() {
  if (!authStore.token || automationSaving.value) return
  automationSaving.value = true
  clearMessage()
  try {
    await updateCrawlAutomationConfig(authStore.token, {
      enabled: automationForm.value.enabled,
      cron: automationForm.value.cron.trim(),
      watchdogEnabled: automationForm.value.watchdogEnabled,
      channel: automationForm.value.channel,
      taskNamePrefix: automationForm.value.taskNamePrefix.trim(),
      keywords: splitMultiValue(automationForm.value.keywordsText),
      cities: splitMultiValue(automationForm.value.citiesText),
      pageCount: Number(automationForm.value.pageCount) || 3,
      priority: Number(automationForm.value.priority) || 5,
      incremental: automationForm.value.incremental,
      incrementalPageLimit: Number(automationForm.value.incrementalPageLimit) || 2,
      stalePageThreshold: Number(automationForm.value.stalePageThreshold) || 1,
      lookbackHours: Number(automationForm.value.lookbackHours) || 72,
      createUser: automationForm.value.createUser.trim(),
      pythonCommand: automationForm.value.pythonCommand.trim(),
      watchdogScript: automationForm.value.watchdogScript.trim(),
      syncScript: automationForm.value.syncScript.trim(),
      workspace: automationForm.value.workspace.trim()
    })
    showMessage('success', '自动化配置已保存。')
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    automationSaving.value = false
  }
}

async function handleTriggerAutomation() {
  if (!authStore.token || automationTriggering.value) return
  automationTriggering.value = true
  clearMessage()
  try {
    const payload = await triggerCrawlAutomation(authStore.token)
    const authDispatchStatus = payload?.authDispatch?.status
    showMessage(
      'success',
      authDispatchStatus === 'QUEUED'
        ? '自动化采集已触发，鉴权看门狗已进入宿主机代理队列。'
        : '自动化采集已触发。'
    )
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    automationTriggering.value = false
  }
}

async function handleQueueWatchdog() {
  if (!authStore.token || watchdogLoading.value) return
  watchdogLoading.value = true
  clearMessage()
  try {
    await queueCrawlWatchdog(authStore.token)
    showMessage('success', '鉴权看门狗已加入宿主机代理队列。')
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    watchdogLoading.value = false
  }
}

async function handleQueueAuthSync() {
  if (!authStore.token || authSyncLoading.value) return
  authSyncLoading.value = true
  clearMessage()
  try {
    await queueCrawlAuthSync(authStore.token)
    showMessage('success', '鉴权快照同步已加入宿主机代理队列。')
    await loadDashboard()
  } catch (err) {
    showMessage('error', normalizeError(err))
  } finally {
    authSyncLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  pollTimer.value = window.setInterval(() => {
    if (!loading.value && !creating.value && !syncing.value && !snapshotLoading.value && !automationSaving.value) {
      loadDashboard()
    }
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
})
</script>

<template>
  <div class="collector-page">
    <section class="hero">
      <div>
        <span class="hero-kicker">Distributed Crawler Workspace</span>
        <h1>分布式数据采集控制台</h1>
        <p>
          这里直接展示采集是否跑通、正在采多少、采到了什么岗位，以及失败时的实时提示。
          触发采集后页面会自动轮询，不需要你再去终端里猜状态。
        </p>
      </div>
      <div class="hero-actions">
        <GlowButton variant="ghost" :loading="loading" @click="loadDashboard">
          <RefreshCw :size="16" />
          刷新面板
        </GlowButton>
        <GlowButton variant="ghost" :loading="syncing" @click="handleSyncData">
          <Database :size="16" />
          同步入库
        </GlowButton>
        <GlowButton variant="ghost" :loading="snapshotLoading" @click="handleRefreshSnapshots">
          <Activity :size="16" />
          刷新快照
        </GlowButton>
      </div>
    </section>

    <div v-if="errorMsg" class="banner banner-error">
      <AlertTriangle :size="16" />
      {{ errorMsg }}
    </div>
    <div v-if="successMsg" class="banner banner-success">
      <CheckCircle2 :size="16" />
      {{ successMsg }}
    </div>

    <section class="status-strip">
      <article class="status-card">
        <span>运行态</span>
        <strong>{{ runtimeHint }}</strong>
        <small>最新任务：{{ latestTask?.taskName || '暂无' }}</small>
      </article>
      <article class="status-card">
        <span>宿主机代理</span>
        <strong>{{ agentStatus.online ? '在线' : '离线' }}</strong>
        <small>最后心跳：{{ formatTime(agentStatus.updatedAt) }}</small>
      </article>
      <article class="status-card">
        <span>鉴权状态</span>
        <strong>{{ authReadableStatus }}</strong>
        <small>更新时间：{{ authStatus.updated_at || '--' }}</small>
      </article>
      <article class="status-card danger" v-if="failedTasks.length">
        <span>失败/暂停任务</span>
        <strong>{{ failedTasks.length }}</strong>
        <small>页面会持续显示失败任务和错误日志</small>
      </article>
    </section>

    <section v-if="activeProgress || automationStage" class="progress-board">
      <article class="panel progress-panel">
        <header class="panel-head row">
          <div>
            <h2>实时采集进程</h2>
            <p class="sub">
              {{ activeProgress?.taskName || '等待采集任务' }}
              ｜ {{ displayMultiValue(activeProgress?.city) }}
              ｜ {{ displayMultiValue(activeProgress?.keywords) }}
            </p>
          </div>
          <div class="timeline-chips">
            <span v-for="item in realtimeTimeline" :key="item.label" class="timeline-chip">{{ item.label }}：{{ item.value }}</span>
          </div>
        </header>
        <div class="panel-body progress-body">
          <article class="metric-card">
            <span>鉴权链路</span>
            <strong>{{ automationStage.label }}</strong>
            <small>{{ automationStage.detail }}</small>
          </article>
          <article class="metric-card">
            <span>当前阶段</span>
            <strong>{{ activeProgress?.stageLabel || '等待采集' }}</strong>
            <small>{{ activeProgress?.stageDetail || '当前没有运行中的采集任务，触发后这里会实时显示排队、爬取和汇总阶段。' }}</small>
          </article>
          <article class="metric-card">
            <span>任务进度</span>
            <strong>{{ activeProgress?.finishedCount || 0 }}/{{ activeProgress?.totalCount || 0 }}</strong>
            <small>finishedCount / totalCount</small>
          </article>
          <article class="metric-card">
            <span>分片状态</span>
            <strong>{{ activeProgress?.shardStats?.running || 0 }} 运行中</strong>
            <small>待分发 {{ activeProgress?.shardStats?.pending || 0 }} ｜ 已完成 {{ activeProgress?.shardStats?.completed || 0 }}</small>
          </article>
          <article class="metric-card">
            <span>实时提示</span>
            <strong>{{ realtimeTipLevel }}</strong>
            <small>{{ realtimeTipText }}</small>
          </article>
        </div>
        <div class="panel-body shard-board">
          <div class="shard-column">
            <div class="shard-head">
              <h3>正在执行的分片</h3>
              <span>{{ activeProgress?.activeShards?.length || 0 }}</span>
            </div>
            <div v-if="activeProgress?.activeShards?.length" class="shard-list">
              <article v-for="shard in activeProgress.activeShards" :key="shard.shardId" class="shard-card">
                <strong>第 {{ shard.page || '--' }} 页 / {{ shard.workerId || '待分配节点' }}</strong>
                <small>关键词：{{ shard.keyword || '--' }} ｜ 城市：{{ shard.city || '--' }}</small>
              </article>
            </div>
            <div v-else class="empty-block">当前没有处于运行中的分片</div>
          </div>
          <div class="shard-column">
            <div class="shard-head">
              <h3>最近完成的分片</h3>
              <span>{{ activeProgress?.completedShards?.length || 0 }}</span>
            </div>
            <div v-if="activeProgress?.completedShards?.length" class="shard-list">
              <article v-for="shard in activeProgress.completedShards" :key="shard.shardId" class="shard-card">
                <strong>第 {{ shard.page || '--' }} 页 / {{ shard.workerId || '--' }}</strong>
                <small>新增 {{ shard.newCount || 0 }} ｜ 更新 {{ shard.updatedCount || 0 }} ｜ 去重 {{ shard.duplicateCount || 0 }}</small>
              </article>
            </div>
            <div v-else class="empty-block">还没有完成的分片</div>
          </div>
        </div>
      </article>
    </section>

    <section class="metrics-grid">
      <article v-for="card in qualityCards" :key="card.label" class="metric-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </section>

    <section class="main-grid">
      <div class="left-col">
        <article class="panel">
          <header class="panel-head row">
            <div>
              <h2>实时采集结果</h2>
              <p class="sub">
                最新采集时间：{{ liveOverview.summary?.latestCrawlTime || '--' }}，
                最新入业务库时间：{{ liveOverview.summary?.latestBizTime || '--' }}
              </p>
            </div>
            <span class="pill pill-running" v-if="runningTasks.length">运行中 {{ runningTasks.length }}</span>
          </header>
          <div class="panel-body">
            <div v-if="recentJobs.length" class="job-table">
              <div class="job-row job-head">
                <span>职位</span>
                <span>公司</span>
                <span>城市</span>
                <span>薪资</span>
                <span>采集时间</span>
              </div>
              <div v-for="(job, index) in recentJobs" :key="index" class="job-row">
                <span class="job-title">{{ job.title || '--' }}</span>
                <span>{{ job.companyName || '--' }}</span>
                <span>{{ job.city || '--' }}</span>
                <span>{{ job.salaryRaw || '--' }}</span>
                <span>{{ job.crawlTime || '--' }}</span>
              </div>
            </div>
            <div v-else class="empty-block">还没有可展示的采集结果</div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <div>
              <h2>实时日志</h2>
              <p class="sub">跑不通和跑通都会在这里显示最近日志</p>
            </div>
            <span class="sub">{{ selectedTask ? selectedTask.taskId : latestTask?.taskId || '未选择任务' }}</span>
          </header>
          <div class="panel-body">
            <div v-if="logsLoading" class="empty-block">日志加载中...</div>
            <div v-else-if="logs.length" class="log-list">
              <article v-for="(item, index) in logs" :key="index" class="log-item">
                <div class="log-top">
                  <span class="log-level"><Activity :size="13" /> {{ item.level || 'INFO' }}</span>
                  <span>{{ formatTime(item.createTime) }}</span>
                </div>
                <p class="log-text">{{ item.message || '--' }}</p>
              </article>
            </div>
            <div v-else-if="latestLiveLogs.length" class="log-list">
              <article v-for="(item, index) in latestLiveLogs" :key="index" class="log-item">
                <div class="log-top">
                  <span class="log-level"><Activity :size="13" /> {{ item.level || 'INFO' }}</span>
                  <span>{{ formatTime(item.createTime) }}</span>
                </div>
                <p class="log-text">{{ item.message || '--' }}</p>
              </article>
            </div>
            <div v-else class="empty-block">暂无日志</div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>任务队列</h2>
            <span class="sub">共 {{ totalTasks }} 条</span>
          </header>
          <div class="panel-body">
            <div class="toolbar">
              <select v-model="filters.channel" class="input slim" @change="loadDashboard">
                <option v-for="option in channelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <select v-model="filters.status" class="input slim" @change="loadDashboard">
                <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div v-if="!tasks.length" class="empty-block">暂无采集任务</div>
            <div v-else class="task-sections">
              <section v-for="section in taskSections" :key="section.key" class="task-section">
                <div class="task-section-head">
                  <div>
                    <h3>{{ section.title }}</h3>
                    <p class="sub">{{ section.subtitle }}</p>
                  </div>
                  <span class="pill pill-idle">{{ section.items.length }}</span>
                </div>
                <div v-if="section.items.length" class="task-list">
                  <article
                    v-for="task in section.items"
                    :key="task.taskId"
                    class="task-card"
                    :class="{ active: String(task.taskId) === String(activeTaskId) }"
                    @click="loadLogs(task.taskId)"
                  >
                    <div class="task-top">
                      <h3>{{ task.taskName || task.taskId }}</h3>
                      <span class="pill" :class="`pill-${getStatusMeta(task.status).tone}`">
                        {{ getStatusMeta(task.status).label }}
                      </span>
                    </div>
                    <div class="task-progress">
                      <div class="progress-track">
                        <i class="progress-fill" :style="{ width: `${progressPercent(task)}%` }"></i>
                      </div>
                      <strong>{{ task.finishedCount || 0 }}/{{ task.totalCount || 0 }}</strong>
                    </div>
                    <p class="task-meta">
                      <span>渠道：{{ task.channel || '--' }}</span>
                      <span>城市：{{ displayMultiValue(task.city) }}</span>
                      <span>关键词：{{ displayMultiValue(task.keywords) }}</span>
                      <span>开始时间：{{ formatTime(task.startTime) }}</span>
                    </p>
                    <div class="task-actions">
                      <button class="mini-action" :disabled="updatingTaskId === String(task.taskId)" @click.stop="handleUpdateTask(task, 1)">
                        <PlayCircle :size="14" />
                        {{ Number(task.status) === 2 || Number(task.status) === 3 ? '重跑' : '启动' }}
                      </button>
                      <button class="mini-action" :disabled="updatingTaskId === String(task.taskId)" @click.stop="handleUpdateTask(task, 3)">
                        <PauseCircle :size="14" />
                        暂停
                      </button>
                      <button class="mini-action danger" :disabled="updatingTaskId === String(task.taskId)" @click.stop="handleUpdateTask(task, 2)">
                        <Database :size="14" />
                        同步结果
                      </button>
                    </div>
                  </article>
                </div>
                <div v-else class="empty-block">{{ section.emptyText }}</div>
              </section>
            </div>
          </div>
        </article>
      </div>

      <div class="right-col">
        <article class="panel">
          <header class="panel-head row">
            <h2>一键操作</h2>
            <span class="sub">平台按钮直接驱动真实采集</span>
          </header>
          <div class="panel-body action-grid">
            <GlowButton variant="primary" :loading="automationTriggering" @click="handleTriggerAutomation">
              <Bot :size="16" />
              立即触发自动化采集
            </GlowButton>
            <GlowButton variant="ghost" :loading="watchdogLoading" @click="handleQueueWatchdog">
              <ShieldCheck :size="16" />
              执行鉴权看门狗
            </GlowButton>
            <GlowButton variant="ghost" :loading="authSyncLoading" @click="handleQueueAuthSync">
              <TerminalSquare :size="16" />
              执行鉴权同步
            </GlowButton>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>自动化预览</h2>
            <span class="sub">{{ executionStatus.modeLabel || '宿主机代理执行' }}</span>
          </header>
          <div class="panel-body card-grid">
            <article class="metric-card">
              <span>下次任务名</span>
              <strong>{{ taskPreview.task_name || '--' }}</strong>
              <small>触发自动化时会按这个任务名创建任务</small>
            </article>
            <article class="metric-card">
              <span>生效工作目录</span>
              <strong>{{ executionStatus.effectiveWorkspace || '--' }}</strong>
              <small>留空时由宿主机代理自动识别</small>
            </article>
            <article class="metric-card">
              <span>结果目录</span>
              <strong>{{ executionStatus.resultDirectory || '--' }}</strong>
              <small>代理执行结果会写到这里</small>
            </article>
            <article class="metric-card">
              <span>待执行命令</span>
              <strong>{{ pendingCommands.length }}</strong>
              <small>队列中的本机自动化命令数</small>
            </article>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>自动化结果</h2>
            <span class="sub">跑通和失败都保留最近记录</span>
          </header>
          <div class="panel-body">
            <div v-if="automationResults.length" class="log-list">
              <article v-for="item in automationResults" :key="item.id || item.file" class="log-item">
                <div class="log-top">
                  <span class="log-level"><CheckCircle2 :size="13" /> {{ item.action || item.file || 'RESULT' }}</span>
                  <span>{{ formatTime(item.finishedAt) }}</span>
                </div>
                <p class="log-text">{{ item.message || item.error || item.status || '--' }}</p>
              </article>
            </div>
            <div v-else class="empty-block">暂无宿主机代理执行结果</div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>自动化配置</h2>
            <span class="sub">保存后直接影响按钮触发行为</span>
          </header>
          <div class="panel-body form-grid">
            <label class="field">
              <span>启用平台定时采集</span>
              <select v-model="automationForm.enabled" class="input">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
            <label class="field">
              <span>启用鉴权看门狗</span>
              <select v-model="automationForm.watchdogEnabled" class="input">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
            <label class="field full">
              <span>Cron 表达式</span>
              <input v-model="automationForm.cron" class="input" />
            </label>
            <label class="field full">
              <span>任务名前缀</span>
              <input v-model="automationForm.taskNamePrefix" class="input" />
            </label>
            <label class="field full">
              <span>关键词</span>
              <input v-model="automationForm.keywordsText" class="input" />
            </label>
            <label class="field full">
              <span>城市</span>
              <input v-model="automationForm.citiesText" class="input" />
            </label>
            <label class="field">
              <span>页数</span>
              <input v-model.number="automationForm.pageCount" type="number" min="1" max="10" class="input" />
            </label>
            <label class="field">
              <span>优先级</span>
              <input v-model.number="automationForm.priority" type="number" min="1" max="10" class="input" />
            </label>
            <label class="field full">
              <span>工作目录</span>
              <input v-model="automationForm.workspace" class="input" placeholder="留空时自动识别仓库根目录" />
            </label>
          </div>
          <footer class="panel-foot">
            <GlowButton variant="primary" :loading="automationSaving" @click="handleSaveAutomation">
              <Settings2 :size="16" />
              保存自动化配置
            </GlowButton>
          </footer>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>创建采集任务</h2>
            <span class="sub">手动发起单次采集</span>
          </header>
          <div class="panel-body form-grid">
            <label class="field full">
              <span>任务名称</span>
              <input v-model="taskForm.taskName" class="input" placeholder="例如：成都 Python 采集" />
            </label>
            <label class="field">
              <span>渠道</span>
              <select v-model="taskForm.channel" class="input">
                <option value="zhaopin">zhaopin</option>
                <option value="boss">boss</option>
                <option value="51job">51job</option>
                <option value="lagou">lagou</option>
              </select>
            </label>
            <label class="field">
              <span>优先级</span>
              <input v-model.number="taskForm.priority" type="number" min="1" max="10" class="input" />
            </label>
            <label class="field full">
              <span>关键词</span>
              <input v-model="taskForm.keywords" class="input" />
            </label>
            <label class="field">
              <span>城市</span>
              <input v-model="taskForm.city" class="input" />
            </label>
            <label class="field">
              <span>页数</span>
              <input v-model.number="taskForm.pageCount" type="number" min="1" max="10" class="input" />
            </label>
            <label class="field">
              <span>执行方式</span>
              <select v-model="taskForm.scheduleMode" class="input">
                <option v-for="option in scheduleModeOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>增量采集</span>
              <select v-model="taskForm.incremental" class="input">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
          </div>
          <footer class="panel-foot">
            <GlowButton variant="primary" :loading="creating" @click="handleCreateTask">
              <Plus :size="16" />
              {{ taskForm.scheduleMode === 'SCHEDULED' ? '创建定时模板' : '创建立即任务' }}
            </GlowButton>
          </footer>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>页面快照</h2>
            <span class="sub">下次计划刷新：{{ nextSnapshotTime }}</span>
          </header>
          <div class="panel-body card-grid">
            <article v-for="page in snapshotPages" :key="page.pageCode" class="metric-card">
              <span>{{ page.pageName }}</span>
              <strong>{{ formatTime(page.refreshedAt) }}</strong>
              <small>{{ page.refreshTrigger || '待刷新' }}</small>
            </article>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.collector-page {
  --bg: linear-gradient(180deg, #f6f8fb 0%, #edf2f7 100%);
  --panel: rgba(255, 255, 255, 0.94);
  --line: rgba(15, 23, 42, 0.08);
  --text: #122033;
  --muted: #5f7087;
  --primary: #0f766e;
  --primary-soft: rgba(15, 118, 110, 0.14);
  --danger: #c2410c;
  min-height: 100vh;
  padding: 28px;
  background: var(--bg);
  color: var(--text);
}

.hero,
.panel,
.metric-card,
.status-card,
.task-card,
.log-item {
  backdrop-filter: blur(14px);
  background: var(--panel);
  border: 1px solid var(--line);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
}

.hero,
.panel {
  border-radius: 24px;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  margin-bottom: 18px;
}

.hero-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero h1 {
  margin: 12px 0 10px;
  font-size: 34px;
  line-height: 1.1;
}

.hero p,
.sub,
.task-meta,
.log-text,
.metric-card small,
.status-card small {
  color: var(--muted);
}

.hero-actions,
.toolbar,
.row,
.task-top,
.task-actions,
.log-top,
.action-grid {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hero-actions,
.toolbar,
.action-grid {
  flex-wrap: wrap;
}

.banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-radius: 16px;
  margin-bottom: 16px;
}

.banner-error {
  background: rgba(239, 68, 68, 0.12);
  color: #991b1b;
}

.banner-success {
  background: rgba(34, 197, 94, 0.12);
  color: #166534;
}

.status-strip,
.metrics-grid,
.card-grid {
  display: grid;
  gap: 16px;
}

.status-strip {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-bottom: 18px;
}

.metrics-grid,
.card-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.metrics-grid {
  margin-bottom: 18px;
}

.progress-board {
  margin-bottom: 18px;
}

.progress-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.timeline-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.timeline-chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--primary);
  font-size: 12px;
  font-weight: 700;
}

.shard-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding-top: 0;
}

.shard-column {
  display: grid;
  gap: 12px;
}

.shard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.shard-head h3 {
  margin: 0;
  font-size: 15px;
}

.shard-list {
  display: grid;
  gap: 10px;
}

.shard-card {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.shard-card strong,
.shard-card small {
  display: block;
}

.shard-card small {
  margin-top: 6px;
  color: var(--muted);
}

.status-card,
.metric-card {
  border-radius: 20px;
  padding: 18px;
}

.status-card span,
.metric-card span {
  display: block;
  font-size: 13px;
  color: var(--muted);
}

.status-card strong,
.metric-card strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 24px;
  word-break: break-word;
}

.status-card.danger {
  border-color: rgba(239, 68, 68, 0.25);
}

.panel {
  margin-bottom: 18px;
}

.panel-head,
.panel-foot {
  padding: 20px 22px;
}

.panel-head {
  border-bottom: 1px solid var(--line);
}

.panel-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.panel-body {
  padding: 20px 22px;
}

.main-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field.full {
  grid-column: 1 / -1;
}

.field span {
  font-size: 13px;
  color: var(--muted);
}

.input {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: rgba(255, 255, 255, 0.88);
  color: var(--text);
}

.input.slim {
  width: auto;
  min-width: 150px;
}

.task-list,
.log-list {
  display: grid;
  gap: 14px;
}

.task-sections {
  display: grid;
  gap: 18px;
}

.task-section {
  display: grid;
  gap: 12px;
}

.task-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-section-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.task-card,
.log-item {
  border-radius: 18px;
  padding: 16px;
}

.task-card.active {
  border-color: rgba(15, 118, 110, 0.4);
  box-shadow: 0 22px 44px rgba(15, 118, 110, 0.12);
}

.task-top h3 {
  margin: 0;
  font-size: 17px;
}

.pill {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.pill-idle {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.pill-running {
  background: rgba(37, 99, 235, 0.14);
  color: #1d4ed8;
}

.pill-done {
  background: rgba(22, 163, 74, 0.14);
  color: #15803d;
}

.pill-danger {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.task-progress {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: center;
  margin: 12px 0;
}

.progress-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.18);
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #14b8a6 0%, #0f766e 100%);
}

.task-meta {
  display: grid;
  gap: 4px;
  margin: 0 0 12px;
  font-size: 13px;
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 12px;
  background: white;
  color: var(--text);
  cursor: pointer;
}

.mini-action.danger {
  color: var(--danger);
}

.empty-block {
  padding: 22px;
  text-align: center;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.1);
  color: var(--muted);
}

.log-level {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-weight: 700;
}

.log-text {
  margin: 10px 0 0;
  line-height: 1.55;
  white-space: pre-wrap;
}

.job-table {
  display: grid;
  gap: 10px;
}

.job-row {
  display: grid;
  grid-template-columns: 1.6fr 1.2fr 0.8fr 0.8fr 1fr;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(148, 163, 184, 0.08);
  align-items: center;
  font-size: 13px;
}

.job-head {
  font-weight: 700;
  background: rgba(15, 118, 110, 0.08);
}

.job-title {
  font-weight: 700;
}

@media (max-width: 1100px) {
  .main-grid,
  .shard-board {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .collector-page {
    padding: 18px;
  }

  .hero {
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .field.full {
    grid-column: auto;
  }

  .job-row {
    grid-template-columns: 1fr;
  }
}
</style>
