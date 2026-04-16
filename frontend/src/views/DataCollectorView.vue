<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Activity,
  CheckCircle2,
  Clock3,
  DatabaseZap,
  FileText,
  LoaderCircle,
  PauseCircle,
  PlayCircle,
  Plus,
  Radar,
  ShieldCheck,
  SquareX,
  TerminalSquare
} from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createCrawlTask,
  fetchCrawlQuality,
  fetchCrawlTaskLogs,
  fetchCrawlTasks,
  normalizeError,
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

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="collector-page page-animate">
    <section class="collector-hero">
      <div class="hero-copy">
        <div class="hero-badge">
          <DatabaseZap :size="18" />
          数据采集控制台
        </div>
        <h1>统一管理采集任务、质量和日志</h1>
        <p>创建任务、查看状态、切换日志，都在这一页完成。</p>
      </div>
      <div class="hero-strip">
        <div class="hero-chip">
          <ShieldCheck :size="16" />
          管理员页
        </div>
        <div class="hero-chip">
          <Radar :size="16" />
          任务 / 质量 / 日志
        </div>
      </div>
    </section>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <section class="metrics-grid">
      <PremiumCard
        v-for="item in qualityCards"
        :key="item.label"
        :title="item.label"
        glowColor="teal"
        class="metric-card"
      >
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-note">{{ item.note }}</div>
      </PremiumCard>
    </section>

    <section class="collector-main">
      <div class="left-column">
        <PremiumCard title="创建采集任务" glowColor="primary">
          <div class="task-form">
            <input v-model="taskForm.taskName" class="collector-input" placeholder="任务名称，例如：上海 Java 日采集" />
            <div class="form-row">
              <select v-model="taskForm.channel" class="collector-input">
                <option value="boss">BOSS 直聘</option>
                <option value="zhaopin">智联招聘</option>
                <option value="51job">前程无忧</option>
                <option value="liepin">猎聘</option>
              </select>
              <input v-model="taskForm.city" class="collector-input" placeholder="城市" />
            </div>
            <input v-model="taskForm.keywords" class="collector-input" placeholder="关键词，例如：Java, 数据分析, Vue" />
            <div class="form-row compact">
              <input v-model.number="taskForm.priority" class="collector-input" type="number" min="1" max="10" placeholder="优先级" />
              <GlowButton variant="primary" :loading="submitting" @click="handleCreateTask">
                <Plus :size="15" />
                创建任务
              </GlowButton>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="任务队列" glowColor="teal">
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
              @click="loadLogs(task.taskId)"
            >
              <div class="task-head">
                <div>
                  <h3>{{ task.taskName }}</h3>
                  <p>{{ task.channel }} · {{ task.city || '全域' }} · {{ task.keywords || '无关键词' }}</p>
                </div>
                <span class="status-pill" :class="getStatusMeta(task.status).tone">
                  <component :is="getStatusMeta(task.status).icon" :size="14" />
                  {{ getStatusMeta(task.status).label }}
                </span>
              </div>

              <div class="task-progress">
                <div class="progress-track">
                  <div class="progress-fill" :style="{ width: `${progressPercent(task)}%` }" />
                </div>
                <span>{{ task.finishedCount || 0 }} / {{ task.totalCount || 0 }}</span>
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
                  <PlayCircle :size="14" /> 启动
                </button>
                <button
                  class="mini-action"
                  :disabled="statusUpdating === `${task.taskId}:2`"
                  @click.stop="handleTaskStatus(task, 2)"
                >
                  <PauseCircle :size="14" /> 暂停
                </button>
                <button
                  class="mini-action danger"
                  :disabled="statusUpdating === `${task.taskId}:3`"
                  @click.stop="handleTaskStatus(task, 3)"
                >
                  <SquareX :size="14" /> 结束
                </button>
              </div>
            </article>
          </div>
          <div class="foot-note">共 {{ totalTasks }} 个任务，默认展示最近 20 条。</div>
        </PremiumCard>
      </div>

      <div class="right-column">
        <PremiumCard title="质量健康概览" glowColor="secondary">
          <div class="quality-section">
            <div class="quality-block">
              <div class="quality-label">字段完整率</div>
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
            </div>

            <div class="quality-block">
              <div class="quality-label">数据新鲜度</div>
              <div class="freshness-list">
                <div v-for="item in freshnessRows" :key="item.period" class="freshness-row">
                  <span>{{ item.period }}</span>
                  <div class="freshness-bar">
                    <div
                      class="freshness-fill"
                      :style="{ width: `${Math.min(100, Number(item.count || 0) / Math.max(Number(quality.totalJobs || 1), 1) * 100)}%` }"
                    />
                  </div>
                  <strong>{{ item.count }}</strong>
                </div>
              </div>
            </div>

            <div class="quality-foot">
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
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="实时抓取日志" glowColor="primary">
          <div class="log-head">
            <div class="log-title">
              <TerminalSquare :size="16" />
              <span>{{ activeTaskId || '尚未选择任务' }}</span>
            </div>
            <span class="log-subtitle">最近 20 条</span>
          </div>

          <div v-if="logsLoading" class="empty-block">正在拉取日志...</div>
          <div v-else-if="logs.length === 0" class="empty-block">当前任务暂无日志输出。</div>
          <div v-else class="log-stream">
            <article v-for="item in logs" :key="item.logId" class="log-line">
              <div class="log-meta-line">
                <span class="log-level" :class="(item.level || 'INFO').toLowerCase()">{{ item.level || 'INFO' }}</span>
                <span>{{ item.workerId || 'worker-unknown' }}</span>
                <span>{{ formatTime(item.createTime) }}</span>
              </div>
              <div class="log-message">{{ item.message }}</div>
            </article>
          </div>
        </PremiumCard>
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
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  border: 1px solid rgba(9, 30, 66, 0.08);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(1, 102, 255, 0.08), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(244, 248, 255, 0.9));
}

