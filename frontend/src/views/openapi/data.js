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
      { id: 'limits', label: '限流与配额', to: '/openapi/limits', tag: null, soon: false },
      { id: 'dictionary', label: '字段字典', to: '/openapi/dictionary', tag: null, soon: false },
      { id: 'changelog', label: '更新日志', to: '/openapi/changelog', tag: null, soon: false }
    ]
  }
]

export const ledeText =
  '职业能力平台开放 API 面向岗位、分析与公开报告能力，文档只覆盖当前仓库后端已上线接口，避免出现未接入功能描述。'

export const openDataItems = [
  {
    title: '岗位查询',
    desc: '支持按关键词、城市、行业分页查询岗位，接口为 /api/v1/open/jobs。'
  },
  {
    title: '市场分析快照',
    desc: '支持 /analysis/overview、/skills、/salary、/trend、/industry、/insights 等只读分析接口。'
  },
  {
    title: '公开报告访问',
    desc: '支持公开报告列表与详情接口，覆盖 /reports/public、/reports/public-scoped、/reports/{id}。'
  }
]

export const accessModes = [
  {
    label: '定位',
    data: '开放只读查询接口',
    intel: '管理端密钥与审计接口（管理员）'
  },
  {
    label: '认证方式',
    data: '可匿名访问；携带 X-API-Key 可获得租户范围和配额上下文',
    intel: '需要平台登录态（Bearer）访问控制台，再调用 /open/api-keys* 管理接口'
  },
  {
    label: '适用场景',
    data: '第三方看板、院校数据展示、轻量报表查询',
    intel: '创建/停用 API Key，审计调用日志，查看密钥配额与状态'
  },
  {
    label: '文档入口',
    data: '开放接口文档',
    intel: '控制台与管理员接口文档'
  }
]

export const coreModules = [
  {
    id: 'module-meta',
    icon: 'info',
    title: '平台元数据',
    desc: '返回开放 API 元信息、能力清单与订阅元数据，便于调用方完成接入自检。',
    method: 'GET',
    path: '/api/v1/open/meta',
    count: '3 个接口',
    tag: null
  },
  {
    id: 'module-jobs',
    icon: 'briefcase',
    title: '岗位数据 API',
    desc: '分页查询岗位列表，支持 keyword/city/industry 过滤，返回标准分页结构。',
    method: 'GET',
    path: '/api/v1/open/jobs',
    count: '1 个接口',
    tag: 'HOT'
  },
  {
    id: 'module-analysis',
    icon: 'bar-chart-3',
    title: '分析快照 API',
    desc: '提供总览、技能、薪资分布、趋势、行业快照与洞察等分析查询。',
    method: 'GET',
    path: '/api/v1/open/analysis/*',
    count: '6 个接口',
    tag: 'NEW'
  },
  {
    id: 'module-report',
    icon: 'scroll-text',
    title: '公开报告 API',
    desc: '返回公开报告列表与详情，支持 public-scoped 版本按租户范围过滤。',
    method: 'GET',
    path: '/api/v1/open/reports/*',
    count: '3 个接口',
    tag: null
  },
  {
    id: 'module-key',
    icon: 'shield',
    title: 'API Key 管理',
    desc: '管理员可创建、停用 API Key 并查看调用审计日志，全部已接入后端真实接口。',
    method: 'POST/GET/PUT',
    path: '/api/v1/open/api-keys*',
    count: '3 个接口',
    tag: 'ADMIN'
  }
]

export const pathSteps = [
  { n: '1', title: '确认 Base URL', desc: '统一通过 /api/v1/open 访问开放接口。' },
  { n: '2', title: '先跑通只读接口', desc: '优先调用 /analysis/overview 或 /jobs，确认网络与响应结构。' },
  { n: '3', title: '按需携带 X-API-Key', desc: '需要租户范围与配额信息时，在请求头增加 X-API-Key。' },
  { n: '4', title: '进入控制台管理密钥', desc: '管理员在 API 控制台创建/停用密钥并查看审计日志。' }
]

export const keyCapabilities = [
  {
    icon: 'check-circle-2',
    title: '真实接口对齐',
    desc: '文档只展示当前仓库后端可访问接口，不包含未实现能力。'
  },
  {
    icon: 'shield',
    title: '统一密钥头',
    desc: '开放接口统一使用 X-API-Key，不使用 AppKey+Signature。'
  },
  {
    icon: 'terminal',
    title: '标准响应结构',
    desc: '返回 code/message/data/total/page/pageSize/timestamp，便于前后端统一处理。'
  },
  {
    icon: 'refresh-cw',
    title: '可审计调用',
    desc: '请求会带 X-Request-Id，携带有效密钥的调用会进入审计日志。'
  }
]

export const supportText =
  '如需扩展字段、提高配额或开通租户范围策略，请由管理员在 API 控制台提交申请并附带 requestId 样例。'

export const quickStartCommand = `curl -X GET "https://your-domain.com/api/v1/open/analysis/overview" \\
  -H "Accept: application/json" \\
  -H "X-API-Key: YOUR_API_KEY"`

export const apiFacts = [
  {
    label: 'Base URL',
    value: '/api/v1/open',
    detail: '开放接口统一挂载在该路径下。'
  },
  {
    label: '认证头',
    value: 'X-API-Key',
    detail: '读取接口可匿名调用；需要配额与租户上下文时传入 X-API-Key。'
  },
  {
    label: '响应格式',
    value: 'JSON / UTF-8',
    detail: '统一包含 code、message、data、timestamp 等字段。'
  },
  {
    label: '成功码',
    value: 'code = 200',
    detail: '本项目前端按 code===200 判定成功，不使用 code=0。'
  }
]

