<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchCareerProfile,
  fetchRecommendPlan,
  fetchSimilarJobs,
  importAiProfileFile,
  normalizeError,
  predictSalary,
  recommendCareerPath,
  recommendJobs,
  recommendSkillRadar,
  recommendSkills,
  scoreResume
} from '../api'
import { Bot, Calculator, Compass, FileSearch, FileUp, Radar, Sparkles } from 'lucide-vue-next'
import EmptyState from '../components/common/EmptyState.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const activeTab = ref('jobs')
const loading = ref(false)
const profileLoading = ref(false)
const importLoading = ref(false)
const planLoading = ref(false)
const similarLoading = ref(false)
const error = ref('')
const success = ref('')

const planResult = ref(null)
const similarJobsResult = ref(null)
const similarJobsTarget = ref(null)
const jobSortKey = ref('score')

const jobsForm = ref({
  skills: 'Java, Spring Boot, MySQL, Redis',
  preferredCities: '北京, 上海',
  education: '本科',
  experience: '1-3年',
  industry: '后端开发',
  limit: 8
})
const jobsResult = ref(null)

const skillsForm = ref({
  userSkills: 'Java, Spring Boot, MySQL',
  targetJobType: '后端开发',
  city: '北京'
})
const skillsResult = ref(null)
const radarResult = ref(null)

const pathForm = ref({
  currentJob: 'Java 开发',
  targetJob: '架构师',
  currentSkills: 'Java, Spring Boot, Redis, MySQL',
  city: '北京'
})
const pathResult = ref(null)

const resumeForm = ref({
  targetJobType: '后端开发',
  targetCity: '北京',
  education: '本科',
  experienceYears: 2,
  userSkills: 'Java, Spring Boot, Redis',
  industry: '互联网'
})
const resumeResult = ref(null)

const predictForm = ref({
  city: '北京',
  education: '本科',
  experience: '1-3年',
  skills: 'Java, Spring Boot, MySQL',
  industry: '互联网'
})
const predictResult = ref(null)

const uploadFile = ref(null)
const overwriteSkills = ref(true)
const importResult = ref(null)

const tabs = [
  { key: 'jobs', label: '岗位匹配', icon: Sparkles },
  { key: 'skills', label: '技能差距', icon: Radar },
  { key: 'path', label: '成长路径', icon: Compass },
  { key: 'resume', label: '简历诊断', icon: FileSearch },
  { key: 'import', label: '画像导入', icon: FileUp },
  { key: 'salary', label: '薪资预测', icon: Calculator }
]

const loginPrompt = computed(() => !authStore.isLoggedIn)
const sortedJobItems = computed(() => {
  const items = listify(jobsResult.value?.items).slice()
  switch (jobSortKey.value) {
    case 'salary':
      return items.sort((a, b) => Number(b.salaryMax || 0) - Number(a.salaryMax || 0))
    case 'city':
      return items.sort((a, b) => `${a.city || ''}`.localeCompare(`${b.city || ''}`, 'zh-CN'))
    default:
      return items.sort((a, b) => Number(b.score || 0) - Number(a.score || 0))
  }
})

