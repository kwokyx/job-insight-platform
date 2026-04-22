<script setup>
import { computed, onMounted, ref } from 'vue'
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
  fetchCrawlQuality,
  fetchCrawlTask,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  normalizeError,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'
import { FAILURE_OPTIONS, resolveFailureLabel } from '../constants/crawlFailure'

const authStore = useAuthStore()

const loading = ref(false)
const submitting = ref(false)
const statusUpdating = ref('')
const error = ref('')

const tasks = ref([])
const totalTasks = ref(0)
const quality = ref({})
const logs = ref([])
const activeTaskId = ref('')
const logsLoading = ref(false)
// 失败原因筛选器（前端侧过滤；后端补 failureCode 字段后直接生效）
const logFailureFilter = ref('')
const failureOptions = FAILURE_OPTIONS
const filteredLogs = computed(() => {
  if (!logFailureFilter.value) return logs.value
  return logs.value.filter((item) => item.failureCode === logFailureFilter.value)
})

// 任务详情抽屉
const detailTask = ref(null)
const detailLoading = ref(false)
const detailTaskId = ref('')

const filters = ref({
  channel: '',
  status: ''
})

// 任务列表分页 —— 后端 /crawl/tasks 支持 page/pageSize 但前端一直写死只拿 20 条，
// 现在补上完整的翻页控件。默认每页 10 条（和 metrics/logs 面板共用可视区域更合适）。
const taskPage = ref(1)
const taskPageSize = ref(10)
const taskTotalPages = computed(() =>
  Math.max(1, Math.ceil((totalTasks.value || 0) / taskPageSize.value))
)

// "创建任务"默认折叠，点击"新建任务"按钮展开，避免永久占视觉空间。
const createFormOpen = ref(false)

const taskForm = ref({
  taskName: '',
  channel: 'boss',
  keywords: '',
  city: '',
  priority: 5
})

const qualityCards = computed(() => {
  const q = quality.value || {}
  const completeness = q.completeness || {}
  return [
    {
      label: '总岗位量',
      value: q.totalJobs ?? '--',
      note: '当前岗位库规模'
    },
    {
      label: '标题完整率',
      value: completeness.titleRate || '--',
      note: '职位标题字段有效占比'
    },
    {
      label: '薪资完整率',
      value: completeness.salaryRate || '--',
      note: '薪资区间有效占比'
    },
    {
      label: '僵尸岗位',
      value: q.suspectedZombieJobRate || '--',
      note: `疑似过期 ${q.suspectedZombieJobs ?? 0} 条`
    }
  ]
})

const freshnessRows = computed(() => quality.value?.freshness || [])

function getStatusMeta(status) {
  const map = {
    0: { label: '待启动', tone: 'idle', icon: Clock3 },
    1: { label: '运行中', tone: 'running', icon: LoaderCircle },
    2: { label: '已暂停', tone: 'paused', icon: PauseCircle },
    3: { label: '已结束', tone: 'done', icon: CheckCircle2 }
  }
  return map[status] || { label: '未知', tone: 'idle', icon: Activity }
}

function formatTime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatRate(value) {
  if (value === null || value === undefined || value === '') return '--'
  const num = Number(value)
  if (Number.isNaN(num)) return '--'
  // 容忍两种入参：0-1 的小数 或 0-100 的百分比
  const pct = num <= 1 ? num * 100 : num
  return `${pct.toFixed(1)}%`
}

function progressPercent(task) {
  const total = Number(task.totalCount || 0)
  const finished = Number(task.finishedCount || 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((finished / total) * 100)))
}

