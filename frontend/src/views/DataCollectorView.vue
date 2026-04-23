<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Activity,
  CheckCircle2,
  Clock3,
  PauseCircle,
  PlayCircle,
  Plus,
  RefreshCw,
  Server
} from 'lucide-vue-next'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createCrawlTask,
  fetchCrawlQuality,
  fetchPageSnapshotStatus,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  normalizeError,
  refreshPageSnapshots,
  updateCrawlTaskStatus
} from '../api'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const creating = ref(false)
const updatingTaskId = ref('')
const errorMsg = ref('')
const successMsg = ref('')

const tasks = ref([])
const totalTasks = ref(0)
const quality = ref({})
const logs = ref([])
const logsLoading = ref(false)
const activeTaskId = ref('')
const snapshotLoading = ref(false)
const snapshotStatus = ref({ pages: [], nextScheduledAt: '' })
const snapshotTarget = ref('ALL')
const actionToast = ref('')
const pollTimer = ref(null)

const filters = ref({
  channel: '',
  status: ''
})

const taskForm = ref({
  taskName: '',
  channel: 'boss',
  keywords: 'Python',
  city: '成都',
  priority: 5
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待执行', value: '0' },
  { label: '运行中', value: '1' },
  { label: '已完成', value: '2' },
  { label: '已失败', value: '3' }
]

const channelOptions = [
  { label: '全部渠道', value: '' },
  { label: 'BOSS', value: 'boss' },
  { label: '智联', value: 'zhaopin' },
  { label: '前程无忧', value: '51job' },
  { label: '拉勾', value: 'lagou' }
]

const snapshotTargetOptions = [
  { label: '全部页面', value: 'ALL', pageCodes: [] },
  { label: '首页', value: 'HOME', pageCodes: ['MARKET_OVERVIEW', 'MARKET_SKILLS', 'HOME_HOT_JOBS'] },
  {
    label: '数据分析',
    value: 'INSIGHTS',
    pageCodes: ['MARKET_OVERVIEW', 'INSIGHTS_SALARY_TREND', 'INSIGHTS_WELFARE', 'INSIGHTS_COMPANY_SIZE', 'INSIGHTS_FINANCE_STAGE']
  },
  { label: '管理员运营面板', value: 'ADMIN', pageCodes: ['ADMIN_OPERATIONS'] }
]

function getStatusMeta(status) {
  const map = {
    0: { label: '待执行', tone: 'idle' },
    1: { label: '运行中', tone: 'running' },
    2: { label: '已完成', tone: 'done' },
    3: { label: '已失败', tone: 'danger' }
  }
  return map[Number(status)] || { label: '未知', tone: 'idle' }
}

function buildRerunTaskName(baseName) {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  return `${baseName || '采集任务'}-重跑-${y}${m}${d}${hh}${mm}${ss}`
}

function formatTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { hour12: false })
}

function progressPercent(task) {
  const total = Number(task?.totalCount || 0)
  const finished = Number(task?.finishedCount || 0)
  if (!total) return 0
  return Math.max(0, Math.min(100, Math.round((finished / total) * 100)))
}

function normalizePercent(value) {
  const n = Number(value || 0)
  if (!Number.isFinite(n)) return '0%'
  if (n > 1) return `${n.toFixed(1)}%`
  return `${(n * 100).toFixed(1)}%`
}

function openCollectorReport() {
  router.push({
    path: '/reports',
    query: { reportType: 'OPERATIONS' }
  })
}

function openCollectorAi() {
  router.push({
    path: '/ai',
    query: { draft: '请基于数据采集任务与质量指标输出平台数据运营分析。' }
  })
}

const qualityCards = computed(() => {
  const q = quality.value || {}
  const completeness = q.completeness || {}
  return [
    { label: '岗位总量', value: q.totalJobs ?? '--', note: '当前岗位库规模' },
    { label: '标题完整率', value: normalizePercent(completeness.titleRate), note: '标题字段可用比例' },
    { label: '薪资完整率', value: normalizePercent(completeness.salaryRate), note: '薪资字段可用比例' },
    { label: '疑似僵尸岗位', value: q.suspectedZombieJobs ?? 0, note: '需后续清洗过滤' }
  ]
})

const selectedTask = computed(() => {
  return tasks.value.find((t) => String(t.taskId) === String(activeTaskId.value)) || null
})

