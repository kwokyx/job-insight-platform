<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { mapErrorMessage } from '../utils/errorMap'
import {
  createCrawlTask,
  fetchCrawlLiveOverview,
  fetchCrawlQuality,
  fetchCrawlTask,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  updateCrawlTaskStatus,
  fetchCrawlAutomationStatus,
  updateCrawlAutomationConfig,
  triggerCrawlAutomation
} from '../api'
import {
  Activity,
  AlertTriangle,
  Calendar,
  Clock3,
  Database,
  PauseCircle,
  PlayCircle,
  RefreshCw,
  Settings,
  SquareStack,
  TerminalSquare,
  Timer,
  Zap
} from 'lucide-vue-next'

const authStore = useAuthStore()
const toast = useToast()

const SOURCE_CHANNEL = 'zhaopin'
const DASHBOARD_REQUEST_TIMEOUT_MS = 15000
const LIVE_OVERVIEW_TIMEOUT_MS = 30000
const LIVE_OVERVIEW_RETRY_DELAY_MS = 800
const TASK_ACTION_TIMEOUT_MS = 8000
const CREATE_TASK_TIMEOUT_MS = 60000
const WATCHDOG_TIMEOUT_MS = 90000
const WATCHDOG_INITIAL_GRACE_MS = 180000
const WATCHDOG_RESTART_COOLDOWN_MS = 30000

const TARGET_COUNT_OPTIONS = [10, 20, 50, 100, 200, 300, 500, 800, 1000, 1500, 2000, 3000, 5000]
const CITY_OPTIONS = [
  { code: '530', label: '\u5317\u4EAC' },
  { code: '531', label: '\u5929\u6D25' },
  { code: '538', label: '\u4E0A\u6D77' },
  { code: '551', label: '\u91CD\u5E86' },
  { code: '635', label: '\u5357\u4EAC' },
  { code: '653', label: '\u676D\u5DDE' },
  { code: '736', label: '\u6B66\u6C49' },
  { code: '763', label: '\u5E7F\u5DDE' },
  { code: '765', label: '\u6DF1\u5733' },
  { code: '801', label: '\u6210\u90FD' }
]
const CITY_MAP = Object.fromEntries(CITY_OPTIONS.map((item) => [item.code, item.label]))
const CRON_PRESETS = [
  { label: '\u6BCF\u5C0F\u65F6', value: '0 0 * * * *', desc: '\u6BCF\u5C0F\u65F6\u6267\u884C\u4E00\u6B21' },
  { label: '\u6BCF2\u5C0F\u65F6', value: '0 0 */2 * * *', desc: '\u6BCF2\u5C0F\u65F6\u6267\u884C\u4E00\u6B21' },
  { label: '\u6BCF\u5929\u65E9\u4E0A8\u70B9', value: '0 0 8 * * *', desc: '\u6BCF\u65E5\u65E9\u4E0A8:00\u6267\u884C' },
  { label: '\u6BCF\u5929\u665A\u4E0A10\u70B9', value: '0 0 22 * * *', desc: '\u6BCF\u65E5\u665A\u4E0A22:00\u6267\u884C' },
  { label: '\u6BCF\u5929\u65E9\u665A\u5404\u4E00\u6B21', value: '0 0 8,22 * * *', desc: '\u6BCF\u5929 8:00 \u548C 22:00 \u6267\u884C' },
  { label: '\u5DE5\u4F5C\u65E5\u65E9\u4E0A9\u70B9', value: '0 0 9 * * MON-FRI', desc: '\u5468\u4E00\u5230\u5468\u4E94\u65E9\u4E0A9:00\u6267\u884C' }
]

const loading = ref(false)
const bootstrapLoading = ref(true)
const submitting = ref(false)
const actionTaskId = ref('')
const detailLoading = ref(false)
const logsLoading = ref(false)
const softError = ref('')
const lastRefreshAt = ref(0)

const tasks = ref([])
const totalTasks = ref(0)
const quality = ref({})
const liveOverview = ref({})
const logs = ref([])
const selectedTaskId = ref('')
const pinnedTaskId = ref('')

const pagination = ref({
  page: 1,
  pageSize: 8
})

const form = ref(defaultForm())
const pollingTimer = ref(null)
const clockTimer = ref(null)
const fastPollingUntil = ref(0)
const now = ref(Date.now())
const activeTab = ref('manual') // 'manual' | 'scheduled'
const automationStatus = ref({})
const automationLoading = ref(false)
const scheduleForm = ref(defaultScheduleForm())
const scheduleSubmitting = ref(false)
const crawledDataTab = ref('live') // 'live' | 'data'
const syncFeedback = ref({
  status: 'idle', // idle | running | success | timeout | error
  message: '',
  startedAt: '',
  finishedAt: '',
  beforeBizRows: 0,
  afterBizRows: 0,
  deltaBizRows: 0,
  latestSyncAt: ''
})

const watchdog = ref({
  taskId: '',
  visibleAt: 0,
  lastSignalAt: 0,
  lastSignature: '',
  armed: false,
  restarting: false,
  restartCount: 0,
  lastRestartAt: 0,
  message: ''
})

function defaultForm() {
  return {
    taskName: '',
    channel: SOURCE_CHANNEL,
    keywords: '',
    city: '801',
    targetCount: 100,
    priority: 5
  }
}

function defaultScheduleForm() {
  return {
    enabled: false,
    cronPreset: '0 0 8 * * *',
    watchdogEnabled: true,
    keywords: 'Python',
    cities: '801',
    targetCount: 100,
    priority: 5,
    incremental: true
  }
}

