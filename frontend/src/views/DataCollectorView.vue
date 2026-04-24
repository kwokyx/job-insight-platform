<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  FileText,
  Info,
  LoaderCircle,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  ShieldCheck,
  SquareX,
  TerminalSquare,
  X as CloseIcon
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createCrawlTask,
  fetchCrawlLiveOverview,
  fetchCrawlQuality,
  fetchCrawlTask,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  normalizeError,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const SOURCE_CHANNEL = 'zhaopin'
const SOURCE_CHANNEL_LABEL = '智联招聘'
const TARGET_COUNT_OPTIONS = [10, 20, 50, 100, 200, 300, 500, 800, 1000]
const WATCHDOG_TIMEOUT_MS = 10000
const WATCHDOG_RESTART_COOLDOWN_MS = 15000
const SCHEDULER_FALLBACK_SYNC_MS = 2000
const REGION_DISPLAY_MAP = {
  '530': '\u5317\u4eac',
  '531': '\u5929\u6d25',
  '538': '\u4e0a\u6d77',
  '551': '\u91cd\u5e86',
  '635': '\u5357\u4eac',
  '653': '\u676d\u5dde',
  '736': '\u6b66\u6c49',
  '763': '\u5e7f\u5dde',
  '765': '\u6df1\u5733',
  '801': '\u6210\u90fd'
}

const loading = ref(false)
const submitting = ref(false)
const logsLoading = ref(false)
const detailLoading = ref(false)
const statusUpdating = ref('')
const error = ref('')
const successMsg = ref('')

const tasks = ref([])
const totalTasks = ref(0)
const quality = ref({})
const liveOverview = ref({})
const logs = ref([])
const activeTaskId = ref('')
const trackedTask = ref(null)
const detailTask = ref(null)
const detailTaskId = ref('')

const pollTimer = ref(null)
const fastPollingUntil = ref(0)
const clockNow = ref(Date.now())
const clockTimer = ref(null)
const schedulerShardState = ref({
  taskId: '',
  loading: false,
  lastLoadedAt: 0,
  freshWorkerId: '',
  activeShards: [],
  completedShards: []
})
const watchdogState = ref({
  taskId: '',
  lastSignalAt: 0,
  lastSignalSourceAt: 0,
  lastSignature: '',
  restartCount: 0,
  lastRestartAt: 0,
  restarting: false,
  message: ''
})

const filters = ref({
  channel: SOURCE_CHANNEL,
  status: ''
})

const taskPage = ref(1)
const taskPageSize = ref(10)
const createFormOpen = ref(false)
const taskForm = ref(defaultTaskForm())

function defaultTaskForm() {
  return {
    taskName: '',
    channel: SOURCE_CHANNEL,
    keywords: '',
    city: '801',
    targetCount: 20,
    priority: 5
  }
}

const taskTotalPages = computed(() => Math.max(1, Math.ceil((totalTasks.value || 0) / taskPageSize.value)))
const showInitialTaskLoading = computed(() => loading.value && tasks.value.length === 0)
const showTaskRefreshing = computed(() => loading.value && tasks.value.length > 0)
const freshnessRows = computed(() => quality.value?.freshness || [])
const selectedTask = computed(() =>
  tasks.value.find((item) => item.taskId === activeTaskId.value) || trackedTask.value || detailTask.value || null
)
const filteredRunningTasks = computed(() => (liveOverview.value?.runningTasks || []).filter((item) => !isRealtimeNoiseTask(item)))
const realtimeTask = computed(() => {
  if (trackedTask.value?.taskId === activeTaskId.value) {
    return trackedTask.value
  }

  const progress = liveOverview.value?.activeProgress
  if (progress && !isRealtimeNoiseTask(progress)) {
    return progress
  }

  const latest = liveOverview.value?.latestTask
  if (latest && !isRealtimeNoiseTask(latest)) {
    return latest
  }

  if (filteredRunningTasks.value.length > 0) {
    return filteredRunningTasks.value[0]
  }

  return selectedTask.value
})
const realtimeLatestLog = computed(() => {
  const taskId = realtimeTask.value?.taskId
  if (!taskId) return null

  if (taskId === activeTaskId.value && logs.value.length > 0) {
    return logs.value[0]
  }

  if (liveOverview.value?.activeProgress?.taskId === taskId && liveOverview.value?.activeProgress?.latestLog) {
    return liveOverview.value.activeProgress.latestLog
  }

  return (liveOverview.value?.latestLogs || []).find((item) => item.taskId === taskId) || null
})
const realtimeStageLabel = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus === 1) return '采集中'
  if (runtimeStatus === 0) return '排队中'
  if (liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && liveOverview.value?.activeProgress?.stageLabel) {
    return liveOverview.value.activeProgress.stageLabel
  }
  return getStatusMeta(runtimeStatus).label
})
const realtimeStageDetail = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus === 1) return '当前任务正在执行，页面会持续刷新采集进度和实时日志。'
  if (runtimeStatus === 0) return '任务已创建，正在等待调度中心分发或浏览器完成鉴权初始化。'
  if (liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && liveOverview.value?.activeProgress?.stageDetail) {
    return liveOverview.value.activeProgress.stageDetail
  }

  if (runtimeStatus === 1) return '当前任务正在执行，页面会持续刷新采集进度和实时日志。'
  if (runtimeStatus === 2) return '任务已完成，当前展示的是最近一次采集结果。'
  if (runtimeStatus === 3) return '任务已结束或失败，请查看下方日志确认原因。'
  return '任务已创建，等待调度中心开始执行。'
})
const realtimeShardStats = computed(() => {
  const stats = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId
    ? liveOverview.value?.activeProgress?.shardStats
    : null
  return {
    total: Number(stats?.total || 0),
    pending: Number(stats?.pending || 0),
    running: Number(stats?.running || 0),
    completed: Number(stats?.completed || 0),
    failed: Number(stats?.failed || 0)
  }
})
const realtimeShardLogFallback = computed(() => {
  const taskId = realtimeTask.value?.taskId
  if (!taskId) {
    return { activeShards: [], completedShards: [] }
  }

  const candidates = []
  if (taskId === activeTaskId.value && Array.isArray(logs.value)) {
    candidates.push(...logs.value)
  }
  if (realtimeLatestLog.value) {
    candidates.push(realtimeLatestLog.value)
  }

  return parseRealtimeShardsFromLogs(candidates, taskId)
})
const realtimeSchedulerShardFallback = computed(() => {
  return schedulerShardState.value?.taskId === realtimeTask.value?.taskId
    ? schedulerShardState.value
    : { activeShards: [], completedShards: [] }
})
function shardIdentity(item) {
  if (!item) return ''
  if (item.shardId) return `id:${item.shardId}`
  return [
    item.page ?? '',
    item.keyword ?? '',
    resolveCityDisplayName(item.city ?? '')
  ].join('|')
}