const taskLogs = computed(() => logs.value || [])
const snapshotPages = computed(() => snapshotStatus.value?.pages || [])
const nextSnapshotTime = computed(() => formatTime(snapshotStatus.value?.nextScheduledAt))

async function loadSnapshotStatus() {
  if (!authStore.token) return
  try {
    snapshotStatus.value = await fetchPageSnapshotStatus(authStore.token)
  } catch (err) {
    errorMsg.value = normalizeError(err)
  }
}

async function loadLogs(taskId) {
  if (!taskId || !authStore.token) return
  activeTaskId.value = String(taskId)
  logsLoading.value = true
  try {
    const result = await fetchCrawlTaskLogs(authStore.token, taskId, { page: 1, pageSize: 30 })
    logs.value = Array.isArray(result?.data) ? result.data : []
  } catch (err) {
    errorMsg.value = normalizeError(err)
  } finally {
    logsLoading.value = false
  }
}

async function loadDashboard() {
  if (!authStore.token) return
  loading.value = true
  errorMsg.value = ''

  try {
    const [taskResult, qualityResult, nextSnapshotStatus] = await Promise.all([
      fetchCrawlTasks(authStore.token, {
        channel: filters.value.channel || undefined,
        status: filters.value.status === '' ? undefined : Number(filters.value.status),
        page: 1,
        pageSize: 30
      }),
      fetchCrawlQuality(authStore.token),
      fetchPageSnapshotStatus(authStore.token)
    ])

    tasks.value = Array.isArray(taskResult?.data) ? taskResult.data : []
    totalTasks.value = Number(taskResult?.total || tasks.value.length)
    quality.value = qualityResult || {}
    snapshotStatus.value = nextSnapshotStatus || { pages: [], nextScheduledAt: '' }

    if (tasks.value.length) {
      const exists = tasks.value.some((t) => String(t.taskId) === String(activeTaskId.value))
      const targetId = exists ? activeTaskId.value : tasks.value[0].taskId
      loadLogs(targetId)
    } else {
      activeTaskId.value = ''
      logs.value = []
    }
  } catch (err) {
    errorMsg.value = normalizeError(err)
  } finally {
    loading.value = false
  }
}

function showActionToast(message) {
  actionToast.value = message
  window.clearTimeout(showActionToast._timer)
  showActionToast._timer = window.setTimeout(() => {
    actionToast.value = ''
  }, 2500)
}
showActionToast._timer = null

async function handleCreateTask() {
  if (!authStore.token || creating.value) return
  if (!taskForm.value.taskName.trim()) {
    errorMsg.value = '请先填写任务名称'
    successMsg.value = ''
    showActionToast('请先填写任务名称')
    return
  }

  creating.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const created = await createCrawlTask(authStore.token, {
      taskName: taskForm.value.taskName.trim(),
      channel: taskForm.value.channel,
      keywords: taskForm.value.keywords.trim(),
      city: taskForm.value.city.trim(),
      priority: Number(taskForm.value.priority) || 5
    })
    if (created?.taskId) {
      await updateCrawlTaskStatus(authStore.token, created.taskId, { status: 1 })
    }
    taskForm.value.taskName = ''
    successMsg.value = '任务创建成功，已自动启动采集。'
    showActionToast('任务创建成功，已自动启动采集。')
    await loadDashboard()
  } catch (err) {
    successMsg.value = ''
    errorMsg.value = normalizeError(err)
    showActionToast(`操作失败：${normalizeError(err)}`)
  } finally {
    creating.value = false
  }
}

