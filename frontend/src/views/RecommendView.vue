<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import {
  fetchJobDetail,
  fetchPersonalizedRecommendPlan,
  fetchRecommendHealth,
  importAiProfileFile,
  normalizeError,
  predictSalary,
  recommendCareerPath,
  recommendJobs,
  recommendSkillRadar,
  recommendSkills,
  scoreResume
} from '../api'
import {
  ArrowRight,
  Bot,
  Building2,
  Calculator,
  Clock,
  Compass,
  ExternalLink,
  FileSearch,
  FileUp,
  GraduationCap,
  Inbox,
  MapPin,
  Radar,
  Sparkles,
  Target,
  X
} from 'lucide-vue-next'
import { useAuthStore } from '../store/auth'
import { markStudentRecommendDone } from '../utils/reportReadiness'

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const planLoading = ref(false)
const error = ref('')
const infoMessage = ref('')
const importLoading = ref(false)
const importSuccess = ref('')
const success = ref('')
const personalizedPlan = ref(null)
const recommendHealth = ref(null)
const selectedJob = ref(null)
const isLoadingJobDetail = ref(false)
const healthLoading = ref(false)
const prototypeState = ref({
  jobs: false,
  skills: false,
  path: false,
  resume: false,
  import: false,
  salary: false
})

const MOCK_RESULTS = {
  jobs: [
    {
      id: 'mock-job-1',
      isMock: true,
      title: 'Java 后端工程师',
      companyName: '曜石云数据',
      city: '上海',
      salaryText: '24K-32K',
      confidence: 92,
      matchedSkills: ['Java', 'Spring Boot', 'MySQL'],
      reason: '技能栈和城市偏好高度吻合，适合直接投递中型企业核心后端岗位。',
      education: '本科',
      experience: '3-5年',
      industryName: '企业服务',
      description: '负责中后台服务设计与迭代，维护交易链路稳定性，并与产品和前端协作推进需求上线。',
      requirements: '熟悉 Java / Spring Boot / MySQL，具备接口设计、缓存和性能调优经验。'
    },
    {
      id: 'mock-job-2',
      isMock: true,
      title: '平台研发工程师',
      companyName: '北川智能',
      city: '北京',
      salaryText: '26K-34K',
      confidence: 88,
      matchedSkills: ['Redis', 'Docker', 'API 设计'],
      reason: '更偏平台底座方向，适合往工程效率和基础设施能力继续延展。',
      education: '本科',
      experience: '3-5年',
      industryName: 'AI 基础设施',
      description: '参与平台基础能力建设，包括统一权限、任务调度与服务治理模块。',
      requirements: '具备 Java 服务开发经验，理解缓存、容器化部署和基础运维协作。'
    },
    {
      id: 'mock-job-3',
      isMock: true,
      title: '资深后端开发',
      companyName: '岚石科技',
      city: '深圳',
      salaryText: '28K-36K',
      confidence: 85,
      matchedSkills: ['Spring Cloud', 'Redis', '系统设计'],
      reason: '对服务拆分和稳定性要求更高，适合下一阶段往架构能力过渡。',
      education: '本科',
      experience: '5年左右',
      industryName: 'SaaS',
      description: '负责多租户 SaaS 平台核心后端模块，推进系统扩展性和稳定性优化。',
      requirements: '熟悉微服务、消息队列、缓存和高并发场景下的性能优化。'
    },
    {
      id: 'mock-job-4',
      isMock: true,
      title: '业务架构支持工程师',
      companyName: '矩阵零售',
      city: '杭州',
      salaryText: '23K-30K',
      confidence: 83,
      matchedSkills: ['Java', 'SQL 建模', '跨团队协作'],
      reason: '业务复杂度适中，适合作为从交付型开发向方案设计过渡的跳板。',
      education: '本科',
      experience: '3-5年',
      industryName: '零售科技',
      description: '连接业务系统与数据中台，负责复杂业务流程的系统拆解与方案落地。',
      requirements: '具备较强业务理解力，能独立完成接口设计和库表建模。'
    },
    {
      id: 'mock-job-5',
      isMock: true,
      title: '中间件研发工程师',
      companyName: '深空云科',
      city: '广州',
      salaryText: '27K-35K',
      confidence: 80,
      matchedSkills: ['消息队列', 'Docker', '监控治理'],
      reason: '对系统底层能力要求更集中，适合补齐可观测性和中间件治理经验。',
      education: '本科',
      experience: '3-5年',
      industryName: '云计算',
      description: '参与消息、缓存和服务治理相关中间件能力的集成与维护。',
      requirements: '理解分布式系统基础概念，具备服务部署、排障和性能调优经验。'
    },
    {
      id: 'mock-job-6',
      isMock: true,
      title: '高级服务端工程师',
      companyName: '栈桥互联',
      city: '成都',
      salaryText: '21K-28K',
      confidence: 78,
      matchedSkills: ['Spring Boot', 'Redis', '接口联调'],
      reason: '整体匹配度稳定，工作内容偏业务交付，适合看重节奏和成长平衡的场景。',
      education: '本科',
      experience: '3年左右',
      industryName: '互联网平台',
      description: '承担业务系统服务端开发，负责需求迭代、接口联调与线上问题修复。',
      requirements: '有扎实的 Java 基础，熟悉数据库、缓存和常见接口安全处理。'
    }
  ],
  skills: {
    coverage: 72,
    summary: '基础后端能力已经成型，但从中级岗位继续往上走时，系统设计、云原生和观测性仍是主要缺口。',
    strengths: ['Java / Spring Boot 基础扎实', 'SQL 建模与接口开发能力稳定', '有缓存与常规性能优化经验'],
    gaps: [
      { title: '系统设计', detail: '需要把“完成功能”提升到“设计方案”，包括拆分边界、接口治理和容量预估。', meta: '高优先级' },
      { title: '云原生交付', detail: '建议补齐容器部署、CI/CD 和环境一致性经验，增强工程交付能力。', meta: '中高优先级' },
      { title: '可观测性', detail: '日志、指标和链路追踪的实践偏弱，影响复杂系统排障效率。', meta: '中优先级' }
    ],
    actions: ['先做一个含鉴权、缓存和监控的完整服务案例', '补一轮系统设计与高并发场景表达', '把现有项目经验整理成“问题-方案-结果”的面试叙述'],
    radar: [
      { label: '后端基础', score: 86 },
      { label: '数据建模', score: 78 },
      { label: '系统设计', score: 58 },
      { label: '工程交付', score: 64 },
      { label: '性能优化', score: 70 },
      { label: '业务表达', score: 66 }
    ]
  },
  path: {
    summary: '建议先把当前后端交付能力做厚，再逐步把角色重心转向系统方案、稳定性和跨团队协同。',
    stages: [
      {
        phase: '0-3 个月',
        title: '补齐系统设计底座',
        focus: '从功能开发者转成能描述方案边界的人。',
        actions: ['输出 2 个完整系统设计案例', '梳理常见高并发与缓存策略', '建立一份可复用技术方案模板']
      },
      {
        phase: '3-6 个月',
        title: '强化工程与稳定性能力',
        focus: '让自己具备更可靠的服务交付和问题定位能力。',
        actions: ['补齐容器部署和监控治理实践', '对现有项目做一次性能瓶颈复盘', '形成上线前检查清单']
      },
      {
        phase: '6-12 个月',
        title: '向架构支持角色过渡',
        focus: '开始承担跨模块设计与协同职责。',
        actions: ['参与跨团队技术评审', '主导一个中等规模服务重构', '积累一套稳定的业务抽象方法']
      }
    ]
  },
  resume: {
    score: 78,
    summary: '经历和技能都够用，但简历过于“做了什么”，还没有把业务结果和复杂度讲清楚。',
    strengths: ['技术关键词完整，岗位相关性高', '项目经历覆盖接口、数据库和缓存', '适合投递后端与平台研发岗位'],
    issues: ['缺少量化结果，成果感不够强', '项目描述偏平铺直叙，缺少难点与决策', '系统设计和稳定性优化表达不足'],
    actions: ['把每段项目改成“背景-动作-结果”三段式', '至少补 2 条性能优化或稳定性提升结果', '把技能区按语言、框架、工程能力重新分组'],
    keywords: ['高并发', '接口治理', '缓存优化', '服务稳定性']
  },
  import: {
    savedSkills: 12,
    profile: {
      targetRole: '后端工程师',
      city: '上海',
      experience: '3-5年',
      education: '本科'
    },
    skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Docker', 'Git', 'Linux', 'REST API', 'SQL 调优']
  },
  salary: {
    range: '24K-32K',
    median: '28K',
    confidence: '中高',
    summary: '在一线城市的中级后端岗位里具备稳定竞争力，若补齐系统设计与架构表达，天花板还能再往上抬一档。',
    factors: [
      { label: '技能栈贴合度', detail: 'Java / Spring Boot / MySQL 是主流后端岗位的直接匹配项。' },
      { label: '经验阶段', detail: '当前履历适合中级偏上的服务端岗位，但更高职级还需要系统设计案例支撑。' },
      { label: '城市差异', detail: '北京、上海和深圳的上沿更高，杭州和成都更看重业务匹配度。' }
    ],
    benchmarks: [
      { label: '当前市场中位', value: '26K' },
      { label: '补齐架构表达后', value: '34K+' },
      { label: '非一线核心区间', value: '20K-26K' }
    ]
  }
}

const jobsForm = ref({
  skills: 'Java, Spring Boot, MySQL',
  preferredCities: '北京, 上海',
  education: '本科',
  experience: '1-3年',
  targetJobType: '',
  targetCity: '',
  industry: '',
  experienceYears: 1,
  userSkills: ''
})
const jobsResult = ref(null)
const recommendedJobs = computed(() => {
  const payload = jobsResult.value
  if (!payload) {
    return MOCK_RESULTS.jobs
  }

  if (Array.isArray(payload)) {
    return payload
  }

  const candidates = [
    payload.items,
    payload.recommendedJobs,
    payload.recommendations,
    payload.jobs,
    payload.data?.items,
    payload.data?.recommendedJobs
  ]

  return candidates.find(Array.isArray) || []
})
const hasStructuredJobs = computed(() => recommendedJobs.value.length > 0)

