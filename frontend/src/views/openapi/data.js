// ---------- Navigation groups ----------
// Each item describes a left-nav entry. `to` is either an absolute route path
// (possibly with a hash for same-page anchors) or `null` when the item is a
// not-yet-built placeholder (marked with `soon: true`).
export const navGroups = [
  {
    title: '入门',
    items: [
      { id: 'intro', label: 'API 总览', to: '/openapi/intro', tag: null, soon: false },
      { id: 'quickstart', label: '快速开始', to: '/openapi/quickstart', tag: null, soon: false },
      { id: 'auth', label: '认证与密钥', to: '/openapi/auth', tag: null, soon: false },
      { id: 'examples', label: '示例接口', to: '/openapi/examples', tag: null, soon: false }
    ]
  },
  {
    title: '应用',
    items: [
      { id: 'console', label: 'API 控制台', to: '/console', tag: null, soon: false }
    ]
  },
  {
    title: '参考',
    items: [
      { id: 'errors', label: '错误码', to: '/openapi/errors', tag: null, soon: false },
      { id: 'limits', label: '限流与配额', to: null, tag: null, soon: true },
      { id: 'dictionary', label: '字段字典', to: null, tag: null, soon: true },
      { id: 'changelog', label: '更新日志', to: null, tag: null, soon: true }
    ]
  }
]

// ---------- Mock data for API Console (no backend yet) ----------
// When the backend ships /auth/api-keys and /auth/usage endpoints, the
// component can swap these constants for real fetches — the shape is
// meant to match a typical API-key management response.

export const mockApiKeys = [
  {
    id: 'ak_1a2b3c',
    name: '教研数据看板',
    prefix: 'jc-live-8f3a42…',
    createdAt: '2026-03-22',
    lastUsedAt: '2026-04-17 14:02',
    status: 'active',
    scope: 'read-only'
  },
  {
    id: 'ak_4d5e6f',
    name: '院校就业周报',
    prefix: 'jc-live-5b1c09…',
    createdAt: '2026-02-14',
    lastUsedAt: '2026-04-15 09:30',
    status: 'active',
    scope: 'full'
  },
  {
    id: 'ak_7g8h9i',
    name: '2024 旧实验环境',
    prefix: 'jc-test-ae1b77…',
    createdAt: '2024-11-02',
    lastUsedAt: '2025-09-11 17:45',
    status: 'revoked',
    scope: 'read-only'
  }
]

export const mockUsage7d = [
  { date: '04-12', calls: 1820, errors: 12 },
  { date: '04-13', calls: 2240, errors: 18 },
  { date: '04-14', calls: 1980, errors: 9 },
  { date: '04-15', calls: 2610, errors: 22 },
  { date: '04-16', calls: 3105, errors: 14 },
  { date: '04-17', calls: 3442, errors: 27 },
  { date: '04-18', calls: 1284, errors: 3 }
]

export const mockQuota = {
  used: 48200,
  total: 100000,
  resetAt: '2026-05-01',
  rpsCurrent: 12,
  rpsLimit: 60,
  retentionDays: 30
}

export const mockConsoleStats = [
  { label: '今日调用', value: '1,284', change: '+12.4%', trend: 'up' },
  { label: '本月配额', value: '48.2%', change: '剩余 51,800 次', trend: 'flat' },
  { label: '活跃 Key', value: '2', change: '共 3 个（1 已撤销）', trend: 'flat' },
  { label: '平均延迟', value: '184 ms', change: '近 7 天 P95', trend: 'flat' }
]

// ---------- Content constants ----------

export const ledeText =
  '职涯OS 开放 API 将平台沉淀的职位、薪资、技能与院校就业大数据以标准化接口对外输出，支持学校、研究机构与第三方系统基于真实市场数据构建自己的教学、科研与产品应用。'

export const openDataItems = [
  {
    title: '职位数据 v3.2',
    desc: '覆盖 23 个招聘站点、2,444 条实时更新岗位，支持按行业、地区、学历多维筛选。'
  },
  {
    title: '技能图谱 v2.0',
    desc: '基于岗位 JD 抽取的技能共现网络与需求热度指标。'
  },
  {
    title: '智能推荐 v1.4',
    desc: '简历画像到岗位的闭环推荐，平均匹配度 91%。'
  }
]