function enrichRealtimeShards(primary, fallback, fallbackWorkerId = '') {
  if (!Array.isArray(primary) || primary.length === 0) {
    return Array.isArray(fallback) ? fallback : []
  }
  const fallbackMap = new Map(
    (Array.isArray(fallback) ? fallback : [])
      .map((item) => [shardIdentity(item), item])
      .filter(([key]) => key)
  )
  return primary.map((item) => {
    const matched = fallbackMap.get(shardIdentity(item))
    return {
      ...(matched || {}),
      ...item,
      city: resolveCityDisplayName(item?.city || matched?.city || ''),
      workerId: item?.workerId || item?.worker_id || matched?.workerId || matched?.worker_id || fallbackWorkerId
    }
  })
}
const realtimeActiveShards = computed(() => {
  const raw = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && Array.isArray(liveOverview.value?.activeProgress?.activeShards)
    ? liveOverview.value.activeProgress.activeShards
    : []
  if (raw.length) return enrichRealtimeShards(raw, realtimeSchedulerShardFallback.value.activeShards, realtimeSchedulerShardFallback.value.freshWorkerId)
  if (realtimeSchedulerShardFallback.value.activeShards?.length) return realtimeSchedulerShardFallback.value.activeShards
  return realtimeShardLogFallback.value.activeShards
})
const realtimeCompletedShards = computed(() => {
  const raw = liveOverview.value?.activeProgress?.taskId === realtimeTask.value?.taskId && Array.isArray(liveOverview.value?.activeProgress?.completedShards)
    ? liveOverview.value.activeProgress.completedShards
    : []
  if (raw.length) return enrichRealtimeShards(raw, realtimeSchedulerShardFallback.value.completedShards, realtimeSchedulerShardFallback.value.freshWorkerId)
  if (realtimeSchedulerShardFallback.value.completedShards?.length) return realtimeSchedulerShardFallback.value.completedShards
  return realtimeShardLogFallback.value.completedShards
})
const realtimeDataCards = computed(() => {
  const task = realtimeTask.value || {}
  return [
    { label: '已抓取', value: crawledCount(task), note: `目标 ${targetCount(task) || '--'} 条` },
    { label: '新增', value: safeNumber(task?.newCount), note: '本轮写入的新职位数' },
    { label: '更新', value: safeNumber(task?.updatedCount), note: '已有职位的更新条数' },
    { label: '去重', value: safeNumber(task?.duplicateCount), note: '去重过滤的重复记录' }
  ]
})
const realtimeWatchdogVisible = computed(() => {
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  return runtimeStatus === 0 || runtimeStatus === 1 || watchdogState.value.restarting || watchdogState.value.restartCount > 0
})
const realtimeWatchdogCountdown = computed(() => {
  if (watchdogState.value.restarting) return 0
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus !== 0 && runtimeStatus !== 1) return null
  if (!watchdogState.value.lastSignalAt) return Math.ceil(WATCHDOG_TIMEOUT_MS / 1000)
  const remainMs = WATCHDOG_TIMEOUT_MS - (clockNow.value - watchdogState.value.lastSignalAt)
  return Math.min(Math.ceil(WATCHDOG_TIMEOUT_MS / 1000), Math.max(0, Math.ceil(remainMs / 1000)))
})
const realtimeWatchdogText = computed(() => {
  if (watchdogState.value.restarting) {
    return watchdogState.value.message || '当前任务超过 10 秒没有新进度，正在自动结束并重启。'
  }
  if (watchdogState.value.message) {
    return watchdogState.value.message
  }
  const runtimeStatus = deriveTaskRuntimeStatus(realtimeTask.value)
  if (runtimeStatus !== 0 && runtimeStatus !== 1) {
    return watchdogState.value.restartCount > 0
      ? `本任务最近已自动重启 ${watchdogState.value.restartCount} 次。`
      : '当前没有需要自动重启的活动任务。'
  }
  const remain = realtimeWatchdogCountdown.value
  return `如果连续 ${remain ?? 10} 秒没有新的进度、日志或分片变化，系统会自动结束并重启当前任务。`
})
const qualityCards = computed(() => {
  const q = quality.value || {}
  const completeness = q.completeness || {}
  return [
    {
      label: '岗位总量',
      value: q.totalJobs ?? '--',
      note: '当前职位库规模'
    },
    {
      label: '标题完整率',
      value: completeness.titleRate || '--',
      note: '职位标题字段有效占比'
    },
    {
      label: '薪资完整率',
      value: completeness.salaryRate || '--',
      note: '薪资字段有效占比'
    },
    {
      label: '疑似僵尸岗',
      value: q.suspectedZombieJobRate || '--',
      note: `疑似过期 ${q.suspectedZombieJobs ?? 0} 条`
    }
  ]
})
const detailMetaRows = computed(() => {
  const task = detailTask.value
  if (!task) return []
  return [
    { label: '任务 ID', value: task.taskId || '--' },
    { label: '父任务', value: task.parentTaskId || '--' },
    { label: '渠道', value: formatChannel(task.channel) },
    { label: '城市', value: formatCityList(task.city, '全域') },
    { label: '关键词', value: formatList(task.keywords, '--') },
    { label: '优先级', value: `P${task.priority ?? 5}` },
    { label: '状态', value: getStatusMeta(task.status).label },
    { label: '目标条数', value: targetCount(task) || '未设置' },
    { label: '已采集', value: crawledCount(task) },
    { label: '去重', value: task.duplicateCount ?? 0 },
    { label: '创建人', value: task.createUser || '--' },
    { label: '创建时间', value: formatTime(task.createTime) },
    { label: '开始时间', value: formatTime(task.startTime) },
    { label: '结束时间', value: formatTime(task.endTime) },
    { label: '更新时间', value: formatTime(task.updateTime) }
  ]
})

function setFeedback(type, message) {
  if (type === 'error') {
    error.value = message
    successMsg.value = ''
    return
  }
  successMsg.value = message
  error.value = ''
}

function isRealtimeNoiseTask(task) {
  if (!task) return false
  const taskId = String(task.taskId || '')
  const taskName = String(task.taskName || '').toLowerCase()
  const scheduleType = String(task.scheduleType || '').toUpperCase()
  const createUser = String(task.createUser || '').toLowerCase()

  return taskId.startsWith('probe_')
    || taskName.includes('probe')
    || scheduleType === 'SCHEDULED_TEMPLATE'
    || createUser === 'probe'
    || createUser === 'system-probe'
}

function getStatusMeta(status) {
  const map = {
    0: { label: '排队中', tone: 'idle', icon: Clock3 },
    1: { label: '运行中', tone: 'running', icon: LoaderCircle },
    2: { label: '已完成', tone: 'done', icon: CheckCircle2 },
    3: { label: '已结束', tone: 'paused', icon: PauseCircle }
  }
  return map[status] || { label: '未知', tone: 'idle', icon: Activity }
}

function deriveTaskRuntimeStatus(task) {
  if (!task) return null
  const rawStatus = Number(task.status)
  const total = targetCount(task)
  const crawled = crawledCount(task)

  if (total > 0 && crawled > 0 && crawled < total) {
    return 1
  }

  if (rawStatus === 3 && total > 0 && !task?.endTime && crawled < total) {
    return crawled > 0 ? 1 : 0
  }

  if ((rawStatus === 0 || rawStatus === 3) && isTaskRecentlyQueued(task, total, crawled)) {
    return 0
  }

  return rawStatus
}

function isTaskRecentlyQueued(task, total, crawled) {
  if (total <= 0) return false
  if (crawled > 0) return false
  if (task?.endTime) return false

  const startTs = parseTaskTimestamp(task?.startTime)
  const createTs = parseTaskTimestamp(task?.createTime)
  const updateTs = parseTaskTimestamp(task?.updateTime)
  const latestTs = Math.max(startTs, createTs, updateTs)
  if (!latestTs) return false

  return Date.now() - latestTs <= 2 * 60 * 1000
}

function parseTaskTimestamp(value) {
  if (!value) return 0
  const normalized = String(value).replace(' ', 'T')
  const parsed = Date.parse(normalized)
  return Number.isFinite(parsed) ? parsed : 0
}

function formatTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatChannel(channel) {
  return channel === SOURCE_CHANNEL || !channel ? SOURCE_CHANNEL_LABEL : channel
}

function formatList(value, fallback = '--') {
  if (Array.isArray(value)) {
    return value.length ? value.join(' / ') : fallback
  }
  return value || fallback
}

function resolveCityDisplayName(value) {
  if (value == null) return ''
  const text = String(value).trim()
  if (!text) return ''
  return REGION_DISPLAY_MAP[text] || text
}

function formatCityList(value, fallback = '--') {
  if (Array.isArray(value)) {
    const items = value.map(resolveCityDisplayName).filter(Boolean)
    return items.length ? items.join(' / ') : fallback
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return fallback
    if ((text.startsWith('[') && text.endsWith(']')) || (text.startsWith('{') && text.endsWith('}'))) {
      try {
        return formatCityList(JSON.parse(text), fallback)
      } catch (e) {
        return resolveCityDisplayName(text)
      }
    }
    if (text.includes(',')) {
      const items = text.split(',').map(resolveCityDisplayName).filter(Boolean)
      return items.length ? items.join(' / ') : fallback
    }
    return resolveCityDisplayName(text)
  }
  return resolveCityDisplayName(value) || fallback
}

function safeNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function crawledCount(task) {
  return Math.max(safeNumber(task?.crawledCount), safeNumber(task?.finishedCount))
}

