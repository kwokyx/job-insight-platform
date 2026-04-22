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

export async function fetchWelfareDistribution(limit = 20) {
  const payload = await request(`/analysis/welfare${buildQuery({ limit })}`)
  return payload.data || {}
}

export async function fetchCompanySizeDistribution() {
  const payload = await request('/analysis/company-size')
  return payload.data || {}
}

export async function fetchFinanceStageDistribution() {
  const payload = await request('/analysis/finance-stage')
  return payload.data || {}
}

export async function fetchPersonalizedOverview(token) {
  const payload = await request('/analysis/overview/personalized', {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function fetchDeepInsights(token, params = {}) {
  const payload = await request(`/analysis/insights/deep${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
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

export async function fetchJobStats() {
  const payload = await request('/jobs/stats')
  return payload.data || {}
}

// ═════════════════════════════════════════
// 知识图谱 API
// ═════════════════════════════════════════

export async function fetchSkillMap(params = {}) {
  const payload = await request(`/kg/skill-map${buildQuery(params)}`)
  return payload.data || {}
}

export async function fetchJobSkillMatrix(params = {}) {
  const payload = await request(`/kg/job-skill-matrix${buildQuery(params)}`)
  return payload.data || {}
}

export async function fetchCareerLadder(jobTitle) {
  const payload = await request(`/kg/career-ladder/${encodeURIComponent(jobTitle)}`)
  return payload.data || {}
}

export async function buildKnowledgeGraph(token, minSupport = 3) {
  const result = await request(`/kg/build?minSupport=${encodeURIComponent(minSupport)}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 认证 API
// 对应后端 AuthController：基础路径 /api/v1/auth
// request() 会自动拼接 API_BASE，所以这里只写相对子路径。
// ═════════════════════════════════════════

// POST /auth/login —— 账号密码 + 验证码登录
// payload: { username, password, captchaId?, captchaCode? }
// 返回：{ accessToken, refreshToken, expiresIn, user }
export async function login(payload) {
  const result = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// POST /auth/register —— 用户注册，需要验证码
// payload: { username, password, email?, nickname?, roleType?, captchaId, captchaCode }
// 后端限制 roleType 只接受 0（学生）或 2（教师）
// 返回：{ userId, roleType }
export async function register(payload) {
  const result = await request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// GET /auth/profile —— 取当前登录用户完整资料
// 返回：{ id, username, nickname, roleType, avatarUrl, email, phone, lastLoginAt, createdAt }
export async function fetchAuthProfile(token) {
  const result = await request('/auth/profile', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

// PUT /auth/profile —— 更新昵称/邮箱/手机号/头像
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

// PUT /profile —— 更新职业画像（majorId、目标地域、薪资、技能等）
// 与 /auth/profile 不同：/auth/profile 管账号基础资料，这里管职业画像
export async function updateProfile(token, payload) {
  const result = await request('/profile', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

export async function updateProfileSkills(token, payload) {
  const result = await request('/profile/skills', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// PUT /auth/password —— 登录态下修改密码
// payload: { oldPassword, newPassword }；密码需至少 8 位且含字母+数字
export async function changeAuthPassword(token, payload) {
  const result = await request('/auth/password', {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// GET /auth/captcha?type=AUTO|MATH|CHAR —— 拉取验证码挑战
// 返回文本挑战：{ captchaId, captchaPrompt, captchaType, expiresInSeconds }
// 注意：不是图片流，前端自行把 captchaPrompt 渲染成可视化区域
export async function fetchCaptcha(type) {
  const query = type ? buildQuery({ type }) : ''
  const result = await request(`/auth/captcha${query}`, { cache: false })
  return result.data || {}
}

// POST /auth/refresh —— 用 refreshToken 换新的 accessToken
// payload: { refreshToken }；返回 { accessToken, expiresIn }
export async function refreshToken(payload) {
  const result = await request('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// POST /auth/password/reset/request —— 忘记密码第一步：用户名+邮箱+验证码换 resetToken
// 返回：{ resetToken, expiresInSeconds, username, maskedEmail }
export async function requestPasswordReset(payload) {
  const result = await request('/auth/password/reset/request', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
  return result.data || {}
}

// POST /auth/password/reset/confirm —— 忘记密码第二步：凭 resetToken 写入新密码
// payload: { resetToken, newPassword }
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

// GET /crawl/tasks/{id} —— 采集任务详情
export async function fetchCrawlTask(token, id) {
  const result = await request(`/crawl/tasks/${id}`, {
    headers: authHeaders(token)
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

export async function backfillCrawlQualityHistory(token) {
  const result = await request('/crawl/tasks/quality/history/backfill', {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 数据源 API（需认证，管理员）
// ═════════════════════════════════════════

export async function fetchDataSources(token) {
  const payload = await request('/crawl/sources', {
    headers: authHeaders(token)
  })
  const data = payload.data || {}
  return {
    records: data.records || [],
    count: data.count ?? (data.records ? data.records.length : 0)
  }
}

export async function fetchDataSource(token, id) {
  const payload = await request(`/crawl/sources/${id}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
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

// GET /recommend/ranker-status —— 查询 LTR 排序模型当前状态
export async function fetchRankerStatus(token) {
  const result = await request('/recommend/ranker-status', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

// POST /recommend/train-ranker —— 触发排序模型训练
// options: { limit?: number } 作为 query string 传给后端
export async function trainRanker(token, options = {}) {
  const query = buildQuery(options || {})
  const result = await request(`/recommend/train-ranker${query}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 岗位收藏 API（需认证）
// ═════════════════════════════════════════

// GET /favorites —— 我的收藏列表（分页）
export async function fetchFavorites(token, params = {}) {
  const payload = await request(`/favorites${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || params.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

// POST /favorites/{jobId} —— 收藏岗位
export async function addFavorite(token, jobId) {
  const result = await request(`/favorites/${jobId}`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

// DELETE /favorites/{jobId} —— 取消收藏
export async function removeFavorite(token, jobId) {
  const result = await request(`/favorites/${jobId}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

// GET /favorites/{jobId}/check —— 检查是否已收藏，返回 boolean
export async function checkFavorite(token, jobId) {
  const result = await request(`/favorites/${jobId}/check`, {
    headers: authHeaders(token)
  })
  return Boolean(result.data?.favorited)
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

export async function fetchAiQuickCommands(token) {
  const result = await request('/ai/quick-commands', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function parseResumeViaAi(token, file) {
  const formData = new FormData()
  formData.append('file', file)

  const response = await fetch(`${API_BASE}/ai/agent/parse-resume`, {
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

// GET /reports/{id}/download —— 取下载元信息（不是 PDF 本身，含 pdfUrl/viewCount 等字段）
export async function fetchReportDownloadMeta(token, id) {
  const result = await request(`/reports/${id}/download`, {
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

// 前置分析就绪状态（学生是否做过智能推荐 / 教师是否已备齐课程资料）
// 后端现已提供 GET /reports/readiness。
// 调用方仍需处理 null 返回值，以兼容鉴权失败、网络异常或后端暂不可用时的本地兜底逻辑。
// 当前返回结构示例：
//   {
//     roleType: 0|1|2,
//     roleLabel: string,
//     status: 'ready' | 'missing_input',
//     ready: boolean,
//     missingRequirements: [{ title: string, detail: string, routePath: string, actionLabel: string, action: { path: string, label: string, detail: string } }],
//     primaryAction: { path: string, label: string, detail: string } | null,
//     assistant: { ready: boolean, message: string, nextPath?: string }
//   }
export async function fetchReportReadiness(token) {
  try {
    const result = await request('/reports/readiness', {
      headers: authHeaders(token)
    })
    return result.data || null
  } catch (e) {
    // 接口异常时，返回 null 让调用方走本地兜底
    return null
  }
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
  ids.forEach((id) => params.append('ids', id))
  const result = await request(`/reports/batch?${params.toString()}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || result.message || true
}

export async function fetchReportSchedules(token) {
  const result = await request('/reports/schedules', {
    headers: authHeaders(token)
  })
  return result.data || []
}

// GET /reports/schedules/{id} —— 调度详情
export async function fetchReportSchedule(token, id) {
  const result = await request(`/reports/schedules/${id}`, {
    headers: authHeaders(token)
  })
  return result.data || {}
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

export async function fetchPublicationQueue(token, params = {}) {
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

export async function submitReportReview(token, id, payload) {
  const result = await request(`/reports/${id}/submit-review`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

export async function reviewReport(token, id, payload) {
  const result = await request(`/reports/${id}/review`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

export async function publishReport(token, id, payload) {
  const result = await request(`/reports/${id}/publish`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
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

export async function fetchReportVersions(token, id) {
  const payload = await request(`/reports/${id}/versions`, {
    headers: authHeaders(token)
  })
  return payload.data || []
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



export async function fetchSkillGraph(topN = 30) {
  const result = await request(`/analysis/skills/graph${buildQuery({ topN })}`)
  return result.data || {}
}

// ═════════════════════════════════════════
// 深度分析 API（需认证）
// ═════════════════════════════════════════

export async function fetchSupplyDemand(token, params = {}) {
  const payload = await request(`/analysis/deep/supply-demand${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function runSupplyDemandAnalysis(token, payload) {
  const result = await request('/analysis/deep/supply-demand', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

export async function runCurriculumGap(token, payload) {
  const result = await request('/analysis/deep/curriculum-gap', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

export async function fetchSalaryPremium(token, params = {}) {
  const payload = await request(`/analysis/deep/salary-premium${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function fetchTrendForecast(token, params = {}) {
  const payload = await request(`/analysis/deep/trend-forecast${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function runEtl(token, payload) {
  const result = await request('/analysis/deep/etl/run', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

export async function fetchWarehouseOverview(token) {
  const payload = await request('/analysis/deep/warehouse/overview', {
    headers: authHeaders(token)
  })
  return payload.data || {}
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

export async function fetchSubscriptions(token, params = {}) {
  const payload = await request(`/subscriptions${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  const records = Array.isArray(payload.data) ? payload.data : (payload.data?.records || [])
  return {
    data: records,
    total: payload.total || 0,
    page: payload.page || params.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

export async function deleteSubscription(token, id) {
  const result = await request(`/subscriptions/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchSubscriptionMatches(token, id) {
  const payload = await request(`/subscriptions/${id}/matches`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function dispatchSubscription(token, id) {
  const result = await request(`/subscriptions/${id}/dispatch`, {
    method: 'POST',
    headers: authHeaders(token)
  })
  return result.data || {}
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

export async function fetchAdminLogs(token, params = {}) {
  const payload = await request(`/admin/logs${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 20
  }
}

// ═════════════════════════════════════════
// 教师 API（需 TEACHER 权限）
// ═════════════════════════════════════════

export async function fetchTeacherCourses(token) {
  const result = await request('/teacher/courses', {
    headers: authHeaders(token)
  })
  return result.data || []
}

export async function fetchCurriculums(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/curriculum${buildQuery(params)}`, {
    headers: authHeaders(token)
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

export async function fetchTeacherMarketMatch(token) {
  const result = await request('/teacher/market-match', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function fetchTeachingReform(token, params = {}) {
  const payload = await request(`/teacher/teaching-reform${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

// GET /teacher/materials/status —— 教师备课资料准备状态
export async function fetchTeacherMaterialStatus(token) {
  const result = await request('/teacher/materials/status', {
    headers: authHeaders(token)
  })
  return result.data || {}
}

// POST /teacher/materials/upload —— 上传教学资料 Excel（multipart）
// formData 至少含 file、materialType（SYLLABUS / STUDENT_STATUS），可含 major
// 注意：不要自己设 Content-Type，浏览器会自动带 boundary
export async function uploadTeacherMaterial(token, formData) {
  const result = await request('/teacher/materials/upload', {
    method: 'POST',
    headers: authHeaders(token),
    body: formData
  })
  return result.data || {}
}

// GET /teacher/materials/template/{materialType} —— 下载资料模板（xlsx Blob）
export async function downloadTeacherMaterialTemplate(token, materialType) {
  const response = await fetch(
    `${API_BASE}/teacher/materials/template/${encodeURIComponent(materialType)}`,
    { headers: authHeaders(token) }
  )
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    let message = text || `模板下载失败: ${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload.message || message
    } catch {
      // ignore
    }
    throw new Error(message)
  }
  return await response.blob()
}

// ═════════════════════════════════════════
// 院校课程 API（需认证）
// ═════════════════════════════════════════

export async function fetchCurriculumTemplate(token) {
  const payload = await request('/curriculum/template', {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function fetchCurriculumSkills(token, id) {
  const payload = await request(`/curriculum/${id}/skills`, {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

export async function deleteCurriculum(token, id) {
  const result = await request(`/curriculum/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 平台元数据 API
// ═════════════════════════════════════════

export async function fetchPlatformAdvisory(token) {
  const payload = await request('/platform/advisory', {
    headers: authHeaders(token)
  })
  return payload.data || {}
}

// ═════════════════════════════════════════
// 开放平台元数据 API
// ═════════════════════════════════════════

export async function fetchOpenMeta() {
  const payload = await request('/open/meta')
  return payload.data || {}
}

export async function fetchOpenCapabilities() {
  const payload = await request('/open/capabilities')
  return payload.data || {}
}

export async function fetchOpenSubscriptionsMeta() {
  const payload = await request('/open/subscriptions/meta')
  return payload.data || {}
}

export async function fetchOpenAnalysisIndustry(params = {}) {
  const payload = await request(`/open/analysis/industry${buildQuery(params)}`)
  return payload.data || {}
}

export async function fetchOpenAnalysisInsights(params = {}) {
  const payload = await request(`/open/analysis/insights${buildQuery(params)}`)
  return payload.data || {}
}

// GET /open/analysis/salary —— 开放接口：薪资分布（无需 token）
export async function fetchOpenSalaryAnalysis(params = {}) {
  const payload = await request(`/open/analysis/salary${buildQuery(params)}`)
  return payload.data || {}
}

// GET /open/analysis/trend —— 开放接口：趋势数据（无需 token）
export async function fetchOpenTrendAnalysis(params = {}) {
  const payload = await request(`/open/analysis/trend${buildQuery(params)}`)
  return payload.data || {}
}

// GET /open/reports/public-scoped —— 租户受限的公开报告列表（需 token 识别租户）
export async function fetchPublicReportsScoped(token, params = { page: 1, pageSize: 10 }) {
  const payload = await request(`/open/reports/public-scoped${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  return {
    data: payload.data || [],
    total: payload.total || 0,
    page: payload.page || 1,
    pageSize: payload.pageSize || params.pageSize || 10
  }
}

// GET /open/reports/{id} —— 公开报告详情
export async function fetchPublicReportDetail(token, id) {
  const result = await request(`/open/reports/${id}`, {
    headers: authHeaders(token)
  })
  return result.data || {}
}

// 开放平台 API Key 管理与审计（需 ADMIN 权限）
export async function fetchOpenApiKeys(token) {
  const payload = await request('/open/api-keys', {
    headers: authHeaders(token)
  })
  return payload.data || []
}

// POST /open/api-keys —— 创建 API Key（ADMIN）
export async function createOpenApiKey(token, payload) {
  const result = await request('/open/api-keys', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload || {})
  })
  return result.data || {}
}

// PUT /open/api-keys/{id}/toggle —— 启用/停用 API Key
export async function toggleOpenApiKey(token, id, enabled) {
  const result = await request(`/open/api-keys/${id}/toggle`, {
    method: 'PUT',
    headers: authHeaders(token),
    body: JSON.stringify({ active: Boolean(enabled) })
  })
  return result.data || {}
}

export async function fetchOpenApiKeyLogs(token, params = { page: 1, pageSize: 20 }) {
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

// ═════════════════════════════════════════
// Webhook API（需认证）
// ═════════════════════════════════════════

export async function fetchWebhooks(token) {
  const payload = await request('/webhooks', {
    headers: authHeaders(token)
  })
  return payload.data || []
}

export async function fetchWebhookDeliveries(token, id) {
  const payload = await request(`/webhooks/${id}/deliveries`, {
    headers: authHeaders(token)
  })
  return payload.data || []
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
  return result.data || {}
}

export async function toggleWebhook(token, id) {
  const result = await request(`/webhooks/${id}/toggle`, {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || {}
}

// ═════════════════════════════════════════
// 通知 API（需认证）
// ═════════════════════════════════════════

export async function fetchNotifications(token, params = {}) {
  // 后端返回结构：R.ok({ records, total, unreadCount }) → payload.data = { records, total, unreadCount }
  const payload = await request(`/notifications${buildQuery(params)}`, {
    headers: authHeaders(token)
  })
  const body = payload.data || {}
  return {
    data: Array.isArray(body.records) ? body.records : (body.records || []),
    total: body.total || 0,
    unreadCount: body.unreadCount || 0,
    page: params.page || 1,
    pageSize: params.pageSize || 20
  }
}

export async function markNotificationRead(token, id) {
  const result = await request(`/notifications/${id}/read`, {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || {}
}

export async function markAllNotificationsRead(token) {
  const result = await request('/notifications/read-all', {
    method: 'PUT',
    headers: authHeaders(token)
  })
  return result.data || {}
}
