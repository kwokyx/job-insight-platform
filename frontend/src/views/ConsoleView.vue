<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  KeyRound,
  Plus,
  Copy,
  Ban,
  CheckCircle2,
  BookOpen,
  ArrowLeft,
  AlertTriangle,
  RefreshCw,
  Download,
  ChevronDown,
  CalendarDays,
  Inbox
} from 'lucide-vue-next'
import {
  createOpenApiKey,
  fetchAdminUsers,
  fetchOpenApiKeyLogs,
  fetchOpenApiKeys,
  normalizeError,
  toggleOpenApiKey
} from '../api'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { useThemeStore } from '../store/theme'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const { error: toastError, success } = useToast()

use([CanvasRenderer, LineChart, TitleComponent, TooltipComponent, GridComponent])

const router = useRouter()

// ---- Real data state ----
// 后端 /open/api-keys 返回 ApiKey 实体数组（id, keyName, apiKey, isActive, createdAt, lastUsedAt, userId...）
// 后端 /open/api-keys/logs 返回原始调用日志（id, apiKeyId, endpoint, method, responseCode, responseTime, createdAt...）
// 后端 /admin/users 返回用户列表，拿来把 apiKey.userId → 用户昵称，方便"按用户"视图。
// 聚合图表 / KPI 全部在前端按日桶 + 分组算出来。
const keys = ref([])
const rawLogs = ref([])
const users = ref([])
const loading = ref(false)
const loadError = ref('')

// ---- Filter chip state (date range only) ----
// 项目 chip 在后端没有实现（日志没有 project/tenant 列），先只保留时间范围。
const dateRange = ref('last-14-days')

const dateRangeOptions = [
  { value: 'last-7-days', label: '近 7 天', days: 7 },
  { value: 'last-14-days', label: '近 14 天', days: 14 },
  { value: 'last-30-days', label: '近 30 天', days: 30 }
]

const dateRangeChipLabel = computed(() => {
  if (!dateRange.value) return '时间范围'
  return dateRangeOptions.find((o) => o.value === dateRange.value)?.label ?? '时间范围'
})
const isDateRangeActive = computed(() => !!dateRange.value)

const currentDays = computed(() => {
  const hit = dateRangeOptions.find((o) => o.value === dateRange.value)
  return hit?.days ?? 14
})

const openFilterKey = ref('')
let filterCloseTimer = null
function openFilter(key) {
  if (filterCloseTimer) {
    clearTimeout(filterCloseTimer)
    filterCloseTimer = null
  }
  openFilterKey.value = key
}
function scheduleCloseFilter() {
  if (filterCloseTimer) clearTimeout(filterCloseTimer)
  filterCloseTimer = setTimeout(() => {
    openFilterKey.value = ''
    filterCloseTimer = null
  }, 120)
}
function closeFilterNow() {
  if (filterCloseTimer) {
    clearTimeout(filterCloseTimer)
    filterCloseTimer = null
  }
  openFilterKey.value = ''
}
function handleFilterOutsideClick(e) {
  if (!openFilterKey.value) return
  const target = e.target
  if (target instanceof Element && target.closest('.console-chip-wrap')) return
  closeFilterNow()
}
function handleFilterKey(e) {
  if (e.key === 'Escape') closeFilterNow()
}

function pickDateRange(value) {
  dateRange.value = value
  closeFilterNow()
}
function clearDateRange() {
  dateRange.value = 'last-14-days'
  closeFilterNow()
}

// ---- Key derivations from real data ----
const activeKeyCount = computed(() => keys.value.filter((k) => k.isActive === 1).length)
const revokedKeyCount = computed(() => keys.value.filter((k) => k.isActive !== 1).length)
const totalKeyCount = computed(() => keys.value.length)

// apiKeyId → keyName 映射，最近活动表里显示每条日志属于哪个 Key
const keyNameMap = computed(() => {
  const map = {}
  for (const k of keys.value) {
    map[k.id] = k.keyName || `Key #${k.id}`
  }
  return map
})

// userId → 用户对象（拿来在"按用户"视图里显示昵称 / 角色）
const userById = computed(() => {
  const map = {}
  for (const u of users.value) {
    map[u.id] = u
  }
  return map
})

// apiKeyId → userId，把日志归到用户上的关键索引
const userIdByKeyId = computed(() => {
  const map = {}
  for (const k of keys.value) {
    if (k.userId != null) map[k.id] = k.userId
  }
  return map
})

function userDisplayName(u) {
  if (!u) return null
  return u.nickname || u.username || `用户 #${u.id}`
}

function userRoleLabel(u) {
  const r = u?.roleType
  if (r === 1) return '管理员'
  if (r === 2) return '教师'
  return '学生'
}