function targetCount(task) {
  return Math.max(safeNumber(task?.targetCount), safeNumber(task?.totalCount))
}

function progressPercent(task) {
  const total = targetCount(task)
  const crawled = crawledCount(task)
  if (!total) return crawled > 0 ? 100 : 0
  return Math.max(0, Math.min(100, Math.round((crawled / total) * 100)))
}

function progressText(task) {
  const total = targetCount(task)
  const crawled = crawledCount(task)
  if (total > 0) {
    return `${crawled} / ${total}`
  }
  return `${crawled} 条`
}

function formatShardSummary(shard) {
  if (!shard) return '--'
  const parts = []
  if (shard.page != null) parts.push(`页码 ${shard.page}`)
  if (shard.keyword) parts.push(`关键词 ${shard.keyword}`)
  if (shard.city) parts.push(`城市 ${formatList(shard.city, '--')}`)
  return parts.join(' / ') || '--'
}

function formatShardResult(shard) {
  if (!shard) return '--'
  if (safeNumber(shard.collectedCount) > 0) {
    return `采集 ${safeNumber(shard.collectedCount)} 条`
  }
  return `新增 ${safeNumber(shard.newCount)} / 更新 ${safeNumber(shard.updatedCount)} / 去重 ${safeNumber(shard.duplicateCount)}`
}

function parseRealtimeShardsFromLogs(items, taskId) {
  if (!Array.isArray(items) || !items.length || !taskId) {
    return { activeShards: [], completedShards: [] }
  }

  const startPattern = /开始处理任务分片:\s*shard_id=([^,\s]+),\s*keyword=([^,]+),\s*city=([^,]+),\s*page=(\d+)/i
  const donePattern = /任务分片完成:\s*shard_id=([^,\s]+),\s*采集数据(\d+)条/i
  const shardMap = new Map()

  const sortedItems = [...items]
    .filter((item) => !item?.taskId || item.taskId === taskId)
    .sort((a, b) => parseTaskTimestamp(a?.createTime) - parseTaskTimestamp(b?.createTime))

  sortedItems.forEach((item) => {
    const message = String(item?.message || item?.logMessage || item?.content || '').trim()
    if (!message) return

    const startMatch = message.match(startPattern)
    if (startMatch) {
      const [, shardId, keyword, city, page] = startMatch
      const current = shardMap.get(shardId) || { shardId }
      shardMap.set(shardId, {
        ...current,
        shardId,
        keyword: keyword?.trim(),
        city: resolveCityDisplayName(city?.trim()),
        page: safeNumber(page, null),
        workerId: current.workerId || item?.workerId || item?.worker || '',
        status: 1,
        startTime: current.startTime || item?.createTime || current.startTime,
        updateTime: item?.createTime || current.updateTime
      })
      return
    }

    const doneMatch = message.match(donePattern)
    if (doneMatch) {
      const [, shardId, collectedCount] = doneMatch
      const current = shardMap.get(shardId) || { shardId }
      shardMap.set(shardId, {
        ...current,
        shardId,
        status: 2,
        collectedCount: safeNumber(collectedCount),
        endTime: item?.createTime || current.endTime,
        updateTime: item?.createTime || current.updateTime
      })
    }
  })

  const allShards = [...shardMap.values()]
  const activeShards = allShards
    .filter((item) => item.startTime && !item.endTime)
    .sort((a, b) => parseTaskTimestamp(b?.startTime || b?.updateTime) - parseTaskTimestamp(a?.startTime || a?.updateTime))
    .slice(0, 6)
  const completedShards = allShards
    .filter((item) => item.endTime)
    .sort((a, b) => parseTaskTimestamp(b?.endTime || b?.updateTime) - parseTaskTimestamp(a?.endTime || a?.updateTime))
    .slice(0, 6)

  return { activeShards, completedShards }
}

function schedulerApiBase() {
  if (typeof window === 'undefined') return ''
  return `${window.location.protocol}//${window.location.hostname}:8001/api`
}

function normalizeSchedulerShard(item) {
  return {
    shardId: item?.shard_id || item?.shardId || '',
    taskId: item?.task_id || item?.taskId || '',
    page: item?.page,
    keyword: item?.keyword || '',
    city: resolveCityDisplayName(item?.city || ''),
    categoryCode: item?.category_code || item?.categoryCode || '',
    status: item?.status,
    retryCount: item?.retry_count || item?.retryCount || 0,
    stopReason: item?.stop_reason || item?.stopReason || '',
    newCount: safeNumber(item?.new_count ?? item?.newCount),
    updatedCount: safeNumber(item?.updated_count ?? item?.updatedCount),
    duplicateCount: safeNumber(item?.duplicate_count ?? item?.duplicateCount),
    workerId: item?.worker_id || item?.workerId || '',
    startTime: item?.start_time || item?.startTime || '',
    endTime: item?.end_time || item?.endTime || '',
    collectedCount: safeNumber(item?.collected_count ?? item?.collectedCount ?? item?.new_count ?? item?.updatedCount ?? item?.updated_count)
  }
}

function resolveFreshWorkerId(items) {
  const now = Date.now()
  const freshWorkers = (Array.isArray(items) ? items : []).filter((item) => {
    if (!item?.worker_id) return false
    if (Number(item?.status) !== 1) return false
    const rawHeartbeat = item?.last_heartbeat || item?.lastHeartbeat
    const heartbeatAt = String(rawHeartbeat || '').match(/Z|[+-]\d{2}:\d{2}$/)
      ? parseTaskTimestamp(rawHeartbeat)
      : Date.parse(String(rawHeartbeat || '').replace(' ', 'T') + 'Z')
    return heartbeatAt > 0 && now - heartbeatAt <= 2 * 60 * 1000
  })
  if (freshWorkers.length !== 1) return ''
  return freshWorkers[0].worker_id || ''
}

async function syncSchedulerShardFallback(force = false) {
  const taskId = realtimeTask.value?.taskId
  if (!taskId) {
    schedulerShardState.value = {
      taskId: '',
      loading: false,
      lastLoadedAt: 0,
      freshWorkerId: '',
      activeShards: [],
      completedShards: []
    }
    return
  }

  const now = Date.now()
  if (!force && schedulerShardState.value.taskId === taskId && now - schedulerShardState.value.lastLoadedAt < SCHEDULER_FALLBACK_SYNC_MS) {
    return
  }
  if (schedulerShardState.value.loading) {
    return
  }

  schedulerShardState.value = {
    ...schedulerShardState.value,
    taskId,
    loading: true
  }

  try {
    const base = schedulerApiBase()
    const [activeRes, completedRes, workersRes] = await Promise.all([
      fetch(`${base}/tasks/${taskId}/shards?page=1&size=6&status=1`),
      fetch(`${base}/tasks/${taskId}/shards?page=1&size=6&status=2`),
      fetch(`${base}/workers?page=1&size=50`)
    ])
    const [activeJson, completedJson, workersJson] = await Promise.all([activeRes.json(), completedRes.json(), workersRes.json()])
    const freshWorkerId = resolveFreshWorkerId(workersJson?.data?.items)
    const activeShards = Array.isArray(activeJson?.data?.items) ? activeJson.data.items.map(normalizeSchedulerShard) : []
    const completedShards = Array.isArray(completedJson?.data?.items) ? completedJson.data.items.map(normalizeSchedulerShard) : []
    schedulerShardState.value = {
      taskId,
      loading: false,
      lastLoadedAt: now,
      freshWorkerId,
      activeShards: activeShards.map((item) => ({ ...item, workerId: item.workerId || freshWorkerId })),
      completedShards: completedShards.map((item) => ({ ...item, workerId: item.workerId || freshWorkerId }))
    }
  } catch (e) {
    schedulerShardState.value = {
      ...schedulerShardState.value,
      taskId,
      loading: false,
      lastLoadedAt: now
    }
  }
}

function latestSignalTimestamp(task, latestLog, activeShards, completedShards) {
  let latest = 0
  const candidates = [
    task?.startTime,
    task?.endTime,
    latestLog?.createTime
  ]
  activeShards.forEach((item) => {
    candidates.push(item?.startTime, item?.endTime)
  })
  completedShards.forEach((item) => {
    candidates.push(item?.startTime, item?.endTime)
  })
  candidates.forEach((value) => {
    latest = Math.max(latest, parseTaskTimestamp(value))
  })
  return latest
}

