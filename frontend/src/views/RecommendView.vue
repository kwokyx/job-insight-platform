<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchJobDetail,
  fetchPersonalizedRecommendPlan,
  importAiProfileFile,
  normalizeError,
  parseResume,
  predictSalary,
  recommendJobs,
  scoreResume
} from '../api'
import {
  AlertTriangle,
  Bot,
  Briefcase,
  Building2,
  Calculator,
  Clock,
  Compass,
  ExternalLink,
  FileSearch,
  FileUp,
  GraduationCap,
  Lightbulb,
  MapPin,
  Radar,
  Sparkles,
  Target,
  Upload,
  X
} from 'lucide-vue-next'
import { useAuthStore } from '../store/auth'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { RadarChart } from 'echarts/charts'
import { TooltipComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, RadarChart, TooltipComponent, RadarComponent])

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const parsing = ref(false)
const planLoading = ref(false)
const error = ref('')
const infoMessage = ref('')
const importLoading = ref(false)
const importSuccess = ref('')
const success = ref('')
const fileInputRef = ref(null)
const personalizedPlan = ref(null)
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
  recommendMode: 'steady',
  targetJobType: '',
  targetCity: '',
  industry: '',
  limit: 12,
  experienceYears: 1,
  userSkills: ''
})
// Alias: main 的代码使用 form.*，wt 的 UI 使用 jobsForm.*，此处保持双向引用。
// TODO: 集成后复核
const form = jobsForm
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

const RECOMMEND_MODE_OPTIONS = [
  {
    key: 'steady',
    label: '稳妥投递',
    desc: '优先主投可投递岗位，结果更稳定',
    abGroup: 'control',
    strategyVersion: 'student_recommend_v3'
  },
  {
    key: 'explore',
    label: '扩圈探索',
    desc: '扩大召回池，补充跨方向机会',
    abGroup: 'exp_recall_heavy',
    strategyVersion: 'student_recommend_v3'
  },
  {
    key: 'sprint',
    label: '冲刺高薪',
    desc: '偏向高潜高薪岗位，但风险更高',
    abGroup: 'exp_fresh_first',
    strategyVersion: 'student_recommend_v3'
  }
]

const recommendModeMeta = computed(() => (
  RECOMMEND_MODE_OPTIONS.find((item) => item.key === jobsForm.value.recommendMode) ||
  RECOMMEND_MODE_OPTIONS[0]
))

const JOB_LAYER_META = {
  primary: { label: '主投岗位', hint: '优先投递，命中度与可投递性最高' },
  backup: { label: '备投岗位', hint: '作为备选池，建议按兴趣和城市筛选后投递' },
  explore: { label: '扩圈岗位', hint: '建议谨慎评估，补齐短板后再投递' }
}
const JOB_LAYER_ORDER = ['primary', 'backup', 'explore']
const jobLayerSections = JOB_LAYER_ORDER.map((key) => ({
  key,
  ...JOB_LAYER_META[key]
}))

const JOBS_PAGE_SIZE = 6
const visibleJobsCount = ref(JOBS_PAGE_SIZE)

function normalizeKeyText(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[\s·•\-_/()（）【】\[\]<>《》]/g, '')
}

function normalizeRoleFamily(title) {
  const normalized = normalizeKeyText(title)
    .replace(/(资深|高级|中级|初级|实习|校招|社招|全职|兼职|remote|junior|senior|staff|sr|jr)/g, '')
    .replace(/(工程师|开发|研发|岗位|职位|专家|顾问|经理|leader)/g, '')
  return normalized.slice(0, 12) || 'generic'
}

const dedupedJobs = computed(() => {
  const grouped = new Map()

  recommendedJobs.value.forEach((job, index) => {
    const companyKey = normalizeKeyText(getJobCompany(job))
    const roleKey = normalizeRoleFamily(getJobTitle(job))
    const key = `${companyKey}::${roleKey}`
    const entry = grouped.get(key)

    if (!entry) {
      grouped.set(key, {
        representative: job,
        duplicateJobs: [],
        sourceIndex: index
      })
      return
    }

    const candidateScore = getJobConfidence(job) ?? -1
    const currentScore = getJobConfidence(entry.representative) ?? -1
    if (candidateScore > currentScore) {
      entry.duplicateJobs.push(entry.representative)
      entry.representative = job
    } else {
      entry.duplicateJobs.push(job)
    }
  })

  return Array.from(grouped.values())
    .sort((a, b) => {
      const scoreDiff = (getJobConfidence(b.representative) ?? -1) - (getJobConfidence(a.representative) ?? -1)
      if (scoreDiff !== 0) return scoreDiff
      return a.sourceIndex - b.sourceIndex
    })
    .map((item) => ({
      job: item.representative,
      duplicateJobs: item.duplicateJobs,
      duplicateCount: item.duplicateJobs.length
    }))
})

