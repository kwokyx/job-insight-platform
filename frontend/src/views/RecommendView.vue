<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchCareerProfile,
  fetchJobDetail,
  fetchJobRankerStatus,
  fetchPersonalizedRecommendPlan,
  importAiProfileFile,
  normalizeError,
  parseResume,
  predictSalary,
  recommendCareerPath,
  recommendJobs,
  recommendSkillRadar,
  recommendSkills,
  reviewResume,
  scoreResume,
  trainJobRanker,
  updateProfile
} from '../api'
import {
  AlertTriangle,
  Briefcase,
  Building2,
  Calculator,
  Clock,
  ExternalLink,
  FileSearch,
  GraduationCap,
  Lightbulb,
  MapPin,
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
const resumeUploadName = ref('')
const personalizedPlan = ref(null)
const selectedJob = ref(null)
const isLoadingJobDetail = ref(false)
const rankerStatus = ref(null)
const rankerLoading = ref(false)
const rankerTraining = ref(false)

const jobsForm = ref({
  skills: 'Java, Spring Boot, MySQL',
  coreSkills: 'Java, Spring Boot, MySQL',
  preferredCities: '北京, 上海',
  excludedKeywords: '',
  preferredCompanySizes: '',
  preferredFinanceStages: '',
  education: '本科',
  experience: '1-3年',
  targetJobType: '',
  targetCity: '',
  industry: '',
  experienceYears: 1,
  userSkills: ''
})
// Alias: main 的代码使用 form.*，wt 的 UI 使用 jobsForm.*，此处保持双向引用。
const form = jobsForm
const jobsResult = ref(null)
const recommendedJobs = computed(() => {
  const payload = jobsResult.value
  if (!payload) {
    return []
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
  resumeText: 'Two years of backend development experience, API design, database modelling, and cache optimization.',
  currentJob: 'Backend Developer',
  education: '本科',
  experienceYears: 2,
  targetCity: 'Beijing',
  industry: 'Internet'
})
const resumeResult = ref(null)
const parsedResumeData = ref(null)

const predictForm = ref({
  city: 'Beijing',
  education: '本科',
  experience: '1-3年',
  skills: 'Java, Spring Boot, MySQL',
  industry: 'Internet'
})
const predictResult = ref(null)

const uploadFile = ref(null)
const overwriteSkills = ref(true)
const importResult = ref(null)
const selectedImportFileName = computed(() => uploadFile.value?.name || '')

const tabs = [
  { key: 'resume', label: '简历优化', icon: FileSearch },
  { key: 'jobs', label: '职位匹配', icon: Sparkles },
  { key: 'salary', label: '薪资参考', icon: Calculator }
]

const activeTab = ref('resume')
const activeTabMeta = computed(() => tabs.find((item) => item.key === activeTab.value) || tabs[0])
const controlPanelDescription = computed(() => {
  if (activeTab.value === 'resume') {
    return '先上传简历并补齐画像字段，再驱动后续职位匹配和薪资参考。'
  }

  if (activeTab.value === 'jobs') {
    return '简历画像会自动回填到这里，进一步生成更精准的职位匹配结果。'
  }

  return '基于简历画像中的城市、学历、经验和技能，生成更接近真实求职场景的薪资参考。'
})
const resultCountText = computed(() => {
  if (activeTab.value === 'jobs') {
    return hasStructuredJobs.value ? `${recommendedJobs.value.length} 条结果` : '暂无结果'
  }

  if (activeTab.value === 'resume') {
    return resumeResult.value ? '已生成结果' : '暂无结果'
  }

  return predictResult.value ? '已生成结果' : '暂无结果'
})

// 角色感知 + 个人化计划（来自 main）
const isStudent = computed(() => (authStore.user?.roleType ?? 0) === 0)
const isAdmin = computed(() => (authStore.user?.roleType ?? 0) === 1)
const isTeacher = computed(() => (authStore.user?.roleType ?? 0) === 2)

// results 兼容层：wt UI 大量使用 jobsResult/skillsResult/resumeResult/predictResult 分散的 ref，
// main 新增接口使用统一的 results.* 字段，这里提供一个聚合读取。
const results = computed(() => ({
  jobs: recommendedJobs.value,
  score: resumeResult.value,
  salary: predictResult.value,
  skills: skillsResult.value
}))

const quickActions = computed(() => {
  if (!personalizedPlan.value) {
    return [
      '先上传简历 PDF 或粘贴简历正文，建立完整求职画像。',
      '补充目标岗位、技能、学历、经验和城市，让后续推荐更聚焦。',
      '完成简历优化后，再看职位匹配和薪资参考，结果会更稳定。'
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

function pickCoreSkills(skills, targetJob = '') {
  const normalizedSkills = normalizeStrings(skills, 12)
  if (!normalizedSkills.length) {
    return []
  }

  const targetText = `${targetJob || ''}`.toLowerCase()
  const semanticHints = [
    /算法|机器学习|深度学习|大数据|数据分析|推荐系统|自然语言|计算机视觉|后端|前端|测试|运维/i,
    /java|python|sql|spark|hadoop|pyspark|redis|mysql|docker|vue|react|c\+\+|linux|git/i
  ]
  const noiseHints = [/能力$/, /处理$/, /设计$/, /基础$/, /路线$/, /实践$/, /总结$/, /文档$/, /表达$/]

  const scored = normalizedSkills.map((skill, index) => {
    let score = 0

    if (/[A-Za-z0-9+#]/.test(skill)) {
      score += 5
    }
    if (semanticHints.some((pattern) => pattern.test(skill))) {
      score += 4
    }
    if (targetText && targetText.includes('算法') && /算法|机器学习|深度学习|python|c\+\+/i.test(skill)) {
      score += 3
    }
    if (targetText && /大数据|data/.test(targetText) && /大数据|python|sql|spark|hadoop|pyspark/i.test(skill)) {
      score += 3
    }
    if (targetText && /ai|人工智能/.test(targetText) && /机器学习|深度学习|python|大模型|llm/i.test(skill)) {
      score += 3
    }
    if (skill.length <= 12) {
      score += 1
    }
    if (noiseHints.some((pattern) => pattern.test(skill))) {
      score -= 4
    }

    return { skill, index, score }
  })

  const preferred = scored
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .map((item) => item.skill)

  return normalizeStrings(preferred.length ? preferred : normalizedSkills, 3)
}

function syncResumeProfileToDownstream(profile = {}) {
  const mergedSkills = normalizeStrings([
    ...splitInput(resumeForm.value.userSkills),
    ...(profile.skills || []),
    ...(resumeResult.value?.matchedSkills || [])
  ], 12)
  const skillsText = mergedSkills.join(', ')
  const targetJob = firstText(profile.targetJob, resumeForm.value.targetJob)
  const coreSkills = pickCoreSkills(mergedSkills, targetJob)
  const city = firstText(profile.targetCity, resumeForm.value.targetCity)
  const education = firstText(profile.education, resumeForm.value.education)
  const industry = firstText(profile.industry, resumeForm.value.industry)
  const years = Number(profile.experienceYears ?? resumeForm.value.experienceYears ?? 0)
  const experienceText = years > 0 ? `${years}年` : jobsForm.value.experience

  if (skillsText) {
    jobsForm.value.skills = skillsText
    jobsForm.value.coreSkills = coreSkills.join(', ')
    jobsForm.value.userSkills = skillsText
    predictForm.value.skills = skillsText
  }
  if (targetJob) {
    jobsForm.value.targetJobType = targetJob
  }
  if (city) {
    jobsForm.value.preferredCities = city
    jobsForm.value.targetCity = city
    predictForm.value.city = city
  }
  if (education) {
    jobsForm.value.education = education
    predictForm.value.education = education
  }
  if (industry) {
    jobsForm.value.industry = industry
    predictForm.value.industry = industry
  }
  if (years > 0) {
    jobsForm.value.experienceYears = years
    jobsForm.value.experience = experienceText
    predictForm.value.experience = experienceText
  }
}

function normalizeNullableText(value) {
  const text = typeof value === 'string' ? value.trim() : ''
  return text || null
}

function buildResumeProfilePayload() {
  const mergedSkills = normalizeStrings([
    ...splitInput(resumeForm.value.userSkills),
    ...(parsedResumeData.value?.skills || []),
    ...(resumeResult.value?.extractedProfile?.skills || []),
    ...(resumeResult.value?.matchedSkills || [])
  ], 24)

  const experienceYears = Number(resumeForm.value.experienceYears)

  return {
    educationLevel: normalizeNullableText(resumeForm.value.education),
    skills: mergedSkills,
    profileSummary: normalizeNullableText(resumeForm.value.targetJob),
    targetJob: normalizeNullableText(resumeForm.value.targetJob),
    currentJob: normalizeNullableText(resumeForm.value.currentJob),
    targetCityName: normalizeNullableText(resumeForm.value.targetCity),
    industry: normalizeNullableText(resumeForm.value.industry),
    experienceYears: Number.isFinite(experienceYears) && experienceYears > 0 ? experienceYears : null,
    resumeText: normalizeNullableText(resumeForm.value.resumeText),
    resumeFileName: normalizeNullableText(resumeUploadName.value || uploadFile.value?.name)
  }
}

async function persistResumeProfile(options = {}) {
  if (!authStore.isLoggedIn) {
    return null
  }

  try {
    await updateProfile(authStore.token, buildResumeProfilePayload())
    return true
  } catch (persistError) {
    if (options.silent) {
      return null
    }
    throw persistError
  }
}

function hydrateResumeProfile(profilePayload = {}) {
  const profile = profilePayload?.profile || {}
  const savedSkills = Array.isArray(profilePayload?.skills) ? profilePayload.skills : []

  if (savedSkills.length) {
    const skillsText = savedSkills.join(', ')
    resumeForm.value.userSkills = skillsText
    jobsForm.value.skills = skillsText
    jobsForm.value.userSkills = skillsText
    jobsForm.value.coreSkills = pickCoreSkills(savedSkills, profile.targetJob || profile.profileSummary || '').join(', ')
    predictForm.value.skills = skillsText
  }

  if (profile.educationLevel) {
    resumeForm.value.education = profile.educationLevel
    jobsForm.value.education = profile.educationLevel
    predictForm.value.education = profile.educationLevel
  }

  if (profile.targetJob || profile.profileSummary) {
    const targetJob = profile.targetJob || profile.profileSummary
    resumeForm.value.targetJob = targetJob
    jobsForm.value.targetJobType = targetJob
  }

  if (profile.currentJob) {
    resumeForm.value.currentJob = profile.currentJob
  }

  if (profile.targetCityName || profile.targetCityCode) {
    const targetCity = profile.targetCityName || profile.targetCityCode
    resumeForm.value.targetCity = targetCity
    jobsForm.value.preferredCities = targetCity
    jobsForm.value.targetCity = targetCity
    predictForm.value.city = targetCity
  }

  if (profile.industry) {
    resumeForm.value.industry = profile.industry
    jobsForm.value.industry = profile.industry
    predictForm.value.industry = profile.industry
  }

  if (Number.isFinite(Number(profile.experienceYears)) && Number(profile.experienceYears) > 0) {
    const years = Number(profile.experienceYears)
    resumeForm.value.experienceYears = years
    jobsForm.value.experienceYears = years
    jobsForm.value.experience = `${years}年`
    predictForm.value.experience = `${years}年`
  }

  if (profile.resumeText) {
    resumeForm.value.resumeText = profile.resumeText
  }

  if (profile.resumeFileName) {
    resumeUploadName.value = profile.resumeFileName
  }

  syncResumeProfileToDownstream({
    targetJob: profile.targetJob || profile.profileSummary,
    targetCity: profile.targetCityName || profile.targetCityCode,
    education: profile.educationLevel,
    experienceYears: profile.experienceYears,
    industry: profile.industry,
    skills: savedSkills
  })
}

async function loadPersistedCareerProfile() {
  if (!authStore.isLoggedIn) {
    return
  }

  try {
    const profilePayload = await fetchCareerProfile(authStore.token)
    hydrateResumeProfile(profilePayload)
  } catch {
    // Keep page usable even if profile bootstrap fails.
  }
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

      const title = firstText(item.title, item.section, item.name, item.skill, item.label)
      if (!title) {
        return null
      }

      return {
        title,
        detail: firstText(item.detail, item.advice, item.issue, item.reason, item.description, item.text, item.gap) || '建议纳入下一阶段提升计划。',
        meta: firstText(item.meta, item.priority, item.level, item.type, item.expectedGain ? `预期 +${item.expectedGain}` : '') || '建议优先'
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

function normalizeResumeScorecard(value, limit = 9) {
  const labelMap = {
    keywordCoverage: '关键词覆盖',
    projectEvidence: '项目证据',
    businessImpact: '结果量化',
    structureReadability: '结构表达',
    architectureEvidence: '架构深度',
    deliveryEvidence: '交付闭环',
    experienceFit: '经验匹配',
    educationFit: '学历匹配',
    locationFit: '城市匹配'
  }

  return Object.entries(value || {})
    .map(([key, score]) => {
      const num = Number(score)
      if (!Number.isFinite(num)) {
        return null
      }

      const normalized = Math.max(0, Math.min(100, Math.round(num <= 1 ? num * 100 : num)))
      return {
        key,
        label: labelMap[key] || key,
        score: normalized,
        tone: normalized >= 80 ? 'strong' : (normalized >= 60 ? 'medium' : 'weak')
      }
    })
    .filter(Boolean)
    .slice(0, limit)
}

function normalizeRiskTone(value) {
  const text = String(value || '').toLowerCase()
  if (['low', '低', '较低'].some((item) => text.includes(item))) {
    return { label: '低风险', tone: 'strong' }
  }
  if (['medium', '中', '一般'].some((item) => text.includes(item))) {
    return { label: '中风险', tone: 'medium' }
  }
  if (['high', '高', '严重'].some((item) => text.includes(item))) {
    return { label: '高风险', tone: 'weak' }
  }
  return { label: value || '待评估', tone: 'medium' }
}

function normalizeReadinessTone(value, score) {
  const text = String(value || '').trim()
  if (text) {
    if (text.includes('可投')) return { label: text, tone: 'strong' }
    if (text.includes('优化后')) return { label: text, tone: 'medium' }
    if (text.includes('重写')) return { label: text, tone: 'weak' }
    return { label: text, tone: score >= 78 ? 'strong' : (score >= 60 ? 'medium' : 'weak') }
  }

  if (score >= 78) return { label: '可直接投递', tone: 'strong' }
  if (score >= 60) return { label: '优化后可投递', tone: 'medium' }
  return { label: '建议重点重写', tone: 'weak' }
}

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

const jobResultSummary = computed(
  () => jobsResult.value?.summary || jobsResult.value?.data?.summary || null
)

const jobProfileSnapshot = computed(
  () => jobsResult.value?.profile || jobsResult.value?.data?.profile || null
)

const jobsInputQuality = computed(() => {
  const score = Math.max(0, Math.min(100, [
    jobsForm.value.targetJobType ? 24 : 0,
    splitInput(jobsForm.value.skills).length ? 18 : 0,
    splitInput(jobsForm.value.coreSkills).length >= 2 ? 18 : 0,
    splitInput(jobsForm.value.preferredCities).length ? 10 : 0,
    jobsForm.value.education ? 8 : 0,
    jobsForm.value.experience ? 8 : 0,
    Number(jobsForm.value.experienceYears) > 0 ? 6 : 0,
    jobsForm.value.industry ? 4 : 0,
    splitInput(jobsForm.value.preferredCompanySizes).length ? 2 : 0,
    splitInput(jobsForm.value.preferredFinanceStages).length ? 2 : 0
  ].reduce((sum, item) => sum + item, 0)))

  const missing = []
  if (!jobsForm.value.targetJobType) missing.push('补充目标岗位，先锁定岗位族')
  if (splitInput(jobsForm.value.coreSkills).length < 2) missing.push('至少填写 2 个核心技能，避免召回过宽')
  if (!jobsForm.value.experience) missing.push('补充经验区间，减少层级错配')
  if (!splitInput(jobsForm.value.preferredCities).length) missing.push('补充期望城市，降低区域噪声')
  if (!jobsForm.value.industry) missing.push('补充目标行业，让结果更聚焦')

  let level = '待加强'
  let summary = '输入画像还偏薄，模型会更多依赖通用信号，推荐容易发散。'
  if (score >= 80) {
    level = '高质量'
    summary = '输入约束已经比较完整，推荐会更贴近目标岗位、城市和层级。'
  } else if (score >= 60) {
    level = '可用'
    summary = '方向已基本明确，再补 1 到 2 个关键条件就能继续压缩噪声。'
  }

  return {
    score,
    level,
    summary,
    missing: missing.slice(0, 4)
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
        strengths,
        gaps,
        actions,
        radar
      }
    : null

  return parsed
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

  return parsed
})

const resumeInsight = computed(() => {
  const rawScore = Number(
    resumeResult.value?.score ??
    resumeResult.value?.overallScore ??
    resumeResult.value?.overall_score ??
    resumeResult.value?.totalScore ??
    resumeResult.value?.matchScore
  )
  const score = Number.isFinite(rawScore)
    ? Math.max(0, Math.min(100, Math.round(rawScore <= 1 ? rawScore * 100 : rawScore)))
    : 0
  const strengths = normalizeStrings(
    resumeResult.value?.strengths ||
    resumeResult.value?.highlights ||
    resumeResult.value?.advantages ||
    resumeResult.value?.matchedSkills,
    4
  )
  const issues = normalizeStrings(
    resumeResult.value?.issues ||
    resumeResult.value?.improvements ||
    resumeResult.value?.weaknesses ||
    resumeResult.value?.missingSkills,
    4
  )
  const actions = normalizeStrings(
    resumeResult.value?.actions ||
    resumeResult.value?.suggestions ||
    resumeResult.value?.recommendations ||
    resumeResult.value?.rewriteHints,
    4
  )
  const keywords = normalizeStrings(
    resumeResult.value?.keywords ||
    resumeResult.value?.tags ||
    resumeResult.value?.matchedSkills ||
    resumeResult.value?.missingSkills,
    6
  )
  const matchedKeywords = normalizeStrings(
    resumeResult.value?.matchedSkills ||
    resumeResult.value?.matched_keywords ||
    resumeResult.value?.keywordsMatched,
    8
  )
  const missingKeywords = normalizeStrings(
    resumeResult.value?.missingSkills ||
    resumeResult.value?.missing_keywords ||
    resumeResult.value?.keywordsMissing,
    8
  )
  const rewriteHints = normalizeStrings(
    resumeResult.value?.rewriteHints ||
    resumeResult.value?.rewrite_hints ||
    resumeResult.value?.sectionAdvice,
    5
  )
  const sectionAdvice = normalizeDetailItems(
    resumeResult.value?.sectionAdvice ||
    resumeResult.value?.section_advice,
    4
  )
  const priorityFixes = normalizeDetailItems(
    resumeResult.value?.priorityFixes ||
    resumeResult.value?.priority_fixes,
    4
  )
  const scorecard = normalizeResumeScorecard(
    resumeResult.value?.scorecard ||
    resumeResult.value?.dimensionScores ||
    resumeResult.value?.dimension_scores
  )
  const diagnosis = resumeResult.value?.diagnosis || {}
  const extractedProfile = resumeResult.value?.extractedProfile || resumeResult.value?.profile || {}
  const profileTags = normalizeStrings([
    extractedProfile.currentJob && `当前岗位: ${extractedProfile.currentJob}`,
    extractedProfile.education && `学历: ${extractedProfile.education}`,
    Number(extractedProfile.experienceYears) > 0 && `经验: ${Number(extractedProfile.experienceYears)} 年`,
    extractedProfile.targetCity && `城市: ${extractedProfile.targetCity}`,
    extractedProfile.industry && `行业: ${extractedProfile.industry}`
  ], 5)
  const extractedSkills = normalizeStrings(extractedProfile.skills, 10)
  const parsed = strengths.length || issues.length || actions.length || keywords.length || scorecard.length || matchedKeywords.length || missingKeywords.length || rewriteHints.length || Number.isFinite(rawScore)
    ? {
        score,
        summary: firstText(resumeResult.value?.summary, resumeResult.value?.analysis, resumeResult.value?.message) || '简历内容具备基本岗位贴合度，但表达层次仍需收紧。',
        strengths,
        issues,
        actions,
        keywords,
        scorecard,
        matchedKeywords,
        missingKeywords,
        rewriteHints,
        sectionAdvice,
        priorityFixes,
        diagnosis: {
          readiness: normalizeReadinessTone(diagnosis.readinessLevel || diagnosis.readiness, score),
          atsRisk: normalizeRiskTone(diagnosis.atsRisk || diagnosis.risk),
          coreIssue: firstText(diagnosis.coreIssue, diagnosis.problem, diagnosis.summary) || '待补充'
        },
        profileTags,
        extractedSkills
      }
    : null

  return parsed
})

const resumeParseInsight = computed(() => {
  const data = parsedResumeData.value
  if (!data || typeof data !== 'object') {
    return null
  }

  const fields = [
    { label: '姓名', value: firstText(data.name) },
    { label: '手机', value: firstText(data.phone) },
    { label: '邮箱', value: firstText(data.email) },
    { label: '目标岗位', value: firstText(data.target_job_type, data.targetJob) },
    { label: '当前岗位', value: firstText(data.current_job, data.currentJob) },
    { label: '目标城市', value: firstText(data.target_city, data.targetCity) },
    { label: '目标行业', value: firstText(data.industry) },
    { label: '学历', value: firstText(data.education) },
    {
      label: '工作年限',
      value: Number.isFinite(Number(data.experience_years ?? data.experienceYears))
        ? `${Number(data.experience_years ?? data.experienceYears)} 年`
        : ''
    }
  ].filter((item) => item.value)

  const missingFieldLabels = [
    !firstText(data.target_job_type, data.targetJob) && '目标岗位',
    !firstText(data.target_city, data.targetCity) && '目标城市',
    !firstText(data.industry) && '目标行业',
    !firstText(data.education) && '学历',
    !Number.isFinite(Number(data.experience_years ?? data.experienceYears)) && '工作年限'
  ].filter(Boolean)

  const awards = toList(data.awards)
    .map((item) => {
      if (!item || typeof item !== 'object') {
        return null
      }

      const name = firstText(item.name, item.title)
      if (!name) {
        return null
      }

      return {
        name,
        level: firstText(item.level) || '未识别级别',
        rank: firstText(item.rank) || '未识别等级',
        authority: firstText(item.authority) || '主办方待识别',
        score: formatScore(item.score),
        explanation: firstText(item.explanation) || '当前按规则信号做基线判断，不是完整赛事知识图谱。'
      }
    })
    .filter(Boolean)

  const parseMeta = data.parse_meta || {}
  const parserMode = String(parseMeta.parser_mode || '').includes('llm')
    ? '规则抽取 + LLM 补充'
    : '规则抽取'

  return {
    fields,
    missingFieldLabels,
    skills: normalizeStrings(data.skills, 12),
    certificates: normalizeStrings(data.certificates, 8),
    awards,
    parserMode,
    recognizedCount: Number(parseMeta.recognized_count || fields.length + awards.length),
    textLength: Number(data.raw_text_length || parseMeta.text_length || 0)
  }
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
        skills
      }
    : null

  return parsed
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
  const matchedSkills = normalizeStrings(
    predictResult.value?.matchedSkills ||
    predictResult.value?.matched_skills,
    8
  )
  const missingSkills = normalizeStrings(
    predictResult.value?.missingSkills ||
    predictResult.value?.missing_skills,
    8
  )
  const scorecard = normalizeRadar(
    predictResult.value?.salaryScorecard ||
    predictResult.value?.scorecard ||
    predictResult.value?.data?.salaryScorecard,
    4
  )

  const range = firstText(
    predictResult.value?.range,
    predictResult.value?.salaryRange,
    predictResult.value?.predictedRange,
    predictResult.value?.data?.range
  )

  const parsed = range || factors.length || benchmarks.length
    ? {
        range: range || '--',
        median: firstText(
          predictResult.value?.median,
          predictResult.value?.predictedSalary,
          predictResult.value?.predicted_median ? `${Math.round(predictResult.value.predicted_median)}K` : '',
          predictResult.value?.data?.median
        ) || '--',
        confidence: firstText(
          predictResult.value?.confidenceLabel,
          predictResult.value?.level,
          predictResult.value?.confidence
        ) || '--',
        summary: firstText(predictResult.value?.summary, predictResult.value?.analysis, predictResult.value?.message) || '',
        factors,
        benchmarks,
        matchedSkills,
        missingSkills,
        scorecard
      }
    : null

  return parsed
})

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
  importSuccess.value = ''
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

  if (!jobId) {
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
    error.value = '登录状态已失效，请重新登录后再导入资料。'
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
    await persistResumeProfile({ silent: true })
    importSuccess.value = `已导入个人资料。已保存技能数: ${importResult.value.savedSkills || 0}。`
    if (authStore.syncProfile) {
      await authStore.syncProfile()
    }
  } catch (e) {
    importResult.value = null
    error.value = normalizeError(e)
  } finally {
    importLoading.value = false
  }
}

async function handleJobsRecommend() {
  if (!authStore.isLoggedIn) {
    jobsResult.value = null
    error.value = '登录状态已失效，请重新登录后再运行推荐。'
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    jobsResult.value = await recommendJobs(authStore.token, {
      targetJobType: jobsForm.value.targetJobType,
      skills: splitInput(jobsForm.value.skills),
      coreSkills: splitInput(jobsForm.value.coreSkills),
      preferredCities: splitInput(jobsForm.value.preferredCities),
      excludedKeywords: splitInput(jobsForm.value.excludedKeywords),
      preferredCompanySizes: splitInput(jobsForm.value.preferredCompanySizes),
      preferredFinanceStages: splitInput(jobsForm.value.preferredFinanceStages),
      education: jobsForm.value.education,
      experience: jobsForm.value.experience,
      experienceYears: Number(jobsForm.value.experienceYears) || undefined,
      industry: jobsForm.value.industry,
      limit: Number(jobsForm.value.limit)
    })
    resetVisibleJobs()
    await loadJobRankerStatus()
  } catch (e) {
    jobsResult.value = null
    error.value = normalizeError(e)
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

async function loadJobRankerStatus() {
  if (!authStore.isLoggedIn) {
    rankerStatus.value = null
    return
  }

  rankerLoading.value = true
  try {
    rankerStatus.value = await fetchJobRankerStatus(authStore.token)
  } catch {
    rankerStatus.value = null
  } finally {
    rankerLoading.value = false
  }
}

async function handleTrainJobRanker() {
  if (!authStore.isLoggedIn || rankerTraining.value) {
    return
  }

  rankerTraining.value = true
  error.value = ''
  success.value = ''

  try {
    const result = await trainJobRanker(authStore.token, 20000)
    const sampleCount = result?.sample_count ?? result?.data?.sample_count ?? 0
    success.value = `职位排序模型训练完成，样本数 ${sampleCount}。`
    await loadJobRankerStatus()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    rankerTraining.value = false
  }
}

async function handleParseResume(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploadFile.value = file
  resumeUploadName.value = file.name
  parsing.value = true
  error.value = ''
  success.value = ''
  try {
    const data = await parseResume(file)
    parsedResumeData.value = data
    const parsedSkills = listify(data.skills).join(', ')
    const parsedTargetJob = data.target_job_type || data.targetJob || data.target_role || ''
    const parsedResumeText = data.resume_text || data.resumeText || data.text || ''
    const parsedExperienceYears = Number(data.experience_years || data.experienceYears || 0)

    if (parsedSkills) {
      form.value.userSkills = parsedSkills
      resumeForm.value.userSkills = parsedSkills
      predictForm.value.skills = parsedSkills
    }
    form.value.education = data.education || '本科'
    form.value.experienceYears = parsedExperienceYears
    resumeForm.value.education = data.education || resumeForm.value.education
    resumeForm.value.experienceYears = parsedExperienceYears || resumeForm.value.experienceYears
    form.value.experience = data.experience || (parsedExperienceYears ? `${parsedExperienceYears} \u5e74` : form.value.experience)
    predictForm.value.education = data.education || predictForm.value.education
    predictForm.value.experience = data.experience || (parsedExperienceYears ? `${parsedExperienceYears} \u5e74` : predictForm.value.experience)

    if (data.target_city) {
      jobsForm.value.preferredCities = data.target_city
      predictForm.value.city = data.target_city
      resumeForm.value.targetCity = data.target_city
    }

    if (data.industry) {
      jobsForm.value.industry = data.industry
      predictForm.value.industry = data.industry
      resumeForm.value.industry = data.industry
    }

    if (parsedTargetJob) {
      resumeForm.value.targetJob = parsedTargetJob
      jobsForm.value.targetJobType = parsedTargetJob
    }
    if (data.current_job || data.currentJob) {
      resumeForm.value.currentJob = data.current_job || data.currentJob
    }

    if (parsedResumeText) {
      resumeForm.value.resumeText = parsedResumeText
    }
    syncResumeProfileToDownstream({
      targetJob: parsedTargetJob,
      targetCity: data.target_city,
      education: data.education,
      experienceYears: parsedExperienceYears,
      industry: data.industry,
      skills: listify(data.skills)
    })
    if (data.target_city) form.value.targetCity = data.target_city
    if (data.industry) form.value.industry = data.industry
    activeTab.value = 'resume'

    if (authStore.isLoggedIn) {
      let saveMessages = []
      try {
        const saved = await importAiProfileFile(authStore.token, file, overwriteSkills.value)
        importResult.value = saved
        saveMessages.push(`简历已自动入库，已保存技能数: ${saved.savedSkills || 0}。`)
        if (authStore.syncProfile) {
          await authStore.syncProfile()
        }
      } catch (persistError) {
        saveMessages.push(`技能画像入库失败：${normalizeError(persistError)}`)
      }

      try {
        await persistResumeProfile()
      } catch (persistError) {
        saveMessages.push(`简历内容保存失败：${normalizeError(persistError)}`)
      }

      if (saveMessages.length) {
        const errorMessages = saveMessages.filter((item) => item.includes('失败'))
        if (errorMessages.length) {
          infoMessage.value = `简历解析成功，但部分保存失败：${errorMessages.join('；')}`
        } else {
          importSuccess.value = saveMessages.join(' ')
        }
      }
    }

    event.target.value = ''
    success.value = '简历识别成功，已自动填充关键信息。'
  } catch (e) {
    parsedResumeData.value = null
    error.value = normalizeError(e)
  } finally {
    parsing.value = false
  }
}

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
      targetJobType: form.value.targetJobType,
      skills: splitInput(form.value.userSkills),
      preferredCities: splitInput(form.value.targetCity),
      education: form.value.education,
      experience: `${form.value.experienceYears}年`,
      experienceYears: Number(form.value.experienceYears) || undefined,
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
    success.value = isStudent.value ? '学生求职分析已生成。' : '简历诊断结果已生成。'
    await loadPersonalizedPlan()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleSkillGap() {
  if (!authStore.isLoggedIn) {
    skillsResult.value = null
    radarResult.value = null
    error.value = '登录状态已失效，请重新登录后再分析技能差距。'
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
    const [skillsRes, radarRes] = await Promise.allSettled([
      recommendSkills(authStore.token, payload),
      recommendSkillRadar(authStore.token, payload)
    ])

    if (skillsRes.status === 'fulfilled') {
      skillsResult.value = skillsRes.value
    } else {
      skillsResult.value = null
    }

    if (radarRes.status === 'fulfilled') {
      radarResult.value = radarRes.value
    } else {
      radarResult.value = null
    }

    if (!skillsResult.value && !radarResult.value) {
      throw new Error('技能差距接口未返回可用数据。')
    }

    if (!radarResult.value) {
      infoMessage.value = '技能差距分析已返回，雷达图数据暂不可用。'
    }
  } catch (e) {
    skillsResult.value = null
    radarResult.value = null
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleCareerPath() {
  if (!authStore.isLoggedIn) {
    pathResult.value = null
    error.value = '登录状态已失效，请重新登录后再生成职业路径。'
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
    if (!pathResult.value) {
      throw new Error('职业路径接口未返回可用数据。')
    }
  } catch (e) {
    pathResult.value = null
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleResumeReview() {
  if (!authStore.isLoggedIn) {
    resumeResult.value = null
    error.value = '登录状态已失效，请重新登录后再进行简历优化。'
    return
  }

  loading.value = true
  error.value = ''
  infoMessage.value = ''
  try {
    if (!resumeForm.value.targetJob) {
      throw new Error('请先填写目标职位。')
    }
    if (!resumeForm.value.resumeText) {
      throw new Error('请先粘贴简历正文或上传简历文件。')
    }
    resumeResult.value = await reviewResume(authStore.token, {
      targetJob: resumeForm.value.targetJob,
      resumeText: resumeForm.value.resumeText,
      userSkills: splitInput(resumeForm.value.userSkills),
      currentJob: resumeForm.value.currentJob,
      education: resumeForm.value.education,
      experienceYears: Number(resumeForm.value.experienceYears) || 0,
      targetCity: resumeForm.value.targetCity,
      industry: resumeForm.value.industry
    })
    syncResumeProfileToDownstream({
      targetJob: resumeForm.value.targetJob,
      targetCity: resumeForm.value.targetCity,
      education: resumeForm.value.education,
      experienceYears: Number(resumeForm.value.experienceYears) || 0,
      industry: resumeForm.value.industry,
      skills: [
        ...(resumeResult.value?.extractedProfile?.skills || []),
        ...(resumeResult.value?.matchedSkills || [])
      ]
    })
    await persistResumeProfile()
    success.value = '简历画像已更新，后续职位匹配和薪资参考会直接复用这份信息。'
  } catch (e) {
    resumeResult.value = null
    error.value = normalizeError(e)
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
  } catch (e) {
    predictResult.value = null
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    loadPersistedCareerProfile(),
    loadPersonalizedPlan(),
    loadJobRankerStatus()
  ])
})
</script>

<template>
  <div class="recommend-page page-animate">
    <section class="recommend-hero">
      <div class="recommend-hero-row">
        <div class="recommend-hero-copy">
          <h1 class="recommend-hero-title">智能推荐</h1>
        </div>

        <div class="recommend-hero-side">
          <div class="recommend-hero-meta">
            <div class="recommend-hero-meta-item">
              <span>当前模块</span>
              <strong>{{ activeTabMeta.label }}</strong>
            </div>
            <div class="recommend-hero-meta-item">
              <span>结果状态</span>
              <strong>{{ resultCountText }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="recommend-tabs" role="tablist">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="recommend-tab"
          :class="{ active: activeTab === tab.key }"
          role="tab"
          :aria-selected="activeTab === tab.key"
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" :size="14" />
          <span>{{ tab.label }}</span>
        </button>
      </div>
    </section>

    <div v-if="error" class="recommend-banner error">{{ error }}</div>
    <div v-if="infoMessage" class="recommend-banner info">{{ infoMessage }}</div>
    <div v-if="success" class="recommend-banner success">{{ success }}</div>
    <div v-if="importSuccess" class="recommend-banner success">{{ importSuccess }}</div>

    <section class="recommend-main">
      <article class="recommend-panel control-panel">
        <header class="recommend-panel-head">
          <div class="recommend-panel-copy">
            <h2 class="recommend-panel-title">
              <component :is="activeTabMeta.icon" :size="15" />
              {{ activeTabMeta.label }}
            </h2>
            <p class="recommend-panel-sub">{{ controlPanelDescription }}</p>
          </div>
          <span class="recommend-panel-badge">输入</span>
        </header>

        <div class="recommend-panel-body">
          <template v-if="activeTab === 'jobs'">
            <section class="insight-card">
              <h3>前置建议</h3>
              <p class="insight-card-copy">
                建议先在“简历优化”中上传简历并补齐画像字段，再生成职位匹配。这样目标岗位、技能、学历、经验和城市会自动同步，推荐会更稳。
              </p>
            </section>
            <div class="form-grid">
              <label class="field">
                <span class="field-label">目标岗位</span>
                <input v-model="jobsForm.targetJobType" class="recommend-input" placeholder="如 Java 后端工程师" />
              </label>
              <label class="field">
                <span class="field-label">技能</span>
                <input v-model="jobsForm.skills" class="recommend-input" placeholder="如 Java, Spring Boot" />
              </label>
              <label class="field">
                <span class="field-label">核心技能</span>
                <input v-model="jobsForm.coreSkills" class="recommend-input" placeholder="如 Java, Spring Boot, MySQL" />
              </label>
              <label class="field">
                <span class="field-label">期望城市</span>
                <input v-model="jobsForm.preferredCities" class="recommend-input" placeholder="如 上海, 北京" />
              </label>
              <label class="field">
                <span class="field-label">排除关键词</span>
                <input v-model="jobsForm.excludedKeywords" class="recommend-input" placeholder="如 销售, 顾问, 普工" />
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
                <span class="field-label">公司规模偏好</span>
                <input v-model="jobsForm.preferredCompanySizes" class="recommend-input" placeholder="如 100-499人, 1000人以上" />
              </label>
              <label class="field">
                <span class="field-label">融资阶段偏好</span>
                <input v-model="jobsForm.preferredFinanceStages" class="recommend-input" placeholder="如 A轮, 已上市" />
              </label>
              <label class="field">
                <span class="field-label">数量</span>
                <input v-model="jobsForm.limit" class="recommend-input" type="number" min="1" max="20" />
              </label>
            </div>
            <div class="insight-grid two-col compact">
              <section class="insight-card">
                <h3>输入质量诊断</h3>
                <div class="summary-grid compact">
                  <div class="summary-tile">
                    <span>画像分</span>
                    <strong>{{ jobsInputQuality.score }}</strong>
                  </div>
                  <div class="summary-tile">
                    <span>状态</span>
                    <strong>{{ jobsInputQuality.level }}</strong>
                  </div>
                </div>
                <p class="insight-card-copy">{{ jobsInputQuality.summary }}</p>
                <div v-if="jobsInputQuality.missing.length" class="detail-list dense">
                  <article v-for="item in jobsInputQuality.missing" :key="item" class="detail-item">
                    <p>{{ item }}</p>
                  </article>
                </div>
              </section>
              <section v-if="isAdmin" class="insight-card">
                <h3>排序模型状态</h3>
                <div class="summary-grid compact">
                  <div class="summary-tile">
                    <span>训练状态</span>
                    <strong>{{ rankerLoading ? '读取中' : (rankerStatus?.trained ? '已训练' : '未训练') }}</strong>
                  </div>
                  <div class="summary-tile">
                    <span>样本量</span>
                    <strong>{{ rankerStatus?.sample_count || 0 }}</strong>
                  </div>
                </div>
                <div class="chip-row">
                  <span>模型: {{ rankerStatus?.model_type || 'unknown' }}</span>
                  <span>来源: {{ rankerStatus?.source || 'unknown' }}</span>
                </div>
                <p class="insight-card-copy">
                  {{ rankerStatus?.message || '当前页可直接查看职位排序模型状态，并在数据更新后重新触发训练。' }}
                </p>
                <div class="panel-actions">
                  <GlowButton variant="primary" :loading="rankerTraining" @click="handleTrainJobRanker">
                    重新训练排序模型
                  </GlowButton>
                </div>
              </section>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">
                运行推荐
              </GlowButton>
            </div>
          </template>

          <template v-else-if="false && activeTab === 'skills'">
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
                分析差距
              </GlowButton>
            </div>
          </template>

          <template v-else-if="false && activeTab === 'path'">
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
                生成路径
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
                <span class="field-label">当前岗位</span>
                <input v-model="resumeForm.currentJob" class="recommend-input" placeholder="Backend Developer" />
              </label>
              <label class="field">
                <span class="field-label">核心技能</span>
                <input v-model="resumeForm.userSkills" class="recommend-input" placeholder="Java, Spring Boot" />
              </label>
              <label class="field">
                <span class="field-label">学历</span>
                <input v-model="resumeForm.education" class="recommend-input" placeholder="本科" />
              </label>
              <label class="field">
                <span class="field-label">经验年限</span>
                <input v-model="resumeForm.experienceYears" type="number" min="0" class="recommend-input" placeholder="2" />
              </label>
              <label class="field">
                <span class="field-label">目标城市</span>
                <input v-model="resumeForm.targetCity" class="recommend-input" placeholder="Beijing" />
              </label>
              <label class="field">
                <span class="field-label">目标行业</span>
                <input v-model="resumeForm.industry" class="recommend-input" placeholder="Internet" />
              </label>
              <label class="field field-full">
                <span class="field-label">简历正文</span>
                <textarea v-model="resumeForm.resumeText" class="recommend-input tall" placeholder="粘贴简历正文或项目经历" />
              </label>
              <div class="field field-full upload-grid">
                <section class="upload-card">
                  <div class="template-banner">
                    <div class="template-banner-copy">
                      <strong>先下载简历模板</strong>
                      <span>模板内容已同步为当前指定版本，建议先按模板字段填写后再上传解析。</span>
                    </div>
                <a class="template-download" href="/templates/resume.md" download>下载模板</a>
                  </div>
                  <div class="upload-card-head">
                    <span class="field-label">简历文件解析</span>
                    <span class="upload-card-status">{{ parsing ? '解析中' : (resumeUploadName ? '已选择' : '待上传') }}</span>
                  </div>
                  <p class="upload-card-copy">支持 PDF、DOCX、TXT、MD，当前重点识别目标岗位、当前岗位、城市、行业、学历、年限、技能、经历摘要、奖项和证书。</p>
                  <div class="detail-list dense">
                    <article class="detail-item">
                      <div class="detail-head">
                        <strong>技能分隔规则</strong>
                        <span>推荐格式</span>
                      </div>
                      <p>请优先使用逗号、中文逗号、顿号、斜杠或分号分隔多个技能，不建议只用空格分隔。</p>
                    </article>
                    <article class="detail-item">
                      <div class="detail-head">
                        <strong>动作与结果</strong>
                        <span>写法示例</span>
                      </div>
                      <p>动作写“具体做了什么”，结果写“带来了什么变化”，例如“负责社群触达与转化，活动报名率提升 28%”。</p>
                    </article>
                    <article class="detail-item">
                      <div class="detail-head">
                        <strong>奖项识别</strong>
                        <span>当前能力</span>
                      </div>
                      <p>当前支持识别奖项名称、级别和奖项等级，并给出基础含金量评分；这是规则评分，不是完整赛事知识图谱。</p>
                    </article>
                  </div>
                  <label class="upload-dropzone">
                    <Upload :size="18" />
                    <div class="upload-dropzone-copy">
                      <strong>{{ resumeUploadName || '选择简历文件' }}</strong>
                      <span>{{ parsing ? '正在解析文件，请稍候。' : '点击选择文件后自动开始解析。' }}</span>
                    </div>
                    <input type="file" class="upload-hidden-input" accept=".pdf,.docx,.txt,.md" @change="handleParseResume" />
                  </label>
                </section>
              </div>
              <label v-if="false" class="field">
                <span class="field-label">解析简历文件</span>
                <input type="file" class="recommend-input file" accept=".pdf,.docx,.txt,.md" @change="handleParseResume" />
              </label>
              <label v-if="false" class="field">
                <span class="field-label">导入个人资料</span>
                <input type="file" class="recommend-input file" @change="handleFileChange" />
              </label>
              <label v-if="false" class="field field-full">
                <span class="field-label">导入策略</span>
                <label class="checkbox-row">
                  <input v-model="overwriteSkills" type="checkbox" />
                  <span>导入个人资料时覆盖现有技能</span>
                </label>
              </label>
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">
                开始简历优化
              </GlowButton>
            </div>
          </template>

          <template v-else-if="false && activeTab === 'import'">
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
                导入文件
              </GlowButton>
            </div>
          </template>

          <template v-else>
            <section class="insight-card">
              <h3>前置建议</h3>
              <p class="insight-card-copy">
                薪资参考会直接复用简历页里的技能、学历、经验和目标城市。先完成简历优化，薪资区间会更接近真实投递场景。
              </p>
            </section>
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
                预测薪资
              </GlowButton>
            </div>
          </template>
        </div>
      </article>

      <article class="recommend-panel result-panel">
        <header class="recommend-panel-head">
          <div class="recommend-panel-copy">
            <h2 class="recommend-panel-title">结果</h2>
            <p class="recommend-panel-sub">以下为 API 实时返回结果。</p>
          </div>
          <span
            class="result-badge"
            :class="'is-live'"
          >
            实时结果
          </span>
        </header>

        <div class="recommend-panel-body">
          <template v-if="activeTab === 'jobs'">
            <section v-if="jobResultSummary || jobProfileSnapshot" class="insight-card">
              <h3>推荐摘要</h3>
              <div class="summary-grid compact">
                <div class="summary-tile">
                  <span>返回数量</span>
                  <strong>{{ jobResultSummary?.returnedCount ?? recommendedJobs.length }}</strong>
                </div>
                <div class="summary-tile">
                  <span>平均匹配</span>
                  <strong>{{ jobResultSummary?.avgScore ? `${Math.round(jobResultSummary.avgScore)}%` : jobsOverview?.avgScore || '--' }}</strong>
                </div>
              </div>
              <p v-if="jobResultSummary?.marketDiagnosis" class="insight-card-copy">{{ jobResultSummary.marketDiagnosis }}</p>
              <p v-if="jobResultSummary?.applicationStrategy" class="insight-card-copy">{{ jobResultSummary.applicationStrategy }}</p>
              <div v-if="jobProfileSnapshot" class="chip-row">
                <span v-if="jobProfileSnapshot.targetJobType">目标: {{ jobProfileSnapshot.targetJobType }}</span>
                <span v-for="item in (jobProfileSnapshot.coreSkills || []).slice(0, 4)" :key="item">{{ item }}</span>
                <span v-for="item in (jobProfileSnapshot.excludedKeywords || []).slice(0, 2)" :key="`excluded-${item}`">排除 {{ item }}</span>
              </div>
            </section>
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
                <div class="job-card-top">
                  <span class="job-rank">MATCH {{ String(index + 1).padStart(2, '0') }}</span>
                  <span v-if="getJobConfidence(job) !== null" class="job-score">
                    {{ getJobConfidence(job) }}%
                  </span>
                </div>
                <h3 class="job-title">{{ getJobTitle(job) }}</h3>
                <div class="job-meta">
                  <span><Building2 :size="13" /> {{ getJobCompany(job) }}</span>
                  <span><MapPin :size="13" /> {{ getJobCity(job) }}</span>
                </div>
                <p class="job-salary">{{ getJobSalary(job) }}</p>
                <p class="job-reason">{{ getJobReason(job) }}</p>
                <div v-if="getJobTags(job).length" class="job-tags">
                  <span v-for="tag in getJobTags(job)" :key="tag">{{ tag }}</span>
                </div>
                <div class="job-footer">
                  <span><Target :size="13" /> 匹配依据</span>
                  <span class="job-link">查看详情</span>
                </div>
              </article>
            </div>
            <div v-else class="empty-block">当前条件下没有找到岗位结果。</div>

            <div v-if="hasMoreJobs" class="list-more">
              <button type="button" class="more-btn" @click="showMoreJobs">
                查看更多（已显示 {{ visibleJobs.length }} / {{ recommendedJobs.length }}）
              </button>
            </div>
          </template>

          <template v-else-if="false && activeTab === 'skills'">
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

          <template v-else-if="false && activeTab === 'path'">
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
              <section v-if="resumeParseInsight" class="insight-card">
                <div class="section-head">
                  <h3>已识别字段</h3>
                  <span>上传后立即可见，不需要等完整诊断</span>
                </div>

                <div class="summary-grid resume-summary-grid">
                  <div class="summary-tile">
                    <span>识别字段数</span>
                    <strong>{{ resumeParseInsight.recognizedCount }}</strong>
                  </div>
                  <div class="summary-tile">
                    <span>解析模式</span>
                    <strong>{{ resumeParseInsight.parserMode }}</strong>
                  </div>
                  <div class="summary-tile">
                    <span>奖项条数</span>
                    <strong>{{ resumeParseInsight.awards.length }}</strong>
                  </div>
                  <div class="summary-tile">
                    <span>文本长度</span>
                    <strong>{{ resumeParseInsight.textLength || '--' }}</strong>
                  </div>
                </div>

                <div class="insight-grid two-col">
                  <section class="insight-card subtle-card">
                    <div class="section-head">
                      <h3>基础画像</h3>
                      <span>会自动回填到推荐与薪资模块</span>
                    </div>
                    <div class="detail-list">
                      <article
                        v-for="item in resumeParseInsight.fields"
                        :key="item.label"
                        class="detail-item"
                      >
                        <div class="detail-head">
                          <strong>{{ item.label }}</strong>
                          <span>已识别</span>
                        </div>
                        <p>{{ item.value }}</p>
                      </article>
                    </div>
                  </section>

                  <section class="insight-card subtle-card">
                    <div class="section-head">
                      <h3>缺失字段</h3>
                      <span>补齐后推荐会更稳</span>
                    </div>
                    <div v-if="resumeParseInsight.missingFieldLabels.length" class="chip-row">
                      <span v-for="item in resumeParseInsight.missingFieldLabels" :key="item">{{ item }}</span>
                    </div>
                    <p v-else class="micro-copy">关键基础字段已基本识别完整。</p>

                    <div class="section-head compact-head">
                      <h3>技能识别</h3>
                      <span>支持逗号、顿号、斜杠、分号分隔</span>
                    </div>
                    <div v-if="resumeParseInsight.skills.length" class="chip-row">
                      <span v-for="item in resumeParseInsight.skills" :key="item">{{ item }}</span>
                    </div>
                    <p v-else class="micro-copy">当前没有识别到稳定技能项，建议按模板字段填写。</p>

                    <div class="section-head compact-head">
                      <h3>证书识别</h3>
                      <span>按字段规则直接抽取</span>
                    </div>
                    <div v-if="resumeParseInsight.certificates.length" class="chip-row">
                      <span v-for="item in resumeParseInsight.certificates" :key="item">{{ item }}</span>
                    </div>
                    <p v-else class="micro-copy">当前没有识别到证书字段。</p>
                  </section>
                </div>

                <section v-if="resumeParseInsight.awards.length" class="insight-card subtle-card">
                  <div class="section-head">
                    <h3>奖项含金量判断</h3>
                    <span>当前按赛事级别、奖项等级、主办方权威度做规则评分</span>
                  </div>
                  <div class="detail-list">
                    <article
                      v-for="item in resumeParseInsight.awards"
                      :key="item.name"
                      class="detail-item"
                    >
                      <div class="detail-head">
                        <strong>{{ item.name }}</strong>
                        <span>{{ item.score }} 分</span>
                      </div>
                      <p>{{ item.level }} / {{ item.rank }} / {{ item.authority }}</p>
                      <p class="micro-copy">{{ item.explanation }}</p>
                    </article>
                  </div>
                </section>
              </section>

              <section class="insight-hero">
                <div>
                  <span class="kicker">简历摘要</span>
                  <h3>{{ resumeInsight.summary }}</h3>
                </div>
                <div class="score-block">
                  <span>优化评分</span>
                  <strong>{{ resumeInsight.score }}</strong>
                </div>
              </section>

              <div class="summary-grid resume-summary-grid">
                <div class="summary-tile">
                  <span>投递状态</span>
                  <strong>{{ resumeInsight.diagnosis.readiness.label }}</strong>
                </div>
                <div class="summary-tile">
                  <span>ATS 风险</span>
                  <strong>{{ resumeInsight.diagnosis.atsRisk.label }}</strong>
                </div>
                <div class="summary-tile">
                  <span>核心短板</span>
                  <strong>{{ resumeInsight.diagnosis.coreIssue }}</strong>
                </div>
              </div>

              <section class="insight-card">
                <div class="section-head">
                  <h3>评审维度</h3>
                  <span>把“能不能投”拆成具体信号</span>
                </div>
                <div class="resume-scorecard-grid">
                  <article
                    v-for="item in resumeInsight.scorecard"
                    :key="item.key"
                    class="resume-scorecard-item"
                    :class="`tone-${item.tone}`"
                  >
                    <div class="resume-scorecard-head">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.score }}</strong>
                    </div>
                    <div class="resume-scorecard-bar">
                      <i :style="{ width: `${item.score}%` }" />
                    </div>
                  </article>
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
                  <h3>需要调整</h3>
                  <ul class="plain-list">
                    <li v-for="item in resumeInsight.issues" :key="item">{{ item }}</li>
                  </ul>
                </section>
              </div>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <div class="section-head">
                    <h3>分段评审</h3>
                    <span>按简历模块逐段修</span>
                  </div>
                  <div class="detail-list">
                    <article v-for="item in resumeInsight.sectionAdvice" :key="item.title" class="detail-item">
                      <div class="detail-head">
                        <strong>{{ item.title }}</strong>
                        <span>{{ item.meta }}</span>
                      </div>
                      <p>{{ item.detail }}</p>
                    </article>
                  </div>
                </section>
                <section class="insight-card">
                  <div class="section-head">
                    <h3>优先修复项</h3>
                    <span>先改最影响转化的部分</span>
                  </div>
                  <div class="detail-list">
                    <article v-for="item in resumeInsight.priorityFixes" :key="item.title" class="detail-item">
                      <div class="detail-head">
                        <strong>{{ item.title }}</strong>
                        <span>{{ item.meta }}</span>
                      </div>
                      <p>{{ item.detail }}</p>
                    </article>
                  </div>
                </section>
              </div>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <div class="section-head">
                    <h3>命中关键词</h3>
                    <span>已经形成岗位相关信号</span>
                  </div>
                  <div class="chip-row">
                    <span v-for="item in resumeInsight.matchedKeywords" :key="item">{{ item }}</span>
                  </div>
                </section>
                <section class="insight-card">
                  <div class="section-head">
                    <h3>缺失关键词</h3>
                    <span>建议优先补到摘要或项目经历</span>
                  </div>
                  <div class="chip-row">
                    <span v-for="item in resumeInsight.missingKeywords" :key="item">{{ item }}</span>
                  </div>
                </section>
              </div>

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <div class="section-head">
                    <h3>优先动作</h3>
                    <span>先改最影响投递转化的部分</span>
                  </div>
                  <ul class="plain-list">
                    <li v-for="item in resumeInsight.actions" :key="item">{{ item }}</li>
                  </ul>
                </section>
                <section class="insight-card">
                  <div class="section-head">
                    <h3>改写提示</h3>
                    <span>直接指导项目描述重写</span>
                  </div>
                  <ul class="plain-list">
                    <li v-for="item in resumeInsight.rewriteHints" :key="item">{{ item }}</li>
                  </ul>
                </section>
              </div>

              <section class="insight-card">
                <div class="section-head">
                  <h3>岗位信号汇总</h3>
                  <span>用于和后续匹配、薪资页联动</span>
                </div>
                <div class="chip-row">
                  <span v-for="item in resumeInsight.keywords" :key="item">{{ item }}</span>
                </div>
                <div v-if="resumeInsight.profileTags.length" class="chip-row">
                  <span v-for="item in resumeInsight.profileTags" :key="item">{{ item }}</span>
                </div>
                <div v-if="resumeInsight.extractedSkills.length" class="chip-row">
                  <span v-for="item in resumeInsight.extractedSkills" :key="`profile-skill-${item}`">{{ item }}</span>
                </div>
              </section>

            </div>
            <div v-else class="empty-block">上传简历或粘贴正文后，这里会显示优化建议、关键词和个人画像结果。</div>
          </template>

          <template v-else-if="false && activeTab === 'import'">
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

              <section class="insight-card">
                <div class="section-head">
                  <h3>薪资维度</h3>
                  <span>区间不是拍脑袋给出的</span>
                </div>
                <div class="resume-scorecard-grid">
                  <article
                    v-for="item in salaryInsight.scorecard"
                    :key="item.label"
                    class="resume-scorecard-item"
                    :class="`tone-${item.score >= 80 ? 'strong' : (item.score >= 60 ? 'medium' : 'weak')}`"
                  >
                    <div class="resume-scorecard-head">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.score }}</strong>
                    </div>
                    <div class="resume-scorecard-bar">
                      <i :style="{ width: `${item.score}%` }" />
                    </div>
                  </article>
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

              <div class="insight-grid two-col">
                <section class="insight-card">
                  <div class="section-head">
                    <h3>已命中技能</h3>
                    <span>对薪资上沿有直接支撑</span>
                  </div>
                  <div class="chip-row">
                    <span v-for="item in salaryInsight.matchedSkills" :key="item">{{ item }}</span>
                  </div>
                </section>
                <section class="insight-card">
                  <div class="section-head">
                    <h3>待补技能</h3>
                    <span>补齐后更容易抬高上沿</span>
                  </div>
                  <div class="chip-row">
                    <span v-for="item in salaryInsight.missingSkills" :key="item">{{ item }}</span>
                  </div>
                </section>
              </div>
            </div>
            <div v-else class="empty-block">先输入预测条件，再查看薪资区间结果。</div>
          </template>
        </div>
      </article>
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
/* ----------------------------------------------------------
 * Page frame
 * -------------------------------------------------------- */
.recommend-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ----------------------------------------------------------
 * Hero (minimal header)
 * -------------------------------------------------------- */
.recommend-hero {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recommend-hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
}

.recommend-hero-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.recommend-hero-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.2vw, 30px);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
  color: var(--c-text-primary);
}

.recommend-hero-side {
  display: flex;
  align-items: center;
  gap: 12px;
}

.recommend-hero-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.recommend-hero-meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recommend-hero-meta-item span {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
}

.recommend-hero-meta-item strong {
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 600;
  color: var(--c-text-primary);
  line-height: 1.3;
}

/* ----------------------------------------------------------
 * Tabs
 * -------------------------------------------------------- */
.recommend-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface-hover);
}

.recommend-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.recommend-tab :deep(svg) {
  flex: none;
  color: inherit;
}

.recommend-tab:hover {
  background: rgba(0, 87, 194, 0.06);
  color: var(--c-accent-primary);
}

.recommend-tab.active {
  background: var(--c-surface-card-strong);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  box-shadow: var(--shadow-card-soft);
}

.recommend-tab:focus-visible {
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
  background: var(--c-surface-card-strong);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.5;
  box-shadow: var(--shadow-card-soft);
}

.recommend-banner.info {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-soft);
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
 * Two-column main layout
 * -------------------------------------------------------- */
.recommend-main {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  align-items: start;
  gap: 24px;
}

/* ----------------------------------------------------------
 * Panel — white card base (aligned with Collector / Console)
 * -------------------------------------------------------- */
.recommend-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--c-glass-panel-bg);
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  box-shadow: var(--shadow-card-soft);
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-out);
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
  background: var(--c-accent-primary-soft);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
  white-space: nowrap;
  flex-shrink: 0;
}

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

.result-badge.is-live {
  background: var(--c-accent-primary-soft);
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
  background: var(--c-surface-card-strong);
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
  box-shadow: 0 0 0 3px var(--c-accent-primary-soft);
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

.upload-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.upload-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background:
    linear-gradient(180deg, var(--c-accent-primary-soft), transparent 44%),
    var(--c-surface-card-strong);
}

.template-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(0, 87, 194, 0.14);
  border-radius: 12px;
  background: rgba(0, 87, 194, 0.05);
}