const taskRows = computed(() => tasks.value || [])
const selectedTask = computed(() => {
  return taskRows.value.find((item) => item.taskId === selectedTaskId.value) || null
})
const liveTask = computed(() => {
  const activeProgressTaskId = liveOverview.value?.activeProgress?.taskId
  if (selectedTask.value?.taskId === activeProgressTaskId) {
    return {
      ...selectedTask.value,
      ...liveOverview.value.activeProgress
    }
  }
  if (selectedTask.value) return selectedTask.value
  if (liveOverview.value?.activeProgress?.taskId) return liveOverview.value.activeProgress
  if (liveOverview.value?.latestTask?.taskId) return liveOverview.value.latestTask
  return taskRows.value[0] || null
})
const liveLog = computed(() => {
  if (logs.value.length > 0 && logs.value[0]?.taskId === liveTask.value?.taskId) {
    return logs.value[0]
  }
  if (liveOverview.value?.activeProgress?.taskId === liveTask.value?.taskId && liveOverview.value?.activeProgress?.latestLog) {
    return liveOverview.value.activeProgress.latestLog
  }
  return (liveOverview.value?.latestLogs || []).find((item) => item.taskId === liveTask.value?.taskId) || null
})
const progressStats = computed(() => {
  const task = liveTask.value || {}
  const total = toNumber(task.totalCount ?? task.targetCount)
  const finished = toNumber(task.finishedCount)
  const status = deriveRuntimeStatus(task)
  const rawPercent = total > 0 ? Math.min(100, Math.round((finished / total) * 100)) : 0
  // Terminal tasks frequently end with tiny rounding gaps (e.g. 99/100).
  // When status is completed and progress is already >=99%, normalize to 100%.
  const normalizeToDone = status === 2 && total > 0 && rawPercent >= 99
  const percent = normalizeToDone ? 100 : rawPercent
  return {
    total,
    finished: normalizeToDone ? total : finished,
    percent
  }
})
const shardStats = computed(() => {
  const stats = liveOverview.value?.activeProgress?.taskId === liveTask.value?.taskId
    ? (liveOverview.value?.activeProgress?.shardStats || {})
    : {}
  return {
    total: toNumber(stats.total),
    pending: toNumber(stats.pending),
    running: toNumber(stats.running),
    completed: toNumber(stats.completed),
    failed: toNumber(stats.failed)
  }
})
const activeShards = computed(() => {
  if (liveOverview.value?.activeProgress?.taskId !== liveTask.value?.taskId) return []
  return normalizeShardList(liveOverview.value?.activeProgress?.activeShards || [])
})
const completedShards = computed(() => {
  if (liveOverview.value?.activeProgress?.taskId !== liveTask.value?.taskId) return []
  return normalizeShardList(liveOverview.value?.activeProgress?.completedShards || [])
})
const runtimeStatus = computed(() => deriveRuntimeStatus(liveTask.value))
const statusMeta = computed(() => getStatusMeta(runtimeStatus.value))
const stageText = computed(() => {
  if (liveOverview.value?.activeProgress?.taskId === liveTask.value?.taskId && liveOverview.value?.activeProgress?.stageLabel) {
    return liveOverview.value.activeProgress.stageLabel
  }
  return statusMeta.value.label
})
const stageDetail = computed(() => {
  if (liveOverview.value?.activeProgress?.taskId === liveTask.value?.taskId && liveOverview.value?.activeProgress?.stageDetail) {
    return liveOverview.value.activeProgress.stageDetail
  }
  if (runtimeStatus.value === 1) return '任务运行中，页面会持续刷新进度、日志和分片状态。'
  if (runtimeStatus.value === 0) return '任务已创建，等待调度中心开始执行。'
  if (runtimeStatus.value === 2) return '任务已完成，可继续查看采集结果与最近日志。'
  if (runtimeStatus.value === 3) return '任务已暂停或异常结束，请查看最近日志定位原因。'
  return '暂无实时任务。'
})
const watchdogCountdown = computed(() => {
  if (!watchdog.value.taskId) return 0
  const base = watchdog.value.armed ? watchdog.value.lastSignalAt : watchdog.value.visibleAt
  const timeout = watchdog.value.armed ? WATCHDOG_TIMEOUT_MS : WATCHDOG_INITIAL_GRACE_MS
  return Math.max(0, timeout - (now.value - base))
})
const qualitySummary = computed(() => {
  const syncState = quality.value?.syncState || {}
  const summary = liveOverview.value?.summary || {}
  return {
    totalJobs: toNumber(syncState.totalJobs ?? summary.totalJobs),
    latestSyncAt: syncState.latestSyncAt || summary.latestSyncAt || '--',
    todayIncrement: toNumber(syncState.todayIncrement ?? summary.todayIncrement),
    runningTasks: Array.isArray(liveOverview.value?.runningTasks) ? liveOverview.value.runningTasks.length : 0,
    latestNewCount: toNumber(syncState.latestNewCount),
    latestUpdatedCount: toNumber(syncState.latestUpdatedCount),
    latestDuplicateCount: toNumber(syncState.latestDuplicateCount),
    bizRows: toNumber(syncState.bizRows)
  }
})
const recentSyncedJobs = computed(() => {
  const rows = liveOverview.value?.summary?.recentJobs
  return Array.isArray(rows) ? rows.slice(0, 8) : []
})

const totalPages = computed(() => Math.max(1, Math.ceil(totalTasks.value / pagination.value.pageSize)))

function changePage(delta) {
  const newPage = pagination.value.page + delta
  if (newPage > 0 && newPage <= totalPages.value) {
    pagination.value.page = newPage
    refreshDashboard({ silent: false, keepSelection: true, includeLogs: false })
  }
}

function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function formatTime(value) {
  if (!value) return '--'
  return String(value)
}

function formatRelative(ms) {
  if (!ms || ms <= 0) return '0 秒'
  const seconds = Math.ceil(ms / 1000)
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return remain ? `${minutes} 分 ${remain} 秒` : `${minutes} 分`
}

function formatList(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join('、') || '--'
  if (value == null || value === '') return '--'
  return String(value)
}

function resolveCityLabel(value) {
  if (Array.isArray(value)) return value.map((item) => resolveCityLabel(item)).filter(Boolean)
  if (!value) return ''
  return CITY_MAP[value] || String(value)
}

function normalizeShardList(items) {
  return (items || []).map((item) => ({
    ...item,
    city: resolveCityLabel(item.city),
    workerId: item.workerId || item.worker_id || '采集节点'
  }))
}

function getDisplayStatus(task) {
  const status = Number(task.status ?? 0)
  const total = toNumber(task.totalCount ?? task.targetCount)
  const finished = toNumber(task.finishedCount)
  const rawPercent = total > 0 ? Math.min(100, Math.round((finished / total) * 100)) : 0
  
  if (status === 3 && total > 0 && rawPercent >= 99) {
    return 2 // fake it as completed
  }
  return status
}

function getDisplayFinishedCount(task) {
  const status = Number(task.status ?? 0)
  const total = toNumber(task.totalCount ?? task.targetCount)
  const finished = toNumber(task.finishedCount)
  const rawPercent = total > 0 ? Math.min(100, Math.round((finished / total) * 100)) : 0
  
  if ((status === 2 || status === 3) && total > 0 && rawPercent >= 99) {
    return total
  }
  return finished
}

function deriveRuntimeStatus(task) {
  if (!task) return -1
  const raw = Number(task.status)
  if (Number.isFinite(raw)) return raw
  return -1
}

function getStatusMeta(status) {
  if (status === 1) return { label: '运行中', tone: 'running' }
  if (status === 2) return { label: '已完成', tone: 'success' }
  if (status === 3) return { label: '已暂停', tone: 'danger' }
  return { label: '待执行', tone: 'queued' }
}

function buildSignalSignature() {
  return JSON.stringify({
    taskId: liveTask.value?.taskId || '',
    finishedCount: progressStats.value.finished,
    totalCount: progressStats.value.total,
    stage: liveOverview.value?.activeProgress?.stageKey || '',
    shardPending: shardStats.value.pending,
    shardRunning: shardStats.value.running,
    shardCompleted: shardStats.value.completed,
    latestLogTime: liveLog.value?.createTime || '',
    latestLogMessage: liveLog.value?.message || ''
  })
}

function hasMeaningfulSignal() {
  if (!liveTask.value?.taskId) return false
  if (progressStats.value.finished > 0) return true
  if (shardStats.value.pending > 0 || shardStats.value.running > 0 || shardStats.value.completed > 0 || shardStats.value.failed > 0) {
    return true
  }
  if (liveLog.value?.message) return true
  const stageKey = liveOverview.value?.activeProgress?.stageKey
  return ['dispatching', 'crawling', 'finishing'].includes(stageKey)
}

function resetWatchdog() {
  watchdog.value = {
    taskId: '',
    visibleAt: 0,
    lastSignalAt: 0,
    lastSignature: '',
    armed: false,
    restarting: false,
    restartCount: 0,
    lastRestartAt: 0,
    message: ''
  }
}

