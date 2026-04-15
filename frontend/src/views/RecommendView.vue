<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchCareerProfile,
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
const error = ref('')
const importLoading = ref(false)
const importSuccess = ref('')
const profileLoading = ref(false)

const jobsForm = ref({
  skills: 'Java, Spring Boot, MySQL',
  preferredCities: 'Beijing, Shanghai',
  education: 'Bachelor',
  experience: '1-3 years',
  industry: '',
  limit: 8
})
const jobsResult = ref(null)

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
  { key: 'jobs', label: 'Job Match', icon: Sparkles },
  { key: 'skills', label: 'Skill Gap', icon: Radar },
  { key: 'path', label: 'Career Path', icon: Compass },
  { key: 'resume', label: 'Resume Review', icon: FileSearch },
  { key: 'import', label: 'Profile Import', icon: FileUp },
  { key: 'salary', label: 'Salary Predict', icon: Calculator }
]

const loginPrompt = computed(() => !authStore.isLoggedIn)

function splitInput(value) {
  return `${value || ''}`
    .split(/[,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function listify(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function formatMoney(value) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString() : value
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') {
    return '--'
  }
  const num = Number(value)
  return Number.isFinite(num) ? `${num}` : value
}

function stringifyList(value) {
  return listify(value).join(', ')
}

function toneFromScore(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) {
    return 'neutral'
  }
  if (num >= 75) {
    return 'good'
  }
  if (num >= 55) {
    return 'mid'
  }
  return 'weak'
}

function searchJobsFromRecommendation(item) {
  router.push({
    path: '/jobs',
    query: {
      keyword: item?.title || '',
      city: item?.city || ''
    }
  })
}

function openJobFromRecommendation(item) {
  if (!item?.id) return
  router.push({
    path: '/jobs',
    query: {
      keyword: item?.title || '',
      city: item?.city || '',
      open: item.id
    }
  })
}

function searchTargetRole(role, city = '') {
  router.push({
    path: '/jobs',
    query: {
      keyword: role || '',
      city: city || ''
    }
  })
}

async function autofillFromProfile() {
  if (!authStore.token || profileLoading.value) return
  profileLoading.value = true
  error.value = ''
  try {
    const profileData = await fetchCareerProfile(authStore.token)
    const profile = profileData.profile || {}
    const skills = listify(profileData.skills)
    const city = profile.targetCityCode || ''
    const role = profile.profileSummary || ''
    const education = profile.educationLevel || ''

    if (skills.length) {
      jobsForm.value.skills = stringifyList(skills)
      skillsForm.value.userSkills = stringifyList(skills)
      pathForm.value.currentSkills = stringifyList(skills)
      resumeForm.value.userSkills = stringifyList(skills)
      predictForm.value.skills = stringifyList(skills)
    }
    if (city) {
      jobsForm.value.preferredCities = city
      skillsForm.value.city = city
      pathForm.value.city = city
      predictForm.value.city = city
    }
    if (role) {
      skillsForm.value.targetJobType = role
      resumeForm.value.targetJob = role
    }
    if (education) {
      jobsForm.value.education = education
      predictForm.value.education = education
    }
    if (profile.expectedSalaryMin) {
      predictForm.value.salaryMin = profile.expectedSalaryMin
    }
    importSuccess.value = 'Recommendation forms synced from your profile.'
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    profileLoading.value = false
  }
}

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
}