function splitInput(value) {
  return `${value || ''}`
    .split(/[,\n，、]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function listify(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function stringifyList(value) {
  return listify(value).join(', ')
}

function formatScore(value) {
  const num = Number(value)
  return Number.isFinite(num) ? Math.round(num) : '--'
}

function formatMoney(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString() : value || '--'
}

function toneFromScore(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 'neutral'
  if (num >= 75) return 'good'
  if (num >= 55) return 'mid'
  return 'weak'
}

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
}

function searchJobs(keyword, city = '') {
  router.push({ path: '/jobs', query: { keyword: keyword || '', city: city || '' } })
}

async function loadPlan() {
  if (!authStore.token) return
  planLoading.value = true
  try {
    planResult.value = await fetchRecommendPlan(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    planLoading.value = false
  }
}

async function autofillFromProfile() {
  if (!authStore.token || profileLoading.value) return
  profileLoading.value = true
  error.value = ''
  success.value = ''
  try {
    const profileData = await fetchCareerProfile(authStore.token)
    const profile = profileData.profile || {}
    const skills = listify(profileData.skills)
    const city = profile.targetCityCode || ''
    const role = profile.profileSummary || ''
    const education = profile.educationLevel || ''

    if (skills.length) {
      const text = stringifyList(skills)
      jobsForm.value.skills = text
      skillsForm.value.userSkills = text
      pathForm.value.currentSkills = text
      resumeForm.value.userSkills = text
      predictForm.value.skills = text
    }
    if (city) {
      jobsForm.value.preferredCities = city
      skillsForm.value.city = city
      pathForm.value.city = city
      predictForm.value.city = city
    }
    if (role) {
      jobsForm.value.industry = role
      skillsForm.value.targetJobType = role
      pathForm.value.currentJob = role
      resumeForm.value.targetJob = role
    }
    if (education) {
      jobsForm.value.education = education
      predictForm.value.education = education
    }
    success.value = '已根据你的个人画像自动填充推荐表单。'
    await loadPlan()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    profileLoading.value = false
  }
}

async function importProfile() {
  if (!uploadFile.value || importLoading.value) return
  importLoading.value = true
  error.value = ''
  success.value = ''
  try {
    importResult.value = await importAiProfileFile(authStore.token, uploadFile.value, overwriteSkills.value)
    success.value = `画像导入完成，已保存技能 ${importResult.value.savedSkills || 0} 项。`
    await autofillFromProfile()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    importLoading.value = false
  }
}

async function handleJobsRecommend() {
  loading.value = true
  error.value = ''
  try {
    jobsResult.value = await recommendJobs(authStore.token, {
      skills: splitInput(jobsForm.value.skills),
      preferredCities: splitInput(jobsForm.value.preferredCities),
      education: jobsForm.value.education,
      experience: jobsForm.value.experience,
      industry: jobsForm.value.industry,
      limit: Number(jobsForm.value.limit)
    })
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function loadSimilarJobs(item) {
  if (!item?.id) return
  similarLoading.value = true
  similarJobsTarget.value = item
  try {
    similarJobsResult.value = await fetchSimilarJobs(authStore.token, item.id, 8)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    similarLoading.value = false
  }
}

async function handleSkillGap() {
  loading.value = true
  error.value = ''
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
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleCareerPath() {
  loading.value = true
  error.value = ''
  try {
    pathResult.value = await recommendCareerPath(authStore.token, {
      currentJob: pathForm.value.currentJob,
      targetJob: pathForm.value.targetJob,
      currentSkills: splitInput(pathForm.value.currentSkills),
      city: pathForm.value.city
    })
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function handleResumeReview() {
  loading.value = true
  error.value = ''
  try {
    resumeResult.value = await scoreResume({
      target_job_type: resumeForm.value.targetJobType,
      target_city: resumeForm.value.targetCity,
      education: resumeForm.value.education,
      experience_years: Number(resumeForm.value.experienceYears),
      industry: resumeForm.value.industry,
      skills: splitInput(resumeForm.value.userSkills)
    })
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

async function runPrediction() {
  loading.value = true
  error.value = ''
  try {
    predictResult.value = await predictSalary(authStore.token, {
      city: predictForm.value.city,
      education: predictForm.value.education,
      experience: predictForm.value.experience,
      skills: splitInput(predictForm.value.skills),
      industry: predictForm.value.industry
    })
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (authStore.isLoggedIn) loadPlan()
})
</script>

<template>
  <div class="recommend-page">
    <section class="hero glass-panel">
      <div>
        <p class="eyebrow">智能推荐工作室</p>
        <h1>把推荐结果变成可执行的求职动作</h1>
        <p class="hero-text">
          这里把岗位匹配、技能缺口、成长路径、简历诊断和画像导入串成一套完整动作，不只是给你一个分数。
        </p>
      </div>
      <div class="hero-actions">
        <GlowButton variant="primary" :loading="profileLoading" @click="autofillFromProfile">同步我的画像</GlowButton>
        <GlowButton variant="ghost" :loading="planLoading" @click="loadPlan">刷新个性化总览</GlowButton>
      </div>
    </section>

    <div v-if="loginPrompt" class="login-banner glass-panel">
      <Bot :size="18" />
      <span>请先登录，再使用推荐与画像导入功能。</span>
    </div>

    <div v-if="error" class="error-banner glass-panel">{{ error }}</div>
    <div v-if="success" class="success-banner glass-panel">{{ success }}</div>

    <template v-if="!loginPrompt">
      <PremiumCard title="个性化推荐总览" glowColor="primary">
        <div v-if="planResult" class="plan-grid">
          <div class="summary-box">
            <span>总览结论</span>
            <strong>{{ planResult.planSummary?.headline || '暂无' }}</strong>
          </div>
          <div class="summary-box">
            <span>优先技能</span>
            <strong>{{ listify(planResult.planSummary?.prioritySkills).join(' / ') || '--' }}</strong>
          </div>
          <div class="summary-box">
            <span>下一步</span>
            <strong>{{ planResult.planSummary?.nextStep || '--' }}</strong>
          </div>
        </div>
        <div v-if="planResult" class="dual-grid">
          <div class="result-card compact">
            <h3>推荐岗位</h3>
            <ul>
              <li v-for="item in listify(planResult.recommendedJobs).slice(0, 5)" :key="item.id || item.title">
                {{ item.title }}{{ item.city ? ` / ${item.city}` : '' }}
              </li>
            </ul>
          </div>
          <div class="result-card compact">
            <h3>缺口技能</h3>
            <div class="chip-row">
              <span v-for="item in listify(planResult.skillGap?.prioritySkills)" :key="item" class="chip chip-warn">{{ item }}</span>
            </div>
          </div>
        </div>
      </PremiumCard>

      <div class="tabs">
        <button v-for="tab in tabs" :key="tab.key" class="tab-btn" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <component :is="tab.icon" :size="14" />
          {{ tab.label }}
        </button>
      </div>

      <section class="grid">
        <PremiumCard v-if="activeTab === 'jobs'" title="岗位匹配" glowColor="primary">
          <div class="recommend-layout">
            <div class="recommend-sidebar">
              <div class="form-grid">
                <input v-model="jobsForm.skills" class="glass-input" placeholder="技能，例如 Java, Spring Boot, MySQL" />
                <input v-model="jobsForm.preferredCities" class="glass-input" placeholder="目标城市，例如 北京, 上海" />
                <input v-model="jobsForm.education" class="glass-input" placeholder="学历" />
                <input v-model="jobsForm.experience" class="glass-input" placeholder="经验年限" />
                <input v-model="jobsForm.industry" class="glass-input" placeholder="目标方向或行业" />
                <input v-model="jobsForm.limit" class="glass-input" type="number" min="1" max="20" placeholder="返回数量" />
              </div>
              <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">开始匹配</GlowButton>
            </div>
            
            <div class="recommend-content">
              <div v-if="loading && activeTab === 'jobs'" class="result-shell">
                <SkeletonCard type="card" :lines="3" v-for="i in 3" :key="i" />
              </div>
              <div v-else-if="jobsResult" class="result-shell">
                <div class="summary-grid">
                  <div class="summary-box"><span>匹配岗位数</span><strong>{{ jobsResult.summary?.returnedCount ?? listify(jobsResult.items).length }}</strong></div>
                  <div class="summary-box"><span>平均匹配度</span><strong>{{ formatScore(jobsResult.summary?.avgScore) }}</strong></div>
                  <div class="summary-box"><span>重点城市</span><strong>{{ listify(jobsResult.summary?.topCities).join(' / ') || '--' }}</strong></div>
                </div>

                <div class="toolbar-row">
                  <span class="toolbar-label">排序方式</span>
                  <div class="chip-row">
                    <button class="inline-action" :class="{ primary: jobSortKey === 'score' }" @click="jobSortKey = 'score'">按匹配度</button>
                    <button class="inline-action" :class="{ primary: jobSortKey === 'salary' }" @click="jobSortKey = 'salary'">按薪资</button>
                    <button class="inline-action" :class="{ primary: jobSortKey === 'city' }" @click="jobSortKey = 'city'">按城市</button>
                  </div>
                </div>

                <div class="job-list">
                  <article v-for="item in sortedJobItems" :key="item.id" class="result-card">
                    <div class="result-head">
                      <div>
                        <h3>{{ item.title }}</h3>
                        <p>{{ item.companyName || '--' }}{{ item.city ? ` / ${item.city}` : '' }}</p>
                      </div>
                      <div class="score-badge" :class="toneFromScore(item.score)">{{ item.fitLabel || '匹配' }} {{ formatScore(item.score) }}</div>
                    </div>
                    <div class="meta-row">
                      <span>{{ item.industryName || '--' }}</span>
                      <span>{{ item.education || '--' }}</span>
                      <span>{{ item.experience || '--' }}</span>
                      <span>{{ item.salaryText || `${formatMoney(item.salaryMin)} - ${formatMoney(item.salaryMax)}` }}</span>
                    </div>
                    <div class="chip-row">
                      <span v-for="skill in listify(item.matchedSkills)" :key="skill" class="chip chip-good">{{ skill }}</span>
                    </div>
                    <div class="detail-block" v-if="listify(item.whyMatched).length">
                      <h4>匹配原因</h4>
                      <ul><li v-for="reason in listify(item.whyMatched)" :key="reason">{{ reason }}</li></ul>
                    </div>
                    <div class="detail-block" v-if="listify(item.nextActions).length">
                      <h4>建议动作</h4>
                      <ul><li v-for="action in listify(item.nextActions)" :key="action">{{ action }}</li></ul>
                    </div>
                    <div class="action-row">
                      <button class="inline-action primary" @click="searchJobs(item.title, item.city)">查看同类岗位</button>
                      <button class="inline-action" :disabled="similarLoading" @click="loadSimilarJobs(item)">相似岗位</button>
                    </div>
                  </article>
                </div>

                <div v-if="similarJobsResult && similarJobsTarget" class="result-card">
                  <h3>相似岗位：{{ similarJobsTarget.title }}</h3>
                  <ul>
                    <li v-for="item in listify(similarJobsResult.recommendations)" :key="`${item.id}-${item.title}`">
                      {{ item.title }}{{ item.city ? ` / ${item.city}` : '' }}{{ item.matchedSkills ? ` / ${item.matchedSkills}` : '' }}
                    </li>
                  </ul>
                </div>
              </div>
              <div v-else-if="!loading" class="result-shell">
                 <EmptyState icon="sparkles" title="等待匹配" description="填写左侧表单以获取 AI 岗位推荐" />
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'skills'" title="技能差距" glowColor="secondary">
          <div class="recommend-layout">
            <div class="recommend-sidebar">
              <div class="form-grid">
                <input v-model="skillsForm.userSkills" class="glass-input" placeholder="当前技能" />
                <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="目标岗位" />
                <input v-model="skillsForm.city" class="glass-input" placeholder="城市" />
              </div>
              <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">开始分析</GlowButton>
            </div>
            
            <div class="recommend-content">
              <div v-if="loading && activeTab === 'skills'" class="result-shell">
                <SkeletonCard type="chart" />
                <SkeletonCard type="list" :lines="4" />
              </div>
              <div v-else-if="skillsResult" class="result-shell">
                <div class="summary-grid">
                  <div class="summary-box"><span>目标岗位</span><strong>{{ skillsResult.targetJobType || '--' }}</strong></div>
                  <div class="summary-box"><span>匹配率</span><strong>{{ skillsResult.matchRate || '--' }}</strong></div>
                  <div class="summary-box"><span>优先补齐</span><strong>{{ listify(skillsResult.prioritySkills).length }}</strong></div>
                </div>
                <div class="chip-row">
                  <span v-for="item in listify(skillsResult.prioritySkills)" :key="item" class="chip chip-warn">{{ item }}</span>
                </div>
                <div class="result-card compact" v-if="listify(skillsResult.learningPath).length">
                  <h3>学习项目建议</h3>
                  <div class="timeline">
                    <div v-for="item in listify(skillsResult.learningPath)" :key="item.stage" class="timeline-item">
                      <strong>{{ item.stage }}</strong>
                      <span>{{ item.focusSkill }}</span>
                      <p>{{ item.goal }}</p>
                    </div>
                  </div>
                </div>
                <div class="result-card compact" v-if="radarResult">
                  <h3>技能雷达</h3>
                  <div class="radar-list">
                    <div v-for="item in listify(radarResult.skills)" :key="item.skill" class="radar-row">
                      <div>
                        <strong>{{ item.skill }}</strong>
                        <p>当前 {{ item.currentScore }} / 目标 {{ item.targetScore }}</p>
                      </div>
                      <div class="gap-pill" :class="toneFromScore(100 - item.gapScore)">Gap {{ item.gapScore }}</div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else-if="!loading" class="result-shell">
                 <EmptyState icon="radar" title="等待诊断" description="填写左侧表单以获取技能差距雷达图" />
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'path'" title="成长路径" glowColor="teal">
          <div class="recommend-layout">
            <div class="recommend-sidebar">
              <div class="form-grid">
                <input v-model="pathForm.currentJob" class="glass-input" placeholder="当前岗位" @keydown.enter="handleCareerPath" />
                <input v-model="pathForm.targetJob" class="glass-input" placeholder="目标岗位" @keydown.enter="handleCareerPath" />
                <input v-model="pathForm.currentSkills" class="glass-input" placeholder="当前技能" @keydown.enter="handleCareerPath" />
                <input v-model="pathForm.city" class="glass-input" placeholder="城市" @keydown.enter="handleCareerPath" />
              </div>
              <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">生成路径</GlowButton>
            </div>
            
            <div class="recommend-content">
              <div v-if="loading && activeTab === 'path'" class="result-shell">
                <SkeletonCard type="stat" :lines="2" />
                <SkeletonCard type="list" :lines="4" v-for="i in 3" :key="i" />
              </div>
              <div v-else-if="pathResult" class="result-shell">
                <div class="timeline">
                  <div v-for="step in listify(pathResult.steps)" :key="`${step.fromRole}-${step.toRole}`" class="timeline-item">
                    <strong>{{ step.fromRole }} → {{ step.toRole }}</strong>
                    <span>{{ step.transitionType }} / {{ step.avgYears }} 年</span>
                    <div class="chip-row">
                      <span v-for="skill in listify(step.requiredSkills)" :key="skill" class="chip">{{ skill }}</span>
                    </div>
                  </div>
                </div>
                <div class="dual-grid">
                  <div class="result-card compact" v-if="listify(pathResult.milestones).length">
                    <h3>季度行动清单</h3>
                    <ul>
                      <li v-for="item in listify(pathResult.milestones)" :key="item.quarter">
                        {{ item.quarter }}：{{ item.goal }}
                      </li>
                    </ul>
                  </div>
                  <div class="result-card compact" v-if="listify(pathResult.recommendedProjects).length">
                    <h3>推荐项目</h3>
                    <ul>
                      <li v-for="item in listify(pathResult.recommendedProjects)" :key="item.name">
                        {{ item.name }}：{{ item.goal }}
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
              <div v-else-if="!loading" class="result-shell">
                 <EmptyState icon="compass" title="等待规划" description="填写左侧表单以获取 AI 职场成长路径规划" />
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'resume'" title="简历诊断" glowColor="primary">
          <div class="recommend-layout">
            <div class="recommend-sidebar">
              <div class="form-grid">
                <input v-model="resumeForm.targetJobType" class="glass-input" placeholder="目标岗位类型 (如: 后端开发)" @keydown.enter="handleResumeReview" />
                <input v-model="resumeForm.targetCity" class="glass-input" placeholder="目标城市" @keydown.enter="handleResumeReview" />
                <input v-model="resumeForm.education" class="glass-input" placeholder="最高学历 (如: 本科)" @keydown.enter="handleResumeReview" />
                <input v-model="resumeForm.experienceYears" class="glass-input" type="number" placeholder="工作年限" @keydown.enter="handleResumeReview" />
                <input v-model="resumeForm.industry" class="glass-input" placeholder="目标行业" @keydown.enter="handleResumeReview" />
                <input v-model="resumeForm.userSkills" class="glass-input" placeholder="当前技能，逗号分隔" @keydown.enter="handleResumeReview" />
              </div>
              <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">多维评分诊断</GlowButton>
            </div>
            
            <div class="recommend-content">
              <div v-if="loading && activeTab === 'resume'" class="result-shell">
                <SkeletonCard type="stat" :lines="2" />
                <SkeletonCard type="list" :lines="3" />
              </div>
              <div v-else-if="resumeResult" class="result-shell">
                <div class="summary-grid">
                  <div class="summary-box"><span>目标岗位</span><strong>{{ resumeResult.job_title || resumeForm.targetJobType }}</strong></div>
                  <div class="summary-box"><span>综合得分 ({{ resumeResult.grade }})</span><strong>{{ resumeResult.overall_score }}</strong></div>
                  <div class="summary-box"><span>缺失关键技能</span><strong>{{ listify(resumeResult.missing_skills).length }}</strong></div>
                </div>
                
                <div class="result-card compact">
                  <p>{{ resumeResult.summary }}</p>
                </div>
                
                <div class="result-card compact" v-if="resumeResult.dimension_scores">
                  <h3>维度得分</h3>
                  <div class="radar-list">
                    <div class="radar-row">
                      <div><strong>技能匹配 (35%)</strong></div>
                      <div class="gap-pill" :class="toneFromScore(resumeResult.dimension_scores.skill)">{{ resumeResult.dimension_scores.skill }}</div>
                    </div>
                    <div class="radar-row">
                      <div><strong>经验匹配 (20%)</strong></div>
                      <div class="gap-pill" :class="toneFromScore(resumeResult.dimension_scores.experience)">{{ resumeResult.dimension_scores.experience }}</div>
                    </div>
                    <div class="radar-row">
                      <div><strong>学历匹配 (20%)</strong></div>
                      <div class="gap-pill" :class="toneFromScore(resumeResult.dimension_scores.education)">{{ resumeResult.dimension_scores.education }}</div>
                    </div>
                    <div class="radar-row">
                      <div><strong>城市匹配 (15%)</strong></div>
                      <div class="gap-pill" :class="toneFromScore(resumeResult.dimension_scores.city)">{{ resumeResult.dimension_scores.city }}</div>
                    </div>
                  </div>
                </div>

                <div class="result-card compact" v-if="resumeResult.improvement_tips && resumeResult.improvement_tips.length">
                  <h3>改进建议</h3>
                  <div class="timeline">
                    <div v-for="tip in resumeResult.improvement_tips" :key="tip.tip" class="timeline-item">
                      <strong>{{ tip.dimension.toUpperCase() }}</strong>
                      <p>{{ tip.tip }}</p>
                      <span>预计可提升匹配度: +{{ tip.expected_gain }}%</span>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else-if="!loading" class="result-shell">
                 <EmptyState icon="file" title="等待诊断" description="填写左侧表单以获取 AI 简历匹配度多维评分" />
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'import'" title="画像导入" glowColor="secondary">
          <div class="form-grid">
            <input type="file" class="glass-input" @change="handleFileChange" accept=".txt,.docx,.pdf" />
            <label class="checkbox-row"><input v-model="overwriteSkills" type="checkbox" />覆盖已有技能</label>
          </div>
          <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">导入文件</GlowButton>
          <div v-if="importResult" class="result-shell">
            <div class="summary-box"><span>已保存技能</span><strong>{{ importResult.savedSkills || 0 }}</strong></div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'salary'" title="薪资预测" glowColor="teal">
          <div class="recommend-layout">
            <div class="recommend-sidebar">
              <div class="form-grid">
                <input v-model="predictForm.city" class="glass-input" placeholder="城市" @keydown.enter="runPrediction" />
                <input v-model="predictForm.education" class="glass-input" placeholder="学历" @keydown.enter="runPrediction" />
                <input v-model="predictForm.experience" class="glass-input" placeholder="经验" @keydown.enter="runPrediction" />
                <input v-model="predictForm.skills" class="glass-input" placeholder="技能" @keydown.enter="runPrediction" />
                <input v-model="predictForm.industry" class="glass-input" placeholder="行业/方向" @keydown.enter="runPrediction" />
              </div>
              <GlowButton variant="primary" :loading="loading" @click="runPrediction">开始预测</GlowButton>
            </div>
            
            <div class="recommend-content">
              <div v-if="loading && activeTab === 'salary'" class="result-shell">
                <SkeletonCard type="stat" :lines="2" />
                <SkeletonCard type="list" :lines="3" />
              </div>
              <div v-else-if="predictResult" class="result-shell">
                <div class="summary-grid">
                  <div class="summary-box">
                    <span>预测中位数</span>
                    <strong>{{ formatMoney(predictResult.salary_median) }} /月</strong>
                  </div>
                  <div class="summary-box">
                    <span>预测区间 (P25 - P75)</span>
                    <strong v-if="predictResult.confidence_interval">{{ formatMoney(predictResult.confidence_interval.p25) }} - {{ formatMoney(predictResult.confidence_interval.p75) }}</strong>
                    <strong v-else>--</strong>
                  </div>
                  <div class="summary-box">
                    <span>模型 MAE (误差)</span>
                    <strong>±{{ predictResult.model_metrics?.cv_mae_mean ? predictResult.model_metrics.cv_mae_mean.toFixed(2) : '--' }}K</strong>
                  </div>
                </div>
                
                <div class="dual-grid" v-if="predictResult.feature_importance && predictResult.feature_importance.length">
                  <div class="result-card compact">
                    <h3>驱动因素 (特征重要度)</h3>
                    <div class="radar-list">
                      <div v-for="(feat, index) in predictResult.feature_importance.slice(0, 5)" :key="feat.feature" class="radar-row">
                        <div>
                          <strong>{{ feat.feature }}</strong>
                        </div>
                        <div class="gap-pill neutral">{{ (feat.importance * 100).toFixed(1) }}%</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else-if="!loading" class="result-shell">
                 <EmptyState icon="calculator" title="等待预测" description="填写左侧表单以获取 AI 薪资预测报告" />
              </div>
            </div>
          </div>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.recommend-page { display: flex; flex-direction: column; gap: 24px; }
.hero, .login-banner, .error-banner, .success-banner { padding: 24px 30px; border-radius: 24px; background: rgba(255, 255, 255, 0.5); backdrop-filter: blur(16px); border: 1px solid var(--c-border-glass); box-shadow: 0 4px 24px rgba(0, 0, 0, 0.04); }
.hero { display: grid; grid-template-columns: 1.6fr .9fr; gap: 24px; align-items: center; background: linear-gradient(135deg, rgba(255, 255, 255, 0.8), rgba(255, 255, 255, 0.3)); position: relative; overflow: hidden; }
.hero::before { content: ''; position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(56, 189, 248, 0.15) 0%, transparent 60%); z-index: -1; animation: rotate 20s linear infinite; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.eyebrow { margin: 0 0 10px; font-size: 13px; font-weight: 800; letter-spacing: 0.2em; text-transform: uppercase; background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-teal)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.hero h1 { margin: 0; font-size: clamp(32px, 4.5vw, 48px); line-height: 1.1; font-weight: 800; letter-spacing: -0.02em; }
.hero-text { margin: 16px 0 0; color: var(--c-text-secondary); line-height: 1.8; font-size: 15px; max-width: 90%; }
.hero-actions { display: flex; flex-direction: column; gap: 14px; justify-self: end; width: 100%; max-width: 240px; }
.login-banner, .error-banner, .success-banner { display: flex; align-items: center; gap: 12px; font-weight: 500; }
.login-banner { background: rgba(56, 189, 248, 0.08); border-color: rgba(56, 189, 248, 0.2); color: var(--c-accent-primary); }
.error-banner { background: rgba(239, 68, 68, 0.08); border-color: rgba(239, 68, 68, 0.2); color: #b91c1c; }
.success-banner { background: rgba(16, 185, 129, 0.08); border-color: rgba(16, 185, 129, 0.2); color: #047857; }
.tabs { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 8px; }
.tab-btn { display: inline-flex; align-items: center; gap: 8px; padding: 12px 20px; border-radius: 999px; background: rgba(255, 255, 255, 0.4); border: 1px solid var(--c-border-glass); color: var(--c-text-secondary); font-weight: 600; font-size: 14px; backdrop-filter: blur(8px); transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); cursor: pointer; }
.tab-btn:hover { background: rgba(255, 255, 255, 0.8); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); color: var(--c-text-primary); }
.tab-btn.active { background: linear-gradient(135deg, rgba(56, 189, 248, 0.15), rgba(168, 85, 247, 0.1)); border-color: rgba(56, 189, 248, 0.4); color: var(--c-accent-primary); box-shadow: 0 4px 16px rgba(56, 189, 248, 0.15); transform: translateY(-2px); }
.grid, .form-grid, .result-shell, .job-list, .timeline, .radar-list { display: grid; gap: 20px; }
.form-grid { margin-bottom: 20px; }
.glass-input, .summary-box, .result-card, .result-box { border: 1px solid var(--c-border-glass); background: rgba(255, 255, 255, 0.6); backdrop-filter: blur(12px); border-radius: 18px; transition: all 0.3s ease; }
.glass-input { width: 100%; padding: 14px 18px; color: var(--c-text-primary); font-size: 14px; font-weight: 500; }
.glass-input:focus { outline: none; border-color: rgba(56, 189, 248, 0.5); background: rgba(255, 255, 255, 0.9); box-shadow: 0 0 0 4px rgba(56, 189, 248, 0.1); }
.tall { min-height: 180px; resize: vertical; }
.summary-grid, .plan-grid, .dual-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.dual-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.summary-box { padding: 20px; display: flex; flex-direction: column; justify-content: center; position: relative; overflow: hidden; }
.summary-box::after { content: ''; position: absolute; top: 0; right: 0; width: 60px; height: 100%; background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4)); transform: skewX(-20deg) translateX(150%); transition: 0.5s; }
.summary-box:hover::after { transform: skewX(-20deg) translateX(-150%); }
.summary-box span { display: block; font-size: 12px; color: var(--c-text-faint); margin-bottom: 8px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; }
.summary-box strong { font-size: 20px; color: var(--c-text-primary); font-weight: 800; }
.result-card { padding: 24px; display: flex; flex-direction: column; gap: 16px; }
.result-card:hover { transform: translateY(-2px); box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06); border-color: rgba(255, 255, 255, 0.8); }
.result-card h3 { margin: 0; font-size: 18px; font-weight: 700; color: var(--c-text-primary); }
.result-card p, .result-card li { color: var(--c-text-secondary); line-height: 1.7; font-size: 14px; }
.result-card ul { margin: 0; padding-left: 20px; display: grid; gap: 10px; }
.result-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.result-head h3 { margin: 0 0 8px; font-size: 20px; }
.result-head p { margin: 0; font-size: 14px; opacity: 0.8; }
.meta-row, .chip-row, .action-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.meta-row span { padding: 6px 12px; background: rgba(15, 23, 42, 0.04); color: var(--c-text-secondary); font-size: 13px; font-weight: 600; border-radius: 8px; }
.chip { padding: 6px 14px; background: rgba(255,255,255,0.8); color: var(--c-text-secondary); font-size: 13px; font-weight: 600; border-radius: 999px; border: 1px solid var(--c-border-glass); box-shadow: 0 2px 4px rgba(0,0,0,0.02); }
.chip-good { background: rgba(16, 185, 129, 0.1); color: #059669; border-color: rgba(16, 185, 129, 0.2); }
.chip-warn { background: rgba(245, 158, 11, 0.1); color: #d97706; border-color: rgba(245, 158, 11, 0.2); }
.inline-action { padding: 10px 18px; border: 1px solid var(--c-border-glass); background: rgba(255, 255, 255, 0.8); color: var(--c-text-primary); font-size: 13px; font-weight: 700; border-radius: 12px; transition: all 0.2s; cursor: pointer; }
.inline-action:hover { background: var(--c-bg-surface); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.inline-action.primary { background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-teal)); color: white; border: none; box-shadow: 0 4px 12px rgba(56, 189, 248, 0.3); }
.inline-action.primary:hover { opacity: 0.9; box-shadow: 0 6px 16px rgba(56, 189, 248, 0.4); }
.score-badge, .gap-pill { padding: 8px 14px; font-size: 13px; font-weight: 800; border-radius: 12px; }
.good { color: #059669; background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); }
.mid { color: #d97706; background: rgba(245, 158, 11, 0.15); border: 1px solid rgba(245, 158, 11, 0.3); }
.weak { color: #dc2626; background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3); }
.neutral { color: var(--c-text-secondary); background: rgba(15, 23, 42, 0.06); border: 1px solid var(--c-border-glass); }
.timeline-item, .radar-row { padding: 18px 20px; border-radius: 16px; background: rgba(255, 255, 255, 0.5); border: 1px solid var(--c-border-glass); display: flex; flex-direction: column; gap: 8px; transition: all 0.3s; }
.timeline-item:hover, .radar-row:hover { background: rgba(255, 255, 255, 0.9); transform: translateX(4px); border-color: rgba(56, 189, 248, 0.3); }
.radar-row { flex-direction: row; justify-content: space-between; align-items: center; }
.radar-row strong { font-size: 16px; color: var(--c-text-primary); }
.radar-row p { margin: 4px 0 0; font-size: 13px; color: var(--c-text-faint); }
.checkbox-row { display: inline-flex; align-items: center; gap: 10px; color: var(--c-text-secondary); font-weight: 500; cursor: pointer; }
.checkbox-row input { width: 18px; height: 18px; accent-color: var(--c-accent-primary); cursor: pointer; }
.result-box { margin: 0; padding: 20px; border-radius: 16px; overflow: auto; white-space: pre-wrap; word-break: break-word; color: var(--c-text-secondary); font-family: ui-monospace, monospace; font-size: 13px; background: rgba(15, 23, 42, 0.02); }
.detail-block h4 { margin: 0 0 10px; font-size: 15px; color: var(--c-text-primary); font-weight: 700; }
.toolbar-row { display: flex; align-items: center; gap: 16px; margin: 10px 0; padding: 12px 16px; background: rgba(255, 255, 255, 0.4); border-radius: 14px; border: 1px solid var(--c-border-glass); }
.toolbar-label { font-size: 13px; font-weight: 600; color: var(--c-text-faint); text-transform: uppercase; letter-spacing: 0.05em; }
.recommend-layout { display: grid; grid-template-columns: 300px 1fr; gap: 24px; align-items: start; }
.recommend-sidebar { display: flex; flex-direction: column; gap: 20px; position: sticky; top: 20px; }
.recommend-content { min-width: 0; }
@media (max-width: 960px) {
  .recommend-layout { grid-template-columns: 1fr; }
  .recommend-sidebar { position: static; }
  .hero, .summary-grid, .plan-grid, .dual-grid { grid-template-columns: 1fr; }
  .result-head, .radar-row { flex-direction: column; align-items: flex-start; gap: 12px; }
  .hero-actions { justify-self: stretch; max-width: none; }
  .tab-btn { flex: 1; justify-content: center; }
}
</style>