function updateWatchdog() {
  const task = liveTask.value
  const status = runtimeStatus.value
  if (!task?.taskId || (status !== 0 && status !== 1)) {
    resetWatchdog()
    return
  }

  const signature = buildSignalSignature()
  const meaningfulSignal = hasMeaningfulSignal()

  if (watchdog.value.taskId !== task.taskId) {
    watchdog.value = {
      taskId: task.taskId,
      visibleAt: now.value,
      lastSignalAt: meaningfulSignal ? now.value : 0,
      lastSignature: meaningfulSignal ? signature : '',
      armed: meaningfulSignal,
      restarting: false,
      restartCount: 0,
      lastRestartAt: 0,
      message: meaningfulSignal
        ? '已收到进度信号，正在持续跟踪任务。'
        : '任务已进入监控，正在等待首个进度信号。'
    }
    return
  }

  if (meaningfulSignal && signature !== watchdog.value.lastSignature) {
    watchdog.value.lastSignalAt = now.value
    watchdog.value.lastSignature = signature
    watchdog.value.armed = true
    watchdog.value.message = '收到新的进度信号，监控正常。'
    return
  }

  if (watchdog.value.restarting) {
    watchdog.value.message = '任务长时间无新进度，正在自动重启。'
    return
  }

  if (!watchdog.value.armed) {
    const waitMs = now.value - watchdog.value.visibleAt
    watchdog.value.message = `等待首个进度信号，剩余 ${formatRelative(WATCHDOG_INITIAL_GRACE_MS - waitMs)}。`
    if (waitMs >= WATCHDOG_INITIAL_GRACE_MS) {
      triggerAutoRestart('首次启动超过 60 秒仍无信号')
    }
    return
  }

  const silentMs = now.value - watchdog.value.lastSignalAt
  watchdog.value.message = `最近 ${formatRelative(silentMs)} 没有新进度，剩余 ${formatRelative(WATCHDOG_TIMEOUT_MS - silentMs)}。`
  if (silentMs >= WATCHDOG_TIMEOUT_MS) {
    triggerAutoRestart('运行中超过 30 秒无新信号')
  }
}

async function triggerAutoRestart(reason) {
  if (!watchdog.value.taskId || watchdog.value.restarting) return
  if (now.value - watchdog.value.lastRestartAt < WATCHDOG_RESTART_COOLDOWN_MS) return
  const taskId = watchdog.value.taskId
  watchdog.value.restarting = true
  watchdog.value.lastRestartAt = now.value
  watchdog.value.message = `${reason}，平台正在自动重启任务。`
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 3 }, { timeoutMs: TASK_ACTION_TIMEOUT_MS }).catch(() => null)
    await sleep(800)
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 1 }, { timeoutMs: TASK_ACTION_TIMEOUT_MS })
    watchdog.value.restartCount += 1
    watchdog.value.visibleAt = Date.now()
    watchdog.value.lastSignalAt = 0
    watchdog.value.lastSignature = ''
    watchdog.value.armed = false
    fastPollingUntil.value = Date.now() + 45000
    patchTask(taskId, { status: 1 })
    toast.success(`任务 ${taskId} 已自动重启`)
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
  } catch (error) {
    toast.error(`自动重启失败：${mapErrorMessage(error)}`)
  } finally {
    watchdog.value.restarting = false
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function isTimeoutLikeError(error) {
  const text = String(error?.message || '').toLowerCase()
  return text.includes('timeout') || text.includes('timed out') || text.includes('abort')
}

async function fetchLiveOverviewWithRetry(token) {
  try {
    return await fetchCrawlLiveOverview(token, { timeoutMs: LIVE_OVERVIEW_TIMEOUT_MS })
  } catch (error) {
    if (!isTimeoutLikeError(error)) throw error
    await sleep(LIVE_OVERVIEW_RETRY_DELAY_MS)
    return fetchCrawlLiveOverview(token, { timeoutMs: LIVE_OVERVIEW_TIMEOUT_MS })
  }
}

function patchTask(taskId, patch) {
  tasks.value = tasks.value.map((item) => (item.taskId === taskId ? { ...item, ...patch } : item))
}

function resetForm() {
  form.value = defaultForm()
}

function prependTask(task) {
  tasks.value = [task, ...tasks.value.filter((item) => item.taskId !== task.taskId)].slice(0, pagination.value.pageSize)
  totalTasks.value += 1
}

function pinTask(taskId) {
  if (!taskId) return
  selectedTaskId.value = taskId
  pinnedTaskId.value = taskId
}

async function refreshDashboard(options = {}) {
  const { silent = false, keepSelection = true, includeLogs = true } = options
  if (!silent) loading.value = true
  softError.value = ''

  const token = authStore.token
  const page = pagination.value.page
  const pageSize = pagination.value.pageSize
  const previousSelection = keepSelection ? (pinnedTaskId.value || selectedTaskId.value) : ''

  const [tasksRes, liveRes, qualityRes] = await Promise.allSettled([
    fetchCrawlTasks(token, { page, pageSize, timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS }),
    fetchLiveOverviewWithRetry(token),
    fetchCrawlQuality(token, { timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS })
  ])

  if (tasksRes.status === 'fulfilled') {
    tasks.value = tasksRes.value.data || []
    totalTasks.value = tasksRes.value.total || 0
    if (previousSelection && !tasks.value.some((item) => item.taskId === previousSelection)) {
      try {
        const pinnedTask = await fetchCrawlTask(token, previousSelection)
        if (pinnedTask?.taskId) {
          tasks.value = [pinnedTask, ...tasks.value.filter((item) => item.taskId !== pinnedTask.taskId)]
        }
      } catch {
        // Ignore pinned task fetch failure to keep dashboard responsive.
      }
    }
  } else {
    softError.value = `任务列表刷新失败：${mapErrorMessage(tasksRes.reason)}`
  }

  if (liveRes.status === 'fulfilled') {
    liveOverview.value = liveRes.value || {}
  } else if (!softError.value) {
    const hasCachedLiveData = !!(
      liveOverview.value?.activeProgress?.taskId
      || liveOverview.value?.latestTask?.taskId
      || (Array.isArray(liveOverview.value?.runningTasks) && liveOverview.value.runningTasks.length > 0)
    )
    if (!hasCachedLiveData) {
      softError.value = `实时面板刷新失败：${mapErrorMessage(liveRes.reason)}`
    }
  }

  if (qualityRes.status === 'fulfilled') {
    quality.value = qualityRes.value || {}
  }

  if (!previousSelection) {
    selectedTaskId.value = liveOverview.value?.activeProgress?.taskId
      || liveOverview.value?.latestTask?.taskId
      || tasks.value[0]?.taskId
      || ''
    if (selectedTaskId.value && !pinnedTaskId.value) {
      pinnedTaskId.value = selectedTaskId.value
    }
  } else if (tasks.value.some((item) => item.taskId === previousSelection)) {
    selectedTaskId.value = previousSelection
  } else {
    selectedTaskId.value = liveOverview.value?.activeProgress?.taskId
      || liveOverview.value?.latestTask?.taskId
      || tasks.value[0]?.taskId
      || ''
    if (!pinnedTaskId.value && selectedTaskId.value) {
      pinnedTaskId.value = selectedTaskId.value
    }
  }

  if (includeLogs && selectedTaskId.value) {
    await loadTaskDetails(selectedTaskId.value, true)
  }

  lastRefreshAt.value = Date.now()
  bootstrapLoading.value = false
  loading.value = false
}

async function loadTaskDetails(taskId, logsOnly = false) {
  if (!taskId) return
  if (!logsOnly) detailLoading.value = true
  logsLoading.value = true
  try {
    const token = authStore.token
    const requests = [
      fetchCrawlTaskLogs(token, taskId, { page: 1, pageSize: 20 })
    ]
    if (!logsOnly) {
      requests.unshift(fetchCrawlTask(token, taskId))
    }
    const results = await Promise.all(requests)
    if (!logsOnly) {
      const detail = results[0]
      patchTask(taskId, detail)
      logs.value = results[1]?.data || []
    } else {
      logs.value = results[0]?.data || []
    }
  } catch (error) {
    softError.value = `任务详情刷新失败：${mapErrorMessage(error)}`
  } finally {
    detailLoading.value = false
    logsLoading.value = false
  }
}

async function handleCreateTask() {
  if (submitting.value) return
  submitting.value = true
  softError.value = ''
  try {
    const payload = {
      taskName: form.value.taskName?.trim() || undefined,
      channel: SOURCE_CHANNEL,
      keywords: form.value.keywords?.trim() || undefined,
      city: form.value.city,
      targetCount: Number(form.value.targetCount) || 100,
      priority: Number(form.value.priority) || 5
    }
    const result = await createCrawlTask(authStore.token, payload, { timeoutMs: CREATE_TASK_TIMEOUT_MS })
    const optimisticTask = {
      taskId: result.taskId,
      taskName: payload.taskName || `${payload.keywords || '\u91C7\u96C6\u4EFB\u52A1'}-${resolveCityLabel(payload.city)}-${payload.targetCount}\u6761`,
      channel: SOURCE_CHANNEL,
      keywords: payload.keywords ? [payload.keywords] : [],
      city: [resolveCityLabel(payload.city)],
      targetCount: payload.targetCount,
      priority: payload.priority,
      status: Number(result.status ?? 0),
      createTime: formatTime(new Date().toISOString()),
      updateTime: formatTime(new Date().toISOString()),
      executionMode: result.executionMode || 'scheduler-center'
    }
    prependTask(optimisticTask)
    pinTask(optimisticTask.taskId)
    logs.value = []
    form.value.taskName = ''
    fastPollingUntil.value = Date.now() + 20000
    toast.success('\u4EFB\u52A1\u5DF2\u521B\u5EFA\uFF0C\u8BF7\u70B9\u51FB\u201C\u8FD0\u884C\u4EFB\u52A1\u201D\u5F00\u59CB\u91C7\u96C6\u3002')
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
  } catch (error) {
    softError.value = `创建任务失败：${mapErrorMessage(error)}`
    toast.error(softError.value)
  } finally {
    submitting.value = false
  }
}

async function handleTaskAction(taskId, status, successText, optimisticPatch) {
  if (!taskId || actionTaskId.value) return
  actionTaskId.value = taskId
  patchTask(taskId, optimisticPatch)
  fastPollingUntil.value = Date.now() + 30000
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status }, { timeoutMs: TASK_ACTION_TIMEOUT_MS })
    toast.success(successText)
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
  } catch (error) {
    toast.error(`${successText}失败：${mapErrorMessage(error)}`)
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
  } finally {
    actionTaskId.value = ''
  }
}

