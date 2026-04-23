<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { mapErrorMessage } from '../utils/errorMap'
import {
  backfillCrawlQualityHistory,
  createCrawlTask,
  createOpenApiKey,
  fetchAdminDashboard,
  fetchAdminLogs,
  fetchCrawlTasks,
  fetchCrawlQuality,
  fetchOpenApiKeyLogs,
  fetchOpenApiKeys,
  fetchRankerStatus,
  toggleOpenApiKey,
  trainRanker,
  updateCrawlTaskStatus
} from '../api'
import {
  Activity,
  Brain,
  Briefcase,
  Copy,
  Database,
  FileText,
  KeyRound,
  Pause,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Square,
  X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const router = useRouter()
const { error, success } = useToast()

const loading = ref(true)
const dashboard = ref(null)

// 平台采集健康度
const crawlTasks = ref([])
const crawlQuality = ref(null)

// Operation logs (平台系统日志)
const logs = ref([])
const logsLoading = ref(false)
const logsPage = ref(1)
const logsPageSize = ref(10)
const logsTotal = ref(0)
const logsHasMore = computed(() => logs.value.length < logsTotal.value)

// OpenAPI key audit (接口调用统计)
const apiKeys = ref([])
const apiKeyLogs = ref([])
const apiKeyLogsLoading = ref(false)

// API Key 管理：新建表单、创建态、启停态、展开行、行内日志
const apiKeyForm = ref({
  keyName: '',
  permissionProfile: 'basic',
  tenantScope: 'public',
  rateLimitQps: 10,
  dailyQuota: 1000
})
const apiKeyCreating = ref(false)
const apiKeyToggling = ref('')
const apiKeyExpanded = ref('') // 当前展开查看日志的 keyId
const apiKeyCreated = ref(null) // 创建后一次性展示的完整 key 对象（含明文 apiKey）

// 排序模型管理
const rankerStatus = ref(null)
const rankerLoading = ref(false)
const rankerTraining = ref(false)
const rankerLimit = ref(20000)

// 采集任务管理：新建表单 / 行内启停 / 质量回填
// 后端 CreateTaskRequest：taskName + channel 必填，keywords / city / priority 选填
const crawlTaskFormVisible = ref(false)
const crawlTaskCreating = ref(false)
const crawlTaskActionId = ref('') // 当前正在启停的任务 id，避免按钮重复点击
const crawlBackfilling = ref(false)
const crawlTaskForm = ref({
  taskName: '',
  channel: 'zhaopin',
  keywords: '',
  city: '',
  priority: 5
})
function resetCrawlTaskForm() {
  crawlTaskForm.value = {
    taskName: '',
    channel: 'zhaopin',
    keywords: '',
    city: '',
    priority: 5
  }
}

function showRequestError(prefix, err) {
  error(`${prefix}：${mapErrorMessage(err)}`)
}

function isApiKeyActive(key) {
  return key?.isActive === true || Number(key?.isActive) === 1
}

// 后端 CrawlTask.status 是严格 Integer（0=待启动 / 1=运行中 / 2=已暂停 / 3=已结束），
// 见 backend/.../crawl/entity/CrawlTask.java + CrawlTaskController.updateStatus 校验。
// 不做字符串兼容——任何非 0/1/2/3 都视为脏数据。
function crawlTaskStatusLabel(status) {
  switch (status) {
    case 0: return '待启动'
    case 1: return '运行中'
    case 2: return '已暂停'
    case 3: return '已结束'
    default: return '未知'
  }
}

function crawlTaskStatusTone(status) {
  return status === 1 ? 'ok' : 'off'
}

function apiLogCode(log) {
  return Number(log?.responseCode ?? 200)
}

const apiKeyNameById = computed(() => {
  return Object.fromEntries(
    apiKeys.value
      .filter((key) => key?.id !== undefined && key?.id !== null)
      .map((key) => [String(key.id), key.keyName || `Key #${key.id}`])
  )
})

// ---------- 数据就绪判断 ----------
// 文档《二、4.3》要求：未采集到业务数据时运营报告页只展示"数据准备中/待采集"，避免误导性空报表
// 条件：dashboard 返回的岗位总量、或采集质量里的累计岗位数，任一 > 0 即视为已就绪
const dataReady = computed(() => {
  const totalJobs = Number(dashboard.value?.totalJobs || 0)
  const crawledJobs = Number(crawlQuality.value?.totalJobs || 0)
  return totalJobs > 0 || crawledJobs > 0
})

// ---------- KPI ----------
// 平台级指标：岗位 / 报告 / 采集任务。用户侧指标一律下放到 /admin/users。
const kpiCards = computed(() => {
  if (!dashboard.value) return []
  // 后端契约：status === 1 即运行中
  const runningTasks = crawlTasks.value.filter((t) => t.status === 1).length
  return [
    { label: '岗位总量', value: dashboard.value.totalJobs ?? '--', hint: `近 7 天新增 ${dashboard.value.newJobs7d ?? 0}`, icon: Briefcase },
    { label: '报告总量', value: dashboard.value.totalReports ?? '--', hint: '累计产出', icon: FileText },
    { label: '采集任务', value: crawlTasks.value.length || '--', hint: `运行中 ${runningTasks}`, icon: Activity }
  ]
})

// ---------- 采集健康度摘要 ----------
const crawlHealth = computed(() => {
  const q = crawlQuality.value
  if (!q) return []
  const completeness = q.completeness || {}
  const pick = (value, label, fmt = (v) => v) => {
    const v = value
    if (v === undefined || v === null) return null
    return { label, value: fmt(v) }
  }
  return [
    pick(q.totalJobs, '累计入库岗位'),
    pick(completeness.titleRate, '标题完整率'),
    pick(completeness.salaryRate, '薪资完整率'),
    pick(q.updatedAt, '更新时间', formatDateTime)
  ].filter(Boolean)
})

// ---------- 接口调用统计 ----------
const apiCallStats = computed(() => {
  const rows = apiKeyLogs.value
  if (!rows.length) {
    return { total: 0, ok: 0, err: 0, uniqueKeys: apiKeys.value.length }
  }
  const ok = rows.filter((row) => apiLogCode(row) < 400).length
  const err = rows.length - ok
  return {
    total: rows.length,
    ok,
    err,
    uniqueKeys: apiKeys.value.length
  }
})

// ---------- Sidebar ----------
const navGroups = [
  {
    title: '概览',
    items: [
      { id: 'section-collector', label: '数据采集' },
      { id: 'section-api-audit', label: '接口调用审计' }
    ]
  },
  {
    title: '开放平台',
    items: [
      { id: 'section-api-keys', label: 'API Key 管理' },
      { id: 'section-ranker', label: '排序模型管理' }
    ]
  },
  {
    title: '治理',
    items: [
      { id: 'section-logs', label: '系统日志' }
    ]
  }
]

const activeSection = ref('section-collector')
let observer = null

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  if (!element) return
  const scrollContainer = document.querySelector('.main-content')
  if (scrollContainer instanceof HTMLElement) {
    const containerRect = scrollContainer.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const targetTop = scrollContainer.scrollTop + elementRect.top - containerRect.top - 12
    scrollContainer.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
    activeSection.value = sectionId
    return
  }
  const targetTop = element.getBoundingClientRect().top + window.scrollY - 12
  window.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
  activeSection.value = sectionId
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  if (observer) { observer.disconnect(); observer = null }
  const sections = document.querySelectorAll('.admin-section')
  if (!sections.length) return
  const root = document.querySelector('.main-content') || null
  observer = new IntersectionObserver((entries) => {
    const visible = entries
      .filter((e) => e.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
    if (visible[0]) activeSection.value = visible[0].target.id
  }, { root, rootMargin: '-20% 0px -60% 0px', threshold: 0 })
  sections.forEach((s) => observer.observe(s))
}

// ---------- Loaders ----------
async function loadDashboard() {
  dashboard.value = await fetchAdminDashboard(authStore.token)
}

async function loadCrawl() {
  // 采集任务与质量报告。任一失败不阻塞页面其它部分。
  const tasks = fetchCrawlTasks(authStore.token, { page: 1, pageSize: 20 })
    .then((res) => { crawlTasks.value = res.data || [] })
    .catch((e) => {
      crawlTasks.value = []
      showRequestError('加载采集任务失败', e)
    })
  const quality = fetchCrawlQuality(authStore.token)
    .then((res) => { crawlQuality.value = res || null })
    .catch((e) => {
      crawlQuality.value = null
      showRequestError('加载采集质量失败', e)
    })
  await Promise.all([tasks, quality])
}

// 新建采集任务：POST /crawl/tasks（CreateTaskRequest 要求 taskName + channel）
async function handleCreateCrawlTask() {
  if (crawlTaskCreating.value) return
  const name = crawlTaskForm.value.taskName.trim()
  const channel = crawlTaskForm.value.channel.trim()
  if (!name) { error('请填写任务名称'); return }
  if (!channel) { error('请填写采集渠道'); return }
  crawlTaskCreating.value = true
  try {
    const result = await createCrawlTask(authStore.token, {
      taskName: name,
      channel,
      keywords: crawlTaskForm.value.keywords.trim() || null,
      city: crawlTaskForm.value.city.trim() || null,
      priority: Number(crawlTaskForm.value.priority) || 5
    })
    success(`任务已创建（taskId: ${String(result?.taskId || '').slice(0, 8)}…）`)
    crawlTaskFormVisible.value = false
    resetCrawlTaskForm()
    await loadCrawl()
  } catch (e) {
    showRequestError('创建采集任务失败', e)
  } finally {
    crawlTaskCreating.value = false
  }
}

// 调整任务状态：PUT /crawl/tasks/{id}/status（status 0=待启动 1=运行 2=暂停 3=结束）
async function handleUpdateCrawlTaskStatus(task, nextStatus) {
  const taskId = task?.taskId
  if (!taskId || crawlTaskActionId.value) return
  crawlTaskActionId.value = String(taskId)
  try {
    await updateCrawlTaskStatus(authStore.token, taskId, { status: nextStatus })
    success(`任务状态已更新为「${crawlTaskStatusLabel(nextStatus)}」`)
    await loadCrawl()
  } catch (e) {
    showRequestError('更新任务状态失败', e)
  } finally {
    crawlTaskActionId.value = ''
  }
}

// 触发质量历史回填：POST /crawl/tasks/quality/history/backfill
async function handleBackfillCrawlQuality() {
  if (crawlBackfilling.value) return
  crawlBackfilling.value = true
  try {
    const result = await backfillCrawlQualityHistory(authStore.token)
    const inserted = result?.inserted ?? result?.count ?? 0
    success(`质量历史已回填${inserted ? `，新增 ${inserted} 条` : ''}。`)
    await loadCrawl()
  } catch (e) {
    showRequestError('质量回填失败', e)
  } finally {
    crawlBackfilling.value = false
  }
}

async function loadApiAudit() {
  apiKeyLogsLoading.value = true
  try {
    const [keys, logsRes] = await Promise.all([
      fetchOpenApiKeys(authStore.token).catch((e) => {
        showRequestError('加载 API Key 列表失败', e)
        return []
      }),
      fetchOpenApiKeyLogs(authStore.token, { page: 1, pageSize: 10 }).catch((e) => {
        showRequestError('加载 API 调用日志失败', e)
        return { data: [], total: 0 }
      })
    ])
    apiKeys.value = keys || []
    apiKeyLogs.value = logsRes.data || []
  } finally {
    apiKeyLogsLoading.value = false
  }
}

// ---------- API Key 管理 ----------
async function reloadApiKeys() {
  try {
    const keys = await fetchOpenApiKeys(authStore.token)
    apiKeys.value = keys || []
  } catch (e) {
    showRequestError('加载 API Key 失败', e)
  }
}

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('zh-CN', { hour12: false })
}

