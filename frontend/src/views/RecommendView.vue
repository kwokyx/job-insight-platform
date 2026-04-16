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
const importLoading = ref(false)
const importSuccess = ref('')
const selectedJob = ref(null)
const isLoadingJobDetail = ref(false)

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
const activeTabMeta = computed(() => tabs.find((item) => item.key === activeTab.value) || tabs[0])
const resultCountText = computed(() => {
  if (activeTab.value === 'jobs') {
    return hasStructuredJobs.value ? `${recommendedJobs.value.length} 条结果` : '等待运行'
  }

  if (activeTab.value === 'skills') {
    return skillsResult.value || radarResult.value ? '已生成结果' : '等待分析'
  }

  if (activeTab.value === 'path') {
    return pathResult.value ? '已生成结果' : '等待规划'
  }

  if (activeTab.value === 'resume') {
    return resumeResult.value ? '已生成结果' : '等待评估'
  }

  if (activeTab.value === 'import') {
    return importResult.value ? '已导入' : '等待导入'
  }

  return predictResult.value ? '已生成结果' : '等待预测'
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

async function openRecommendedJob(job) {
  const jobId = job.jobId || job.job_id || job.id
  if (!jobId) {
    return
  }

  selectedJob.value = {
    id: jobId,
    title: getJobTitle(job),
    companyName: getJobCompany(job),
    city: getJobCity(job),
    salaryText: getJobSalary(job),
    education: job.education,
    experience: job.experience,
    industryName: job.industryName || job.industry_name,
    sourceUrl: job.sourceUrl || job.source_url
  }
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
  <div class="recommend-page page-shell">
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <span class="eyebrow">智能推荐工作台</span>
        <h1>把岗位匹配、技能差距、职业路径和简历评估放在同一套操作界面里</h1>
        <p>
          左侧负责输入和控制，右侧负责结果和解释。结构收紧之后，中屏和移动端不会再被多个重卡片和重复装饰打断阅读。
        </p>
        <div class="hero-actions">
          <GlowButton variant="ghost" :loading="loading" @click="handleJobsRecommend">
            <Sparkles :size="14" />
            快速运行
          </GlowButton>
          <div class="hero-note">
            <Bot :size="14" />
            <span>{{ loginPrompt ? '未登录，推荐和导入功能受限' : '已登录，所有推荐工作区均可使用' }}</span>
          </div>
        </div>
      </div>

      <div class="hero-aside">
        <div class="metric-grid">
          <div class="metric-tile">
            <span>当前模式</span>
            <strong>{{ activeTabMeta.label }}</strong>
          </div>
          <div class="metric-tile">
            <span>结果状态</span>
            <strong>{{ resultCountText }}</strong>
          </div>
          <div class="metric-tile">
            <span>资料导入</span>
            <strong>{{ importSuccess ? '已完成' : '待导入' }}</strong>
          </div>
          <div class="metric-tile">
            <span>登录状态</span>
            <strong>{{ loginPrompt ? '未登录' : '已登录' }}</strong>
          </div>
        </div>
        <div class="status-strip">
          <Radar :size="16" />
          <div>
            <strong>工作台提示</strong>
            <p>先输入条件，再在右侧查看结果。岗位卡片和详情弹层保留，但整体层级更清楚。</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="loginPrompt" class="login-banner status-banner">
      <Bot :size="18" />
      <span>请先登录以使用智能推荐和资料导入功能。</span>
    </div>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="importSuccess" class="status-banner success-banner">{{ importSuccess }}</div>

    <template v-if="!loginPrompt">
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
              <h2>{{ activeTabMeta.label }}配置</h2>
              <p>左侧放参数、右侧看结果。只保留当前任务需要的控件，减少视线来回切换。</p>
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
              <GlowButton variant="primary" :loading="loading" @click="handleJobsRecommend">运行推荐</GlowButton>
              <span class="panel-hint">基于技能、城市、学历和经验组合匹配。</span>
            </div>
          </template>

          <template v-else-if="activeTab === 'skills'">
            <div class="form-grid">
              <input v-model="skillsForm.userSkills" class="glass-input" placeholder="当前技能" />
              <input v-model="skillsForm.targetJobType" class="glass-input" placeholder="目标职位" />
              <input v-model="skillsForm.city" class="glass-input" placeholder="城市" />
            </div>
            <div class="panel-actions">
              <GlowButton variant="secondary" :loading="loading" @click="handleSkillGap">分析差距</GlowButton>
              <span class="panel-hint">同时生成差距结果和技能雷达。</span>
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
              <GlowButton variant="primary" :loading="loading" @click="handleCareerPath">生成路径</GlowButton>
              <span class="panel-hint">给出职业路径和阶段性建议。</span>
            </div>
          </template>

          <template v-else-if="activeTab === 'resume'">
            <div class="form-grid">
              <input v-model="resumeForm.targetJob" class="glass-input" placeholder="目标职位" />
              <input v-model="resumeForm.userSkills" class="glass-input" placeholder="技能" />
              <textarea v-model="resumeForm.resumeText" class="glass-input tall" placeholder="简历正文" />
            </div>
            <div class="panel-actions">
              <GlowButton variant="primary" :loading="loading" @click="handleResumeReview">评估简历</GlowButton>
              <span class="panel-hint">输出可直接用于修改简历的建议。</span>
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
              <GlowButton variant="secondary" :loading="importLoading" @click="importProfile">导入文件</GlowButton>
              <span class="panel-hint">导入简历或资料文件，更新个人画像。</span>
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
              <GlowButton variant="primary" :loading="loading" @click="runPrediction">预测薪资</GlowButton>
              <span class="panel-hint">根据城市、经验、技能和行业组合推断区间。</span>
            </div>
          </template>
        </article>

        <aside class="surface section-panel result-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">结果区</span>
              <h2>{{ activeTabMeta.label }}输出</h2>
              <p>右侧只呈现当前任务的结果或解释，不再堆叠多层容器。</p>
            </div>
          </div>

          <template v-if="activeTab === 'jobs'">
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
                    <span class="job-card-link">推荐摘要</span>
                  </div>
                </div>
              </article>
            </div>
            <pre v-else-if="jobsResult" class="result-box">{{ JSON.stringify(jobsResult, null, 2) }}</pre>
            <div v-else class="empty-state">运行后会在这里显示推荐岗位。</div>
          </template>

          <template v-else-if="activeTab === 'skills'">
            <pre v-if="skillsResult" class="result-box">{{ JSON.stringify(skillsResult, null, 2) }}</pre>
            <pre v-if="radarResult" class="result-box">{{ JSON.stringify(radarResult, null, 2) }}</pre>
            <div v-if="!skillsResult && !radarResult" class="empty-state">先提交技能信息，再查看差距和雷达结果。</div>
          </template>

          <template v-else-if="activeTab === 'path'">
            <pre v-if="pathResult" class="result-box">{{ JSON.stringify(pathResult, null, 2) }}</pre>
            <div v-else class="empty-state">先生成路径，再在这里查看规划结果。</div>
          </template>

          <template v-else-if="activeTab === 'resume'">
            <pre v-if="resumeResult" class="result-box">{{ JSON.stringify(resumeResult, null, 2) }}</pre>
            <div v-else class="empty-state">上传简历或输入正文后，这里会显示评估结果。</div>
          </template>

          <template v-else-if="activeTab === 'import'">
            <pre v-if="importResult" class="result-box">{{ JSON.stringify(importResult, null, 2) }}</pre>
            <div v-else class="empty-state">导入成功后，这里会显示保存结果和技能数。</div>
          </template>

          <template v-else>
            <pre v-if="predictResult" class="result-box">{{ JSON.stringify(predictResult, null, 2) }}</pre>
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
    </template>
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
  gap: 24px;
  padding: 28px;
  border-radius: 22px;
}

.hero-copy,
.hero-aside,
.section-panel {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 14px;
}

.hero-copy h1,
.panel-head h2,
.panel-head h3,
.recommend-modal-copy h2 {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.08;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.recommend-modal-text,
.empty-state,
.panel-hint {
  color: var(--c-text-secondary);
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
.status-banner,
.tabs-rail,
.empty-state,
.result-box {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
}

.hero-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  color: var(--c-text-secondary);
  background: rgba(255, 255, 255, 0.54);
}

.hero-aside {
  gap: 14px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
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
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.58);
}

.tabs-rail {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.64);
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: rgba(255, 255, 255, 0.7);
  color: var(--c-text-primary);
}

.tab-btn.active {
  border-color: rgba(30, 117, 255, 0.28);
  background: rgba(30, 117, 255, 0.12);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 24px;
}

.section-panel {
  gap: 18px;
  min-width: 0;
  padding: 24px;
  border-radius: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
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
  justify-content: space-between;
}

.panel-hint {
  font-size: 13px;
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
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
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
  border: 1px solid rgba(27, 38, 59, 0.08);
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f6f8fc);
  box-shadow: var(--shadow-card-quiet);
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
  box-shadow: var(--shadow-card-raised);
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
  gap: 12px;
  padding: 22px;
  transform: translateZ(34px);
}

.job-card-topline {
  display: flex;
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

@media (max-width: 1180px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-hero {
    grid-template-columns: 1fr;
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

  .metric-grid {
    grid-template-columns: 1fr 1fr;
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