// ---- Data loader ----
async function loadConsoleData() {
  if (!authStore.token) return
  loading.value = true
  loadError.value = ''
  try {
    // 最多拉 500 条日志作为"近 30 天"数据源；超过这个量再做服务端聚合才合适。
    // users 用来把 apiKey.userId → 用户昵称，失败不致命（会退回 userId 显示）。
    const [keysRes, logsRes, usersRes] = await Promise.all([
      fetchOpenApiKeys(authStore.token).catch(() => []),
      fetchOpenApiKeyLogs(authStore.token, { page: 1, pageSize: 500 }).catch(() => ({ data: [] })),
      fetchAdminUsers(authStore.token, { page: 1, pageSize: 200 }).catch(() => ({ data: [] }))
    ])
    keys.value = Array.isArray(keysRes) ? keysRes : (keysRes?.data || [])
    rawLogs.value = Array.isArray(logsRes?.data) ? logsRes.data : []
    users.value = Array.isArray(usersRes?.data) ? usersRes.data : (usersRes?.data?.records || [])
  } catch (e) {
    loadError.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

// ---- Refresh / export ----
const refreshing = ref(false)
async function refreshUsage() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await loadConsoleData()
  } finally {
    // 动画至少保留 400ms，避免按钮一闪而过造成"没响应"错觉
    setTimeout(() => { refreshing.value = false }, 400)
  }
}
function exportUsage() {
  // 简版 CSV 导出：按当前日期范围导出每日调用 / 错误 / P95
  const rows = [['date', 'calls', 'errors', 'p95_latency_ms']]
  for (const d of aggregatedDaily.value) {
    rows.push([d.date, d.calls, d.errors, d.p95])
  }
  const csv = rows.map((r) => r.join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `api-usage-${dateRange.value}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ---- Usage dimension tab group ----
// 默认"按用户"：管理员打开控制台第一眼想看的往往是"谁在消耗配额"，
// 不是"哪个端点忙"（后者数据分析同学用更多）。
const usageDimension = ref('user')

// ---- Aggregation helpers ----
// 把 Date 格式化成 "YYYY-MM-DD" 用作桶 key
function fmtDay(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

// P95：对数组做升序排序取第 95 位
function p95(arr) {
  if (!arr.length) return 0
  const sorted = [...arr].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))
  return Math.round(sorted[idx])
}

// 关键聚合：把原始 logs 按日期分桶，每桶输出 calls / errors / latencies / p95
// 如果当前范围内没日志也会返回 numDays 个零值桶，便于 sparkline 不抖动
function bucketLogsByDay(logs, numDays) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const buckets = []
  for (let i = numDays - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    buckets.push({ date: fmtDay(d), calls: 0, errors: 0, latencies: [] })
  }
  const map = Object.fromEntries(buckets.map((b) => [b.date, b]))
  for (const log of logs) {
    if (!log?.createdAt) continue
    const d = new Date(log.createdAt)
    if (Number.isNaN(d.getTime())) continue
    const key = fmtDay(d)
    const bucket = map[key]
    if (!bucket) continue
    bucket.calls++
    const code = Number(log.responseCode || 0)
    if (code >= 400) bucket.errors++
    const lat = Number(log.responseTime)
    if (Number.isFinite(lat)) bucket.latencies.push(lat)
  }
  // 收尾：把每桶的 p95 / avg 落出来，方便后续直接用
  return buckets.map((b) => ({
    ...b,
    p95: p95(b.latencies),
    avg: b.latencies.length ? Math.round(b.latencies.reduce((a, c) => a + c, 0) / b.latencies.length) : 0
  }))
}

// ---- Aggregated KPI series ----
const aggregatedDaily = computed(() => bucketLogsByDay(rawLogs.value, currentDays.value))

const totalCalls14d = computed(() => aggregatedDaily.value.reduce((acc, d) => acc + d.calls, 0))
const totalErrors14d = computed(() => aggregatedDaily.value.reduce((acc, d) => acc + d.errors, 0))
const errorRatePct = computed(() => {
  if (!totalCalls14d.value) return 0
  return Math.round((totalErrors14d.value / totalCalls14d.value) * 10000) / 100
})

// 对比：当前范围内后一半 vs 前一半的调用量差
const callsChangePct = computed(() => {
  const days = aggregatedDaily.value
  if (days.length < 4) return null
  const half = Math.floor(days.length / 2)
  const lastHalf = days.slice(-half).reduce((a, d) => a + d.calls, 0)
  const prevHalf = days.slice(0, half).reduce((a, d) => a + d.calls, 0)
  if (!prevHalf) return null
  const pct = ((lastHalf - prevHalf) / prevHalf) * 100
  return { pct: Math.round(pct * 10) / 10, dir: pct >= 0 ? 'up' : 'down' }
})

// 延迟系列：每天 p95
const latencyDaily = computed(() =>
  aggregatedDaily.value.map((d) => ({ date: d.date, value: d.p95 }))
)
const latencyP95 = computed(() => {
  // 用整范围内的 latency 全集算 p95 更准；按天再取 max 会把平滑值放大
  const allLatencies = []
  for (const d of aggregatedDaily.value) allLatencies.push(...d.latencies)
  return p95(allLatencies)
})

// 活跃 Key 系列：每天有过调用的 key 数量（真实反映活跃度）
const activeKeyDaily = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const numDays = currentDays.value
  const buckets = []
  for (let i = numDays - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    buckets.push({ date: fmtDay(d), set: new Set() })
  }
  const map = Object.fromEntries(buckets.map((b) => [b.date, b]))
  for (const log of rawLogs.value) {
    if (!log?.createdAt || !log?.apiKeyId) continue
    const d = new Date(log.createdAt)
    const key = fmtDay(d)
    const b = map[key]
    if (b) b.set.add(log.apiKeyId)
  }
  return buckets.map((b) => ({ date: b.date, value: b.set.size }))
})

// ---- Chart theme (shared palette for all ECharts instances on page) ----
// Factored out so sparklines, KPI charts and the legacy 7-day chart all
// react to theme changes via a single computed.
const chartTheme = computed(() => {
  const isDark = themeStore.isDark
  return {
    isDark,
    tooltipBg: isDark ? '#1d212c' : '#ffffff',
    tooltipBorder: isDark ? 'rgba(175,198,255,0.16)' : 'rgba(24,27,35,0.08)',
    tooltipText: isDark ? '#eff0fc' : '#181b23',
    axisLine: isDark ? 'rgba(175,198,255,0.16)' : 'rgba(24,27,35,0.08)',
    splitLine: isDark ? 'rgba(175,198,255,0.08)' : 'rgba(24,27,35,0.05)',
    axisLabel: isDark ? '#b1b8cd' : '#727786',
    accent: isDark ? '#afc6ff' : '#0057c2',
    areaTop: isDark ? 'rgba(175, 198, 255, 0.32)' : 'rgba(0, 87, 194, 0.22)',
    areaBottom: isDark ? 'rgba(175, 198, 255, 0)' : 'rgba(0, 87, 194, 0)',
    danger: isDark ? '#ff9b91' : '#b23b2e',
    dangerAreaTop: isDark ? 'rgba(255, 155, 145, 0.28)' : 'rgba(178, 59, 46, 0.18)',
    dangerAreaBottom: isDark ? 'rgba(255, 155, 145, 0)' : 'rgba(178, 59, 46, 0)'
  }
})

// Factory: build a minimal sparkline option from a 14-day series.
// `series` items can be either `{calls}` / `{value}` shape.
// `tone` = 'accent' | 'danger' picks the color swatch from chartTheme.
function buildSparkOption(series, tone = 'accent') {
  const t = chartTheme.value
  const dates = series.map((d) => d.date)
  const data = series.map((d) => (typeof d.value === 'number' ? d.value : d.calls))
  const line = tone === 'danger' ? t.danger : t.accent
  const areaTop = tone === 'danger' ? t.dangerAreaTop : t.areaTop
  const areaBottom = tone === 'danger' ? t.dangerAreaBottom : t.areaBottom
  return {
    grid: { top: 4, right: 2, bottom: 4, left: 2, containLabel: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      borderWidth: 1,
      textStyle: { color: t.tooltipText, fontSize: 11 },
      padding: [4, 8],
      axisPointer: { lineStyle: { color: t.axisLine } }
    },
    xAxis: {
      type: 'category',
      data: dates,
      show: false,
      boundaryGap: false
    },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line',
        data,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1.6, color: line },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: areaTop },
              { offset: 1, color: areaBottom }
            ]
          }
        }
      }
    ]
  }
}

// 4 KPI sparkline options
const sparkCallsOption = computed(() => buildSparkOption(aggregatedDaily.value, 'accent'))
const sparkErrorsOption = computed(() =>
  buildSparkOption(aggregatedDaily.value.map((d) => ({ date: d.date, value: d.errors })), 'danger')
)
const sparkLatencyOption = computed(() => buildSparkOption(latencyDaily.value, 'accent'))
const sparkKeysOption = computed(() => buildSparkOption(activeKeyDaily.value, 'accent'))

// Per-card sparkline factory — same shape, closes over `daily`.
function cardSparkOption(daily) {
  return buildSparkOption(daily, 'accent')
}

// ---- Create-key modal ----
const showCreateModal = ref(false)
const creatingName = ref('')
const creatingScope = ref('read-only')
const creatingSubmitting = ref(false)
// 后端创建成功后**一次性**回传完整 apiKey，关闭弹窗后无法再看；
// 把返回的整个对象存起来展示
const revealedKey = ref(null)
const copiedField = ref('')

function openCreateModal() {
  creatingName.value = ''
  creatingScope.value = 'read-only'
  revealedKey.value = null
  showCreateModal.value = true
}
function closeCreateModal() {
  showCreateModal.value = false
  revealedKey.value = null
}

async function confirmCreateKey() {
  const name = creatingName.value.trim()
  if (!name || creatingSubmitting.value) return
  creatingSubmitting.value = true
  try {
    const created = await createOpenApiKey(authStore.token, {
      keyName: name,
      permissionProfile: creatingScope.value === 'full' ? 'full' : 'basic',
      tenantScope: 'public',
      rateLimitQps: 10,
      dailyQuota: 1000
    })
    // 创建成功后展示"一次性明文 Key"的 modal，同时刷新列表
    revealedKey.value = {
      name: created.keyName || name,
      secret: created.apiKey || '',
      prefix: created.apiKey ? `${created.apiKey.slice(0, 14)}…` : ''
    }
    await loadConsoleData()
    success('API Key 已创建，请立即复制保存')
  } catch (e) {
    toastError('创建失败：' + normalizeError(e))
  } finally {
    creatingSubmitting.value = false
  }
}

// ---- Revoke-key confirm modal ----
// 后端 toggle 是"切换"语义：传 active=false 等同于撤销。
const revokingKey = ref(null)
const revokingSubmitting = ref(false)

function revokeKey(id) {
  const k = keys.value.find((x) => x.id === id)
  if (!k) return
  revokingKey.value = k
}
function closeRevokeModal() {
  revokingKey.value = null
}
async function confirmRevoke() {
  if (!revokingKey.value || revokingSubmitting.value) return
  revokingSubmitting.value = true
  try {
    await toggleOpenApiKey(authStore.token, revokingKey.value.id, false)
    success('已撤销')
    revokingKey.value = null
    await loadConsoleData()
  } catch (e) {
    toastError('撤销失败：' + normalizeError(e))
  } finally {
    revokingSubmitting.value = false
  }
}

async function copyText(text, fieldKey) {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copiedField.value = fieldKey
    setTimeout(() => {
      if (copiedField.value === fieldKey) copiedField.value = ''
    }, 1500)
  } catch {
    // ignore
  }
}

// ---- 分组卡片：按端点 / 按 Key ----
// 对 rawLogs 做 group-by 然后再 bucketLogsByDay，同一聚合逻辑复用
const endpointCards = computed(() => {
  const groups = {}
  for (const log of rawLogs.value) {
    const ep = log?.endpoint || 'unknown'
    if (!groups[ep]) groups[ep] = []
    groups[ep].push(log)
  }
  const numDays = currentDays.value
  return Object.entries(groups)
    .map(([endpoint, rows]) => {
      const daily = bucketLogsByDay(rows, numDays)
      return {
        id: endpoint,
        label: endpoint,
        endpoints: [endpoint],
        totalRequests: rows.length,
        daily
      }
    })
    .sort((a, b) => b.totalRequests - a.totalRequests)
    .slice(0, 9)  // 最多显示 9 张卡，对齐 OpenAI usage 页的 grid
})

// 按用户聚合：对每条日志 apiKeyId → userId → user，再按 userId 分组。
// 同一个用户的多把 Key 汇总到一起——管理员最关心的是"谁在用"，而不是"哪把 Key 在用"。
const userCards = computed(() => {
  const groups = {}
  const keyLookup = userIdByKeyId.value
  for (const log of rawLogs.value) {
    const kid = log?.apiKeyId
    if (kid == null) continue
    const uid = keyLookup[kid]
    if (uid == null) continue
    if (!groups[uid]) groups[uid] = []
    groups[uid].push(log)
  }
  const numDays = currentDays.value
  return Object.entries(groups)
    .map(([uid, rows]) => {
      const user = userById.value[uid]
      const daily = bucketLogsByDay(rows, numDays)
      // 该用户下用过几把 Key
      const distinctKeys = new Set(rows.map((r) => r.apiKeyId)).size
      return {
        id: uid,
        label: userDisplayName(user) || `用户 #${uid}`,
        role: userRoleLabel(user),
        keyCount: distinctKeys,
        totalRequests: rows.length,
        daily
      }
    })
    .filter((u) => u.totalRequests > 0)
    .sort((a, b) => b.totalRequests - a.totalRequests)
})