function apiKeyDisplayValue(key) {
  // 列表返回的 apiKey 可能是完整值，也可能已脱敏；前端只做裁剪展示。
  const raw = key?.apiKey || ''
  if (!raw) return '--'
  if (raw.length <= 12) return raw
  return `${raw.slice(0, 6)}****${raw.slice(-4)}`
}

async function handleCreateApiKey() {
  if (apiKeyCreating.value) return
  const name = apiKeyForm.value.keyName?.trim()
  if (!name) {
    error('请填写 Key 名称')
    return
  }
  apiKeyCreating.value = true
  try {
    const result = await createOpenApiKey(authStore.token, {
      keyName: name,
      permissionProfile: apiKeyForm.value.permissionProfile,
      tenantScope: apiKeyForm.value.tenantScope,
      rateLimitQps: Number(apiKeyForm.value.rateLimitQps) || 10,
      dailyQuota: Number(apiKeyForm.value.dailyQuota) || 1000
    })
    // 后端返回整条 ApiKey 实体，含明文 apiKey；此处只展示一次。
    apiKeyCreated.value = result || null
    success('API Key 已创建，请立即复制保存')
    apiKeyForm.value.keyName = ''
    await reloadApiKeys()
  } catch (e) {
    showRequestError('创建失败', e)
  } finally {
    apiKeyCreating.value = false
  }
}

