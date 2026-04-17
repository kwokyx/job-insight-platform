<script setup>
import { computed, ref } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchJobDetail,
  importAiProfileFile,
  normalizeError,
  predictSalary,
  recommendCareerPath,
  recommendJobs,
  recommendSkillRadar,
  recommendSkills,
  reviewResume
} from '../api'
import { useAuthStore } from '../store/auth'
import {
  Bot,
  Building2,
  Calculator,
  Clock,
  Compass,
  ExternalLink,
  FileSearch,
  FileUp,
  GraduationCap,
  MapPin,
  Radar,
  Sparkles,
  Target,
  X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const activeTab = ref('jobs')
const loading = ref(false)
const error = ref('')
const infoMessage = ref('')
const importLoading = ref(false)
const importSuccess = ref('')
const selectedJob = ref(null)
const isLoadingJobDetail = ref(false)
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
  industry: '',
  limit: 8
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
  { key: 'jobs', label: '职位匹配', icon: Sparkles },
  { key: 'skills', label: '技能差距', icon: Radar },
  { key: 'path', label: '职业路径', icon: Compass },
  { key: 'resume', label: '简历评估', icon: FileSearch },
  { key: 'import', label: '资料导入', icon: FileUp },
  { key: 'salary', label: '薪资预测', icon: Calculator }
]

const loginPrompt = computed(() => !authStore.isLoggedIn)
const activeTabMeta = computed(() => tabs.find((item) => item.key === activeTab.value) || tabs[0])
const resultCountText = computed(() => {
  if (activeTab.value === 'jobs') {
    return hasStructuredJobs.value ? `${recommendedJobs.value.length} 条结果` : '暂无结果'
  }

  if (activeTab.value === 'skills') {
    return skillsResult.value || radarResult.value ? '已生成结果' : '示例结果'
  }

  if (activeTab.value === 'path') {
    return pathResult.value ? '已生成结果' : '示例结果'
  }

  if (activeTab.value === 'resume') {
    return resumeResult.value ? '已生成结果' : '示例结果'
  }

  if (activeTab.value === 'import') {
    return importResult.value ? '已导入' : '示例结果'
  }

  return predictResult.value ? '已生成结果' : '示例结果'
})

function splitInput(value) {
  return value
    .split(/[,\n]+/)
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

  card.style.setProperty('--rx', `${rotateX.toFixed(2)}deg`)
  card.style.setProperty('--ry', `${rotateY.toFixed(2)}deg`)
  card.style.setProperty('--mx', `${((x / rect.width) * 100).toFixed(1)}%`)
  card.style.setProperty('--my', `${((y / rect.height) * 100).toFixed(1)}%`)
}

