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
import { Bot, Calculator, Compass, FileSearch, FileUp, Radar, Sparkles } from 'lucide-vue-next'

const authStore = useAuthStore()
const activeTab = ref('jobs')
const loading = ref(false)
const error = ref('')
const importLoading = ref(false)
const importSuccess = ref('')

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
  return value
    .split(/[,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function handleFileChange(event) {
  uploadFile.value = event.target.files?.[0] || null
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
    importSuccess.value = `Imported profile. Saved skills: ${importResult.value.savedSkills || 0}.`
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
          <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">Run</GlowButton>
          <pre v-if="jobsResult" class="result-box">{{ JSON.stringify(jobsResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'skills'" title="Skill Gap Analysis" glowColor="secondary">
          <div class="form-grid">
            <input v-model="skillsForm.userSkills" class="glass-input" placeholder="Current skills" />
            <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="Target role" />
            <input v-model="skillsForm.city" class="glass-input" placeholder="City" />
          </div>
          <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">Analyze</GlowButton>
          <pre v-if="skillsResult" class="result-box">{{ JSON.stringify(skillsResult, null, 2) }}</pre>
          <pre v-if="radarResult" class="result-box">{{ JSON.stringify(radarResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'path'" title="Career Path Planning" glowColor="teal">
          <div class="form-grid">
            <input v-model="pathForm.currentJob" class="glass-input" placeholder="Current role" />
            <input v-model="pathForm.targetJob" class="glass-input" placeholder="Target role" />
            <input v-model="pathForm.currentSkills" class="glass-input" placeholder="Current skills" />
            <input v-model="pathForm.city" class="glass-input" placeholder="City" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">Plan</GlowButton>
          <pre v-if="pathResult" class="result-box">{{ JSON.stringify(pathResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'resume'" title="Resume Review" glowColor="primary">
          <div class="form-grid">
            <input v-model="resumeForm.targetJob" class="glass-input" placeholder="Target role" />
            <input v-model="resumeForm.userSkills" class="glass-input" placeholder="Skills" />
            <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="Resume text" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">Review</GlowButton>
          <pre v-if="resumeResult" class="result-box">{{ JSON.stringify(resumeResult, null, 2) }}</pre>
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
          <pre v-if="importResult" class="result-box">{{ JSON.stringify(importResult, null, 2) }}</pre>
        </PremiumCard>

        <PremiumCard v-if="activeTab === 'salary'" title="Salary Prediction" glowColor="teal">
          <div class="form-grid">
            <input v-model="predictForm.city" class="glass-input" placeholder="City" />
            <input v-model="predictForm.education" class="glass-input" placeholder="Education" />
            <input v-model="predictForm.experience" class="glass-input" placeholder="Experience" />
            <input v-model="predictForm.skills" class="glass-input" placeholder="Skills" />
            <input v-model="predictForm.industry" class="glass-input" placeholder="Industry" />
          </div>
          <GlowButton variant="primary" :loading="loading" @click="runPrediction">Predict</GlowButton>
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
</style>