function getJobRiskReasons(job) {
  const source = job.risk_flags || job.riskFlags || job.advice?.risk_flags || job.advice?.riskFlags

  if (Array.isArray(source)) {
    return source
      .map((item) => {
        if (typeof item === 'string') return item.trim()
        if (item && typeof item === 'object') {
          return firstText(item.reason, item.label, item.title, item.name, item.detail, item.message)
        }
        return ''
      })
      .filter(Boolean)
  }

  if (typeof source === 'string') {
    return splitInput(source)
  }

  return []
}

function getJobLayer(job) {
  const fit = normalizeKeyText(firstText(job.fit_label, job.fitLabel, job.advice?.fit_label, job.advice?.fitLabel))
  const confidence = getJobConfidence(job) ?? 0
  const riskReasons = getJobRiskReasons(job)
  const highRisk = riskReasons.length >= 2

  if (fit.includes('主投') || fit.includes('强匹配') || fit.includes('high') || fit.includes('strong')) {
    return highRisk && confidence < 82 ? 'backup' : 'primary'
  }

  if (fit.includes('备投') || fit.includes('可尝试') || fit.includes('medium') || fit.includes('normal')) {
    return highRisk ? 'explore' : 'backup'
  }

  if (
    fit.includes('扩圈') ||
    fit.includes('谨慎') ||
    fit.includes('拒投') ||
    fit.includes('不建议') ||
    fit.includes('reject') ||
    fit.includes('caution') ||
    fit.includes('low')
  ) {
    return 'explore'
  }

  if (confidence >= 82 && !highRisk) return 'primary'
  if (confidence >= 65) return 'backup'
  return 'explore'
}

const layeredJobs = computed(() => {
  const grouped = { primary: [], backup: [], explore: [] }
  dedupedJobs.value.forEach((item) => {
    grouped[getJobLayer(item.job)].push(item)
  })
  return grouped
})

const rankedLayeredJobs = computed(() => {
  const grouped = { primary: [], backup: [], explore: [] }
  const flat = []
  let rank = 1

  JOB_LAYER_ORDER.forEach((layerKey) => {
    layeredJobs.value[layerKey].forEach((item) => {
      const ranked = { ...item, layer: layerKey, rank }
      grouped[layerKey].push(ranked)
      flat.push(ranked)
      rank += 1
    })
  })

  return { grouped, flat }
})