export const accessModes = [
  {
    label: '定位',
    data: '面向结构化数据的实时查询接口',
    intel: '基于大数据与算法的分析、推荐与报告生成能力'
  },
  {
    label: '适用场景',
    data: '校园招聘看板、教研参考、院校就业大屏等对延迟敏感的轻量查询',
    intel: '自动化报告、岗位推荐、采集任务编排等长耗时或异步任务'
  },
  {
    label: '文档',
    data: '数据服务接口文档',
    intel: '智能服务接口文档'
  }
]

export const coreModules = [
  {
    id: 'module-jobs',
    icon: 'briefcase',
    title: '职位数据 API',
    desc: '岗位列表、详情、筛选、全文检索。支持按行业、城市、学历、薪资区间、经验年限组合过滤。',
    method: 'GET',
    path: '/v1/jobs',
    count: '6 个接口',
    tag: 'HOT'
  },
  {
    id: 'module-salary',
    icon: 'bar-chart-3',
    title: '薪资与市场 API',
    desc: '按行业、地区、学历维度输出薪资分布、中位数与百分位。含市场供需比与增长率等衍生指标。',
    method: 'GET',
    path: '/v1/salary/distribution',
    count: '5 个接口'
  },
  {
    id: 'module-skills',
    icon: 'sparkles',
    title: '技能图谱 API',
    desc: '从 JD 抽取的技能实体、共现关系与需求热度。支持按岗位/专业检索关键技能栈与课程对应关系。',
    method: 'GET',
    path: '/v1/skills/graph',
    count: '4 个接口',
    tag: 'NEW'
  },
  {
    id: 'module-education',
    icon: 'graduation-cap',
    title: '院校与专业就业 API',
    desc: '院校、专业维度的就业率、去向分布、对口率与起薪分析。助力院校管理决策与专业调优。',
    method: 'GET',
    path: '/v1/education/placement',
    count: '5 个接口'
  },
  {
    id: 'module-industry',
    icon: 'compass',
    title: '行业趋势 API',
    desc: '按行业大盘输出岗位供需、薪资走势、技能变迁，三年滑动窗口对比。',
    method: 'GET',
    path: '/v1/industry/trends',
    count: '3 个接口'
  },
  {
    id: 'module-recommend',
    icon: 'wand',
    title: '智能推荐 API',
    desc: '基于简历画像的岗位推荐与反向人才匹配。返回匹配度、关键特征贡献与可解释标签。',
    method: 'POST',
    path: '/v1/recommend/jobs',
    count: '3 个接口'
  },
  {
    id: 'module-report',
    icon: 'scroll-text',
    title: '报告生成 API',
    desc: '异步调用平台的报告自动生成能力：院校分析、行业趋势、评估论文等多模板一键输出。',
    method: 'POST',
    path: '/v1/reports',
    count: '4 个接口',
    tag: 'BETA'
  }
]

export const pathSteps = [
  { n: '1', title: '了解平台', desc: '阅读平台能力定位与数据更新机制，理解各模块之间的关系。' },
  { n: '2', title: '获取 API Key', desc: '进入控制台创建应用，生成一个以 jc- 开头的 API Key。' },
  { n: '3', title: '发起第一次请求', desc: '参考快速开始 5 行代码调用职位数据接口，拿到真实返回。' },
  { n: '4', title: '组合调用', desc: '配合技能图谱与薪资 API，搭建专业-岗位-技能的分析链路。' }
]

export const keyCapabilities = [
  {
    icon: 'refresh-cw',
    title: '实时更新',
    desc: '分布式采集模块每 6 小时拉取增量数据，核心指标 T+0 可查，保证策略决策的时效性。'
  },
  {
    icon: 'grid-2x2',
    title: '结构化字段',
    desc: '岗位、薪资、技能、院校等实体均已清洗、去重、标准化，直接返回符合教研与 BI 使用的 JSON。'
  },
  {
    icon: 'shield',
    title: '分级鉴权',
    desc: '支持 API Key、IP 白名单与 OAuth2 三种鉴权方式，配额按院校 / 项目维度隔离管理。'
  },
  {
    icon: 'terminal',
    title: '多语言 SDK',
    desc: '官方提供 Python / TypeScript / Java 三端 SDK，封装鉴权、重试、分页与流式返回，开箱即用。'
  }
]