async function handleToggleApiKey(key) {
  if (!key?.id) return
  const currentActive = isApiKeyActive(key)
  const nextActive = !currentActive
  apiKeyToggling.value = String(key.id)
  try {
    await toggleOpenApiKey(authStore.token, key.id, nextActive)
    success(nextActive ? '已启用' : '已停用')
    await reloadApiKeys()
  } catch (e) {
    showRequestError('操作失败', e)
  } finally {
    apiKeyToggling.value = ''
  }
}

async function toggleApiKeyLogs(key) {
  const id = String(key?.id || '')
  if (apiKeyExpanded.value === id) {
    apiKeyExpanded.value = ''
    return
  }
  apiKeyExpanded.value = id
  // 展开时重新拉取最近 20 条调用日志；审计列表本身是全平台的，这里复用并显示 20 条。
  try {
    const res = await fetchOpenApiKeyLogs(authStore.token, { page: 1, pageSize: 20 })
    apiKeyLogs.value = res.data || []
  } catch (e) {
    showRequestError('加载调用日志失败', e)
  }
}

function expandedLogsFor(key) {
  return apiKeyLogs.value
    .filter((row) => String(row.apiKeyId || '') === String(key?.id || ''))
    .slice(0, 10)
}

async function copyApiKey(value) {
  if (!value) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
    } else {
      const ta = document.createElement('textarea')
      ta.value = value
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    success('已复制到剪贴板')
  } catch (e) {
    showRequestError('复制失败', e)
  }
}

function dismissCreatedKey() {
  apiKeyCreated.value = null
}

// ---------- 排序模型管理 ----------
async function loadRankerStatus() {
  rankerLoading.value = true
  try {
    rankerStatus.value = await fetchRankerStatus(authStore.token)
  } catch (e) {
    showRequestError('加载排序器状态失败', e)
  } finally {
    rankerLoading.value = false
  }
}

async function handleTrainRanker() {
  if (rankerTraining.value) return
  rankerTraining.value = true
  try {
    await trainRanker(authStore.token, { limit: Number(rankerLimit.value) || 20000 })
    success('训练任务已触发，正在刷新状态')
    await loadRankerStatus()
  } catch (e) {
    showRequestError('训练失败', e)
  } finally {
    rankerTraining.value = false
  }
}

const rankerCards = computed(() => {
  const s = rankerStatus.value
  if (!s) return []
  const featureNames = Array.isArray(s.feature_names) ? s.feature_names : (Array.isArray(s.featureNames) ? s.featureNames : [])
  const metrics = s.metrics && typeof s.metrics === 'object' ? s.metrics : null
  const cards = [
    { label: '模型状态', value: s.trained ? '已就绪' : '未训练' },
    { label: '模型类型', value: s.model_type || s.modelType || '--' },
    { label: '样本数', value: s.sample_count ?? s.sampleCount ?? '--' },
    { label: '特征数', value: featureNames.length || '--' }
  ]
  if (s.trained_at || s.trainedAt) cards.push({ label: '最近训练', value: formatDateTime(s.trained_at || s.trainedAt) })
  if (s.version) cards.push({ label: '版本', value: s.version })
  if (metrics?.ndcg) cards.push({ label: 'NDCG', value: Number(metrics.ndcg).toFixed(4) })
  if (metrics?.mrr) cards.push({ label: 'MRR', value: Number(metrics.mrr).toFixed(4) })
  return cards
})

async function loadLogs(append = false) {
  logsLoading.value = true
  try {
    const res = await fetchAdminLogs(authStore.token, {
      page: logsPage.value,
      pageSize: logsPageSize.value
    })
    const items = res.data || []
    logs.value = append ? logs.value.concat(items) : items
    const totalFromRes = Number(res.total)
    logsTotal.value = Number.isFinite(totalFromRes) && totalFromRes > 0
      ? totalFromRes
      : logs.value.length
  } catch (e) {
    showRequestError('加载日志失败', e)
  } finally {
    logsLoading.value = false
  }
}

function refreshLogs() { logsPage.value = 1; loadLogs(false) }
function loadMoreLogs() {
  if (logsLoading.value || !logsHasMore.value) return
  logsPage.value += 1
  loadLogs(true)
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([
      loadDashboard(),
      loadCrawl(),
      loadApiAudit(),
      loadRankerStatus().catch(() => {})
    ])
  } catch (e) {
    showRequestError('运营面板加载失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const logsPromise = loadLogs(false)
  await loadData()
  await nextTick()
  setupObserver()
  logsPromise.catch(() => {})
})

watch(loading, async () => { await nextTick(); setupObserver() })
onBeforeUnmount(() => { if (observer) { observer.disconnect(); observer = null } })
</script>