function handleRunTask(taskId) {
  pinTask(taskId)
  handleTaskAction(taskId, 1, '\u4EFB\u52A1\u5DF2\u5F00\u59CB', { status: 1 })
}

function handlePauseTask(taskId) {
  pinTask(taskId)
  handleTaskAction(taskId, 3, '\u4EFB\u52A1\u5DF2\u505C\u6B62', { status: 3 })
}

async function handleSyncTask(taskId) {
  if (!taskId || actionTaskId.value) return
  pinTask(taskId)
  actionTaskId.value = taskId
  const beforeSyncAt = quality.value?.syncState?.latestSyncAt || ''
  const beforeBizRows = Number(quality.value?.syncState?.bizRows || 0)
  syncFeedback.value = {
    status: 'running',
    message: '正在触发同步并等待入库结果...',
    startedAt: formatTime(new Date().toISOString()),
    finishedAt: '',
    beforeBizRows,
    afterBizRows: beforeBizRows,
    deltaBizRows: 0,
    latestSyncAt: beforeSyncAt || '--'
  }
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 2 }, { timeoutMs: 15000 })
    toast.success('同步已触发，后台处理中（通常 10-30 秒完成）')
    let completed = false
    for (let i = 0; i < 6; i += 1) {
      await sleep(3000)
      await refreshDashboard({ silent: true, keepSelection: true, includeLogs: i === 0 })
      const latestSyncAt = quality.value?.syncState?.latestSyncAt || ''
      const afterBizRows = Number(quality.value?.syncState?.bizRows || 0)
      const deltaBizRows = afterBizRows - beforeBizRows
      syncFeedback.value = {
        ...syncFeedback.value,
        afterBizRows,
        deltaBizRows,
        latestSyncAt: latestSyncAt || '--'
      }
      if (latestSyncAt && latestSyncAt !== beforeSyncAt) {
        completed = true
        syncFeedback.value = {
          ...syncFeedback.value,
          status: 'success',
          message: '同步完成',
          finishedAt: formatTime(new Date().toISOString())
        }
        toast.success(`同步完成，入库更新时间：${latestSyncAt}`)
        break
      }
    }
    if (!completed) {
      syncFeedback.value = {
        ...syncFeedback.value,
        status: 'timeout',
        message: '同步已触发，但在当前等待窗口内未拿到完成信号',
        finishedAt: formatTime(new Date().toISOString())
      }
    }
  } catch (error) {
    syncFeedback.value = {
      ...syncFeedback.value,
      status: 'error',
      message: `同步触发失败：${mapErrorMessage(error)}`,
      finishedAt: formatTime(new Date().toISOString())
    }
    toast.error(`同步触发失败：${mapErrorMessage(error)}`)
  } finally {
    actionTaskId.value = ''
  }
}

async function loadAutomationStatus() {
  automationLoading.value = true
  try {
    const result = await fetchCrawlAutomationStatus(authStore.token)
    automationStatus.value = result || {}
    const settings = result?.settings || {}
    scheduleForm.value.enabled = !!settings.enabled
    scheduleForm.value.watchdogEnabled = settings.watchdogEnabled !== false
    if (settings.cron) {
      const matchPreset = CRON_PRESETS.find(p => p.value === settings.cron)
      if (matchPreset) scheduleForm.value.cronPreset = settings.cron
    }
    if (settings.keywords?.length) scheduleForm.value.keywords = settings.keywords.join(', ')
    if (settings.cities?.length) scheduleForm.value.cities = settings.cities.join(',')
    if (settings.targetCount) scheduleForm.value.targetCount = settings.targetCount
    if (settings.priority) scheduleForm.value.priority = settings.priority
    scheduleForm.value.incremental = settings.incremental !== false
  } catch (error) {
    softError.value = `\u52A0\u8F7D\u5B9A\u65F6\u914D\u7F6E\u5931\u8D25\uFF1A${mapErrorMessage(error)}`
  } finally {
    automationLoading.value = false
  }
}

async function handleSaveSchedule() {
  if (scheduleSubmitting.value) return
  scheduleSubmitting.value = true
  softError.value = ''
  try {
    const cron = scheduleForm.value.cronPreset
    const keywords = scheduleForm.value.keywords.split(/[,\uFF0C\u3001\s]+/).map(k => k.trim()).filter(Boolean)
    const cities = scheduleForm.value.cities.split(/[,\uFF0C\u3001]+/).map(c => c.trim()).filter(Boolean)
    await updateCrawlAutomationConfig(authStore.token, {
      enabled: scheduleForm.value.enabled, cron,
      watchdogEnabled: scheduleForm.value.watchdogEnabled,
      keywords, cities,
      targetCount: Number(scheduleForm.value.targetCount) || 100,
      priority: Number(scheduleForm.value.priority) || 5,
      incremental: scheduleForm.value.incremental
    })
    toast.success('\u5B9A\u65F6\u4EFB\u52A1\u914D\u7F6E\u5DF2\u4FDD\u5B58')
    await loadAutomationStatus()
  } catch (error) {
    softError.value = `\u4FDD\u5B58\u5B9A\u65F6\u914D\u7F6E\u5931\u8D25\uFF1A${mapErrorMessage(error)}`
    toast.error(softError.value)
  } finally {
    scheduleSubmitting.value = false
  }
}

async function handleTriggerNow() {
  if (scheduleSubmitting.value) return
  scheduleSubmitting.value = true
  softError.value = ''
  try {
    await triggerCrawlAutomation(authStore.token, {})
    toast.success('\u5B9A\u65F6\u4EFB\u52A1\u5DF2\u7ACB\u5373\u89E6\u53D1')
    fastPollingUntil.value = Date.now() + 30000
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
  } catch (error) {
    softError.value = `\u89E6\u53D1\u5931\u8D25\uFF1A${mapErrorMessage(error)}`
    toast.error(softError.value)
  } finally {
    scheduleSubmitting.value = false
  }
}