const JOBS_PAGE_SIZE = 6
const visibleJobsCount = ref(JOBS_PAGE_SIZE)
const visibleJobs = computed(() => recommendedJobs.value.slice(0, visibleJobsCount.value))
const hasMoreJobs = computed(() => visibleJobsCount.value < recommendedJobs.value.length)
function showMoreJobs() {
  visibleJobsCount.value = Math.min(
    visibleJobsCount.value + JOBS_PAGE_SIZE,
    recommendedJobs.value.length
  )
}
function resetVisibleJobs() {
  visibleJobsCount.value = JOBS_PAGE_SIZE
}

const skillsForm = ref({
  userSkills: 'Java, MySQL, Vue',
  targetJobType: 'Backend Engineer',
  city: 'Beijing'
})
const skillsResult = ref(null)
const radarResult = ref(null)

const pathForm = ref({
  currentJob: 'Java Engineer',
  targetJob: 'Architecture Engineer',
  currentSkills: 'Java, Spring, Redis, MySQL',
  city: 'Beijing'
})
const pathResult = ref(null)

const resumeForm = ref({
  targetJob: 'Backend Engineer',
  userSkills: 'Java, Spring Boot, Redis',
  resumeText: 'Two years of backend development experience, API design, database modelling, and cache optimization.'
})
const resumeResult = ref(null)

const predictForm = ref({
  city: 'Beijing',
  education: 'Bachelor',
  experience: '1-3 years',
  skills: 'Java, Spring Boot, MySQL',
  industry: 'Internet'
})
const predictResult = ref(null)

const uploadFile = ref(null)
const overwriteSkills = ref(true)
const importResult = ref(null)

const tabs = [
  { key: 'jobs', label: '职位匹配', icon: Sparkles, group: 'recommend' },
  { key: 'skills', label: '技能差距', icon: Radar, group: 'recommend' },
  { key: 'path', label: '职业路径', icon: Compass, group: 'recommend' },
  { key: 'resume', label: '简历评估', icon: FileSearch, group: 'support' },
  { key: 'import', label: '资料导入', icon: FileUp, group: 'support' },
  { key: 'salary', label: '薪资预测', icon: Calculator, group: 'support' }
]

const sidebarGroups = [
  { key: 'recommend', label: '推荐', items: tabs.filter((t) => t.group === 'recommend') },
  { key: 'support', label: '辅助', items: tabs.filter((t) => t.group === 'support') }
]

const activeTab = ref('jobs')
const loginPrompt = computed(() => !authStore.isLoggedIn)
const activeTabMeta = computed(() => tabs.find((item) => item.key === activeTab.value) || tabs[0])

// 切换标签时，把主滚动区回到顶部 —— 比如看"职位匹配"滑到最下面切到"技能分析"
// 不做的话新标签会沿用上一个 scroll 位置，用户以为页面空白。
// 不使用 smooth 是因为切换往往伴随内容重排（骨架屏/新面板高度），平滑滚到一半会被打断。
watch(activeTab, () => {
  const container = document.querySelector('.main-content')
  if (container) {
    container.scrollTo({ top: 0 })
  } else {
    window.scrollTo({ top: 0 })
  }
})

// 角色感知 + 个人化计划（来自 main）
const isAdmin = computed(() => (authStore.user?.roleType ?? 0) === 1)

const quickActions = computed(() => {
  if (!personalizedPlan.value) {
    return [
      '补充目标岗位和核心技能，先让推荐模型识别你的求职方向。',
      '上传简历 PDF，让系统自动回填学历、技能和经历。',
      '生成个人报告，把匹配分析转成可执行的提升动作。'
    ]
  }
  const planSummary = personalizedPlan.value.planSummary
  const executionPlan = Array.isArray(personalizedPlan.value.executionPlan) ? personalizedPlan.value.executionPlan : []
  const skillFocus = normalizeStrings(
    personalizedPlan.value.skillFocus ||
    personalizedPlan.value.skillGap?.prioritySkills ||
    planSummary?.prioritySkills,
    4
  )
  const actions = [
    firstText(planSummary?.nextStep),
    firstText(planSummary?.executionAdvice),
    ...executionPlan.map((item) => firstText(
      item?.title && item?.detail ? `${item.title}：${item.detail}` : '',
      item?.detail,
      item?.title
    )),
    ...(skillFocus.length ? [`优先补齐技能：${skillFocus.join('、')}`] : [])
  ].filter(Boolean)

  return actions.slice(0, 5)
})

const planSummary = computed(() => {
  const raw = personalizedPlan.value?.planSummary
  return raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : null
})

const planExecutionSteps = computed(() => {
  const raw = personalizedPlan.value?.executionPlan
  return Array.isArray(raw)
    ? raw.filter((item) => firstText(item?.title, item?.detail))
    : []
})

const planRiskAlerts = computed(() => normalizeStrings(personalizedPlan.value?.riskAlerts, 5))

const planSkillFocus = computed(() => normalizeStrings(
  personalizedPlan.value?.skillFocus ||
  personalizedPlan.value?.skillGap?.prioritySkills ||
  planSummary.value?.prioritySkills,
  6
))

const planDiagnosis = computed(() => {
  const raw = personalizedPlan.value?.skillGap?.diagnosis
  return raw && typeof raw === 'object' ? raw : {}
})

const planJobFocus = computed(() => {
  const raw = personalizedPlan.value?.jobFocus
  return raw && typeof raw === 'object' ? raw : {}
})

const planFocusGroups = computed(() => ([
  { label: '主投岗位', items: normalizeStrings(planJobFocus.value?.topTitles, 3) },
  { label: '重点城市', items: normalizeStrings(planJobFocus.value?.topCities, 3) },
  { label: '重点行业', items: normalizeStrings(planJobFocus.value?.topIndustries, 3) },
  { label: '命中技能', items: normalizeStrings(planJobFocus.value?.topMatchedSkills, 6) }
]).filter((group) => group.items.length))

const personalizedPlanReady = computed(() => Boolean(
  planSummary.value ||
  planExecutionSteps.value.length ||
  planRiskAlerts.value.length ||
  planSkillFocus.value.length ||
  planFocusGroups.value.length
))

const planOverviewTiles = computed(() => {
  if (!personalizedPlanReady.value) return []
  return [
    { label: '主投岗位', value: planJobFocus.value?.topTitles?.[0] || '--' },
    { label: '优先技能', value: planSkillFocus.value[0] || '--' },
    { label: '执行步骤', value: `${planExecutionSteps.value.length || 0} 项` },
    { label: '风险提醒', value: `${planRiskAlerts.value.length || 0} 条` }
  ]
})

const planDiagnosisCards = computed(() => {
  if (!personalizedPlanReady.value) return []
  return [
    {
      label: '准备度',
      value: planDiagnosis.value?.readinessLevel || '--',
      detail: planDiagnosis.value?.coreConclusion || '系统会根据你的岗位焦点和技能缺口动态调整建议。'
    },
    {
      label: '已命中技能',
      value: `${planDiagnosis.value?.matchedSkillCount ?? 0}`,
      detail: '当前技能与目标岗位核心要求的重合项。'
    },
    {
      label: '待补齐技能',
      value: `${planDiagnosis.value?.missingSkillCount ?? 0}`,
      detail: planDiagnosis.value?.priorityAction || '优先从最影响投递结果的技能开始补齐。'
    }
  ]
})

const recommendHealthTiles = computed(() => {
  if (!recommendHealth.value) return []
  const breakerActive = Boolean(
    recommendHealth.value.breakerOpen &&
    Number(recommendHealth.value.breakerOpenUntilMs) > Number(recommendHealth.value.nowMs)
  )
  return [
    { label: '算法服务', value: recommendHealth.value.algorithmHealthy ? '正常' : '异常' },
    { label: '熔断状态', value: breakerActive ? '保护中' : '关闭' },
    { label: '失败次数', value: `${recommendHealth.value.failureCount ?? 0}` }
  ]
})

const recommendHealthMessage = computed(() => {
  if (!recommendHealth.value) return ''
  const nowMs = Number(recommendHealth.value.nowMs) || Date.now()
  const openUntilMs = Number(recommendHealth.value.breakerOpenUntilMs) || 0
  const breakerActive = Boolean(recommendHealth.value.breakerOpen && openUntilMs > nowMs)
  const snapshot = `${recommendHealth.value.algorithmHealthSnapshot || 'unknown'}`
  const compactSnapshot = snapshot.length > 120 ? `${snapshot.slice(0, 117)}...` : snapshot

  if (breakerActive) {
    const minutes = Math.max(1, Math.ceil((openUntilMs - nowMs) / 60000))
    return `推荐服务当前处于保护状态，预计约 ${minutes} 分钟后恢复探测。最近健康快照：${compactSnapshot}`
  }
  if (!recommendHealth.value.algorithmHealthy) {
    return `算法服务探测未通过，前台会继续走降级策略兜底。最近健康快照：${compactSnapshot}`
  }
  return `算法服务探测正常，当前未触发熔断。最近健康快照：${compactSnapshot}`
})

function splitInput(value) {
  return `${value || ''}`
    .split(/[,\n，、/]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function formatPlainText(value) {
  return escapeHtml(value).replace(/\n/g, '<br/>')
}

function firstText(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}

function toList(value) {
  return Array.isArray(value) ? value : []
}

function normalizeStrings(value, limit = 6) {
  return toList(value)
    .map((item) => {
      if (typeof item === 'string') {
        return item.trim()
      }

      if (item && typeof item === 'object') {
        return firstText(item.name, item.skill, item.title, item.label, item.text, item.keyword)
      }

      return ''
    })
    .filter(Boolean)
    .slice(0, limit)
}

function normalizeDetailItems(value, limit = 4) {
  return toList(value)
    .map((item) => {
      if (typeof item === 'string') {
        return {
          title: item.trim(),
          detail: '建议纳入下一阶段提升计划。',
          meta: '建议优先'
        }
      }

      if (!item || typeof item !== 'object') {
        return null
      }

      const title = firstText(item.title, item.name, item.skill, item.label)
      if (!title) {
        return null
      }

      return {
        title,
        detail: firstText(item.detail, item.reason, item.description, item.text, item.gap) || '建议纳入下一阶段提升计划。',
        meta: firstText(item.meta, item.priority, item.level, item.type) || '建议优先'
      }
    })
    .filter(Boolean)
    .slice(0, limit)
}

function normalizeRadar(value, limit = 6) {
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        const score = Number(item?.score ?? item?.value ?? item?.level)
        const label = firstText(item?.label, item?.name, item?.skill, item?.dimension)
        if (!label || !Number.isFinite(score)) {
          return null
        }

        return {
          label,
          score: Math.max(0, Math.min(100, Math.round(score <= 1 ? score * 100 : score)))
        }
      })
      .filter(Boolean)
      .slice(0, limit)
  }

  if (value && typeof value === 'object') {
    return Object.entries(value)
      .map(([label, score]) => {
        const num = Number(score)
        if (!Number.isFinite(num)) {
          return null
        }

        return {
          label,
          score: Math.max(0, Math.min(100, Math.round(num <= 1 ? num * 100 : num)))
        }
      })
      .filter(Boolean)
      .slice(0, limit)
  }

  return []
}