export const docHighlights = [
  {
    label: '请求追踪',
    value: 'X-Request-Id',
    detail: '每次请求都会返回 requestId，便于日志追踪与问题定位。'
  },
  {
    label: '配额头',
    value: 'X-RateLimit-*',
    detail: '携带有效 API Key 时会返回配额上限与剩余信息。'
  },
  {
    label: '租户范围',
    value: 'X-Tenant-Scope',
    detail: '响应头返回当前调用生效的租户范围，默认 public。'
  }
]

export const authRules = [
  {
    title: '开放读取接口',
    detail: '如 /meta、/jobs、/analysis/*、/reports/public 可直接访问；传 X-API-Key 可携带权限上下文。'
  },
  {
    title: '受限接口头',
    detail: '调用方仅需在 Header 携带 X-API-Key，不需要 X-Signature 与 AppSecret。'
  },
  {
    title: '管理员接口',
    detail: '/open/api-keys* 由平台管理员调用，控制台通过登录态（Bearer）访问这些管理接口。'
  }
]

export const endpointExamples = [
  {
    title: '分页查询岗位',
    method: 'GET',
    path: '/jobs',
    summary: '按关键词、城市与行业查询岗位列表，返回标准分页字段。',
    headers: [
      'Accept: application/json',
      'X-API-Key: YOUR_API_KEY (可选)'
    ],
    params: [
      { name: 'keyword', type: 'string', required: '否', description: '岗位标题或公司关键词。' },
      { name: 'city', type: 'string', required: '否', description: '城市筛选，如 Shanghai。' },
      { name: 'industry', type: 'string', required: '否', description: '行业筛选。' },
      { name: 'page', type: 'number', required: '否', description: '页码，默认 1。' },
      { name: 'pageSize', type: 'number', required: '否', description: '每页数量，默认 20，最大 50。' }
    ],
    requestExample: `curl -X GET "https://your-domain.com/api/v1/open/jobs?keyword=Java&city=Shanghai&page=1&pageSize=20" \\
  -H "Accept: application/json" \\
  -H "X-API-Key: YOUR_API_KEY"`,
    responseExample: `{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1024,
      "title": "Java后端工程师",
      "companyName": "示例科技",
      "city": "Shanghai",
      "industryName": "互联网",
      "salaryText": "25k-35k"
    }
  ],
  "total": 1832,
  "page": 1,
  "pageSize": 20,
  "timestamp": "2026-04-23T10:20:00"
}`
  },
  {
    title: '获取分析总览',
    method: 'GET',
    path: '/analysis/overview',
    summary: '返回岗位总量、热门城市、热门行业与热门技能等概览指标。',
    headers: [
      'Accept: application/json',
      'X-API-Key: YOUR_API_KEY (可选)'
    ],
    params: [],
    requestExample: `curl -X GET "https://your-domain.com/api/v1/open/analysis/overview" \\
  -H "Accept: application/json" \\
  -H "X-API-Key: YOUR_API_KEY"`,
    responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "totalJobs": 128564,
    "topCities": [
      { "name": "Shanghai", "value": 18620 }
    ],
    "topIndustries": [
      { "name": "互联网", "value": 24110 }
    ],
    "topSkills": [
      { "name": "Java", "value": 13240 }
    ]
  },
  "timestamp": "2026-04-23T10:21:00"
}`
  },
  {
    title: '查询公开报告详情',
    method: 'GET',
    path: '/reports/{id}',
    summary: '根据报告 ID 获取公开报告详情，未发布或越权范围会返回 404。',
    headers: [
      'Accept: application/json',
      'X-API-Key: YOUR_API_KEY (可选)'
    ],
    params: [
      { name: 'id', type: 'number', required: '是', description: '报告 ID（路径参数）。' }
    ],
    requestExample: `curl -X GET "https://your-domain.com/api/v1/open/reports/1001" \\
  -H "Accept: application/json" \\
  -H "X-API-Key: YOUR_API_KEY"`,
    responseExample: `{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1001,
    "reportName": "华东地区就业趋势报告",
    "reportType": "industry",
    "description": "公开报告示例",
    "analysisData": {},
    "generatedAt": "2026-04-22T18:00:00",
    "viewCount": 236
  },
  "timestamp": "2026-04-23T10:22:00"
}`
  }
]

export const errorCodes = [
  { code: '200', meaning: '请求成功', action: '按 data 字段解析业务结果。' },
  { code: '401', meaning: '密钥无效或已过期', action: '检查 X-API-Key 是否正确、是否被停用或过期。' },
  { code: '403', meaning: '权限不足', action: '确认当前账号是否具备管理员权限（管理接口）。' },
  { code: '404', meaning: '资源不存在', action: '检查报告 ID、路径参数与资源可见性。' },
  { code: '429', meaning: '触发限流或配额耗尽', action: '降低调用频率，或在控制台提升配额。' },
  { code: '500', meaning: '服务内部异常', action: '记录 requestId 并联系平台支持排查。' }
]

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

export function slugifyTitle(title) {
  return String(title)
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9\-\u4e00-\u9fa5]/g, '')
}