const currentCards = computed(() =>
  usageDimension.value === 'endpoint' ? endpointCards.value : userCards.value
)

// ---- Recent activity (下方活动表：对齐 OpenAI usage 页的 activity list) ----
const recentLogs = computed(() => rawLogs.value.slice(0, 15))

function fmtTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function fmtDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value).slice(0, 10)
  return fmtDay(d)
}

function keyPrefix(apiKey) {
  if (!apiKey) return '—'
  return `${apiKey.slice(0, 12)}…`
}

function formatNumber(n) {
  return Number(n).toLocaleString('zh-CN')
}
function goToDocs() {
  router.push('/openapi/intro')
}

// ---- Lifecycle ----
onMounted(() => {
  window.addEventListener('click', handleFilterOutsideClick)
  window.addEventListener('keydown', handleFilterKey)
  loadConsoleData()
})
onBeforeUnmount(() => {
  window.removeEventListener('click', handleFilterOutsideClick)
  window.removeEventListener('keydown', handleFilterKey)
  if (filterCloseTimer) clearTimeout(filterCloseTimer)
})
</script>

<template>
  <div class="console-shell">
    <header class="console-top">
      <div class="console-top-left">
        <button class="console-back" type="button" @click="goToDocs">
          <ArrowLeft :size="14" :stroke-width="1.8" />
          返回文档
        </button>
        <div class="console-title-block">
          <h1 class="console-title">API 控制台</h1>
        </div>
      </div>
      <div class="console-top-right">
        <button class="console-ghost-btn" type="button" @click="goToDocs" title="查看 API 文档">
          <BookOpen :size="14" :stroke-width="1.8" />
          <span class="console-ghost-label">文档</span>
        </button>
        <button
          class="console-ghost-btn"
          type="button"
          :class="{ 'is-spinning': refreshing }"
          @click="refreshUsage"
          title="刷新"
        >
          <RefreshCw :size="14" :stroke-width="1.8" />
          <span class="console-ghost-label">刷新</span>
        </button>
        <button class="console-ghost-btn" type="button" @click="exportUsage" title="导出">
          <Download :size="14" :stroke-width="1.8" />
          <span class="console-ghost-label">导出</span>
        </button>
        <button class="console-primary-btn" type="button" @click="openCreateModal">
          <Plus :size="14" :stroke-width="2" />
          创建新 Key
        </button>
      </div>
    </header>

    <!-- 加载 / 错误横幅 -->
    <div v-if="loadError" class="console-error">
      <AlertTriangle :size="14" :stroke-width="2" /> {{ loadError }}
    </div>

    <!-- Filter chip row（时间范围）。项目维度先不做，等后端给 tenant/project 列再加。 -->
    <div class="console-chip-row">
      <div
        class="console-chip-wrap"
        :class="{ open: openFilterKey === 'dateRange' }"
        @mouseenter="openFilter('dateRange')"
        @mouseleave="scheduleCloseFilter"
      >
        <div
          class="console-chip"
          :class="{ active: isDateRangeActive }"
          tabindex="0"
          role="button"
          :aria-expanded="openFilterKey === 'dateRange'"
          @focus="openFilter('dateRange')"
          @blur="scheduleCloseFilter"
        >
          <CalendarDays :size="13" :stroke-width="1.9" class="console-chip-icon" />
          <span class="console-chip-label">{{ dateRangeChipLabel }}</span>
          <ChevronDown :size="13" :stroke-width="1.8" class="console-chip-caret" />
        </div>
        <div v-if="openFilterKey === 'dateRange'" class="console-chip-panel" role="menu">
          <button
            v-for="opt in dateRangeOptions"
            :key="opt.value"
            class="console-chip-option"
            :class="{ active: dateRange === opt.value }"
            type="button"
            role="menuitem"
            @click="pickDateRange(opt.value)"
          >{{ opt.label }}</button>
        </div>
      </div>
    </div>

    <!-- KPI strip: big-number + sparkline (OpenAI Usage-style) -->
    <section class="console-metrics">
      <div class="metric">
        <div class="metric-head">
          <span class="metric-label">总调用量</span>
          <span
            v-if="callsChangePct"
            :class="`metric-delta metric-delta--${callsChangePct.dir}`"
          >
            {{ callsChangePct.dir === 'up' ? '↑' : '↓' }} {{ Math.abs(callsChangePct.pct) }}%
          </span>
        </div>
        <div class="metric-body">
          <div class="metric-body-left">
            <span class="metric-value">
              <strong>{{ formatNumber(totalCalls14d) }}</strong>
            </span>
            <span class="metric-sub">{{ dateRangeChipLabel }}总调用</span>
          </div>
          <div class="metric-spark">
            <VChart :option="sparkCallsOption" autoresize />
          </div>
        </div>
      </div>

      <div class="metric">
        <div class="metric-head">
          <span class="metric-label">错误率</span>
        </div>
        <div class="metric-body">
          <div class="metric-body-left">
            <span class="metric-value">
              <strong>{{ errorRatePct }}<span class="metric-suffix-pct">%</span></strong>
            </span>
            <span class="metric-sub">{{ formatNumber(totalErrors14d) }} 次错误 / {{ dateRangeChipLabel }}</span>
          </div>
          <div class="metric-spark">
            <VChart :option="sparkErrorsOption" autoresize />
          </div>
        </div>
      </div>

      <div class="metric">
        <div class="metric-head">
          <span class="metric-label">P95 延迟</span>
        </div>
        <div class="metric-body">
          <div class="metric-body-left">
            <span class="metric-value">
              <strong>{{ latencyP95 }}</strong>
              <span class="metric-suffix">ms</span>
            </span>
            <span class="metric-sub">{{ dateRangeChipLabel }} · P95</span>
          </div>
          <div class="metric-spark">
            <VChart :option="sparkLatencyOption" autoresize />
          </div>
        </div>
      </div>

      <div class="metric">
        <div class="metric-head">
          <span class="metric-label">活跃 Key</span>
        </div>
        <div class="metric-body">
          <div class="metric-body-left">
            <span class="metric-value">
              <strong>{{ activeKeyCount }}</strong>
              <span class="metric-suffix">/ {{ totalKeyCount }}</span>
            </span>
            <span class="metric-sub">共 {{ totalKeyCount }} 个（{{ revokedKeyCount }} 已撤销）</span>
          </div>
          <div class="metric-spark">
            <VChart :option="sparkKeysOption" autoresize />
          </div>
        </div>
      </div>
    </section>

    <!-- Segmented tab group -->
    <div class="console-tabs" role="tablist" aria-label="用量分组">
      <button
        class="console-tab"
        :class="{ active: usageDimension === 'user' }"
        role="tab"
        :aria-selected="usageDimension === 'user'"
        type="button"
        @click="usageDimension = 'user'"
      >按用户</button>
      <button
        class="console-tab"
        :class="{ active: usageDimension === 'endpoint' }"
        role="tab"
        :aria-selected="usageDimension === 'endpoint'"
        type="button"
        @click="usageDimension = 'endpoint'"
      >按端点</button>
    </div>

    <Transition name="console-section" mode="out-in">
      <div :key="usageDimension" class="console-usage-stage">
        <!-- Card grid — one small card per dimension entry -->
        <section v-if="currentCards.length" class="console-usage-grid">
          <article
            v-for="card in currentCards"
            :key="card.id"
            class="console-usage-card"
          >
            <header class="console-usage-card-head">
              <span class="console-usage-card-label">{{ card.label }}</span>
              <span class="console-usage-card-total">{{ formatNumber(card.totalRequests) }}</span>
            </header>
            <div class="console-usage-card-sub">
              <template v-if="usageDimension === 'endpoint'">
                调用次数 · {{ dateRangeChipLabel }}
              </template>
              <template v-else>
                {{ card.role }} · {{ card.keyCount }} 把 Key
              </template>
            </div>
            <div class="console-usage-card-chart">
              <VChart :option="cardSparkOption(card.daily)" autoresize />
            </div>
            <footer class="console-usage-card-foot">
              <span>{{ card.daily[0]?.date }}</span>
              <span>{{ card.daily[card.daily.length - 1]?.date }}</span>
            </footer>
          </article>
        </section>

        <section v-else class="console-empty-grid">
          <span class="empty-icon">
            <Inbox :size="22" :stroke-width="1.6" />
          </span>
          <p>当前筛选条件下没有用量数据。</p>
          <span>尝试调整时间范围或切换分组。</span>
        </section>
      </div>
    </Transition>

    <section class="console-panel">
      <header class="console-panel-head">
        <div>
          <h2 class="console-panel-title">API Keys</h2>
          <p class="console-panel-sub">按环境或应用分别创建独立 Key。撤销后立即失效，不可恢复。</p>
        </div>
      </header>

      <div class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>Key</th>
              <th>限流</th>
              <th>创建时间</th>
              <th>最近使用</th>
              <th>状态</th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in keys" :key="k.id" :class="{ 'is-revoked': k.isActive !== 1 }">
              <td class="col-name" data-label="名称">
                <span class="col-name-icon"><KeyRound :size="13" :stroke-width="1.8" /></span>
                {{ k.keyName || `Key #${k.id}` }}
              </td>
              <td class="col-prefix" data-label="Key">
                <code>{{ keyPrefix(k.apiKey) }}</code>
                <button
                  class="inline-icon-btn"
                  type="button"
                  :title="copiedField === k.id ? '已复制' : '复制'"
                  @click="copyText(k.apiKey, k.id)"
                >
                  <Copy :size="12" :stroke-width="1.7" />
                </button>
              </td>
              <td data-label="限流">
                <span class="pill pill-muted">
                  {{ k.rateLimitQps || 10 }} QPS · {{ k.dailyQuota || 1000 }}/日
                </span>
              </td>
              <td data-label="创建时间">{{ fmtDate(k.createdAt) }}</td>
              <td data-label="最近使用">{{ k.lastUsedAt ? fmtTime(k.lastUsedAt) : '—' }}</td>
              <td data-label="状态">
                <span class="pill" :class="k.isActive === 1 ? 'pill-success' : 'pill-danger'">
                  {{ k.isActive === 1 ? '启用' : '已撤销' }}
                </span>
              </td>
              <td class="col-actions" data-label="操作">
                <button
                  v-if="k.isActive === 1"
                  class="row-action danger"
                  type="button"
                  @click="revokeKey(k.id)"
                >
                  <Ban :size="12" :stroke-width="1.7" />
                  撤销
                </button>
                <span v-else class="row-muted">—</span>
              </td>
            </tr>
            <tr v-if="keys.length === 0 && !loading">
              <td colspan="7" class="console-empty">
                还没有 Key。点击右上角「创建新 Key」开始。
              </td>
            </tr>
            <tr v-if="loading && !keys.length">
              <td colspan="7" class="console-empty">正在加载…</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- —— 最近活动：对应 OpenAI Usage 的 activity 区 —— -->
    <section class="console-panel">
      <header class="console-panel-head">
        <div>
          <h2 class="console-panel-title">最近活动</h2>
          <p class="console-panel-sub">按时间倒序展示最近的 API 调用，点击右上刷新拿新数据。</p>
        </div>
      </header>

      <div v-if="!recentLogs.length" class="console-activity-empty">
        <Inbox :size="22" :stroke-width="1.6" />
        <p>还没有调用记录。</p>
        <span>创建 Key 并开始调用后，活动会实时出现在这里。</span>
      </div>
      <div v-else class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>端点</th>
              <th>方法</th>
              <th>状态</th>
              <th>耗时</th>
              <th>Key</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in recentLogs" :key="log.id" :class="{ 'is-revoked': Number(log.responseCode || 0) >= 400 }">
              <td>{{ fmtTime(log.createdAt) }}</td>
              <td>{{ userDisplayName(userById[userIdByKeyId[log.apiKeyId]]) || '—' }}</td>
              <td class="col-endpoint"><code>{{ log.endpoint || '—' }}</code></td>
              <td>
                <span class="pill pill-muted">{{ log.method || 'GET' }}</span>
              </td>
              <td>
                <span
                  class="pill"
                  :class="Number(log.responseCode || 0) >= 400 ? 'pill-danger' : 'pill-success'"
                >
                  {{ log.responseCode || '—' }}
                </span>
              </td>
              <td><span class="metric-sub">{{ log.responseTime ?? '—' }} ms</span></td>
              <td>{{ keyNameMap[log.apiKeyId] || `#${log.apiKeyId ?? '—'}` }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- Create-key modal -->
    <div v-if="showCreateModal" class="console-modal-mask" @click.self="closeCreateModal">
      <div class="console-modal">
        <template v-if="!revealedKey">
          <h3 class="console-modal-title">创建新的 API Key</h3>
          <p class="console-modal-lead">为本 Key 填一个便于辨认的名称，并选择对应的访问权限。</p>

          <label class="console-field">
            <span class="console-field-label">名称</span>
            <input
              v-model="creatingName"
              class="console-input"
              type="text"
              placeholder="如：教研数据看板"
              maxlength="40"
              autofocus
            />
          </label>

          <label class="console-field">
            <span class="console-field-label">权限</span>
            <select v-model="creatingScope" class="console-input">
              <option value="read-only">只读（推荐）</option>
              <option value="full">完整读写</option>
            </select>
          </label>

          <div class="console-modal-actions">
            <button class="console-secondary-btn" type="button" @click="closeCreateModal">取消</button>
            <button
              class="console-primary-btn"
              type="button"
              :disabled="!creatingName.trim() || creatingSubmitting"
              @click="confirmCreateKey"
            >
              {{ creatingSubmitting ? '创建中…' : '创建' }}
            </button>
          </div>
        </template>

        <template v-else>
          <h3 class="console-modal-title">
            <CheckCircle2 :size="18" :stroke-width="2" style="color:#1e8a5b; vertical-align: -3px;" />
            Key 已生成
          </h3>
          <p class="console-modal-lead">
            这是「<strong>{{ revealedKey.name }}</strong>」的完整密钥。关闭本窗口后将无法再次查看，请立即保存到安全的地方。
          </p>

          <div class="console-secret-box">
            <code class="console-secret">{{ revealedKey.secret }}</code>
            <button
              class="inline-icon-btn on-dark"
              type="button"
              :title="copiedField === 'revealed' ? '已复制' : '复制完整 Key'"
              @click="copyText(revealedKey.secret, 'revealed')"
            >
              <Copy :size="14" :stroke-width="1.8" />
            </button>
          </div>

          <p class="console-secret-hint">
            请求时用 HTTPS 头 <code>Authorization: Bearer &lt;your-key&gt;</code> 携带。Key 泄露请回到列表立即撤销并创建新的。
          </p>

          <div class="console-modal-actions">
            <button class="console-primary-btn" type="button" @click="closeCreateModal">我已保存</button>
          </div>
        </template>
      </div>
    </div>

    <!-- Revoke-key confirm modal -->
    <div v-if="revokingKey" class="console-modal-mask" @click.self="closeRevokeModal">
      <div class="console-modal is-danger" role="alertdialog" aria-labelledby="revoke-title">
        <div class="revoke-icon-wrap">
          <AlertTriangle :size="22" :stroke-width="2" />
        </div>
        <h3 id="revoke-title" class="console-modal-title">
          撤销「{{ revokingKey.keyName || `Key #${revokingKey.id}` }}」？
        </h3>
        <p class="console-modal-lead">
          撤销后，依赖此 Key 的应用将立刻收到 <code>401 Unauthorized</code>。撤销之后在上方列表仍可看到，
          但不会再有调用通过。如果只是临时停用，建议先在调用方替换为新 Key，再回来撤销旧的。
        </p>

        <div class="revoke-detail">
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">Key</span>
            <code>{{ keyPrefix(revokingKey.apiKey) }}</code>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">限流</span>
            <span>{{ revokingKey.rateLimitQps || 10 }} QPS · {{ revokingKey.dailyQuota || 1000 }} 次 / 日</span>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">最近使用</span>
            <span>{{ revokingKey.lastUsedAt ? fmtTime(revokingKey.lastUsedAt) : '从未使用' }}</span>
          </div>
        </div>

        <div class="console-modal-actions">
          <button class="console-secondary-btn" type="button" @click="closeRevokeModal">取消</button>
          <button
            class="console-danger-btn"
            type="button"
            :disabled="revokingSubmitting"
            @click="confirmRevoke"
          >
            <Ban :size="13" :stroke-width="1.9" />
            {{ revokingSubmitting ? '撤销中…' : '确认撤销' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.console-shell {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px 48px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- Top header ---------- */
.console-top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 8px;
  border-bottom: 1px solid var(--c-border-glass);
  padding-bottom: 20px;
}
.console-top-left {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.console-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px 3px 6px;
  border: none;
  background: transparent;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  border-radius: 6px;
  align-self: flex-start;
  transition: background-color 140ms, color 140ms;
}
.console-back:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.console-title-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.console-title {
  font-family: var(--font-serif);
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
  line-height: 1.15;
  margin: 0;
}
.console-top-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- Buttons ---------- */
.console-primary-btn,
.console-secondary-btn,
.console-ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 9px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms, border-color 140ms, color 140ms;
  line-height: 1;
}
.console-primary-btn {
  border: none;
  background: var(--c-accent-primary);
  color: #ffffff;
}
.console-primary-btn:hover {
  background: var(--c-accent-primary-hover);
}
/* In dark mode, --c-accent-primary is a light lavender (#afc6ff), so
   white label text would wash out. Swap to the dark base text on the
   same button for a readable "pill on light-blue" look. */
:global([data-theme="dark"]) .console-primary-btn {
  color: #0f1420;
}
:global([data-theme="dark"]) .console-primary-btn:hover {
  color: #0f1420;
}
.console-primary-btn:disabled {
  background: var(--c-text-faint);
  cursor: not-allowed;
}
.console-secondary-btn {
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
}
.console-secondary-btn:hover {
  background: var(--c-bg-surface-hover);
  border-color: var(--c-text-faint);
  color: var(--c-text-primary);
}
.console-ghost-btn {
  padding: 7px 10px;
  border: 1px solid var(--c-border-glass);
  background: transparent;
  color: var(--c-text-secondary);
}
.console-ghost-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  border-color: var(--c-text-faint);
}
.console-ghost-label {
  font-size: 12.5px;
}
.console-ghost-btn.is-spinning svg {
  animation: console-spin 700ms linear;
}
@keyframes console-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.inline-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  transition: background-color 140ms, color 140ms, border-color 140ms;
}
.inline-icon-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  border-color: var(--c-border-glass);
}
/* The secret-box (`.console-secret-box`) is intentionally dark in both
   themes — it's a "code on a terminal" aesthetic — so the `.on-dark`
   icon variant stays with explicit white-on-dark values rather than
   tokenized theme colors. */