const cronPresetDesc = computed(() => {
  const preset = CRON_PRESETS.find(p => p.value === scheduleForm.value.cronPreset)
  return preset ? preset.desc : ''
})

const automationExecSummary = computed(() => {
  const s = automationStatus.value?.settings || {}
  return {
    enabled: !!s.enabled,
    cron: s.cron || '--',
    lastTriggeredAt: s.lastTriggeredAt || '--',
    lastTriggerStatus: s.lastTriggerStatus || '--'
  }
})

function startPolling() {
  stopPolling()
  const tick = async () => {
    await refreshDashboard({ silent: true, keepSelection: true, includeLogs: true })
    const interval = Date.now() < fastPollingUntil.value ? 2500 : 6000
    pollingTimer.value = setTimeout(tick, interval)
  }
  const interval = Date.now() < fastPollingUntil.value ? 2500 : 6000
  pollingTimer.value = setTimeout(tick, interval)
}

function stopPolling() {
  if (pollingTimer.value) {
    clearTimeout(pollingTimer.value)
    pollingTimer.value = null
  }
}

watch(selectedTaskId, async (taskId, previous) => {
  if (!taskId || taskId === previous) return
  await loadTaskDetails(taskId)
})

watch(
  () => [
    liveTask.value?.taskId,
    runtimeStatus.value,
    progressStats.value.finished,
    progressStats.value.total,
    shardStats.value.pending,
    shardStats.value.running,
    shardStats.value.completed,
    shardStats.value.failed,
    liveLog.value?.createTime,
    liveLog.value?.message
  ],
  () => updateWatchdog()
)

onMounted(async () => {
  clockTimer.value = setInterval(() => {
    now.value = Date.now()
    updateWatchdog()
  }, 1000)
  await refreshDashboard({ includeLogs: true })
  loadAutomationStatus()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
  if (clockTimer.value) {
    clearInterval(clockTimer.value)
    clockTimer.value = null
  }
})
</script>

