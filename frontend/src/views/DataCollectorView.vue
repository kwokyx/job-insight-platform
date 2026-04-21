<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  CircleDashed,
  ExternalLink,
  Network,
  PlayCircle,
  RefreshCcw,
  Server,
  TimerReset,
  Workflow,
  Wrench
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'

const ACQ_API_BASE = import.meta.env.VITE_ACQ_API_BASE || 'http://127.0.0.1:8001/api'
const DOCS_URL = import.meta.env.VITE_ACQ_DOCS_URL || 'http://127.0.0.1:8001/docs'
const REDOC_URL = import.meta.env.VITE_ACQ_REDOC_URL || 'http://127.0.0.1:8001/redoc'
const RABBITMQ_URL = import.meta.env.VITE_RABBITMQ_URL || 'http://127.0.0.1:15672'

const loading = ref(false)
const creating = ref(false)
const startingTaskId = ref('')
const error = ref('')
const success = ref('')
const taskFilter = ref('active')

const dashboard = ref(null)
const tasks = ref([])
const selectedTaskId = ref('')
const selectedTask = ref(null)
const selectedTaskStats = ref(null)
const selectedTaskShards = ref([])

const taskForm = ref({
  taskName: '最小自动采集任务',
  channel: 'zhaopin',
  keywords: 'Python',
  city: '801',
  pageCount: 1,
  priority: 7
})

let refreshTimer = null

function normalizeStatus(status) {
  const mapping = {
    0: { label: '待启动', tone: 'idle' },
    1: { label: '运行中', tone: 'running' },
    2: { label: '已完成', tone: 'done' },
    3: { label: '失败', tone: 'failed' }
  }
  return mapping[Number(status)] || { label: '未知', tone: 'idle' }
}

function formatDateTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}

function asList(text) {
  return String(text || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function progressPercent(task) {
  const total = Number(task?.total_count || 0)
  const finished = Number(task?.finished_count || 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((finished / total) * 100)))
}

async function acqRequest(path, options = {}) {
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
}

function toTimestamp(value) {
  if (!value) return 0
  const time = new Date(String(value).replace(' ', 'T')).getTime()
  return Number.isNaN(time) ? 0 : time
}

const sortedTasks = computed(() => {
  return [...tasks.value].sort((a, b) => {
    const statusA = Number(a.status ?? 0)
    const statusB = Number(b.status ?? 0)
    const activeWeight = (status) => (status === 1 ? 3 : status === 0 ? 2 : status === 2 ? 1 : 0)
    const statusDiff = activeWeight(statusB) - activeWeight(statusA)
    if (statusDiff !== 0) return statusDiff

    const timeA = toTimestamp(a.end_time || a.start_time || a.created_at)
    const timeB = toTimestamp(b.end_time || b.start_time || b.created_at)
    return timeB - timeA
  })
})

const taskList = computed(() => {
  if (taskFilter.value === 'active') {
    return sortedTasks.value.filter((task) => Number(task.status) === 1 || Number(task.status) === 0)
  }
  if (taskFilter.value === 'failed') {
    return sortedTasks.value.filter((task) => Number(task.status) === 3)
  }
  return sortedTasks.value
})

const selectedTaskStatus = computed(() => normalizeStatus(selectedTask.value?.status))

const liveSummary = computed(() => {
  const running = Number(dashboard.value?.stats?.running_tasks || 0)
  if (running === 0) {
    return '当前无任务执行，系统没有卡住。下面主要展示历史任务结果。'
  }
  return `当前有 ${running} 个任务正在执行，页面会自动刷新。`
})

const failedShards = computed(() => {
  return selectedTaskShards.value.filter((item) => Number(item.status) === 3)
})

const workerCards = computed(() => dashboard.value?.worker_stats || [])

const overviewCards = computed(() => {
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
      note: '当前仍在执行的采集任务'
    },
    {
      label: '今日入库',
      value: stats.today_collected ?? '--',
      note: '已写入 crawl_job_posting 的数据量'
    },
    {
      label: '成功率',
      value: stats.success_rate != null ? `${stats.success_rate}%` : '--',
      note: '按分片结果统计'
    }
  ]
})