async function handleUpdateStatus(task, status) {
  if (!authStore.token || !task?.taskId || updatingTaskId.value) return
  updatingTaskId.value = `${task.taskId}`
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const currentStatus = Number(task.status)
    if ((currentStatus === 2 || currentStatus === 3) && status === 1) {
      const created = await createCrawlTask(authStore.token, {
        taskName: buildRerunTaskName(task.taskName),
        channel: task.channel || 'boss',
        keywords: task.keywords || '',
        city: task.city || '',
        priority: Number(task.priority) || 5
      })
      if (created?.taskId) {
        await updateCrawlTaskStatus(authStore.token, created.taskId, { status: 1 })
      }
      successMsg.value = '原任务已结束，已创建并启动新的重跑任务。'
      showActionToast('原任务已结束，已创建并启动新的重跑任务。')
      await loadDashboard()
      return
    }

    if (currentStatus === status) {
      successMsg.value = `任务当前已是“${getStatusMeta(status).label}”状态。`
      showActionToast(`任务当前已是“${getStatusMeta(status).label}”状态`)
      return
    }

    await updateCrawlTaskStatus(authStore.token, task.taskId, { status })
    const statusTextMap = {
      1: '任务已启动，正在采集中。',
      2: '任务已标记为完成。',
      3: '任务已停止。'
    }
    successMsg.value = statusTextMap[status] || '任务状态已更新。'
    showActionToast(statusTextMap[status] || '任务状态已更新。')
    await loadDashboard()
  } catch (err) {
    successMsg.value = ''
    errorMsg.value = normalizeError(err)
    showActionToast(`操作失败：${normalizeError(err)}`)
  } finally {
    updatingTaskId.value = ''
  }
}

async function handleRefreshSnapshots() {
  if (!authStore.token || snapshotLoading.value) return
  snapshotLoading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    const selected = snapshotTargetOptions.find((item) => item.value === snapshotTarget.value)
    await refreshPageSnapshots(authStore.token, {
      pageCodes: selected?.pageCodes || [],
      runIncrementalEtl: true
    })
    successMsg.value = '页面快照刷新成功。'
    showActionToast('页面快照刷新成功。')
    await loadDashboard()
  } catch (err) {
    successMsg.value = ''
    errorMsg.value = normalizeError(err)
    showActionToast(`操作失败：${normalizeError(err)}`)
  } finally {
    snapshotLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  pollTimer.value = window.setInterval(() => {
    if (!loading.value && !creating.value && !updatingTaskId.value && !snapshotLoading.value) {
      loadDashboard()
    }
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer.value) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  if (showActionToast._timer) {
    window.clearTimeout(showActionToast._timer)
    showActionToast._timer = null
  }
})
</script>