.inline-icon-btn.on-dark {
  color: rgba(255, 255, 255, 0.72);
}
.inline-icon-btn.on-dark:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.16);
}

/* ---------- Filter chip row ---------- */
.console-chip-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.console-chip-wrap {
  position: relative;
  display: inline-flex;
}
.console-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 500;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease, border-color 140ms ease;
  outline: none;
}
.console-chip:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.console-chip:focus-visible {
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}
.console-chip.active {
  color: var(--c-accent-primary);
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  font-weight: 600;
}
.console-chip-icon {
  opacity: 0.75;
}
.console-chip.active .console-chip-icon {
  opacity: 1;
  color: var(--c-accent-primary);
}
.console-chip-label {
  line-height: 1.2;
}
.console-chip-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 87, 194, 0.18);
  color: var(--c-accent-primary);
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.console-chip-clear:hover {
  background: var(--c-accent-primary);
  color: #ffffff;
}
:global([data-theme="dark"]) .console-chip-clear:hover {
  color: #0f1420;
}
.console-chip-caret {
  transition: transform 180ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
  color: currentColor;
  opacity: 0.7;
}
.console-chip-wrap.open .console-chip-caret {
  transform: rotate(180deg);
  opacity: 1;
}
.console-chip-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  min-width: 180px;
  padding: 6px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  box-shadow: var(--shadow-card-raised);
  display: flex;
  flex-direction: column;
  gap: 2px;
  animation: console-chip-in 140ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}