async function loadDashboard() {
  if (!authStore.token) return

  loading.value = true
  error.value = ''

  try {
    const [taskResult, qualityResult] = await Promise.all([
      fetchCrawlTasks(authStore.token, {
        channel: filters.value.channel,
        status: filters.value.status,
        page: taskPage.value,
        pageSize: taskPageSize.value
      }),
      fetchCrawlQuality(authStore.token)
    ])

    tasks.value = taskResult.data
    totalTasks.value = taskResult.total
    quality.value = qualityResult

    // 翻到一个没有数据的页（比如删任务后），自动回到上一页
    if (!taskResult.data.length && taskPage.value > 1) {
      taskPage.value = Math.max(1, taskPage.value - 1)
      await loadDashboard()
      return
    }

    if (activeTaskId.value) {
      await loadLogs(activeTaskId.value)
    } else if (taskResult.data.length) {
      await loadLogs(taskResult.data[0].taskId)
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

// 筛选变更：重置回第一页再拉
function applyFilters() {
  taskPage.value = 1
  loadDashboard()
}

function goToPage(n) {
  const next = Math.max(1, Math.min(taskTotalPages.value, n))
  if (next === taskPage.value) return
  taskPage.value = next
  loadDashboard()
}

async function loadLogs(taskId) {
  if (!taskId || !authStore.token) return

  activeTaskId.value = taskId
  logsLoading.value = true
  try {
    const result = await fetchCrawlTaskLogs(authStore.token, taskId, {
      page: 1,
      pageSize: 20
    })
    logs.value = result.data
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    logsLoading.value = false
  }
}

async function handleCreateTask() {
  if (!authStore.token || submitting.value) return

  submitting.value = true
  error.value = ''
  try {
    await createCrawlTask(authStore.token, {
      taskName: taskForm.value.taskName,
      channel: taskForm.value.channel,
      keywords: taskForm.value.keywords,
      city: taskForm.value.city,
      priority: Number(taskForm.value.priority) || 5
    })

    taskForm.value = {
      taskName: '',
      channel: taskForm.value.channel,
      keywords: '',
      city: '',
      priority: 5
    }
    createFormOpen.value = false
    // 新建任务回到第 1 页，用户能立即看到自己刚创建的
    taskPage.value = 1
    await loadDashboard()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    submitting.value = false
  }
}

async function handleTaskStatus(task, status) {
  if (!authStore.token) return

  statusUpdating.value = `${task.taskId}:${status}`
  error.value = ''
  try {
    await updateCrawlTaskStatus(authStore.token, task.taskId, { status })
    await loadDashboard()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    statusUpdating.value = ''
  }
}

async function openTaskDetail(task) {
  const id = task?.taskId || task?.id
  if (!id) return
  detailTaskId.value = String(id)
  detailLoading.value = true
  detailTask.value = null
  error.value = ''
  try {
    detailTask.value = await fetchCrawlTask(authStore.token, id)
    // 同步激活右侧日志流，方便联看
    await loadLogs(id)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    detailLoading.value = false
  }
}

function closeTaskDetail() {
  detailTask.value = null
  detailTaskId.value = ''
}

const detailMetaRows = computed(() => {
  const t = detailTask.value
  if (!t) return []
  const rows = [
    { label: '任务 ID', value: t.taskId || '--' },
    { label: '父任务', value: t.parentTaskId || '—' },
    { label: '渠道', value: t.channel || '--' },
    { label: '城市', value: t.city || '全域' },
    { label: '关键词', value: t.keywords || '—' },
    { label: '优先级', value: `P${t.priority ?? 5}` },
    { label: '状态', value: getStatusMeta(t.status).label },
    { label: '总数', value: t.totalCount ?? 0 },
    { label: '已完成', value: t.finishedCount ?? 0 },
    { label: '去重', value: t.duplicateCount ?? 0 },
    { label: '创建人', value: t.createUser || '--' },
    { label: '创建时间', value: formatTime(t.createTime) },
    { label: '开始时间', value: formatTime(t.startTime) },
    { label: '结束时间', value: formatTime(t.endTime) },
    { label: '更新时间', value: formatTime(t.updateTime) }
  ]
  return rows
})

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero">
      <div>
        <h1 class="collector-title">数据采集</h1>
        <p class="collector-subtitle">任务调度 · 数据质量 · 运行日志</p>
      </div>
      <div class="collector-hero-actions">
        <GlowButton variant="ghost" @click="loadDashboard">
          <RefreshCw :size="14" /> 刷新
        </GlowButton>
        <GlowButton variant="primary" @click="createFormOpen = !createFormOpen">
          <Plus :size="14" /> {{ createFormOpen ? '收起' : '新建任务' }}
        </GlowButton>
      </div>
    </section>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <section class="metrics-grid">
      <article
        v-for="item in qualityCards"
        :key="item.label"
        class="collector-metric-card"
      >
        <div class="collector-metric-head">
          <span class="collector-metric-label">{{ item.label }}</span>
          <span class="collector-metric-dot" aria-hidden="true"></span>
        </div>
        <div class="collector-metric-value">{{ item.value }}</div>
        <div class="collector-metric-note">{{ item.note }}</div>
      </article>
    </section>

    <!-- —— 创建任务 —— 点击 hero 的"新建任务"按钮展开/收起 —— -->
    <transition name="collapse">
      <article v-if="createFormOpen" class="collector-panel create-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><Plus :size="15" /> 创建任务</h2>
            <p class="collector-panel-sub">填写关键信息后即可加入采集队列。</p>
          </div>
          <button
            class="icon-close"
            type="button"
            aria-label="收起"
            @click="createFormOpen = false"
          >
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
                  placeholder="例如：上海 Java 日采集"
                />
              </label>
              <label class="field">
                <span class="field-label">渠道</span>
                <select v-model="taskForm.channel" class="collector-input">
                  <option value="boss">BOSS 直聘</option>
                  <option value="zhaopin">智联招聘</option>
                  <option value="51job">前程无忧</option>
                  <option value="liepin">猎聘</option>
                </select>
              </label>
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="taskForm.city" class="collector-input" placeholder="如 上海" />
              </label>
            </div>
            <div class="form-row">
              <label class="field field-grow">
                <span class="field-label">关键词</span>
                <input
                  v-model="taskForm.keywords"
                  class="collector-input"
                  placeholder="Java, 数据分析, Vue"
                />
              </label>
              <label class="field field-priority">
                <span class="field-label">优先级 (1-10)</span>
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

    <!-- —— 任务队列（全宽，支持分页）—— -->
    <article class="collector-panel">
      <header class="collector-panel-head">
        <div class="collector-panel-copy">
          <h2 class="collector-panel-title"><FileText :size="15" /> 任务队列</h2>
          <p class="collector-panel-sub">
            第 {{ taskPage }} / {{ taskTotalPages }} 页 · 共 {{ totalTasks }} 个任务
          </p>
        </div>
        <div class="collector-panel-tools">
          <select v-model="filters.channel" class="collector-input slim" @change="applyFilters">
            <option value="">全部渠道</option>
            <option value="boss">BOSS 直聘</option>
            <option value="zhaopin">智联招聘</option>
            <option value="51job">前程无忧</option>
            <option value="liepin">猎聘</option>
          </select>
          <select v-model="filters.status" class="collector-input slim" @change="applyFilters">
            <option value="">全部状态</option>
            <option value="0">待启动</option>
            <option value="1">运行中</option>
            <option value="2">已暂停</option>
            <option value="3">已结束</option>
          </select>
          <select
            v-model.number="taskPageSize"
            class="collector-input slim"
            @change="applyFilters"
            aria-label="每页条数"
          >
            <option :value="10">10 / 页</option>
            <option :value="20">20 / 页</option>
            <option :value="50">50 / 页</option>
          </select>
        </div>
      </header>

      <div class="collector-panel-body">
        <div v-if="loading" class="empty-block">正在同步任务状态...</div>
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
                  {{ task.channel }} · {{ task.city || '全域' }} · {{ task.keywords || '无关键词' }}
                </p>
              </div>
              <span class="pill" :class="`pill-${getStatusMeta(task.status).tone}`">
                <component :is="getStatusMeta(task.status).icon" :size="12" />
                {{ getStatusMeta(task.status).label }}
              </span>
            </div>

            <div class="task-progress">
              <div class="progress-track">
                <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
              </div>
              <span class="progress-count">{{ task.finishedCount || 0 }} / {{ task.totalCount || 0 }}</span>
            </div>

            <div class="task-meta">
              <span>优先级 P{{ task.priority || 5 }}</span>
              <span>去重 {{ task.duplicateCount || 0 }}</span>
              <span>创建于 {{ formatTime(task.createTime) }}</span>
            </div>

            <div class="task-actions">
              <button class="mini-action" @click.stop="openTaskDetail(task)">
                <Info :size="13" /> 详情
              </button>
              <button
                class="mini-action"
                :disabled="statusUpdating === `${task.taskId}:1`"
                @click.stop="handleTaskStatus(task, 1)"
              >
                <PlayCircle :size="13" /> 启动
              </button>
              <button
                class="mini-action"
                :disabled="statusUpdating === `${task.taskId}:2`"
                @click.stop="handleTaskStatus(task, 2)"
              >
                <PauseCircle :size="13" /> 暂停
              </button>
              <button
                class="mini-action danger"
                :disabled="statusUpdating === `${task.taskId}:3`"
                @click.stop="handleTaskStatus(task, 3)"
              >
                <SquareX :size="13" /> 结束
              </button>
            </div>
          </article>
        </div>

        <!-- —— 分页条 —— -->
        <nav v-if="taskTotalPages > 1" class="task-pager" aria-label="任务分页">
          <button
            type="button"
            class="pager-btn"
            :disabled="taskPage <= 1"
            @click="goToPage(taskPage - 1)"
          >
            <ChevronLeft :size="14" /> 上一页
          </button>
          <span class="pager-info">
            第 <strong>{{ taskPage }}</strong> / {{ taskTotalPages }} 页
          </span>
          <button
            type="button"
            class="pager-btn"
            :disabled="taskPage >= taskTotalPages"
            @click="goToPage(taskPage + 1)"
          >
            下一页 <ChevronRight :size="14" />
          </button>
        </nav>
      </div>
    </article>

    <!-- —— 底部两列：左侧数据质量、右侧任务日志 —— -->
    <section class="collector-bottom">
      <article class="collector-panel">
        <header class="collector-panel-head">
          <div class="collector-panel-copy">
            <h2 class="collector-panel-title"><ShieldCheck :size="15" /> 数据质量</h2>
            <p class="collector-panel-sub">字段完整率 / 新鲜度 / 异常分布。</p>
          </div>
          <span class="collector-panel-badge">岗位库质量</span>
        </header>

        <div class="collector-panel-body quality-body">
          <section class="quality-section">
            <h3 class="quality-label">字段完整率</h3>
            <div class="quality-list">
              <div class="quality-row">
                <span>公司名</span>
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

          <section class="quality-section">
            <h3 class="quality-label">分布式采集成功率</h3>
            <div class="rate-grid">
              <div class="rate-card">
                <span class="rate-label">分片成功率</span>
                <strong class="rate-value">{{ formatRate(quality.shardSuccessRate) }}</strong>
                <span class="rate-hint">后端待提供</span>
              </div>
              <div class="rate-card">
                <span class="rate-label">重试后成功率</span>
                <strong class="rate-value">{{ formatRate(quality.retrySuccessRate) }}</strong>
                <span class="rate-hint">后端待提供</span>
              </div>
              <div class="rate-card">
                <span class="rate-label">端到端成功率</span>
                <strong class="rate-value">{{ formatRate(quality.endToEndSuccessRate) }}</strong>
                <span class="rate-hint">后端待提供</span>
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
              {{ activeTaskId ? `task: ${activeTaskId}` : '点选上方任务查看日志' }}
            </p>
          </div>
          <div class="log-filter">
            <select v-model="logFailureFilter" class="log-filter-select" title="按失败原因筛选">
              <option value="">全部日志</option>
              <option v-for="opt in failureOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
        </header>

        <div class="collector-panel-body log-body">
          <div v-if="logsLoading" class="empty-block">正在拉取日志...</div>
          <div v-else-if="filteredLogs.length === 0" class="empty-block">
            {{ logs.length === 0 ? '当前任务暂无日志输出。' : '当前筛选条件下没有匹配日志。' }}
          </div>
          <div v-else class="log-stream">
            <article v-for="item in filteredLogs" :key="item.logId" class="log-line">
              <div class="log-meta-line">
                <span class="log-level" :class="(item.level || 'INFO').toLowerCase()">
                  {{ item.level || 'INFO' }}
                </span>
                <span class="log-worker">{{ item.workerId || 'worker-unknown' }}</span>
                <span v-if="item.shardId" class="log-chip">shard: {{ item.shardId }}</span>
                <span v-if="item.requestId" class="log-chip" :title="item.requestId">req: {{ String(item.requestId).slice(0, 8) }}</span>
                <span v-if="item.retryCount" class="log-chip warn">重试 {{ item.retryCount }}</span>
                <span v-if="item.failureCode" class="log-chip danger">{{ resolveFailureLabel(item.failureCode) }}</span>
                <span class="log-time">{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="log-message">{{ item.message }}</div>
            </article>
          </div>
        </div>
      </article>
    </section>

    <!-- 任务详情抽屉：点击列表「详情」按钮拉起，展示 fetchCrawlTask 返回的全部字段 -->
    <transition name="drawer-fade">
      <div
        v-if="detailTaskId"
        class="task-detail-mask"
        role="dialog"
        aria-modal="true"
        @click.self="closeTaskDetail"
      >
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
/* ----------------------------------------------------------
 * Page shell
 * -------------------------------------------------------- */
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 紧凑 hero：左标题右 action，不再塞 3 个冗余 meta 卡 */
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

.error-banner {
  padding: 12px 16px;
  border: 1px solid rgba(178, 59, 46, 0.22);
  border-radius: 12px;
  background: rgba(254, 242, 240, 0.92);
  color: #b23b2e;
  font-family: var(--font-sans);
  font-size: 13px;
}

/* Dark mode override — the banner's warm pink background glows on a
   dark panel; swap for a dark-ruby translucent fill while keeping the
   semantic red accent. */
:global([data-theme="dark"]) .error-banner {
  background: rgba(178, 59, 46, 0.18);
  color: #ffb4a6;
}

/* ----------------------------------------------------------
 * Quality metric cards (top strip of 4)
 * -------------------------------------------------------- */
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
  min-height: 16px;
}

.collector-metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
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
  min-height: 1.5em;
}

/* ----------------------------------------------------------
 * Main grid: left = create + queue / right = quality + logs
 * -------------------------------------------------------- */
/* 底部两列：左数据质量、右实时日志 */
.collector-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 20px;
  align-items: stretch;
}

/* 创建任务面板的折叠动效 */
.collapse-enter-active,
.collapse-leave-active {
  transition: opacity 220ms var(--ease-out, ease), transform 220ms var(--ease-out, ease);
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.create-panel {
  border-left: 3px solid var(--c-accent-primary);
}

/* 面板头部右侧工具区（筛选器 + 每页条数） */
.collector-panel-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

/* —— 分页条 —— */
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
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 140ms ease, border-color 140ms ease, color 140ms ease;
}

.pager-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.35);
  color: var(--c-accent-primary);
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
  font-weight: 700;
}

