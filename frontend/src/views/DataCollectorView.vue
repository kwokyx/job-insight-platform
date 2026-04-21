<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  Bot,
  CheckCircle2,
  Clock3,
  ExternalLink,
  LoaderCircle,
  PlayCircle,
  Plus,
  RefreshCcw,
  Server,
  ShieldAlert,
  SquareChartGantt,
  Workflow
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import { useAuthStore } from '../store/auth'

const ACQ_API_BASE = import.meta.env.VITE_ACQ_API_BASE || 'http://localhost:8001/api'
const DOCS_URL = import.meta.env.VITE_ACQ_DOCS_URL || 'http://127.0.0.1:8080/doc.html'
const REDOC_URL = import.meta.env.VITE_ACQ_REDOC_URL || 'http://127.0.0.1:8080/doc.html'
const RABBITMQ_URL = import.meta.env.VITE_RABBITMQ_URL || 'http://localhost:15672'
const BACKEND_CRAWL_BASE = '/api/v1/crawl'

const DAILY_TEMPLATE_NAME = 'daily-incremental-python-801'
const DAILY_TEMPLATE_BASELINE = 'task_395ff83a4dad4212'
const ETL_TASK_NAME = 'CareerPlatformBizSyncDaily'
const authStore = useAuthStore()

const loading = ref(false)
const creating = ref(false)
const creatingTemplate = ref(false)
const startingTaskId = ref('')
const selectedTaskId = ref('')
const activeBoard = ref('immediate')
const error = ref('')
const success = ref('')
const dashboard = ref(null)
const authStatus = ref(null)
const tasks = ref([])
const selectedTask = ref(null)
const selectedTaskStats = ref(null)
const selectedTaskShards = ref([])

const taskForm = ref({
  taskName: '最小自动采集任务',
  channel: 'zhaopin',
  keywords: 'Python',
  city: '801',
  pageCount: 1,
  priority: 8
})
const sourceForm = ref({
  sourceName: '智联招聘',
  sourceCode: 'zhaopin'
})

function authHeaders() {
  return authStore.token
    ? { Authorization: `Bearer ${authStore.token}` }
    : {}
}

function formatTime(value) {
  return formatDateTime(value)
}

function normalizeBackendTask(task = {}) {
  return {
    task_id: task.taskId || task.task_id || '',
    task_name: task.taskName || task.task_name || '',
    channel: task.channel || '',
    keywords: task.keywords || '',
    city: task.city || '',
    status: Number(task.status ?? 0),
    priority: Number(task.priority ?? 5),
    total_count: Number(task.totalCount ?? task.total_count ?? 0),
    finished_count: Number(task.finishedCount ?? task.finished_count ?? 0),
    duplicate_count: Number(task.duplicateCount ?? task.duplicate_count ?? 0),
    duplicateCount: Number(task.duplicateCount ?? task.duplicate_count ?? 0),
    createTime: task.createTime || task.create_time || '',
    updateTime: task.updateTime || task.update_time || '',
    start_time: task.startTime || task.start_time || '',
    end_time: task.endTime || task.end_time || '',
    last_success_at: task.endTime || task.last_success_at || '',
    new_count: Number(task.totalCount ?? task.total_count ?? 0),
    updated_count: Number(task.duplicateCount ?? task.duplicate_count ?? 0),
    schedule_type: task.taskName === DAILY_TEMPLATE_NAME ? 'SCHEDULED_TEMPLATE' : 'IMMEDIATE',
    schedule_time: '09:00',
    next_run_time: '',
    incremental: true
  }
}

function normalizeBackendLogShard(log = {}, index = 0) {
  const level = String(log.level || '').toUpperCase()
  const failed = level.includes('ERROR') || level.includes('FAIL')
  return {
    shard_id: log.id || `log-${index}`,
    worker_id: log.workerId || log.operator || 'backend-log',
    status: failed ? 3 : 2,
    stop_reason: log.message || '',
    start_time: log.createTime || '',
    end_time: log.createTime || '',
    keyword: '',
    city: '',
    category_code: '',
    message: log.message || ''
  }
}