function normalizeStages(value, limit = 4) {
  return toList(value)
    .map((item, index) => {
      if (!item || typeof item !== 'object') {
        return null
      }

      const title = firstText(item.title, item.name, item.stage, item.label)
      const focus = firstText(item.focus, item.summary, item.description, item.goal, item.detail)
      if (!title && !focus) {
        return null
      }

      return {
        phase: firstText(item.phase, item.timeframe, item.period) || `阶段 ${index + 1}`,
        title: title || `阶段 ${index + 1}`,
        focus: focus || '建议围绕该阶段补齐关键能力。',
        actions: normalizeStrings(item.actions || item.todos || item.recommendations || item.skills, 3)
      }
    })
    .filter(Boolean)
    .slice(0, limit)
}

function normalizeFactors(value, limit = 4) {
  return toList(value)
    .map((item) => {
      if (typeof item === 'string') {
        return { label: item.trim(), detail: '这是当前结果的重要影响因素。' }
      }

      if (!item || typeof item !== 'object') {
        return null
      }

      const label = firstText(item.label, item.title, item.name)
      if (!label) {
        return null
      }

      return {
        label,
        detail: firstText(item.detail, item.reason, item.description, item.text) || '这是当前结果的重要影响因素。'
      }
    })
    .filter(Boolean)
    .slice(0, limit)
}

function usePrototypeResult(key, message) {
  prototypeState.value[key] = true
  error.value = ''
  infoMessage.value = message
}

function clearPrototypeResult(key) {
  prototypeState.value[key] = false
}

const jobsUsingPrototype = computed(() => prototypeState.value.jobs || !jobsResult.value)
const skillsUsingPrototype = computed(() => prototypeState.value.skills || (!skillsResult.value && !radarResult.value))
const pathUsingPrototype = computed(() => prototypeState.value.path || !pathResult.value)
const resumeUsingPrototype = computed(() => prototypeState.value.resume || !resumeResult.value)
const importUsingPrototype = computed(() => prototypeState.value.import || !importResult.value)
const salaryUsingPrototype = computed(() => prototypeState.value.salary || !predictResult.value)

const activeTabUsingPrototype = computed(() => {
  if (activeTab.value === 'jobs') return jobsUsingPrototype.value
  if (activeTab.value === 'skills') return skillsUsingPrototype.value
  if (activeTab.value === 'path') return pathUsingPrototype.value
  if (activeTab.value === 'resume') return resumeUsingPrototype.value
  if (activeTab.value === 'import') return importUsingPrototype.value
  return salaryUsingPrototype.value
})

const resultPanelCopy = computed(() => (
  activeTabUsingPrototype.value
    ? '原型结果'
    : '实时结果'
))

const jobsOverview = computed(() => {
  if (!recommendedJobs.value.length) {
    return null
  }

  const scoreValues = recommendedJobs.value
    .map((job) => getJobConfidence(job))
    .filter((score) => score !== null)
  const avgScore = scoreValues.length
    ? `${Math.round(scoreValues.reduce((sum, score) => sum + score, 0) / scoreValues.length)}%`
    : '待评估'

  const cityCounter = recommendedJobs.value.reduce((acc, job) => {
    const city = getJobCity(job)
    acc[city] = (acc[city] || 0) + 1
    return acc
  }, {})
  const topCity = Object.entries(cityCounter).sort((a, b) => b[1] - a[1])[0]?.[0] || '多城市'

  const tagCounter = recommendedJobs.value.flatMap((job) => getJobTags(job)).reduce((acc, tag) => {
    acc[tag] = (acc[tag] || 0) + 1
    return acc
  }, {})
  const topTag = Object.entries(tagCounter).sort((a, b) => b[1] - a[1])[0]?.[0] || '岗位匹配'

  return {
    count: recommendedJobs.value.length,
    topCity,
    avgScore,
    topTag
  }
})

const skillInsight = computed(() => {
  const radar = normalizeRadar(radarResult.value?.items || radarResult.value?.radar || radarResult.value?.data || radarResult.value)
  const strengths = normalizeStrings(
    skillsResult.value?.strengths ||
    skillsResult.value?.strongSkills ||
    skillsResult.value?.advantages ||
    skillsResult.value?.data?.strengths,
    4
  )
  const gaps = normalizeDetailItems(
    skillsResult.value?.gaps ||
    skillsResult.value?.gapSkills ||
    skillsResult.value?.missingSkills ||
    skillsResult.value?.recommendedSkills ||
    skillsResult.value?.data?.gaps,
    4
  )
  const actions = normalizeStrings(
    skillsResult.value?.actions ||
    skillsResult.value?.recommendations ||
    skillsResult.value?.learningPlan ||
    skillsResult.value?.data?.actions,
    4
  )
  const parsed = strengths.length || gaps.length || actions.length || radar.length
    ? {
        coverage: radar.length
          ? Math.round(radar.reduce((sum, item) => sum + item.score, 0) / radar.length)
          : 70,
        summary: firstText(
          skillsResult.value?.summary,
          skillsResult.value?.analysis,
          skillsResult.value?.message
        ) || '技能结构存在提升空间，建议优先补齐更接近目标岗位的核心能力。',
        strengths: strengths.length ? strengths : MOCK_RESULTS.skills.strengths,
        gaps: gaps.length ? gaps : MOCK_RESULTS.skills.gaps,
        actions: actions.length ? actions : MOCK_RESULTS.skills.actions,
        radar: radar.length ? radar : MOCK_RESULTS.skills.radar
      }
    : null

  return parsed || (skillsUsingPrototype.value ? MOCK_RESULTS.skills : null)
})

const pathInsight = computed(() => {
  const stages = normalizeStages(
    pathResult.value?.stages ||
    pathResult.value?.path ||
    pathResult.value?.steps ||
    pathResult.value?.phases ||
    pathResult.value?.data?.stages,
    4
  )
  const parsed = stages.length
    ? {
        summary: firstText(pathResult.value?.summary, pathResult.value?.analysis, pathResult.value?.message) || '建议按阶段推进职业路径，而不是一次性跨越目标岗位。',
        stages
      }
    : null

  return parsed || (pathUsingPrototype.value ? MOCK_RESULTS.path : null)
})

const resumeInsight = computed(() => {
  const strengths = normalizeStrings(resumeResult.value?.strengths || resumeResult.value?.highlights || resumeResult.value?.advantages, 4)
  const issues = normalizeStrings(resumeResult.value?.issues || resumeResult.value?.improvements || resumeResult.value?.weaknesses, 4)
  const actions = normalizeStrings(resumeResult.value?.actions || resumeResult.value?.suggestions || resumeResult.value?.recommendations, 4)
  const keywords = normalizeStrings(resumeResult.value?.keywords || resumeResult.value?.tags, 5)
  const rawScore = Number(resumeResult.value?.score ?? resumeResult.value?.totalScore ?? resumeResult.value?.matchScore)
  const parsed = strengths.length || issues.length || actions.length || keywords.length || Number.isFinite(rawScore)
    ? {
        score: Number.isFinite(rawScore) ? Math.max(0, Math.min(100, Math.round(rawScore <= 1 ? rawScore * 100 : rawScore))) : 76,
        summary: firstText(resumeResult.value?.summary, resumeResult.value?.analysis, resumeResult.value?.message) || '简历内容具备基本岗位贴合度，但表达层次仍需收紧。',
        strengths: strengths.length ? strengths : MOCK_RESULTS.resume.strengths,
        issues: issues.length ? issues : MOCK_RESULTS.resume.issues,
        actions: actions.length ? actions : MOCK_RESULTS.resume.actions,
        keywords: keywords.length ? keywords : MOCK_RESULTS.resume.keywords
      }
    : null

  return parsed || (resumeUsingPrototype.value ? MOCK_RESULTS.resume : null)
})

const importInsight = computed(() => {
  const skills = normalizeStrings(importResult.value?.skills || importResult.value?.savedSkillNames || importResult.value?.data?.skills, 10)
  const parsed = importResult.value
    ? {
        savedSkills: Number(importResult.value.savedSkills ?? importResult.value.saved_skills ?? skills.length) || skills.length,
        profile: {
          targetRole: firstText(importResult.value.targetRole, importResult.value.targetJob, importResult.value.data?.targetRole) || '个人画像待补齐',
          city: firstText(importResult.value.city, importResult.value.data?.city) || '城市待识别',
          experience: firstText(importResult.value.experience, importResult.value.data?.experience) || '经验待识别',
          education: firstText(importResult.value.education, importResult.value.data?.education) || '学历待识别'
        },
        skills: skills.length ? skills : MOCK_RESULTS.import.skills
      }
    : null

  return parsed || (importUsingPrototype.value ? MOCK_RESULTS.import : null)
})

const salaryInsight = computed(() => {
  const factors = normalizeFactors(
    predictResult.value?.factors ||
    predictResult.value?.reasons ||
    predictResult.value?.drivers ||
    predictResult.value?.data?.factors,
    4
  )
  const benchmarks = normalizeDetailItems(
    predictResult.value?.benchmarks ||
    predictResult.value?.comparison ||
    predictResult.value?.data?.benchmarks,
    3
  ).map((item) => ({
    label: item.title,
    value: item.detail
  }))

  const range = firstText(
    predictResult.value?.range,
    predictResult.value?.salaryRange,
    predictResult.value?.predictedRange,
    predictResult.value?.data?.range
  )

  const parsed = range || factors.length || benchmarks.length
    ? {
        range: range || MOCK_RESULTS.salary.range,
        median: firstText(predictResult.value?.median, predictResult.value?.predictedSalary, predictResult.value?.data?.median) || MOCK_RESULTS.salary.median,
        confidence: firstText(predictResult.value?.confidence, predictResult.value?.level) || MOCK_RESULTS.salary.confidence,
        summary: firstText(predictResult.value?.summary, predictResult.value?.analysis, predictResult.value?.message) || MOCK_RESULTS.salary.summary,
        factors: factors.length ? factors : MOCK_RESULTS.salary.factors,
        benchmarks: benchmarks.length ? benchmarks : MOCK_RESULTS.salary.benchmarks
      }
    : null

  return parsed || (salaryUsingPrototype.value ? MOCK_RESULTS.salary : null)
})

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
}