@keyframes console-chip-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
.console-chip-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  text-align: left;
  padding: 7px 10px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease;
}
.console-chip-option:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-accent-primary);
}
.console-chip-option.active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.console-chip-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

/* ---------- Metric strip (KPI + sparkline) ---------- */
.console-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1px;
  background: var(--c-border-glass);
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  overflow: hidden;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px 12px;
  background: var(--c-bg-base-elevated);
}
.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 16px;
}
.metric-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.metric-body-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.metric-spark {
  flex: 0 0 88px;
  width: 88px;
  height: 44px;
}
.metric-spark .echarts {
  width: 100%;
  height: 100%;
}
.metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.metric-value {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  font-family: var(--font-serif);
  color: var(--c-text-primary);
  line-height: 1.1;
}
.metric-value strong {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
  font-feature-settings: 'tnum' 1;
  font-variant-numeric: tabular-nums;
}
.metric-suffix {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
  font-weight: 500;
}
.metric-suffix-pct {
  font-size: 14px;
  color: var(--c-text-muted);
  font-weight: 600;
  margin-left: 2px;
}
.metric-delta {
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 999px;
}
.metric-delta--up {
  color: #1e8a5b;
  background: rgba(30, 138, 91, 0.1);
}
.metric-delta--down {
  color: #b23b2e;
  background: rgba(178, 59, 46, 0.1);
}
.metric-sub {
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
}