export const supportText =
  '如需接入沟通、数据定制或额度提升，请通过控制台提交工单，或联系平台运营。常规工单会在一个工作日内响应。'

export const successCalloutText =
  '已为高校免费开放基础配额。院校邮箱注册后自动获得每月 10,000 次调用额度，覆盖常见教研与就业大屏使用场景。'

export const quickStartCommand = `curl -X GET "https://api.jobinsight.com/v1/reports?visibility=PUBLIC" \\
  -H "Accept: application/json" \\
  -H "X-App-Key: YOUR_APP_KEY" \\
  -H "X-Timestamp: 1713336000" \\
  -H "X-Signature: GENERATED_SIGNATURE"`

export const apiFacts = [
  {
    label: 'Base URL',
    value: 'https://api.jobinsight.com/v1',
    detail: '所有请求均以版本路径 /v1 访问。'
  },
  {
    label: '鉴权方式',
    value: 'AppKey + Signature',
    detail: '通过请求头传递应用标识、时间戳和签名。'
  },
  {
    label: '响应格式',
    value: 'JSON / UTF-8',
    detail: '统一返回 code、message、requestId 和 data 字段。'
  },
  {
    label: '默认限流',
    value: '60 req/min',
    detail: '按应用维度统计请求频次，可按需申请更高配额。'
  }
]

export const docHighlights = [
  {
    label: '认证',
    value: 'Header Signature',
    detail: '请求头中携带 X-App-Key、X-Timestamp 和 X-Signature。'
  },
  {
    label: '限流',
    value: '60 req/min',
    detail: '默认按应用维度进行限流，可根据接入规模申请扩容。'
  },
  {
    label: '追踪',
    value: 'requestId',
    detail: '每次响应都会返回唯一请求标识，便于排查和审计。'
  }
]

export const authRules = [
  {
    title: '请求头',
    detail: 'Header 中携带 X-App-Key、X-Timestamp、X-Signature。'
  },
  {
    title: '签名规则',
    detail: '按请求路径、时间戳和请求体拼接后使用 AppSecret 计算 SHA256。'
  },
  {
    title: '时间窗口',
    detail: 'X-Timestamp 默认 5 分钟有效，超时请求会被拒绝。'
  }
]

export const endpointGroups = [
  {
    name: '职位匹配',
    count: '2 个接口',
    summary: '根据用户画像、简历关键词和城市偏好返回岗位匹配结果与推荐理由。',
    endpoints: [
      { method: 'POST', path: '/jobs/match/analyze' },
      { method: 'GET', path: '/jobs/match/{taskId}' }
    ]
  },
  {
    name: '报告中心',
    count: '3 个接口',
    summary: '获取公开/私有报告列表、报告详情和导出任务状态。',
    endpoints: [
      { method: 'GET', path: '/reports' },
      { method: 'GET', path: '/reports/{reportId}' },
      { method: 'POST', path: '/reports/{reportId}/export' }
    ]
  },
  {
    name: '市场趋势',
    count: '2 个接口',
    summary: '按城市、岗位、技能维度返回市场热度、薪资带宽与供需趋势。',
    endpoints: [
      { method: 'GET', path: '/market/trends' },
      { method: 'GET', path: '/market/skills/heatmap' }
    ]
  },
  {
    name: 'AI 助手会话',
    count: '2 个接口',
    summary: '承接对话上下文、消息流和会话列表，方便接入第三方工作台。',
    endpoints: [
      { method: 'POST', path: '/assistant/chat' },
      { method: 'GET', path: '/assistant/sessions' }
    ]
  }
]