async function backendRequest(path, options = {}) {
  const response = await fetch(`${BACKEND_CRAWL_BASE}${path}`, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
      ...(options.headers || {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok || (payload.code && payload.code !== 200)) {
    throw new Error(payload.message || `采集中心请求失败: ${response.status}`)
  }
  return payload
}

async function backendCompatRequest(path, options = {}) {
  if (path === '/monitor/dashboard') {
    const [qualityPayload, tasksPayload, sourcesPayload] = await Promise.all([
      backendRequest('/tasks/quality'),
      backendRequest('/tasks?page=1&pageSize=50'),
      backendRequest('/sources')
    ])
    const taskItems = (tasksPayload.data || []).map(normalizeBackendTask)
    const sourceItems = sourcesPayload.data?.records || sourcesPayload.data || []
    return {
      stats: {
        online_workers: sourceItems.filter((item) => item.isActive === 1).length || sourceItems.length || '--',
        running_tasks: taskItems.filter((item) => item.status === 1).length,
        today_collected: qualityPayload.data?.totalJobs ?? '--',
        success_rate: 100 - Number(qualityPayload.data?.suspectedZombieJobRate?.replace?.('%', '') || 0)
      },
      worker_stats: sourceItems.map((item) => ({
        worker_id: item.sourceCode || item.sourceName || `source-${item.id}`,
        status: item.healthStatus || 'UNKNOWN',
        cpu_usage: '--',
        memory_usage: '--',
        completed_shards: 0,
        total_shards: 0
      })),
      quality: qualityPayload.data || {}
    }
  }

  if (path === '/config/zhaopin/auth/status') {
    const payload = await backendRequest('/sources')
    const records = payload.data?.records || payload.data || []
    const source = records.find((item) => item.sourceCode === 'zhaopin') || records[0] || null
    return {
      cookie_present: Boolean(source),
      updated_at: source?.updatedAt || source?.lastCrawlAt || ''
    }
  }

  if (path.startsWith('/tasks/?')) {
    const search = new URLSearchParams(path.split('?')[1] || '')
    const page = search.get('page') || '1'
    const size = search.get('size') || '50'
    const payload = await backendRequest(`/tasks?page=${page}&pageSize=${size}`)
    return {
      items: (payload.data || []).map(normalizeBackendTask),
      total: payload.total || 0
    }
  }

  if (/^\/tasks\/[^/]+$/.test(path)) {
    const taskId = path.split('/')[2]
    const payload = await backendRequest(`/tasks/${taskId}`)
    return normalizeBackendTask(payload.data || {})
  }

  if (/^\/tasks\/[^/]+\/stats$/.test(path)) {
    const taskId = path.split('/')[2]
    const payload = await backendRequest(`/tasks/${taskId}`)
    const task = normalizeBackendTask(payload.data || {})
    return {
      completed_shards: task.finished_count,
      total_shards: task.total_count,
      new_count: task.finished_count,
      updated_count: task.duplicate_count
    }
  }

  if (/^\/tasks\/[^/]+\/shards/.test(path)) {
    const taskId = path.split('/')[2]
    const payload = await backendRequest(`/tasks/${taskId}/logs?page=1&pageSize=20`)
    return {
      items: (payload.data || []).map((item, index) => normalizeBackendLogShard(item, index))
    }
  }

  if (path === '/tasks/' && (options.method || 'GET').toUpperCase() === 'POST') {
    const payload = options.body || {}
    const created = await backendRequest('/tasks', {
      method: 'POST',
      body: {
        taskName: payload.task_name,
        channel: payload.channel,
        keywords: Array.isArray(payload.keywords) ? payload.keywords.join(',') : payload.keywords,
        city: Array.isArray(payload.city) ? payload.city.join(',') : payload.city,
        priority: payload.priority
      }
    })
    return {
      task_id: created.data?.taskId || created.data?.task_id || ''
    }
  }

  if (/^\/tasks\/[^/]+\/start$/.test(path) && (options.method || 'GET').toUpperCase() === 'POST') {
    const taskId = path.split('/')[2]
    await backendRequest(`/tasks/${taskId}/status`, {
      method: 'PUT',
      body: { status: 1 }
    })
    return { task_id: taskId }
  }

  throw new Error('当前环境未启用独立采集调度中心，且该能力尚未映射到平台后端。')
}

async function acqRequest(path, options = {}) {
  try {
    const response = await fetch(`${ACQ_API_BASE}${path}`, {
      method: options.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {})
      },
      body: options.body ? JSON.stringify(options.body) : undefined
    })

    const payload = await response.json().catch(() => ({}))
    if (!response.ok || payload.code !== 200) {
      throw new Error(payload.message || `采集中心请求失败: ${response.status}`)
    }
    return payload.data
  } catch (error) {
    return await backendCompatRequest(path, options)
  }
}

function normalizeStatus(status) {
  const mapping = {
    0: { label: '待启动', tone: 'idle' },
    1: { label: '运行中', tone: 'running' },
    2: { label: '已完成', tone: 'done' },
    3: { label: '失败', tone: 'failed' }
  }
  return mapping[status] || { label: '未知', tone: 'idle' }
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}