.hero-copy {
  max-width: 760px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hero-badge,
.hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(9, 30, 66, 0.08);
  color: #28425f;
  font-size: 13px;
  font-weight: 700;
}

.hero-copy h1 {
  margin: 0;
  color: #172435;
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.hero-copy p {
  margin: 0;
  color: #5a6d86;
  max-width: 42ch;
  font-size: 14px;
  line-height: 1.55;
}

.hero-strip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.error-banner {
  padding: 14px 16px;
  border: 1px solid rgba(185, 28, 28, 0.15);
  border-radius: 14px;
  background: rgba(254, 242, 242, 0.96);
  color: #b91c1c;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  min-height: 132px;
}

.metric-value {
  margin-top: 8px;
  color: #172435;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.metric-note {
  margin-top: 8px;
  color: #61758d;
  font-size: 13px;
  line-height: 1.6;
}

.collector-main {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(360px, 0.9fr);
  gap: 20px;
}

.left-column,
.right-column {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.task-form,
.quality-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row,
.toolbar,
.quality-foot,
.task-actions,
.task-progress,
.task-head,
.quality-row,
.freshness-row,
.log-head,
.log-meta-line {
  display: flex;
  align-items: center;
}

.form-row,
.toolbar,
.quality-foot {
  gap: 12px;
}

.form-row.compact {
  justify-content: space-between;
}

.collector-input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  color: #1d2a3b;
  font-size: 14px;
}

.collector-input.slim {
  max-width: 180px;
}

.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  border: 1px dashed rgba(15, 23, 42, 0.1);
  border-radius: 14px;
  background: rgba(247, 249, 252, 0.88);
  color: #6b7c93;
  font-size: 14px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-row {
  padding: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-card-quiet);
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
  cursor: pointer;
}

.task-row.active,
.task-row:hover {
  border-color: rgba(1, 102, 255, 0.2);
  box-shadow: var(--shadow-card-soft);
  transform: translateY(-1px);
}

.task-head {
  justify-content: space-between;
  gap: 14px;
}

.task-head h3 {
  margin: 0;
  color: #1a2a40;
  font-size: 16px;
}

.task-head p,
.task-meta,
.log-subtitle {
  margin: 4px 0 0;
  color: #6a7b92;
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.status-pill.idle {
  background: rgba(241, 245, 249, 1);
  color: #475569;
}

.status-pill.running {
  background: rgba(219, 234, 254, 1);
  color: #0057c2;
}

.status-pill.running :deep(svg) {
  animation: spin 1s linear infinite;
}

.status-pill.paused {
  background: rgba(254, 243, 199, 1);
  color: #b45309;
}

.status-pill.done {
  background: rgba(220, 252, 231, 1);
  color: #15803d;
}

.task-progress {
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.progress-track {
  position: relative;
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.9);
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0057c2, #41a5ff);
}

.task-progress span,
.task-meta span {
  color: #61758d;
  font-size: 12px;
  font-weight: 700;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 12px;
}

.task-actions {
  gap: 8px;
  margin-top: 14px;
}

.mini-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.86);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.mini-action.danger {
  color: #b91c1c;
}

.foot-note {
  margin-top: 12px;
  color: #72849a;
  font-size: 12px;
}

.quality-block {
  padding: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.88);
}

.quality-label {
  margin-bottom: 10px;
  color: #4f6480;
  font-size: 13px;
  font-weight: 800;
}

.quality-list,
.freshness-list,
.log-stream {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quality-row,
.freshness-row,
.log-head,
.log-meta-line {
  justify-content: space-between;
  gap: 12px;
}

.quality-row span,
.freshness-row span,
.log-message {
  color: #566980;
  font-size: 13px;
}

.quality-row strong,
.freshness-row strong,
.quality-stat strong {
  color: #172435;
}

.freshness-bar {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.86);
}

.freshness-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0f9d8a, #44d4b7);
}

.quality-foot {
  margin-top: 4px;
}

.quality-stat {
  flex: 1;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(250, 252, 255, 0.92);
}

.quality-stat span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 12px;
}

.log-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1f3148;
  font-size: 14px;
  font-weight: 800;
}

.log-line {
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: rgba(251, 252, 254, 0.96);
}

.log-level {
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.log-level.info {
  background: rgba(219, 234, 254, 1);
  color: #1d4ed8;
}

.log-level.warn {
  background: rgba(254, 243, 199, 1);
  color: #b45309;
}

.log-level.error {
  background: rgba(254, 226, 226, 1);
  color: #b91c1c;
}

.log-message {
  margin-top: 8px;
  line-height: 1.7;
  word-break: break-word;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1200px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .collector-main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .collector-hero {
    flex-direction: column;
    padding: 22px;
  }

  .hero-strip {
    align-items: flex-start;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .form-row,
  .quality-foot,
  .task-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .collector-input.slim {
    max-width: none;
  }

  .task-head,
  .task-progress,
  .log-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
