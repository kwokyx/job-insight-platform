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
  X,
  FolderKanban,
  CalendarDays,
  Inbox
} from 'lucide-vue-next'
import { useThemeStore } from '../store/theme'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  createOpenApiKey,
  fetchOpenApiKeyLogs,
  fetchOpenApiKeys,
  toggleOpenApiKey
} from '../api'

const themeStore = useThemeStore()
const authStore = useAuthStore()
const { success, error } = useToast()

use([CanvasRenderer, LineChart, TitleComponent, TooltipComponent, GridComponent])

const router = useRouter()

// ---- Local state ----
const keys = ref([])
const allApiLogs = ref([])
const usage = ref([])

const activeKeyCount = computed(() => keys.value.filter((k) => k.status === 'active').length)
const revokedKeyCount = computed(() => keys.value.filter((k) => k.status === 'revoked').length)
const totalKeyCount = computed(() => keys.value.length)

const quota = computed(() => {
  const total = keys.value.reduce((sum, item) => sum + (Number(item.dailyQuota) || 0), 0)
  const used = filteredLogs.value.length
  const avgQps = keys.value.length
    ? Math.round(keys.value.reduce((sum, item) => sum + (Number(item.rateLimitQps) || 0), 0) / keys.value.length)
    : 0
  return {
    used,
    total,
    resetAt: '每日 00:00',
    rpsCurrent: 0,
    rpsLimit: avgQps,
    retentionDays: 30
  }
})

// ---- Filter chip state (project + date range) ----
// Hover-to-open with 120 ms grace on close, matches JobsView's pattern.
const selectedProject = ref('')
const dateRange = ref('last-14-days')

const dateRangeOptions = [
  { value: 'last-7-days', label: '近 7 天' },
  { value: 'last-14-days', label: '近 14 天' },
  { value: 'last-30-days', label: '近 30 天' },
  { value: 'this-month', label: '本月' },
  { value: 'last-month', label: '上月' }
]

const projects = computed(() => {
  const palette = ['#0057c2', '#425d97', '#1e8a5b', '#b26a2e', '#7b4db0']
  const scopeSet = new Set()
  keys.value.forEach((item) => {
    if (item.tenantScope) scopeSet.add(item.tenantScope)
  })
  return [...scopeSet].sort().map((scope, idx) => ({
    id: scope,
    name: scope,
    color: palette[idx % palette.length]
  }))
})

const projectChipLabel = computed(() => {
  if (!selectedProject.value) return '项目'
  return projects.value.find((p) => p.id === selectedProject.value)?.name ?? '项目'
})
const dateRangeChipLabel = computed(() => {
  if (!dateRange.value) return '时间范围'
  return dateRangeOptions.find((o) => o.value === dateRange.value)?.label ?? '时间范围'
})
const isProjectActive = computed(() => !!selectedProject.value)
const isDateRangeActive = computed(() => !!dateRange.value)

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
onBeforeUnmount(() => {
  window.removeEventListener('click', handleFilterOutsideClick)
  window.removeEventListener('keydown', handleFilterKey)
  if (filterCloseTimer) clearTimeout(filterCloseTimer)
})

function pickProject(id) {
  selectedProject.value = id
  closeFilterNow()
}
function clearProject() {
  selectedProject.value = ''
  closeFilterNow()
}
function pickDateRange(value) {
  dateRange.value = value
  closeFilterNow()
}
function clearDateRange() {
  dateRange.value = ''
  closeFilterNow()
}

// ---- Usage dimension tab group ----
const usageDimension = ref('endpoint')

// ---- Refresh / export ----
const refreshing = ref(false)
function refreshUsage() {
  if (refreshing.value) return
  refreshing.value = true
  loadUsageLogs()
    .then(() => {
      success('刷新成功')
    })
    .catch((e) => {
      error(`刷新失败: ${e.message || e}`)
    })
    .finally(() => {
      refreshing.value = false
    })
}