function asList(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  return [value]
}

function progressPercent(task) {
  const total = Number(task?.total_count || 0)
  const finished = Number(task?.finished_count || 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((finished / total) * 100)))
}

const immediateTasks = computed(() => tasks.value.filter((task) => task.schedule_type !== 'SCHEDULED_TEMPLATE'))
const scheduledTemplates = computed(() => tasks.value.filter((task) => task.schedule_type === 'SCHEDULED_TEMPLATE'))
const visibleTasks = computed(() => (activeBoard.value === 'scheduled' ? scheduledTemplates.value : immediateTasks.value))

const latestSuccessfulTask = computed(() => {
  return immediateTasks.value.find((task) => task.status === 2 && task.last_success_at) || null
})

const dailyTemplate = computed(() => {
  return scheduledTemplates.value.find((task) => task.task_name === DAILY_TEMPLATE_NAME) || null
})

const failedShards = computed(() => selectedTaskShards.value.filter((shard) => shard.status === 3))

const connectionCards = computed(() => {
  const stats = dashboard.value?.stats || {}
  return [
    {
      label: '在线节点',
      value: stats.online_workers ?? '--',
      note: 'hadoop001 / hadoop002 / hadoop003'
    },
    {
      label: '运行任务',
      value: stats.running_tasks ?? '--',
      note: '调度中心当前执行中的任务数'
    },
    {
      label: '今日采集',
      value: stats.today_collected ?? '--',
      note: '今日已进入 crawl_job_posting 的岗位数'
    },
    {
      label: '成功率',
      value: stats.success_rate != null ? `${stats.success_rate}%` : '--',
      note: '按今日分片执行结果统计'
    }
  ]
})

const automationCards = computed(() => {
  const template = dailyTemplate.value
  const baseline = latestSuccessfulTask.value
  return [
    {
      title: '智联鉴权状态',
      value: authStatus.value?.cookie_present ? '已注入' : '未注入',
      note: authStatus.value?.cookie_present
        ? `最近更新时间 ${formatDateTime(authStatus.value.updated_at)}，三台节点已可复用登录态`
        : '当前没有可复用登录态，受保护接口稳定性会下降',
      tone: authStatus.value?.cookie_present ? 'good' : 'warn'
    },
    {
      title: '每日自动采集',
      value: template ? '已配置' : '未配置',
      note: template
        ? `每天 ${template.schedule_time || '--'} 自动生成增量任务，下一次 ${formatDateTime(template.next_run_time)}`
        : '当前页面可一键补齐每日增量模板',
      tone: template ? 'good' : 'warn'
    },
    {
      title: '自动清洗入库',
      value: '已落地',
      note: `Windows 计划任务 ${ETL_TASK_NAME} 每天 09:20 同步 crawl_job_posting -> biz_job_posting`,
      tone: 'good'
    },
    {
      title: '增量基线',
      value: baseline?.task_id || DAILY_TEMPLATE_BASELINE,
      note: baseline
        ? `最近成功基线 ${formatDateTime(baseline.last_success_at)}，新增 ${baseline.new_count}，更新 ${baseline.updated_count}`
        : '基线任务已预置，自动任务会按基线做增量',
      tone: 'neutral'
    }
  ]
})

const workerNarrative = computed(() => {
  const workers = dashboard.value?.worker_stats || []
  if (!workers.length) return '当前未发现在线 worker。'
  return workers.map((worker) => `${worker.worker_id} ${worker.status}`).join(' · ')
})

async function loadDashboard() {
  dashboard.value = await acqRequest('/monitor/dashboard')
}

async function loadAuthStatus() {
  authStatus.value = await acqRequest('/config/zhaopin/auth/status')
}

async function loadTasks() {
  const data = await acqRequest('/tasks/?page=1&size=50')
  tasks.value = data.items || []

  if (!selectedTaskId.value) {
    const preferred = immediateTasks.value[0] || scheduledTemplates.value[0]
    if (preferred) {
      selectedTaskId.value = preferred.task_id
    }
  }
}

async function loadTaskDetail(taskId) {
  if (!taskId) return
  selectedTaskId.value = taskId
  const [task, stats, shards] = await Promise.all([
    acqRequest(`/tasks/${taskId}`),
    acqRequest(`/tasks/${taskId}/stats`),
    acqRequest(`/tasks/${taskId}/shards?page=1&size=20`)
  ])
  selectedTask.value = task
  selectedTaskStats.value = stats
  selectedTaskShards.value = shards.items || []
}