export const endpointExamples = [
  {
    title: '职位匹配分析',
    method: 'POST',
    path: '/jobs/match/analyze',
    summary: '提交候选人画像，返回职位匹配任务与首批推荐结果。',
    headers: [
      'Content-Type: application/json',
      'X-App-Key: YOUR_APP_KEY',
      'X-Timestamp: 1713336000',
      'X-Signature: GENERATED_SIGNATURE'
    ],
    params: [
      { name: 'city', type: 'string', required: '是', description: '目标城市，例如 Shanghai。' },
      { name: 'targetRole', type: 'string', required: '是', description: '目标岗位，例如 后端工程师。' },
      { name: 'skills', type: 'string[]', required: '是', description: '核心技能标签列表。' },
      { name: 'experienceYears', type: 'number', required: '否', description: '工作年限，用于排序权重。' }
    ],
    requestExample: `curl -X POST "https://api.jobinsight.com/v1/jobs/match/analyze" \\
  -H "Content-Type: application/json" \\
  -H "X-App-Key: YOUR_APP_KEY" \\
  -H "X-Timestamp: 1713336000" \\
  -H "X-Signature: GENERATED_SIGNATURE" \\
  -d '{
    "city": "Shanghai",
    "targetRole": "后端工程师",
    "skills": ["Java", "Spring Boot", "MySQL", "Redis"],
    "experienceYears": 2
  }'`,
    responseExample: `{
  "code": 0,
  "message": "ok",
  "requestId": "req_match_20260417_001",
  "data": {
    "taskId": "match_84d9c2",
    "items": [
      {
        "jobId": "job_1024",
        "title": "Java 后端工程师",
        "company": "星图数据",
        "city": "上海",
        "salaryRange": "25k-35k",
        "confidence": 92,
        "reason": "技能标签与岗位要求高度重合"
      }
    ]
  }
}`
  },
  {
    title: '查询报告详情',
    method: 'GET',
    path: '/reports/{reportId}',
    summary: '根据报告 ID 获取报告摘要、图表洞察、建议动作和可见范围。',
    headers: [
      'Accept: application/json',
      'X-App-Key: YOUR_APP_KEY',
      'X-Timestamp: 1713336000',
      'X-Signature: GENERATED_SIGNATURE'
    ],
    params: [
      { name: 'reportId', type: 'string', required: '是', description: '路径参数，报告唯一 ID。' },
      { name: 'includeCharts', type: 'boolean', required: '否', description: '是否返回图表摘要信息。' }
    ],
    requestExample: `curl -X GET "https://api.jobinsight.com/v1/reports/rpt_20260417001?includeCharts=true" \\
  -H "Accept: application/json" \\
  -H "X-App-Key: YOUR_APP_KEY" \\
  -H "X-Timestamp: 1713336000" \\
  -H "X-Signature: GENERATED_SIGNATURE"`,
    responseExample: `{
  "code": 0,
  "message": "ok",
  "requestId": "req_report_20260417_010",
  "data": {
    "reportId": "rpt_20260417001",
    "reportName": "华东地区后端岗位趋势报告",
    "visibility": "PRIVATE",
    "summary": "华东地区中高级 Java 岗位需求稳定增长。",
    "chartInsights": "上海、杭州的薪资中位数持续高于全国均值。",
    "recommendations": [
      "优先布局 Java + 微服务岗位",
      "加强缓存与消息队列相关能力"
    ]
  }
}`
  },
  {
    title: '获取市场趋势',
    method: 'GET',
    path: '/market/trends',
    summary: '按城市和岗位查询热度指数、职位数量与薪资区间。',
    headers: [
      'Accept: application/json',
      'X-App-Key: YOUR_APP_KEY',
      'X-Timestamp: 1713336000',
      'X-Signature: GENERATED_SIGNATURE'
    ],
    params: [
      { name: 'city', type: 'string', required: '否', description: '查询城市，例如 Beijing。' },
      { name: 'role', type: 'string', required: '否', description: '岗位名称，例如 数据分析师。' },
      { name: 'window', type: 'string', required: '否', description: '统计窗口，支持 7d / 30d / 90d。' }
    ],
    requestExample: `curl -X GET "https://api.jobinsight.com/v1/market/trends?city=Beijing&role=数据分析师&window=30d" \\
  -H "Accept: application/json" \\
  -H "X-App-Key: YOUR_APP_KEY" \\
  -H "X-Timestamp: 1713336000" \\
  -H "X-Signature: GENERATED_SIGNATURE"`,
    responseExample: `{
  "code": 0,
  "message": "ok",
  "requestId": "req_trend_20260417_021",
  "data": {
    "city": "Beijing",
    "role": "数据分析师",
    "window": "30d",
    "jobCount": 1832,
    "heatIndex": 78,
    "salaryMedian": "22k",
    "trend": "up"
  }
}`
  }
]