function buildWatchdogSignature(task, latestLog, activeShards, completedShards) {
  return JSON.stringify({
    taskId: task?.taskId || '',
    status: deriveTaskRuntimeStatus(task),
    crawled: crawledCount(task),
    total: targetCount(task),
    latestLogTime: latestLog?.createTime || '',
    latestLogMessage: latestLog?.message || '',
    active: activeShards.map((item) => [item.shardId, item.page, item.status, item.workerId, item.startTime, item.endTime]),
    completed: completedShards.map((item) => [item.shardId, item.page, item.status, item.newCount, item.updatedCount, item.duplicateCount, item.endTime])
  })
}

function canStartTask(task) {
  const status = deriveTaskRuntimeStatus(task)
  return status !== 1 && status !== 2
}

function canPauseTask(task) {
  return deriveTaskRuntimeStatus(task) === 1
}

function canFinishTask(task) {
  const status = deriveTaskRuntimeStatus(task)
  return status !== 2 && status !== 3
}

function startActionLabel(task) {
  return Number(task?.status) === 3 ? '重跑' : '启动'
}

function optimisticStatusPatch(status, task = {}) {
  const now = new Date().toISOString()
  if (status === 1) {
    return { status, startTime: task.startTime || now, endTime: null, updateTime: now }
  }
  if (status === 0) {
    return { status, updateTime: now }
  }
  if (status === 3) {
    return { status, endTime: now, updateTime: now }
  }
  return { status, updateTime: now }
}

function patchTaskState(taskId, patch) {
  tasks.value = tasks.value.map((item) => (item.taskId === taskId ? { ...item, ...patch } : item))
  if (trackedTask.value?.taskId === taskId) {
    trackedTask.value = { ...trackedTask.value, ...patch }
  }
  if (detailTask.value?.taskId === taskId) {
    detailTask.value = { ...detailTask.value, ...patch }
  }
}

function pinTaskToTop(task) {
  if (!task?.taskId) return
  const next = [task, ...tasks.value.filter((item) => item.taskId !== task.taskId)]
  tasks.value = next
  totalTasks.value = Math.max(totalTasks.value || 0, next.length)
}

async function loadDashboard(options = {}) {
  if (!authStore.token) return

  loading.value = true
  if (!options.silent) {
    error.value = ''
  }

  try {
    const [taskResult, liveResult, qualityResult] = await Promise.all([
      fetchCrawlTasks(authStore.token, {
        channel: filters.value.channel,
        status: filters.value.status,
        page: taskPage.value,
        pageSize: taskPageSize.value
      }),
      fetchCrawlLiveOverview(authStore.token),
      fetchCrawlQuality(authStore.token)
    ])

    tasks.value = taskResult.data || []
    totalTasks.value = taskResult.total || 0
    liveOverview.value = liveResult || {}
    quality.value = qualityResult || {}

    if (!tasks.value.length && taskPage.value > 1) {
      taskPage.value = Math.max(1, taskPage.value - 1)
      await loadDashboard(options)
      return
    }

    if (activeTaskId.value) {
      await loadTrackedTask(activeTaskId.value, true)
      if (trackedTask.value?.taskId === activeTaskId.value) {
        pinTaskToTop(trackedTask.value)
      }
      await loadLogs(activeTaskId.value, true)
    } else if (tasks.value.length > 0) {
      await loadTrackedTask(tasks.value[0].taskId, true)
      await loadLogs(tasks.value[0].taskId, true)
    }
    syncRealtimeWatchdog()
    await syncSchedulerShardFallback(true)
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    loading.value = false
  }
}

function stopPolling() {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function startClock() {
  if (clockTimer.value) return
  clockTimer.value = window.setInterval(() => {
    clockNow.value = Date.now()
    syncRealtimeWatchdog()
    void syncSchedulerShardFallback()
  }, 1000)
}

function stopClock() {
  if (clockTimer.value) {
    window.clearInterval(clockTimer.value)
    clockTimer.value = null
  }
}

function startPolling() {
  stopPolling()
  const interval = Date.now() < fastPollingUntil.value ? 1500 : 4000
  pollTimer.value = window.setInterval(async () => {
    if (!loading.value && !submitting.value && !detailLoading.value) {
      await loadDashboard({ silent: true })
    }
    if (Date.now() >= fastPollingUntil.value && interval !== 4000) {
      startPolling()
    }
  }, interval)
}

function boostPolling(durationMs = 45000) {
  fastPollingUntil.value = Date.now() + durationMs
  startPolling()
}

function applyFilters() {
  taskPage.value = 1
  void loadDashboard()
}

function goToPage(n) {
  const next = Math.max(1, Math.min(taskTotalPages.value, n))
  if (next === taskPage.value) return
  taskPage.value = next
  void loadDashboard()
}

async function loadLogs(taskId, silent = false) {
  if (!taskId || !authStore.token) return

  activeTaskId.value = taskId
  logsLoading.value = true
  try {
    const result = await fetchCrawlTaskLogs(authStore.token, taskId, {
      page: 1,
      pageSize: 20
    })
    logs.value = result.data || []
    syncRealtimeWatchdog()
    await syncSchedulerShardFallback(true)
  } catch (e) {
    if (!silent) {
      setFeedback('error', normalizeError(e))
    }
  } finally {
    logsLoading.value = false
  }
}

async function loadTrackedTask(taskId, silent = false) {
  if (!taskId || !authStore.token) return

  try {
    trackedTask.value = await fetchCrawlTask(authStore.token, taskId)
    if (trackedTask.value?.taskId === taskId) {
      pinTaskToTop(trackedTask.value)
    }
    syncRealtimeWatchdog()
    await syncSchedulerShardFallback(true)
  } catch (e) {
    if (!silent) {
      setFeedback('error', normalizeError(e))
    }
  }
}

async function handleCreateTask() {
  if (!authStore.token || submitting.value) return

  submitting.value = true
  error.value = ''
  try {
    const result = await createCrawlTask(authStore.token, {
      taskName: taskForm.value.taskName,
      channel: SOURCE_CHANNEL,
      keywords: taskForm.value.keywords,
      city: taskForm.value.city,
      targetCount: Number(taskForm.value.targetCount) || 20,
      priority: Number(taskForm.value.priority) || 5
    })

    const createdTaskId = result?.taskId || ''
    taskForm.value = defaultTaskForm()
    createFormOpen.value = false
    taskPage.value = 1
    if (createdTaskId) {
      activeTaskId.value = createdTaskId
      await loadTrackedTask(createdTaskId, true)
    }
    setFeedback('success', createdTaskId ? `任务已创建并开始跟踪：${createdTaskId}` : '任务已创建')
    boostPolling()
    await loadDashboard()
    if (createdTaskId) {
      await loadLogs(createdTaskId, true)
    }
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    submitting.value = false
  }
}

async function handleTaskStatus(task, status) {
  if (!authStore.token) return

  const taskId = task.taskId
  const previousTask = tasks.value.find((item) => item.taskId === taskId)
  const previousDetailTask = detailTask.value?.taskId === taskId ? detailTask.value : null
  statusUpdating.value = `${task.taskId}:${status}`
  error.value = ''
  patchTaskState(taskId, optimisticStatusPatch(status, task))
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status })
    setFeedback('success', `${task.taskName || taskId} 状态已更新`)
    boostPolling()
    await loadDashboard()
  } catch (e) {
    if (previousTask) {
      patchTaskState(taskId, previousTask)
    }
    if (previousDetailTask) {
      detailTask.value = previousDetailTask
    }
    setFeedback('error', normalizeError(e))
  } finally {
    statusUpdating.value = ''
  }
}