async function importProfile() {
  if (!uploadFile.value || importLoading.value) return
  importLoading.value = true
  error.value = ''
  importSuccess.value = ''

  try {
    importResult.value = await importAiProfileFile(authStore.token, uploadFile.value, overwriteSkills.value)
    importSuccess.value = `Imported profile. Saved skills: ${importResult.value.savedSkills || 0}.`
    if (authStore.syncProfile) await authStore.syncProfile()
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
    predictResult.value = await predictSalary({
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
</script>

<template>
  <div class="recommend-page">
    <section class="hero glass-panel">
      <div class="hero-copy">
        <p class="eyebrow">Career Decision Studio</p>
        <h1>把推荐结果变成可执行的求职动作</h1>
        <p class="hero-text">
          这里不是调接口结果，而是把岗位匹配、技能缺口、路径规划和简历诊断整理成一套职业行动台。
        </p>
      </div>
      <div class="hero-metrics">
        <div class="metric-pill">
          <span class="metric-label">Modules</span>
          <strong>6</strong>
        </div>
        <div class="metric-pill">
          <span class="metric-label">Output</span>
          <strong>Action-ready</strong>
        </div>
        <button class="hero-sync" :disabled="profileLoading" @click="autofillFromProfile">
          {{ profileLoading ? 'Syncing...' : 'Use My Profile' }}
        </button>
      </div>
    </section>

    <div v-if="loginPrompt" class="login-banner glass-panel">
      <Bot :size="18" />
      <span>Sign in first to use recommendation and profile import features.</span>
    </div>

    <div v-if="error" class="error-banner glass-panel">{{ error }}</div>
    <div v-if="importSuccess" class="success-banner glass-panel">{{ importSuccess }}</div>

    <template v-if="!loginPrompt">
      <div class="tabs">
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

      <section class="grid">
        <PremiumCard v-if="activeTab === 'jobs'" title="Job Recommendation" glowColor="primary">
          <div class="form-grid">
            <input v-model="jobsForm.skills" class="glass-input" placeholder="Skills" />
            <input v-model="jobsForm.preferredCities" class="glass-input" placeholder="Preferred cities" />
            <input v-model="jobsForm.education" class="glass-input" placeholder="Education" />
            <input v-model="jobsForm.experience" class="glass-input" placeholder="Experience" />
            <input v-model="jobsForm.industry" class="glass-input" placeholder="Industry" />
            <input v-model="jobsForm.limit" class="glass-input" type="number" min="1" max="20" placeholder="Limit" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">Run Match</GlowButton>

          <template v-if="jobsResult">
            <div class="result-shell">
              <div class="summary-grid">
                <div class="summary-box">
                  <span>Matches</span>
                  <strong>{{ jobsResult.summary?.returnedCount ?? listify(jobsResult.items).length }}</strong>
                </div>
                <div class="summary-box">
                  <span>Average Fit</span>
                  <strong>{{ formatScore(jobsResult.summary?.avgScore) }}</strong>
                </div>
                <div class="summary-box">
                  <span>Top Cities</span>
                  <strong>{{ listify(jobsResult.summary?.topCities).slice(0, 2).join(' / ') || '--' }}</strong>
                </div>
              </div>

              <div v-if="listify(jobsResult.items).length" class="job-list">
                <article v-for="item in jobsResult.items" :key="item.id" class="result-card">
                  <div class="result-head">
                    <div>
                      <h3>{{ item.title }}</h3>
                      <p>{{ item.companyName }} · {{ item.city || '--' }}</p>
                    </div>
                    <div class="score-badge" :class="toneFromScore(item.score)">
                      {{ item.fitLabel || 'Fit' }} {{ formatScore(item.score) }}
                    </div>
                  </div>
                  <div class="meta-row">
                    <span>{{ item.industryName || '--' }}</span>
                    <span>{{ item.education || '--' }}</span>
                    <span>{{ item.experience || '--' }}</span>
                    <span>{{ item.salaryText || `${formatMoney(item.salaryMin)} - ${formatMoney(item.salaryMax)}` }}</span>
                  </div>
                  <div class="chip-row" v-if="listify(item.matchedSkills).length">
                    <span v-for="skill in item.matchedSkills" :key="skill" class="chip chip-good">{{ skill }}</span>
                  </div>
                  <div class="detail-block">
                    <h4>Why Matched</h4>
                    <ul>
                      <li v-for="reason in listify(item.whyMatched)" :key="reason">{{ reason }}</li>
                    </ul>
                  </div>
                  <div class="detail-block" v-if="listify(item.nextActions).length">
                    <h4>Next Actions</h4>
                    <ul>
                      <li v-for="action in listify(item.nextActions)" :key="action">{{ action }}</li>
                    </ul>
                  </div>
                  <div class="action-row">
                    <button class="inline-action primary" @click="openJobFromRecommendation(item)">View Detail</button>
                    <button class="inline-action" @click="searchJobsFromRecommendation(item)">Search Similar</button>
                  </div>
                </article>
              </div>
              <div v-else class="empty-state">No strong matches yet. Adjust the stack or city and run again.</div>
            </div>
          </template>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'skills'" title="Skill Gap Analysis" glowColor="secondary">
          <div class="form-grid">
            <input v-model="skillsForm.userSkills" class="glass-input" placeholder="Current skills" />
            <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="Target role" />
            <input v-model="skillsForm.city" class="glass-input" placeholder="City" />
          </div>
          <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">Analyze Gap</GlowButton>

          <div v-if="skillsResult" class="result-shell">
            <div class="summary-grid">
              <div class="summary-box">
                <span>Target Role</span>
                <strong>{{ skillsResult.targetJobType || '--' }}</strong>
              </div>
              <div class="summary-box">
                <span>Match Rate</span>
                <strong>{{ skillsResult.matchRate || '--' }}</strong>
              </div>
              <div class="summary-box">
                <span>Priority Skills</span>
                <strong>{{ listify(skillsResult.prioritySkills).length }}</strong>
              </div>
            </div>

            <div class="dual-grid">
              <div class="result-card compact">
                <h3>Current Strengths</h3>
                <div class="chip-row">
                  <span v-for="item in listify(skillsResult.entryStrengths)" :key="item" class="chip chip-good">{{ item }}</span>
                </div>
                <p class="subtle">{{ skillsResult.summary }}</p>
              </div>
              <div class="result-card compact">
                <h3>Priority Gaps</h3>
                <div class="chip-row">
                  <span v-for="item in listify(skillsResult.prioritySkills)" :key="item" class="chip chip-warn">{{ item }}</span>
                </div>
              </div>
            </div>

            <div class="result-card">
              <h3>Learning Path</h3>
              <div class="timeline">
                <div v-for="stage in listify(skillsResult.learningPath)" :key="stage.stage" class="timeline-item">
                  <strong>{{ stage.stage }}</strong>
                  <span>{{ stage.focusSkill }}</span>
                  <p>{{ stage.goal }}</p>
                </div>
              </div>
            </div>

            <div v-if="radarResult" class="result-card">
              <h3>Skill Radar</h3>
              <div class="radar-list">
                <div v-for="item in listify(radarResult.skills)" :key="item.skill" class="radar-row">
                  <div>
                    <strong>{{ item.skill }}</strong>
                    <p>Current {{ item.currentScore }} / Target {{ item.targetScore }}</p>
                  </div>
                  <div class="gap-pill" :class="toneFromScore(100 - item.gapScore)">Gap {{ item.gapScore }}</div>
                </div>
              </div>
              <p class="subtle">{{ radarResult.interpretation }}</p>
            </div>
            <div class="action-row">
              <button class="inline-action primary" @click="searchTargetRole(skillsResult.targetJobType, skillsResult.city)">Search Target Jobs</button>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'path'" title="Career Path Planning" glowColor="teal">
          <div class="form-grid">
            <input v-model="pathForm.currentJob" class="glass-input" placeholder="Current role" />
            <input v-model="pathForm.targetJob" class="glass-input" placeholder="Target role" />
            <input v-model="pathForm.currentSkills" class="glass-input" placeholder="Current skills" />
            <input v-model="pathForm.city" class="glass-input" placeholder="City" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">Generate Path</GlowButton>

          <div v-if="pathResult" class="result-shell">
            <div class="summary-grid">
              <div class="summary-box">
                <span>Current</span>
                <strong>{{ pathResult.currentJob }}</strong>
              </div>
              <div class="summary-box">
                <span>Target</span>
                <strong>{{ pathResult.targetJob }}</strong>
              </div>
              <div class="summary-box">
                <span>Direction</span>
                <strong>{{ pathResult.direction || '--' }}</strong>
              </div>
            </div>

            <div class="timeline">
              <div v-for="step in listify(pathResult.steps)" :key="`${step.fromRole}-${step.toRole}`" class="timeline-item path-item">
                <strong>{{ step.fromRole }} → {{ step.toRole }}</strong>
                <span>{{ step.transitionType }} · {{ step.avgYears }} years</span>
                <div class="chip-row">
                  <span v-for="skill in listify(step.requiredSkills)" :key="skill" class="chip">{{ skill }}</span>
                </div>
              </div>
            </div>

            <div class="dual-grid">
              <div class="result-card compact">
                <h3>Milestones</h3>
                <ul>
                  <li v-for="item in listify(pathResult.milestones)" :key="item.quarter">
                    {{ item.quarter }} · {{ item.goal }}
                  </li>
                </ul>
              </div>
              <div class="result-card compact">
                <h3>Recommended Projects</h3>
                <ul>
                  <li v-for="item in listify(pathResult.recommendedProjects)" :key="item.name">
                    {{ item.name }}
                  </li>
                </ul>
              </div>
            </div>
            <p class="subtle">{{ pathResult.timelineSummary }}</p>
            <div class="action-row">
              <button class="inline-action primary" @click="searchTargetRole(pathResult.targetJob, pathResult.city)">Search This Path Target</button>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'resume'" title="Resume Review" glowColor="primary">
          <div class="form-grid">
            <input v-model="resumeForm.targetJob" class="glass-input" placeholder="Target role" />
            <input v-model="resumeForm.userSkills" class="glass-input" placeholder="Skills" />
            <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="Resume text" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">Review Resume</GlowButton>

          <div v-if="resumeResult" class="result-shell">
            <div class="summary-grid">
              <div class="summary-box">
                <span>Target Role</span>
                <strong>{{ resumeResult.targetJob }}</strong>
              </div>
              <div class="summary-box">
                <span>Overall Score</span>
                <strong>{{ resumeResult.overallScore }}</strong>
              </div>
              <div class="summary-box">
                <span>Missing Skills</span>
                <strong>{{ listify(resumeResult.missingSkills).length }}</strong>
              </div>
            </div>

            <div class="dual-grid">
              <div class="result-card compact">
                <h3>Scorecard</h3>
                <div class="score-list">
                  <div v-for="(value, key) in resumeResult.scorecard || {}" :key="key" class="score-row">
                    <span>{{ key }}</span>
                    <strong :class="toneFromScore(value)">{{ value }}</strong>
                  </div>
                </div>
              </div>
              <div class="result-card compact">
                <h3>Matched Skills</h3>
                <div class="chip-row">
                  <span v-for="item in listify(resumeResult.matchedSkills)" :key="item" class="chip chip-good">{{ item }}</span>
                </div>
              </div>
            </div>

            <div class="result-card compact">
              <h3>Suggestions</h3>
              <ul>
                <li v-for="item in listify(resumeResult.suggestions)" :key="item">{{ item }}</li>
              </ul>
            </div>

            <div class="result-card compact">
              <h3>Rewrite Hints</h3>
              <ul>
                <li v-for="item in listify(resumeResult.rewriteHints)" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div class="action-row">
              <button class="inline-action primary" @click="searchTargetRole(resumeResult.targetJob)">Search Matching Jobs</button>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'import'" title="Profile Import" glowColor="secondary">
          <div class="form-grid">
            <input type="file" class="glass-input" @change="handleFileChange" />
            <label class="checkbox-row">
              <input v-model="overwriteSkills" type="checkbox" />
              Overwrite existing skills
            </label>
          </div>
          <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">Import File</GlowButton>
          <div v-if="importResult" class="result-shell">
            <div class="summary-grid">
              <div class="summary-box">
                <span>Saved Skills</span>
                <strong>{{ importResult.savedSkills || 0 }}</strong>
              </div>
              <div class="summary-box">
                <span>Updated</span>
                <strong>{{ importResult.updated ? 'Yes' : 'No' }}</strong>
              </div>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'salary'" title="Salary Prediction" glowColor="teal">
          <div class="form-grid">
            <input v-model="predictForm.city" class="glass-input" placeholder="City" />
            <input v-model="predictForm.education" class="glass-input" placeholder="Education" />
            <input v-model="predictForm.experience" class="glass-input" placeholder="Experience" />
            <input v-model="predictForm.skills" class="glass-input" placeholder="Skills" />
            <input v-model="predictForm.industry" class="glass-input" placeholder="Industry" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="runPrediction">Predict Salary</GlowButton>
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
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(220px, 0.7fr);
  gap: 20px;
  padding: 28px;
  overflow: hidden;
  position: relative;
}
.hero::after {
  content: '';
  position: absolute;
  inset: auto -10% -35% auto;
  width: 320px;
  height: 320px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.22), rgba(59, 130, 246, 0));
  pointer-events: none;
}
.eyebrow { margin: 0 0 10px; font-size: 12px; letter-spacing: .16em; text-transform: uppercase; color: var(--c-text-faint); }
.hero h1 { margin: 0; font-size: clamp(30px, 4vw, 48px); line-height: 1.05; max-width: 10ch; }
.hero-text { margin: 14px 0 0; max-width: 60ch; color: var(--c-text-secondary); line-height: 1.7; }
.hero-metrics { display: grid; gap: 12px; align-content: start; }
.metric-pill, .summary-box, .result-card, .tab-btn, .glass-input, .result-box {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
}
.hero-sync {
  padding: 12px 16px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  background: rgba(59, 130, 246, 0.14);
  color: var(--c-text-primary);
  font-weight: 700;
}
.hero-sync:disabled {
  opacity: .6;
  cursor: not-allowed;
}
.metric-pill { border-radius: 18px; padding: 16px 18px; }
.metric-label { display: block; color: var(--c-text-faint); font-size: 12px; margin-bottom: 6px; text-transform: uppercase; letter-spacing: .08em; }
.metric-pill strong { font-size: 20px; color: var(--c-text-primary); }
.login-banner, .error-banner, .success-banner {
  display: flex; align-items: center; gap: 10px; padding: 14px 16px; border-radius: 16px;
}
.error-banner { color: #fecaca; }
.success-banner { color: #bbf7d0; }
.tabs { display: flex; flex-wrap: wrap; gap: 10px; }
.tab-btn {
  display: inline-flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 999px;
  color: var(--c-text-primary);
}
.tab-btn.active { background: rgba(30, 117, 255, 0.16); border-color: rgba(30, 117, 255, 0.42); }
.grid { display: grid; gap: 24px; }
.form-grid { display: grid; gap: 12px; margin-bottom: 16px; }
.glass-input { width: 100%; padding: 12px 14px; border-radius: 14px; color: var(--c-text-primary); }
.tall { min-height: 180px; resize: vertical; }
.checkbox-row { display: inline-flex; align-items: center; gap: 10px; color: var(--c-text-secondary); }
.result-shell { margin-top: 18px; display: grid; gap: 16px; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.summary-box { border-radius: 18px; padding: 16px; }
.summary-box span { display: block; font-size: 12px; color: var(--c-text-faint); text-transform: uppercase; letter-spacing: .08em; margin-bottom: 8px; }
.summary-box strong { color: var(--c-text-primary); font-size: 20px; line-height: 1.2; }
.job-list, .dual-grid { display: grid; gap: 14px; }
.dual-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.result-card { border-radius: 20px; padding: 18px; display: grid; gap: 12px; }
.result-card.compact { gap: 10px; }
.result-card h3, .detail-block h4 { margin: 0; color: var(--c-text-primary); }
.result-card p, .result-card li, .subtle { color: var(--c-text-secondary); line-height: 1.7; }
.result-card ul { margin: 0; padding-left: 18px; display: grid; gap: 8px; }
.result-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.result-head h3 { margin: 0 0 6px; font-size: 20px; }
.result-head p { margin: 0; color: var(--c-text-muted); }
.meta-row { display: flex; flex-wrap: wrap; gap: 8px; color: var(--c-text-muted); font-size: 13px; }
.meta-row span { padding: 6px 10px; border-radius: 999px; background: rgba(255,255,255,.03); }
.chip-row { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  padding: 7px 10px; border-radius: 999px; background: rgba(255,255,255,.05);
  color: var(--c-text-secondary); font-size: 12px; font-weight: 600;
}
.chip-good { background: rgba(16, 185, 129, .14); color: #9ae6b4; }
.chip-warn { background: rgba(245, 158, 11, .14); color: #fcd34d; }
.action-row { display: flex; flex-wrap: wrap; gap: 10px; }
.inline-action {
  padding: 10px 14px; border-radius: 999px; border: 1px solid var(--c-border-glass);
  background: rgba(255,255,255,.04); color: var(--c-text-primary); font-size: 13px; font-weight: 700;
}
.inline-action.primary {
  background: rgba(59, 130, 246, .18);
  border-color: rgba(59, 130, 246, .35);
}
.score-badge, .gap-pill {
  padding: 8px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap;
}
.score-badge.good, .gap-pill.good, strong.good { color: #86efac; background: rgba(34, 197, 94, .14); }
.score-badge.mid, .gap-pill.mid, strong.mid { color: #fcd34d; background: rgba(245, 158, 11, .14); }
.score-badge.weak, .gap-pill.weak, strong.weak { color: #fca5a5; background: rgba(239, 68, 68, .14); }
.score-badge.neutral, .gap-pill.neutral, strong.neutral { color: var(--c-text-secondary); background: rgba(255,255,255,.06); }
.timeline { display: grid; gap: 12px; }
.timeline-item {
  padding: 14px 16px; border-radius: 18px; border: 1px dashed var(--c-border-glass);
  background: rgba(255,255,255,.025); display: grid; gap: 8px;
}
.timeline-item strong { color: var(--c-text-primary); }
.timeline-item span { color: var(--c-text-muted); font-size: 13px; }
.radar-list, .score-list { display: grid; gap: 10px; }
.radar-row, .score-row {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  padding: 12px 0; border-bottom: 1px solid var(--c-border-glass);
}
.radar-row:last-child, .score-row:last-child { border-bottom: 0; }
.empty-state {
  padding: 22px; text-align: center; border-radius: 18px; color: var(--c-text-muted);
  border: 1px dashed var(--c-border-glass); background: rgba(255,255,255,.025);
}
.result-box {
  margin: 0; padding: 16px; border-radius: 16px; overflow: auto; white-space: pre-wrap; word-break: break-word;
  color: var(--c-text-secondary);
}
@media (max-width: 960px) {
  .hero, .dual-grid, .summary-grid { grid-template-columns: 1fr; }
  .result-head, .radar-row, .score-row { flex-direction: column; align-items: flex-start; }
}
</style>