export const errorCodes = [
  { code: '0', meaning: '请求成功', action: '按 data 字段解析业务结果。' },
  { code: '40001', meaning: '签名错误', action: '检查 X-Signature、时间戳和请求体拼接顺序。' },
  { code: '40002', meaning: '缺少必填参数', action: '根据接口参数表补齐必填字段。' },
  { code: '40404', meaning: '资源不存在', action: '确认 reportId、taskId 或路径参数是否正确。' },
  { code: '42900', meaning: '超出限流阈值', action: '降低调用频率或申请更高配额。' },
  { code: '50000', meaning: '服务内部异常', action: '记录 requestId 并联系平台支持排查。' }
]

// ---------- Helpers ----------

export function methodClass(method) {
  return `method-badge ${String(method).toLowerCase()}`
}

export function requiredClass(required) {
  return `required-pill ${required === '是' ? 'yes' : 'no'}`
}

export function tagClass(tag) {
  if (!tag) return ''
  return `tag--${String(tag).toLowerCase()}`
}

// slugify an example title into a stable DOM id.
export function slugifyTitle(title) {
  return String(title)
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9\-\u4e00-\u9fa5]/g, '')
}

// ---------- Console usage breakdowns (mock) ----------
// Shape chosen to match a future real endpoint:
//   GET /auth/usage?groupBy=endpoint  -> mockEndpointUsage
//   GET /auth/usage?groupBy=key       -> mockKeyUsage
// Each row has { id, label, totalRequests, totalErrors, daily: [{date, calls, errors}] }
// where `daily` is 14 days ending today-ish (MM-DD strings, matches mockUsage7d).
//
// The daily series are hand-crafted (not random at module-load) so the
// chart has some plausible variance without jittering on every reload.
// Dates deliberately overlap mockUsage7d's tail (04-12 .. 04-18) so the
// last-week slice lines up with the existing "近 7 天" chart.

const USAGE_DATES_14D = [
  '04-05', '04-06', '04-07', '04-08', '04-09', '04-10', '04-11',
  '04-12', '04-13', '04-14', '04-15', '04-16', '04-17', '04-18'
]

// Helper: build a `daily` array from a pair of same-length arrays (calls,
// errors). Keeps data definitions compact and readable.
function makeDaily(calls, errors) {
  return USAGE_DATES_14D.map((date, i) => ({
    date,
    calls: calls[i] ?? 0,
    errors: errors[i] ?? 0
  }))
}