function getJobTitle(job) {
  return job.title || job.jobTitle || job.positionName || job.name || '推荐岗位'
}

function getJobCompany(job) {
  return job.companyName || job.company_name || job.company || '优质企业'
}

function getJobCity(job) {
  return job.city || job.location || job.workCity || '地点不限'
}

function getJobSalary(job) {
  return job.salaryText || job.salary_text || job.salary || job.salaryRange || '薪资面议'
}

function getJobConfidence(job) {
  const raw = job.confidence ?? job.matchScore ?? job.match_score ?? job.score
  const score = Number(raw)
  if (!Number.isFinite(score)) {
    return null
  }
  return Math.max(0, Math.min(100, Math.round(score <= 1 ? score * 100 : score)))
}

function getJobTags(job) {
  const tags = job.matchedSkills || job.matched_skills || job.advice?.matched_skills || job.skills || job.tags || []
  return Array.isArray(tags) ? tags.slice(0, 3) : []
}

function getJobReason(job) {
  const reasons = job.whyMatched || job.reasons || job.reason
  if (Array.isArray(reasons)) {
    return reasons.slice(0, 2).join(' / ')
  }
  return reasons || job.industryName || '结合技能、城市和行业偏好综合推荐。'
}

function handleJobCardMove(event) {
  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    return
  }

  const card = event.currentTarget
  const rect = card.getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  const rotateY = ((x / rect.width) - 0.5) * 12
  const rotateX = -((y / rect.height) - 0.5) * 10

  card.style.setProperty('--card-rx', `${rotateX.toFixed(2)}deg`)
  card.style.setProperty('--card-ry', `${rotateY.toFixed(2)}deg`)
  card.style.setProperty('--card-mx', `${((x / rect.width) * 100).toFixed(1)}%`)
  card.style.setProperty('--card-my', `${((y / rect.height) * 100).toFixed(1)}%`)
}

function resetJobCard(event) {
  const card = event.currentTarget
  card.style.setProperty('--card-rx', '0deg')
  card.style.setProperty('--card-ry', '0deg')
  card.style.setProperty('--card-mx', '50%')
  card.style.setProperty('--card-my', '35%')
}

async function openRecommendedJob(job) {
  const jobId = job.jobId || job.job_id || job.id
  const baseJob = {
    id: jobId,
    title: getJobTitle(job),
    companyName: getJobCompany(job),
    city: getJobCity(job),
    salaryText: getJobSalary(job),
    education: job.education,
    experience: job.experience,
    industryName: job.industryName || job.industry_name,
    sourceUrl: job.sourceUrl || job.source_url,
    description: job.description,
    requirements: job.requirements
  }

  if (!jobId || job.isMock || jobsUsingPrototype.value) {
    selectedJob.value = baseJob
    isLoadingJobDetail.value = false
    return
  }

  selectedJob.value = baseJob
  isLoadingJobDetail.value = true

  try {
    const detail = await fetchJobDetail(jobId)
    selectedJob.value = {
      ...selectedJob.value,
      ...detail
    }
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    isLoadingJobDetail.value = false
  }
}

function closeRecommendedJob() {
  selectedJob.value = null
  isLoadingJobDetail.value = false
}

