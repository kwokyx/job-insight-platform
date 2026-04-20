const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

// ───────────────────────────────────────────────────────────
// Lightweight in-memory cache for idempotent (GET) requests
// - Returns cached payload for up to CACHE_TTL ms (default 30 s)
// - Deduplicates concurrent GETs for the same key → one network
//   round-trip even if several components ask for the same thing
//   in the same tick
// - Keyed by method + path + Authorization header so switching
//   users doesn't reuse the previous user's data
//
// Call `invalidateApiCache()` (full) or `invalidateApiCache(prefix)`
// (substring match) after a mutation to drop stale entries.
// Pass `{ cache: false }` in options to opt a specific call out
// (useful for polling / status checks).
// ───────────────────────────────────────────────────────────
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

async function request(path, options = {}) {
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
    const response = await fetch(`${API_BASE}${path}`, {
      ...fetchOptions,
      headers: {
        'Content-Type': 'application/json',
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

function buildQuery(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      search.set(key, value)
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

function authHeaders(token) {
  return token
    ? {
        Authorization: `Bearer ${token}`
      }
    : {}
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

// ═════════════════════════════════════════
// 分析 API（公开只读）
// ═════════════════════════════════════════

export async function fetchAnalysisOverview() {
  const payload = await request('/analysis/overview')
  return payload.data || {}
}

export async function fetchSalaryAnalysis(groupBy = 'city', limit = 20) {
  const payload = await request(`/analysis/salary${buildQuery({ groupBy, limit })}`)
  return payload.data || {}
}

export async function fetchSalaryTrend(params = {}) {
  const payload = await request(`/analysis/salary/trend${buildQuery(params)}`)
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

export async function changeAuthPassword(token, payload) {
  const result = await request('/auth/password', {
    method: 'PUT',
    headers: authHeaders(token),
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

export async function recommendSkillRadar(token, payload) {
  const result = await request('/recommend/skill-radar', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
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

export async function fetchSimilarJobs(jobId, limit = 10) {
  const payload = await request(`/recommend/similar-jobs/${jobId}${buildQuery({ limit })}`)
  return payload.data || {}
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

export async function fetchAiQuota(token) {
  const result = await request('/ai/quota', {
    headers: authHeaders(token)
  })
  return result.data || {}
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

export async function fetchReportSchedules(token) {
  const result = await request('/reports/schedules', {
    headers: authHeaders(token)
  })
  return result.data || []
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

export async function exportReportPdf(token, id, fileName = `report-${id}.pdf`) {
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
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function normalizeError(error) {
  if (!error) {
    return 'Unknown error'
  }
  return error.message || String(error)
}

// ═════════════════════════════════════════
// 算法服务 API（通过后端代理调用）
// ═════════════════════════════════════════

export async function predictSalary(payload) {
  const result = await request('/analysis/salary/predict', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function fetchMarketSentiment(params = {}) {
  const result = await request(`/analysis/sentiment${buildQuery(params)}`)
  return result.data || {}
}

export async function fetchTrendForecast(params = {}) {
  // 趋势预测走后端算法代理
  const result = await request('/analysis/salary/trend' + buildQuery(params))
  return result.data || {}
}

export async function fetchSkillGraph(topN = 30) {
  const result = await request(`/analysis/skills/graph${buildQuery({ topN })}`)
  return result.data || {}
}
