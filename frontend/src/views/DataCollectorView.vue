<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  CheckCircle2,
  Clock3,
  DatabaseZap,
  FileText,
  History,
  LoaderCircle,
  PauseCircle,
  PlayCircle,
  Plus,
  Radar,
  ShieldCheck,
  SquareX,
  TerminalSquare
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import {
  backfillCrawlQualityHistory,
  createDataSource,
  createCrawlTask,
  deleteDataSource,
  fetchCrawlQuality,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  fetchDataSources,
  normalizeError,
  updateDataSource,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'

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
const sources = ref([])
const sourceSubmitting = ref(false)
const editingSourceId = ref(null)
const backfillLoading = ref(false)

const filters = ref({
  channel: '',
  status: ''
})

const taskForm = ref({
  taskName: '',
  channel: 'boss',
  keywords: '',
  city: '',
  priority: 5
})

const sourceForm = ref({
  sourceName: '',
  sourceCode: '',
  baseUrl: '',
  crawlStrategy: ''
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
        page: 1,
        pageSize: 20
      }),
      fetchCrawlQuality(authStore.token)
    ])

    tasks.value = taskResult.data
    totalTasks.value = taskResult.total
    quality.value = qualityResult
    sources.value = await fetchDataSources(authStore.token)

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

function resetSourceForm() {
  editingSourceId.value = null
  sourceForm.value = {
    sourceName: '',
    sourceCode: '',
    baseUrl: '',
    crawlStrategy: ''
  }
}

function handleEditSource(source) {
  editingSourceId.value = source.id
  sourceForm.value = {
    sourceName: source.sourceName || '',
    sourceCode: source.sourceCode || '',
    baseUrl: source.baseUrl || '',
    crawlStrategy: source.crawlStrategy || ''
  }
}

