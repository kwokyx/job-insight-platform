<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import {
  fetchAdminDashboard,
  fetchAdminLogs,
  fetchCrawlTasks,
  fetchCrawlQuality,
  fetchDataSources,
  fetchOpenApiKeyLogs,
  fetchOpenApiKeys
} from '../api'
import {
  Activity,
  Briefcase,
  Database,
  FileText,
  KeyRound,
  RefreshCw,
  ShieldAlert
} from 'lucide-vue-next'

const authStore = useAuthStore()
const router = useRouter()
const { error } = useToast()

const loading = ref(true)
const dashboard = ref(null)

// Crawl / data-source state (平台采集健康度)
const crawlTasks = ref([])
const crawlQuality = ref(null)
const dataSources = ref([])

// Operation logs (平台系统日志)
const logs = ref([])
const logsLoading = ref(false)
const logsPage = ref(1)
const logsPageSize = ref(10)
const logsTotal = ref(0)
const logsHasMore = computed(() => logs.value.length < logsTotal.value)
const displayLogs = computed(() => {
  if (logs.value.length) return logs.value
  return dashboard.value?.recentLogs || []
})

// OpenAPI key audit (接口调用统计)
const apiKeys = ref([])
const apiKeyLogs = ref([])
const apiKeyLogsLoading = ref(false)

// ---------- KPI ----------
// 平台级指标：岗位 / 报告 / 采集任务 / 数据源。用户侧指标一律下放到 /admin/users。
const kpiCards = computed(() => {
  if (!dashboard.value) return []
  const runningTasks = crawlTasks.value.filter((t) => {
    const s = String(t.status || '').toLowerCase()
    return s === 'running' || t.status === 1
  }).length
  return [
    { label: '岗位总量', value: dashboard.value.totalJobs ?? '--', hint: `近 7 天新增 ${dashboard.value.newJobs7d ?? 0}`, icon: Briefcase },
    { label: '报告总量', value: dashboard.value.totalReports ?? '--', hint: '累计产出', icon: FileText },
    { label: '采集任务', value: crawlTasks.value.length || '--', hint: `运行中 ${runningTasks}`, icon: Activity },
    { label: '数据源', value: dataSources.value.length || '--', hint: '已接入的外部站点', icon: Database }
  ]
})

// ---------- 采集健康度摘要 ----------
const crawlHealth = computed(() => {
  const q = crawlQuality.value
  if (!q) return []
  const pick = (key, label, fmt = (v) => v) => {
    const v = q[key]
    if (v === undefined || v === null) return null
    return { label, value: fmt(v) }
  }
  return [
    pick('totalJobs', '累计入库岗位'),
    pick('duplicateCount', '重复样本'),
    pick('missingFieldRatio', '字段缺失率', (v) => `${(Number(v) * 100).toFixed(1)}%`),
    pick('lastRunAt', '最近采集', (v) => new Date(v).toLocaleString('zh-CN'))
  ].filter(Boolean)
})