<template>
  <div class="collector-page">
    <transition name="fade-toast">
      <div v-if="actionToast" class="action-toast">{{ actionToast }}</div>
    </transition>
    <section class="hero">
      <div class="hero-main">
        <div>
          <span class="hero-kicker">Distributed Crawler Workspace</span>
          <h1>分布式数据采集控制台</h1>
          <p>创建任务、控制状态、查看日志和数据质量，保证采集链路可监控可回放。</p>
        </div>
        <div class="hero-actions">
          <GlowButton variant="ghost" :loading="loading" @click="loadDashboard">
            <RefreshCw :size="16" />
            刷新
          </GlowButton>
          <GlowButton variant="ghost" @click="openCollectorReport">
            生成运营报告
          </GlowButton>
          <GlowButton variant="ghost" @click="openCollectorAi">
            AI 深度分析
          </GlowButton>
        </div>
      </div>
      <div v-if="errorMsg" class="error-banner">{{ errorMsg }}</div>
      <div v-if="successMsg" class="success-banner">{{ successMsg }}</div>
    </section>

    <section class="metrics-grid">
      <article v-for="card in qualityCards" :key="card.label" class="metric-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </section>

    <section class="panel snapshot-panel">
      <header class="panel-head row">
        <div>
          <h2>页面数据快照</h2>
          <p class="sub">首页、数据分析和管理员运营面板统一改为读取后端快照表，默认每天凌晨 04:00 刷新，采集任务完成后也会自动更新。</p>
        </div>
        <div class="toolbar">
          <select v-model="snapshotTarget" class="input slim">
            <option v-for="item in snapshotTargetOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <GlowButton variant="primary" :loading="snapshotLoading" @click="handleRefreshSnapshots">
            <RefreshCw :size="16" />
            手动更新
          </GlowButton>
        </div>
      </header>
      <div class="panel-body">
        <div class="metrics-grid snapshot-metrics">
          <article v-for="page in snapshotPages" :key="page.pageCode" class="metric-card">
            <span>{{ page.pageName }}</span>
            <strong>{{ formatTime(page.refreshedAt) }}</strong>
            <small>{{ page.refreshTrigger || '待刷新' }}</small>
          </article>
        </div>
      </div>
      <footer class="panel-foot">
        <span class="sub">下次定时刷新：{{ nextSnapshotTime }}</span>
      </footer>
    </section>

    <section class="main-grid">
      <div class="left-col">
        <article class="panel">
          <header class="panel-head">
            <h2>创建任务</h2>
          </header>
          <div class="panel-body form-grid">
            <label class="field full">
              <span>任务名称</span>
              <input v-model="taskForm.taskName" class="input" placeholder="例如：成都 Python 每日采集" />
            </label>

            <label class="field">
              <span>渠道</span>
              <select v-model="taskForm.channel" class="input">
                <option value="boss">boss</option>
                <option value="zhaopin">zhaopin</option>
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
              <input v-model="taskForm.keywords" class="input" placeholder="Python, Java" />
            </label>

            <label class="field full">
              <span>城市</span>
              <input v-model="taskForm.city" class="input" placeholder="成都" />
            </label>
          </div>
          <footer class="panel-foot">
            <GlowButton variant="primary" :loading="creating" @click="handleCreateTask">
              <Plus :size="16" />
              创建任务
            </GlowButton>
          </footer>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>任务队列</h2>
            <span class="sub">共 {{ totalTasks }} 条</span>
          </header>

          <div class="panel-body">
            <div class="toolbar">
              <select v-model="filters.channel" class="input slim" @change="loadDashboard">
                <option v-for="item in channelOptions" :key="`ch-${item.value}`" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
              <select v-model="filters.status" class="input slim" @change="loadDashboard">
                <option v-for="item in statusOptions" :key="`st-${item.value}`" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </div>

            <div v-if="!tasks.length && !loading" class="empty-block">暂无任务</div>

            <div v-else class="task-list">
              <article
                v-for="task in tasks"
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
                  <span>渠道 {{ task.channel || '--' }}</span>
                  <span>城市 {{ task.city || '--' }}</span>
                  <span>开始 {{ formatTime(task.startTime) }}</span>
                </p>

                <div class="task-actions">
                  <button
                    class="mini-action"
                    :disabled="updatingTaskId === String(task.taskId)"
                    @click.stop="handleUpdateStatus(task, 1)"
                  >
                    <PlayCircle :size="14" />
                    {{ Number(task.status) === 2 || Number(task.status) === 3 ? '重跑' : '启动' }}
                  </button>
                  <button
                    class="mini-action"
                    :disabled="updatingTaskId === String(task.taskId)"
                    @click.stop="handleUpdateStatus(task, 3)"
                  >
                    <PauseCircle :size="14" />
                    停止
                  </button>
                  <button
                    class="mini-action danger"
                    :disabled="updatingTaskId === String(task.taskId)"
                    @click.stop="handleUpdateStatus(task, 2)"
                  >
                    <CheckCircle2 :size="14" />
                    完成
                  </button>
                </div>
              </article>
            </div>
          </div>
        </article>
      </div>

      <div class="right-col">
        <article class="panel">
          <header class="panel-head row">
            <h2>实时日志</h2>
            <span class="sub">{{ selectedTask ? selectedTask.taskId : '未选择任务' }}</span>
          </header>
          <div class="panel-body">
            <div v-if="logsLoading" class="empty-block">日志加载中...</div>
            <div v-else-if="!taskLogs.length" class="empty-block">暂无日志</div>
            <div v-else class="log-list">
              <article v-for="(item, idx) in taskLogs" :key="idx" class="log-item">
                <div class="log-top">
                  <span class="log-level">
                    <Activity :size="13" />
                    {{ item.level || 'INFO' }}
                  </span>
                  <span class="log-time">{{ formatTime(item.timestamp || item.createTime) }}</span>
                </div>
                <p class="log-text">{{ item.message || item.content || '--' }}</p>
              </article>
            </div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head row">
            <h2>采集健康摘要</h2>
            <span class="sub">
              <Server :size="13" />
              数据质量中心
            </span>
          </header>
          <div class="panel-body quality-list">
            <div class="quality-row">
              <span>重复率</span>
              <strong>{{ normalizePercent(quality.duplicateRate) }}</strong>
            </div>
            <div class="quality-row">
              <span>平均时延</span>
              <strong>{{ quality.avgLatency || '--' }}</strong>
            </div>
            <div class="quality-row">
              <span>今日新增</span>
              <strong>{{ quality.todayNewJobs ?? '--' }}</strong>
            </div>
            <div class="quality-row">
              <span>最后一次采集</span>
              <strong>{{ formatTime(quality.lastRunAt) }}</strong>
            </div>
            <div class="quality-row">
              <span>成功任务</span>
              <strong>
                <CheckCircle2 :size="13" />
                {{ quality.successTasks ?? '--' }}
              </strong>
            </div>
            <div class="quality-row">
              <span>待处理任务</span>
              <strong>
                <Clock3 :size="13" />
                {{ quality.pendingTasks ?? '--' }}
              </strong>
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
  gap: 18px;
}