<template>
  <div class="admin-view page-animate">
    <div v-if="loading" class="admin-skel">
      <div class="admin-skel-row">
        <SkeletonCard v-for="i in 4" :key="`s-${i}`" type="stat" />
      </div>
      <SkeletonCard type="chart" />
      <SkeletonCard type="list" :lines="5" />
    </div>

    <template v-else-if="dashboard">
      <div class="admin-shell">
        <aside class="admin-sidebar" aria-label="运营面板目录">
          <div class="admin-sidebar-inner">
            <h1 class="admin-hero-title">运营面板</h1>
            <nav
              v-for="group in navGroups"
              :key="group.title"
              class="admin-nav-group"
              :aria-label="group.title"
            >
              <div class="admin-nav-group-label">{{ group.title }}</div>
              <ul class="admin-nav-list">
                <li v-for="item in group.items" :key="item.id">
                  <a
                    :href="`#${item.id}`"
                    class="admin-nav-link"
                    :class="{ 'is-active': activeSection === item.id }"
                    @click.prevent="scrollToSection(item.id)"
                  >
                    <span class="admin-nav-link-label">{{ item.label }}</span>
                  </a>
                </li>
              </ul>
            </nav>
          </div>
        </aside>

        <div class="admin-main">
          <!-- 数据准备中引导：文档《二、4.3》要求未采集到数据时不展示误导性空报表 -->
          <article v-if="!dataReady" class="admin-readiness-banner">
            <div class="admin-readiness-icon"><Database :size="20" /></div>
            <div class="admin-readiness-body">
              <h3>数据准备中 / 待采集</h3>
              <p>平台暂未采集到业务数据，运营指标与报告仍在准备中。先运行采集任务，数据到位后会自动显示本页全部分析。</p>
            </div>
            <button class="admin-readiness-cta" type="button" @click="router.push('/crawler')">
              <Activity :size="14" /> 去采集控制台
            </button>
          </article>

          <section v-if="dataReady" class="workspace-metric-strip">
            <article v-for="card in kpiCards" :key="card.label" class="metric-card">
              <div class="metric-head">
                <span class="metric-label">{{ card.label }}</span>
                <component :is="card.icon" :size="16" class="metric-icon" />
              </div>
              <div class="metric-value">{{ card.value }}</div>
              <div class="metric-note">{{ card.hint }}</div>
            </article>
          </section>

          <article id="section-collector" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">数据采集</h2>
              <div class="panel-head-actions">
                <button
                  class="btn-ghost"
                  type="button"
                  :disabled="crawlBackfilling"
                  @click="handleBackfillCrawlQuality"
                >
                  <RotateCcw :size="14" />
                  {{ crawlBackfilling ? '回填中…' : '质量回填' }}
                </button>
                <button
                  class="btn-primary"
                  type="button"
                  @click="crawlTaskFormVisible = !crawlTaskFormVisible"
                >
                  <Plus :size="14" />
                  {{ crawlTaskFormVisible ? '收起' : '新建任务' }}
                </button>
                <button class="btn-ghost" type="button" @click="loadCrawl">
                  <RefreshCw :size="14" /> 刷新
                </button>
              </div>
            </header>
            <div class="panel-body">
              <!-- 新建任务内联表单：对齐后端 CreateTaskRequest -->
              <div v-if="crawlTaskFormVisible" class="crawl-task-form">
                <div class="crawl-task-form-grid">
                  <label class="field">
                    <span>任务名称 *</span>
                    <input
                      v-model="crawlTaskForm.taskName"
                      class="panel-input"
                      placeholder="例：BOSS · Java · 北京"
                    />
                  </label>
                  <label class="field">
                    <span>采集渠道 *</span>
                    <input
                      v-model="crawlTaskForm.channel"
                      class="panel-input"
                      placeholder="例：zhaopin / boss / lagou"
                    />
                  </label>
                  <label class="field">
                    <span>关键词</span>
                    <input
                      v-model="crawlTaskForm.keywords"
                      class="panel-input"
                      placeholder="例：Java 后端"
                    />
                  </label>
                  <label class="field">
                    <span>目标城市</span>
                    <input
                      v-model="crawlTaskForm.city"
                      class="panel-input"
                      placeholder="例：北京"
                    />
                  </label>
                  <label class="field">
                    <span>优先级（1~10）</span>
                    <input
                      v-model.number="crawlTaskForm.priority"
                      type="number"
                      min="1"
                      max="10"
                      class="panel-input"
                    />
                  </label>
                </div>
                <div class="crawl-task-form-actions">
                  <button
                    type="button"
                    class="btn-ghost"
                    :disabled="crawlTaskCreating"
                    @click="crawlTaskFormVisible = false; resetCrawlTaskForm()"
                  >
                    <X :size="13" /> 取消
                  </button>
                  <button
                    type="button"
                    class="btn-primary"
                    :disabled="crawlTaskCreating"
                    @click="handleCreateCrawlTask"
                  >
                    <Plus :size="13" />
                    {{ crawlTaskCreating ? '创建中…' : '创建任务' }}
                  </button>
                </div>
              </div>

              <div v-if="crawlHealth.length" class="kv-grid">
                <div v-for="kv in crawlHealth" :key="kv.label" class="kv-item">
                  <span class="kv-label">{{ kv.label }}</span>
                  <strong class="kv-value">{{ kv.value }}</strong>
                </div>
              </div>
              <div v-else class="empty-state">
                <Database :size="22" />
                <p>暂未获取到采集质量报告。</p>
              </div>

              <div v-if="crawlTasks.length" class="table-wrap">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>任务</th>
                      <th>状态</th>
                      <th>最近时间</th>
                      <th class="col-actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="t in crawlTasks.slice(0, 6)" :key="t.taskId">
                      <td><strong>{{ t.taskName || `#${t.taskId || '--'}` }}</strong></td>
                      <td><span :class="['status-pill', crawlTaskStatusTone(t.status)]">{{ crawlTaskStatusLabel(t.status) }}</span></td>
                      <td class="cell-muted">{{ formatDateTime(t.startTime || t.updateTime || t.createTime) }}</td>
                      <td class="cell-actions">
                        <!-- 三个按钮常驻显示，不可用状态用 disabled 灰化，避免"按钮消失"的错觉。
                             有效转换（以后端 updateStatus 校验为准）：
                               启动：非 1 → 1；暂停：1 → 2；结束：非 3 → 3 -->
                        <button
                          class="row-action-btn"
                          type="button"
                          :disabled="t.status === 1 || crawlTaskActionId === String(t.taskId)"
                          title="启动"
                          @click="handleUpdateCrawlTaskStatus(t, 1)"
                        >
                          <Play :size="12" /> 启动
                        </button>
                        <button
                          class="row-action-btn"
                          type="button"
                          :disabled="t.status !== 1 || crawlTaskActionId === String(t.taskId)"
                          title="暂停"
                          @click="handleUpdateCrawlTaskStatus(t, 2)"
                        >
                          <Pause :size="12" /> 暂停
                        </button>
                        <button
                          class="row-action-btn danger"
                          type="button"
                          :disabled="t.status === 3 || crawlTaskActionId === String(t.taskId)"
                          title="结束"
                          @click="handleUpdateCrawlTaskStatus(t, 3)"
                        >
                          <Square :size="12" /> 结束
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </article>

          <article id="section-api-audit" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">接口调用审计</h2>
              <button class="btn-ghost" type="button" :disabled="apiKeyLogsLoading" @click="loadApiAudit">
                <RefreshCw :size="14" /> 刷新
              </button>
            </header>
            <div class="panel-body">
              <div class="kv-grid">
                <div class="kv-item"><span class="kv-label">API Key 总数</span><strong class="kv-value">{{ apiCallStats.uniqueKeys }}</strong></div>
                <div class="kv-item"><span class="kv-label">最近调用样本</span><strong class="kv-value">{{ apiCallStats.total }}</strong></div>
                <div class="kv-item"><span class="kv-label">成功</span><strong class="kv-value">{{ apiCallStats.ok }}</strong></div>
                <div class="kv-item"><span class="kv-label">失败</span><strong class="kv-value">{{ apiCallStats.err }}</strong></div>
              </div>

              <div v-if="apiKeyLogs.length" class="table-wrap">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>Key</th>
                      <th>接口</th>
                      <th>状态</th>
                      <th>时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(r, idx) in apiKeyLogs.slice(0, 8)" :key="r.id || `${r.apiKeyId || 'row'}-${idx}`">
                      <td class="cell-muted">{{ apiKeyNameById[String(r.apiKeyId)] || (r.apiKeyId ? `Key #${r.apiKeyId}` : '--') }}</td>
                      <td><strong>{{ r.endpoint || '--' }}</strong></td>
                      <td>
                        <span :class="['status-pill', apiLogCode(r) < 400 ? 'ok' : 'off']">
                          {{ r.responseCode ?? '--' }}
                        </span>
                      </td>
                      <td class="cell-muted">{{ formatDateTime(r.createdAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else class="empty-state">
                <KeyRound :size="22" />
                <p>暂无 API 调用日志。</p>
              </div>
            </div>
          </article>

          <article id="section-api-keys" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">API Key 管理</h2>
              <button class="btn-ghost" type="button" @click="reloadApiKeys">
                <RefreshCw :size="14" /> 刷新
              </button>
            </header>
            <div class="panel-body">
              <div class="api-key-form">
                <div class="api-key-form-row">
                  <label class="form-field">
                    <span class="form-label">名称</span>
                    <input v-model="apiKeyForm.keyName" class="glass-input" placeholder="例如：合作伙伴 A-公开接口" />
                  </label>
                  <label class="form-field">
                    <span class="form-label">权限档位</span>
                    <select v-model="apiKeyForm.permissionProfile" class="glass-input">
                      <option value="basic">basic（公开只读）</option>
                      <option value="standard">standard（含报告 / 订阅）</option>
                      <option value="premium">premium（含分析 / 深度）</option>
                    </select>
                  </label>
                  <label class="form-field">
                    <span class="form-label">租户范围</span>
                    <select v-model="apiKeyForm.tenantScope" class="glass-input">
                      <option value="public">public（平台公开）</option>
                      <option value="tenant">tenant（限本租户）</option>
                    </select>
                  </label>
                </div>
                <div class="api-key-form-row">
                  <label class="form-field small">
                    <span class="form-label">QPS 限速</span>
                    <input v-model.number="apiKeyForm.rateLimitQps" type="number" min="1" max="200" class="glass-input" />
                  </label>
                  <label class="form-field small">
                    <span class="form-label">日配额</span>
                    <input v-model.number="apiKeyForm.dailyQuota" type="number" min="100" max="100000" step="100" class="glass-input" />
                  </label>
                  <div class="form-field-action">
                    <button class="btn-primary" type="button" :disabled="apiKeyCreating" @click="handleCreateApiKey">
                      <Plus :size="14" /> {{ apiKeyCreating ? '创建中…' : '新建 Key' }}
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="apiKeyCreated" class="created-key-banner">
                <div class="created-key-head">
                  <strong>新 Key 已生成，请立即抄录保存</strong>
                  <button class="btn-ghost" type="button" @click="dismissCreatedKey">我已保存</button>
                </div>
                <p class="created-key-hint">出于安全考虑，该明文值仅展示一次，关闭后列表将只显示脱敏值。</p>
                <div class="created-key-row">
                  <code class="created-key-value">{{ apiKeyCreated.apiKey || '--' }}</code>
                  <button class="btn-ghost" type="button" @click="copyApiKey(apiKeyCreated.apiKey)">
                    <Copy :size="14" /> 复制
                  </button>
                </div>
                <div class="created-key-meta">
                  <span>名称：{{ apiKeyCreated.keyName || '--' }}</span>
                  <span>QPS：{{ apiKeyCreated.rateLimitQps ?? '--' }}</span>
                  <span>日配额：{{ apiKeyCreated.dailyQuota ?? '--' }}</span>
                </div>
              </div>

              <div v-if="apiKeys.length" class="table-wrap">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>名称</th>
                      <th>Key</th>
                      <th>限速 / 配额</th>
                      <th>创建时间</th>
                      <th>最近调用</th>
                      <th>状态</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="k in apiKeys" :key="k.id">
                      <tr>
                        <td><strong>{{ k.keyName || '--' }}</strong></td>
                        <td class="cell-muted mono">{{ apiKeyDisplayValue(k) }}</td>
                        <td class="cell-muted">{{ k.rateLimitQps ?? '--' }} QPS · {{ k.dailyQuota ?? '--' }}/天</td>
                        <td class="cell-muted">{{ formatDateTime(k.createdAt) }}</td>
                        <td class="cell-muted">{{ k.lastUsedAt ? formatDateTime(k.lastUsedAt) : '暂无' }}</td>
                        <td>
                          <label class="toggle-switch" :class="{ busy: apiKeyToggling === String(k.id) }">
                            <input
                              type="checkbox"
                              :checked="isApiKeyActive(k)"
                              :disabled="apiKeyToggling === String(k.id)"
                              @change="handleToggleApiKey(k)"
                            />
                            <span class="toggle-track"></span>
                            <span class="toggle-label">{{ isApiKeyActive(k) ? '启用' : '停用' }}</span>
                          </label>
                        </td>
                        <td>
                          <button class="btn-ghost small" type="button" @click="toggleApiKeyLogs(k)">
                            {{ apiKeyExpanded === String(k.id) ? '收起日志' : '查看日志' }}
                          </button>
                        </td>
                      </tr>
                      <tr v-if="apiKeyExpanded === String(k.id)" class="expanded-row">
                        <td colspan="7">
                          <div class="log-inline">
                            <div class="log-inline-head">
                              <strong>最近调用日志</strong>
                              <span class="cell-muted">仅展示该 Key 最近 10 条调用记录。</span>
                            </div>
                            <div v-if="!expandedLogsFor(k).length" class="empty-inline">暂无调用记录。</div>
                            <div v-else class="log-inline-list">
                              <div v-for="(r, idx) in expandedLogsFor(k)" :key="r.id || idx" class="log-inline-item">
                                <span class="mono">{{ r.method || 'GET' }} {{ r.endpoint || '--' }}</span>
                                <span
                                  :class="['status-pill', apiLogCode(r) < 400 ? 'ok' : 'off']"
                                >
                                  {{ r.responseCode ?? '--' }}
                                </span>
                                <span class="cell-muted">{{ formatDateTime(r.createdAt) }}</span>
                              </div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
              <div v-else class="empty-state">
                <KeyRound :size="22" />
                <p>尚未签发 API Key。</p>
              </div>
            </div>
          </article>

          <article id="section-ranker" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">排序模型管理</h2>
              <button class="btn-ghost" type="button" :disabled="rankerLoading" @click="loadRankerStatus">
                <RefreshCw :size="14" /> 刷新
              </button>
            </header>
            <div class="panel-body">
              <div v-if="rankerCards.length" class="kv-grid">
                <div v-for="kv in rankerCards" :key="kv.label" class="kv-item">
                  <span class="kv-label">{{ kv.label }}</span>
                  <strong class="kv-value">{{ kv.value }}</strong>
                </div>
              </div>
              <div v-else class="empty-state">
                <Brain :size="22" />
                <p>{{ rankerLoading ? '正在拉取排序器状态…' : '暂未获取到排序器状态。' }}</p>
              </div>

              <div class="ranker-train-row">
                <label class="form-field small">
                  <span class="form-label">训练样本上限</span>
                  <input v-model.number="rankerLimit" type="number" min="1000" max="100000" step="1000" class="glass-input" />
                </label>
                <button
                  class="btn-primary"
                  type="button"
                  :disabled="rankerTraining"
                  @click="handleTrainRanker"
                >
                  <Brain :size="14" />
                  {{ rankerTraining ? '训练中，请稍候…' : '触发重新训练' }}
                </button>
                <p class="ranker-hint">
                  训练会调用管理端真实接口 <code>/recommend/train-ranker</code>，单次可能耗时数十秒到数分钟。
                </p>
              </div>
            </div>
          </article>

          <article id="section-logs" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">系统日志</h2>
              <button class="btn-ghost" type="button" :disabled="logsLoading" @click="refreshLogs">
                <RefreshCw :size="14" />
                <span>刷新</span>
              </button>
            </header>
            <div class="panel-body">
              <div v-if="logsLoading && !logs.length" class="logs-skel">
                <SkeletonCard type="list" :lines="4" />
              </div>
              <div v-else-if="logs.length" class="log-list">
                <div v-for="log in logs" :key="log.id" class="log-item">
                  <div class="log-main">
                    <strong>{{ log.operation }}</strong>
                    <span>{{ log.username || '系统' }} / {{ log.ipAddress || '未知 IP' }}</span>
                  </div>
                  <time>{{ formatDateTime(log.createdAt) }}</time>
                </div>
              </div>
              <div v-else class="empty-state">
                <FileText :size="24" />
                <p>暂无可展示的系统日志。</p>
              </div>
              <div v-if="logs.length && logsHasMore" class="log-loadmore">
                <button class="btn-ghost" type="button" :disabled="logsLoading" @click="loadMoreLogs">
                  {{ logsLoading ? '加载中…' : '加载更多' }}
                </button>
              </div>
            </div>
          </article>

        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 134px;
  padding: 18px 20px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
}

.metric-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.metric-label {
  font-family: var(--font-sans); font-size: 11px; font-weight: 700;
  letter-spacing: 0.12em; text-transform: uppercase; color: var(--c-text-muted);
}
.metric-icon { color: var(--c-accent-primary); opacity: 0.75; }
.metric-value {
  margin-top: auto; font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 28px); font-weight: 700;
  letter-spacing: -0.03em; line-height: 1.1;
  color: var(--c-text-primary); font-variant-numeric: tabular-nums;
}
.metric-note { font-family: var(--font-sans); font-size: 12.5px; line-height: 1.5; color: var(--c-text-secondary); }

.admin-shell { display: grid; grid-template-columns: 232px minmax(0, 1fr); gap: 28px; align-items: start; }

.admin-sidebar {
  position: sticky; top: 0; align-self: start;
  max-height: calc(100vh - 24px); overflow-y: auto;
  border-right: 1px solid var(--c-border-glass);
  background: transparent; scrollbar-width: none;
}
.admin-sidebar::-webkit-scrollbar { display: none; }
.admin-sidebar-inner { display: flex; flex-direction: column; gap: 20px; padding: 24px 16px 32px; }
.admin-hero-title {
  margin: 0 0 4px;
  padding: 0 8px 14px;
  border-bottom: 1px solid var(--c-border-glass);
  font-family: var(--font-serif);
  font-size: clamp(20px, 1.8vw, 24px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}
.admin-nav-group { display: flex; flex-direction: column; gap: 4px; }
.admin-nav-group-label {
  padding: 0 8px 2px; color: var(--c-text-muted);
  font-family: var(--font-sans); font-size: 11px; font-weight: 600;
  letter-spacing: 0.12em; text-transform: uppercase;
}
.admin-nav-list { display: flex; flex-direction: column; gap: 2px; margin: 0; padding: 0; list-style: none; }
.admin-nav-link {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  gap: 8px; padding: 7px 10px 7px 12px; border-radius: 6px;
  color: var(--c-text-secondary); font-family: var(--font-sans);
  font-size: 13.5px; font-weight: 400; line-height: 1.4;
  text-decoration: none; cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.admin-nav-link-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-nav-link:hover { background: var(--c-bg-surface-hover); color: var(--c-text-primary); }
.admin-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.admin-nav-link.is-active::before {
  content: ''; position: absolute; left: 0; top: 6px; bottom: 6px;
  width: 2px; border-radius: 2px; background: var(--c-accent-primary);
}

.admin-main { display: flex; flex-direction: column; gap: 24px; min-width: 0; }
.admin-section { scroll-margin-top: 16px; }

/* 数据就绪前的引导 banner */
.admin-readiness-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-radius: 14px;
  border: 1px dashed rgba(255, 196, 87, 0.45);
  background: rgba(255, 196, 87, 0.06);
}
.admin-readiness-icon {
  width: 40px; height: 40px;
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(240, 168, 48, 0.15);
  color: #f0a830;
  flex-shrink: 0;
}
.admin-readiness-body { flex: 1; min-width: 0; }
.admin-readiness-body h3 { margin: 0 0 4px; font-size: 15px; color: var(--c-text-primary); }
.admin-readiness-body p { margin: 0; font-size: 13px; color: var(--c-text-muted); line-height: 1.6; }
.admin-readiness-cta {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 999px;
  border: 1px solid rgba(30, 117, 255, 0.35);
  background: rgba(30, 117, 255, 0.12);
  color: var(--c-accent-primary);
  cursor: pointer; font-size: 13px; font-weight: 500;
  transition: all .2s;
}
.admin-readiness-cta:hover { background: rgba(30, 117, 255, 0.2); transform: translateY(-1px); }

.panel {
  display: flex; flex-direction: column;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px; box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
}
.panel-head { padding: 16px 22px 12px; border-bottom: 1px solid var(--c-border-glass); }
.panel-head-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.panel-title {
  margin: 0; font-family: var(--font-serif); font-size: 16px;
  font-weight: 700; letter-spacing: -0.01em; line-height: 1.25; color: var(--c-text-primary);
}
.panel-body { padding: 18px 22px 20px; display: flex; flex-direction: column; gap: 14px; }

.kv-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px;
}
.kv-item {
  display: flex; flex-direction: column; gap: 4px;
  padding: 12px 14px; border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.kv-label { color: var(--c-text-muted); font-size: 11.5px; }
.kv-value { color: var(--c-text-primary); font-family: var(--font-serif); font-size: 18px; font-weight: 700; }

.log-list { display: grid; gap: 8px; }
.log-item {
  display: flex; justify-content: space-between; gap: 14px;
  padding: 12px 14px; border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.log-main { display: grid; gap: 4px; min-width: 0; }
.log-main strong { color: var(--c-text-primary); font-size: 13.5px; font-weight: 600; }
.log-main span { color: var(--c-text-muted); font-size: 12px; }
.log-item time { color: var(--c-text-muted); font-family: var(--font-mono); font-size: 11.5px; flex-shrink: 0; }

.empty-state {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; padding: 28px 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px; color: var(--c-text-muted); font-size: 13px;
}
.empty-state :deep(svg) { color: var(--c-text-faint); }

.btn-ghost {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 12px; border-radius: 9px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans); font-size: 13px; font-weight: 600;
  cursor: pointer;
  transition: background-color var(--duration-fast), color var(--duration-fast);
}
.btn-ghost:hover { background: var(--c-bg-surface-hover); color: var(--c-text-primary); }
.btn-ghost:disabled { cursor: not-allowed; opacity: 0.6; }

.log-loadmore { display: flex; justify-content: center; padding-top: 4px; }

.table-wrap { overflow-x: auto; border-radius: 12px; border: 1px solid var(--c-border-glass); }
.data-table { width: 100%; border-collapse: collapse; font-family: var(--font-sans); }
.data-table th {
  padding: 10px 14px; text-align: left; font-size: 11px; font-weight: 600;
  letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--c-text-muted); background: var(--c-bg-surface-hover);
  border-bottom: 1px solid var(--c-border-glass);
}
.data-table td {
  padding: 12px 14px; border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-primary); font-size: 13px; vertical-align: middle;
}
.data-table tbody tr:last-child td { border-bottom: none; }
.cell-muted { color: var(--c-text-muted); font-size: 12.5px; }

