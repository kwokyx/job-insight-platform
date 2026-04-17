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
  reviewResume
} from '../api'
import { useAuthStore } from '../store/auth'
import { Bot, Calculator, Compass, FileSearch, FileUp, Radar, Sparkles } from 'lucide-vue-next'

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
  targetJob: '后端开发',
  userSkills: 'Java, Spring Boot, Redis',
  resumeText: '两年后端开发经验，负责接口设计、数据库建模和缓存优化。'
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
    resumeResult.value = await reviewResume(authStore.token, {
      targetJob: resumeForm.value.targetJob,
      userSkills: splitInput(resumeForm.value.userSkills),
      resumeText: resumeForm.value.resumeText
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
        <p class="eyebrow">Recommendation Studio</p>
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
          <div class="form-grid">
            <input v-model="jobsForm.skills" class="glass-input" placeholder="技能，例如 Java, Spring Boot, MySQL" />
            <input v-model="jobsForm.preferredCities" class="glass-input" placeholder="目标城市，例如 北京, 上海" />
            <input v-model="jobsForm.education" class="glass-input" placeholder="学历" />
            <input v-model="jobsForm.experience" class="glass-input" placeholder="经验年限" />
            <input v-model="jobsForm.industry" class="glass-input" placeholder="目标方向或行业" />
            <input v-model="jobsForm.limit" class="glass-input" type="number" min="1" max="20" placeholder="返回数量" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">开始匹配</GlowButton>

          <div v-if="jobsResult" class="result-shell">
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
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'skills'" title="技能差距" glowColor="secondary">
          <div class="form-grid">
            <input v-model="skillsForm.userSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="目标岗位" />
            <input v-model="skillsForm.city" class="glass-input" placeholder="城市" />
          </div>
          <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">开始分析</GlowButton>

          <div v-if="skillsResult" class="result-shell">
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
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'path'" title="成长路径" glowColor="teal">
          <div class="form-grid">
            <input v-model="pathForm.currentJob" class="glass-input" placeholder="当前岗位" />
            <input v-model="pathForm.targetJob" class="glass-input" placeholder="目标岗位" />
            <input v-model="pathForm.currentSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="pathForm.city" class="glass-input" placeholder="城市" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">生成路径</GlowButton>
          <div v-if="pathResult" class="result-shell">
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
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'resume'" title="简历诊断" glowColor="primary">
          <div class="form-grid">
            <input v-model="resumeForm.targetJob" class="glass-input" placeholder="目标岗位" />
            <input v-model="resumeForm.userSkills" class="glass-input" placeholder="技能" />
            <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="粘贴简历内容" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">开始诊断</GlowButton>
          <div v-if="resumeResult" class="result-shell">
            <div class="summary-grid">
              <div class="summary-box"><span>目标岗位</span><strong>{{ resumeResult.targetJob }}</strong></div>
              <div class="summary-box"><span>总分</span><strong>{{ resumeResult.overallScore }}</strong></div>
              <div class="summary-box"><span>缺口技能</span><strong>{{ listify(resumeResult.missingSkills).length }}</strong></div>
            </div>
            <div class="result-card compact">
              <h3>改写建议</h3>
              <ul><li v-for="item in listify(resumeResult.suggestions)" :key="item">{{ item }}</li></ul>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'import'" title="画像导入" glowColor="secondary">
          <div class="form-grid">
            <input type="file" class="glass-input" @change="handleFileChange" />
            <label class="checkbox-row"><input v-model="overwriteSkills" type="checkbox" />覆盖已有技能</label>
          </div>
          <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">导入文件</GlowButton>
          <div v-if="importResult" class="result-shell">
            <div class="summary-box"><span>已保存技能</span><strong>{{ importResult.savedSkills || 0 }}</strong></div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'salary'" title="薪资预测" glowColor="teal">
          <div class="form-grid">
            <input v-model="predictForm.city" class="glass-input" placeholder="城市" />
            <input v-model="predictForm.education" class="glass-input" placeholder="学历" />
            <input v-model="predictForm.experience" class="glass-input" placeholder="经验" />
            <input v-model="predictForm.skills" class="glass-input" placeholder="技能" />
            <input v-model="predictForm.industry" class="glass-input" placeholder="行业/方向" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="runPrediction">开始预测</GlowButton>
          <div v-if="predictResult" class="result-shell">
            <pre class="result-box">{{ JSON.stringify(predictResult, null, 2) }}</pre>
          </div>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.recommend-page { display: flex; flex-direction: column; gap: 24px; }
.hero, .login-banner, .error-banner, .success-banner { padding: 20px 24px; border-radius: 18px; }
.hero { display: grid; grid-template-columns: 1.6fr .9fr; gap: 20px; }
.eyebrow { margin: 0 0 8px; font-size: 12px; letter-spacing: .16em; text-transform: uppercase; color: var(--c-text-faint); }
.hero h1 { margin: 0; font-size: clamp(30px, 4vw, 44px); line-height: 1.08; }
.hero-text { margin: 12px 0 0; color: var(--c-text-secondary); line-height: 1.7; }
.hero-actions { display: grid; gap: 12px; align-content: start; }
.login-banner, .error-banner, .success-banner { display: flex; align-items: center; gap: 10px; }
.tabs { display: flex; flex-wrap: wrap; gap: 10px; }
.tab-btn, .glass-input, .summary-box, .result-card, .result-box {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
}
.tab-btn { display: inline-flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 999px; color: var(--c-text-primary); }
.tab-btn.active { background: rgba(30, 117, 255, 0.16); border-color: rgba(30, 117, 255, 0.42); }
.grid, .form-grid, .result-shell, .job-list, .timeline, .radar-list { display: grid; gap: 16px; }
.form-grid { margin-bottom: 16px; }
.glass-input { width: 100%; padding: 12px 14px; border-radius: 14px; color: var(--c-text-primary); }
.tall { min-height: 180px; resize: vertical; }
.summary-grid, .plan-grid, .dual-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.dual-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.summary-box, .result-card { border-radius: 18px; padding: 16px; }
.summary-box span { display: block; font-size: 12px; color: var(--c-text-faint); margin-bottom: 8px; text-transform: uppercase; }
.summary-box strong, .result-card h3 { color: var(--c-text-primary); }
.result-card h3, .detail-block h4 { margin: 0; }
.result-card p, .result-card li { color: var(--c-text-secondary); line-height: 1.7; }
.result-card ul { margin: 0; padding-left: 18px; display: grid; gap: 8px; }
.result-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.result-head h3 { margin: 0 0 6px; }
.meta-row, .chip-row, .action-row { display: flex; flex-wrap: wrap; gap: 8px; }
.meta-row span, .chip, .inline-action, .score-badge, .gap-pill { border-radius: 999px; }
.meta-row span { padding: 6px 10px; background: rgba(255,255,255,.03); color: var(--c-text-muted); font-size: 13px; }
.chip { padding: 7px 10px; background: rgba(255,255,255,.05); color: var(--c-text-secondary); font-size: 12px; font-weight: 600; }
.chip-good { background: rgba(16,185,129,.14); color: #9ae6b4; }
.chip-warn { background: rgba(245,158,11,.14); color: #fcd34d; }
.inline-action { padding: 10px 14px; border: 1px solid var(--c-border-glass); background: rgba(255,255,255,.04); color: var(--c-text-primary); font-size: 13px; font-weight: 700; }
.inline-action.primary { background: rgba(59,130,246,.18); border-color: rgba(59,130,246,.35); }
.score-badge, .gap-pill { padding: 8px 12px; font-size: 12px; font-weight: 700; }
.good { color: #86efac; background: rgba(34,197,94,.14); }
.mid { color: #fcd34d; background: rgba(245,158,11,.14); }
.weak { color: #fca5a5; background: rgba(239,68,68,.14); }
.neutral { color: var(--c-text-secondary); background: rgba(255,255,255,.06); }
.timeline-item, .radar-row { padding: 14px 16px; border-radius: 18px; border: 1px dashed var(--c-border-glass); background: rgba(255,255,255,.025); }
.radar-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.checkbox-row { display: inline-flex; align-items: center; gap: 10px; color: var(--c-text-secondary); }
.result-box { margin: 0; padding: 16px; border-radius: 16px; overflow: auto; white-space: pre-wrap; word-break: break-word; color: var(--c-text-secondary); }
@media (max-width: 960px) {
  .hero, .summary-grid, .plan-grid, .dual-grid { grid-template-columns: 1fr; }
  .result-head, .radar-row { flex-direction: column; align-items: flex-start; }
}
</style>