async function refreshAll(taskId = selectedTaskId.value) {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadDashboard(), loadTasks(), loadAuthStatus()])
    if (taskId) {
      await loadTaskDetail(taskId)
    }
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    loading.value = false
  }
}

async function createTask(autoStart = false) {
  creating.value = true
  error.value = ''
  success.value = ''
  try {
    const payload = {
      task_name: taskForm.value.taskName.trim(),
      channel: taskForm.value.channel,
      keywords: asList(taskForm.value.keywords.split(',').map((item) => item.trim())),
      city: asList(taskForm.value.city.split(',').map((item) => item.trim())),
      page_count: Number(taskForm.value.pageCount) || 1,
      schedule_type: 'IMMEDIATE',
      schedule_timezone: 'Asia/Shanghai',
      priority: Number(taskForm.value.priority) || 5,
      create_user: 'frontend'
    }

    const result = await acqRequest('/tasks/', {
      method: 'POST',
      body: payload
    })

    selectedTaskId.value = result.task_id
    activeBoard.value = 'immediate'
    success.value = autoStart
      ? `任务 ${result.task_id} 已创建，正在启动`
      : `任务 ${result.task_id} 已创建`

    if (autoStart) {
      await startTask(result.task_id, false)
    }

    await refreshAll(result.task_id)
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    creating.value = false
  }
}

async function createDailyTemplate() {
  creatingTemplate.value = true
  error.value = ''
  success.value = ''
  try {
    const payload = {
      task_name: DAILY_TEMPLATE_NAME,
      channel: 'zhaopin',
      keywords: ['Python'],
      city: ['801'],
      page_count: 1,
      priority: 6,
      schedule_type: 'SCHEDULED_TEMPLATE',
      schedule_mode: 'SCHEDULED',
      schedule_enabled: true,
      schedule_timezone: 'Asia/Shanghai',
      schedule_preset: 'DAILY',
      schedule_time: '09:00',
      incremental: true,
      baseline_task_id: latestSuccessfulTask.value?.task_id || DAILY_TEMPLATE_BASELINE,
      incremental_page_limit: 1,
      stale_page_threshold: 1,
      lookback_hours: 72,
      create_user: 'frontend-auto'
    }

    const result = await acqRequest('/tasks/', {
      method: 'POST',
      body: payload
    })

    activeBoard.value = 'scheduled'
    selectedTaskId.value = result.task_id
    success.value = `每日自动采集模板 ${result.task_id} 已创建`
    await refreshAll(result.task_id)
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    creatingTemplate.value = false
  }
}