const visibleJobs = computed(() => rankedLayeredJobs.value.flat.slice(0, visibleJobsCount.value))
const visibleJobsByLayer = computed(() => {
  const grouped = { primary: [], backup: [], explore: [] }
  visibleJobs.value.forEach((item) => {
    grouped[item.layer].push(item)
  })
  return grouped
})
const hasMoreJobs = computed(() => visibleJobsCount.value < rankedLayeredJobs.value.flat.length)
function showMoreJobs() {
  visibleJobsCount.value = Math.min(
    visibleJobsCount.value + JOBS_PAGE_SIZE,
    rankedLayeredJobs.value.flat.length
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
const isStudent = computed(() => (authStore.user?.roleType ?? 0) === 0)
const isAdmin = computed(() => (authStore.user?.roleType ?? 0) === 1)
const isTeacher = computed(() => (authStore.user?.roleType ?? 0) === 2)
const reportTypeByTab = computed(() => {
  if (isTeacher.value) return 'SUPPLY_DEMAND'
  if (isAdmin.value) return 'OPERATIONS'
  const map = {
    jobs: 'JOB_SEEKING',
    skills: 'SKILL_GAP',
    path: 'COMPREHENSIVE',
    resume: 'SKILL_GAP',
    import: 'JOB_SEEKING',
    salary: 'SALARY'
  }
  return map[activeTab.value] || 'JOB_SEEKING'
})

// results 兼容层：wt UI 大量使用 jobsResult/skillsResult/resumeResult/predictResult 分散的 ref，
// main 新增接口使用统一的 results.* 字段，这里提供一个聚合读取。
// TODO: 集成后复核
const results = computed(() => ({
  jobs: recommendedJobs.value,
  score: resumeResult.value,
  salary: predictResult.value,
  skills: skillsResult.value
}))

const quickActions = computed(() => {
  if (!personalizedPlan.value) {
    return [
      '补充目标岗位和核心技能，先让推荐模型识别你的求职方向。',
      '上传简历 PDF，让系统自动回填学历、技能和经历。',
      '生成个人报告，把匹配分析转成可执行的提升动作。'
    ]
  }
  return [
    ...(personalizedPlan.value.planSummary || []),
    ...((personalizedPlan.value.skillGap?.recommendedSkills || []).slice(0, 3).map((item) => `优先补齐技能：${item}`))
  ].slice(0, 5)
})

const skillRadarOption = computed(() => {
  if (!personalizedPlan.value?.skillRadar?.length) return null
  const radarData = personalizedPlan.value.skillRadar
  return {
    tooltip: { trigger: 'item' },
    radar: {
      indicator: radarData.map(item => ({ name: item.dimension, max: 100 })),
      splitArea: { areaStyle: { color: ['rgba(56, 189, 248, 0.05)', 'rgba(56, 189, 248, 0.02)'] } },
      axisName: { color: 'var(--c-text-secondary)' },
      axisLine: { lineStyle: { color: 'rgba(15,23,42,0.08)' } },
      splitLine: { lineStyle: { color: 'rgba(15,23,42,0.08)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: radarData.map(item => item.score),
        name: '能力评估',
        areaStyle: { color: 'rgba(30, 117, 255, 0.2)' },
        lineStyle: { color: 'var(--c-accent-primary)', width: 2 },
        itemStyle: { color: 'var(--c-accent-primary)' }
      }]
    }]
  }
})

const studentInsights = computed(() => {
  if (!resumeResult.value && !predictResult.value && !recommendedJobs.value.length) return []
  const score = Number(resumeResult.value?.overall_score || 0)
  const tipsCount = resumeResult.value?.improvement_tips?.length || 0
  const salary = Number(predictResult.value?.prediction || 0)
  const jobCount = recommendedJobs.value?.length || 0

  return [
    {
      title: '竞争力判断',
      summary: score ? `${Math.round(score)} 分` : '待评估',
      detail: score >= 80
        ? '当前简历已经具备较强竞争力，重点应转向提升表达质量和项目证明力。'
        : score >= 60
          ? '你的基础能力已具备，但还没有形成足够稳定的岗位说服力。'
          : '当前更需要先补齐关键技能和项目经历，再进入大规模投递。'
    },
    {
      title: '行动负荷',
      summary: `待优化 ${tipsCount} 项`,
      detail: tipsCount > 3
        ? '需要分阶段优化，不建议一次性改完所有问题，先改最影响匹配度的项。'
        : '优化项数量不多，说明你已经接近可投递状态。'
    },
    {
      title: '岗位机会密度',
      summary: `已命中 ${jobCount} 个推荐岗位`,
      detail: jobCount >= 4
        ? '说明当前方向已有比较明确的岗位承接，可以开始围绕目标岗位做针对性准备。'
        : '推荐岗位偏少，可能是目标方向过窄，也可能是技能描述还不够完整。'
    },
    {
      title: '薪资预期位置',
      summary: salary ? `${salary.toLocaleString('zh-CN')} 元/月` : '--',
      detail: salary
        ? '这个结果更适合拿来判断城市与方向是否匹配，不建议把它当成单点承诺。'
        : '当前还没有形成稳定薪资估计，建议先完善简历和目标岗位信息。'
    }
  ]
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
    count: rankedLayeredJobs.value.flat.length,
    topCity,
    avgScore,
    topTag
  }
})