const guideSteps = computed(() => [
  '1. 在左侧填写关键词、城市和页数，点击“创建并自动开始”。',
  '2. 任务创建后会自动拆分成分片，直接派发到 3 台虚拟机节点。',
  '3. 右侧任务列表会显示进度，点任一任务可查看分片明细。',
  '4. 如果有失败分片，页面底部会直接显示失败原因，不需要再翻日志。'
])

async function loadDashboard() {
  dashboard.value = await acqRequest('/monitor/dashboard')
}

async function loadTasks() {
  const data = await acqRequest('/tasks/?page=1&size=50')
  tasks.value = data.items || []

  const activeTasks = sortedTasks.value.filter((task) => Number(task.status) === 1 || Number(task.status) === 0)
  const preferredTask = activeTasks[0] || sortedTasks.value[0] || null

  if (!selectedTaskId.value && preferredTask) {
    selectedTaskId.value = preferredTask.task_id
  }

  if (selectedTaskId.value && !tasks.value.some((task) => task.task_id === selectedTaskId.value) && preferredTask) {
    selectedTaskId.value = preferredTask.task_id
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
    await Promise.all([loadDashboard(), loadTasks()])
    if (taskId) {
      await loadTaskDetail(taskId)
    }
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    loading.value = false
  }
}

async function createTask() {
  creating.value = true
  error.value = ''
  success.value = ''

  try {
    const payload = {
      task_name: taskForm.value.taskName.trim(),
      channel: taskForm.value.channel,
      keywords: asList(taskForm.value.keywords),
      city: asList(taskForm.value.city),
      page_count: Number(taskForm.value.pageCount) || 1,
      schedule_type: 'IMMEDIATE',
      schedule_timezone: 'Asia/Shanghai',
      priority: Number(taskForm.value.priority) || 7,
      create_user: 'frontend'
    }

    const result = await acqRequest('/tasks/', {
      method: 'POST',
      body: payload
    })

    selectedTaskId.value = result.task_id
    success.value = `任务 ${result.task_id} 已创建，并将自动进入采集。`
    await refreshAll(result.task_id)
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    creating.value = false
  }
}

