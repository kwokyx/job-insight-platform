const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'
const ALGO_BASE = import.meta.env.VITE_ALGO_BASE || 'http://localhost:8000'

async function request(path, options = {}) {
  const mergedHeaders = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: mergedHeaders
  })

  const payload = await response.json().catch(() => ({}))
  if (!response.ok || (payload.code && payload.code !== 200)) {
    throw new Error(payload.message || `请求失败: ${response.status}`)
  }
  return payload
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

export async function fetchSimilarJobs(token, jobId, limit = 10) {
  const payload = await request(`/recommend/similar-jobs/${jobId}${buildQuery({ limit })}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
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

export async function openReportPdf(token, id) {
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
  window.open(url, '_blank', 'noopener,noreferrer')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export function normalizeError(error) {
  if (!error) {
    return '未知错误'
  }
  return error.message || String(error)
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

export async function fetchTrendForecast(params = {}) {
  // 趋势预测走后端算法代理
  const result = await request('/analysis/salary/trend' + buildQuery(params))
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

export async function scoreResume(payload) {
  return await algoRequest('/algorithm/resume/score', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function fetchSkillEvolution(skills, windowMonths = 12) {
  return await algoRequest('/algorithm/skills/evolution', {
    method: 'POST',
    body: JSON.stringify({ skills, window_months: windowMonths })
  })
}