/* ---------- Segmented tabs ---------- */
.console-tabs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  align-self: flex-start;
}
.console-tab {
  padding: 6px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease, border-color 140ms ease;
}
.console-tab:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.console-tab.active {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  font-weight: 600;
}

.console-usage-stage {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
}

.console-section-enter-active,
.console-section-leave-active {
  transition:
    opacity 220ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 280ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 280ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform, filter;
  transform-origin: top left;
}

.console-section-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
  filter: blur(10px);
}

.console-section-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.992);
  filter: blur(8px);
}

.console-section-enter-to,
.console-section-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

/* ---------- Usage card grid ---------- */
.console-usage-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.console-usage-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  box-shadow: var(--shadow-card-quiet);
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}
.console-usage-card:hover {
  border-color: var(--c-border-glass-hover);
  box-shadow: var(--shadow-card-soft);
}
.console-usage-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.console-usage-card-label {
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-primary);
}
.console-usage-card-total {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
  font-feature-settings: 'tnum' 1;
  font-variant-numeric: tabular-nums;
}
.console-usage-card-sub {
  font-family: var(--font-sans);
  font-size: 11px;
  color: var(--c-text-muted);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.console-usage-card-sub code {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}
.console-usage-card-chart {
  width: 100%;
  height: 60px;
  margin-top: 2px;
}
.console-usage-card-chart .echarts {
  width: 100%;
  height: 100%;
}
.console-usage-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--font-sans);
  font-size: 10.5px;
  color: var(--c-text-faint);
}