.status-pill {
  display: inline-flex; align-items: center;
  padding: 3px 9px; border-radius: 999px;
  font-size: 11px; font-weight: 600;
}
.status-pill.ok { background: rgba(30, 138, 91, 0.12); color: #1e8a5b; }
.status-pill.off { background: rgba(178, 59, 46, 0.1); color: #b23b2e; }

/* ---------- API Key 管理 ---------- */
.api-key-form {
  display: flex; flex-direction: column; gap: 12px;
  padding: 14px 16px; border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}
.api-key-form-row {
  display: grid; gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: end;
}
.form-field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.form-field.small { max-width: 180px; }
.form-label {
  font-family: var(--font-sans); font-size: 11px; font-weight: 700;
  letter-spacing: 0.08em; text-transform: uppercase; color: var(--c-text-muted);
}
.form-field-action { display: flex; align-items: flex-end; justify-content: flex-end; }
.glass-input {
  width: 100%; padding: 9px 12px; border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans); font-size: 13px; line-height: 1.3;
  transition: border-color var(--duration-fast) var(--ease-out),
              box-shadow var(--duration-fast) var(--ease-out);
}
.glass-input:focus, .glass-input:focus-visible {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
  outline: none;
}
.btn-primary {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 10px; border: 1px solid transparent;
  background: var(--c-accent-primary); color: #fff;
  font-family: var(--font-sans); font-size: 13px; font-weight: 600;
  cursor: pointer;
  transition: filter var(--duration-fast) var(--ease-out),
              opacity var(--duration-fast) var(--ease-out);
}
.btn-primary:hover { filter: brightness(1.05); }
.btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
.btn-ghost.small { padding: 4px 10px; font-size: 12px; }

.created-key-banner {
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px 16px; border-radius: 12px;
  border: 1px solid rgba(30, 138, 91, 0.28);
  background: rgba(30, 138, 91, 0.08);
}
.created-key-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.created-key-head strong { color: var(--c-text-primary); font-size: 13.5px; }
.created-key-hint { margin: 0; color: var(--c-text-secondary); font-size: 12.5px; }
.created-key-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 10px;
  border: 1px dashed rgba(30, 138, 91, 0.4);
  background: var(--c-bg-base-elevated);
}
.created-key-value {
  flex: 1; min-width: 0;
  font-family: var(--font-mono); font-size: 13px;
  color: var(--c-text-primary);
  overflow-wrap: anywhere; word-break: break-all;
}
.created-key-meta {
  display: flex; flex-wrap: wrap; gap: 14px;
  font-size: 12px; color: var(--c-text-muted);
}