<template>
  <div class="collector-page page-animate">
    <div class="collector-layout">
      <aside class="collector-sidebar" aria-label="数据采集模块导航">
        <div class="collector-sidebar-inner">
          <h1 class="collector-hero-title">数据采集</h1>
          <nav class="collector-nav" aria-label="采集模块章节导航">
            <div class="collector-nav-group">
              <div class="collector-nav-group-label">采集配置</div>
              <ul class="collector-nav-list" role="tablist">
                <li>
                  <button
                    type="button"
                    class="collector-nav-link"
                    :class="{ 'is-active': activeTab === 'manual' }"
                    role="tab"
                    :aria-selected="activeTab === 'manual'"
                    @click="activeTab = 'manual'"
                  >
                    <span class="collector-nav-link-label">手动任务</span>
                  </button>
                </li>
                <li>
                  <button
                    type="button"
                    class="collector-nav-link"
                    :class="{ 'is-active': activeTab === 'scheduled' }"
                    role="tab"
                    :aria-selected="activeTab === 'scheduled'"
                    @click="activeTab = 'scheduled'; loadAutomationStatus()"
                  >
                    <span class="collector-nav-link-label">定时任务</span>
                  </button>
                </li>
              </ul>
            </div>
          </nav>
        </div>
      </aside>

      <div class="collector-content">
        <div class="collector-hero">
          <div class="collector-hero-row">
            <div class="collector-hero-copy">
              <h2 class="collector-hero-title" style="font-size: clamp(20px, 1.8vw, 24px);">分布式数据采集</h2>
              <p class="collector-panel-sub" style="margin-top: 6px; max-width: 800px;">
                任务创建、实时监控、自动重启统一在这个页面完成。
                创建任务后不会再自动阻塞启动链路。页面会持续刷新任务、实时日志、分片状态，并在首次 60 秒无信号或运行中 30 秒无信号时自动重启。
              </p>
            </div>
            <div class="collector-hero-side">
              <button class="ghost-btn" :disabled="loading" @click="refreshDashboard({ includeLogs: true })">
                <RefreshCw :size="16" />
                <span>{{ loading ? '刷新中' : '立即刷新' }}</span>
              </button>
            </div>
          </div>
          
          <div class="summary-grid compact" style="margin-top: 8px;">
            <div class="summary-tile">
              <span>累计职位</span>
              <strong>{{ qualitySummary.totalJobs || 0 }}</strong>
            </div>
            <div class="summary-tile">
              <span>运行任务</span>
              <strong>{{ qualitySummary.runningTasks }}</strong>
            </div>
            <div class="summary-tile">
              <span>今日增量</span>
              <strong>{{ qualitySummary.todayIncrement }}</strong>
            </div>
            <div class="summary-tile">
              <span>最近刷新</span>
              <strong>{{ lastRefreshAt ? formatTime(new Date(lastRefreshAt).toLocaleTimeString('zh-CN', { hour12: false })) : '--' }}</strong>
            </div>
          </div>
        </div>

        <div v-if="softError" class="collector-banner error">{{ softError }}</div>

        <Transition name="report-section" mode="out-in">
          <section :key="activeTab" class="collector-main">
            <!-- Form panel -->
            <article class="collector-panel control-panel">
              <header class="collector-panel-head">
                <div class="collector-panel-copy">
                  <h2 class="collector-panel-title">
                    <Zap v-if="activeTab === 'manual'" :size="15" />
                    <Calendar v-else :size="15" />
                    {{ activeTab === 'manual' ? '新建采集任务' : '定时采集' }}
                  </h2>
                  <p class="collector-panel-sub">
                    {{ activeTab === 'manual' ? '选择城市和目标数量，创建后手动运行。' : '配置 Cron 实现自动定时采集。' }}
                  </p>
                </div>
                <span v-if="activeTab === 'scheduled'" class="result-badge" :class="automationExecSummary.enabled ? 'is-live' : 'is-idle'">
                  {{ automationExecSummary.enabled ? '已启用' : '未启用' }}
                </span>
                <span v-else class="collector-panel-badge">输入</span>
              </header>

              <div class="collector-panel-body">
                <template v-if="activeTab === 'manual'">
                  <div class="form-grid">
                    <label class="field">
                      <span class="field-label">任务名称</span>
                      <input v-model.trim="form.taskName" class="collector-input" type="text" placeholder="例如：成都 Java 职位采集" />
                    </label>
                    <label class="field">
                      <span class="field-label">关键词</span>
                      <input v-model.trim="form.keywords" class="collector-input" type="text" placeholder="例如：Java、前端、数据分析" />
                    </label>
                    <label class="field">
                      <span class="field-label">城市</span>
                      <select v-model="form.city" class="collector-input">
                        <option v-for="city in CITY_OPTIONS" :key="city.code" :value="city.code">{{ city.label }}</option>
                      </select>
                    </label>
                    <label class="field">
                      <span class="field-label">目标条数</span>
                      <select v-model="form.targetCount" class="collector-input">
                        <option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 条</option>
                      </select>
                    </label>
                    <label class="field">
                      <span class="field-label">优先级</span>
                      <input v-model.number="form.priority" class="collector-input" type="number" min="1" max="10" />
                    </label>
                    <label class="field">
                      <span class="field-label">渠道</span>
                      <input :value="'智联招聘'" class="collector-input" type="text" disabled />
                    </label>
                  </div>
                  <div class="panel-actions">
                    <button class="primary-btn" :disabled="submitting" @click="handleCreateTask">
                      <span>{{ submitting ? '创建中…' : '创建任务' }}</span>
                    </button>
                    <button class="ghost-btn" :disabled="submitting" @click="resetForm">重置</button>
                  </div>
                </template>

                <template v-else>
                  <div v-if="automationLoading" class="placeholder small">加载中…</div>
                  <template v-else>
                    <div class="form-grid">
                      <label class="field switch-label">
                        <span class="field-label" style="display: flex; align-items: center; justify-content: space-between;">
                          启用定时采集
                          <label class="toggle-switch"><input v-model="scheduleForm.enabled" type="checkbox" /><span class="toggle-slider"></span></label>
                        </span>
                      </label>
                      <label class="field switch-label">
                        <span class="field-label" style="display: flex; align-items: center; justify-content: space-between;">
                          增量采集
                          <label class="toggle-switch"><input v-model="scheduleForm.incremental" type="checkbox" /><span class="toggle-slider"></span></label>
                        </span>
                      </label>
                      <label class="field">
                        <span class="field-label">执行频率</span>
                        <select v-model="scheduleForm.cronPreset" class="collector-input">
                          <option v-for="p in CRON_PRESETS" :key="p.value" :value="p.value">{{ p.label }}</option>
                        </select>
                        <p v-if="cronPresetDesc" class="form-hint" style="margin-top: 4px;">{{ cronPresetDesc }}</p>
                      </label>
                      <label class="field">
                        <span class="field-label">关键词（逗号分隔）</span>
                        <input v-model.trim="scheduleForm.keywords" class="collector-input" type="text" placeholder="Python, Java" />
                      </label>
                      <label class="field">
                        <span class="field-label">城市编码（逗号分隔）</span>
                        <input v-model.trim="scheduleForm.cities" class="collector-input" type="text" placeholder="801, 763, 530" />
                      </label>
                      <label class="field">
                        <span class="field-label">目标条数</span>
                        <select v-model="scheduleForm.targetCount" class="collector-input">
                          <option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 条</option>
                        </select>
                      </label>
                    </div>
                    <div class="panel-actions">
                      <button class="primary-btn" :disabled="scheduleSubmitting" @click="handleSaveSchedule">
                        <Settings :size="14" />
                        <span>{{ scheduleSubmitting ? '保存中…' : '保存配置' }}</span>
                      </button>
                      <button class="ghost-btn" :disabled="scheduleSubmitting" @click="handleTriggerNow">
                        <Timer :size="14" />
                        <span>立即触发</span>
                      </button>
                    </div>
                  </template>
                </template>
              </div>
            </article>

            <!-- Result Grid to hold Live and Tasks -->
            <div class="result-grid">
              <article class="collector-panel result-panel">
                 <header class="collector-panel-head">
                   <div class="collector-panel-copy">
                     <h2 class="collector-panel-title">实时任务看板</h2>
                     <p class="collector-panel-sub">实时展示当前任务状态、日志、分片和自动重启监控。</p>
                   </div>
                   <span class="result-badge" :class="statusMeta.tone === 'running' ? 'is-live' : (statusMeta.tone === 'success' ? 'is-live' : 'is-idle')">{{ stageText }}</span>
                 </header>

                 <div class="collector-panel-body">
                   <div v-if="bootstrapLoading" class="placeholder">正在加载采集面板…</div>
                   <template v-else-if="liveTask">
                     <div class="task-summary">
                       <div>
                         <p class="task-name">{{ liveTask.taskName || liveTask.taskId }}</p>
                         <p class="task-meta">
                           任务 ID：{{ liveTask.taskId }} · 城市：{{ formatList(resolveCityLabel(liveTask.city)) }} · 关键词：{{ formatList(liveTask.keywords) }}
                         </p>
                       </div>
                       <div class="action-row">
                         <button class="icon-btn success" :disabled="actionTaskId === liveTask.taskId || runtimeStatus === 1" @click="handleRunTask(liveTask.taskId)">
                           <PlayCircle :size="16" />
                           <span>运行</span>
                         </button>
                         <button class="icon-btn warn" :disabled="actionTaskId === liveTask.taskId || runtimeStatus === 3" @click="handlePauseTask(liveTask.taskId)">
                           <PauseCircle :size="16" />
                           <span>停止</span>
                         </button>
                         <button class="icon-btn" :disabled="actionTaskId === liveTask.taskId" @click="handleSyncTask(liveTask.taskId)">
                           <RefreshCw :size="16" />
                           <span>同步</span>
                         </button>
                       </div>
                     </div>

                     <p class="stage-detail">{{ stageDetail }}</p>

                     <div class="progress-card">
                       <div class="progress-head">
                         <span>采集进度</span>
                         <strong>{{ progressStats.finished }} / {{ progressStats.total || liveTask.targetCount || '--' }}</strong>
                       </div>
                       <div class="progress-bar">
                         <div class="progress-fill" :style="{ width: `${progressStats.percent}%` }"></div>
                       </div>
                       <div class="progress-meta">
                         <span>完成度 {{ progressStats.percent }}%</span>
                         <span>开始时间 {{ formatTime(liveTask.startTime || liveTask.createTime) }}</span>
                       </div>
                     </div>

                     <div class="progress-meta" style="margin-top: 12px;">
                       <span>新增 {{ liveTask.newCount || 0 }}</span>
                       <span>更新 {{ liveTask.updatedCount || 0 }}</span>
                       <span>去重 {{ liveTask.duplicateCount || 0 }}</span>
                     </div>
                     <div class="progress-meta" style="margin-top: 4px;">
                       <span>最近入库同步 {{ qualitySummary.latestSyncAt || '--' }}</span>
                     </div>

                     <div class="sync-result-card" :data-status="syncFeedback.status">
                       <div class="mini-head">
                         <h3>同步结果</h3>
                         <span>{{ syncFeedback.status === 'running' ? '同步中' : (syncFeedback.finishedAt || '--') }}</span>
                       </div>
                       <p class="sync-result-message">{{ syncFeedback.message || '尚未触发同步' }}</p>
                       <div class="progress-meta">
                         <span>业务表总量 {{ syncFeedback.afterBizRows || qualitySummary.bizRows || 0 }}</span>
                         <span>本次增量 {{ syncFeedback.deltaBizRows || 0 }}</span>
                       </div>
                       <div class="progress-meta">
                         <span>今日累计新增 {{ qualitySummary.latestNewCount }}</span>
                         <span>今日累计更新 {{ qualitySummary.latestUpdatedCount }}</span>
                         <span>今日去重 {{ qualitySummary.latestDuplicateCount }}</span>
                       </div>
                       <div class="recent-job-list">
                         <p class="recent-job-title">最近入库数据</p>
                         <div v-if="recentSyncedJobs.length" class="recent-job-items">
                           <div v-for="(job, idx) in recentSyncedJobs" :key="`${job.url || job.title || 'job'}-${idx}`" class="recent-job-item">
                             <strong>{{ job.title || '--' }}</strong>
                             <span>{{ job.companyName || '--' }} · {{ resolveCityLabel(job.city) }}</span>
                           </div>
                         </div>
                         <p v-else class="placeholder small">暂无可展示的数据</p>
                       </div>
                     </div>

                     <div class="watchdog-card" :data-restarting="watchdog.restarting">
                       <div class="watchdog-head">
                         <span>自动守护</span>
                         <strong>{{ watchdog.taskId ? formatRelative(watchdogCountdown) : '--' }}</strong>
                       </div>
                       <p>{{ watchdog.message || '当前没有需要守护的运行任务。' }}</p>
                       <p class="watchdog-meta">已重启 {{ watchdog.restartCount }} 次。</p>
                     </div>

                     <div class="shard-grid">
                       <article class="mini-panel">
                         <div class="mini-head">
                           <h3>活动分片</h3>
                           <span>{{ shardStats.running || activeShards.length }}</span>
                         </div>
                         <div v-if="activeShards.length" class="shard-list">
                           <div v-for="shard in activeShards" :key="shard.shardId || `${shard.page}-${shard.keyword}`" class="shard-item">
                             <strong>{{ shard.workerId }}</strong>
                             <span>第 {{ shard.page || '--' }} 页 · {{ shard.keyword || '未命名关键词' }}</span>
                           </div>
                         </div>
                         <div v-else class="placeholder small">暂无活动分片</div>
                       </article>

                       <article class="mini-panel">
                         <div class="mini-head">
                           <h3>已完成分片</h3>
                           <span>{{ shardStats.completed || completedShards.length }}</span>
                         </div>
                         <div v-if="completedShards.length" class="shard-list">
                           <div v-for="shard in completedShards" :key="shard.shardId || `${shard.page}-${shard.keyword}`" class="shard-item">
                             <strong>{{ shard.workerId }}</strong>
                             <span>第 {{ shard.page || '--' }} 页 · 新增 {{ shard.newCount || 0 }}</span>
                           </div>
                         </div>
                         <div v-else class="placeholder small">暂无完成分片</div>
                       </article>
                     </div>

                     <article class="log-card">
                       <div class="mini-head">
                         <h3>最近日志</h3>
                         <span>{{ logsLoading ? '刷新中' : `${logs.length} 条` }}</span>
                       </div>
                       <div v-if="liveLog" class="latest-log">
                         <p class="log-time">{{ formatTime(liveLog.createTime) }}</p>
                         <p class="log-message">{{ liveLog.message }}</p>
                       </div>
                       <div v-if="logs.length" class="log-list">
                         <div v-for="entry in logs.slice(0, 8)" :key="entry.logId || `${entry.createTime}-${entry.message}`" class="log-item">
                           <span>{{ formatTime(entry.createTime) }}</span>
                           <p>{{ entry.message }}</p>
                         </div>
                       </div>
                       <div v-else class="placeholder small">
                         <TerminalSquare :size="16" />
                         <span>暂无日志。</span>
                       </div>
                     </article>
                   </template>
                   <div v-else class="placeholder">暂无任务，请先创建采集任务。</div>
                 </div>
              </article>

              <article class="collector-panel result-panel">
                 <header class="collector-panel-head">
                   <div class="collector-panel-copy">
                     <h2 class="collector-panel-title">任务列表</h2>
                     <p class="collector-panel-sub">运行与停止操作会直接反馈。</p>
                   </div>
                 </header>
                 <div class="collector-panel-body">
                   <div v-if="bootstrapLoading" class="placeholder">正在加载任务列表…</div>
                   <div v-else-if="taskRows.length" class="task-list">
                     <button
                       v-for="task in taskRows"
                       :key="task.taskId"
                       class="task-item"
                       :class="{ active: selectedTaskId === task.taskId }"
                       @click="pinTask(task.taskId)"
                     >
                       <div class="task-item-head">
                         <strong>{{ task.taskName || task.taskId }}</strong>
                         <span class="status-pill small" :data-tone="getStatusMeta(deriveRuntimeStatus(task)).tone">
                           {{ getStatusMeta(deriveRuntimeStatus(task)).label }}
                         </span>
                       </div>
                       <p>{{ formatList(task.keywords) }} · {{ formatList(resolveCityLabel(task.city)) }}</p>
                       <p>进度：{{ getDisplayFinishedCount(task) || 0 }} / {{ (task.totalCount ?? task.targetCount) || '--' }}</p>
                       <p>{{ formatTime(task.updateTime || task.createTime) }}</p>
                     </button>
                     <div class="pagination-controls" style="display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: auto;" v-if="totalPages > 1">
                       <button class="ghost-btn" style="min-height: 32px; padding: 0 12px;" :disabled="pagination.page <= 1" @click="changePage(-1)">上一页</button>
                       <span class="page-info" style="font-size: 13px; color: var(--c-text-muted);">{{ pagination.page }} / {{ totalPages }}</span>
                       <button class="ghost-btn" style="min-height: 32px; padding: 0 12px;" :disabled="pagination.page >= totalPages" @click="changePage(1)">下一页</button>
                     </div>
                   </div>
                   <div v-else class="placeholder">当前没有采集任务。</div>
                 </div>
              </article>
            </div>
          </section>
        </Transition>
      </div>
    </div>
  </div>
