const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
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

  while (!finished) {
    const { value, done } = await reader.read()
    finished = done
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''

    for (const chunk of chunks) {
      const lines = chunk
        .split('\n')
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
        continue
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
  }
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

export function normalizeError(error) {
  if (!error) {
    return '未知错误'
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