export const mockEndpointUsage = [
  {
    id: 'jobs',
    label: '职位 API',
    endpoints: ['/jobs', '/jobs/search', '/jobs/:id'],
    totalRequests: 8420,
    totalErrors: 32,
    daily: makeDaily(
      [520, 612, 498, 604, 712, 640, 588, 602, 694, 580, 672, 708, 750, 840],
      [2,   3,   1,   2,   3,   2,   1,   2,   3,   1,   3,   3,   4,   2]
    )
  },
  {
    id: 'analysis',
    label: '洞察分析 API',
    endpoints: ['/analysis/*'],
    totalRequests: 5110,
    totalErrors: 18,
    daily: makeDaily(
      [310, 340, 290, 360, 380, 342, 370, 360, 402, 348, 388, 420, 440, 460],
      [1,   1,   0,   2,   1,   1,   2,   1,   2,   1,   2,   2,   1,   1]
    )
  },
  {
    id: 'recommend',
    label: '推荐 API',
    endpoints: ['/recommend/*'],
    totalRequests: 3240,
    totalErrors: 12,
    daily: makeDaily(
      [180, 210, 232, 198, 240, 220, 244, 228, 250, 232, 258, 266, 282, 300],
      [0,   1,   1,   1,   1,   0,   1,   1,   1,   1,   1,   1,   2,   1]
    )
  },
  {
    id: 'reports',
    label: '报告 API',
    endpoints: ['/reports/*'],
    totalRequests: 2140,
    totalErrors: 7,
    daily: makeDaily(
      [120, 140, 132, 158, 142, 166, 150, 162, 148, 170, 156, 178, 184, 154],
      [0,   1,   0,   1,   0,   1,   1,   0,   1,   1,   0,   1,   0,   0]
    )
  },
  {
    id: 'ai',
    label: 'AI 助手 API',
    endpoints: ['/ai/chat', '/ai/agent'],
    totalRequests: 1870,
    totalErrors: 9,
    daily: makeDaily(
      [98, 110, 124, 108, 132, 140, 118, 144, 126, 138, 150, 162, 158, 162],
      [0,  1,   1,   0,   1,   1,   0,   1,   1,   0,   1,   1,   1,   0]
    )
  },
  {
    id: 'crawl',
    label: '数据采集 API',
    endpoints: ['/crawl/*'],
    totalRequests: 940,
    totalErrors: 4,
    daily: makeDaily(
      [48, 62, 58, 70, 66, 78, 64, 72, 68, 74, 72, 80, 66, 62],
      [0,  0,  1,  0,  0,  1,  0,  0,  1,  0,  0,  0,  1,  0]
    )
  },
  {
    id: 'admin',
    label: '运营 API',
    endpoints: ['/admin/*'],
    totalRequests: 320,
    totalErrors: 1,
    daily: makeDaily(
      [18, 22, 16, 24, 20, 26, 22, 24, 20, 28, 22, 26, 24, 28],
      [0,  0,  0,  0,  0,  1,  0,  0,  0,  0,  0,  0,  0,  0]
    )
  },
  {
    id: 'auth',
    label: '鉴权 API',
    endpoints: ['/auth/*'],
    totalRequests: 1120,
    totalErrors: 0,
    daily: makeDaily(
      [62, 70, 74, 82, 76, 88, 72, 84, 78, 90, 82, 94, 88, 80],
      [0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0]
    )
  }
]

// Per-key daily usage. `id` matches mockApiKeys[].id so the UI can join
// against the key row. Revoked keys still have history — backend target
// exposes them too.
export const mockKeyUsage = [
  {
    id: 'ak_1a2b3c',
    name: '教研数据看板',
    prefix: 'jc-live-8f3a42…',
    totalRequests: 14220,
    totalErrors: 48,
    daily: makeDaily(
      [820, 910, 870, 1004, 1120, 988, 940, 1060, 1150, 980, 1090, 1200, 1240, 848],
      [3,   4,   2,   5,    4,    3,   3,   4,    3,    4,   4,    5,    5,    3]
    )
  },
  {
    id: 'ak_4d5e6f',
    name: '院校就业周报',
    prefix: 'jc-live-5b1c09…',
    totalRequests: 6420,
    totalErrors: 22,
    daily: makeDaily(
      [362, 408, 394, 448, 502, 472, 430, 466, 494, 440, 488, 520, 542, 354],
      [1,   2,   1,   2,    2,   2,   1,   2,   2,   1,   2,   2,   1,   1]
    )
  },
  {
    id: 'ak_7g8h9i',
    name: '2024 旧实验环境',
    prefix: 'jc-test-ae1b77…',
    totalRequests: 0,
    totalErrors: 0,
    daily: makeDaily(
      [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
      [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
    )
  }
]

// Mini projects — currently fake, shape matches a future
// GET /auth/projects list. `color` is used to tint project chips if we
// ever surface multiple projects in the same view.
export const mockProjects = [
  { id: 'proj_default', name: '默认项目', color: '#0057c2' },
  { id: 'proj_lab', name: '教研实验', color: '#425d97' }
]