// ---------- 接口调用统计 ----------
const apiCallStats = computed(() => {
  const rows = apiKeyLogs.value
  if (!rows.length) {
    return { total: 0, ok: 0, err: 0, uniqueKeys: apiKeys.value.length }
  }
  const ok = rows.filter((r) => Number(r.statusCode || r.status || 200) < 400).length
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
    title: '治理',
    items: [
      { id: 'section-logs', label: '系统日志' },
      { id: 'section-risks', label: '平台风险' },
      { id: 'section-quicklinks', label: '快捷入口' }
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
    .then((res) => { crawlTasks.value = res?.data || res?.records || res || [] })
    .catch(() => {})
  const quality = fetchCrawlQuality(authStore.token)
    .then((res) => { crawlQuality.value = res || null })
    .catch(() => {})
  const sources = fetchDataSources(authStore.token)
    .then((res) => { dataSources.value = res?.data || res || [] })
    .catch(() => {})
  await Promise.all([tasks, quality, sources])
}

async function loadApiAudit() {
  apiKeyLogsLoading.value = true
  try {
    const [keys, logsRes] = await Promise.all([
      fetchOpenApiKeys(authStore.token).catch(() => []),
      fetchOpenApiKeyLogs(authStore.token, { page: 1, pageSize: 10 }).catch(() => ({ data: [], total: 0 }))
    ])
    apiKeys.value = Array.isArray(keys) ? keys : (keys?.data || [])
    apiKeyLogs.value = logsRes?.data || []
  } finally {
    apiKeyLogsLoading.value = false
  }
}

async function loadLogs(append = false) {
  logsLoading.value = true
  try {
    const res = await fetchAdminLogs(authStore.token, {
      page: logsPage.value,
      pageSize: logsPageSize.value
    })
    const rawItems = Array.isArray(res) ? res : (res?.data ?? res?.records ?? [])
    const items = Array.isArray(rawItems) ? rawItems : []
    logs.value = append ? logs.value.concat(items) : items
    const totalFromRes = Number(res?.total)
    logsTotal.value = Number.isFinite(totalFromRes) && totalFromRes > 0
      ? totalFromRes
      : logs.value.length
  } catch (e) {
    error('加载日志失败：' + e.message)
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
    await Promise.all([loadDashboard(), loadCrawl(), loadApiAudit()])
  } catch (e) {
    error(`运营面板加载失败：${e.message}`)
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
    <header class="workspace-page-head">
      <h1 class="workspace-page-title">运营面板</h1>
      <p class="workspace-page-desc">平台级监控：数据采集、接口调用、系统日志与风险。用户与角色管理请前往「用户管理」。</p>
    </header>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载运营面板...</p>
    </div>

    <template v-else-if="dashboard">
      <section class="workspace-metric-strip">
        <article v-for="card in kpiCards" :key="card.label" class="metric-card">
          <div class="metric-head">
            <span class="metric-label">{{ card.label }}</span>
            <component :is="card.icon" :size="16" class="metric-icon" />
          </div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-note">{{ card.hint }}</div>
        </article>
      </section>

      <div class="admin-shell">
        <aside class="admin-sidebar" aria-label="运营面板目录">
          <div class="admin-sidebar-inner">
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
          <article id="section-collector" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">数据采集状态</h2>
              <button class="btn-ghost" type="button" @click="loadCrawl">
                <RefreshCw :size="14" /> 刷新
              </button>
            </header>
            <div class="panel-body">
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
                      <th>数据源</th>
                      <th>状态</th>
                      <th>最近运行</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="t in crawlTasks.slice(0, 6)" :key="t.id">
                      <td><strong>{{ t.taskName || t.name || `#${t.id}` }}</strong></td>
                      <td class="cell-muted">{{ t.sourceName || t.dataSourceName || t.dataSourceId || '--' }}</td>
                      <td><span class="status-pill ok">{{ t.statusLabel || t.status || '--' }}</span></td>
                      <td class="cell-muted">{{ t.lastRunAt ? new Date(t.lastRunAt).toLocaleString('zh-CN') : (t.updatedAt ? new Date(t.updatedAt).toLocaleString('zh-CN') : '--') }}</td>
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
                    <tr v-for="(r, idx) in apiKeyLogs.slice(0, 8)" :key="r.id || idx">
                      <td class="cell-muted">{{ r.keyName || r.apiKeyId || '--' }}</td>
                      <td><strong>{{ r.path || r.endpoint || '--' }}</strong></td>
                      <td>
                        <span :class="['status-pill', Number(r.statusCode || r.status || 200) < 400 ? 'ok' : 'off']">
                          {{ r.statusCode || r.status || '--' }}
                        </span>
                      </td>
                      <td class="cell-muted">{{ r.createdAt ? new Date(r.createdAt).toLocaleString('zh-CN') : (r.calledAt ? new Date(r.calledAt).toLocaleString('zh-CN') : '--') }}</td>
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

          <article id="section-logs" class="admin-section panel">
            <header class="panel-head panel-head-row">
              <h2 class="panel-title">最近系统日志</h2>
              <button class="btn-ghost" type="button" :disabled="logsLoading" @click="refreshLogs">
                <RefreshCw :size="14" />
                <span>刷新</span>
              </button>
            </header>
            <div class="panel-body">
              <div v-if="logsLoading && !displayLogs.length" class="empty-state">
                <div class="loader-ring"></div>
                <p>正在加载日志…</p>
              </div>
              <div v-else-if="displayLogs.length" class="log-list">
                <div v-for="log in displayLogs" :key="log.id" class="log-item">
                  <div class="log-main">
                    <strong>{{ log.operation }}</strong>
                    <span>{{ log.username || '系统' }} / {{ log.ipAddress || log.ip || '未知 IP' }}</span>
                  </div>
                  <time>{{ new Date(log.createdAt).toLocaleString('zh-CN') }}</time>
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

          <article id="section-risks" class="admin-section panel">
            <header class="panel-head">
              <h2 class="panel-title">平台风险提醒</h2>
            </header>
            <div class="panel-body">
              <div class="risk-list">
                <div class="risk-item">
                  <ShieldAlert :size="16" />
                  <div>
                    <strong>采集任务空置</strong>
                    <p>{{ crawlTasks.length ? `当前已配置 ${crawlTasks.length} 个任务，持续关注运行状态。` : '尚未配置采集任务，岗位数据将无法更新。' }}</p>
                  </div>
                </div>
                <div class="risk-item">
                  <ShieldAlert :size="16" />
                  <div>
                    <strong>API Key 治理</strong>
                    <p>{{ apiKeys.length ? `已签发 ${apiKeys.length} 个 Key，请定期轮换并核对审计日志。` : '尚未签发开放平台 Key，对外集成能力未开放。' }}</p>
                  </div>
                </div>
                <div class="risk-item">
                  <ShieldAlert :size="16" />
                  <div>
                    <strong>报告与推荐转化</strong>
                    <p>核心价值尚未稳定进入高频使用流程，建议在报告中心持续沉淀模板。</p>
                  </div>
                </div>
              </div>
            </div>
          </article>

          <article id="section-quicklinks" class="admin-section panel">
            <header class="panel-head">
              <h2 class="panel-title">管理员动作</h2>
            </header>
            <div class="panel-body">
              <div class="feature-list">
                <button class="feature-card" @click="router.push('/admin/users')">
                  <strong>用户与角色管理</strong>
                </button>
                <button class="feature-card" @click="router.push('/crawler')">
                  <strong>数据采集监控</strong>
                </button>
                <button class="feature-card" @click="router.push('/openapi')">
                  <strong>开放平台治理</strong>
                </button>
                <button class="feature-card" @click="router.push('/reports')">
                  <strong>运营报告模板</strong>
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

.workspace-page-desc {
  margin: 4px 0 0;
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.workspace-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
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

.feature-list { display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.feature-card {
  display: flex; align-items: center; padding: 14px 16px;
  border-radius: 12px; border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated); color: var(--c-text-primary);
  text-align: left; cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out),
              background-color var(--duration-fast) var(--ease-out);
}
.feature-card:hover { border-color: var(--c-border-glass-hover); background: var(--c-accent-primary-glow); }
.feature-card strong { font-family: var(--font-serif); font-size: 14px; font-weight: 700; }
.feature-card:hover strong { color: var(--c-accent-primary); }

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

.risk-list { display: grid; gap: 10px; }
.risk-item {
  display: flex; gap: 12px; align-items: flex-start;
  padding: 14px 16px; border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.risk-item :deep(svg) { color: var(--c-accent-primary); flex-shrink: 0; margin-top: 2px; }
.risk-item strong {
  display: block; color: var(--c-text-primary);
  font-family: var(--font-serif); font-size: 14px;
  font-weight: 700; margin-bottom: 4px;
}
.risk-item p { margin: 0; color: var(--c-text-secondary); font-size: 13px; line-height: 1.55; }

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

.loading-state {
  display: flex; flex-direction: column; align-items: center;
  gap: 12px; padding: 48px 0;
  color: var(--c-text-muted); font-size: 13px;
}
.loader-ring {
  width: 28px; height: 28px;
  border: 2px solid var(--c-accent-primary-glow);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

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
@media (max-width: 640px) {
  .workspace-metric-strip { grid-template-columns: 1fr; }
}
</style>