async function importProfile() {
  if (importLoading.value) {
    return
  }

  if (!authStore.isLoggedIn) {
    importResult.value = null
    importSuccess.value = '当前展示示例导入结果，登录后可写入真实个人画像。'
    usePrototypeResult('import', '资料导入接口未启用，已切换为示例导入结果。')
    return
  }

  if (!uploadFile.value) {
    return
  }

  importLoading.value = true
  error.value = ''
  importSuccess.value = ''
  infoMessage.value = ''

  try {
    importResult.value = await importAiProfileFile(authStore.token, uploadFile.value, overwriteSkills.value)
    clearPrototypeResult('import')
    importSuccess.value = `已导入个人资料。已保存技能数: ${importResult.value.savedSkills || 0}。`
    if (authStore.syncProfile) {
      await authStore.syncProfile()
    }
  } catch (e) {
    importResult.value = null
    importSuccess.value = '资料导入接口暂未返回，当前展示示例导入结果。'
    usePrototypeResult('import', `资料导入接口暂未返回，已切换为示例导入结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    importLoading.value = false
  }
}

async function handleJobsRecommend() {
  if (!authStore.isLoggedIn) {
    jobsResult.value = null
    usePrototypeResult('jobs', '未登录时默认展示示例岗位匹配结果。')
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    jobsResult.value = await recommendJobs(authStore.token, {
      skills: splitInput(jobsForm.value.skills),
      preferredCities: splitInput(jobsForm.value.preferredCities),
      education: jobsForm.value.education,
      experience: jobsForm.value.experience,
      industry: jobsForm.value.industry,
      limit: Number(jobsForm.value.limit)
    })
    clearPrototypeResult('jobs')
    resetVisibleJobs()
    // 保留本地完成标记，供报告中心在后端异常时兜底判断。
    markStudentRecommendDone(authStore.user?.id)
  } catch (e) {
    jobsResult.value = null
    usePrototypeResult('jobs', `职位推荐接口暂未返回，已切换为示例岗位结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

// --- 来自 main 的工具函数与 API 调用 ---
async function loadPersonalizedPlan() {
  if (!authStore.isLoggedIn) return
  planLoading.value = true
  try {
    personalizedPlan.value = await fetchPersonalizedRecommendPlan(authStore.token)
  } catch {
    personalizedPlan.value = null
  } finally {
    planLoading.value = false
  }
}

async function loadRecommendHealth() {
  if (!authStore.isLoggedIn || !isAdmin.value) return
  healthLoading.value = true
  try {
    recommendHealth.value = await fetchRecommendHealth(authStore.token)
  } catch {
    recommendHealth.value = null
  } finally {
    healthLoading.value = false
  }
}

async function handleSkillGap() {
  if (!authStore.isLoggedIn) {
    skillsResult.value = null
    radarResult.value = null
    usePrototypeResult('skills', '未登录时默认展示示例技能差距结果。')
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    const payload = {
      userSkills: splitInput(skillsForm.value.userSkills),
      targetJobType: skillsForm.value.targetJobType,
      city: skillsForm.value.city
    }

    skillsResult.value = null
    radarResult.value = null

    const [skillsResponse, radarResponse] = await Promise.allSettled([
      recommendSkills(authStore.token, payload),
      recommendSkillRadar(authStore.token, payload)
    ])

    skillsResult.value = skillsResponse.status === 'fulfilled' ? skillsResponse.value : null
    radarResult.value = radarResponse.status === 'fulfilled' ? radarResponse.value : null

    if (skillsResult.value || radarResult.value) {
      clearPrototypeResult('skills')
      const partialFailures = [
        skillsResponse.status === 'rejected' ? '技能差距建议' : '',
        radarResponse.status === 'rejected' ? '技能雷达' : ''
      ].filter(Boolean)
      if (partialFailures.length) {
        infoMessage.value = `${partialFailures.join('、')}暂时获取失败，页面已展示当前可用结果。`
      }
      return
    }

    throw skillsResponse.status === 'rejected' ? skillsResponse.reason : radarResponse.reason
  } catch (e) {
    skillsResult.value = null
    radarResult.value = null
    usePrototypeResult('skills', `技能差距接口暂未返回，已切换为示例结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

async function handleCareerPath() {
  if (!authStore.isLoggedIn) {
    pathResult.value = null
    usePrototypeResult('path', '未登录时默认展示示例职业路径结果。')
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    pathResult.value = null
    pathResult.value = await recommendCareerPath(authStore.token, {
      currentJob: pathForm.value.currentJob,
      targetJob: pathForm.value.targetJob,
      currentSkills: splitInput(pathForm.value.currentSkills),
      city: pathForm.value.city
    })
    clearPrototypeResult('path')
  } catch (e) {
    pathResult.value = null
    usePrototypeResult('path', `职业路径接口暂未返回，已切换为示例路径结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

async function handleResumeReview() {
  if (!authStore.isLoggedIn) {
    resumeResult.value = null
    usePrototypeResult('resume', '未登录时默认展示示例简历评估结果。')
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    // 用 main 引入的 scoreResume 替代 HEAD 里未实现的 reviewResume
    resumeResult.value = await scoreResume({
      target_job_type: resumeForm.value.targetJob,
      skills: splitInput(resumeForm.value.userSkills),
      resume_text: resumeForm.value.resumeText
    })
    clearPrototypeResult('resume')
  } catch (e) {
    resumeResult.value = null
    usePrototypeResult('resume', `简历评估接口暂未返回，已切换为示例评估结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

async function runPrediction() {
  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    predictResult.value = await predictSalary(authStore.token, {
      city: predictForm.value.city,
      education: predictForm.value.education,
      experience: predictForm.value.experience,
      skills: splitInput(predictForm.value.skills),
      industry: predictForm.value.industry
    })
    clearPrototypeResult('salary')
  } catch (e) {
    predictResult.value = null
    usePrototypeResult('salary', `薪资预测接口暂未返回，已切换为示例薪资结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    loadPersonalizedPlan(),
    loadRecommendHealth()
  ])
})
</script>

<template>
  <div class="recommend-page page-animate">
    <div class="recommend-layout">
      <aside class="recommend-sidebar" aria-label="推荐模块导航">
        <div class="recommend-sidebar-inner">
          <h1 class="recommend-hero-title">智能推荐</h1>
          <nav class="recommend-nav" aria-label="推荐模块章节导航">
            <div
              v-for="group in sidebarGroups"
              :key="group.key"
              class="recommend-nav-group"
            >
              <div class="recommend-nav-group-label">{{ group.label }}</div>
              <ul class="recommend-nav-list" role="tablist">
                <li v-for="tab in group.items" :key="tab.key">
                  <button
                    type="button"
                    class="recommend-nav-link"
                    :class="{ 'is-active': activeTab === tab.key }"
                    role="tab"
                    :aria-selected="activeTab === tab.key"
                    @click="activeTab = tab.key"
                  >
                    <span class="recommend-nav-link-label">{{ tab.label }}</span>
                  </button>
                </li>
              </ul>
            </div>
          </nav>
        </div>
      </aside>

      <div class="recommend-content">
        <div v-if="loginPrompt" class="recommend-banner info">
          <Bot :size="16" />
          <span>当前以原型模式展示结果，登录后会切换为真实推荐与导入能力。</span>
        </div>

        <div v-if="error" class="recommend-banner error">{{ error }}</div>
        <div v-if="infoMessage" class="recommend-banner info">{{ infoMessage }}</div>
        <div v-if="importSuccess" class="recommend-banner success">{{ importSuccess }}</div>

        <article v-if="isAdmin && (healthLoading || recommendHealth)" class="recommend-panel">
          <header class="recommend-panel-head">
            <div class="recommend-panel-copy">
              <h2 class="recommend-panel-title">
                <Bot :size="15" />
                推荐服务状态
              </h2>
              <p class="recommend-panel-sub">管理员可在这里快速判断算法服务是否处于正常探测或保护状态。</p>
            </div>
            <span class="recommend-panel-badge">{{ healthLoading ? '检测中' : '运维视角' }}</span>
          </header>

          <div class="recommend-panel-body">
            <template v-if="healthLoading && !recommendHealth">
              <SkeletonCard type="list" :lines="3" />
            </template>
            <template v-else-if="recommendHealth">
              <div class="summary-grid">
                <div v-for="item in recommendHealthTiles" :key="item.label" class="summary-tile">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>
              <p class="panel-muted recommend-health-copy">{{ recommendHealthMessage }}</p>
            </template>
          </div>
        </article>

        <article
          v-if="authStore.isLoggedIn && (planLoading || personalizedPlanReady)"
          class="recommend-panel recommend-plan-panel"
        >
          <header class="recommend-panel-head">
            <div class="recommend-panel-copy">
              <h2 class="recommend-panel-title">
                <Target :size="15" />
                个性化行动计划
              </h2>
              <p class="recommend-panel-sub">把最新推荐结果收敛成岗位焦点、技能优先级和可执行动作。</p>
            </div>
            <span class="recommend-panel-badge">{{ planLoading && !personalizedPlanReady ? '同步中' : '已对齐后端' }}</span>
          </header>

          <div class="recommend-panel-body">
            <template v-if="planLoading && !personalizedPlanReady">
              <SkeletonCard type="card" :lines="3" />
              <SkeletonCard type="list" :lines="4" />
            </template>

            <template v-else-if="personalizedPlanReady">
              <section class="insight-hero">
                <div>
                  <span class="kicker">行动总览</span>
                  <h3>{{ planSummary?.headline || planDiagnosis.coreConclusion || '系统已生成你的个性化行动计划。' }}</h3>
                  <p class="plan-hero-copy">
                    {{ planSummary?.executionAdvice || planDiagnosis.priorityAction || '建议先锁定主投岗位，再同步补齐关键技能与项目证明。' }}
                  </p>
                </div>
                <div class="score-block wide">
                  <span>下一步</span>
                  <strong>{{ planDiagnosis.readinessLevel || '执行中' }}</strong>
                  <small>{{ planSummary?.nextStep || '根据当前结果逐步推进投递与补齐。' }}</small>
                </div>
              </section>

              <div class="summary-grid">
                <div v-for="item in planOverviewTiles" :key="item.label" class="summary-tile">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </div>
              </div>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <h3>优先动作</h3>
                  <ul class="plain-list">
                    <li v-for="item in quickActions" :key="item">{{ item }}</li>
                  </ul>
                </section>
                <section class="insight-card">
                  <h3>风险提醒</h3>
                  <ul v-if="planRiskAlerts.length" class="plain-list">
                    <li v-for="item in planRiskAlerts" :key="item">{{ item }}</li>
                  </ul>
                  <p v-else class="panel-muted">当前没有额外风险提醒，可以按既定节奏推进。</p>
                </section>
              </div>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <h3>岗位聚焦</h3>
                  <div v-if="planFocusGroups.length" class="plan-focus-stack">
                    <div v-for="group in planFocusGroups" :key="group.label" class="plan-focus-group">
                      <span class="plan-focus-label">{{ group.label }}</span>
                      <div class="chip-row">
                        <span v-for="item in group.items" :key="`${group.label}-${item}`">{{ item }}</span>
                      </div>
                    </div>
                  </div>
                  <p v-else class="panel-muted">当前还没有稳定的岗位聚焦结果，建议先补齐画像和目标方向。</p>
                </section>

                <section class="insight-card">
                  <h3>技能焦点</h3>
                  <div v-if="planSkillFocus.length" class="chip-row">
                    <span v-for="item in planSkillFocus" :key="item">{{ item }}</span>
                  </div>
                  <div v-if="planDiagnosisCards.length" class="detail-list">
                    <article v-for="item in planDiagnosisCards" :key="item.label" class="detail-item">
                      <div class="detail-head">
                        <strong>{{ item.label }}</strong>
                        <span>{{ item.value }}</span>
                      </div>
                      <p>{{ item.detail }}</p>
                    </article>
                  </div>
                </section>
              </div>

              <section class="insight-card">
                <h3>执行清单</h3>
                <div v-if="planExecutionSteps.length" class="detail-list">
                  <article v-for="item in planExecutionSteps" :key="`${item.order || 0}-${item.title}`" class="detail-item">
                    <div class="detail-head">
                      <strong>{{ item.title || `步骤 ${item.order || ''}` }}</strong>
                      <span>{{ item.order ? `STEP ${item.order}` : 'NOW' }}</span>
                    </div>
                    <p>{{ item.detail }}</p>
                  </article>
                </div>
                <p v-else class="panel-muted">当前还没有拆解出的执行步骤，建议先补齐画像后重新生成推荐。</p>
              </section>
            </template>
          </div>
        </article>

        <Transition name="recommend-section" mode="out-in">
          <section :key="activeTab" class="recommend-main">
            <article class="recommend-panel control-panel">
              <header class="recommend-panel-head">
                <div class="recommend-panel-copy">
                  <h2 class="recommend-panel-title">
                    <component :is="activeTabMeta.icon" :size="15" />
                    {{ activeTabMeta.label }}
                  </h2>
                  <p class="recommend-panel-sub">填写条件后，点击下方按钮运行推荐。</p>
                </div>
                <span class="recommend-panel-badge">输入</span>
              </header>

              <div class="recommend-panel-body">
          <template v-if="activeTab === 'jobs'">
            <div class="form-grid">
              <label class="field">
                <span class="field-label">技能</span>
                <input v-model="jobsForm.skills" class="recommend-input" placeholder="如 Java, Spring Boot" />
              </label>
              <label class="field">
                <span class="field-label">期望城市</span>
                <input v-model="jobsForm.preferredCities" class="recommend-input" placeholder="如 上海, 北京" />
              </label>
              <label class="field">
                <span class="field-label">学历</span>
                <input v-model="jobsForm.education" class="recommend-input" placeholder="本科 / 硕士" />
              </label>
              <label class="field">
                <span class="field-label">经验</span>
                <input v-model="jobsForm.experience" class="recommend-input" placeholder="1-3 年" />
              </label>
              <label class="field">
                <span class="field-label">行业</span>
                <input v-model="jobsForm.industry" class="recommend-input" placeholder="可选" />
              </label>
              <label class="field">
                <span class="field-label">数量</span>
                <input v-model="jobsForm.limit" class="recommend-input" type="number" min="1" max="20" />
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">
                {{ loginPrompt ? '查看示例推荐' : '运行推荐' }}
              </GlowButton>
            </div>
          </template>

          <template v-else-if="activeTab === 'skills'">
            <div class="form-grid">
              <label class="field">
                <span class="field-label">当前技能</span>
                <input v-model="skillsForm.userSkills" class="recommend-input" placeholder="Java, MySQL" />
              </label>
              <label class="field">
                <span class="field-label">目标职位</span>
                <input v-model="skillsForm.targetJobType" class="recommend-input" placeholder="Backend Engineer" />
              </label>
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="skillsForm.city" class="recommend-input" placeholder="Beijing" />
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleSkillGap">
                {{ loginPrompt ? '查看示例差距' : '分析差距' }}
              </GlowButton>
            </div>
          </template>

          <template v-else-if="activeTab === 'path'">
            <div class="form-grid">
              <label class="field">
                <span class="field-label">当前职位</span>
                <input v-model="pathForm.currentJob" class="recommend-input" placeholder="Java Engineer" />
              </label>
              <label class="field">
                <span class="field-label">目标职位</span>
                <input v-model="pathForm.targetJob" class="recommend-input" placeholder="Architecture Engineer" />
              </label>
              <label class="field">
                <span class="field-label">当前技能</span>
                <input v-model="pathForm.currentSkills" class="recommend-input" placeholder="Java, Spring, Redis" />
              </label>
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="pathForm.city" class="recommend-input" placeholder="Beijing" />
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">
                {{ loginPrompt ? '查看示例路径' : '生成路径' }}
              </GlowButton>
            </div>
          </template>

          <template v-else-if="activeTab === 'resume'">
            <div class="form-grid">
              <label class="field">
                <span class="field-label">目标职位</span>
                <input v-model="resumeForm.targetJob" class="recommend-input" placeholder="Backend Engineer" />
              </label>
              <label class="field">
                <span class="field-label">技能</span>
                <input v-model="resumeForm.userSkills" class="recommend-input" placeholder="Java, Spring Boot" />
              </label>
              <label class="field field-full">
                <span class="field-label">简历正文</span>
                <textarea v-model="resumeForm.resumeText" class="recommend-input tall" placeholder="粘贴简历正文" />
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">
                {{ loginPrompt ? '查看示例评估' : '评估简历' }}
              </GlowButton>
            </div>
          </template>

          <template v-else-if="activeTab === 'import'">
            <div class="form-grid">
              <label class="field field-full">
                <span class="field-label">选择文件</span>
                <input type="file" class="recommend-input file" @change="handleFileChange" />
              </label>
              <label class="checkbox-row">
                <input v-model="overwriteSkills" type="checkbox" />
                <span>覆盖现有技能</span>
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="importLoading" @click="importProfile">
                {{ loginPrompt ? '查看示例导入' : '导入文件' }}
              </GlowButton>
            </div>
          </template>

          <template v-else>
            <div class="form-grid">
              <label class="field">
                <span class="field-label">城市</span>
                <input v-model="predictForm.city" class="recommend-input" placeholder="Beijing" />
              </label>
              <label class="field">
                <span class="field-label">学历</span>
                <input v-model="predictForm.education" class="recommend-input" placeholder="Bachelor" />
              </label>
              <label class="field">
                <span class="field-label">经验</span>
                <input v-model="predictForm.experience" class="recommend-input" placeholder="1-3 years" />
              </label>
              <label class="field">
                <span class="field-label">技能</span>
                <input v-model="predictForm.skills" class="recommend-input" placeholder="Java, Spring Boot" />
              </label>
              <label class="field">
                <span class="field-label">行业</span>
                <input v-model="predictForm.industry" class="recommend-input" placeholder="Internet" />
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="runPrediction">
                {{ loginPrompt ? '查看示例预测' : '预测薪资' }}
              </GlowButton>
            </div>
          </template>
              </div>
            </article>

            <article class="recommend-panel result-panel" :class="{ 'is-prototype': activeTabUsingPrototype }">
              <header class="recommend-panel-head">
                <div class="recommend-panel-copy">
                  <h2 class="recommend-panel-title">结果</h2>
                  <p class="recommend-panel-sub">{{ activeTabUsingPrototype ? '以下为示例数据，可用于评审排版。' : '以下为 API 实时返回结果。' }}</p>
                </div>
                <span
                  class="result-badge"
                  :class="activeTabUsingPrototype ? 'is-mock' : 'is-live'"
                >
                  {{ activeTabUsingPrototype ? '示例数据' : '实时结果' }}
                </span>
              </header>

              <div class="recommend-panel-body">
          <template v-if="activeTab === 'jobs'">
            <div v-if="jobsOverview" class="summary-grid">
              <div class="summary-tile">
                <span>候选岗位</span>
                <strong>{{ jobsOverview.count }}</strong>
              </div>
              <div class="summary-tile">
                <span>主要城市</span>
                <strong>{{ jobsOverview.topCity }}</strong>
              </div>
              <div class="summary-tile">
                <span>平均匹配</span>
                <strong>{{ jobsOverview.avgScore }}</strong>
              </div>
              <div class="summary-tile">
                <span>高频标签</span>
                <strong>{{ jobsOverview.topTag }}</strong>
              </div>
            </div>

            <div v-if="hasStructuredJobs" class="job-list" aria-label="推荐岗位列表">
              <article
                v-for="(job, index) in visibleJobs"
                :key="job.jobId || job.job_id || job.id || `${getJobTitle(job)}-${index}`"
                class="job-card"
                tabindex="0"
                role="button"
                @click="openRecommendedJob(job)"
                @keydown.enter.prevent="openRecommendedJob(job)"
                @keydown.space.prevent="openRecommendedJob(job)"
              >
                <span v-if="getJobConfidence(job) !== null" class="job-score-badge">
                  {{ getJobConfidence(job) }}%
                </span>

                <div class="job-card-surface">
                  <div class="job-card-top">
                    <div class="job-title-group">
                      <span class="job-rank">MATCH {{ String(index + 1).padStart(2, '0') }}</span>
                      <h3 class="job-title">{{ getJobTitle(job) }}</h3>
                      <p class="job-company">{{ getJobCompany(job) }}</p>
                    </div>
                    <div class="salary-block">
                      <span class="job-salary">{{ getJobSalary(job) }}</span>
                    </div>
                  </div>

                  <div class="job-meta-row">
                    <span class="meta-pill">
                      <MapPin :size="12" />
                      {{ getJobCity(job) }}
                    </span>
                    <span class="meta-pill" v-if="job.industryName || job.industry_name">
                      <Building2 :size="12" />
                      {{ job.industryName || job.industry_name }}
                    </span>
                    <span class="meta-pill" v-if="job.experience">
                      <Clock :size="12" />
                      {{ job.experience }}
                    </span>
                    <span class="meta-pill" v-if="job.education">
                      <GraduationCap :size="12" />
                      {{ job.education }}
                    </span>
                  </div>

                  <div v-if="getJobTags(job).length" class="job-tags">
                    <span v-for="tag in getJobTags(job)" :key="tag">{{ tag }}</span>
                  </div>

                  <p class="job-snippet">{{ getJobReason(job) }}</p>

                  <div class="job-card-footer" aria-hidden="true">
                    <span>{{ jobsUsingPrototype ? '查看示例详情' : '查看匹配依据' }}</span>
                    <ArrowRight :size="14" />
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-block">当前条件下没有找到岗位结果。</div>

            <div v-if="hasMoreJobs" class="list-more">
              <button type="button" class="more-btn" @click="showMoreJobs">
                查看更多（已显示 {{ visibleJobs.length }} / {{ recommendedJobs.length }}）
              </button>
            </div>

            <!-- 报告生成入口：学生完成推荐后跳转到 /reports，顺带 autogen=1 -->
            <div v-if="hasStructuredJobs && authStore.isLoggedIn" class="report-cta">
              <div class="report-cta-copy">
                <strong>把这次推荐沉淀成一份个人分析报告</strong>
                <p>基于当前岗位推荐、匹配度和城市偏好，生成带薪资趋势和技能缺口的完整报告。</p>
              </div>
              <GlowButton variant="primary" @click="router.push('/reports?autogen=1')">
                生成个人分析报告
                <ArrowRight :size="14" />
              </GlowButton>
            </div>
          </template>

          <template v-else-if="activeTab === 'skills'">
            <div v-if="skillInsight" class="insight-stack">
              <section class="insight-hero">
                <div>
                  <span class="kicker">能力摘要</span>
                  <h3>{{ skillInsight.summary }}</h3>
                </div>
                <div class="score-block">
                  <span>岗位贴合度</span>
                  <strong>{{ skillInsight.coverage }}%</strong>
                </div>
              </section>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <h3>已有优势</h3>
                  <ul class="plain-list">
                    <li v-for="item in skillInsight.strengths" :key="item">{{ item }}</li>
                  </ul>
                </section>
                <section class="insight-card">
                  <h3>优先补齐</h3>
                  <div class="detail-list">
                    <article v-for="item in skillInsight.gaps" :key="item.title" class="detail-item">
                      <div class="detail-head">
                        <strong>{{ item.title }}</strong>
                        <span>{{ item.meta }}</span>
                      </div>
                      <p>{{ item.detail }}</p>
                    </article>
                  </div>
                </section>
              </div>

              <section class="insight-card">
                <h3>能力刻度</h3>
                <div class="metric-list">
                  <div v-for="item in skillInsight.radar" :key="item.label" class="metric-row">
                    <span>{{ item.label }}</span>
                    <div class="metric-bar"><i :style="{ width: `${item.score}%` }" /></div>
                    <strong>{{ item.score }}</strong>
                  </div>
                </div>
              </section>

              <section class="insight-card">
                <h3>下一步动作</h3>
                <ul class="plain-list">
                  <li v-for="item in skillInsight.actions" :key="item">{{ item }}</li>
                </ul>
              </section>
            </div>
            <div v-else class="empty-block">先提交技能信息，再查看差距和雷达结果。</div>
          </template>

          <template v-else-if="activeTab === 'path'">
            <div v-if="pathInsight" class="insight-stack">
              <section class="insight-hero compact">
                <div>
                  <span class="kicker">路径摘要</span>
                  <h3>{{ pathInsight.summary }}</h3>
                </div>
              </section>

              <div class="timeline-list">
                <article v-for="stage in pathInsight.stages" :key="`${stage.phase}-${stage.title}`" class="timeline-stage">
                  <span class="timeline-phase">{{ stage.phase }}</span>
                  <h3>{{ stage.title }}</h3>
                  <p>{{ stage.focus }}</p>
                  <div class="chip-row">
                    <span v-for="action in stage.actions" :key="action">{{ action }}</span>
                  </div>
                </article>
              </div>
            </div>
            <div v-else class="empty-block">先生成路径，再在这里查看规划结果。</div>
          </template>

          <template v-else-if="activeTab === 'resume'">
            <div v-if="resumeInsight" class="insight-stack">
              <section class="insight-hero">
                <div>
                  <span class="kicker">简历摘要</span>
                  <h3>{{ resumeInsight.summary }}</h3>
                </div>
                <div class="score-block">
                  <span>评估得分</span>
                  <strong>{{ resumeInsight.score }}</strong>
                </div>
              </section>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <h3>值得保留</h3>
                  <ul class="plain-list">
                    <li v-for="item in resumeInsight.strengths" :key="item">{{ item }}</li>
                  </ul>
                </section>
                <section class="insight-card">
                  <h3>需要改写</h3>
                  <ul class="plain-list">
                    <li v-for="item in resumeInsight.issues" :key="item">{{ item }}</li>
                  </ul>
                </section>
              </div>

              <section class="insight-card">
                <h3>关键词与动作</h3>
                <div class="chip-row">
                  <span v-for="item in resumeInsight.keywords" :key="item">{{ item }}</span>
                </div>
                <ul class="plain-list">
                  <li v-for="item in resumeInsight.actions" :key="item">{{ item }}</li>
                </ul>
              </section>
            </div>
            <div v-else class="empty-block">上传简历或输入正文后，这里会显示评估结果。</div>
          </template>

          <template v-else-if="activeTab === 'import'">
            <div v-if="importInsight" class="insight-stack">
              <div class="summary-grid">
                <div class="summary-tile">
                  <span>已识别技能</span>
                  <strong>{{ importInsight.savedSkills }}</strong>
                </div>
                <div class="summary-tile">
                  <span>目标岗位</span>
                  <strong>{{ importInsight.profile.targetRole }}</strong>
                </div>
                <div class="summary-tile">
                  <span>城市</span>
                  <strong>{{ importInsight.profile.city }}</strong>
                </div>
                <div class="summary-tile">
                  <span>经验</span>
                  <strong>{{ importInsight.profile.experience }}</strong>
                </div>
              </div>

              <section class="insight-card">
                <h3>画像摘要</h3>
                <div class="chip-row">
                  <span>{{ importInsight.profile.education }}</span>
                  <span>{{ importInsight.profile.targetRole }}</span>
                  <span>{{ importInsight.profile.city }}</span>
                </div>
              </section>

              <section class="insight-card">
                <h3>已归档技能</h3>
                <div class="chip-row">
                  <span v-for="item in importInsight.skills" :key="item">{{ item }}</span>
                </div>
              </section>
            </div>
            <div v-else class="empty-block">导入成功后，这里会显示保存结果和技能数。</div>
          </template>

          <template v-else>
            <div v-if="salaryInsight" class="insight-stack">
              <section class="insight-hero">
                <div>
                  <span class="kicker">薪资区间</span>
                  <h3>{{ salaryInsight.summary }}</h3>
                </div>
                <div class="score-block wide">
                  <span>预测区间</span>
                  <strong>{{ salaryInsight.range }}</strong>
                  <small>中位 {{ salaryInsight.median }} / 置信 {{ salaryInsight.confidence }}</small>
                </div>
              </section>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <h3>影响因素</h3>
                  <div class="detail-list">
                    <article v-for="item in salaryInsight.factors" :key="item.label" class="detail-item">
                      <div class="detail-head">
                        <strong>{{ item.label }}</strong>
                      </div>
                      <p>{{ item.detail }}</p>
                    </article>
                  </div>
                </section>
                <section class="insight-card">
                  <h3>市场对位</h3>
                  <div class="benchmark-list">
                    <div v-for="item in salaryInsight.benchmarks" :key="item.label" class="benchmark-row">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.value }}</strong>
                    </div>
                  </div>
                </section>
              </div>
            </div>
            <div v-else class="empty-block">先输入预测条件，再查看薪资区间结果。</div>
          </template>
              </div>
            </article>
          </section>
        </Transition>
      </div>
    </div>

    <Teleport to="body">
      <transition name="modal-fade">
        <div v-if="selectedJob" class="modal-overlay" @click.self="closeRecommendedJob">
          <div class="modal-wrapper">
            <div class="modal-content">
              <button class="modal-close" type="button" aria-label="关闭" @click="closeRecommendedJob">
                <X :size="18" :stroke-width="2" />
              </button>

              <div class="modal-header">
                <div class="header-main">
                  <h2 class="modal-title">{{ selectedJob.title }}</h2>
                  <div class="modal-meta-row">
                    <span class="company">{{ selectedJob.companyName || '企业信息待补充' }}</span>
                    <span class="dot">·</span>
                    <span class="location">{{ selectedJob.city || '地点不限' }}</span>
                  </div>
                </div>
                <div class="salary-box">
                  <span class="modal-salary">{{ selectedJob.salaryText || '薪资面议' }}</span>
                </div>
              </div>

              <div class="modal-tags">
                <div class="tag-group">
                  <span class="detail-tag detail-tag--edu" v-if="selectedJob.education">
                    <GraduationCap :size="13" :stroke-width="1.8" />
                    {{ selectedJob.education }}
                  </span>
                  <span class="detail-tag detail-tag--exp" v-if="selectedJob.experience">
                    <Clock :size="13" :stroke-width="1.8" />
                    {{ selectedJob.experience }}
                  </span>
                  <span class="detail-tag detail-tag--industry" v-if="selectedJob.industryName">
                    <Building2 :size="13" :stroke-width="1.8" />
                    {{ selectedJob.industryName }}
                  </span>
                </div>
              </div>

              <div class="modal-body">
                <div v-if="isLoadingJobDetail" class="recommend-detail-skel">
                  <SkeletonCard type="card" :lines="5" />
                  <SkeletonCard type="card" :lines="4" />
                </div>
                <template v-else>
                  <div v-if="selectedJob.description" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>岗位描述</h3>
                    </div>
                    <div class="detail-text" v-html="formatPlainText(selectedJob.description)"></div>
                  </div>

                  <div v-if="selectedJob.requirements" class="detail-section">
                    <div class="section-title">
                      <div class="title-indicator"></div>
                      <h3>任职要求</h3>
                    </div>
                    <div class="detail-text" v-html="formatPlainText(selectedJob.requirements)"></div>
                  </div>

                  <div
                    v-if="!selectedJob.description && !selectedJob.requirements"
                    class="modal-empty"
                  >
                    <Inbox :size="28" :stroke-width="1.6" class="modal-empty-icon" />
                    <p class="modal-empty-title">暂无详细描述</p>
                    <span class="modal-empty-sub">
                      当前岗位详情内容未返回，建议查看原始页面或重新尝试推荐。
                    </span>
                    <a
                      v-if="selectedJob.sourceUrl"
                      :href="selectedJob.sourceUrl"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="modal-empty-cta"
                    >
                      <ExternalLink :size="14" :stroke-width="1.8" />
                      查看原始页面
                    </a>
                  </div>
                </template>
              </div>

              <div v-if="selectedJob.sourceUrl && (selectedJob.description || selectedJob.requirements)" class="modal-footer">
                <a
                  :href="selectedJob.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="action-button primary"
                >
                  <ExternalLink :size="14" :stroke-width="1.8" />
                  查看原始页面
                </a>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
/* ----------------------------------------------------------
 * Page frame
 * -------------------------------------------------------- */
.recommend-page {
  display: flex;
  flex-direction: column;
}

/* ----------------------------------------------------------
 * 标题放在侧栏最上方，作为侧栏"盒子"的一部分一起 sticky
 * -------------------------------------------------------- */
.recommend-hero-title {
  margin: 0 0 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--c-border-glass);
  font-family: var(--font-serif);
  font-size: clamp(20px, 1.8vw, 24px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

/* ----------------------------------------------------------
 * Two-column layout — sidebar + content column
 * -------------------------------------------------------- */
.recommend-layout {
  display: grid;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}

.recommend-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

/* ----------------------------------------------------------
 * Sidebar — mirrors OpenApiSidebar styling exactly (label-only
 * nav, 13.5px links, glow active state with 2px left bar).
 * -------------------------------------------------------- */
.recommend-sidebar {
  position: sticky;
  top: 24px;
  align-self: start;
  min-width: 0;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  scrollbar-width: none;
}

.recommend-sidebar::-webkit-scrollbar {
  display: none;
}

.recommend-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.recommend-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.recommend-nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recommend-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.recommend-nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.recommend-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 10px 7px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 400;
  line-height: 1.4;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}

.recommend-nav-link-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.recommend-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}

.recommend-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

.recommend-nav-link:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

/* ----------------------------------------------------------
 * Status banners
 * -------------------------------------------------------- */
.recommend-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.5;
  box-shadow: var(--shadow-card-quiet);
}

.recommend-banner.info {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.recommend-banner.error {
  border-color: rgba(178, 59, 46, 0.22);
  background: rgba(254, 242, 240, 0.9);
  color: #b23b2e;
}

.recommend-banner.success {
  border-color: rgba(22, 101, 52, 0.2);
  background: rgba(220, 252, 231, 0.75);
  color: #166534;
}

/* ----------------------------------------------------------
 * Stacked main layout — input panel on top, result below.
 * With the sidebar taking ~232px the content column is too
 * narrow to split further, so both panels render full-width.
 * -------------------------------------------------------- */
.recommend-main {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.recommend-main > .recommend-panel {
  width: 100%;
}

.recommend-section-enter-active,
.recommend-section-leave-active {
  transition:
    opacity 220ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 280ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 280ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform, filter;
  transform-origin: top left;
}

.recommend-section-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
  filter: blur(10px);
}

.recommend-section-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.992);
  filter: blur(8px);
}