async function startTask(taskId, refreshAfter = true) {
  startingTaskId.value = taskId
  error.value = ''
  success.value = ''
  try {
    await acqRequest(`/tasks/${taskId}/start`, { method: 'POST' })
    success.value = `任务 ${taskId} 已启动`
    if (refreshAfter) {
      await refreshAll(taskId)
    }
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    startingTaskId.value = ''
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero">
      <div class="hero-copy">
        <p class="hero-kicker">Distributed Acquisition Console</p>
        <h1 class="hero-title">分布式数据采集运行台</h1>
        <p class="hero-description">
          页面已经直连你当前主机上的调度中心。它现在不只是“发起采集”，还把每日自动采集、自动清洗入库、
          三台虚拟机执行状态和失败诊断都集中展示出来。
        </p>
      </div>

      <div class="hero-rail">
        <div class="hero-rail-card">
          <div class="rail-label">当前集群</div>
          <div class="rail-value">{{ workerNarrative }}</div>
        </div>
        <div class="hero-actions">
          <a class="hero-link" :href="DOCS_URL" target="_blank" rel="noreferrer">
            <ExternalLink :size="14" />
            Swagger
          </a>
          <a class="hero-link" :href="REDOC_URL" target="_blank" rel="noreferrer">
            <ExternalLink :size="14" />
            ReDoc
          </a>
          <a class="hero-link" :href="RABBITMQ_URL" target="_blank" rel="noreferrer">
            <ExternalLink :size="14" />
            RabbitMQ
          </a>
          <GlowButton variant="ghost" :loading="loading" @click="refreshAll()">
            <RefreshCcw :size="15" />
            刷新状态
          </GlowButton>
        </div>
      </div>
    </section>

    <div v-if="error" class="banner banner-error">{{ error }}</div>
    <div v-else-if="success" class="banner banner-success">{{ success }}</div>

    <section class="metrics-grid">
      <article v-for="card in connectionCards" :key="card.label" class="metric-card">
        <div class="metric-head">
          <span>{{ card.label }}</span>
          <Activity :size="14" />
        </div>
        <div class="metric-value">{{ card.value }}</div>
        <div class="metric-note">{{ card.note }}</div>
      </article>
    </section>

    <section class="automation-grid">
      <article
        v-for="card in automationCards"
        :key="card.title"
        class="automation-card"
        :class="`tone-${card.tone}`"
      >
        <div class="automation-head">
          <span>{{ card.title }}</span>
          <Bot v-if="card.title === '每日自动采集'" :size="16" />
          <Workflow v-else-if="card.title === '自动清洗入库'" :size="16" />
          <Clock3 v-else :size="16" />
        </div>
        <div class="automation-value">{{ card.value }}</div>
        <div class="automation-note">{{ card.note }}</div>
      </article>
    </section>

    <section class="collector-main">
      <div class="collector-col">
        <article class="collector-panel">
          <header class="collector-panel-head">
            <div>
              <h2 class="collector-panel-title"><Plus :size="15" /> 创建任务</h2>
              <p class="collector-panel-sub">填写关键信息后即可加入采集队列。</p>
            </div>
            <span class="collector-panel-badge">采集入口</span>
          </header>

          <div class="collector-panel-body split-layout">
            <div class="form-block">
              <div class="section-mini-title">最小即时采集任务</div>
              <div class="form-grid">
                <label class="field">
                  <span class="field-label">任务名称</span>
                  <input v-model="taskForm.taskName" class="collector-input" />
                </label>
                <label class="field">
                  <span class="field-label">渠道</span>
                  <select v-model="taskForm.channel" class="collector-input">
                    <option value="zhaopin">智联招聘</option>
                    <option value="boss">Boss 直聘</option>
                  </select>
                </label>
                <label class="field">
                  <span class="field-label">城市</span>
                  <input v-model="taskForm.city" class="collector-input" placeholder="801 或 成都" />
                </label>
                <label class="field">
                  <span class="field-label">数据源名称</span>
                  <input v-model="sourceForm.sourceName" class="collector-input" placeholder="例如：BOSS 直聘" />
                </label>
                <label class="field">
                  <span class="field-label">数据源编码</span>
                  <input v-model="sourceForm.sourceCode" class="collector-input" placeholder="例如：boss" />
                </label>
              </div>

              <div class="action-row">
                <GlowButton variant="secondary" :loading="creating" @click="createTask(false)">
                  <Plus :size="15" />
                  仅创建任务
                </GlowButton>
                <GlowButton variant="primary" :loading="creating" @click="createTask(true)">
                  <PlayCircle :size="15" />
                  创建并启动
                </GlowButton>
              </div>
            </div>

            <div class="automation-block">
              <div class="section-mini-title">无人值守自动化</div>
              <div class="automation-stack">
                <div class="auto-item">
                  <div class="auto-item-title">每日自动采集模板</div>
                  <div class="auto-item-note">
                    每天 09:00 自动生成增量采集任务，基于成功基线做最小页数增量抓取。
                  </div>
                </div>
                <div class="auto-item">
                  <div class="auto-item-title">每日自动清洗入库</div>
                  <div class="auto-item-note">
                    Windows 计划任务 <strong>{{ ETL_TASK_NAME }}</strong> 每天 09:20 自动把原始表同步到业务表。
                  </div>
                </div>
                <GlowButton
                  v-if="!dailyTemplate"
                  variant="primary"
                  :loading="creatingTemplate"
                  @click="createDailyTemplate"
                >
                  <Bot :size="15" />
                  创建每日自动模板
                </GlowButton>
                <div v-else class="auto-ok">
                  自动模板已存在：{{ dailyTemplate.task_id }}，下一次执行 {{ formatDateTime(dailyTemplate.next_run_time) }}
                </div>
              </div>
            </div>
          </div>
        </article>

        <article class="collector-panel">
          <header class="collector-panel-head">
            <div>
              <h2 class="collector-panel-title"><SquareChartGantt :size="16" /> 任务看板</h2>
              <p class="collector-panel-sub">支持切换查看即时任务和定时模板，方便答辩时同时展示“手动发起”和“自动运行”。</p>
            </div>
            <div class="board-switch">
              <button
                class="switch-chip"
                :class="{ active: activeBoard === 'immediate' }"
                @click="activeBoard = 'immediate'"
              >
                即时任务
              </button>
              <button
                class="switch-chip"
                :class="{ active: activeBoard === 'scheduled' }"
                @click="activeBoard = 'scheduled'"
              >
                定时模板
              </button>
            </div>
          </header>

          <div class="collector-panel-body">
            <div v-if="!visibleTasks.length" class="empty-block">当前看板下还没有任务。</div>
            <div v-else class="task-list">
              <article
                v-for="task in visibleTasks"
                :key="task.task_id"
                class="task-row"
                :class="{ active: selectedTaskId === task.task_id }"
                @click="loadTaskDetail(task.task_id)"
              >
                <div class="task-head">
                  <div class="task-main">
                    <h3 class="task-title">{{ task.task_name }}</h3>
                    <p class="task-subtitle">
                      {{ task.channel }} · {{ task.city || '全域' }} · {{ task.keywords || '无关键词' }}
                    </p>
                  </div>
                  <span class="pill" :class="`pill-${normalizeStatus(task.status).tone}`">
                    {{ normalizeStatus(task.status).label }}
                  </span>
                </div>

                <div v-if="task.schedule_type === 'SCHEDULED_TEMPLATE'" class="template-strip">
                  <span>每天 {{ task.schedule_time || '--' }}</span>
                  <span>下次 {{ formatDateTime(task.next_run_time) }}</span>
                  <span>{{ task.incremental ? '增量模式' : '全量模式' }}</span>
                </div>

                <div v-else class="task-progress">
                  <div class="progress-track">
                    <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
                  </div>
                  <span class="progress-count">{{ task.finished_count || 0 }} / {{ task.total_count || 0 }}</span>
                </div>

                <div class="task-meta">
                  <span>优先级 P{{ task.priority || 5 }}</span>
                  <span>去重 {{ task.duplicateCount || 0 }}</span>
                  <span>创建于 {{ formatTime(task.createTime) }}</span>
                </div>

                <div class="task-actions">
                  <button
                    v-if="task.status === 0 && task.schedule_type !== 'SCHEDULED_TEMPLATE'"
                    class="mini-action"
                    :disabled="startingTaskId === task.task_id"
                    @click.stop="startTask(task.task_id)"
                  >
                    <LoaderCircle v-if="startingTaskId === task.task_id" :size="14" class="spin" />
                    <PlayCircle v-else :size="14" />
                    启动
                  </button>
                  <button class="mini-action" @click.stop="loadTaskDetail(task.task_id)">查看详情</button>
                </div>
              </article>
            </div>
          </div>
        </article>
      </div>

      <div class="collector-col">
        <article class="collector-panel">
          <header class="collector-panel-head">
            <div>
              <h2 class="collector-panel-title"><Server :size="16" /> 节点与任务详情</h2>
              <p class="collector-panel-sub">
                {{ selectedTask ? `当前选中 ${selectedTask.task_id}` : '请先从左侧选择一个任务' }}
              </p>
            </div>
            <span class="collector-panel-badge">{{ dashboard?.worker_stats?.length || 0 }} 个在线 worker</span>
          </header>

          <div class="collector-panel-body">
            <div class="worker-grid">
              <article v-for="worker in dashboard?.worker_stats || []" :key="worker.worker_id" class="worker-card">
                <div class="worker-title">{{ worker.worker_id }}</div>
                <div class="worker-status">{{ worker.status }}</div>
                <div class="worker-meta">CPU {{ worker.cpu_usage }}%</div>
                <div class="worker-meta">内存 {{ worker.memory_usage }}%</div>
                <div class="worker-meta">已完成 {{ worker.completed_shards }} / {{ worker.total_shards }}</div>
              </article>
            </div>

            <div v-if="selectedTask && selectedTaskStats" class="detail-grid">
              <article class="detail-card">
                <div class="detail-label">任务状态</div>
                <div class="detail-value">{{ normalizeStatus(selectedTask.status).label }}</div>
              </article>
              <article class="detail-card">
                <div class="detail-label">分片完成</div>
                <div class="detail-value">{{ selectedTaskStats.completed_shards }} / {{ selectedTaskStats.total_shards }}</div>
              </article>
              <article class="detail-card">
                <div class="detail-label">新增岗位</div>
                <div class="detail-value">{{ selectedTaskStats.new_count }}</div>
              </article>
              <article class="detail-card">
                <div class="detail-label">更新岗位</div>
                <div class="detail-value">{{ selectedTaskStats.updated_count }}</div>
              </article>
            </div>

            <div v-if="selectedTaskShards.length" class="shard-list">
              <article v-for="shard in selectedTaskShards" :key="shard.shard_id" class="shard-row">
                <div class="shard-top">
                  <strong>{{ shard.worker_id || '未分配' }}</strong>
                  <span class="pill" :class="`pill-${normalizeStatus(shard.status).tone}`">
                    {{ normalizeStatus(shard.status).label }}
                  </span>
                </div>
                <div class="shard-meta">
                  <span>经验</span>
                  <strong>{{ dashboard?.quality?.completeness?.experienceRate || '--' }}</strong>
                </div>
                <div v-if="shard.stop_reason" class="shard-meta shard-reason">
                  <span>描述</span>
                  <span>{{ shard.stop_reason }}</span>
                </div>
                <div class="shard-meta">
                  <span>开始 {{ formatDateTime(shard.start_time) }}</span>
                  <span>结束 {{ formatDateTime(shard.end_time) }}</span>
                </div>
              </article>
            </div>

            <div v-else class="empty-block">选中任务后，这里会展示分片执行详情。</div>
          </div>
        </article>

        <article class="collector-panel">
          <header class="collector-panel-head">
            <div>
              <h2 class="collector-panel-title"><ShieldAlert :size="16" /> 失败诊断与链路说明</h2>
              <p class="collector-panel-sub">失败原因现在会直接落在分片记录中，便于你展示“系统不是黑盒”。</p>
            </div>
          </header>
          <div class="collector-panel-body">
            <div v-if="failedShards.length" class="diagnosis-list">
              <article v-for="shard in failedShards" :key="`${shard.shard_id}-diagnosis`" class="diagnosis-item">
                <div class="diagnosis-title">{{ shard.shard_id }}</div>
                <div class="diagnosis-note">
                  {{ shard.worker_id || '未分配 worker' }} · {{ shard.keyword }} / {{ shard.city }} / {{ shard.category_code }}
                </div>
                <div class="log-message">{{ shard.message || shard.stop_reason || '未记录失败日志' }}</div>
              </article>
            </div>
            <div v-else class="empty-block compact-empty">当前选中任务没有失败分片，或失败原因尚未触发。</div>

            <div class="flow-block">
              <div class="flow-step">
                <div class="flow-index">1</div>
                <div>
                  <div class="flow-title">调度中心生成任务</div>
                  <div class="flow-note">主机负责拆分分片、分发到 RabbitMQ，并维护任务生命周期。</div>
                </div>
              </div>
              <div class="flow-step">
                <div class="flow-index">2</div>
                <div>
                  <div class="flow-title">hadoop001/002/003 执行采集</div>
                  <div class="flow-note">三台虚拟机各自消费分片，返回成功、失败、提前停止原因。</div>
                </div>
              </div>
              <div class="flow-step">
                <div class="flow-index">3</div>
                <div>
                  <div class="flow-title">原始数据落入 crawl_job_posting</div>
                  <div class="flow-note">采集原始表保留最新抓取结果，供追溯、对账和清洗。</div>
                </div>
              </div>
              <div class="flow-step">
                <div class="flow-index">4</div>
                <div>
                  <div class="flow-title">自动同步到 biz_job_posting</div>
                  <div class="flow-note">每日 09:20 自动 ETL，同步后端直接使用业务表分析。</div>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.collector-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
  gap: 22px;
  padding: 28px 30px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(0, 108, 204, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(248, 251, 255, 0.98), rgba(238, 244, 251, 0.96));
  border: 1px solid rgba(142, 164, 194, 0.22);
  box-shadow: 0 22px 40px rgba(16, 40, 74, 0.08);
}

.hero-kicker {
  margin: 0 0 10px;
  color: #0d63be;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.hero-description {
  max-width: 740px;
  margin: 14px 0 0;
  color: var(--c-text-secondary);
  line-height: 1.7;
  font-size: 14px;
}

.hero-rail {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
}

.hero-rail-card {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(24, 27, 35, 0.08);
}

.rail-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.rail-value {
  margin-top: 8px;
  color: var(--c-text-primary);
  line-height: 1.6;
  font-size: 14px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.hero-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  color: var(--c-text-secondary);
  text-decoration: none;
  background: rgba(255, 255, 255, 0.82);
}

.hero-link:hover {
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.28);
}