/* ----------------------------------------------------------
 * Panel — white card base (aligned with Console / OpenApi)
 * -------------------------------------------------------- */
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
  flex-shrink: 0;
}

.collector-panel-body {
  padding: 18px 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ----------------------------------------------------------
 * Form
 * -------------------------------------------------------- */
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
  line-height: 1.2;
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
  transition:
    border-color var(--duration-fast) var(--ease-out),
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

/* ----------------------------------------------------------
 * Toolbar (filters row for task queue)
 * -------------------------------------------------------- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* ----------------------------------------------------------
 * Empty / loading state
 * -------------------------------------------------------- */
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
}

/* ----------------------------------------------------------
 * Task queue rows
 * -------------------------------------------------------- */
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
  transition:
    border-color var(--duration-fast) var(--ease-out),
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

.task-row:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
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
  transition:
    border-color var(--duration-fast) var(--ease-out),
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

/* ----------------------------------------------------------
 * Status pill — aligned with ConsoleView pill-success/danger
 * -------------------------------------------------------- */
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

/* ----------------------------------------------------------
 * Quality panel
 * -------------------------------------------------------- */
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
  letter-spacing: -0.01em;
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
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

.quality-row strong {
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
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
  font-family: var(--font-sans);
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
  transition: width var(--duration-normal) var(--ease-out);
}