.mono { font-family: var(--font-mono); }
.toggle-switch {
  display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
  user-select: none;
}
.toggle-switch input { display: none; }
.toggle-track {
  width: 34px; height: 18px; border-radius: 999px;
  background: var(--c-bg-surface-hover);
  border: 1px solid var(--c-border-glass);
  position: relative;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.toggle-track::after {
  content: ''; position: absolute; top: 1px; left: 1px;
  width: 14px; height: 14px; border-radius: 50%;
  background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.15);
  transition: transform var(--duration-fast) var(--ease-out);
}
.toggle-switch input:checked + .toggle-track { background: var(--c-accent-primary); border-color: var(--c-accent-primary); }
.toggle-switch input:checked + .toggle-track::after { transform: translateX(16px); }
.toggle-label { font-size: 12px; color: var(--c-text-secondary); }
.toggle-switch.busy { opacity: 0.65; pointer-events: none; }

.expanded-row > td {
  background: var(--c-bg-surface-hover);
  padding: 12px 14px;
}
.log-inline { display: flex; flex-direction: column; gap: 8px; }
.log-inline-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.log-inline-head strong { font-size: 12px; color: var(--c-text-primary); }
.log-inline-list { display: flex; flex-direction: column; gap: 6px; }
.log-inline-item {
  display: grid; grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px; align-items: center;
  padding: 6px 10px; border-radius: 8px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  font-size: 12px;
}
.empty-inline {
  padding: 10px; text-align: center;
  color: var(--c-text-muted); font-size: 12px;
  border-radius: 8px; border: 1px dashed var(--c-border-glass);
}