.banner {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
}

.banner-error {
  background: rgba(178, 59, 46, 0.08);
  color: #9d3328;
  border: 1px solid rgba(178, 59, 46, 0.2);
}

.banner-success {
  background: rgba(30, 138, 91, 0.08);
  color: #1e8a5b;
  border: 1px solid rgba(30, 138, 91, 0.2);
}

.metrics-grid,
.automation-grid {
  display: grid;
  gap: 16px;
}

.metrics-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.automation-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card,
.automation-card,
.collector-panel {
  background: #fff;
  border: 1px solid var(--c-border-glass);
  border-radius: 18px;
  box-shadow: 0 8px 20px rgba(24, 27, 35, 0.05);
}

.metric-card,
.automation-card {
  padding: 18px 20px;
}

.metric-head,
.automation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-value,
.automation-value {
  margin-top: 18px;
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
}

.metric-note,
.automation-note {
  margin-top: 8px;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.tone-good {
  background: linear-gradient(180deg, rgba(246, 255, 250, 0.96), #ffffff);
}

.tone-warn {
  background: linear-gradient(180deg, rgba(255, 250, 240, 0.96), #ffffff);
}

.tone-neutral {
  background: linear-gradient(180deg, rgba(246, 249, 255, 0.96), #ffffff);
}

.collector-main {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 1fr);
  gap: 24px;
}

.collector-col {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.collector-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.collector-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
}

.collector-panel-sub {
  margin: 6px 0 0;
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.collector-panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.collector-panel-body {
  padding: 18px 22px 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.split-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 18px;
}

.form-block,
.automation-block {
  min-width: 0;
}

.section-mini-title {
  margin-bottom: 12px;
  color: var(--c-text-primary);
  font-size: 14px;
  font-weight: 700;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.collector-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  border-radius: 10px;
  background: #fff;
  font-size: 13.5px;
}

.collector-input:focus,
.collector-input:focus-visible {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(0, 87, 194, 0.12);
}

.action-row,
.task-actions,
.automation-stack {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.automation-stack {
  flex-direction: column;
}

.auto-item,
.auto-ok {
  padding: 14px 14px 15px;
  border-radius: 14px;
  background: rgba(247, 249, 252, 0.9);
  border: 1px solid rgba(24, 27, 35, 0.08);
  color: var(--c-text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.auto-item-title {
  color: var(--c-text-primary);
  font-weight: 700;
  margin-bottom: 4px;
}

.task-list,
.shard-list,
.diagnosis-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-row,
.shard-row,
.worker-card,
.detail-card,
.diagnosis-item {
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: #fff;
}

.task-row {
  padding: 14px 16px;
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
}

.task-row:hover,
.task-row.active {
  border-color: rgba(0, 87, 194, 0.28);
  box-shadow: 0 4px 14px rgba(0, 87, 194, 0.08);
  background: rgba(0, 87, 194, 0.03);
}

.task-head,
.shard-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-main {
  min-width: 0;
}

.task-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
}

.task-subtitle,
.task-meta,
.shard-meta,
.worker-meta,
.diagnosis-note {
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
}

.template-strip {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: #0d63be;
  font-size: 12px;
  font-weight: 600;
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 10px 0;
}

.progress-track {
  flex: 1;
  height: 6px;
  background: rgba(24, 27, 35, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--c-accent-primary);
}

.progress-count {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--c-text-secondary);
}

.mini-action,
.switch-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 10px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  border-radius: 999px;
  background: #fff;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.mini-action:hover:not(:disabled),
.switch-chip:hover {
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.28);
}

.mini-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.switch-chip.active {
  color: #fff;
  background: rgba(0, 89, 199, 0.94);
  border-color: rgba(0, 89, 199, 0.3);
}

.board-switch {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.pill-idle {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.pill-running {
  background: rgba(0, 87, 194, 0.1);
  color: var(--c-accent-primary);
}

.pill-done {
  background: rgba(30, 138, 91, 0.12);
  color: #1e8a5b;
}

.pill-failed {
  background: rgba(178, 59, 46, 0.1);
  color: #9d3328;
}

.worker-grid,
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.worker-card,
.detail-card,
.shard-row,
.diagnosis-item {
  padding: 12px 14px;
}

.worker-title,
.detail-value {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
}

.worker-status,
.detail-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.shard-reason,
.diagnosis-reason {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #9d3328;
  font-size: 12.5px;
  line-height: 1.6;
}

.diagnosis-title {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.flow-block {
  display: grid;
  gap: 12px;
}

.flow-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  align-items: start;
}

.flow-index {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 87, 194, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
}

.flow-title {
  color: var(--c-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.flow-note {
  margin-top: 4px;
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
}

.empty-block {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(24, 27, 35, 0.12);
  border-radius: 12px;
  color: var(--c-text-muted);
  background: var(--c-bg-surface-hover);
}

.compact-empty {
  min-height: 82px;
}

.spin {
  animation: spin 1.1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (max-width: 1180px) {
  .collector-hero,
  .metrics-grid,
  .automation-grid,
  .collector-main,
  .split-layout,
  .worker-grid,
  .detail-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .collector-hero {
    padding: 22px 18px;
    border-radius: 20px;
  }

  .collector-panel-head,
  .task-head,
  .shard-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-actions,
  .action-row,
  .task-actions,
  .board-switch {
    width: 100%;
  }
}
</style>