</template>\n\n<style scoped>
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.collector-hero-title {
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

.collector-layout {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.collector-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.collector-sidebar {
  position: sticky;
  top: 24px;
  align-self: start;
  min-width: 0;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  scrollbar-width: none;
}
.collector-sidebar::-webkit-scrollbar { display: none; }

.collector-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.collector-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.collector-nav-group { display: flex; flex-direction: column; gap: 4px; }
.collector-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.collector-nav-list { display: flex; flex-direction: column; gap: 2px; margin: 0; padding: 0; list-style: none; }

.collector-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 10px 7px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 400;
  text-align: left;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}

.collector-nav-link:hover { background: var(--c-bg-surface-hover); color: var(--c-text-primary); }
.collector-nav-link.is-active { background: var(--c-accent-primary-glow); color: var(--c-accent-primary); font-weight: 600; }
.collector-nav-link.is-active::before { content: ''; position: absolute; left: 0; top: 6px; bottom: 6px; width: 2px; border-radius: 2px; background: var(--c-accent-primary); }

.collector-hero { display: flex; flex-direction: column; gap: 16px; }
.collector-hero-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; flex-wrap: wrap; }
.collector-hero-copy { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.collector-hero-side { display: flex; align-items: center; gap: 12px; }

.collector-banner {
  display: flex; align-items: center; gap: 10px; padding: 12px 16px; border: 1px solid var(--c-border-glass); border-radius: 12px;
  background: var(--c-surface-card-strong); color: var(--c-text-secondary); font-family: var(--font-sans); font-size: 13px;
  box-shadow: var(--shadow-card-soft);
}
.collector-banner.error { border-color: rgba(178, 59, 46, 0.22); background: rgba(254, 242, 240, 0.9); color: #b23b2e; }

.collector-main {
  display: grid;
  grid-template-columns: 1fr;
  align-items: start;
  gap: 24px;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 24px;
  align-items: stretch;
}

.collector-panel {
  display: flex; flex-direction: column; min-width: 0; background: var(--c-glass-panel-bg);
  border: 1px solid var(--c-border-glass); border-radius: 14px; box-shadow: var(--shadow-card-soft);
}

.collector-panel-head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}
.collector-panel-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.collector-panel-title { display: inline-flex; align-items: center; gap: 8px; margin: 0; font-family: var(--font-serif); font-size: 16px; font-weight: 700; color: var(--c-text-primary); }
.collector-panel-title :deep(svg) { flex: none; color: var(--c-accent-primary); }
.collector-panel-sub { margin: 0; font-family: var(--font-sans); font-size: 12.5px; line-height: 1.5; color: var(--c-text-muted); }
.collector-panel-badge {
  display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; background: var(--c-accent-primary-soft);
  color: var(--c-accent-primary); font-family: var(--font-sans); font-size: 11px; font-weight: 600;
}
.result-badge {
  display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; font-family: var(--font-sans); font-size: 11px; font-weight: 600;
}
.result-badge.is-live { background: var(--c-accent-primary-soft); color: var(--c-accent-primary); }
.result-badge.is-idle { background: rgba(15, 23, 42, 0.06); color: var(--c-text-secondary); }

.collector-panel-body { display: flex; flex-direction: column; flex: 1; gap: 16px; padding: 18px 22px 20px; }

.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px 16px; }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.field-full { grid-column: 1 / -1; }
.field-label { font-family: var(--font-sans); font-size: 11px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: var(--c-text-muted); }
.collector-input {
  width: 100%; padding: 10px 12px; border: 1px solid var(--c-border-glass); border-radius: 10px; background: var(--c-surface-card-strong);
  color: var(--c-text-primary); font-family: var(--font-sans); font-size: 13.5px;
}
.collector-input:focus { border-color: var(--c-accent-primary); box-shadow: 0 0 0 3px var(--c-accent-primary-soft); outline: none; }