.ranker-train-row {
  display: flex; flex-wrap: wrap; gap: 12px;
  align-items: flex-end;
  padding: 14px 16px; border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}
.ranker-hint {
  flex: 1 1 240px; margin: 0;
  color: var(--c-text-muted); font-size: 12.5px; line-height: 1.5;
}
.ranker-hint code {
  font-family: var(--font-mono); font-size: 12px;
  padding: 1px 4px; border-radius: 4px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
}

.admin-skel { display: flex; flex-direction: column; gap: 16px; padding: 8px 0; }
.admin-skel-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.logs-skel { padding: 8px 0 4px; }

@media (max-width: 1120px) {
  .workspace-metric-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .admin-shell { grid-template-columns: minmax(0, 1fr); gap: 16px; }
  .admin-sidebar {
    position: sticky; top: 0; z-index: 5;
    max-height: none; overflow-x: auto; overflow-y: hidden;
    border-right: none; border-bottom: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated);
  }
  .admin-sidebar-inner { flex-direction: row; flex-wrap: nowrap; gap: 18px; padding: 10px 12px; min-width: max-content; }
  .admin-hero-title { display: none; }
  .admin-nav-group { flex-direction: row; align-items: center; gap: 6px; }
  .admin-nav-group-label { padding: 0 4px 0 0; white-space: nowrap; font-size: 10px; }
  .admin-nav-list { flex-direction: row; gap: 6px; }
  .admin-nav-link {
    padding: 6px 12px; border-radius: 999px;
    border: 1px solid var(--c-border-glass);
    background: var(--c-bg-base-elevated); white-space: nowrap;
  }
  .admin-nav-link.is-active { border-color: var(--c-accent-primary); }
  .admin-nav-link.is-active::before {
    left: 10px; right: 10px; top: auto; bottom: 2px;
    width: auto; height: 2px;
  }
}
@media (max-width: 900px) {
  .api-key-form-row { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .workspace-metric-strip { grid-template-columns: 1fr; }
}

/* ============ 数据采集：新建任务 / 行动作 ============ */
.panel-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.crawl-task-form {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
}
.crawl-task-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}
.crawl-task-form .field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.crawl-task-form .field span {
  font-size: 12px;
  color: var(--c-text-secondary);
  font-weight: 600;
}
.panel-input {
  padding: 7px 10px;
  font-size: 13px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-surface);
  color: var(--c-text-primary);
  transition: border-color 0.15s;
}
.panel-input:focus {
  outline: none;
  border-color: var(--c-accent-primary);
}
.crawl-task-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.col-actions { width: 180px; }
.cell-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.row-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
  border-radius: 6px;
  border: 1px solid var(--c-border-glass);
  background: transparent;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.row-action-btn:hover:not(:disabled) {
  color: var(--c-accent-primary);
  border-color: var(--c-accent-primary);
}
.row-action-btn.danger:hover:not(:disabled) {
  color: #b23b2e;
  border-color: #b23b2e;
}
.row-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