function resetJobCard(event) {
  const card = event.currentTarget
  card.style.setProperty('--rx', '0deg')
  card.style.setProperty('--ry', '0deg')
  card.style.setProperty('--mx', '50%')
  card.style.setProperty('--my', '35%')
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
  } catch (e) {
    jobsResult.value = null
    usePrototypeResult('jobs', `职位推荐接口暂未返回，已切换为示例岗位结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
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
    const [gap, radar] = await Promise.all([
      recommendSkills(authStore.token, payload),
      recommendSkillRadar(authStore.token, payload)
    ])
    skillsResult.value = gap
    radarResult.value = radar
    clearPrototypeResult('skills')
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
    resumeResult.value = await reviewResume(authStore.token, {
      targetJob: resumeForm.value.targetJob,
      userSkills: splitInput(resumeForm.value.userSkills),
      resumeText: resumeForm.value.resumeText
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
    predictResult.value = await predictSalary({
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
</script>

<template>
  <div class="recommend-page page-shell">
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <span class="eyebrow">智能推荐</span>
        <h1>匹配、差距、路径与简历评估</h1>
        <p>输入条件后，结果在右侧直接展开。</p>
        <div class="hero-actions">
          <GlowButton variant="ghost" :loading="loading" @click="handleJobsRecommend">
            <Sparkles :size="14" />
            {{ loginPrompt ? '查看示例' : '快速运行' }}
          </GlowButton>
          <div class="hero-note">
            <Bot :size="14" />
            <span>{{ loginPrompt ? '未登录，默认展示示例结果' : '已登录，可直接调用推荐能力' }}</span>
          </div>
        </div>
      </div>

      <div class="hero-aside">
        <div class="metric-grid">
          <div class="metric-tile">
            <span>当前模块</span>
            <strong>{{ activeTabMeta.label }}</strong>
          </div>
          <div class="metric-tile">
            <span>结果状态</span>
            <strong>{{ resultCountText }}</strong>
          </div>
          <div class="metric-tile">
            <span>资料导入</span>
            <strong>{{ importSuccess ? '已完成' : (importUsingPrototype ? '示例中' : '待导入') }}</strong>
          </div>
        </div>
        <div class="status-strip">
          <Radar :size="16" />
          <div>
            <strong>{{ activeTabMeta.label }}</strong>
            <p>{{ activeTabUsingPrototype ? '当前为可评审原型输出' : '当前为真实调用结果' }}</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="loginPrompt" class="login-banner status-banner">
      <Bot :size="18" />
      <span>当前以原型模式展示结果，登录后会切换为真实推荐与导入能力。</span>
    </div>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="infoMessage" class="status-banner info-banner">{{ infoMessage }}</div>
    <div v-if="importSuccess" class="status-banner success-banner">{{ importSuccess }}</div>

    <div class="tabs-rail surface">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <component :is="tab.icon" :size="14" />
        {{ tab.label }}
      </button>
    </div>

    <section class="workspace-grid">
      <article class="surface section-panel control-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><component :is="activeTabMeta.icon" :size="13" /> {{ activeTabMeta.label }}</span>
            <h2>{{ activeTabMeta.label }}</h2>
            <p>{{ loginPrompt ? '支持示例结果' : '支持实时调用' }}</p>
          </div>
        </div>

        <template v-if="activeTab === 'jobs'">
          <div class="form-grid">
            <input v-model="jobsForm.skills" class="glass-input" placeholder="技能" />
            <input v-model="jobsForm.preferredCities" class="glass-input" placeholder="期望城市" />
            <input v-model="jobsForm.education" class="glass-input" placeholder="学历" />
            <input v-model="jobsForm.experience" class="glass-input" placeholder="经验" />
            <input v-model="jobsForm.industry" class="glass-input" placeholder="行业" />
            <input v-model="jobsForm.limit" class="glass-input" type="number" min="1" max="20" placeholder="数量" />
          </div>
          <div class="panel-actions">
            <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">
              {{ loginPrompt ? '查看示例推荐' : '运行推荐' }}
            </GlowButton>
          </div>
        </template>

        <template v-else-if="activeTab === 'skills'">
          <div class="form-grid">
            <input v-model="skillsForm.userSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="目标职位" />
            <input v-model="skillsForm.city" class="glass-input" placeholder="城市" />
          </div>
          <div class="panel-actions">
            <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">
              {{ loginPrompt ? '查看示例差距' : '分析差距' }}
            </GlowButton>
          </div>
        </template>

        <template v-else-if="activeTab === 'path'">
          <div class="form-grid">
            <input v-model="pathForm.currentJob" class="glass-input" placeholder="当前职位" />
            <input v-model="pathForm.targetJob" class="glass-input" placeholder="目标职位" />
            <input v-model="pathForm.currentSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="pathForm.city" class="glass-input" placeholder="城市" />
          </div>
          <div class="panel-actions">
            <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">
              {{ loginPrompt ? '查看示例路径' : '生成路径' }}
            </GlowButton>
          </div>
        </template>

        <template v-else-if="activeTab === 'resume'">
          <div class="form-grid">
            <input v-model="resumeForm.targetJob" class="glass-input" placeholder="目标职位" />
            <input v-model="resumeForm.userSkills" class="glass-input" placeholder="技能" />
            <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="简历正文" />
          </div>
          <div class="panel-actions">
            <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">
              {{ loginPrompt ? '查看示例评估' : '评估简历' }}
            </GlowButton>
          </div>
        </template>

        <template v-else-if="activeTab === 'import'">
          <div class="form-grid">
            <input type="file" class="glass-input" @change="handleFileChange" />
            <label class="checkbox-row">
              <input v-model="overwriteSkills" type="checkbox" />
              覆盖现有技能
            </label>
          </div>
          <div class="panel-actions">
            <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">
              {{ loginPrompt ? '查看示例导入' : '导入文件' }}
            </GlowButton>
          </div>
        </template>

        <template v-else>
          <div class="form-grid">
            <input v-model="predictForm.city" class="glass-input" placeholder="城市" />
            <input v-model="predictForm.education" class="glass-input" placeholder="学历" />
            <input v-model="predictForm.experience" class="glass-input" placeholder="经验" />
            <input v-model="predictForm.skills" class="glass-input" placeholder="技能" />
            <input v-model="predictForm.industry" class="glass-input" placeholder="行业" />
          </div>
          <div class="panel-actions">
            <GlowButton variant="primary" :loading="loading" @click="runPrediction">
              {{ loginPrompt ? '查看示例预测' : '预测薪资' }}
            </GlowButton>
          </div>
        </template>
      </article>

      <aside class="surface section-panel result-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">结果区</span>
            <h2>{{ activeTabMeta.label }}结果</h2>
            <p>{{ resultPanelCopy }}</p>
          </div>
        </div>

        <template v-if="activeTab === 'jobs'">
          <div v-if="jobsOverview" class="result-summary-grid">
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

          <div v-if="hasStructuredJobs" class="job-album" aria-label="推荐岗位列表">
            <article
              v-for="(job, index) in recommendedJobs"
              :key="job.jobId || job.job_id || job.id || `${getJobTitle(job)}-${index}`"
              class="recommend-job-card"
              tabindex="0"
              role="button"
              @pointermove="handleJobCardMove"
              @pointerleave="resetJobCard"
              @blur="resetJobCard"
              @click="openRecommendedJob(job)"
              @keydown.enter.prevent="openRecommendedJob(job)"
              @keydown.space.prevent="openRecommendedJob(job)"
            >
              <div class="job-card-shine" />
              <div class="job-card-layer">
                <div class="job-card-topline">
                  <span class="job-rank">MATCH {{ String(index + 1).padStart(2, '0') }}</span>
                  <span v-if="getJobConfidence(job) !== null" class="job-match-score">
                    {{ getJobConfidence(job) }}%
                  </span>
                </div>
                <h3>{{ getJobTitle(job) }}</h3>
                <div class="job-meta">
                  <span><Building2 :size="14" /> {{ getJobCompany(job) }}</span>
                  <span><MapPin :size="14" /> {{ getJobCity(job) }}</span>
                </div>
                <p class="job-salary">{{ getJobSalary(job) }}</p>
                <p class="job-reason">{{ getJobReason(job) }}</p>
                <div class="job-tags">
                  <span v-for="tag in getJobTags(job)" :key="tag">{{ tag }}</span>
                </div>
                <div class="job-card-footer">
                  <span><Target :size="14" /> 匹配依据</span>
                  <span class="job-card-link">{{ jobsUsingPrototype ? '示例详情' : '查看详情' }}</span>
                </div>
              </div>
            </article>
          </div>
          <div v-else class="empty-state">当前条件下没有找到岗位结果。</div>
        </template>

        <template v-else-if="activeTab === 'skills'">
          <div v-if="skillInsight" class="insight-stack">
            <section class="insight-hero">
              <div>
                <span class="result-kicker">能力摘要</span>
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
                    <div class="detail-item-head">
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
          <div v-else class="empty-state">先提交技能信息，再查看差距和雷达结果。</div>
        </template>

        <template v-else-if="activeTab === 'path'">
          <div v-if="pathInsight" class="insight-stack">
            <section class="insight-hero compact">
              <div>
                <span class="result-kicker">路径摘要</span>
                <h3>{{ pathInsight.summary }}</h3>
              </div>
            </section>

            <div class="timeline-list">
              <article v-for="stage in pathInsight.stages" :key="`${stage.phase}-${stage.title}`" class="timeline-stage">
                <span class="timeline-phase">{{ stage.phase }}</span>
                <h3>{{ stage.title }}</h3>
                <p>{{ stage.focus }}</p>
                <div class="timeline-tags">
                  <span v-for="action in stage.actions" :key="action">{{ action }}</span>
                </div>
              </article>
            </div>
          </div>
          <div v-else class="empty-state">先生成路径，再在这里查看规划结果。</div>
        </template>

        <template v-else-if="activeTab === 'resume'">
          <div v-if="resumeInsight" class="insight-stack">
            <section class="insight-hero">
              <div>
                <span class="result-kicker">简历摘要</span>
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
              <div class="keyword-cloud">
                <span v-for="item in resumeInsight.keywords" :key="item">{{ item }}</span>
              </div>
              <ul class="plain-list">
                <li v-for="item in resumeInsight.actions" :key="item">{{ item }}</li>
              </ul>
            </section>
          </div>
          <div v-else class="empty-state">上传简历或输入正文后，这里会显示评估结果。</div>
        </template>

        <template v-else-if="activeTab === 'import'">
          <div v-if="importInsight" class="insight-stack">
            <div class="result-summary-grid">
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
              <div class="timeline-tags">
                <span>{{ importInsight.profile.education }}</span>
                <span>{{ importInsight.profile.targetRole }}</span>
                <span>{{ importInsight.profile.city }}</span>
              </div>
            </section>

            <section class="insight-card">
              <h3>已归档技能</h3>
              <div class="keyword-cloud">
                <span v-for="item in importInsight.skills" :key="item">{{ item }}</span>
              </div>
            </section>
          </div>
          <div v-else class="empty-state">导入成功后，这里会显示保存结果和技能数。</div>
        </template>

        <template v-else>
          <div v-if="salaryInsight" class="insight-stack">
            <section class="insight-hero">
              <div>
                <span class="result-kicker">薪资区间</span>
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
                    <div class="detail-item-head">
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
          <div v-else class="empty-state">先输入预测条件，再查看薪资区间结果。</div>
        </template>
      </aside>
    </section>

    <Teleport to="body">
        <transition name="modal-fade">
          <div v-if="selectedJob" class="recommend-modal-overlay" @click.self="closeRecommendedJob">
            <div class="recommend-modal-card">
              <button class="recommend-modal-close" @click="closeRecommendedJob">
                <X :size="18" />
              </button>

              <div class="recommend-modal-head">
                <div class="recommend-modal-copy">
                  <span class="recommend-modal-kicker">职位详情</span>
                  <h2>{{ selectedJob.title }}</h2>
                  <div class="recommend-modal-meta">
                    <span><Building2 :size="14" /> {{ selectedJob.companyName || '企业信息待补充' }}</span>
                    <span><MapPin :size="14" /> {{ selectedJob.city || '地点不限' }}</span>
                  </div>
                </div>
                <div class="recommend-modal-salary">{{ selectedJob.salaryText || '薪资面议' }}</div>
              </div>

              <div class="recommend-modal-tags">
                <span v-if="selectedJob.education"><GraduationCap :size="14" /> {{ selectedJob.education }}</span>
                <span v-if="selectedJob.experience"><Clock :size="14" /> {{ selectedJob.experience }}</span>
                <span v-if="selectedJob.industryName"><Target :size="14" /> {{ selectedJob.industryName }}</span>
              </div>

              <div v-if="isLoadingJobDetail" class="recommend-modal-loading">
                正在加载职位详情...
              </div>

              <div v-else class="recommend-modal-body">
                <section v-if="selectedJob.description" class="recommend-modal-section">
                  <h3>职位描述</h3>
                  <div class="recommend-modal-text" v-html="formatPlainText(selectedJob.description)" />
                </section>

                <section v-if="selectedJob.requirements" class="recommend-modal-section">
                  <h3>任职要求</h3>
                  <div class="recommend-modal-text" v-html="formatPlainText(selectedJob.requirements)" />
                </section>

                <section v-if="!selectedJob.description && !selectedJob.requirements" class="recommend-modal-section empty">
                  <h3>职位概览</h3>
                  <div class="recommend-modal-text">当前岗位详情内容未返回，建议查看原始页面或重新尝试推荐。</div>
                </section>
              </div>

              <div class="recommend-modal-footer">
                <a
                  v-if="selectedJob.sourceUrl"
                  :href="selectedJob.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="recommend-modal-link"
                >
                  <ExternalLink :size="15" /> 查看原始页面
                </a>
                <GlowButton variant="ghost" @click="closeRecommendedJob">关闭</GlowButton>
              </div>
            </div>
          </div>
        </transition>
      </Teleport>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-hero,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 18px;
  padding: 18px 20px;
  border-radius: 16px;
}

.hero-copy,
.hero-aside,
.section-panel {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 8px;
}

.hero-copy h1,
.panel-head h2,
.panel-head h3,
.recommend-modal-copy h2 {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(22px, 2.1vw, 28px);
  line-height: 1.12;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.recommend-modal-text,
.empty-state,
.panel-hint {
  color: var(--c-text-secondary);
  font-size: 0.9rem;
  line-height: 1.5;
}

.hero-actions,
.status-strip,
.panel-actions,
.recommend-modal-meta,
.recommend-modal-tags,
.job-meta,
.job-card-footer,
.inline-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-actions {
  flex-wrap: wrap;
}

.hero-note,
.status-strip,
.metric-tile,
.summary-tile,
.status-banner,
.tabs-rail,
.empty-state,
.result-box,
.insight-card,
.insight-hero,
.timeline-stage {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 14px;
}

.hero-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  color: var(--c-text-secondary);
  background: rgba(255, 255, 255, 0.54);
}

.hero-aside {
  gap: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 13px 14px;
  background: rgba(255, 255, 255, 0.56);
}

.metric-tile span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.metric-tile strong {
  font-size: 22px;
  letter-spacing: -0.03em;
}

.status-strip {
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.58);
}

.tabs-rail {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.64);
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid rgba(193, 198, 215, 0.46);
  background: rgba(255, 255, 255, 0.76);
  color: var(--c-text-secondary);
  font-size: 13.5px;
  font-weight: 700;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.tab-btn:hover {
  border-color: rgba(30, 117, 255, 0.22);
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}

.tab-btn.active {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(30, 117, 255, 0.12);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    0 8px 18px rgba(30, 117, 255, 0.08);
}

.tab-btn :deep(svg) {
  color: inherit;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(272px, 316px) minmax(0, 1fr);
  align-items: start;
  gap: 18px;
}

.section-panel {
  gap: 16px;
  min-width: 0;
  padding: 18px;
  border-radius: 14px;
}

.control-panel {
  background: rgba(255, 255, 255, 0.64);
}

.result-panel {
  background: rgba(255, 255, 255, 0.8);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.form-grid,
.job-album {
  display: grid;
  gap: 12px;
}

.form-grid {
  margin-bottom: 2px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(193, 198, 215, 0.62);
  color: var(--c-text-primary);
}

.tall {
  min-height: 160px;
  resize: vertical;
}

.checkbox-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--c-text-secondary);
}

.panel-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.result-summary-grid,
.insight-grid {
  display: grid;
  gap: 12px;
}

.result-summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 13px 14px;
  background: rgba(255, 255, 255, 0.58);
}

.summary-tile span {
  color: var(--c-text-secondary);
  font-size: 12px;
}

.summary-tile strong {
  font-size: 18px;
  line-height: 1.2;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
}

.insight-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.insight-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(0, 89, 199, 0.045), transparent 46%),
    rgba(255, 255, 255, 0.6);
}

.insight-hero.compact {
  align-items: flex-start;
}

.result-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  color: #5d7290;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.insight-hero h3,
.insight-card h3,
.timeline-stage h3 {
  margin: 0;
}

.insight-hero h3 {
  font-size: clamp(18px, 2vw, 24px);
  line-height: 1.3;
  letter-spacing: -0.03em;
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.insight-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.5);
}

.insight-card h3 {
  font-size: 15px;
  color: var(--c-text-primary);
}

.score-block {
  display: flex;
  min-width: 136px;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.42);
}

.score-block.wide {
  min-width: 182px;
}

.score-block span,
.score-block small {
  color: var(--c-text-secondary);
  font-size: 12px;
}

.score-block strong {
  color: var(--c-text-primary);
  font-size: 28px;
  line-height: 1;
  letter-spacing: -0.04em;
}

.plain-list {
  display: grid;
  gap: 10px;
}

.plain-list li {
  position: relative;
  padding-left: 14px;
  color: var(--c-text-secondary);
  line-height: 1.65;
}

.plain-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: rgba(0, 89, 199, 0.5);
}

.detail-list {
  display: grid;
  gap: 10px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
}

.detail-item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}

.detail-item-head strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.detail-item-head span {
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.detail-item p,
.timeline-stage p {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.metric-list {
  display: grid;
  gap: 10px;
}

.metric-row {
  display: grid;
  grid-template-columns: minmax(72px, 96px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  color: var(--c-text-secondary);
  font-size: 13px;
}

.metric-row strong {
  color: var(--c-text-primary);
  font-size: 13px;
}

.metric-bar {
  position: relative;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(210, 219, 232, 0.58);
}

.metric-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(0, 89, 199, 0.55), rgba(0, 89, 199, 0.92));
}

.timeline-list {
  display: grid;
  gap: 10px;
}

.timeline-stage {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.56);
}

.timeline-phase {
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.timeline-tags,
.keyword-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.timeline-tags span,
.keyword-cloud span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(193, 198, 215, 0.42);
  color: #4f6178;
  font-size: 12px;
  font-weight: 600;
}

.benchmark-list {
  display: grid;
  gap: 10px;
}

.benchmark-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 13px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
}

.benchmark-row span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.benchmark-row strong {
  color: var(--c-text-primary);
  font-size: 14px;
}

.empty-state {
  padding: 16px;
  background: rgba(255, 255, 255, 0.38);
}

.result-box {
  margin: 0;
  padding: 16px;
  background: rgba(255, 255, 255, 0.46);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.job-album {
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
  perspective: 1200px;
}

.recommend-job-card {
  --rx: 0deg;
  --ry: 0deg;
  --mx: 50%;
  --my: 35%;
  position: relative;
  min-height: 226px;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid rgba(27, 38, 59, 0.08);
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff, #f6f8fc);
  box-shadow: 0 12px 26px rgba(20, 31, 55, 0.05);
  color: var(--c-text-primary);
  cursor: pointer;
  transform: rotateX(var(--rx)) rotateY(var(--ry)) translateY(0);
  transform-style: preserve-3d;
  transition:
    transform 220ms ease,
    background 220ms ease,
    border-color 220ms ease,
    box-shadow 220ms ease,
    filter 220ms ease;
}

.job-album:hover .recommend-job-card:not(:hover) {
  filter: saturate(0.9);
  transform: scale(0.985);
}

.recommend-job-card:hover,
.recommend-job-card:focus-visible {
  border-color: rgba(0, 87, 194, 0.24);
  background:
    radial-gradient(circle at var(--mx) var(--my), rgba(0, 110, 242, 0.12), transparent 32%),
    linear-gradient(180deg, #ffffff, #f3f7fd);
  box-shadow: 0 16px 32px rgba(20, 31, 55, 0.09);
  outline: none;
}

.recommend-job-card::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.72), transparent 38%),
    repeating-linear-gradient(135deg, rgba(24, 27, 35, 0.015) 0 1px, transparent 1px 12px);
  opacity: 0.32;
  transition: opacity 220ms ease, background 220ms ease;
}

.recommend-job-card:hover::before,
.recommend-job-card:focus-visible::before {
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.82), transparent 38%),
    repeating-linear-gradient(135deg, rgba(0, 87, 194, 0.04) 0 1px, transparent 1px 12px);
  opacity: 0.58;
}

.job-card-shine {
  position: absolute;
  inset: -35%;
  background: radial-gradient(circle at var(--mx) var(--my), rgba(255, 255, 255, 0.9), transparent 24%);
  opacity: 0;
  mix-blend-mode: screen;
  pointer-events: none;
  transition: opacity 180ms ease;
  transform: translateZ(42px);
}

.recommend-job-card:hover .job-card-shine {
  opacity: 0.55;
}

.job-card-layer {
  position: relative;
  display: flex;
  min-height: inherit;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  transform: translateZ(34px);
}

.job-card-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.job-rank {
  color: var(--c-accent-primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.job-match-score {
  min-width: 48px;
  padding: 5px 8px;
  border: 1px solid rgba(0, 87, 194, 0.16);
  background: rgba(255, 255, 255, 0.62);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

.recommend-job-card h3 {
  margin: 0;
  color: var(--c-text-primary);
  font-size: clamp(18px, 1.6vw, 22px);
  line-height: 1.18;
  letter-spacing: -0.04em;
}

.job-meta {
  flex-wrap: wrap;
  color: var(--c-text-secondary);
  font-size: 13px;
}

.job-meta span,
.job-card-footer span,
.recommend-modal-meta span,
.recommend-modal-tags span,
.recommend-modal-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.job-salary {
  margin: 0;
  color: var(--c-accent-primary);
  font-size: 17px;
  font-weight: 800;
}

.job-reason {
  display: -webkit-box;
  min-height: 40px;
  margin: 0;
  overflow: hidden;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.job-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.job-tags span {
  padding: 5px 8px;
  border: 1px solid rgba(0, 87, 194, 0.13);
  background: rgba(255, 255, 255, 0.58);
  color: var(--c-text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.job-card-footer {
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 87, 194, 0.12);
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.job-card-link {
  color: var(--c-accent-primary);
}

.login-banner,
.status-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
}

.error-banner {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.84);
}

.info-banner {
  color: #0f3f7b;
  background: rgba(223, 238, 255, 0.9);
}

.success-banner {
  color: #166534;
  background: rgba(220, 252, 231, 0.84);
}

.login-banner {
  background: rgba(255, 255, 255, 0.66);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 180ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.recommend-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: rgba(232, 238, 247, 0.72);
  backdrop-filter: blur(10px);
}

.recommend-modal-card {
  position: relative;
  width: min(920px, 100%);
  max-height: min(84vh, 900px);
  overflow: auto;
  border: 1px solid rgba(27, 38, 59, 0.08);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 249, 253, 0.98));
  box-shadow: 0 20px 56px rgba(15, 23, 42, 0.12);
}

.recommend-modal-close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(27, 38, 59, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  color: var(--c-text-secondary);
}

.recommend-modal-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 28px 18px;
  border-bottom: 1px solid rgba(27, 38, 59, 0.08);
}

.recommend-modal-kicker {
  display: inline-block;
  margin-bottom: 8px;
  color: #4b6385;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.recommend-modal-copy h2 {
  color: #182336;
  font-size: 30px;
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.recommend-modal-meta {
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 12px;
  color: #5f6f86;
  font-size: 14px;
}

.recommend-modal-salary {
  align-self: flex-start;
  padding: 10px 14px;
  border-radius: 12px;
  background: #eef4ff;
  color: #0057c2;
  font-size: 20px;
  font-weight: 800;
  white-space: nowrap;
}

.recommend-modal-tags {
  flex-wrap: wrap;
  gap: 10px;
  padding: 18px 28px 0;
}

.recommend-modal-tags span {
  padding: 7px 12px;
  border: 1px solid rgba(0, 87, 194, 0.09);
  border-radius: 999px;
  background: rgba(240, 246, 255, 0.92);
  color: #4c607b;
  font-size: 13px;
  font-weight: 700;
}

.recommend-modal-loading,
.recommend-modal-body {
  padding: 22px 28px 8px;
}

.recommend-modal-loading {
  color: #5f6f86;
  font-size: 14px;
}

.recommend-modal-section + .recommend-modal-section {
  margin-top: 18px;
}

.recommend-modal-section h3 {
  margin: 0 0 10px;
  color: #1a2940;
  font-size: 15px;
}

.recommend-modal-text {
  font-size: 14px;
  line-height: 1.75;
  white-space: normal;
}

.recommend-modal-section.empty .recommend-modal-text {
  color: #6b7c93;
}

.recommend-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 28px 28px;
}

.recommend-modal-link {
  color: #0057c2;
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 1320px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .workspace-hero {
    grid-template-columns: 1fr;
  }

  .result-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .recommend-modal-overlay {
    padding: 14px;
  }

  .recommend-modal-head {
    flex-direction: column;
    gap: 14px;
    padding: 22px 22px 16px;
  }

  .recommend-modal-copy h2 {
    font-size: 24px;
  }

  .recommend-modal-tags,
  .recommend-modal-loading,
  .recommend-modal-body,
  .recommend-modal-footer {
    padding-left: 22px;
    padding-right: 22px;
  }

  .recommend-modal-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .panel-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .insight-hero,
  .score-block {
    align-items: flex-start;
  }

  .insight-hero {
    flex-direction: column;
  }

  .two-col,
  .result-summary-grid {
    grid-template-columns: 1fr;
  }

  .metric-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .job-album {
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}

@media (hover: none), (pointer: coarse) {
  .recommend-job-card,
  .job-album:hover .recommend-job-card:not(:hover) {
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .recommend-job-card,
  .job-card-shine {
    transition: none;
    transform: none;
  }
}
</style>