.template-banner-copy {
  display: grid;
  gap: 4px;
}

.template-banner-copy strong {
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
}

.template-banner-copy span {
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}

.template-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(0, 87, 194, 0.18);
  background: #ffffff;
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
}

.upload-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-card-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--c-accent-primary-soft);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  line-height: 1.3;
}

.upload-card-copy {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.upload-dropzone {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px dashed var(--c-border-glass-hover);
  border-radius: 12px;
  background: var(--c-surface-card-strong);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.upload-dropzone:hover {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-soft);
  box-shadow: var(--shadow-card-soft);
}

.upload-dropzone :deep(svg) {
  flex: none;
  color: var(--c-accent-primary);
}

.upload-dropzone-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.upload-dropzone-copy strong {
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--c-text-primary);
  word-break: break-all;
}

.upload-dropzone-copy span {
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.upload-hidden-input {
  display: none;
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

/* ----------------------------------------------------------
 * Summary tiles (top of result)
 * -------------------------------------------------------- */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.summary-grid.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.resume-summary-grid .summary-tile strong {
  font-size: 18px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-head span {
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
  text-align: right;
}

.resume-scorecard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
}

.resume-scorecard-item {
  display: grid;
  gap: 10px;
  padding: 13px 14px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-bg-surface-hover);
}

.resume-scorecard-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.resume-scorecard-head span {
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-secondary);
}