.freshness-count {
  font-family: var(--font-mono);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
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
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.quality-stat strong {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--c-text-primary);
  font-variant-numeric: tabular-nums;
}

/* 分布式成功率指标（骨架，等后端返回字段后自动填充） */
.rate-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.rate-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rate-label { font-size: 12px; color: var(--c-text-muted); }
.rate-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-primary);
}
.rate-hint { font-size: 11px; color: var(--c-text-muted); opacity: 0.7; }

/* 日志面板新增：失败原因筛选器 + 日志条目上的 trace chips */
.log-filter { display: flex; align-items: center; }
.log-filter-select {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: var(--c-text-primary);
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}
.log-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--c-text-muted);
  font-family: var(--font-mono, monospace);
}
.log-chip.warn { background: rgba(240, 168, 48, 0.15); color: #f0a830; }
.log-chip.danger { background: rgba(239, 68, 68, 0.15); color: #ef4444; }

/* ----------------------------------------------------------
 * Log stream — fixed height + inner scroll
 * -------------------------------------------------------- */
.log-body {
  padding-bottom: 16px;
}

.log-stream {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px 6px 4px 14px;
  border-left: 2px solid var(--c-border-glass);
  mask-image: linear-gradient(
    180deg,
    transparent 0,
    #000 20px,
    #000 calc(100% - 8px),
    transparent 100%
  );
  -webkit-mask-image: linear-gradient(
    180deg,
    transparent 0,
    #000 20px,
    #000 calc(100% - 8px),
    transparent 100%
  );
}

.log-stream::-webkit-scrollbar {
  width: 6px;
}

.log-stream::-webkit-scrollbar-track {
  background: transparent;
}

.log-stream::-webkit-scrollbar-thumb {
  background: var(--c-border-glass);
  border-radius: 999px;
}

.log-stream::-webkit-scrollbar-thumb:hover {
  background: var(--c-text-faint);
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
  font-family: var(--font-sans);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
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

.log-worker {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.log-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-muted);
  margin-left: auto;
}

.log-message {
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.55;
  color: var(--c-text-primary);
  word-break: break-word;
}

/* ----------------------------------------------------------
 * Animations
 * -------------------------------------------------------- */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ----------------------------------------------------------
 * Responsive
 * -------------------------------------------------------- */
@media (max-width: 1279px) {
  .collector-bottom {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .collector-panel-head {
    padding: 16px 16px 12px;
    flex-direction: column;
    align-items: flex-start;
  }

  .collector-panel-body {
    padding: 14px 16px 16px;
  }

  .form-row,
  .collector-panel-tools,
  .quality-foot,
  .task-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .quality-list {
    grid-template-columns: 1fr;
  }

  .quality-foot {
    display: grid;
    grid-template-columns: 1fr;
  }

  .collector-input.slim {
    max-width: none;
  }

  .task-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .form-row .field-priority {
    max-width: none;
    flex: 1 1 auto;
  }
}

/* ----------------------------------------------------------
 * Task detail drawer
 * -------------------------------------------------------- */
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
  transition: background-color var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out);
}
.icon-close:hover { background: var(--c-bg-surface-hover); color: var(--c-text-primary); }

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
  font-family: var(--font-sans);
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
  min-width: 0;
}

.detail-row dt {
  font-size: 11px;
  color: var(--c-text-muted);
  letter-spacing: 0.04em;
}

.detail-row dd {
  margin: 0;
  font-family: var(--font-sans);
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

@media (max-width: 640px) {
  .task-detail-drawer { width: 100%; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-timeline li { grid-template-columns: 1fr; }
}
</style>