function exportUsage() {
  const rows = []
  const rangeText = `${currentRangeStart.value || '--'} ~ ${currentRangeEnd.value || '--'}`
  if (usageDimension.value === 'endpoint') {
    rows.push(['维度', '接口', '调用次数', '错误次数', '时间范围'])
    endpointCards.value.forEach((item) => {
      rows.push(['endpoint', (item.endpoints || []).join(' | '), item.totalRequests, item.totalErrors, rangeText])
    })
  } else {
    rows.push(['维度', 'Key 名称', 'Key 前缀', '调用次数', '错误次数', '时间范围'])
    keyCards.value.forEach((item) => {
      rows.push(['key', item.name || '', item.prefix || '', item.totalRequests, item.totalErrors, rangeText])
    })
  }
  const csv = rows.map((line) => line.map((value) => csvEscape(value)).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const now = new Date()
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`
  triggerDownload(blob, `api-usage-${usageDimension.value}-${stamp}.csv`)
}

const keyScopeMap = computed(() => {
  const map = new Map()
  keys.value.forEach((item) => {
    map.set(Number(item.id), item.tenantScope || 'public')
  })
  return map
})

const rangeStart = computed(() => {
  const now = new Date()
  if (dateRange.value === 'last-7-days') return startOfDay(addDays(now, -6))
  if (dateRange.value === 'last-30-days') return startOfDay(addDays(now, -29))
  if (dateRange.value === 'this-month') return startOfDay(new Date(now.getFullYear(), now.getMonth(), 1))
  if (dateRange.value === 'last-month') return startOfDay(new Date(now.getFullYear(), now.getMonth() - 1, 1))
  return startOfDay(addDays(now, -13))
})

const rangeEnd = computed(() => {
  const now = new Date()
  if (dateRange.value === 'last-month') return endOfDay(new Date(now.getFullYear(), now.getMonth(), 0))
  return endOfDay(now)
})

const axisDays = computed(() => {
  const rows = []
  let cursor = new Date(rangeStart.value)
  while (cursor <= rangeEnd.value) {
    rows.push(formatAxisDate(cursor))
    cursor = addDays(cursor, 1)
  }
  return rows
})

const filteredLogs = computed(() => {
  const start = rangeStart.value
  const end = rangeEnd.value
  const selectedScope = selectedProject.value
  return allApiLogs.value.filter((row) => {
    const ts = toDate(row.createdAt)
    if (!ts) return false
    if (ts < start || ts > end) return false
    if (!selectedScope) return true
    const scope = keyScopeMap.value.get(Number(row.apiKeyId)) || 'public'
    return scope === selectedScope
  })
})

// ---- Aggregated KPI series (14-day totals across all endpoints) ----
// Each entry is "what day X looks like summed across every endpoint".
// Used for the top 4 KPI sparklines so they feel like cohesive overviews.
const aggregatedDaily = computed(() => {
  const counter = new Map(axisDays.value.map((day) => [day, { calls: 0, errors: 0 }]))
  filteredLogs.value.forEach((row) => {
    const day = formatAxisDate(toDate(row.createdAt))
    const bucket = counter.get(day)
    if (!bucket) return
    bucket.calls += 1
    if (Number(row.responseCode || 200) >= 400) bucket.errors += 1
  })
  return axisDays.value.map((day) => ({
    date: day,
    calls: counter.get(day)?.calls || 0,
    errors: counter.get(day)?.errors || 0
  }))
})

const totalCalls14d = computed(() => aggregatedDaily.value.reduce((acc, d) => acc + d.calls, 0))
const totalErrors14d = computed(() => aggregatedDaily.value.reduce((acc, d) => acc + d.errors, 0))
const errorRatePct = computed(() => {
  if (!totalCalls14d.value) return 0
  return Math.round((totalErrors14d.value / totalCalls14d.value) * 10000) / 100
})

// Compare last-7-days sum vs previous-7-days sum to produce a change %
// on the "总调用量" KPI. Keeps the number honest even if the series
// changes shape.
const callsChangePct = computed(() => {
  const days = aggregatedDaily.value
  if (days.length < 14) return null
  const last7 = days.slice(-7).reduce((a, d) => a + d.calls, 0)
  const prev7 = days.slice(-14, -7).reduce((a, d) => a + d.calls, 0)
  if (!prev7) return null
  const pct = ((last7 - prev7) / prev7) * 100
  return { pct: Math.round(pct * 10) / 10, dir: pct >= 0 ? 'up' : 'down' }
})

// Latency mini series — derived heuristically from call volume so the
// sparkline reads "busy days are slightly slower". Tuned to hover
// around ~180 ms.
const latencyDaily = computed(() => {
  const latencyMap = new Map(axisDays.value.map((day) => [day, []]))
  filteredLogs.value.forEach((row) => {
    const day = formatAxisDate(toDate(row.createdAt))
    const list = latencyMap.get(day)
    if (!list) return
    const ms = Number(row.responseTime || 0)
    if (Number.isFinite(ms) && ms >= 0) list.push(ms)
  })
  return axisDays.value.map((day) => {
    const values = latencyMap.get(day) || []
    return { date: day, value: values.length ? percentile(values, 95) : 0 }
  })
})
const latencyP95 = computed(() => {
  const values = filteredLogs.value
    .map((row) => Number(row.responseTime || 0))
    .filter((value) => Number.isFinite(value) && value >= 0)
  if (!values.length) return 0
  return percentile(values, 95)
})

// Active-key mini series — synthetic count over 14 days. Doesn't need
// to be interesting; it just visually anchors the KPI card.
const activeKeyDaily = computed(() =>
  aggregatedDaily.value.map((d) => ({
    date: d.date,
    value: activeKeyCount.value
  }))
)

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

// Create-key modal
const showCreateModal = ref(false)
const creatingName = ref('')
const creatingScope = ref('read-only')
const revealedKey = ref(null)
const copiedField = ref('')
const creating = ref(false)

function openCreateModal() {
  creatingName.value = ''
  creatingScope.value = 'read-only'
  revealedKey.value = null
  showCreateModal.value = true
}
function closeCreateModal() {
  if (creating.value) return
  showCreateModal.value = false
  revealedKey.value = null
}

function confirmCreateKey() {
  if (creating.value) return
  const name = creatingName.value.trim()
  if (!name) return
  creating.value = true
  createOpenApiKey(authStore.token, {
    keyName: name,
    permissionProfile: creatingScope.value === 'full' ? 'full' : 'basic',
    tenantScope: selectedProject.value || 'public',
    rateLimitQps: 10,
    dailyQuota: 1000
  })
    .then(async (result) => {
      revealedKey.value = {
        name,
        secret: result?.apiKey || '',
        prefix: maskApiKey(result?.apiKey || '')
      }
      await Promise.all([loadKeys(), loadUsageLogs()])
      success('API Key 创建成功')
    })
    .catch((e) => {
      error(`创建失败：${e.message || e}`)
    })
    .finally(() => {
      creating.value = false
    })
}

// Revoke-key confirm modal
const revokingKey = ref(null)
function revokeKey(id) {
  const k = keys.value.find((x) => x.id === id)
  if (!k) return
  revokingKey.value = k
}
function closeRevokeModal() {
  revokingKey.value = null
}

function confirmRevoke() {
  if (!revokingKey.value) return
  toggleOpenApiKey(authStore.token, revokingKey.value.id, false)
    .then(async () => {
      revokingKey.value = null
      await Promise.all([loadKeys(), loadUsageLogs()])
      success('已撤销 API Key')
    })
    .catch((e) => {
      error(`撤销失败：${e.message || e}`)
    })
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

// Legacy 7-day chart at the bottom of the page, kept for historical
// context. Uses the same `chartTheme` computed the new sparklines do.
const usageOption = computed(() => {
  const t = chartTheme.value
  return {
    grid: { left: 44, right: 18, top: 20, bottom: 30, containLabel: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: t.tooltipBg,
      borderColor: t.tooltipBorder,
      borderWidth: 1,
      textStyle: { color: t.tooltipText, fontSize: 12 },
      padding: [8, 12]
    },
    xAxis: {
      type: 'category',
      data: usage.value.map((d) => d.date),
      axisLine: { lineStyle: { color: t.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: t.axisLabel, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: t.splitLine } },
      axisLabel: { color: t.axisLabel, fontSize: 11 }
    },
    series: [
      {
        name: '调用数',
        data: usage.value.map((d) => d.calls),
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2, color: t.accent },
        itemStyle: { color: t.accent },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: t.areaTop },
              { offset: 1, color: t.areaBottom }
            ]
          }
        }
      }
    ]
  }
})

const endpointCards = computed(() => {
  const rows = new Map()
  filteredLogs.value.forEach((log) => {
    const endpoint = String(log.endpoint || '-')
    if (!rows.has(endpoint)) {
      rows.set(endpoint, {
        id: endpoint,
        label: endpoint,
        endpoints: [endpoint],
        totalRequests: 0,
        totalErrors: 0,
        dailyMap: new Map(axisDays.value.map((day) => [day, { calls: 0, errors: 0 }]))
      })
    }
    const row = rows.get(endpoint)
    row.totalRequests += 1
    if (Number(log.responseCode || 200) >= 400) row.totalErrors += 1
    const day = formatAxisDate(toDate(log.createdAt))
    const bucket = row.dailyMap.get(day)
    if (bucket) {
      bucket.calls += 1
      if (Number(log.responseCode || 200) >= 400) bucket.errors += 1
    }
  })
  return [...rows.values()]
    .map((row) => ({
      id: row.id,
      label: row.label,
      endpoints: row.endpoints,
      totalRequests: row.totalRequests,
      totalErrors: row.totalErrors,
      daily: axisDays.value.map((day) => ({
        date: day,
        calls: row.dailyMap.get(day)?.calls || 0,
        errors: row.dailyMap.get(day)?.errors || 0
      }))
    }))
    .sort((a, b) => b.totalRequests - a.totalRequests)
})
const keyCards = computed(() => {
  const rows = new Map()
  filteredLogs.value.forEach((log) => {
    const keyId = Number(log.apiKeyId || 0)
    const keyInfo = keys.value.find((item) => Number(item.id) === keyId)
    const id = keyId || -1
    if (!rows.has(id)) {
      rows.set(id, {
        id,
        name: keyInfo?.name || `Key ${keyId}`,
        prefix: keyInfo?.prefix || `id:${keyId}`,
        totalRequests: 0,
        totalErrors: 0,
        dailyMap: new Map(axisDays.value.map((day) => [day, { calls: 0, errors: 0 }]))
      })
    }
    const row = rows.get(id)
    row.totalRequests += 1
    if (Number(log.responseCode || 200) >= 400) row.totalErrors += 1
    const day = formatAxisDate(toDate(log.createdAt))
    const bucket = row.dailyMap.get(day)
    if (bucket) {
      bucket.calls += 1
      if (Number(log.responseCode || 200) >= 400) bucket.errors += 1
    }
  })
  return [...rows.values()]
    .map((row) => ({
      ...row,
      daily: axisDays.value.map((day) => ({
        date: day,
        calls: row.dailyMap.get(day)?.calls || 0,
        errors: row.dailyMap.get(day)?.errors || 0
      }))
    }))
    .filter((item) => item.totalRequests > 0)
    .sort((a, b) => b.totalRequests - a.totalRequests)
})

const currentCards = computed(() =>
  usageDimension.value === 'endpoint' ? endpointCards.value : keyCards.value
)
const currentRangeStart = computed(() => {
  const row = currentCards.value[0] ?? endpointCards.value[0]
  return row?.daily?.[0]?.date ?? ''
})
const currentRangeEnd = computed(() => {
  const row = currentCards.value[0] ?? endpointCards.value[0]
  return row?.daily?.[row.daily.length - 1]?.date ?? ''
})

function formatNumber(n) {
  return Number(n).toLocaleString('zh-CN')
}
function goToDocs() {
  router.push('/openapi/intro')
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0)
}
function endOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999)
}
function addDays(date, offset) {
  const d = new Date(date)
  d.setDate(d.getDate() + offset)
  return d
}
function toDate(value) {
  if (!value) return null
  if (value instanceof Date) return value
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}
function formatAxisDate(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return ''
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
function percentile(values, p) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))
  return Math.round(sorted[idx])
}
function csvEscape(value) {
  const text = String(value ?? '')
  if (/[",\n]/.test(text)) return `"${text.replace(/"/g, '""')}"`
  return text
}
function triggerDownload(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
function parsePermissions(raw) {
  try {
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}
function maskApiKey(key) {
  const raw = String(key || '')
  if (!raw) return '--'
  if (raw.length <= 12) return raw
  return `${raw.slice(0, 10)}...${raw.slice(-4)}`
}
function mapScope(permissions) {
  const profile = String(permissions?.profile || '').toLowerCase()
  return profile === 'full' ? 'full' : 'read-only'
}
function mapKeyRow(raw) {
  const permissions = parsePermissions(raw.permissions)
  return {
    id: raw.id,
    name: raw.keyName || `Key ${raw.id}`,
    prefix: maskApiKey(raw.apiKey),
    status: Number(raw.isActive) === 1 ? 'active' : 'revoked',
    scope: mapScope(permissions),
    tenantScope: permissions?.tenantScope || 'public',
    dailyQuota: Number(raw.dailyQuota) || 0,
    rateLimitQps: Number(raw.rateLimitQps) || 0,
    createdAt: toDate(raw.createdAt)?.toLocaleDateString('zh-CN') || '--',
    lastUsedAt: toDate(raw.lastUsedAt)?.toLocaleString('zh-CN', { hour12: false }) || '--'
  }
}

function loadKeys() {
  return fetchOpenApiKeys(authStore.token).then((rows) => {
    const list = Array.isArray(rows) ? rows : (rows?.data || [])
    keys.value = list.map(mapKeyRow)
    if (selectedProject.value && !projects.value.some((p) => p.id === selectedProject.value)) {
      selectedProject.value = ''
    }
  })
}
function loadUsageLogs() {
  const pageSize = 100
  let page = 1
  let total = 0
  const records = []
  const loop = () => fetchOpenApiKeyLogs(authStore.token, { page, pageSize }).then((res) => {
    const chunk = Array.isArray(res?.data) ? res.data : []
    if (page === 1) total = Number(res?.total || chunk.length)
    records.push(...chunk)
    page += 1
    if (!chunk.length || page > 100 || records.length >= total) {
      allApiLogs.value = records
      usage.value = aggregatedDaily.value.slice(-7)
      return
    }
    return loop()
  })
  return loop()
}

onMounted(() => {
  window.addEventListener('click', handleFilterOutsideClick)
  window.addEventListener('keydown', handleFilterKey)
  refreshing.value = true
  Promise.all([loadKeys(), loadUsageLogs()])
    .catch((e) => {
      error(`加载 API 控制台失败：${e.message || e}`)
    })
    .finally(() => {
      refreshing.value = false
    })
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

    <!-- Filter chip row (project + date range). Hover-to-open with
         120 ms grace, matches JobsView's .zp-chip pattern. -->
    <div class="console-chip-row">
      <div
        class="console-chip-wrap"
        :class="{ open: openFilterKey === 'project' }"
        @mouseenter="openFilter('project')"
        @mouseleave="scheduleCloseFilter"
      >
        <div
          class="console-chip"
          :class="{ active: isProjectActive }"
          tabindex="0"
          role="button"
          :aria-expanded="openFilterKey === 'project'"
          @focus="openFilter('project')"
          @blur="scheduleCloseFilter"
        >
          <FolderKanban :size="13" :stroke-width="1.9" class="console-chip-icon" />
          <button
            v-if="isProjectActive"
            type="button"
            class="console-chip-clear"
            :aria-label="`清除 ${projectChipLabel}`"
            @click.stop.prevent="clearProject"
          >
            <X :size="11" :stroke-width="2" />
          </button>
          <span class="console-chip-label">{{ projectChipLabel }}</span>
          <ChevronDown :size="13" :stroke-width="1.8" class="console-chip-caret" />
        </div>
        <div v-if="openFilterKey === 'project'" class="console-chip-panel" role="menu">
          <button
            v-for="p in projects"
            :key="p.id"
            class="console-chip-option"
            :class="{ active: selectedProject === p.id }"
            type="button"
            role="menuitem"
            @click="pickProject(p.id)"
          >
            <span class="console-chip-dot" :style="{ background: p.color }" />
            {{ p.name }}
          </button>
        </div>
      </div>

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
          <button
            v-if="isDateRangeActive"
            type="button"
            class="console-chip-clear"
            :aria-label="`清除 ${dateRangeChipLabel}`"
            @click.stop.prevent="clearDateRange"
          >
            <X :size="11" :stroke-width="2" />
          </button>
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
            <span class="metric-sub">相比上一周</span>
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
            <span class="metric-sub">{{ formatNumber(totalErrors14d) }} 次错误 / 近 14 天</span>
          </div>
          <div class="metric-spark">
            <VChart :option="sparkErrorsOption" autoresize />
          </div>
        </div>
      </div>

      <div class="metric">
        <div class="metric-head">
          <span class="metric-label">平均延迟</span>
        </div>
        <div class="metric-body">
          <div class="metric-body-left">
            <span class="metric-value">
              <strong>{{ latencyP95 }}</strong>
              <span class="metric-suffix">ms</span>
            </span>
            <span class="metric-sub">近 7 天 P95</span>
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
        :class="{ active: usageDimension === 'endpoint' }"
        role="tab"
        :aria-selected="usageDimension === 'endpoint'"
        type="button"
        @click="usageDimension = 'endpoint'"
      >按端点</button>
      <button
        class="console-tab"
        :class="{ active: usageDimension === 'key' }"
        role="tab"
        :aria-selected="usageDimension === 'key'"
        type="button"
        @click="usageDimension = 'key'"
      >按 Key</button>
    </div>

    <!-- Card grid — one small card per dimension entry -->
    <section v-if="currentCards.length" class="console-usage-grid">
      <article
        v-for="card in currentCards"
        :key="card.id"
        class="console-usage-card"
      >
        <header class="console-usage-card-head">
          <span class="console-usage-card-label">{{ card.label || card.name }}</span>
          <span class="console-usage-card-total">{{ formatNumber(card.totalRequests) }}</span>
        </header>
        <div class="console-usage-card-sub">
          <template v-if="usageDimension === 'endpoint'">
            {{ card.endpoints.join(', ') }}
          </template>
          <template v-else>
            <code>{{ card.prefix }}</code>
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
              <th>权限</th>
              <th>创建时间</th>
              <th>最近使用</th>
              <th>状态</th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in keys" :key="k.id" :class="{ 'is-revoked': k.status === 'revoked' }">
              <td class="col-name" data-label="名称">
                <span class="col-name-icon"><KeyRound :size="13" :stroke-width="1.8" /></span>
                {{ k.name }}
              </td>
              <td class="col-prefix" data-label="Key">
                <code>{{ k.prefix }}</code>
                <button
                  class="inline-icon-btn"
                  type="button"
                  :title="copiedField === k.id ? '已复制' : '复制前缀'"
                  @click="copyText(k.prefix, k.id)"
                >
                  <Copy :size="12" :stroke-width="1.7" />
                </button>
              </td>
              <td data-label="权限"><span class="pill pill-muted">{{ k.scope === 'full' ? '全部' : '只读' }}</span></td>
              <td data-label="创建时间">{{ k.createdAt }}</td>
              <td data-label="最近使用">{{ k.lastUsedAt }}</td>
              <td data-label="状态">
                <span class="pill" :class="k.status === 'active' ? 'pill-success' : 'pill-danger'">
                  {{ k.status === 'active' ? '启用' : '已撤销' }}
                </span>
              </td>
              <td class="col-actions" data-label="操作">
                <button
                  v-if="k.status === 'active'"
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
            <tr v-if="keys.length === 0">
              <td colspan="7" class="console-empty">
                还没有 Key。点击右上角「创建新 Key」开始。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="console-panel">
      <header class="console-panel-head">
        <div>
          <h2 class="console-panel-title">近 7 天用量</h2>
          <p class="console-panel-sub">按自然日统计的调用总数。要按 Key 分组，请参考上方「按 Key」视图。</p>
        </div>
      </header>
      <div class="console-chart-wrap">
        <VChart class="console-chart" :option="usageOption" autoresize />
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
              :disabled="!creatingName.trim()"
              @click="confirmCreateKey"
            >
              创建
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
            调用开放接口时请在请求头携带 <code>X-API-Key: &lt;your-key&gt;</code>。Key 泄露请立即撤销并重新创建。
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
        <h3 id="revoke-title" class="console-modal-title">撤销「{{ revokingKey.name }}」？</h3>
        <p class="console-modal-lead">
          撤销后，依赖此 Key 的应用将立刻收到 <code>401 Unauthorized</code>，且操作<strong>无法恢复</strong>。
          如果只是临时停用，建议先在调用方替换为新 Key，再回来撤销旧的。
        </p>

        <div class="revoke-detail">
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">Key</span>
            <code>{{ revokingKey.prefix }}</code>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">权限</span>
            <span>{{ revokingKey.scope === 'full' ? '完整读写' : '只读' }}</span>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">最近使用</span>
            <span>{{ revokingKey.lastUsedAt }}</span>
          </div>
        </div>

        <div class="console-modal-actions">
          <button class="console-secondary-btn" type="button" @click="closeRevokeModal">取消</button>
          <button class="console-danger-btn" type="button" @click="confirmRevoke">
            <Ban :size="13" :stroke-width="1.9" />
            确认撤销
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
[data-theme="dark"] .console-primary-btn {
  color: #0f1420;
}
[data-theme="dark"] .console-primary-btn:hover {
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
[data-theme="dark"] .console-chip-clear:hover {
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