async function restartTaskByWatchdog(task) {
  const taskId = task?.taskId
  if (!taskId || !authStore.token || watchdogState.value.restarting) return

  watchdogState.value = {
    ...watchdogState.value,
    taskId,
    restarting: true,
    restartCount: watchdogState.value.restartCount + 1,
    lastRestartAt: Date.now(),
    message: '当前任务超过 10 秒没有新进度，正在自动结束并重启。'
  }
  statusUpdating.value = `${taskId}:watchdog`

  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 0 })
    await new Promise((resolve) => window.setTimeout(resolve, 600))
    await updateCrawlTaskStatus(authStore.token, taskId, { status: 1 })
    setFeedback('success', `${task.taskName || taskId} 超过 10 秒无进展，已自动结束并重启`)
    watchdogState.value = {
      ...watchdogState.value,
      restarting: false,
      lastSignalAt: Date.now(),
      lastSignalSourceAt: Date.now(),
      lastSignature: '',
      message: `最近一次自动重启时间：${formatTime(new Date().toISOString())}`
    }
    boostPolling(60000)
    await loadDashboard({ silent: true })
  } catch (e) {
    watchdogState.value = {
      ...watchdogState.value,
      restarting: false,
      message: `自动重启失败：${normalizeError(e)}`
    }
    setFeedback('error', normalizeError(e))
  } finally {
    statusUpdating.value = ''
  }
}

function syncRealtimeWatchdog() {
  const task = realtimeTask.value
  const runtimeStatus = deriveTaskRuntimeStatus(task)
  if (!task?.taskId) {
    watchdogState.value = {
      taskId: '',
      lastSignalAt: 0,
      lastSignalSourceAt: 0,
      lastSignature: '',
      restartCount: 0,
      lastRestartAt: 0,
      restarting: false,
      message: ''
    }
    return
  }

  if (watchdogState.value.taskId !== task.taskId) {
    watchdogState.value = {
      taskId: task.taskId,
      lastSignalAt: Date.now(),
      lastSignalSourceAt: 0,
      lastSignature: '',
      restartCount: 0,
      lastRestartAt: 0,
      restarting: false,
      message: ''
    }
  }

  if (runtimeStatus !== 0 && runtimeStatus !== 1) {
    watchdogState.value = {
      ...watchdogState.value,
      restarting: false
    }
    return
  }

  const latestLog = realtimeLatestLog.value
  const activeShards = realtimeActiveShards.value
  const completedShards = realtimeCompletedShards.value
  const latestSignal = latestSignalTimestamp(task, latestLog, activeShards, completedShards)
  const signature = buildWatchdogSignature(task, latestLog, activeShards, completedShards)
  const signatureChanged = signature !== watchdogState.value.lastSignature
  const sourceAdvanced = latestSignal > watchdogState.value.lastSignalSourceAt

  if (signatureChanged || sourceAdvanced || !watchdogState.value.lastSignalAt) {
    watchdogState.value = {
      ...watchdogState.value,
      lastSignalAt: Date.now(),
      lastSignalSourceAt: Math.max(latestSignal, watchdogState.value.lastSignalSourceAt),
      lastSignature: signature,
      restarting: false,
      message: watchdogState.value.restartCount > 0
        ? `已恢复进度，最近自动重启 ${watchdogState.value.restartCount} 次。`
        : ''
    }
    return
  }

  if (watchdogState.value.restarting) return
  if (Date.now() - watchdogState.value.lastRestartAt < WATCHDOG_RESTART_COOLDOWN_MS) return

  if (Date.now() - watchdogState.value.lastSignalAt >= WATCHDOG_TIMEOUT_MS) {
    void restartTaskByWatchdog(task)
  }
}

async function openTaskDetail(task) {
  const id = task?.taskId || task?.id
  if (!id || !authStore.token) return

  detailTaskId.value = String(id)
  detailLoading.value = true
  detailTask.value = null
  try {
    detailTask.value = await fetchCrawlTask(authStore.token, id)
    trackedTask.value = detailTask.value
    pinTaskToTop(detailTask.value)
    await loadLogs(id, true)
  } catch (e) {
    setFeedback('error', normalizeError(e))
  } finally {
    detailLoading.value = false
  }
}

function closeTaskDetail() {
  detailTaskId.value = ''
  detailTask.value = null
}

onMounted(() => {
  void loadDashboard()
  startClock()
  startPolling()
})