.recommend-section-enter-to,
.recommend-section-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

/* ----------------------------------------------------------
 * Panel — white card base (aligned with Collector / Console)
 * -------------------------------------------------------- */
.recommend-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-quiet);
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-out);
}

.result-panel.is-prototype {
  border-color: var(--c-border-glass);
}

.recommend-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--c-border-glass);
}

.recommend-panel-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.recommend-panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.25;
  color: var(--c-text-primary);
}

.recommend-panel-title :deep(svg) {
  flex: none;
  color: var(--c-accent-primary);
}

.recommend-panel-sub {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.recommend-panel-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
  flex-shrink: 0;
}

/* Result badge — distinguishes prototype vs live */
.result-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
  flex-shrink: 0;
}

.result-badge.is-mock {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
}

.result-badge.is-live {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.recommend-panel-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 22px 20px;
}

@media (prefers-reduced-motion: reduce) {
  .recommend-section-enter-active,
  .recommend-section-leave-active {
    transition: opacity 120ms ease;
  }

  .recommend-section-enter-from,
  .recommend-section-leave-to,
  .recommend-section-enter-to,
  .recommend-section-leave-from {
    transform: none;
    filter: none;
  }
}

/* ----------------------------------------------------------
 * Form grid
 * -------------------------------------------------------- */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field-full {
  grid-column: 1 / -1;
}

