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
const WATCHDOG_TIMEOUT_MS = 30000
const WATCHDOG_INITIAL_GRACE_MS = 60000
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
  { label: '\u5DE5\u4F5C\u65E5\u65E9\u4E0A9\u70B9', value: '0 0 9 * * MON-FRI', desc: '\u5468\u4E00\u5230\u5468\u4E94\u65E9\u4E0A9:00\u6267\u884C' },
  { label: '\u81EA\u5B9A\u4E49', value: 'custom', desc: '\u8F93\u5165\u81EA\u5B9A\u4E49 Cron \u8868\u8FBE\u5F0F' }
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

const pagination = ref({
  page: 1,
  pageSize: 10
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
    customCron: '',
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
    runningTasks: Array.isArray(liveOverview.value?.runningTasks) ? liveOverview.value.runningTasks.length : 0
  }
})

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

async function refreshDashboard(options = {}) {
  const { silent = false, keepSelection = true, includeLogs = true } = options
  if (!silent) loading.value = true
  softError.value = ''

  const token = authStore.token
  const page = pagination.value.page
  const pageSize = pagination.value.pageSize
  const previousSelection = keepSelection ? selectedTaskId.value : ''

  const [tasksRes, liveRes, qualityRes] = await Promise.allSettled([
    fetchCrawlTasks(token, { page, pageSize, timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS }),
    fetchLiveOverviewWithRetry(token),
    fetchCrawlQuality(token, { timeoutMs: DASHBOARD_REQUEST_TIMEOUT_MS })
  ])

  if (tasksRes.status === 'fulfilled') {
    tasks.value = tasksRes.value.data || []
    totalTasks.value = tasksRes.value.total || 0
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
  } else if (tasks.value.some((item) => item.taskId === previousSelection)) {
    selectedTaskId.value = previousSelection
  } else {
    selectedTaskId.value = liveOverview.value?.activeProgress?.taskId
      || liveOverview.value?.latestTask?.taskId
      || tasks.value[0]?.taskId
      || ''
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
    selectedTaskId.value = optimisticTask.taskId
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
  handleTaskAction(taskId, 1, '\u4EFB\u52A1\u5DF2\u5F00\u59CB', { status: 1 })
}

function handlePauseTask(taskId) {
  handleTaskAction(taskId, 3, '\u4EFB\u52A1\u5DF2\u505C\u6B62', { status: 3 })
}

function handleSyncTask(taskId) {
  handleTaskAction(taskId, 2, '\u7ED3\u679C\u540C\u6B65\u5DF2\u89E6\u53D1', {})
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
      scheduleForm.value.cronPreset = matchPreset ? settings.cron : 'custom'
      if (!matchPreset) scheduleForm.value.customCron = settings.cron
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
    const cron = scheduleForm.value.cronPreset === 'custom' ? scheduleForm.value.customCron : scheduleForm.value.cronPreset
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
  <section class="collector-page">
    <header class="hero">
      <div>
        <p class="eyebrow">分布式数据采集</p>
        <h1>任务创建、实时监控、自动重启统一在这个页面完成</h1>
        <p class="hero-text">
          创建任务后不会再自动阻塞启动链路。页面会持续刷新任务、实时日志、分片状态，并在首次 60 秒无信号或运行中
          30 秒无信号时自动重启。
        </p>
      </div>
      <button class="ghost-btn" :disabled="loading" @click="refreshDashboard({ includeLogs: true })">
        <RefreshCw :size="16" />
        <span>{{ loading ? '刷新中' : '立即刷新' }}</span>
      </button>
    </header>

    <div class="top-grid">
      <article class="metric-card">
        <div class="metric-icon accent-blue"><Database :size="18" /></div>
        <div>
          <p class="metric-label">累计职位</p>
          <strong>{{ qualitySummary.totalJobs || 0 }}</strong>
          <span>已入库职位总量</span>
        </div>
      </article>
      <article class="metric-card">
        <div class="metric-icon accent-green"><Activity :size="18" /></div>
        <div>
          <p class="metric-label">运行任务</p>
          <strong>{{ qualitySummary.runningTasks }}</strong>
          <span>当前正在执行</span>
        </div>
      </article>
      <article class="metric-card">
        <div class="metric-icon accent-orange"><SquareStack :size="18" /></div>
        <div>
          <p class="metric-label">今日增量</p>
          <strong>{{ qualitySummary.todayIncrement }}</strong>
          <span>同步到业务表</span>
        </div>
      </article>
      <article class="metric-card">
        <div class="metric-icon accent-red"><Clock3 :size="18" /></div>
        <div>
          <p class="metric-label">最近刷新</p>
          <strong>{{ lastRefreshAt ? formatTime(new Date(lastRefreshAt).toLocaleTimeString('zh-CN', { hour12: false })) : '--' }}</strong>
          <span>实时面板更新时间</span>
        </div>
      </article>
    </div>

    <p v-if="softError" class="soft-error">
      <AlertTriangle :size="16" />
      <span>{{ softError }}</span>
    </p>

    <div class="main-grid">
      <section class="panel form-panel">
        <div class="tab-bar">
          <button :class="{ active: activeTab === 'manual' }" @click="activeTab = 'manual'"><Zap :size="14" /><span>手动任务</span></button>
          <button :class="{ active: activeTab === 'scheduled' }" @click="activeTab = 'scheduled'; loadAutomationStatus()"><Calendar :size="14" /><span>定时任务</span></button>
        </div>
        <template v-if="activeTab === 'manual'">
          <div class="panel-head"><div><h2>新建采集任务</h2><p>选择城市和目标数量，创建后手动运行。</p></div></div>
          <div class="form-grid">
            <label><span>任务名称</span><input v-model.trim="form.taskName" type="text" placeholder="例如：成都 Java 职位采集" /></label>
            <label><span>关键词</span><input v-model.trim="form.keywords" type="text" placeholder="例如：Java、前端、数据分析" /></label>
            <label><span>城市</span><select v-model="form.city"><option v-for="city in CITY_OPTIONS" :key="city.code" :value="city.code">{{ city.label }}</option></select></label>
            <label><span>目标条数</span><select v-model="form.targetCount"><option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 条</option></select></label>
            <label><span>优先级</span><input v-model.number="form.priority" type="number" min="1" max="10" /></label>
            <label><span>渠道</span><input :value="'智联招聘'" type="text" disabled /></label>
          </div>
          <div class="form-actions">
            <button class="primary-btn" :disabled="submitting" @click="handleCreateTask"><span>{{ submitting ? '创建中…' : '创建任务' }}</span></button>
            <button class="ghost-btn" :disabled="submitting" @click="resetForm">重置</button>
          </div>
        </template>
        <template v-else>
          <div class="panel-head">
            <div><h2>定时采集</h2><p>配置 Cron 实现自动定时采集。</p></div>
            <span v-if="automationExecSummary.enabled" class="status-pill small" data-tone="running">已启用</span>
            <span v-else class="status-pill small" data-tone="queued">未启用</span>
          </div>
          <div v-if="automationLoading" class="placeholder small">加载中…</div>
          <template v-else>
            <div class="form-grid">
              <label class="switch-label"><span>启用定时采集</span><label class="toggle-switch"><input v-model="scheduleForm.enabled" type="checkbox" /><span class="toggle-slider"></span></label></label>
              <label><span>执行频率</span><select v-model="scheduleForm.cronPreset"><option v-for="p in CRON_PRESETS" :key="p.value" :value="p.value">{{ p.label }}</option></select></label>
              <label v-if="scheduleForm.cronPreset === 'custom'"><span>Cron 表达式</span><input v-model.trim="scheduleForm.customCron" type="text" placeholder="0 0 8 * * *" /></label>
              <p v-if="cronPresetDesc" class="form-hint">{{ cronPresetDesc }}</p>
              <label><span>关键词（逗号分隔）</span><input v-model.trim="scheduleForm.keywords" type="text" placeholder="Python, Java" /></label>
              <label><span>城市编码（逗号分隔）</span><input v-model.trim="scheduleForm.cities" type="text" placeholder="801, 763, 530" /></label>
              <label><span>目标条数</span><select v-model="scheduleForm.targetCount"><option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 条</option></select></label>
              <label class="switch-label"><span>增量采集</span><label class="toggle-switch"><input v-model="scheduleForm.incremental" type="checkbox" /><span class="toggle-slider"></span></label></label>
            </div>
            <div class="form-actions">
              <button class="primary-btn" :disabled="scheduleSubmitting" @click="handleSaveSchedule"><Settings :size="14" /><span>{{ scheduleSubmitting ? '保存中…' : '保存配置' }}</span></button>
              <button class="ghost-btn" :disabled="scheduleSubmitting" @click="handleTriggerNow"><Timer :size="14" /><span>立即触发</span></button>
            </div>
          </template>
        </template>
      </section>

      <section class="panel live-panel">
        <div class="panel-head">
          <div>
            <h2>实时任务看板</h2>
            <p>实时展示当前任务状态、日志、分片和自动重启监控。</p>
          </div>
          <span class="status-pill" :data-tone="statusMeta.tone">{{ stageText }}</span>
        </div>

        <div v-if="bootstrapLoading" class="placeholder">正在加载采集面板…</div>
        <template v-else-if="liveTask">
          <div class="task-summary">
            <div>
              <p class="task-name">{{ liveTask.taskName || liveTask.taskId }}</p>
              <p class="task-meta">
                任务 ID：{{ liveTask.taskId }} 路 城市：{{ formatList(resolveCityLabel(liveTask.city)) }} 路 关键词：{{ formatList(liveTask.keywords) }}
              </p>
            </div>
            <div class="action-row">
              <button class="icon-btn success" :disabled="actionTaskId === liveTask.taskId || runtimeStatus === 1" @click="handleRunTask(liveTask.taskId)">
                <PlayCircle :size="16" />
                <span>运行任务</span>
              </button>
              <button class="icon-btn warn" :disabled="actionTaskId === liveTask.taskId || runtimeStatus === 3" @click="handlePauseTask(liveTask.taskId)">
                <PauseCircle :size="16" />
                <span>停止任务</span>
              </button>
              <button class="icon-btn" :disabled="actionTaskId === liveTask.taskId" @click="handleSyncTask(liveTask.taskId)">
                <RefreshCw :size="16" />
                <span>同步结果</span>
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

          <div class="watchdog-card" :data-restarting="watchdog.restarting">
            <div class="watchdog-head">
              <span>自动守护</span>
              <strong>{{ watchdog.taskId ? formatRelative(watchdogCountdown) : '--' }}</strong>
            </div>
            <p>{{ watchdog.message || '当前没有需要守护的运行任务。' }}</p>
            <p class="watchdog-meta">首次无响应阈值 60 秒，运行中无响应阈值 30 秒，重启冷却 30 秒，已重启 {{ watchdog.restartCount }} 次。</p>
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
                  <span>第 {{ shard.page || '--' }} 页 路 {{ shard.keyword || '未命名关键词' }} 路 {{ formatList(shard.city) }}</span>
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
                  <span>第 {{ shard.page || '--' }} 页 路 新增 {{ shard.newCount || 0 }} 路 更新 {{ shard.updatedCount || 0 }} 路 {{ formatList(shard.city) }}</span>
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
              <span>当前任务还没有日志输出。</span>
            </div>
          </article>
        </template>
        <div v-else class="placeholder">暂无任务，请先创建采集任务。</div>
      </section>

      <section class="panel task-panel">
        <div class="panel-head">
          <div>
            <h2>任务列表</h2>
            <p>创建后立即展示，运行与停止操作会直接反馈到列表。</p>
          </div>
        </div>

        <div v-if="bootstrapLoading" class="placeholder">正在加载任务列表…</div>
        <div v-else-if="taskRows.length" class="task-list">
          <button
            v-for="task in taskRows"
            :key="task.taskId"
            class="task-item"
            :class="{ active: selectedTaskId === task.taskId }"
            @click="selectedTaskId = task.taskId"
          >
            <div class="task-item-head">
              <strong>{{ task.taskName || task.taskId }}</strong>
              <span class="status-pill small" :data-tone="getStatusMeta(deriveRuntimeStatus(task)).tone">
                {{ getStatusMeta(deriveRuntimeStatus(task)).label }}
              </span>
            </div>
            <p>{{ formatList(task.keywords) }} 路 {{ formatList(resolveCityLabel(task.city)) }}</p>
            <p>目标 {{ task.targetCount || '--' }} 条 路 已完成 {{ task.finishedCount || 0 }} 条</p>
            <p>{{ formatTime(task.updateTime || task.createTime) }}</p>
          </button>
        </div>
        <div v-else class="placeholder">当前没有采集任务。</div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.collector-page {
  min-height: 100%;
  padding: 28px;
  background:
    radial-gradient(circle at top left, rgba(24, 119, 242, 0.12), transparent 32%),
    radial-gradient(circle at top right, rgba(15, 118, 110, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(241, 245, 249, 0.92));
  color: #0f172a;
}

.hero,
.panel,
.metric-card,
.mini-panel,
.progress-card,
.watchdog-card,
.log-card,
.task-item {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 24px;
  margin-bottom: 20px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #2563eb;
}

.hero h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
}

.hero-text {
  max-width: 860px;
  margin: 14px 0 0;
  line-height: 1.7;
  color: #475569;
}

.top-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 18px;
}

.metric-card strong {
  display: block;
  font-size: 24px;
}

.metric-card span,
.metric-label {
  color: #64748b;
}

.metric-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
}

