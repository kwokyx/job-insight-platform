<script setup>
import { computed, ref } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
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
import { Bot, Building2, Calculator, Compass, FileSearch, FileUp, MapPin, Radar, Sparkles, Target } from 'lucide-vue-next'

const authStore = useAuthStore()
const activeTab = ref('jobs')
const loading = ref(false)
const error = ref('')
const importLoading = ref(false)
const importSuccess = ref('')

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

function splitInput(value) {
  return value
    .split(/[,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

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
  return Array.isArray(tags) ? tags.slice(0, 4) : []
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

async function importProfile() {
  if (!uploadFile.value || importLoading.value) {
    return
  }

  importLoading.value = true
  error.value = ''
  importSuccess.value = ''

  try {
    importResult.value = await importAiProfileFile(authStore.token, uploadFile.value, overwriteSkills.value)
    importSuccess.value = `已导入个人资料。已保存技能数: ${importResult.value.savedSkills || 0}。`
    if (authStore.syncProfile) {
      await authStore.syncProfile()
    }
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
    <div v-if="loginPrompt" class="login-banner glass-panel">
      <Bot :size="18" />
      <span>请先登录以使用智能推荐和资料导入功能。</span>
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
        <PremiumCard v-if="activeTab === 'jobs'" title="职位推荐" glowColor="primary">
          <div class="form-grid">
            <input v-model="jobsForm.skills" class="glass-input" placeholder="技能" />
            <input v-model="jobsForm.preferredCities" class="glass-input" placeholder="期望城市" />
            <input v-model="jobsForm.education" class="glass-input" placeholder="学历" />
            <input v-model="jobsForm.experience" class="glass-input" placeholder="经验" />
            <input v-model="jobsForm.industry" class="glass-input" placeholder="行业" />
            <input v-model="jobsForm.limit" class="glass-input" type="number" min="1" max="20" placeholder="数量" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">运行</GlowButton>
          <div v-if="hasStructuredJobs" class="job-album" aria-label="推荐岗位列表">
            <article
              v-for="(job, index) in recommendedJobs"
              :key="job.jobId || job.id || `${getJobTitle(job)}-${index}`"
              class="recommend-job-card"
              tabindex="0"
              @pointermove="handleJobCardMove"
              @pointerleave="resetJobCard"
              @blur="resetJobCard"
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
                  <span class="job-card-link">推荐摘要</span>
                </div>
              </div>
            </article>
          </div>
          <pre v-else-if="jobsResult" class="result-box">{{ JSON.stringify(jobsResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'skills'" title="技能差距分析" glowColor="secondary">
          <div class="form-grid">
            <input v-model="skillsForm.userSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="目标职位" />
            <input v-model="skillsForm.city" class="glass-input" placeholder="城市" />
          </div>
          <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">分析</GlowButton>
          <pre v-if="skillsResult" class="result-box">{{ JSON.stringify(skillsResult, null, 2) }}</pre>
          <pre v-if="radarResult" class="result-box">{{ JSON.stringify(radarResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'path'" title="职业路径规划" glowColor="teal">
          <div class="form-grid">
            <input v-model="pathForm.currentJob" class="glass-input" placeholder="当前职位" />
            <input v-model="pathForm.targetJob" class="glass-input" placeholder="目标职位" />
            <input v-model="pathForm.currentSkills" class="glass-input" placeholder="当前技能" />
            <input v-model="pathForm.city" class="glass-input" placeholder="城市" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">规划</GlowButton>
          <pre v-if="pathResult" class="result-box">{{ JSON.stringify(pathResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'resume'" title="简历评估" glowColor="primary">
          <div class="form-grid">
            <input v-model="resumeForm.targetJob" class="glass-input" placeholder="目标职位" />
            <input v-model="resumeForm.userSkills" class="glass-input" placeholder="技能" />
            <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="简历正文" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">评估</GlowButton>
          <pre v-if="resumeResult" class="result-box">{{ JSON.stringify(resumeResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'import'" title="资料导入" glowColor="secondary">
          <div class="form-grid">
            <input type="file" class="glass-input" @change="handleFileChange" />
            <label class="checkbox-row">
              <input v-model="overwriteSkills" type="checkbox" />
              覆盖现有技能
            </label>
          </div>
          <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">导入文件</GlowButton>
          <pre v-if="importResult" class="result-box">{{ JSON.stringify(importResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'salary'" title="薪资预测" glowColor="teal">
          <div class="form-grid">
            <input v-model="predictForm.city" class="glass-input" placeholder="城市" />
            <input v-model="predictForm.education" class="glass-input" placeholder="学历" />
            <input v-model="predictForm.experience" class="glass-input" placeholder="经验" />
            <input v-model="predictForm.skills" class="glass-input" placeholder="技能" />
            <input v-model="predictForm.industry" class="glass-input" placeholder="行业" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="runPrediction">预测</GlowButton>
          <pre v-if="predictResult" class="result-box">{{ JSON.stringify(predictResult, null, 2) }}</pre>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.recommend-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.login-banner,
.error-banner,
.success-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
}

.error-banner {
  color: #fecaca;
}

.success-banner {
  color: #bbf7d0;
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

.tab-btn.active {
  background: rgba(30, 117, 255, 0.14);
  border-color: rgba(30, 117, 255, 0.4);
}

.grid {
  display: grid;
  gap: 24px;
}

.form-grid {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
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

.result-box {
  margin-top: 16px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.job-album {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
  margin-top: 18px;
  perspective: 1200px;
}

.recommend-job-card {
  --rx: 0deg;
  --ry: 0deg;
  --mx: 50%;
  --my: 35%;
  position: relative;
  min-height: 280px;
  isolation: isolate;
  overflow: hidden;
  border: 1px solid rgba(24, 27, 35, 0.1);
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(247, 249, 253, 0.9));
  box-shadow: 0 10px 28px rgba(24, 27, 35, 0.06);
  color: var(--c-text-primary);
  cursor: default;
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
  border-color: rgba(0, 87, 194, 0.36);
  background:
    radial-gradient(circle at var(--mx) var(--my), rgba(0, 110, 242, 0.2), transparent 34%),
    linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(241, 246, 255, 0.92));
  box-shadow: 0 28px 70px rgba(0, 87, 194, 0.16);
  outline: none;
}

.recommend-job-card::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.72), transparent 38%),
    repeating-linear-gradient(135deg, rgba(24, 27, 35, 0.025) 0 1px, transparent 1px 10px);
  opacity: 0.48;
  transition: opacity 220ms ease, background 220ms ease;
}

.recommend-job-card:hover::before,
.recommend-job-card:focus-visible::before {
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.82), transparent 38%),
    repeating-linear-gradient(135deg, rgba(0, 87, 194, 0.07) 0 1px, transparent 1px 10px);
  opacity: 0.82;
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
  gap: 12px;
  padding: 22px;
  transform: translateZ(34px);
}

.job-card-topline,
.job-meta,
.job-card-footer {
  display: flex;
  align-items: center;
}

.job-card-topline {
  justify-content: space-between;
  gap: 12px;
}

.job-rank {
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.job-match-score {
  min-width: 52px;
  padding: 6px 9px;
  border: 1px solid rgba(0, 87, 194, 0.16);
  background: rgba(255, 255, 255, 0.62);
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}

.recommend-job-card h3 {
  margin: 0;
  color: var(--c-text-primary);
  font-size: clamp(20px, 2.4vw, 26px);
  line-height: 1.12;
  letter-spacing: -0.04em;
}

.job-meta {
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--c-text-secondary);
  font-size: 13px;
}

.job-meta span,
.job-card-footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.job-salary {
  margin: 4px 0 0;
  color: var(--c-accent-primary);
  font-size: 19px;
  font-weight: 800;
}

.job-reason {
  display: -webkit-box;
  min-height: 44px;
  margin: 0;
  overflow: hidden;
  color: var(--c-text-secondary);
  font-size: 14px;
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
  padding: 6px 10px;
  border: 1px solid rgba(0, 87, 194, 0.13);
  background: rgba(255, 255, 255, 0.58);
  color: var(--c-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.job-card-footer {
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid rgba(0, 87, 194, 0.12);
  color: var(--c-text-muted);
  font-size: 13px;
  font-weight: 700;
}

.job-card-link {
  color: var(--c-accent-primary);
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