const jobsSystemInsight = computed(() => {
  const payload = jobsResult.value || {}
  const recommendMeta = payload.recommendMeta || {}
  const experiment = payload.experiment || {}
  const recallDiagnostics = payload.recall_diagnostics || payload.recallDiagnostics || {}
  const rankingDiagnostics = payload.ranking_diagnostics || payload.rankingDiagnostics || {}

  const routeUniqueHits = recallDiagnostics.routeUniqueHits || {}
  const routeItems = Object.entries(routeUniqueHits)
    .map(([route, count]) => ({ route, count: Number(count) || 0 }))
    .sort((a, b) => b.count - a.count)

  const topRoutes = routeItems.slice(0, 5)
  const totalRecallHits = routeItems.reduce((sum, item) => sum + item.count, 0)
  const routeCoverage = Number(recallDiagnostics.routeCount || 0)

  const stageRaw = Number(rankingDiagnostics.rawCandidates || 0)
  const stageCoarse = Number(rankingDiagnostics.afterCoarse || 0)
  const stageFinal = Number(rankingDiagnostics.finalCount || 0)
  const coarsePassRate = stageRaw > 0 ? Math.round((stageCoarse / stageRaw) * 100) : 0
  const finalPassRate = stageRaw > 0 ? Math.round((stageFinal / stageRaw) * 100) : 0

  return {
    abGroup: recommendMeta.abGroup || experiment.group || '--',
    strategyVersion: recommendMeta.strategyVersion || experiment.strategyVersion || '--',
    source: recommendMeta.source || '--',
    algorithmUsed: recommendMeta.algorithmUsed === true,
    breakerOpen: recommendMeta.breakerOpen === true,
    algorithmHealthy: recommendMeta.algorithmHealthy !== false,
    routeCoverage,
    totalRecallHits,
    topRoutes,
    stageRaw,
    stageCoarse,
    stageFinal,
    coarsePassRate,
    finalPassRate
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
  const reasons = job.whyMatched || job.why_matched || job.reasons || job.reason || job.advice?.why_matched
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

function openReportFromRecommend() {
  router.push({
    path: '/reports',
    query: { reportType: reportTypeByTab.value }
  })
}

function openAiFromRecommend() {
  const draft = isTeacher.value
    ? '请基于课程供需与教学改革结果输出可执行建议。'
    : isAdmin.value
      ? '请基于平台推荐与运营数据输出管理分析结论。'
      : '请基于我的智能推荐结果输出求职下一步行动方案。'
  router.push({
    path: '/ai',
    query: { draft }
  })
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
    const modeMeta = recommendModeMeta.value
    jobsResult.value = await recommendJobs(authStore.token, {
      skills: splitInput(jobsForm.value.skills),
      preferredCities: splitInput(jobsForm.value.preferredCities),
      education: jobsForm.value.education,
      experience: jobsForm.value.experience,
      industry: jobsForm.value.industry,
      limit: Number(jobsForm.value.limit),
      ab_group: modeMeta.abGroup,
      strategy_version: modeMeta.strategyVersion,
      recommend_mode: modeMeta.key
    })
    clearPrototypeResult('jobs')
    resetVisibleJobs()
  } catch (e) {
    jobsResult.value = null
    usePrototypeResult('jobs', `职位推荐接口暂未返回，已切换为示例岗位结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

// --- 来自 main 的工具函数与 API 调用 ---
function listify(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function formatScore(value) {
  const num = Number(value)
  return Number.isFinite(num) ? Math.round(num) : '--'
}

function formatMoney(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : value || '--'
}

function formatPercent(value) {
  const num = Number(value)
  return Number.isFinite(num) ? `${Math.round(num)}%` : '--'
}

function triggerFileUpload() {
  fileInputRef.value?.click()
}

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

async function handleParseResume(event) {
  const file = event.target.files?.[0]
  if (!file) return
  parsing.value = true
  error.value = ''
  success.value = ''
  try {
    const data = await parseResume(file)
    form.value.userSkills = listify(data.skills).join(', ')
    form.value.education = data.education || '本科'
    form.value.experienceYears = data.experience_years || 0
    if (data.target_city) form.value.targetCity = data.target_city
    if (data.industry) form.value.industry = data.industry
    event.target.value = ''
    success.value = '简历识别成功，已自动填充关键信息。'
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    parsing.value = false
  }
}

// TODO: 集成后复核 main 的一键诊断接口对接到 wt 的 resumeResult/predictResult 桥接字段
async function handleSmartAnalysis() {
  if (!form.value.targetJobType) {
    error.value = '请先填写目标岗位。'
    return
  }

  loading.value = true
  error.value = ''
  success.value = ''
  resumeResult.value = null
  predictResult.value = null

  try {
    const payloadJob = {
      skills: splitInput(form.value.userSkills),
      preferredCities: splitInput(form.value.targetCity),
      education: form.value.education,
      experience: `${form.value.experienceYears}年`,
      industry: form.value.industry,
      limit: 6
    }

    const [scoreRes, jobsRes, salaryRes] = await Promise.all([
      scoreResume({
        target_job_type: form.value.targetJobType,
        target_city: form.value.targetCity,
        education: form.value.education,
        experience_years: Number(form.value.experienceYears),
        industry: form.value.industry,
        skills: splitInput(form.value.userSkills)
      }),
      recommendJobs(authStore.token, payloadJob),
      predictSalary(authStore.token, {
        city: form.value.targetCity,
        education: form.value.education,
        experience: `${form.value.experienceYears}年`,
        skills: splitInput(form.value.userSkills),
        industry: form.value.industry
      }).catch(() => null)
    ])

    resumeResult.value = scoreRes
    jobsResult.value = jobsRes
    predictResult.value = salaryRes
    clearPrototypeResult('resume')
    clearPrototypeResult('jobs')
    clearPrototypeResult('salary')
    success.value = isStudent.value ? '学生求职分析已生成。' : '简历诊断结果已生成。'
    await loadPersonalizedPlan()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

// TODO: 集成后复核 技能差距接口暂未接入，保留 wt 的原型回退
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
    skillsResult.value = null
    radarResult.value = null
    usePrototypeResult('skills', '技能差距接口暂未返回，已切换为示例结果。')
  } catch (e) {
    skillsResult.value = null
    radarResult.value = null
    usePrototypeResult('skills', `技能差距接口暂未返回，已切换为示例结果。${normalizeError(e) ? ` ${normalizeError(e)}` : ''}`)
  } finally {
    loading.value = false
  }
}

// TODO: 集成后复核 职业路径 / 简历详评接口尚未接入，保留原型回退。
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
    usePrototypeResult('path', '职业路径接口尚未接入，已切换为示例路径结果。')
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

onMounted(loadPersonalizedPlan)
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

        <section class="recommend-main">
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
            <div class="mode-switch">
              <span class="field-label">刷新策略</span>
              <div class="mode-switch-row">
                <button
                  v-for="mode in RECOMMEND_MODE_OPTIONS"
                  :key="mode.key"
                  type="button"
                  class="mode-chip"
                  :class="{ 'is-active': jobsForm.recommendMode === mode.key }"
                  @click="jobsForm.recommendMode = mode.key"
                >
                  {{ mode.label }}
                </button>
              </div>
              <p class="mode-switch-desc">{{ recommendModeMeta.desc }}</p>
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
          <div class="analysis-bridge">
            <button type="button" class="bridge-btn" @click="openReportFromRecommend">
              去报告页生成报告
            </button>
            <button type="button" class="bridge-btn" @click="openAiFromRecommend">
              去 AI 页深度分析
            </button>
          </div>
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

            <section v-if="!jobsUsingPrototype && jobsResult" class="insight-card system-insight">
              <div class="system-insight-head">
                <h3>推荐系统洞察</h3>
                <span class="system-badge" :class="jobsSystemInsight.algorithmUsed ? 'is-online' : 'is-fallback'">
                  {{ jobsSystemInsight.algorithmUsed ? '在线策略' : '降级策略' }}
                </span>
              </div>
              <div class="system-grid">
                <div class="system-item">
                  <span>实验组</span>
                  <strong>{{ jobsSystemInsight.abGroup }}</strong>
                </div>
                <div class="system-item">
                  <span>策略版本</span>
                  <strong>{{ jobsSystemInsight.strategyVersion }}</strong>
                </div>
                <div class="system-item">
                  <span>召回路由数</span>
                  <strong>{{ jobsSystemInsight.routeCoverage }}</strong>
                </div>
                <div class="system-item">
                  <span>召回命中量</span>
                  <strong>{{ jobsSystemInsight.totalRecallHits }}</strong>
                </div>
                <div class="system-item">
                  <span>粗排通过率</span>
                  <strong>{{ jobsSystemInsight.coarsePassRate }}%</strong>
                </div>
                <div class="system-item">
                  <span>最终入池率</span>
                  <strong>{{ jobsSystemInsight.finalPassRate }}%</strong>
                </div>
              </div>
              <div v-if="jobsSystemInsight.topRoutes.length" class="system-route-list">
                <span
                  v-for="item in jobsSystemInsight.topRoutes"
                  :key="item.route"
                  class="system-route-chip"
                >
                  {{ item.route }} · {{ item.count }}
                </span>
              </div>
              <p class="system-note">
                source={{ jobsSystemInsight.source }} ·
                health={{ jobsSystemInsight.algorithmHealthy ? 'ok' : 'degraded' }} ·
                breaker={{ jobsSystemInsight.breakerOpen ? 'open' : 'closed' }}
              </p>
            </section>

            <div v-if="hasStructuredJobs" class="job-list" aria-label="推荐岗位列表">
              <article
                v-for="(item, index) in visibleJobs"
                :key="item.job.jobId || item.job.job_id || item.job.id || `${getJobTitle(item.job)}-${item.rank}`"
                class="job-card"
                tabindex="0"
                role="button"
                @click="openRecommendedJob(item.job)"
                @keydown.enter.prevent="openRecommendedJob(item.job)"
                @keydown.space.prevent="openRecommendedJob(item.job)"
              >
                <div
                  v-if="index === 0 || visibleJobs[index - 1].layer !== item.layer"
                  class="job-layer-divider"
                >
                  <span class="job-layer-title">{{ JOB_LAYER_META[item.layer]?.label || '推荐岗位' }}</span>
                  <small>{{ JOB_LAYER_META[item.layer]?.hint || '' }}</small>
                </div>
                <div class="job-card-top">
                  <span class="job-rank">MATCH {{ String(item.rank).padStart(2, '0') }}</span>
                  <span v-if="getJobConfidence(item.job) !== null" class="job-score">
                    {{ getJobConfidence(item.job) }}%
                  </span>
                </div>
                <h3 class="job-title">{{ getJobTitle(item.job) }}</h3>
                <div class="job-meta">
                  <span><Building2 :size="13" /> {{ getJobCompany(item.job) }}</span>
                  <span><MapPin :size="13" /> {{ getJobCity(item.job) }}</span>
                </div>
                <p class="job-salary">{{ getJobSalary(item.job) }}</p>
                <p class="job-reason">{{ getJobReason(item.job) }}</p>
                <div v-if="getJobRiskReasons(item.job).length" class="job-risk-tags">
                  <span
                    v-for="risk in getJobRiskReasons(item.job).slice(0, 2)"
                    :key="`${item.rank}-${risk}`"
                  >
                    <AlertTriangle :size="12" />
                    {{ risk }}
                  </span>
                </div>
                <div v-if="getJobTags(item.job).length" class="job-tags">
                  <span v-for="tag in getJobTags(item.job)" :key="tag">{{ tag }}</span>
                </div>
                <div v-if="item.duplicateCount > 0" class="job-dup-note">
                  同公司同类岗位已折叠 {{ item.duplicateCount + 1 }} 条
                </div>
                <div class="job-footer">
                  <span><Target :size="13" /> 匹配依据</span>
                  <span class="job-link">{{ jobsUsingPrototype ? '示例详情' : '查看详情' }}</span>
                </div>
              </article>
            </div>
            <div v-else class="empty-block">当前条件下没有找到岗位结果。</div>

            <div v-if="hasMoreJobs" class="list-more">
              <button type="button" class="more-btn" @click="showMoreJobs">
                查看更多（已显示 {{ visibleJobs.length }} / {{ rankedLayeredJobs.flat.length }}）
              </button>
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
      </div>
    </div>

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

.analysis-bridge {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid var(--c-border-glass);
}

.bridge-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.bridge-btn:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.mode-switch {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mode-switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.mode-chip:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-bg-surface-hover);
}

.mode-chip.is-active {
  border-color: var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.mode-switch-desc {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12px;
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
 * Job result list + cards (flat, no 3D tilt)
 * -------------------------------------------------------- */
.job-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.job-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.job-card:hover,
.job-card:focus-visible {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  box-shadow: 0 6px 18px var(--c-accent-primary-glow);
  outline: none;
}

.job-layer-divider {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: -2px 0 2px;
  padding: 8px 10px;
  border: 1px dashed var(--c-border-glass-hover);
  border-radius: 9px;
  background: var(--c-bg-surface-hover);
}

.job-layer-title {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-accent-primary);
}

.job-layer-divider small {
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11.5px;
  line-height: 1.4;
}

.job-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.job-rank {
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--c-accent-primary);
}

.job-score {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.job-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.3;
  color: var(--c-text-primary);
}

.job-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

.job-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.job-salary {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--c-accent-primary);
}

.job-reason {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* VChart 容器 */
.chart {
  width: 100%;
  height: 100%;
}

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

.job-risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.job-risk-tags span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid rgba(178, 59, 46, 0.2);
  border-radius: 999px;
  background: rgba(254, 242, 240, 0.75);
  color: #b23b2e;
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 600;
}

.job-dup-note {
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
}

.job-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--c-border-glass);
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

.job-footer span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.job-link {
  color: var(--c-accent-primary);
  font-weight: 700;
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

.system-insight {
  margin-bottom: 14px;
}

.system-insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.system-badge {
  padding: 4px 9px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  border: 1px solid transparent;
}

.system-badge.is-online {
  color: #0f7a57;
  border-color: rgba(15, 122, 87, 0.3);
  background: rgba(15, 122, 87, 0.12);
}

.system-badge.is-fallback {
  color: #8f4a00;
  border-color: rgba(143, 74, 0, 0.28);
  background: rgba(143, 74, 0, 0.12);
}

.system-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.system-item {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.system-item span {
  font-family: var(--font-sans);
  font-size: 11px;
  color: var(--c-text-tertiary);
}

.system-item strong {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
  font-weight: 700;
}

.system-route-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.system-route-chip {
  padding: 5px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--c-accent-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--c-accent-primary) 28%, transparent);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-secondary);
}

.system-note {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-tertiary);
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
 * Job detail modal
 * -------------------------------------------------------- */
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
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  background: var(--c-bg-modal);
  box-shadow: var(--shadow-glass);
}

.recommend-modal-close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.recommend-modal-close:hover {
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
}

.recommend-modal-head {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px 16px;
  border-bottom: 1px solid var(--c-border-glass);
}

.recommend-modal-kicker {
  display: inline-block;
  margin-bottom: 8px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}

.recommend-modal-copy h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(22px, 2.2vw, 28px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.recommend-modal-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-top: 12px;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-secondary);
}

.recommend-modal-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.recommend-modal-salary {
  align-self: flex-start;
  padding: 8px 14px;
  border-radius: 10px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.recommend-modal-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px 28px 0;
}

.recommend-modal-tags span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
}

.recommend-modal-loading,
.recommend-modal-body {
  padding: 20px 28px 8px;
}

.recommend-modal-loading {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-muted);
}

.recommend-modal-section + .recommend-modal-section {
  margin-top: 18px;
}

.recommend-modal-section h3 {
  margin: 0 0 10px;
  font-family: var(--font-serif);
  font-size: 14.5px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}

.recommend-modal-text {
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1.75;
  color: var(--c-text-secondary);
  white-space: normal;
}

.recommend-modal-section.empty .recommend-modal-text {
  color: var(--c-text-muted);
}

.recommend-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 28px 24px;
}

.recommend-modal-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 700;
  color: var(--c-accent-primary);
  text-decoration: none;
}

.recommend-modal-link:hover {
  text-decoration: underline;
}

/* ----------------------------------------------------------
 * Dark mode overrides — the error/success banners keep their warm
 * pastel fills in light mode; in dark mode those glow, so we swap
 * to translucent semantic tints. The modal overlay's chilly blue
 * gets traded for a deeper scrim that reads as dim-on-dim. Same for
 * the modal salary pill's accent glow that otherwise washes out.
 * -------------------------------------------------------- */
[data-theme="dark"] .recommend-banner.error {
  background: rgba(178, 59, 46, 0.18);
  color: #ffb4a6;
}
[data-theme="dark"] .recommend-banner.success {
  background: rgba(30, 138, 91, 0.18);
  color: #b6e8c8;
}
[data-theme="dark"] .recommend-modal-overlay {
  background: rgba(12, 14, 22, 0.68);
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

  .system-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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

  .recommend-modal-overlay {
    padding: 14px;
  }

  .recommend-modal-head {
    flex-direction: column;
    gap: 14px;
    padding: 22px 22px 14px;
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
    align-items: stretch;
  }

  .system-grid {
    grid-template-columns: 1fr;
  }
}
</style>