.accent-blue { background: rgba(37, 99, 235, 0.12); color: #2563eb; }
.accent-green { background: rgba(5, 150, 105, 0.12); color: #059669; }
.accent-orange { background: rgba(249, 115, 22, 0.12); color: #f97316; }
.accent-red { background: rgba(239, 68, 68, 0.12); color: #ef4444; }

.soft-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(254, 242, 242, 0.95);
  color: #b91c1c;
}

.main-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.panel {
  border-radius: 24px;
  padding: 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
  margin-bottom: 18px;
}

.panel-head h2,
.mini-head h3 {
  margin: 0;
}

.panel-head p,
.stage-detail,
.watchdog-meta,
.task-item p,
.task-meta,
.placeholder,
.mini-head span {
  color: #64748b;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}

.form-grid input,
.form-grid select {
  height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.94);
  color: #0f172a;
}

.form-actions,
.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.primary-btn,
.ghost-btn,
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: transform 0.18s ease, opacity 0.18s ease, background 0.18s ease;
}

.primary-btn {
  background: linear-gradient(135deg, #2563eb, #0f766e);
  color: #fff;
}

.ghost-btn,
.icon-btn {
  background: rgba(255, 255, 255, 0.8);
  border-color: rgba(148, 163, 184, 0.4);
  color: #0f172a;
}

.icon-btn.success { color: #047857; }
.icon-btn.warn { color: #b45309; }

.primary-btn:disabled,
.ghost-btn:disabled,
.icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.primary-btn:hover:not(:disabled),
.ghost-btn:hover:not(:disabled),
.icon-btn:hover:not(:disabled),
.task-item:hover {
  transform: translateY(-1px);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(148, 163, 184, 0.18);
  color: #334155;
}

.status-pill.small {
  padding: 4px 10px;
}

.status-pill[data-tone="running"] {
  background: rgba(14, 165, 233, 0.14);
  color: #0369a1;
}

.status-pill[data-tone="success"] {
  background: rgba(34, 197, 94, 0.14);
  color: #15803d;
}

.status-pill[data-tone="danger"] {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.status-pill[data-tone="queued"] {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.task-summary {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 12px;
}

.task-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.task-meta,
.stage-detail {
  margin: 6px 0 0;
  line-height: 1.6;
}

.progress-card,
.watchdog-card,
.log-card,
.mini-panel {
  border-radius: 18px;
  padding: 16px;
}

.progress-card {
  margin-top: 18px;
}

.progress-head,
.watchdog-head,
.mini-head,
.task-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.progress-bar {
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(148, 163, 184, 0.2);
  margin: 12px 0 10px;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #0f766e);
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: #64748b;
}

.watchdog-card {
  margin-top: 16px;
  background: rgba(255, 247, 237, 0.88);
}

.watchdog-card[data-restarting="true"] {
  background: rgba(254, 242, 242, 0.92);
}

.watchdog-card p {
  margin: 10px 0 0;
}

.shard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.shard-list,
.log-list,
.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.shard-item,
.log-item {
  padding: 12px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.9);
}

.shard-item strong,
.log-item span,
.latest-log .log-time {
  display: block;
  font-size: 13px;
  color: #475569;
}

.shard-item span,
.log-item p,
.latest-log .log-message {
  margin: 6px 0 0;
  line-height: 1.6;
}

.latest-log {
  padding: 12px 14px;
  border-radius: 14px;
  margin-bottom: 10px;
  background: rgba(239, 246, 255, 0.8);
}

.task-item {
  text-align: left;
  border-radius: 18px;
  padding: 14px;
  cursor: pointer;
}

.task-item.active {
  border-color: rgba(37, 99, 235, 0.28);
  box-shadow: 0 18px 40px rgba(37, 99, 235, 0.12);
}

.task-item p {
  margin: 8px 0 0;
  line-height: 1.5;
}

.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.9);
  text-align: center;
}

.placeholder.small {
  min-height: 90px;
  gap: 8px;
}

@media (max-width: 1400px) {
  .main-grid {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .task-panel {
    grid-column: 1 / -1;
  }

  .top-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .collector-page {
    padding: 16px;
  }

  .hero,
  .task-summary,
  .progress-meta {
    flex-direction: column;
  }

  .main-grid,
  .shard-grid,
  .top-grid {
    grid-template-columns: 1fr;
  }

  .panel {
    padding: 18px;
  }
}

.tab-bar {
  display: flex;
  gap: 0;
  margin: -24px -24px 16px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.tab-bar button {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab-bar button:first-child {
  border-radius: 20px 0 0 0;
}

.tab-bar button:last-child {
  border-radius: 0 20px 0 0;
}

.tab-bar button.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  background: rgba(37, 99, 235, 0.04);
}

.tab-bar button:hover:not(.active) {
  color: #334155;
  background: rgba(15, 23, 42, 0.03);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  cursor: pointer;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  inset: 0;
  background: #cbd5e1;
  border-radius: 24px;
  transition: 0.25s;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.25s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background: #2563eb;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(20px);
}

.switch-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.switch-label > span:first-child {
  font-weight: 500;
}

.form-hint {
  margin: -4px 0 8px;
  font-size: 12px;
  color: #64748b;
  padding-left: 2px;
}

.schedule-history {
  margin: 8px 0;
  padding: 8px 12px;
  background: rgba(37, 99, 235, 0.04);
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
}

.schedule-history p {
  margin: 0;
}
</style>