onUnmounted(() => {
  stopClock()
  stopPolling()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero">
      <div>
        <h1 class="collector-title">数据采集</h1>
        <p class="collector-subtitle">任务调度、实时进度、数据质量与运行日志</p>
      </div>
      <div class="collector-hero-actions">
        <GlowButton variant="ghost" @click="loadDashboard">
          <RefreshCw :size="14" /> 刷新
        </GlowButton>
        <GlowButton variant="ghost" @click="router.push('/reports')">
          <FileText :size="14" /> 报告中心
        </GlowButton>
        <GlowButton variant="ghost" @click="router.push('/openapi')">
          <Info :size="14" /> 开放 API
        </GlowButton>
        <GlowButton variant="primary" @click="createFormOpen = !createFormOpen">
          <Plus :size="14" /> {{ createFormOpen ? '收起' : '新建任务' }}
        </GlowButton>
      </div>
    </section>

    <div v-if="error" class="error-banner">{{ error }}</div>
    <div v-else-if="successMsg" class="success-banner">{{ successMsg }}</div>

    <section class="metrics-grid">
      <article v-for="item in qualityCards" :key="item.label" class="collector-metric-card">
        <div class="collector-metric-head">
          <span class="collector-metric-label">{{ item.label }}</span>
          <span class="collector-metric-dot" aria-hidden="true"></span>
        </div>
        <div class="collector-metric-value">{{ item.value }}</div>
        <div class="collector-metric-note">{{ item.note }}</div>
      </article>
    </section>

    <article class="collector-panel live-panel">
      <header class="collector-panel-head">
        <div class="collector-panel-copy">
          <h2 class="collector-panel-title"><Activity :size="15" /> 实时采集进度</h2>
          <p class="collector-panel-sub">
            {{ filteredRunningTasks.length > 0 ? `当前运行中 ${filteredRunningTasks.length} 个任务` : '当前没有运行中的任务，仍会显示最近跟踪对象' }}
          </p>
        </div>
        <span class="collector-panel-badge">{{ realtimeStageLabel }}</span>
      </header>

      <div class="collector-panel-body">
        <div v-if="realtimeTask" class="live-overview">
          <div class="live-main">
            <div>
              <div class="live-title-row">
                <strong class="live-title">{{ realtimeTask.taskName || realtimeTask.taskId }}</strong>
                <span class="pill" :class="`pill-${getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).tone}`">
                  <component :is="getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).icon" :size="12" />
                  {{ getStatusMeta(deriveTaskRuntimeStatus(realtimeTask)).label }}
                </span>
              </div>
              <div class="live-subtitle">task: {{ realtimeTask.taskId || '--' }}</div>
              <p class="live-detail">{{ realtimeStageDetail }}</p>
            </div>
            <div class="live-tags">
              <span class="meta-chip">城市：{{ formatCityList(realtimeTask.city, '全域') }}</span>
              <span class="meta-chip">关键词：{{ formatList(realtimeTask.keywords, '未设置') }}</span>
              <span class="meta-chip">渠道：{{ formatChannel(realtimeTask.channel) }}</span>
            </div>
          </div>

          <div class="live-progress-card">
            <div class="task-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: `${progressPercent(realtimeTask)}%` }" />
              </div>
              <span class="progress-count">{{ progressText(realtimeTask) }}</span>
            </div>
            <div class="live-stats">
              <div class="live-stat">
                <span>总分片</span>
                <strong>{{ realtimeShardStats.total }}</strong>
              </div>
              <div class="live-stat">
                <span>等待中</span>
                <strong>{{ realtimeShardStats.pending }}</strong>
              </div>
              <div class="live-stat">
                <span>运行中</span>
                <strong>{{ realtimeShardStats.running }}</strong>
              </div>
              <div class="live-stat">
                <span>已完成</span>
                <strong>{{ realtimeShardStats.completed }}</strong>
              </div>
              <div class="live-stat">
                <span>失败</span>
                <strong>{{ realtimeShardStats.failed }}</strong>
              </div>
            </div>
          </div>

          <div v-if="realtimeWatchdogVisible" class="watchdog-card" :class="{ 'is-restarting': watchdogState.restarting }">
            <div class="watchdog-head">
              <strong>自动重启守护</strong>
              <span class="watchdog-badge">
                {{ watchdogState.restarting ? '重启中' : `倒计时 ${realtimeWatchdogCountdown ?? '--'} 秒` }}
              </span>
            </div>
            <div class="watchdog-body">{{ realtimeWatchdogText }}</div>
          </div>

          <div class="live-data-grid">
            <article v-for="card in realtimeDataCards" :key="card.label" class="live-data-card">
              <span class="live-data-label">{{ card.label }}</span>
              <strong class="live-data-value">{{ card.value }}</strong>
              <span class="live-data-note">{{ card.note }}</span>
            </article>
          </div>

          <div class="live-shard-section">
            <div class="live-shard-card">
              <div class="live-shard-head">
                <strong>当前抓取到哪里</strong>
                <span>{{ realtimeActiveShards.length }} 个分片</span>
              </div>
              <div v-if="realtimeActiveShards.length" class="shard-list">
                <article v-for="item in realtimeActiveShards" :key="item.shardId" class="shard-item shard-item-active">
                  <div class="shard-title-row">
                    <strong>{{ formatShardSummary(item) }}</strong>
                    <span class="shard-status">节点 {{ item.workerId || '待确认' }}</span>
                  </div>
                  <div class="shard-meta-row">
                    <span>{{ formatShardResult(item) }}</span>
                    <span>{{ formatTime(item.startTime || realtimeTask.startTime) }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">当前还没有正在抓取的分片，可能处于排队、鉴权或调度阶段。</div>
            </div>

            <div class="live-shard-card">
              <div class="live-shard-head">
                <strong>已经抓到什么</strong>
                <span>最近 {{ realtimeCompletedShards.length }} 个完成分片</span>
              </div>
              <div v-if="realtimeCompletedShards.length" class="shard-list">
                <article v-for="item in realtimeCompletedShards" :key="item.shardId" class="shard-item">
                  <div class="shard-title-row">
                    <strong>{{ formatShardSummary(item) }}</strong>
                    <span class="shard-status">已完成</span>
                  </div>
                  <div class="shard-meta-row">
                    <span>{{ formatShardResult(item) }}</span>
                    <span>{{ formatTime(item.endTime || item.startTime) }}</span>
                  </div>
                </article>
              </div>
              <div v-else class="empty-inline">当前还没有完成分片，采集结果会在这里实时出现。</div>
            </div>
          </div>

          <div class="live-log-card">
            <div class="live-log-head">
              <strong>最新实时提示</strong>
              <span>{{ formatTime(realtimeLatestLog?.createTime || realtimeTask.updateTime || realtimeTask.endTime || realtimeTask.startTime) }}</span>
            </div>
            <div class="live-log-body">
              {{ realtimeLatestLog?.message || '当前还没有可展示的最新日志，页面会继续轮询更新。' }}
            </div>
          </div>
        </div>
        <div v-else class="empty-block">当前没有可展示的采集进度。</div>
      </div>
    </article>

    <transition name="collapse">
      <article v-if="createFormOpen" class="collector-panel create-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><Plus :size="15" /> 创建任务</h2>
            <p class="collector-panel-sub">填写关键词、城市和目标条数后即可加入采集队列。</p>
          </div>
          <button class="icon-close" type="button" aria-label="收起" @click="createFormOpen = false">
            <CloseIcon :size="16" />
          </button>
        </header>

        <div class="collector-panel-body">
          <div class="task-form">
            <div class="form-row">
              <label class="field">
                <span class="field-label">任务名称</span>
                <input
                  v-model="taskForm.taskName"
                  class="collector-input"
                  placeholder="例如：成都 Python 实时采集"
                />
              </label>
              <label class="field">
                <span class="field-label">渠道</span>
                <input
                  class="collector-input"
                  :value="SOURCE_CHANNEL_LABEL"
                  disabled
                  aria-label="固定来源渠道：智联招聘"
                />
              </label>
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="taskForm.city" class="collector-input" placeholder="如 801 / 成都" />
              </label>
            </div>

            <div class="form-row">
              <label class="field field-grow">
                <span class="field-label">关键词</span>
                <input
                  v-model="taskForm.keywords"
                  class="collector-input"
                  placeholder="如 Python, 数据分析, Java"
                />
              </label>
              <label class="field field-priority">
                <span class="field-label">目标条数</span>
                <select v-model.number="taskForm.targetCount" class="collector-input">
                  <option v-for="count in TARGET_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 条</option>
                </select>
                <span class="field-help">预计请求 {{ Math.max(1, Math.ceil((taskForm.targetCount || 0) / 10)) }} 页</span>
              </label>
              <label class="field field-priority">
                <span class="field-label">优先级</span>
                <input
                  v-model.number="taskForm.priority"
                  class="collector-input"
                  type="number"
                  min="1"
                  max="10"
                />
              </label>
              <GlowButton variant="primary" :loading="submitting" @click="handleCreateTask" class="form-submit">
                <Plus :size="15" />
                创建
              </GlowButton>
            </div>
          </div>
        </div>
      </article>
    </transition>

    <article class="collector-panel">
      <header class="collector-panel-head">
        <div class="collector-panel-copy">
          <h2 class="collector-panel-title"><FileText :size="15" /> 任务队列</h2>
          <p class="collector-panel-sub">
            第 {{ taskPage }} / {{ taskTotalPages }} 页，共 {{ totalTasks }} 个任务
            <span v-if="showTaskRefreshing">，正在同步最新状态</span>
          </p>
        </div>
        <div class="collector-panel-tools">
          <select
            v-model="filters.channel"
            class="collector-input slim"
            disabled
            aria-label="固定来源渠道：智联招聘"
            @change="applyFilters"
          >
            <option value="zhaopin">智联招聘</option>
          </select>
          <select v-model="filters.status" class="collector-input slim" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="0">待启动</option>
            <option value="1">运行中</option>
            <option value="2">已完成</option>
            <option value="3">已结束</option>
          </select>
          <select v-model.number="taskPageSize" class="collector-input slim" @change="applyFilters" aria-label="每页条数">
            <option :value="10">10 / 页</option>
            <option :value="20">20 / 页</option>
            <option :value="50">50 / 页</option>
          </select>
        </div>
      </header>

      <div class="collector-panel-body">
        <div v-if="showInitialTaskLoading" class="empty-block">正在同步任务状态...</div>
        <div v-else-if="tasks.length === 0" class="empty-block">当前筛选下没有采集任务。</div>
        <div v-else class="task-list">
          <article
            v-for="task in tasks"
            :key="task.taskId"
            class="task-row"
            :class="{ active: activeTaskId === task.taskId }"
            tabindex="0"
            @click="loadLogs(task.taskId)"
            @keydown.enter.prevent="loadLogs(task.taskId)"
            @keydown.space.prevent="loadLogs(task.taskId)"
          >
            <div class="task-head">
              <div class="task-main">
                <h3 class="task-title">{{ task.taskName }}</h3>
                <p class="task-subtitle">
                  {{ formatChannel(task.channel) }} · {{ formatCityList(task.city, '全域') }} · {{ formatList(task.keywords, '未设置关键词') }}
                </p>
              </div>
              <span class="pill" :class="`pill-${getStatusMeta(deriveTaskRuntimeStatus(task)).tone}`">
                <component :is="getStatusMeta(deriveTaskRuntimeStatus(task)).icon" :size="12" />
                {{ getStatusMeta(deriveTaskRuntimeStatus(task)).label }}
              </span>
            </div>

            <div class="task-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
              </div>
              <span class="progress-count">{{ progressText(task) }}</span>
            </div>

            <div class="task-meta">
              <span>优先级 P{{ task.priority || 5 }}</span>
              <span>去重 {{ task.duplicateCount || 0 }}</span>
              <span>创建时间 {{ formatTime(task.createTime) }}</span>
            </div>

            <div class="task-actions">
              <button class="mini-action" @click.stop="openTaskDetail(task)">
                <Info :size="13" /> 详情
              </button>
              <button
                class="mini-action"
                :disabled="!canStartTask(task) || statusUpdating === `${task.taskId}:1`"
                @click.stop="handleTaskStatus(task, 1)"
              >
                <PlayCircle :size="13" /> {{ startActionLabel(task) }}
              </button>
              <button
                class="mini-action"
                :disabled="!canPauseTask(task) || statusUpdating === `${task.taskId}:0`"
                @click.stop="handleTaskStatus(task, 0)"
              >
                <PauseCircle :size="13" /> 暂停
              </button>
              <button
                class="mini-action danger"
                :disabled="!canFinishTask(task) || statusUpdating === `${task.taskId}:3`"
                @click.stop="handleTaskStatus(task, 3)"
              >
                <SquareX :size="13" /> 结束
              </button>
            </div>
          </article>
        </div>

        <nav v-if="taskTotalPages > 1" class="task-pager" aria-label="任务分页">
          <button type="button" class="pager-btn" :disabled="taskPage <= 1" @click="goToPage(taskPage - 1)">
            <ChevronLeft :size="14" /> 上一页
          </button>
          <span class="pager-info">第 <strong>{{ taskPage }}</strong> / {{ taskTotalPages }} 页</span>
          <button type="button" class="pager-btn" :disabled="taskPage >= taskTotalPages" @click="goToPage(taskPage + 1)">
            下一页 <ChevronRight :size="14" />
          </button>
        </nav>
      </div>
    </article>

    <section class="collector-bottom">
      <article class="collector-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><ShieldCheck :size="15" /> 数据质量</h2>
            <p class="collector-panel-sub">字段完整率、数据新鲜度与异常统计</p>
          </div>
          <span class="collector-panel-badge">岗位库体检</span>
        </header>

        <div class="collector-panel-body quality-body">
          <section class="quality-section">
            <h3 class="quality-label">字段完整率</h3>
            <div class="quality-list">
              <div class="quality-row">
                <span>公司名称</span>
                <strong>{{ quality.completeness?.companyNameRate || '--' }}</strong>
              </div>
              <div class="quality-row">
                <span>学历</span>
                <strong>{{ quality.completeness?.educationRate || '--' }}</strong>
              </div>
              <div class="quality-row">
                <span>经验</span>
                <strong>{{ quality.completeness?.experienceRate || '--' }}</strong>
              </div>
              <div class="quality-row">
                <span>描述</span>
                <strong>{{ quality.completeness?.descriptionRate || '--' }}</strong>
              </div>
            </div>
          </section>

          <section class="quality-section">
            <h3 class="quality-label">数据新鲜度</h3>
            <div class="freshness-list">
              <div v-for="item in freshnessRows" :key="item.period" class="freshness-row">
                <span class="freshness-label">{{ item.period }}</span>
                <div class="freshness-bar">
                  <div
                    class="freshness-fill"
                    :style="{ width: `${Math.min(100, Number(item.count || 0) / Math.max(Number(quality.totalJobs || 1), 1) * 100)}%` }"
                  />
                </div>
                <strong class="freshness-count">{{ item.count }}</strong>
              </div>
            </div>
          </section>

          <section class="quality-foot">
            <div class="quality-stat">
              <span>异常薪资</span>
              <strong>{{ quality.salaryAnomalyCount ?? '--' }}</strong>
            </div>
            <div class="quality-stat">
              <span>重复候选</span>
              <strong>{{ quality.duplicateCandidates ?? '--' }}</strong>
            </div>
            <div class="quality-stat">
              <span>历史快照</span>
              <strong>{{ quality.jobHistorySnapshots ?? '--' }}</strong>
            </div>
          </section>
        </div>
      </article>

      <article class="collector-panel log-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><TerminalSquare :size="15" /> 实时抓取日志</h2>
            <p class="collector-panel-sub">
              {{ activeTaskId ? `task: ${activeTaskId}` : '点击上方任务查看日志' }}
            </p>
          </div>
        </header>

        <div class="collector-panel-body log-body">
          <div v-if="logsLoading" class="empty-block">正在拉取日志...</div>
          <div v-else-if="logs.length === 0" class="empty-block">当前任务暂无日志输出。</div>
          <div v-else class="log-stream">
            <article v-for="item in logs" :key="item.logId" class="log-line">
              <div class="log-meta-line">
                <span class="log-level" :class="(item.level || 'INFO').toLowerCase()">
                  {{ item.level || 'INFO' }}
                </span>
                <span class="log-worker">{{ item.workerId || 'worker-unknown' }}</span>
                <span v-if="item.shardId" class="log-chip">shard: {{ item.shardId }}</span>
                <span class="log-time">{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="log-message">{{ item.message }}</div>
            </article>
          </div>
        </div>
      </article>
    </section>

    <transition name="drawer-fade">
      <div v-if="detailTaskId" class="task-detail-mask" role="dialog" aria-modal="true" @click.self="closeTaskDetail">
        <aside class="task-detail-drawer">
          <header class="task-detail-head">
            <div>
              <h2 class="task-detail-title">任务详情</h2>
              <p class="task-detail-sub">{{ detailTaskId }}</p>
            </div>
            <button class="icon-close" type="button" aria-label="关闭" @click="closeTaskDetail">
              <CloseIcon :size="18" />
            </button>
          </header>

          <div class="task-detail-body">
            <div v-if="detailLoading" class="empty-block">正在加载任务详情...</div>
            <template v-else-if="detailTask">
              <section class="detail-section">
                <h3 class="detail-section-title">基础信息</h3>
                <dl class="detail-grid">
                  <div v-for="row in detailMetaRows" :key="row.label" class="detail-row">
                    <dt>{{ row.label }}</dt>
                    <dd>{{ row.value ?? '--' }}</dd>
                  </div>
                </dl>
              </section>

              <section v-if="detailTask.errorStack || detailTask.errorMessage" class="detail-section">
                <h3 class="detail-section-title">错误信息</h3>
                <pre class="detail-pre error">{{ detailTask.errorStack || detailTask.errorMessage }}</pre>
              </section>

              <section v-if="detailTask.configSnapshot || detailTask.configJson" class="detail-section">
                <h3 class="detail-section-title">配置快照</h3>
                <pre class="detail-pre">{{ typeof (detailTask.configSnapshot || detailTask.configJson) === 'string'
                  ? (detailTask.configSnapshot || detailTask.configJson)
                  : JSON.stringify(detailTask.configSnapshot || detailTask.configJson, null, 2) }}</pre>
              </section>

              <section v-if="Array.isArray(detailTask.timeline) && detailTask.timeline.length" class="detail-section">
                <h3 class="detail-section-title">时间线</h3>
                <ol class="detail-timeline">
                  <li v-for="(item, idx) in detailTask.timeline" :key="idx">
                    <span class="timeline-time">{{ formatTime(item.time || item.at || item.timestamp) }}</span>
                    <span class="timeline-label">{{ item.label || item.event || item.stage || '--' }}</span>
                    <span v-if="item.detail || item.message" class="timeline-detail">{{ item.detail || item.message }}</span>
                  </li>
                </ol>
              </section>

              <section class="detail-section">
                <h3 class="detail-section-title">原始返回</h3>
                <pre class="detail-pre subtle">{{ JSON.stringify(detailTask, null, 2) }}</pre>
              </section>
            </template>
            <div v-else class="empty-block">未找到任务数据。</div>
          </div>
        </aside>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.collector-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.collector-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 30px);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
}

.collector-subtitle {
  margin: 4px 0 0;
  color: var(--c-text-muted);
  font-size: 13px;
}

.collector-hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.error-banner,
.success-banner {
  padding: 12px 16px;
  border-radius: 12px;
  font-family: var(--font-sans);
  font-size: 13px;
}

.error-banner {
  border: 1px solid rgba(178, 59, 46, 0.22);
  background: rgba(254, 242, 240, 0.92);
  color: #b23b2e;
}

.success-banner {
  border: 1px solid rgba(30, 138, 91, 0.22);
  background: rgba(236, 253, 245, 0.92);
  color: #1e8a5b;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.collector-metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 148px;
  padding: 18px 20px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
}

.collector-metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.collector-metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.collector-metric-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0.75;
}

.collector-metric-value {
  margin-top: auto;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.1;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

.collector-metric-note {
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

.collector-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}

.collector-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.collector-panel-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.collector-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.collector-panel-title :deep(svg) {
  color: var(--c-accent-primary);
  flex: none;
}

.collector-panel-sub {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.collector-panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
}

.collector-panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.live-overview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.live-main,
.live-progress-card,
.live-log-card,
.watchdog-card,
.live-shard-card,
.live-data-card {
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  padding: 14px 16px;
}

.live-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.live-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.live-title {
  font-family: var(--font-serif);
  font-size: 18px;
  color: var(--c-text-primary);
}

.live-subtitle {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  word-break: break-all;
}

.live-detail {
  margin: 10px 0 0;
  color: var(--c-text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.live-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-size: 12px;
}

.live-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.watchdog-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-color: rgba(192, 122, 47, 0.2);
  background: linear-gradient(135deg, rgba(255, 248, 235, 0.92), rgba(255, 255, 255, 0.96));
}

.watchdog-card.is-restarting {
  border-color: rgba(191, 84, 20, 0.28);
  background: linear-gradient(135deg, rgba(255, 239, 232, 0.96), rgba(255, 250, 246, 0.98));
}

.watchdog-head,
.live-shard-head,
.shard-title-row,
.shard-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.watchdog-head strong,
.live-shard-head strong {
  color: var(--c-text-primary);
  font-size: 13px;
}

.watchdog-badge,
.shard-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(191, 84, 20, 0.08);
  color: #b85a23;
  font-size: 12px;
  font-weight: 600;
}

.watchdog-body {
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.live-data-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.live-data-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 110px;
}

.live-data-label,
.live-data-note,
.live-shard-head span,
.empty-inline {
  color: var(--c-text-muted);
  font-size: 12px;
}

.live-data-value {
  font-family: var(--font-serif);
  font-size: 26px;
  line-height: 1.1;
  color: var(--c-text-primary);
}

.live-data-note {
  line-height: 1.5;
}

.live-shard-section {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.live-shard-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shard-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.shard-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.shard-item-active {
  border-color: rgba(26, 118, 210, 0.18);
  background: linear-gradient(135deg, rgba(241, 247, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.shard-title-row strong,
.shard-meta-row span {
  font-size: 12.5px;
}

.shard-title-row strong {
  color: var(--c-text-primary);
}

.shard-meta-row span {
  color: var(--c-text-secondary);
}

.live-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
}

.live-stat span {
  font-size: 11px;
  color: var(--c-text-muted);
}

.live-stat strong {
  font-size: 18px;
  font-family: var(--font-serif);
  color: var(--c-text-primary);
}

.live-log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--c-text-secondary);
  font-size: 12px;
}

.live-log-body {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-primary);
  word-break: break-word;
}

.create-panel {
  border-left: 3px solid var(--c-accent-primary);
}

.task-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.field-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.field-help {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.form-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-row .field {
  flex: 1 1 180px;
  min-width: 140px;
}

.form-row .field-grow {
  flex: 2 1 260px;
}

.form-row .field-priority {
  flex: 0 0 140px;
  max-width: 160px;
}

.form-row .form-submit,
.form-row :deep(.glow-button.form-submit) {
  align-self: flex-end;
  flex-shrink: 0;
}

.collector-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.4;
  transition: border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.collector-input::placeholder {
  color: var(--c-text-faint);
}

.collector-input:hover {
  border-color: var(--c-border-glass-hover);
}

.collector-input:focus,
.collector-input:focus-visible {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
  outline: none;
}

.collector-input.slim {
  max-width: 180px;
}

.collector-panel-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  padding: 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 13px;
  text-align: center;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.task-row::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.task-row:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.task-row.active {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  box-shadow: 0 4px 14px var(--c-accent-primary-glow);
}

.task-row.active::before {
  opacity: 1;
}

.task-row.active .task-title {
  color: var(--c-accent-primary);
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-main {
  min-width: 0;
  flex: 1;
}

.task-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.task-subtitle {
  margin: 3px 0 0;
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-track {
  position: relative;
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--c-accent-primary);
  transition: width var(--duration-normal) var(--ease-out);
}

.progress-count {
  font-family: var(--font-mono);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-secondary);
  flex-shrink: 0;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.task-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.mini-action:hover:not(:disabled) {
  border-color: var(--c-accent-primary);
  color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
}

.mini-action.danger {
  color: #b23b2e;
}

.mini-action.danger:hover:not(:disabled) {
  border-color: #e8b7b0;
  color: #8f2c22;
  background: rgba(178, 59, 46, 0.04);
}

.mini-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
}

.pill-idle {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.pill-running {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.pill-running :deep(svg) {
  animation: spin 1.2s linear infinite;
}

.pill-paused {
  background: rgba(164, 94, 5, 0.12);
  color: #a45e05;
}

.pill-done {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.task-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  padding: 10px 0 2px;
}

.pager-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-size: 13px;
  cursor: pointer;
}

.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pager-info {
  color: var(--c-text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.pager-info strong {
  color: var(--c-accent-primary);
}

.collector-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 20px;
  align-items: stretch;
}

.quality-body {
  gap: 18px;
}

.quality-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quality-label {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.quality-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
}

.quality-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.quality-row span {
  font-size: 12px;
  color: var(--c-text-muted);
}

.quality-row strong {
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--c-text-primary);
}

.freshness-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.freshness-row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.freshness-label {
  font-size: 12px;
  color: var(--c-text-secondary);
}

.freshness-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.freshness-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--c-accent-primary);
}

.freshness-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-primary);
  min-width: 40px;
  text-align: right;
}