.panel-actions, .action-row { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 8px; }
.primary-btn, .ghost-btn, .icon-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 40px; padding: 0 16px;
  border-radius: 999px; border: 1px solid transparent; cursor: pointer; transition: 0.18s ease; font-family: var(--font-sans); font-size: 13.5px; font-weight: 600;
}
.primary-btn { background: var(--c-accent-primary); color: #fff; }
.primary-btn:hover { background: var(--c-accent-primary-hover); }
.ghost-btn, .icon-btn { background: var(--c-bg-surface-hover); border-color: var(--c-border-glass); color: var(--c-text-primary); }
.ghost-btn:hover, .icon-btn:hover { background: var(--c-border-glass); }
.icon-btn.success { color: var(--c-accent-primary); }
.icon-btn.warn { color: #b45309; }

.primary-btn:disabled, .ghost-btn:disabled, .icon-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.summary-grid.compact { grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; }
.summary-tile { display: flex; flex-direction: column; gap: 6px; padding: 16px; border: 1px solid var(--c-border-glass); border-radius: 12px; background: var(--c-surface-card-strong); }
.summary-tile span { font-family: var(--font-sans); font-size: 12.5px; color: var(--c-text-secondary); }
.summary-tile strong { font-family: var(--font-sans); font-size: 20px; font-weight: 700; color: var(--c-text-primary); }

.task-summary { display: flex; justify-content: space-between; gap: 18px; margin-bottom: 12px; }
.task-name { margin: 0; font-size: 18px; font-weight: 700; color: var(--c-text-primary); }
.task-meta, .stage-detail { margin: 6px 0 0; line-height: 1.6; color: var(--c-text-muted); font-size: 13px; }

.progress-card, .watchdog-card, .log-card, .mini-panel {
  border: 1px solid var(--c-border-glass); border-radius: 12px; padding: 16px; background: var(--c-surface-card-strong); margin-top: 12px;
}
.progress-head, .watchdog-head, .mini-head, .task-item-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.progress-head span, .watchdog-head span, .mini-head h3 { margin: 0; font-size: 13.5px; font-weight: 600; color: var(--c-text-primary); }
.progress-head strong, .watchdog-head strong, .mini-head span { font-size: 13.5px; font-weight: 700; color: var(--c-accent-primary); }

.progress-bar { height: 8px; border-radius: 999px; overflow: hidden; background: var(--c-border-glass); margin: 12px 0 10px; }
.progress-fill { height: 100%; border-radius: inherit; background: var(--c-accent-primary); }
.progress-meta { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: var(--c-text-muted); }

.sync-result-card { margin-top: 12px; padding: 12px 16px; border-radius: 12px; border: 1px solid var(--c-border-glass); background: var(--c-bg-surface-hover); }
.sync-result-card[data-status="running"] { border-color: rgba(37, 99, 235, 0.35); background: rgba(239, 246, 255, 0.9); }
.sync-result-message { margin: 8px 0 10px; font-size: 13px; color: var(--c-text-secondary); }

.recent-job-list { margin-top: 10px; }
.recent-job-title { margin: 0 0 8px; font-size: 12px; color: var(--c-text-muted); }
.recent-job-items { display: flex; flex-direction: column; gap: 8px; }
.recent-job-item { padding: 10px 12px; border-radius: 10px; background: var(--c-surface-card-strong); border: 1px solid var(--c-border-glass); }
.recent-job-item strong { display: block; font-size: 13px; color: var(--c-text-primary); }
.recent-job-item span { display: block; margin-top: 4px; font-size: 12px; color: var(--c-text-muted); }

.watchdog-card { background: rgba(255, 247, 237, 0.88); }
.watchdog-card[data-restarting="true"] { background: rgba(254, 242, 242, 0.92); }
.watchdog-card p { margin: 10px 0 0; font-size: 13px; color: var(--c-text-secondary); }
.watchdog-meta { margin-top: 6px; font-size: 12px; color: var(--c-text-muted); }

.shard-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin-top: 16px; }
.shard-list, .log-list, .task-list { display: flex; flex-direction: column; flex: 1; gap: 10px; margin-top: 12px; }
.shard-item, .log-item { padding: 12px; border-radius: 10px; background: var(--c-bg-surface-hover); }
.shard-item strong, .log-item span, .latest-log .log-time { display: block; font-size: 12.5px; color: var(--c-text-muted); }
.shard-item span, .log-item p, .latest-log .log-message { margin: 6px 0 0; line-height: 1.5; font-size: 13px; color: var(--c-text-secondary); }
.latest-log { padding: 12px 14px; border-radius: 10px; margin-top: 12px; background: var(--c-accent-primary-soft); }

.task-item { text-align: left; border: 1px solid var(--c-border-glass); border-radius: 12px; padding: 14px; background: var(--c-surface-card-strong); cursor: pointer; transition: all 0.2s ease; }
.task-item:hover { border-color: var(--c-border-glass-hover); }
.task-item.active { border-color: var(--c-accent-primary); box-shadow: 0 0 0 1px var(--c-accent-primary); }
.task-item p { margin: 8px 0 0; line-height: 1.5; font-size: 12.5px; color: var(--c-text-muted); }

.status-pill { display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; background: var(--c-bg-surface-hover); color: var(--c-text-secondary); }
.status-pill[data-tone="running"] { background: rgba(14, 165, 233, 0.14); color: #0369a1; }
.status-pill[data-tone="success"] { background: rgba(34, 197, 94, 0.14); color: #15803d; }
.status-pill[data-tone="danger"] { background: rgba(239, 68, 68, 0.14); color: #b91c1c; }
.status-pill[data-tone="queued"] { background: rgba(245, 158, 11, 0.14); color: #b45309; }

.placeholder { display: flex; align-items: center; justify-content: center; min-height: 120px; border-radius: 12px; background: var(--c-bg-surface-hover); text-align: center; color: var(--c-text-muted); font-size: 13px; }
.placeholder.small { min-height: 90px; }

.toggle-switch { position: relative; display: inline-block; width: 44px; height: 24px; cursor: pointer; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; inset: 0; background: var(--c-border-glass-hover); border-radius: 24px; transition: 0.25s; }
.toggle-slider::before { content: ''; position: absolute; height: 18px; width: 18px; left: 3px; bottom: 3px; background: white; border-radius: 50%; transition: 0.25s; box-shadow: 0 1px 3px rgba(0,0,0,0.2); }
.toggle-switch input:checked + .toggle-slider { background: var(--c-accent-primary); }
.toggle-switch input:checked + .toggle-slider::before { transform: translateX(20px); }

.report-section-enter-active,
.report-section-leave-active {
  transition: opacity 220ms cubic-bezier(0.22, 1, 0.36, 1), transform 280ms cubic-bezier(0.22, 1, 0.36, 1), filter 280ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform, filter; transform-origin: top left;
}
.report-section-enter-from { opacity: 0; transform: translateY(18px) scale(0.985); filter: blur(10px); }
.report-section-leave-to { opacity: 0; transform: translateY(-10px) scale(0.992); filter: blur(8px); }
.report-section-enter-to, .report-section-leave-from { opacity: 1; transform: translateY(0) scale(1); filter: blur(0); }

@media (max-width: 1200px) {
  .result-grid { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .collector-layout { grid-template-columns: 1fr; }
  .collector-sidebar { position: static; max-height: none; }
  .collector-sidebar-inner { padding: 16px 4px 8px; }
  .collector-hero-row { flex-direction: column; }
}
</style>\n