async function startTask(taskId) {
  startingTaskId.value = taskId
  error.value = ''
  success.value = ''
  try {
    await acqRequest(`/tasks/${taskId}/start`, { method: 'POST' })
    success.value = `任务 ${taskId} 已启动。`
    await refreshAll(taskId)
  } catch (err) {
    error.value = err.message || String(err)
  } finally {
    startingTaskId.value = ''
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = window.setInterval(() => {
    refreshAll()
  }, 8000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(async () => {
  await refreshAll()
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="hero-shell">
      <div class="hero-copy">
        <p class="hero-kicker">Distributed Data Acquisition</p>
        <h1 class="hero-title">分布式采集操作台</h1>
        <p class="hero-description">
          这个页面现在只做一件事：让你能看懂并真正用起来。左边发任务，右边看节点和结果，底部看失败原因和整条链路。
        </p>
        <div class="hero-actions">
          <GlowButton variant="primary" :loading="creating" @click="createTask">
            <PlayCircle :size="16" />
            创建并自动开始
          </GlowButton>
          <GlowButton variant="ghost" :loading="loading" @click="refreshAll()">
            <RefreshCcw :size="16" />
            刷新状态
          </GlowButton>
        </div>
      </div>

      <div class="hero-links">
        <a class="quick-link" :href="DOCS_URL" target="_blank" rel="noreferrer">
          <ExternalLink :size="15" />
          Swagger
        </a>
        <a class="quick-link" :href="REDOC_URL" target="_blank" rel="noreferrer">
          <ExternalLink :size="15" />
          ReDoc
        </a>
        <a class="quick-link" :href="RABBITMQ_URL" target="_blank" rel="noreferrer">
          <ExternalLink :size="15" />
          RabbitMQ
        </a>
      </div>
    </section>

    <div v-if="error" class="banner banner-error">{{ error }}</div>
    <div v-else-if="success" class="banner banner-success">{{ success }}</div>

    <section class="overview-grid">
      <article v-for="card in overviewCards" :key="card.label" class="overview-card">
        <div class="overview-head">
          <span>{{ card.label }}</span>
          <Activity :size="14" />
        </div>
        <div class="overview-value">{{ card.value }}</div>
        <div class="overview-note">{{ card.note }}</div>
      </article>
    </section>

    <section class="live-banner" :class="{ quiet: Number(dashboard?.stats?.running_tasks || 0) === 0 }">
      <div class="live-banner-main">
        <CircleDashed v-if="Number(dashboard?.stats?.running_tasks || 0) === 0" :size="16" />
        <CheckCircle2 v-else :size="16" />
        <span>{{ liveSummary }}</span>
      </div>
      <div class="filter-row">
        <button class="filter-chip" :class="{ active: taskFilter === 'active' }" @click="taskFilter = 'active'">当前任务</button>
        <button class="filter-chip" :class="{ active: taskFilter === 'all' }" @click="taskFilter = 'all'">全部任务</button>
        <button class="filter-chip" :class="{ active: taskFilter === 'failed' }" @click="taskFilter = 'failed'">失败任务</button>
      </div>
    </section>

    <section class="main-grid">
      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><Wrench :size="16" /> 快速创建任务</h2>
            <p class="panel-sub">默认就是最小演示任务。你只改关键词、城市和页数就能跑。</p>
          </div>
          <span class="pill pill-running">自动调度已启用</span>
        </header>

        <div class="panel-body panel-body-spacious">
          <div class="guide-list">
            <div v-for="step in guideSteps" :key="step" class="guide-item">{{ step }}</div>
          </div>

          <div class="form-grid">
            <label class="field field-span-2">
              <span class="field-label">任务名称</span>
              <input v-model="taskForm.taskName" class="collector-input" placeholder="例如：Python 最小采集演示" />
            </label>

            <label class="field">
              <span class="field-label">采集渠道</span>
              <select v-model="taskForm.channel" class="collector-input">
                <option value="zhaopin">zhaopin</option>
              </select>
            </label>

            <label class="field">
              <span class="field-label">优先级</span>
              <input v-model.number="taskForm.priority" class="collector-input" type="number" min="1" max="10" />
            </label>

            <label class="field field-span-2">
              <span class="field-label">关键词</span>
              <input v-model="taskForm.keywords" class="collector-input" placeholder="多个关键词用英文逗号分隔，例如 Python,Java" />
            </label>

            <label class="field">
              <span class="field-label">城市代码</span>
              <input v-model="taskForm.city" class="collector-input" placeholder="例如 801" />
            </label>

            <label class="field">
              <span class="field-label">每类页数</span>
              <input v-model.number="taskForm.pageCount" class="collector-input" type="number" min="1" max="5" />
            </label>
          </div>

          <div class="quick-tips">
            <div class="tip-card">
              <strong>推荐演示参数</strong>
              <span>关键词 `Python`，城市 `801`，页数 `1`。</span>
            </div>
            <div class="tip-card">
              <strong>当前行为</strong>
              <span>创建后自动拆分并派发，不需要再手动登录节点。</span>
            </div>
          </div>
        </div>
      </article>

      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><Network :size="16" /> 节点与当前任务</h2>
            <p class="panel-sub">右侧优先告诉你系统是否在线、任务是否真的在跑。</p>
          </div>
          <span class="panel-badge">{{ workerCards.length }} 个节点</span>
        </header>

        <div class="panel-body panel-body-spacious">
          <div class="worker-grid">
            <article v-for="worker in workerCards" :key="worker.worker_id" class="worker-card">
              <div class="worker-name">{{ worker.worker_id }}</div>
              <div class="worker-status">{{ worker.status }}</div>
              <div class="worker-meta">CPU {{ worker.cpu_usage }}%</div>
              <div class="worker-meta">内存 {{ worker.memory_usage }}%</div>
            </article>
          </div>

          <div v-if="selectedTask && selectedTaskStats" class="detail-grid">
            <article class="detail-card">
              <span class="detail-label">任务状态</span>
              <strong class="detail-value">{{ selectedTaskStatus.label }}</strong>
            </article>
            <article class="detail-card">
              <span class="detail-label">分片进度</span>
              <strong class="detail-value">{{ selectedTaskStats.completed_shards }} / {{ selectedTaskStats.total_shards }}</strong>
            </article>
            <article class="detail-card">
              <span class="detail-label">新增数据</span>
              <strong class="detail-value">{{ selectedTaskStats.new_count }}</strong>
            </article>
            <article class="detail-card">
              <span class="detail-label">更新数据</span>
              <strong class="detail-value">{{ selectedTaskStats.updated_count }}</strong>
            </article>
          </div>
        </div>
      </article>
    </section>

    <section class="tasks-grid">
      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><TimerReset :size="16" /> 最近任务</h2>
            <p class="panel-sub">默认优先显示当前任务。没有运行中任务时，就看最近完成或失败的历史任务。</p>
          </div>
        </header>

        <div class="panel-body">
          <div v-if="taskList.length" class="task-list">
            <article
              v-for="task in taskList"
              :key="task.task_id"
              class="task-row"
              :class="{ active: selectedTaskId === task.task_id }"
              @click="loadTaskDetail(task.task_id)"
            >
              <div class="task-head">
                <div class="task-main">
                  <h3 class="task-title">{{ task.task_name }}</h3>
                  <div class="task-subtitle">{{ task.task_id }}</div>
                </div>
                <span class="pill" :class="`pill-${normalizeStatus(task.status).tone}`">
                  {{ normalizeStatus(task.status).label }}
                </span>
              </div>

              <div class="task-progress">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
                </div>
                <span class="progress-count">{{ task.finished_count || 0 }} / {{ task.total_count || 0 }}</span>
              </div>

              <div class="task-meta">
                <span>关键词：{{ Array.isArray(task.keywords) ? task.keywords.join(', ') : task.keywords || '--' }}</span>
                <span>城市：{{ Array.isArray(task.city) ? task.city.join(', ') : task.city || '--' }}</span>
                <span>更新时间：{{ formatDateTime(task.end_time || task.start_time || task.created_at) }}</span>
              </div>

              <div class="task-actions">
                <button
                  v-if="Number(task.status) === 0"
                  class="mini-action"
                  :disabled="startingTaskId === task.task_id"
                  @click.stop="startTask(task.task_id)"
                >
                  <PlayCircle :size="14" />
                  {{ startingTaskId === task.task_id ? '启动中' : '手动启动' }}
                </button>
                <button class="mini-action" @click.stop="loadTaskDetail(task.task_id)">查看详情</button>
              </div>
            </article>
          </div>
          <div v-else class="empty-block">当前还没有任务，先在上方创建一个最小采集任务。</div>
        </div>
      </article>

      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><Server :size="16" /> 分片详情</h2>
            <p class="panel-sub">
              {{ selectedTask ? `当前查看 ${selectedTask.task_id}` : '先从左侧选择一条任务' }}
            </p>
          </div>
          <span v-if="selectedTask" class="panel-badge">{{ selectedTaskStatus.label }}</span>
        </header>

        <div class="panel-body">
          <div v-if="selectedTaskShards.length" class="shard-list">
            <article v-for="shard in selectedTaskShards" :key="shard.shard_id" class="shard-row">
              <div class="shard-head">
                <strong>{{ shard.worker_id || '未分配节点' }}</strong>
                <span class="pill" :class="`pill-${normalizeStatus(shard.status).tone}`">
                  {{ normalizeStatus(shard.status).label }}
                </span>
              </div>
              <div class="shard-meta">分片：{{ shard.shard_id }}</div>
              <div class="shard-meta">关键词：{{ shard.keyword || '--' }}，城市：{{ shard.city || '--' }}，分类：{{ shard.category_code || '--' }}</div>
              <div class="shard-meta">开始：{{ formatDateTime(shard.start_time) }}</div>
              <div class="shard-meta">结束：{{ formatDateTime(shard.end_time) }}</div>
              <div v-if="shard.stop_reason" class="shard-reason">{{ shard.stop_reason }}</div>
            </article>
          </div>
          <div v-else class="empty-block">选择任务后，这里会显示该任务拆分到各节点的执行结果。</div>
        </div>
      </article>
    </section>

    <section class="tasks-grid">
      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><AlertTriangle :size="16" /> 失败诊断</h2>
            <p class="panel-sub">这里直接展示失败分片，答辩时不用再解释“要去服务器上查日志”。</p>
          </div>
        </header>

        <div class="panel-body">
          <div v-if="failedShards.length" class="diagnosis-list">
            <article v-for="shard in failedShards" :key="`${shard.shard_id}-failed`" class="diagnosis-item">
              <div class="diagnosis-title">{{ shard.shard_id }}</div>
              <div class="diagnosis-note">
                节点 {{ shard.worker_id || '未分配' }}，关键词 {{ shard.keyword || '--' }}，城市 {{ shard.city || '--' }}
              </div>
              <div class="diagnosis-message">{{ shard.stop_reason || shard.message || '未记录失败原因' }}</div>
            </article>
          </div>
          <div v-else class="empty-block compact-empty">当前选中任务没有失败分片。</div>
        </div>
      </article>

      <article class="panel">
        <header class="panel-head">
          <div>
            <h2 class="panel-title"><Workflow :size="16" /> 系统工作流程</h2>
            <p class="panel-sub">这一块可以直接拿去讲解系统架构。</p>
          </div>
        </header>

        <div class="panel-body">
          <div class="flow-list">
            <div class="flow-step">
              <span class="flow-index">1</span>
              <div>
                <strong>前端创建任务</strong>
                <p>输入关键词、城市和页数，任务提交到调度中心。</p>
              </div>
            </div>
            <div class="flow-step">
              <span class="flow-index">2</span>
              <div>
                <strong>调度中心拆分任务</strong>
                <p>系统把一个任务拆成多个分片，并通过 RabbitMQ 派发。</p>
              </div>
            </div>
            <div class="flow-step">
              <span class="flow-index">3</span>
              <div>
                <strong>3 台虚拟机并行采集</strong>
                <p>hadoop001、hadoop002、hadoop003 各自消费分片，回传成功或失败结果。</p>
              </div>
            </div>
            <div class="flow-step">
              <span class="flow-index">4</span>
              <div>
                <strong>原始数据入库并供后端分析</strong>
                <p>采集结果写入原始表，后续由清洗和业务分析流程继续处理。</p>
              </div>
            </div>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 0.7fr);
  gap: 18px;
  padding: 28px;
  border-radius: 28px;
  border: 1px solid rgba(46, 83, 126, 0.15);
  background:
    radial-gradient(circle at top left, rgba(34, 113, 255, 0.14), transparent 34%),
    radial-gradient(circle at bottom right, rgba(29, 184, 126, 0.12), transparent 26%),
    linear-gradient(140deg, rgba(247, 250, 255, 0.98), rgba(236, 243, 251, 0.96));
  box-shadow: 0 24px 48px rgba(17, 44, 83, 0.08);
}

.hero-kicker {
  margin: 0 0 10px;
  color: #0c62be;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.05;
  letter-spacing: -0.03em;
}

.hero-description {
  margin: 14px 0 0;
  max-width: 760px;
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.75;
}

.hero-actions,
.hero-links,
.task-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-actions {
  margin-top: 18px;
}

.hero-links {
  align-content: flex-start;
  justify-content: flex-end;
}

.quick-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: fit-content;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid rgba(24, 27, 35, 0.1);
  background: rgba(255, 255, 255, 0.88);
  color: var(--c-text-secondary);
  text-decoration: none;
}

.quick-link:hover {
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.24);
}

.banner {
  padding: 12px 16px;
  border-radius: 14px;
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

.overview-grid,
.main-grid,
.tasks-grid,
.worker-grid,
.detail-grid,
.form-grid,
.quick-tips {
  display: grid;
  gap: 16px;
}

.overview-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.live-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 16px;
  border: 1px solid rgba(0, 87, 194, 0.12);
  background: rgba(235, 245, 255, 0.88);
}

.live-banner.quiet {
  border-color: rgba(30, 138, 91, 0.14);
  background: rgba(244, 251, 247, 0.92);
}

.live-banner-main,
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.live-banner-main {
  color: var(--c-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  background: #fff;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.filter-chip.active {
  color: #fff;
  background: rgba(0, 89, 199, 0.94);
  border-color: rgba(0, 89, 199, 0.3);
}

.main-grid,
.tasks-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.worker-grid,
.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quick-tips {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel,
.overview-card {
  background: #fff;
  border: 1px solid var(--c-border-glass);
  border-radius: 20px;
  box-shadow: 0 12px 24px rgba(24, 27, 35, 0.05);
}

.overview-card {
  padding: 18px 20px;
}

.overview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.overview-value {
  margin-top: 16px;
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
}

.overview-note {
  margin-top: 8px;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 17px;
}

.panel-sub {
  margin: 6px 0 0;
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.panel-badge,
.pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.panel-badge {
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
}

.panel-body {
  padding: 18px 22px 22px;
}

.panel-body-spacious {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.guide-list,
.task-list,
.shard-list,
.diagnosis-list,
.flow-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guide-item,
.tip-card,
.task-row,
.worker-card,
.detail-card,
.shard-row,
.diagnosis-item {
  border-radius: 14px;
  border: 1px solid rgba(24, 27, 35, 0.08);
  background: #fff;
}

.guide-item,
.tip-card {
  padding: 14px 16px;
  background: rgba(247, 249, 252, 0.84);
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.tip-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-row,
.worker-card,
.detail-card,
.shard-row,
.diagnosis-item {
  padding: 14px 16px;
}

.task-row {
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
}

.task-row:hover,
.task-row.active {
  border-color: rgba(0, 87, 194, 0.24);
  box-shadow: 0 8px 18px rgba(0, 87, 194, 0.07);
  background: rgba(0, 87, 194, 0.03);
}

.task-head,
.shard-head {
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
  font-size: 16px;
}

.task-subtitle,
.task-meta,
.worker-meta,
.shard-meta,
.diagnosis-note,
.diagnosis-message {
  color: var(--c-text-secondary);
  font-size: 12.5px;
  line-height: 1.65;
}

.task-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
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
  background: linear-gradient(90deg, #0d63be, #2ea0ff);
}

.progress-count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  background: #fff;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.mini-action:hover:not(:disabled) {
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.24);
}

.mini-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-span-2 {
  grid-column: span 2;
}

.field-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.collector-input {
  width: 100%;
  padding: 11px 12px;
  border-radius: 10px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  background: #fff;
  font-size: 13.5px;
}

.collector-input:focus,
.collector-input:focus-visible {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(0, 87, 194, 0.12);
}

.worker-name,
.detail-value {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
}

.worker-status,
.detail-label,
.diagnosis-title {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.diagnosis-message,
.shard-reason {
  margin-top: 6px;
  color: #9d3328;
  line-height: 1.65;
}

.flow-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  align-items: start;
}

.flow-step p {
  margin: 4px 0 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.flow-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: rgba(0, 87, 194, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
}

.empty-block {
  min-height: 132px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  border-radius: 14px;
  border: 1px dashed rgba(24, 27, 35, 0.14);
  color: var(--c-text-muted);
  background: rgba(247, 249, 252, 0.72);
}

.compact-empty {
  min-height: 96px;
}

.pill-idle {
  background: rgba(99, 115, 129, 0.1);
  color: #607080;
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

@media (max-width: 1180px) {
  .hero-shell,
  .overview-grid,
  .live-banner,
  .main-grid,
  .tasks-grid,
  .worker-grid,
  .detail-grid,
  .form-grid,
  .quick-tips {
    grid-template-columns: 1fr;
  }

  .field-span-2 {
    grid-column: auto;
  }
}

@media (max-width: 768px) {
  .hero-shell {
    padding: 22px 18px;
    border-radius: 22px;
  }

  .panel-head,
  .task-head,
  .shard-head,
  .live-banner {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