.resume-scorecard-head strong {
  font-family: var(--font-serif);
  font-size: 20px;
  line-height: 1;
  color: var(--c-text-primary);
}

.resume-scorecard-bar {
  overflow: hidden;
  height: 7px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
}

.resume-scorecard-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1d4ed8, #38bdf8);
}

.resume-scorecard-item.tone-medium .resume-scorecard-bar i {
  background: linear-gradient(90deg, #d97706, #fbbf24);
}

.resume-scorecard-item.tone-weak .resume-scorecard-bar i {
  background: linear-gradient(90deg, #dc2626, #fb7185);
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
  background: var(--c-surface-card-strong);
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
  background: var(--c-accent-primary-soft);
  box-shadow: var(--shadow-card-soft);
  outline: none;
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
  background: var(--c-accent-primary-soft);
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
  background: var(--c-surface-card-strong);
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
  background: var(--c-accent-primary-soft);
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
  background: var(--c-surface-card-strong);
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

.insight-grid.compact {
  gap: 10px;
}

.insight-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-surface-card-strong);
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

.insight-card-copy {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--c-text-secondary);
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

.detail-list.dense {
  gap: 8px;
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
  background: var(--c-surface-card-strong);
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
  background: var(--c-mobile-overlay);
  backdrop-filter: blur(10px);
}

.recommend-modal-card {
  position: relative;
  width: min(920px, 100%);
  max-height: min(84vh, 900px);
  overflow: auto;
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-raised);
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
  background: var(--c-surface-card-strong);
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
  background: var(--c-accent-primary-soft);
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

.subtle-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(247, 250, 255, 0.94));
}

.compact-head {
  margin-top: 18px;
}

.micro-copy {
  margin: 10px 0 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--c-text-muted);
}

/* ----------------------------------------------------------
 * Responsive
 * -------------------------------------------------------- */
@media (max-width: 1180px) {
  .recommend-main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .recommend-hero-row {
    flex-direction: column;
    gap: 12px;
  }

  .recommend-hero-meta {
    gap: 12px;
  }

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
}
</style>