.quality-foot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.quality-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.quality-stat span {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.quality-stat strong {
  font-family: var(--font-serif);
  font-size: 18px;
  color: var(--c-text-primary);
}

.log-body {
  padding-bottom: 16px;
}

.log-stream {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 6px 4px 14px;
  border-left: 2px solid var(--c-border-glass);
}

.log-line {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
}

.log-meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-level {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 700;
}

.log-level.info {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.log-level.warn {
  background: rgba(164, 94, 5, 0.12);
  color: #a45e05;
}

.log-level.error {
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
}

.log-worker,
.log-time,
.log-chip {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.log-time {
  margin-left: auto;
}

.log-message {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
  color: var(--c-text-primary);
  word-break: break-word;
}

.task-detail-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
}

.task-detail-drawer {
  width: min(520px, 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  border-left: 1px solid var(--c-border-glass);
  box-shadow: -12px 0 32px rgba(15, 23, 42, 0.18);
}

.task-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.task-detail-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.task-detail-sub {
  margin: 3px 0 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-muted);
  word-break: break-all;
}

.icon-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
}

.task-detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-section-title {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
}

.detail-row dt {
  font-size: 11px;
  color: var(--c-text-muted);
}

.detail-row dd {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-primary);
  word-break: break-all;
}

.detail-pre {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.detail-pre.error {
  background: rgba(178, 59, 46, 0.08);
  color: #b23b2e;
  border-color: rgba(178, 59, 46, 0.22);
}

.detail-pre.subtle {
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
}

.detail-timeline {
  margin: 0;
  padding: 0 0 0 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
}

.detail-timeline li {
  display: grid;
  grid-template-columns: 110px auto 1fr;
  gap: 8px;
  align-items: baseline;
}

.timeline-time {
  font-family: var(--font-mono);
  color: var(--c-text-muted);
  font-size: 11.5px;
}

.timeline-label {
  color: var(--c-text-primary);
  font-weight: 600;
}

.timeline-detail {
  color: var(--c-text-muted);
}

.collapse-enter-active,
.collapse-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 180ms ease;
}

.drawer-fade-enter-active .task-detail-drawer,
.drawer-fade-leave-active .task-detail-drawer {
  transition: transform 220ms ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-fade-enter-from .task-detail-drawer,
.drawer-fade-leave-to .task-detail-drawer {
  transform: translateX(24px);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1279px) {
  .collector-bottom {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .live-data-grid,
  .live-shard-section,
  .live-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .live-data-grid,
  .live-shard-section,
  .quality-list,
  .quality-foot,
  .live-stats {
    grid-template-columns: 1fr;
  }

  .collector-panel-head,
  .form-row,
  .collector-panel-tools,
  .task-actions,
  .task-head {
    flex-direction: column;
    align-items: stretch;
  }

  .collector-panel-body {
    padding: 14px 16px 16px;
  }

  .collector-panel-head,
  .task-detail-head {
    padding: 16px;
  }

  .task-detail-body {
    padding: 16px;
  }

  .detail-grid,
  .detail-timeline li {
    grid-template-columns: 1fr;
  }
}
</style>
