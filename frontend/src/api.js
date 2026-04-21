const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'
const ALGO_BASE = import.meta.env.VITE_ALGO_BASE || ''

const DEFAULT_CACHE_TTL = 30_000
const memCache = new Map() // key → { ts, payload, pending }

function cacheKey(method, path, headers) {
  const auth = headers?.Authorization || ''
  return `${method}|${path}|${auth}`
}

export function invalidateApiCache(match) {
  if (!match) {
    memCache.clear()
    return
  }
  for (const k of [...memCache.keys()]) {
    if (k.includes(match)) memCache.delete(k)
  }
}

export async function request(path, options = {}) {
  const { headers, cache, ttl, ...fetchOptions } = options
  const method = (fetchOptions.method || 'GET').toUpperCase()
  const cacheable = method === 'GET' && cache !== false
  const key = cacheable ? cacheKey(method, path, headers) : null
  const maxAge = ttl ?? DEFAULT_CACHE_TTL

  if (key) {
    const entry = memCache.get(key)
    if (entry?.pending) {
      return entry.pending
    }
    if (entry && Date.now() - entry.ts < maxAge) {
      return entry.payload
    }
  }

  const run = (async () => {
    const isFormData = typeof FormData !== 'undefined' && fetchOptions.body instanceof FormData
    const response = await fetch(`${API_BASE}${path}`, {
      ...fetchOptions,
      headers: {
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...(headers || {})
      }
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok || (payload.code && payload.code !== 200)) {
      throw new Error(payload.message || `请求失败: ${response.status}`)
    }
    return payload
  })()

  if (key) {
    memCache.set(key, { ts: 0, payload: null, pending: run })
    try {
      const payload = await run
      memCache.set(key, { ts: Date.now(), payload, pending: null })
      return payload
    } catch (err) {
      memCache.delete(key)
      throw err
    }
  }

  return run
}

export function buildQuery(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      search.set(key, value)
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

export function authHeaders(token) {
  return token
    ? {
        Authorization: `Bearer ${token}`
      }
    : {}
}

function isNotFoundError(error) {
  return `${error?.message || ''}`.includes('404')
}

async function requestWithFallback(primaryPath, fallbackPath, normalize, defaultValue = {}) {
  try {
    const payload = await request(primaryPath)
    return typeof normalize === 'function' ? normalize(payload.data || {}) : (payload.data || {})
  } catch (error) {
    if (!fallbackPath || !isNotFoundError(error)) {
      throw error
    }
    try {
      const payload = await request(fallbackPath)
      return typeof normalize === 'function' ? normalize(payload.data || {}) : (payload.data || {})
    } catch (fallbackError) {
      if (isNotFoundError(fallbackError)) {
        return defaultValue
      }
      throw fallbackError
    }
  }
}

// ═════════════════════════════════════════
// 公开 API（无需认证）
// ═════════════════════════════════════════

export async function fetchOverview() {
  const payload = await request('/open/analysis/overview')
  return payload.data || {}
}

export async function fetchSkills(limit = 20) {
  const payload = await request(`/open/analysis/skills${buildQuery({ limit })}`)
  return payload.data || []
}

export async function fetchPublicJobs(params) {
  const payload = await request(`/open/jobs${buildQuery(params)}`)
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function fetchOpenApiMeta() {
  const payload = await request('/open/meta')
  return payload.data || {}
}

export async function fetchOpenApiCapabilities() {
  const payload = await request('/open/capabilities')
  return payload.data || {}
}

export async function fetchOpenApiSubscriptionMeta() {
  const payload = await request('/open/subscriptions/meta')
  return payload.data || {}
}

// ═════════════════════════════════════════
// 分析 API（公开只读）
// ═════════════════════════════════════════

export async function fetchAnalysisOverview() {
  return requestWithFallback(
    '/analysis/overview',
    '/open/analysis/overview',
    null,
    {
      totalJobs: 0,
      avgSalaryMin: 0,
      avgSalaryMax: 0,
      topCities: [],
      topIndustries: [],
      topSkills: [],
      educationDistribution: [],
      experienceDistribution: []
    }
  )
}

export async function fetchSalaryAnalysis(groupBy = 'city', limit = 20) {
  const payload = await request(`/analysis/salary${buildQuery({ groupBy, limit })}`)
  return payload.data || {}
}

export async function fetchSalaryTrend(params = {}) {
  return requestWithFallback(
    `/analysis/salary/trend${buildQuery(params)}`,
    `/open/analysis/trend${buildQuery(params)}`,
    (data) => {
      if (Array.isArray(data?.xAxis) && Array.isArray(data?.series)) {
        return data
      }
      const rows = Array.isArray(data?.series) ? data.series : []
      return {
        xAxis: rows.map(item => item.period),
        series: [
          { name: 'avgSalaryMin', data: rows.map(item => item.avgSalaryMin) },
          { name: 'avgSalaryMax', data: rows.map(item => item.avgSalaryMax) },
          { name: 'jobCount', data: rows.map(item => item.jobCount) }
        ],
        filters: data?.filters || params,
        data: rows
      }
    },
    {
      xAxis: [],
      series: [
        { name: 'avgSalaryMin', data: [] },
        { name: 'avgSalaryMax', data: [] },
        { name: 'jobCount', data: [] }
      ],
      filters: params,
      data: []
    }
  )
}

export async function fetchDeepSupplyDemandAnalysis(params = {}) {
  const payload = await request(`/analysis/deep/supply-demand${buildQuery(params)}`)
  return payload.data || {}
}

export async function fetchDeepCurriculumGapAnalysis(token, payload = {}) {
  const result = await request('/analysis/deep/curriculum-gap', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchDeepSalaryPremiumAnalysis() {
  const payload = await request('/analysis/deep/salary-premium')
  return payload.data || {}
}

export async function fetchDeepTrendForecast(params = {}) {
  const payload = await request(`/analysis/deep/trend-forecast${buildQuery(params)}`)
  return payload.data || {}
}

export async function fetchDeepWarehouseOverview() {
  const payload = await request('/analysis/deep/warehouse/overview')
  return payload.data || {}
}

export async function fetchSkillsRanking(limit = 20) {
  const payload = await request(`/analysis/skills${buildQuery({ limit })}`)
  return payload.data || []
}

export async function fetchRegionHeatmap() {
  const payload = await request('/analysis/regions/heatmap')
  return payload.data || {}
}

export async function fetchWelfareDistribution(limit = 20) {
  try {
    const payload = await request(`/analysis/welfare${buildQuery({ limit })}`)
    return payload.data || {}
  } catch (error) {
    if (isNotFoundError(error)) {
      return { chartType: 'bar', title: 'welfare-distribution', data: [] }
    }
    throw error
  }
}

export async function fetchCompanySizeDistribution() {
  try {
    const payload = await request('/analysis/company-size')
    return payload.data || {}
  } catch (error) {
    if (!isNotFoundError(error)) {
      throw error
    }
    const jobs = await fetchPublicJobs({ page: 1, pageSize: 50 })
    const buckets = new Map()
    for (const item of jobs.data || []) {
      const key = item.companySize || '未知'
      buckets.set(key, (buckets.get(key) || 0) + 1)
    }
    return {
      chartType: 'pie',
      title: 'company-size-distribution',
      data: [...buckets.entries()].map(([companySize, count]) => ({ companySize, count }))
    }
  }
}

export async function fetchFinanceStageDistribution() {
  try {
    const payload = await request('/analysis/finance-stage')
    return payload.data || {}
  } catch (error) {
    if (!isNotFoundError(error)) {
      throw error
    }
    const jobs = await fetchPublicJobs({ page: 1, pageSize: 50 })
    const buckets = new Map()
    for (const item of jobs.data || []) {
      const key = item.companyFinance || '未知'
      buckets.set(key, (buckets.get(key) || 0) + 1)
    }
    return {
      chartType: 'pie',
      title: 'finance-stage-distribution',
      data: [...buckets.entries()].map(([financeStage, count]) => ({ financeStage, count }))
    }
  }
}

export async function fetchDeepMarketInsights(params = {}) {
  return requestWithFallback(
    `/analysis/insights/deep${buildQuery(params)}`,
    `/open/analysis/insights${buildQuery(params)}`,
    null,
    {
      sample: {},
      marketPulse: {},
      cityConcentration: {},
      skillsInsight: {},
      industryMomentum: {},
      structuralInsights: [],
      recommendations: []
    }
  )
}

// ═════════════════════════════════════════
// 职位 API（公开只读）
// ═════════════════════════════════════════

export async function fetchJobs(params) {
  const payload = await request(`/jobs${buildQuery(params)}`)
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function fetchJobDetail(id) {
  const payload = await request(`/jobs/${id}`)
  return payload.data || {}
}

export async function fetchJobsByCity(limit = 20) {
  const payload = await request(`/jobs/by-city${buildQuery({ limit })}`)
  return payload.data || []
}

export async function fetchJobsByIndustry(limit = 20) {
  const payload = await request(`/jobs/by-industry${buildQuery({ limit })}`)
  return payload.data || []
}

export async function fetchJobsByEducation() {
  const payload = await request('/jobs/by-education')
  return payload.data || []
}

export async function fetchJobsByExperience() {
  const payload = await request('/jobs/by-experience')
  return payload.data || []
}

export async function fetchHotJobs(limit = 10) {
  const payload = await request(`/jobs/hot${buildQuery({ limit })}`)
  return payload.data || []
}

export async function searchJobs(params) {
  const payload = await request(`/jobs/search${buildQuery(params)}`)
  return {
    data: payload.data || {},
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || 20
  }
}

// ═════════════════════════════════════════
// 认证 API
// ═════════════════════════════════════════

export async function login(payload) {
  const result = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function register(payload) {
  const result = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchAuthCaptcha(type) {
  const query = type ? `?type=${encodeURIComponent(type)}` : ''
  const result = await request(`/auth/captcha${query}`, { cache: false, ttl: 0 })
  return result.data || {}
}

export async function fetchAuthProfile(token) {
  const result = await request('/auth/profile', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function updateAuthProfile(token, payload) {
  const result = await request('/auth/profile', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchCareerProfile(token) {
  const result = await request('/profile', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function changeAuthPassword(token, payload) {
  const result = await request('/auth/password', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function requestPasswordReset(payload) {
  const result = await request('/auth/password/reset/request', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function confirmPasswordReset(payload) {
  const result = await request('/auth/password/reset/confirm', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 推荐 API（需认证）
// ═════════════════════════════════════════

export async function recommendJobs(token, payload) {
  const result = await request('/recommend/jobs', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function recommendSkills(token, payload) {
  const result = await request('/recommend/skills', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function recommendCareerPath(token, payload) {
  const result = await request('/recommend/career-path', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 数据采集 API（需认证，管理员）
// ═════════════════════════════════════════

export async function fetchCrawlTasks(token, params = {}) {
  const payload = await request(`/crawl/tasks${buildQuery(params)}`, {
    headers: authHeaders(token)
  })

  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function createCrawlTask(token, payload) {
  const result = await request('/crawl/tasks', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function updateCrawlTaskStatus(token, id, payload) {
  const result = await request(`/crawl/tasks/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchCrawlTaskLogs(token, taskId, params = {}) {
  const payload = await request(`/crawl/tasks/${taskId}/logs${buildQuery(params)}`, {
    headers: authHeaders(token)
  })

  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 50
  }
}

export async function fetchCrawlQuality(token) {
  const payload = await request('/crawl/tasks/quality', {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function backfillCrawlQualityHistory(token, limit = 100) {
  const result = await request(`/crawl/tasks/quality/history/backfill${buildQuery({ limit })}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function recommendSkillRadar(token, payload) {
  const result = await request('/recommend/skill-radar', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchPersonalizedRecommendPlan(token) {
  const result = await request('/recommend/plan', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchJobRankerStatus(token) {
  const result = await request('/recommend/ranker-status', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function trainJobRanker(token, limit = 20000) {
  const result = await request(`/recommend/train-ranker${buildQuery({ limit })}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function reviewResume(token, payload) {
  const result = await request('/recommend/resume-review', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchSimilarJobs(token, jobId, limit = 10) {
  const payload = await request(`/recommend/similar-jobs/${jobId}${buildQuery({ limit })}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function fetchFavorites(token, params = { page: 1, pageSize: 20 }) {
  const payload = await request(`/favorites${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function addFavorite(token, jobId, note = '') {
  const result = await request(`/favorites/${jobId}`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ note })
  })
  return result.data || result.message || true
}

export async function removeFavorite(token, jobId) {
  const result = await request(`/favorites/${jobId}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function checkFavorite(token, jobId) {
  const result = await request(`/favorites/${jobId}/check`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchRecommendPlan(token) {
  const result = await request('/recommend/plan', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// AI 对话 API（需认证）
// ═════════════════════════════════════════

export async function streamAiChat(token, payload, handlers = {}) {
  const response = await fetch(`${API_BASE}/ai/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(token)
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `AI 请求失败: ${response.status}`)
  }

  const decoder = new TextDecoder('utf-8')
  const reader = response.body.getReader()
  let buffer = ''
  let finished = false

  const processEventChunk = (chunkText) => {
    const lines = chunkText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean)

    let eventName = 'message'
    let dataLine = ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLine += line.slice(5).trim()
      }
    }

    if (!dataLine) {
      return
    }

    let data
    try {
      data = JSON.parse(dataLine)
    } catch {
      data = { raw: dataLine }
    }

    if (eventName === 'session' && handlers.onSession) {
      handlers.onSession(data)
    }
    if (eventName === 'message' && handlers.onMessage) {
      handlers.onMessage(data)
    }
    if (eventName === 'typing' && handlers.onTyping) {
      handlers.onTyping(data)
    }
    if (eventName === 'done' && handlers.onDone) {
      handlers.onDone(data)
    }
    if (eventName === 'error' && handlers.onError) {
      handlers.onError(data)
    }
  }

  while (!finished) {
    const { value, done } = await reader.read()
    finished = done
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    const chunks = buffer.split(/\r?\n\r?\n/)
    buffer = chunks.pop() || ''

    for (const chunk of chunks) {
      processEventChunk(chunk)
    }

    if (finished && buffer.trim()) {
      processEventChunk(buffer)
    }
  }
}

export async function fetchAiConversations(token) {
  const result = await request('/ai/conversations', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function fetchAiConversation(token, sessionId) {
  const result = await request(`/ai/conversations/${sessionId}`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function deleteAiConversation(token, sessionId) {
  const result = await request(`/ai/conversations/${sessionId}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function renameAiConversation(token, sessionId, title) {
  const result = await request(`/ai/conversations/${sessionId}`, {
    method: 'PATCH',
    headers: { ...authHeaders(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ title })
  })
  return result.data || { title }
}

export async function batchDeleteConversations(token, sessionIds) {
  const params = new URLSearchParams()
  sessionIds.forEach((id) => params.append('sessionIds', id))
  const result = await request(`/ai/conversations/batch?${params.toString()}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function fetchAiQuota(token) {
  const result = await request('/ai/quota', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchAiQuickCommands(token) {
  const result = await request('/ai/quick-commands', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function runAiAgentQuery(token, payload) {
  const result = await request('/ai/agent/query', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function importAiProfileFile(token, file, overwriteSkills = false) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('overwriteSkills', String(overwriteSkills))

  const response = await fetch(`${API_BASE}/ai/agent/import-profile`, {
    method: 'POST',
    headers: authHeaders(token),
    body: formData
  })

  const payload = await response.json().catch(() => ({}))
  if (!response.ok || (payload.code && payload.code !== 200)) {
    throw new Error(payload.message || `Request failed: ${response.status}`)
  }
  return payload.data || {}
}

// ═════════════════════════════════════════
// 报告 API
// ═════════════════════════════════════════

export async function fetchPublicReports(params = { page: 1, pageSize: 6 }) {
  const payload = await request(`/reports/public${buildQuery(params)}`)
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 6
  }
}

export async function createReport(token, payload) {
  const result = await request('/reports/generate', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchReportStatus(token, taskId) {
  const result = await request(`/reports/${taskId}/status`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchReports(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/reports${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 10
  }
}

export async function fetchReportCenterMeta(token) {
  const result = await request('/reports/meta', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function deleteReport(token, id) {
  const res = await fetch(`${API_BASE}/reports/${id}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  })
  if (!res.ok) throw new Error('Failed to delete report')
  return await res.json()
}

export async function batchDeleteReports(token, ids) {
  const params = new URLSearchParams()
  ids.forEach(id => params.append('ids', id))
  const res = await fetch(`${API_BASE}/reports/batch?${params.toString()}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  })
  if (!res.ok) throw new Error('Failed to batch delete reports')
  return await res.json()
}

export async function fetchReportSchedules(token) {
  const result = await request('/reports/schedules', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function fetchReportVersions(token, id) {
  const result = await request(`/reports/${id}/versions`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function submitReportReview(token, id) {
  const result = await request(`/reports/${id}/submit-review`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function reviewReport(token, id, payload) {
  const result = await request(`/reports/${id}/review`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function publishReport(token, id) {
  const result = await request(`/reports/${id}/publish`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function unpublishReport(token, id) {
  const result = await request(`/reports/${id}/unpublish`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchPublicationQueue(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/reports/publication/queue${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 10
  }
}

export async function createReportSchedule(token, payload) {
  const result = await request('/reports/schedule', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function toggleReportSchedule(token, id) {
  const result = await request(`/reports/schedules/${id}/toggle`, {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function deleteReportSchedule(token, id) {
  const result = await request(`/reports/schedules/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchReportDrill(token, id) {
  const result = await request(`/reports/${id}/drill`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

async function fetchPdfBlob(token, id) {
  const response = await fetch(`${API_BASE}/reports/${id}/pdf`, {
    headers: authHeaders(token)
  })
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('application/pdf')) {
    const text = await response.text().catch(() => '')
    let message = text || `PDF export failed: ${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload.message || message
    } catch {
      // ignore parse failure
    }
    throw new Error(message)
  }
  return await response.blob()
}

export async function exportReportPdf(token, id, fileName = `report-${id}.pdf`) {
  const blob = await fetchPdfBlob(token, id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function exportReportFormat(token, id, reportName, format = 'pdf') {
  const response = await fetch(`${API_BASE}/reports/${id}/export?format=${format}`, {
    headers: authHeaders(token)
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    let message = text || `Export failed: ${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload.message || message
    } catch {}
    throw new Error(message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${reportName || 'report'}.${format.toLowerCase()}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function openReportPdf(token, id) {
  const blob = await fetchPdfBlob(token, id)
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener,noreferrer')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export function normalizeError(error) {
  if (!error) {
    return '未知错误'
  }
  const message = error.message || String(error)
  if (message.includes('403')) {
    return '请求失败: 403（当前账号没有操作权限）'
  }
  if (message.includes('401')) {
    return '请求失败: 401（登录状态已失效，请重新登录）'
  }
  return message
}

// ═════════════════════════════════════════
// 算法服务 API（通过后端代理调用）
// ═════════════════════════════════════════

export async function predictSalary(token, payload) {
  const result = await request('/analysis/salary/predict', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchMarketSentiment(params = {}) {
  const result = await request(`/analysis/sentiment${buildQuery(params)}`)
  return result.data || {}
}



export async function fetchSkillGraph(topN = 30) {
  const result = await request(`/analysis/skills/graph${buildQuery({ topN })}`)
  return result.data || {}
}

// ═════════════════════════════════════════
// 纯算法直接调用（绕过网关直接请求算法引擎）
// ═════════════════════════════════════════

async function algoRequest(path, options = {}) {
  const mergedHeaders = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }
  const response = await fetch(`${ALGO_BASE}${path}`, {
    ...options,
    headers: mergedHeaders
  })
  if (!response.ok) {
    throw new Error(`Algorithm API failed: ${response.status}`)
  }
  return await response.json()
}

export async function fetchSentimentHistory(city, industry, months = 12) {
  return await algoRequest('/algorithm/sentiment/history', {
    method: 'POST',
    body: JSON.stringify({ city, industry, months })
  })
}

export async function fetchSentimentCompare(dimension = 'city', values = null, top_n = 8) {
  return await algoRequest(`/algorithm/sentiment/compare?dimension=${dimension}&top_n=${top_n}`, {
    method: 'POST',
    body: JSON.stringify(values || [])
  })
}

// ═════════════════════════════════════════
// 岗位订阅 API
// ═════════════════════════════════════════

export async function createSubscription(token, payload) {
  const result = await request('/subscriptions', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchSubscriptions(token) {
  const payload = await request('/subscriptions', {
    headers: authHeaders(token)
  })
  return payload.data?.records || payload.data || []
}

export async function deleteSubscription(token, id) {
  const result = await request(`/subscriptions/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchSubscriptionMatches(token, id, limit = 10) {
  const result = await request(`/subscriptions/${id}/matches${buildQuery({ limit })}`, {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function fetchSubscriptionMeta(token) {
  const result = await request('/subscriptions/meta', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function dispatchSubscriptionMatches(token, id, limit = 10) {
  const result = await request(`/subscriptions/${id}/dispatch${buildQuery({ limit })}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchNotifications(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/notifications${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data?.records || [],
    total: payload.data?.total || 0,
    unreadCount: payload.data?.unreadCount || 0
  }
}

export async function markNotificationRead(token, id) {
  const result = await request(`/notifications/${id}/read`, {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function markAllNotificationsRead(token) {
  const result = await request('/notifications/read-all', {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function fetchWebhooks(token) {
  const result = await request('/webhooks', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function createWebhook(token, payload) {
  const result = await request('/webhooks', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function deleteWebhook(token, id) {
  const result = await request(`/webhooks/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function toggleWebhook(token, id) {
  const result = await request(`/webhooks/${id}/toggle`, {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function fetchWebhookDeliveries(token, id) {
  const result = await request(`/webhooks/${id}/deliveries`, {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function scoreResume(payload) {
  return await algoRequest('/algorithm/resume/score', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function parseResume(file) {
  const formData = new FormData()
  formData.append('file', file)
  
  const response = await fetch(`${ALGO_BASE}/algorithm/resume/parse`, {
    method: 'POST',
    body: formData
  })
  
  if (!response.ok) {
    throw new Error('简历解析失败')
  }
  return await response.json()
}

export async function fetchSkillEvolution(skills, windowMonths = 12) {
  return await algoRequest('/algorithm/skills/evolution', {
    method: 'POST',
    body: JSON.stringify({ skills, window_months: windowMonths })
  })
}

// ═════════════════════════════════════════
// 管理员 API（需 ADMIN 权限）
// ═════════════════════════════════════════

export async function fetchAdminDashboard(token) {
  const result = await request('/admin/dashboard', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchAdminApiKeys(token) {
  const result = await request('/open/api-keys', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function createAdminApiKey(token, payload) {
  const result = await request('/open/api-keys', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function toggleAdminApiKey(token, id, active) {
  const result = await request(`/open/api-keys/${id}/toggle`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ active })
  })
  return result.data || {}
}

export async function fetchAdminApiLogs(token, params = { page: 1, pageSize: 20 }) {
  const payload = await request(`/open/api-keys/logs${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function fetchAdminUsers(token, params = { page: 1, pageSize: 20 }) {
  const payload = await request(`/admin/users${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function updateAdminUserStatus(token, id, status) {
  const result = await request(`/admin/users/${id}/status`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ status })
  })
  return result.data || {}
}

export async function updateAdminUserRole(token, id, roleType) {
  const result = await request(`/admin/users/${id}/role`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ roleType })
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 教师 API（需 TEACHER 权限）
// ═════════════════════════════════════════

export async function fetchTeacherCourses(token) {
  const result = await request('/teacher/courses', {
    headers: authHeaders(token),
    cache: false
  })
  return result.data || []
}

export async function fetchCurriculums(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/curriculum${buildQuery(params)}`, {
    headers: authHeaders(token),
    cache: false
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 10
  }
}

export async function uploadCurriculumExcel(token, file) {
  const formData = new FormData()
  formData.append('file', file)
  const result = await request('/curriculum/upload', {
    method: 'POST',
    headers: authHeaders(token),
    body: formData
  })
  return result.data || {}
}

export async function downloadCurriculumTemplate(token) {
  const response = await fetch(`${API_BASE}/curriculum/template`, {
    headers: authHeaders(token)
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    let message = text || `Template download failed: ${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload.message || message
    } catch {}
    throw new Error(message)
  }

  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '课程导入模板.xlsx'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function createTeacherCourse(token, payload) {
  const result = await request('/teacher/courses', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function updateTeacherCourse(token, id, payload) {
  const result = await request(`/teacher/courses/${id}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function deleteTeacherCourse(token, id) {
  const result = await request(`/teacher/courses/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchTeacherMarketMatch(token, major) {
  const result = await request(`/teacher/market-match${buildQuery({ major })}`, {
    headers: authHeaders(token),
    cache: false
  })
  return result.data || {}
}

export async function fetchPlatformAdvisory(token) {
  const result = await request('/platform/advisory', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchTeacherTeachingReform(token, major) {
  const result = await request(`/teacher/teaching-reform${buildQuery({ major })}`, {
    headers: authHeaders(token),
    cache: false
  })
  return result.data || {}
}

export async function fetchTeacherMaterialStatus(token, major) {
  const result = await request(`/teacher/materials/status${buildQuery({ major })}`, {
    headers: authHeaders(token),
    cache: false
  })
  return result.data || {}
}

export async function uploadTeacherMaterial(token, materialType, file, major = '') {
  const formData = new FormData()
  formData.append('file', file)
  if (major) {
    formData.append('major', major)
  }
  const query = buildQuery({ materialType })
  const result = await request(`/teacher/materials/upload${query}`, {
    method: 'POST',
    headers: authHeaders(token),
    body: formData
  })
  return result.data || {}
}

export async function downloadTeacherMaterialTemplate(token, materialType) {
  const response = await fetch(`${API_BASE}/teacher/materials/template/${encodeURIComponent(materialType)}`, {
    headers: authHeaders(token)
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    let message = text || `Template download failed: ${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload.message || message
    } catch {}
    throw new Error(message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${materialType}.xlsx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function fetchDataSources(token) {
  const result = await request('/crawl/sources', {
    headers: authHeaders(token)
  })
  return result.data?.records || result.data || []
}

export async function fetchDataSourceDetail(token, id) {
  const result = await request(`/crawl/sources/${id}`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function createDataSource(token, payload) {
  const result = await request('/crawl/sources', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function updateDataSource(token, id, payload) {
  const result = await request(`/crawl/sources/${id}`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function deleteDataSource(token, id) {
  const result = await request(`/crawl/sources/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}