/* ---------- Error banner ---------- */
.console-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(178, 59, 46, 0.08);
  border: 1px solid rgba(178, 59, 46, 0.22);
  color: #b23b2e;
  font-family: var(--font-sans);
  font-size: 13px;
}

:global([data-theme="dark"]) .console-error {
  background: rgba(178, 59, 46, 0.16);
  color: #ffb4a6;
  border-color: rgba(255, 180, 166, 0.24);
}

/* ---------- Recent activity empty state ---------- */
.console-activity-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 40px 20px;
  color: var(--c-text-muted);
  text-align: center;
}

.console-activity-empty p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-text-secondary);
}

.console-activity-empty span {
  font-size: 12px;
  color: var(--c-text-muted);
}

.col-endpoint code {
  font-size: 12px;
  color: var(--c-text-primary);
  background: var(--c-bg-surface-hover);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--c-border-glass);
}

/* ---------- Empty state ---------- */
.console-empty-grid {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 40px 20px;
  background: var(--c-bg-base-elevated);
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  text-align: center;
}
.console-empty-grid .empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  margin-bottom: 4px;
}
.console-empty-grid p {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-secondary);
  margin: 0;
}
.console-empty-grid span:not(.empty-icon) {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .console-section-enter-active,
  .console-section-leave-active {
    transition: opacity 120ms ease;
  }

  .console-section-enter-from,
  .console-section-leave-to,
  .console-section-enter-to,
  .console-section-leave-from {
    transform: none;
    filter: none;
  }
}

/* ---------- Panels (card) ---------- */
.console-panel {
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-card-quiet);
}
.console-panel-head {
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}
.console-panel-title {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text-primary);
  line-height: 1.25;
  margin: 0 0 2px;
}
.console-panel-sub {
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-muted);
  line-height: 1.5;
  margin: 0;
}