async function handleSubmitSource() {
  if (!authStore.token || sourceSubmitting.value) return
  sourceSubmitting.value = true
  error.value = ''
  try {
    if (editingSourceId.value) {
      await updateDataSource(authStore.token, editingSourceId.value, sourceForm.value)
    } else {
      await createDataSource(authStore.token, sourceForm.value)
    }
    resetSourceForm()
    sources.value = await fetchDataSources(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    sourceSubmitting.value = false
  }
}

async function handleDeleteSource(id) {
  if (!authStore.token) return
  error.value = ''
  try {
    await deleteDataSource(authStore.token, id)
    if (editingSourceId.value === id) resetSourceForm()
    sources.value = await fetchDataSources(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
  }
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

async function handleBackfillHistory() {
  if (!authStore.token || backfillLoading.value) return
  backfillLoading.value = true
  error.value = ''
  try {
    const result = await backfillCrawlQualityHistory(authStore.token, 100)
    await loadDashboard()
    const inserted = Number(result.inserted || 0)
    if (!Number.isNaN(inserted)) {
      error.value = inserted > 0 ? '' : '本次未新增历史快照，可能数据已经补齐。'
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    backfillLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero workspace-page-head">
      <div class="workspace-page-row">
        <div class="workspace-page-copy">
          <h1 class="workspace-page-title">数据采集</h1>
        </div>

        <div class="workspace-page-side">
          <div class="workspace-page-meta align-end">
            <div class="workspace-page-meta-item">
              <span>页面</span>
              <strong>管理员页</strong>
            </div>
            <div class="workspace-page-meta-item">
              <span>范围</span>
              <strong>任务 / 质量 / 日志</strong>
            </div>
            <div class="workspace-page-meta-item">
              <span>当前任务</span>
              <strong>{{ totalTasks || '--' }}</strong>
            </div>
          </div>

          <div class="workspace-page-note">
            <DatabaseZap :size="16" />
            <span>默认展示最近 20 条任务与对应日志。</span>
          </div>
        </div>
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

    <section class="collector-main">
      <div class="collector-col collector-col-left">
        <article class="collector-panel">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><Plus :size="15" /> 创建任务</h2>
              <p class="collector-panel-sub">填写关键信息后即可加入采集队列。</p>
            </div>
            <span class="collector-panel-badge">采集入口</span>
          </header>

          <div class="collector-panel-body">
            <div class="task-form">
              <label class="field">
                <span class="field-label">任务名称</span>
                <input
                  v-model="taskForm.taskName"
                  class="collector-input"
                  placeholder="例如：上海 Java 日采集"
                />
              </label>
              <div class="form-row">
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
              <label class="field">
                <span class="field-label">关键词</span>
                <input
                  v-model="taskForm.keywords"
                  class="collector-input"
                  placeholder="Java, 数据分析, Vue"
                />
              </label>
              <div class="form-row compact">
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
                <GlowButton variant="primary" :loading="submitting" @click="handleCreateTask">
                  <Plus :size="15" />
                  创建任务
                </GlowButton>
              </div>
            </div>
          </div>
        </article>

        <article class="collector-panel">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><Radar :size="15" /> 采集源管理</h2>
              <p class="collector-panel-sub">后端已有数据源 CRUD，这里补前端入口，便于直接维护渠道配置。</p>
            </div>
            <span class="collector-panel-badge">{{ sources.length }} 个源</span>
          </header>

          <div class="collector-panel-body">
            <div class="task-form">
              <div class="form-row">
                <label class="field">
                  <span class="field-label">数据源名称</span>
                  <input v-model="sourceForm.sourceName" class="collector-input" placeholder="例如：BOSS 直聘" />
                </label>
                <label class="field">
                  <span class="field-label">数据源编码</span>
                  <input v-model="sourceForm.sourceCode" class="collector-input" placeholder="例如：boss" />
                </label>
              </div>
              <label class="field">
                <span class="field-label">Base URL</span>
                <input v-model="sourceForm.baseUrl" class="collector-input" placeholder="https://..." />
              </label>
              <label class="field">
                <span class="field-label">抓取策略</span>
                <input v-model="sourceForm.crawlStrategy" class="collector-input" placeholder="search-api / browser / hybrid" />
              </label>
              <div class="form-row compact">
                <GlowButton variant="primary" :loading="sourceSubmitting" @click="handleSubmitSource">
                  <Plus :size="15" />
                  {{ editingSourceId ? '保存采集源' : '新增采集源' }}
                </GlowButton>
                <GlowButton v-if="editingSourceId" variant="ghost" @click="resetSourceForm">取消编辑</GlowButton>
              </div>
            </div>

            <div v-if="sources.length" class="source-list">
              <article v-for="source in sources" :key="source.id" class="source-row">
                <div class="task-head">
                  <div class="task-main">
                    <h3 class="task-title">{{ source.sourceName }}</h3>
                    <p class="task-subtitle">{{ source.sourceCode }} · {{ source.baseUrl || '未配置地址' }}</p>
                  </div>
                  <span class="pill" :class="Number(source.isActive) === 1 ? 'pill-done' : 'pill-paused'">
                    {{ Number(source.isActive) === 1 ? '启用中' : '已停用' }}
                  </span>
                </div>
                <div class="task-meta">
                  <span>健康度 {{ source.healthStatus || 'UNKNOWN' }}</span>
                  <span>累计 {{ source.totalRecords || 0 }} 条</span>
                  <span>最近抓取 {{ formatTime(source.lastCrawlAt) }}</span>
                </div>
                <div class="task-actions">
                  <button class="mini-action" @click="handleEditSource(source)">编辑</button>
                  <button class="mini-action danger" @click="handleDeleteSource(source.id)">删除</button>
                </div>
              </article>
            </div>
          </div>
        </article>

        <article class="collector-panel">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><FileText :size="15" /> 任务队列</h2>
              <p class="collector-panel-sub">点选任务行切换右侧日志流。</p>
            </div>
            <span class="collector-panel-badge">{{ totalTasks }} 个任务</span>
          </header>

          <div class="collector-panel-body">
            <div class="toolbar">
              <select v-model="filters.channel" class="collector-input slim" @change="loadDashboard">
                <option value="">全部渠道</option>
                <option value="boss">BOSS 直聘</option>
                <option value="zhaopin">智联招聘</option>
                <option value="51job">前程无忧</option>
                <option value="liepin">猎聘</option>
              </select>
              <select v-model="filters.status" class="collector-input slim" @change="loadDashboard">
                <option value="">全部状态</option>
                <option value="0">待启动</option>
                <option value="1">运行中</option>
                <option value="2">已暂停</option>
                <option value="3">已结束</option>
              </select>
              <GlowButton variant="ghost" @click="loadDashboard">刷新</GlowButton>
            </div>

            <div v-if="loading" class="empty-block">正在同步任务状态...</div>
            <div v-else-if="tasks.length === 0" class="empty-block">当前没有采集任务。</div>
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
            <div class="foot-note">共 {{ totalTasks }} 个任务，默认展示最近 20 条。</div>
          </div>
        </article>
      </div>

      <div class="collector-col collector-col-right">
        <article class="collector-panel">
          <header class="collector-panel-head">
            <div class="collector-panel-copy">
              <h2 class="collector-panel-title"><ShieldCheck :size="15" /> 质量健康概览</h2>
              <p class="collector-panel-sub">字段完整率 / 新鲜度 / 异常态分布。</p>
            </div>
            <div class="panel-head-actions">
              <button class="mini-action" :disabled="backfillLoading" @click="handleBackfillHistory">
                <History :size="13" />
                {{ backfillLoading ? '回填中' : '回填历史快照' }}
              </button>
              <span class="collector-panel-badge">岗位库质量</span>
            </div>
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
                {{ activeTaskId ? `task: ${activeTaskId}` : '尚未选择任务' }}
              </p>
            </div>
            <span class="collector-panel-badge">最近 20 条</span>
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
                  <span class="log-time">{{ formatTime(item.createTime) }}</span>
                </div>
                <div class="log-message">{{ item.message }}</div>
              </article>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ----------------------------------------------------------
 * Page shell
 * -------------------------------------------------------- */
.collector-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.collector-hero {
  gap: 0;
  padding: 0;
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
  background: #ffffff;
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.05);
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
.collector-main {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(360px, 0.95fr);
  gap: 24px;
}

.collector-col {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 24px;
}

/* ----------------------------------------------------------
 * Panel — white card base (aligned with Console / OpenApi)
 * -------------------------------------------------------- */
.collector-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #ffffff;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(24, 27, 35, 0.05);
  overflow: hidden;
}

.collector-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.panel-head-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
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
  background: rgba(0, 87, 194, 0.08);
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
}

.form-row.compact {
  align-items: flex-end;
  gap: 12px;
}

.form-row.compact .field-priority {
  max-width: 180px;
}

.form-row.compact :deep(.glow-button),
.form-row.compact :deep(button) {
  align-self: flex-end;
}

.collector-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  border-radius: 10px;
  background: #ffffff;
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
  border-color: rgba(24, 27, 35, 0.18);
}

.collector-input:focus,
.collector-input:focus-visible {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(0, 87, 194, 0.12);
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
  border: 1px dashed rgba(24, 27, 35, 0.12);
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

.source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: #ffffff;
}

.task-row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: #ffffff;
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
  border-color: rgba(0, 87, 194, 0.28);
  background: rgba(0, 87, 194, 0.04);
}

.task-row.active {
  border-color: rgba(0, 87, 194, 0.35);
  background: rgba(0, 87, 194, 0.05);
  box-shadow: 0 4px 14px rgba(0, 87, 194, 0.1);
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
  background: rgba(24, 27, 35, 0.06);
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
  border: 1px solid rgba(24, 27, 35, 0.1);
  border-radius: 8px;
  background: #ffffff;
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
  background: rgba(0, 87, 194, 0.04);
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

.foot-note {
  padding-top: 2px;
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
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
  background: rgba(0, 87, 194, 0.1);
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
  border: 1px solid rgba(24, 27, 35, 0.06);
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
  background: rgba(24, 27, 35, 0.06);
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
  border: 1px solid rgba(24, 27, 35, 0.06);
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
  background: rgba(24, 27, 35, 0.14);
  border-radius: 999px;
}

.log-stream::-webkit-scrollbar-thumb:hover {
  background: rgba(24, 27, 35, 0.26);
}

.log-line {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid rgba(24, 27, 35, 0.06);
  border-radius: 10px;
  background: #ffffff;
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
  background: rgba(0, 87, 194, 0.1);
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
  .collector-main {
    grid-template-columns: minmax(0, 1.4fr) minmax(320px, 1fr);
    gap: 20px;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .collector-col {
    gap: 20px;
  }
}

@media (max-width: 1023px) {
  .collector-main {
    grid-template-columns: 1fr;
  }

  .collector-col {
    gap: 20px;
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

  .panel-head-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .collector-panel-body {
    padding: 14px 16px 16px;
  }

  .form-row,
  .toolbar,
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

  .form-row.compact {
    gap: 10px;
  }

  .form-row.compact .field-priority {
    max-width: none;
  }
}
</style>