.field-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
}

.recommend-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.4;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.recommend-input::placeholder {
  color: var(--c-text-faint);
}

.recommend-input:hover {
  border-color: var(--c-border-glass-hover);
}

.recommend-input:focus,
.recommend-input:focus-visible {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
  outline: none;
}

.recommend-input.tall {
  min-height: 140px;
  resize: vertical;
  font-family: var(--font-sans);
}

.recommend-input.file {
  padding: 8px 10px;
  font-size: 13px;
}

.checkbox-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.checkbox-row input {
  accent-color: var(--c-accent-primary);
  width: 15px;
  height: 15px;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-start;
}

.recommend-plan-panel {
  overflow: hidden;
}

.recommend-health-copy {
  margin: 0;
}

.plan-hero-copy {
  margin: 10px 0 0;
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.7;
  color: var(--c-text-secondary);
}

.plan-focus-stack {
  display: grid;
  gap: 12px;
}

.plan-focus-group {
  display: grid;
  gap: 8px;
}

.plan-focus-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

/* ----------------------------------------------------------
 * Summary tiles (top of result)
 * -------------------------------------------------------- */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.summary-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
}

.summary-tile span {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
}

.summary-tile strong {
  font-family: var(--font-serif);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--c-text-primary);
}

/* ----------------------------------------------------------
 * Job result list + cards — aligned with JobsView / JobCard.vue
 * so the recommendation cards feel like the same design system.
 * Preserves recommendation-specific surfaces (rank kicker, confidence
 * badge, matched-skill tags) layered on top of the shared structure.
 * -------------------------------------------------------- */
.job-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

.job-card {
  position: relative;
  width: 100%;
  padding: 0;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    box-shadow 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    border-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    background-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}

.job-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-border-glass-hover);
  background: var(--c-bg-surface-hover);
  box-shadow: var(--shadow-card-raised);
}

.job-card:hover .job-title {
  color: var(--c-accent-primary);
}

.job-card:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

.job-card-surface {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 20px 18px;
}

/* 右下角置信度徽标 — 推荐特有，参考 JobCard.vue 的 fav-btn 位置，
   避免与右上角薪资块撞在一起 */