.action-toast {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 1000;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.92);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 13px;
  box-shadow: 0 8px 24px rgba(2, 6, 23, 0.25);
}

.fade-toast-enter-active,
.fade-toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-toast-enter-from,
.fade-toast-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

.hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 22px 24px;
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.1), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(247, 250, 255, 0.98));
}

.hero-main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.hero-kicker {
  display: inline-block;
  margin-bottom: 8px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.hero h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(28px, 4vw, 38px);
  line-height: 1.08;
  letter-spacing: -0.02em;
  color: var(--c-text-primary);
}

.hero p {
  margin: 8px 0 0;
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--c-text-secondary);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
}

.metric-card span,
.metric-card small {
  font-family: var(--font-sans);
  color: var(--c-text-muted);
  font-size: 11px;
}

.metric-card strong {
  font-family: var(--font-serif);
  font-size: 22px;
  color: var(--c-text-primary);
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 16px;
}

.left-col,
.right-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-soft);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--c-border-glass);
}

.panel-head h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  color: var(--c-text-primary);
}

.panel-head.row .sub {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

.panel-body {
  padding: 14px 16px 16px;
}

.panel-foot {
  padding: 0 16px 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field.full {
  grid-column: 1 / -1;
}

.field span {
  font-family: var(--font-sans);
  font-size: 11px;
  color: var(--c-text-muted);
}

.input {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
  background: #fff;
}

.input.slim {
  max-width: 170px;
}

.input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(29, 78, 216, 0.12);
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.task-card {
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  padding: 12px;
  background: var(--c-bg-surface-hover);
  cursor: pointer;
}

.task-card.active {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.task-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.task-top h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 14px;
  color: var(--c-text-primary);
}

.pill {
  padding: 3px 8px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
}

.pill-idle { background: rgba(100, 116, 139, 0.12); color: #64748b; }
.pill-running { background: rgba(29, 78, 216, 0.12); color: #1d4ed8; }
.pill-paused { background: rgba(217, 119, 6, 0.12); color: #a16207; }
.pill-done { background: rgba(22, 163, 74, 0.12); color: #15803d; }
.pill-danger { background: rgba(220, 38, 38, 0.12); color: #b91c1c; }

.task-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.task-progress strong {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-primary);
}

.progress-track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(15, 23, 42, 0.08);
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1d4ed8, #38bdf8);
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin: 10px 0 0;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-secondary);
}

.task-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 9px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-secondary);
  background: var(--c-bg-base-elevated);
  cursor: pointer;
}

.mini-action:hover:not(:disabled) {
  border-color: var(--c-accent-primary);
  color: var(--c-accent-primary);
}

.mini-action.danger:hover:not(:disabled) {
  border-color: #e8b7b0;
  color: #8f2c22;
}

.mini-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-block {
  min-height: 120px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-muted);
  background: var(--c-bg-surface-hover);
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 430px;
  overflow-y: auto;
  padding-right: 4px;
}

.log-item {
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--c-bg-surface-hover);
}

.log-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.log-level {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.log-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-muted);
}

.log-text {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--c-text-primary);
  line-height: 1.55;
  word-break: break-word;
}

.quality-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quality-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--c-bg-surface-hover);
}

.quality-row span {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-secondary);
}

.quality-row strong {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 12.5px;
  color: var(--c-text-primary);
}

.error-banner {
  border: 1px solid rgba(220, 38, 38, 0.16);
  background: rgba(220, 38, 38, 0.06);
  color: #b91c1c;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  padding: 10px 12px;
}

.success-banner {
  border: 1px solid rgba(22, 163, 74, 0.2);
  background: rgba(22, 163, 74, 0.08);
  color: #166534;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  padding: 10px 12px;
}

@media (max-width: 1180px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .main-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .hero-main {
    flex-direction: column;
  }

  .metrics-grid,
  .quality-list,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .task-actions {
    flex-direction: column;
  }

  .input.slim {
    max-width: none;
  }
}
</style>