/* ---------- Table ---------- */
.console-table-wrap {
  overflow-x: auto;
}
.console-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-sans);
  font-size: 13px;
}
.console-table thead th {
  padding: 10px 18px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  background: var(--c-bg-surface-hover);
  border-bottom: 1px solid var(--c-border-glass);
  text-align: left;
}
.console-table tbody td {
  padding: 12px 18px;
  border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  vertical-align: middle;
}
.console-table tbody tr:last-child td { border-bottom: none; }
.console-table tbody tr.is-revoked td { color: var(--c-text-faint); }
.col-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.col-name-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.col-prefix {
  display: flex;
  align-items: center;
  gap: 6px;
}
.col-prefix code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.pill {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.pill-muted { background: var(--c-bg-surface-hover); color: var(--c-text-secondary); }
.pill-success { background: rgba(30, 138, 91, 0.12); color: #1e8a5b; }
.pill-danger { background: rgba(178, 59, 46, 0.1); color: #b23b2e; }

.col-actions { text-align: right; }
.row-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 6px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 140ms, color 140ms, background-color 140ms;
}
.row-action.danger:hover {
  color: #b23b2e;
  border-color: #e8b7b0;
  background: rgba(178, 59, 46, 0.08);
}
.row-muted { color: var(--c-text-faint); font-size: 13px; }
.console-empty {
  text-align: center;
  padding: 32px 20px;
  color: var(--c-text-muted);
}

/* ---------- Chart ---------- */
.console-chart-wrap {
  padding: 8px 12px 12px;
}
.console-chart {
  width: 100%;
  height: 240px;
}

/* ---------- Modal ---------- */
.console-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 20, 32, 0.42);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.console-modal {
  width: min(100%, 480px);
  background: var(--c-bg-modal);
  border-radius: 14px;
  padding: 22px 24px 20px;
  box-shadow: var(--shadow-card-raised);
}
.console-modal-title {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  color: var(--c-text-primary);
  margin: 0 0 6px;
}
.console-modal-lead {
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--c-text-secondary);
  margin: 0 0 14px;
}
.console-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.console-field-label {
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 600;
  color: var(--c-text-secondary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.console-input {
  padding: 8px 11px;
  border: 1px solid var(--c-border-glass);
  border-radius: 7px;
  background: var(--c-bg-base-elevated);
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
  outline: none;
  transition: border-color 140ms, box-shadow 140ms;
}
.console-input:focus {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}
.console-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
/* Secret-box is intentionally terminal-dark in BOTH themes (it
   represents "here is your raw secret, one-time view") — we keep
   #0f1420 and light text on it regardless of theme. */
.console-secret-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 11px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 9px;
  background: #0f1420;
}
.console-secret {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: #e6edf3;
  word-break: break-all;
  line-height: 1.5;
}
.console-secret-hint {
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
  line-height: 1.55;
  margin: 10px 0 0;
}
.console-secret-hint code {
  font-family: var(--font-mono);
  font-size: 11.5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--c-bg-surface-hover);
}

/* ---------- Danger modal (revoke) ---------- */
.console-modal.is-danger {
  width: min(100%, 440px);
  padding: 22px 24px 18px;
}
.revoke-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
  margin-bottom: 12px;
}
.console-modal.is-danger .console-modal-lead code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(178, 59, 46, 0.08);
  color: #b23b2e;
}
.console-modal.is-danger .console-modal-lead strong {
  color: #b23b2e;
  font-weight: 700;
}
.revoke-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--c-bg-surface-hover);
  margin-bottom: 14px;
}
.revoke-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}
.revoke-detail-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.revoke-detail-row code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 5px;
  background: var(--c-bg-surface-active);
  color: var(--c-text-primary);
}
.console-danger-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 9px;
  background: #b23b2e;
  color: #ffffff;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background-color 140ms;
}
.console-danger-btn:hover {
  background: #9b3126;
}

/* ---------- Responsive ---------- */
@media (max-width: 900px) {
  .console-shell {
    padding: 0 14px 32px;
  }
  .console-panel-head {
    padding: 16px 18px 12px;
  }
  .console-table thead th,
  .console-table tbody td {
    padding: 10px 14px;
  }
}

@media (max-width: 720px) {
  .console-top {
    align-items: flex-start;
    padding-bottom: 14px;
  }
  .console-title {
    font-size: 22px;
  }
  .console-top-right {
    width: 100%;
  }
  .console-top-right .console-primary-btn,
  .console-top-right .console-secondary-btn,
  .console-top-right .console-ghost-btn {
    flex: 1 1 auto;
    justify-content: center;
    padding: 10px 14px;
  }
  .console-ghost-label {
    display: none;
  }

  /* Metric strip: single column instead of squeezing 4 cards into a
     200px-minmax grid that leaves ugly half-cards on 375px screens. */
  .console-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric {
    padding: 12px 14px;
  }
  .metric-value strong {
    font-size: 20px;
  }
  .metric-spark {
    flex: 0 0 64px;
    width: 64px;
    height: 36px;
  }

  /* Card-ified table: each row stacks into a labeled card. Data-label
     attributes on <td> become the key; the cell content is the value.
     Uses CSS only so the DOM stays a proper <table> for a11y. */
  .console-table,
  .console-table thead,
  .console-table tbody,
  .console-table tr,
  .console-table th,
  .console-table td {
    display: block;
  }
  .console-table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
  .console-table tbody tr {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 6px 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--c-border-glass);
  }
  .console-table tbody tr:last-child { border-bottom: none; }
  .console-table tbody td {
    padding: 0;
    border: none;
    display: flex;
    align-items: center;
    min-width: 0;
  }
  .console-table tbody td::before {
    content: attr(data-label);
    flex: 0 0 72px;
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--c-text-muted);
  }
  /* Name row = card header: full width, larger, no label */
  .console-table tbody td.col-name {
    grid-column: 1 / -1;
    font-size: 14px;
    font-weight: 600;
    padding-bottom: 4px;
    border-bottom: 1px dashed var(--c-border-glass);
    margin-bottom: 4px;
  }
  .console-table tbody td.col-name::before { display: none; }
  .console-table tbody td.col-prefix code {
    font-size: 11.5px;
  }
  /* Actions: span full width, right-aligned button */
  .console-table tbody td.col-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
    padding-top: 4px;
  }
  .console-table tbody td.col-actions::before { display: none; }
  .row-action {
    padding: 6px 12px;
    font-size: 12.5px;
  }
}

@media (max-width: 480px) {
  .console-metrics {
    grid-template-columns: 1fr;
  }
  .console-modal {
    padding: 18px 18px 16px;
  }
  .console-modal-title {
    font-size: 16px;
  }
  .console-modal.is-danger {
    padding: 18px 18px 16px;
  }
}
</style>