.job-score-badge {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  border: 1px solid rgba(0, 87, 194, 0.22);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.job-card-top {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.job-title-group {
  min-width: 0;
  flex: 1 1 auto;
}

.job-rank {
  display: inline-block;
  margin-bottom: 4px;
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--c-accent-primary);
  opacity: 0.8;
}

.job-title {
  margin: 0 0 4px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.01em;
  transition: color 180ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}

.job-company {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
}

.salary-block {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  text-align: right;
}

.job-salary {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.salary-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 500;
  color: var(--c-text-muted);
  letter-spacing: 0.02em;
}

.job-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
}

.meta-pill :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.7;
}

/* 匹配技能标签：与 meta-pill 视觉上分层，用更柔和的底色 */
.job-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.job-tags span {
  padding: 3px 8px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 600;
}

.job-snippet {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Footer 作为正常流元素放在卡片底部，与右下角置信度徽标分居左右。
   预留右侧 padding，确保徽标与文案不相撞。 */
.job-card-footer {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  margin-top: auto;
  transition:
    border-color 180ms var(--ease-out, ease),
    background-color 180ms var(--ease-out, ease),
    color 180ms var(--ease-out, ease);
}

.job-card:hover .job-card-footer {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.list-more {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.more-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.more-btn:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}

.report-cta {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-radius: 16px;
  border: 1px solid rgba(30, 117, 255, 0.24);
  background:
    linear-gradient(130deg, rgba(30, 117, 255, 0.08), rgba(30, 117, 255, 0.02) 70%),
    var(--c-bg-base-elevated);
  flex-wrap: wrap;
}
.report-cta-copy { min-width: 0; flex: 1 1 320px; }
.report-cta-copy strong {
  display: block;
  margin-bottom: 4px;
  font-family: var(--font-serif);
  font-size: 14.5px;
  color: var(--c-text-primary);
}
.report-cta-copy p {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--c-text-secondary);
}

/* ----------------------------------------------------------
 * Insight stack (skills / path / resume / salary / import)
 * -------------------------------------------------------- */
.insight-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.insight-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
}

.insight-hero.compact {
  align-items: flex-start;
}

.insight-hero > div:first-child {
  min-width: 0;
  flex: 1;
}

.kicker {
  display: inline-flex;
  margin-bottom: 6px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.insight-hero h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(15px, 1.6vw, 18px);
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}

.score-block {
  display: flex;
  min-width: 130px;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  padding: 12px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  text-align: right;
}

.score-block.wide {
  min-width: 170px;
}

.score-block span,
.score-block small {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
}

.score-block strong {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
}

.score-block small {
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: none;
  font-weight: 500;
  color: var(--c-text-secondary);
}

.insight-grid {
  display: grid;
  gap: 12px;
}

.insight-grid.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.insight-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
}

.insight-card h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 14.5px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}

.plain-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.plain-list li {
  position: relative;
  padding-left: 14px;
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.plain-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  opacity: 0.6;
}

.detail-list {
  display: grid;
  gap: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: baseline;
}

.detail-head strong {
  font-family: var(--font-serif);
  font-size: 13.5px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.detail-head span {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--c-accent-primary);
}

.detail-item p {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.panel-muted {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-muted);
}

.metric-list {
  display: grid;
  gap: 10px;
}

.metric-row {
  display: grid;
  grid-template-columns: minmax(80px, 100px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

.metric-row strong {
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.metric-bar {
  position: relative;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.metric-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--c-accent-primary);
}

.timeline-list {
  display: grid;
  gap: 10px;
}

.timeline-stage {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
}

.timeline-stage h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.timeline-stage p {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.timeline-phase {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-accent-primary);
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip-row span {
  padding: 4px 10px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
}

.benchmark-list {
  display: grid;
  gap: 8px;
}

.benchmark-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}

.benchmark-row span {
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

.benchmark-row strong {
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.empty-block {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 18px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 13px;
  text-align: center;
}

/* ----------------------------------------------------------
 * Job detail modal — aligned with JobsView modal so both pages
 * share the same detail experience (tinted tags + section indicators
 * + action-button footer).
 * -------------------------------------------------------- */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.22s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(24, 27, 35, 0.42);
  backdrop-filter: blur(6px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-wrapper {
  width: 100%;
  max-width: 760px;
  animation: modalScaleUp 0.28s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes modalScaleUp {
  from { opacity: 0; transform: scale(0.96) translateY(12px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-content {
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  box-shadow: 0 20px 48px rgba(24, 27, 35, 0.14);
  overflow: hidden;
  position: relative;
  max-height: min(88vh, 720px);
  display: flex;
  flex-direction: column;
}

.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease;
  z-index: 5;
}
.modal-close:hover {
  background: rgba(0, 87, 194, 0.06);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.2);
}

.modal-header {
  padding: 28px 32px 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.header-main {
  flex: 1;
  min-width: 0;
  padding-right: 36px;
}

.modal-title {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--c-text-primary);
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.modal-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 13.5px;
  color: var(--c-text-secondary);
}
.modal-meta-row .dot {
  color: var(--c-text-faint);
}

.salary-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  text-align: right;
}

.modal-salary {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.modal-tags {
  padding: 14px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: rgba(0, 87, 194, 0.02);
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.tag-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
}

.detail-tag :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.8;
}

.detail-tag--edu {
  background: rgba(167, 139, 220, 0.08);
  border-color: rgba(167, 139, 220, 0.22);
}
.detail-tag--edu :deep(svg) { color: #8663c7; opacity: 1; }

.detail-tag--exp {
  background: rgba(66, 166, 176, 0.08);
  border-color: rgba(66, 166, 176, 0.22);
}
.detail-tag--exp :deep(svg) { color: #3a8a92; opacity: 1; }

.detail-tag--industry {
  background: rgba(203, 149, 72, 0.08);
  border-color: rgba(203, 149, 72, 0.22);
}
.detail-tag--industry :deep(svg) { color: #a87229; opacity: 1; }

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 32px 28px;
  scrollbar-width: thin;
  scrollbar-color: var(--c-border-glass-hover) transparent;
}
.modal-body::-webkit-scrollbar {
  width: 6px;
}
.modal-body::-webkit-scrollbar-track {
  background: transparent;
}
.modal-body::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
  border-radius: 999px;
}
.modal-body::-webkit-scrollbar-thumb:hover {
  background: var(--c-accent-primary);
}

.detail-section {
  margin-top: 20px;
}
.detail-section:first-child {
  margin-top: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.title-indicator {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

.section-title h3 {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.detail-text {
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--c-text-primary);
  white-space: pre-line;
  word-break: break-word;
}

.modal-footer {
  padding: 16px 32px;
  background: rgba(0, 87, 194, 0.02);
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid rgba(24, 27, 35, 0.06);
}

.action-button {
  padding: 9px 16px;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
  text-decoration: none;
}

.action-button.primary {
  background: var(--c-accent-primary);
  color: #ffffff;
  border: 1px solid var(--c-accent-primary);
}
.action-button.primary:hover {
  background: #004ba8;
  border-color: #004ba8;
}

.loading-state-simple {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 0;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 13px;
}

.recommend-detail-skel { display: flex; flex-direction: column; gap: 16px; padding: 8px 2px 12px; }

.modal-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px 28px;
  text-align: center;
}
.modal-empty-icon {
  color: var(--c-text-muted);
  opacity: 0.6;
  margin-bottom: 2px;
}
.modal-empty-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-primary);
}
.modal-empty-sub {
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-muted);
  line-height: 1.55;
  max-width: 380px;
}
.modal-empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background-color 140ms ease, color 140ms ease;
}
.modal-empty-cta:hover {
  background: var(--c-accent-primary);
  color: #ffffff;
}
:global([data-theme="dark"]) .modal-empty-cta:hover {
  color: #0f1420;
}

/* ----------------------------------------------------------
 * Dark mode overrides — the error/success banners keep their warm
 * pastel fills in light mode; in dark mode those glow, so we swap
 * to translucent semantic tints. The modal overlay's chilly blue
 * gets traded for a deeper scrim that reads as dim-on-dim. Same for
 * the modal salary pill's accent glow that otherwise washes out.
 * -------------------------------------------------------- */
:global([data-theme="dark"]) .recommend-banner.error {
  background: rgba(178, 59, 46, 0.18);
  color: #ffb4a6;
}
:global([data-theme="dark"]) .recommend-banner.success {
  background: rgba(30, 138, 91, 0.18);
  color: #b6e8c8;
}
:global([data-theme="dark"]) .action-button.primary,
:global([data-theme="dark"]) .modal-empty-cta:hover {
  color: #0f1420;
}

/* ----------------------------------------------------------
 * Responsive
 * -------------------------------------------------------- */
@media (max-width: 900px) {
  .recommend-layout {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .recommend-sidebar {
    position: relative;
    top: 0;
    width: 100%;
    max-height: none;
    overflow: visible;
  }

  .recommend-sidebar-inner {
    flex-direction: row;
    gap: 18px;
    padding: 4px 2px 6px;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .recommend-nav {
    flex-direction: row;
    gap: 18px;
    flex: 1;
  }

  .recommend-nav-group {
    flex-direction: row;
    align-items: center;
    gap: 8px;
    flex: none;
  }

  .recommend-nav-group-label {
    padding: 0;
    margin: 0;
    white-space: nowrap;
  }

  .recommend-nav-list {
    flex-direction: row;
    gap: 6px;
  }

  .recommend-nav-link {
    padding: 6px 12px;
    border-radius: 999px;
    white-space: nowrap;
  }

  .recommend-nav-link.is-active::before {
    display: none;
  }
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .insight-grid.two-col {
    grid-template-columns: 1fr;
  }

  .insight-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .score-block {
    align-items: flex-start;
    text-align: left;
    width: 100%;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .job-list {
    grid-template-columns: 1fr;
  }

  .metric-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .modal-overlay {
    padding: 14px;
  }

  .modal-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding: 22px 22px 14px;
  }

  .header-main {
    padding-right: 36px;
  }

  .salary-box {
    align-items: flex-start;
    text-align: left;
  }

  .modal-tags {
    padding: 12px 22px;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .modal-body {
    padding: 14px 22px 20px;
  }

  .modal-footer {
    padding: 12px 22px;
    flex-direction: column-reverse;
    align-items: stretch;
    gap: 8px;
  }

  .action-button {
    width: 100%;
    justify-content: center;
    min-height: 44px;
  }

  .panel-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
