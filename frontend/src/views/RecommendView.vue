<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchPersonalizedRecommendPlan,
  normalizeError,
  parseResume,
  predictSalary,
  recommendJobs,
  scoreResume
} from '../api'
import {
  Bot,
  Briefcase,
  Calculator,
  FileSearch,
  GraduationCap,
  Lightbulb,
  MapPin,
  Sparkles,
  Target,
  Upload,
  AlertTriangle
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
const success = ref('')
const fileInputRef = ref(null)
const results = ref(null)
const personalizedPlan = ref(null)

const form = ref({
  targetJobType: '',
  targetCity: '',
  industry: '',
  education: '本科',
  experienceYears: 1,
  userSkills: ''
})

const loginPrompt = computed(() => !authStore.isLoggedIn)
const isStudent = computed(() => (authStore.user?.roleType ?? 0) === 0)
const isAdmin = computed(() => (authStore.user?.roleType ?? 0) === 1)
const isTeacher = computed(() => (authStore.user?.roleType ?? 0) === 2)

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
      axisName: { color: '#CBD5E1' },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: radarData.map(item => item.score),
        name: '能力评估',
        areaStyle: { color: 'rgba(56, 189, 248, 0.2)' },
        lineStyle: { color: '#38BDF8', width: 2 },
        itemStyle: { color: '#38BDF8' }
      }]
    }]
  }
})

const studentInsights = computed(() => {
  if (!results.value) return []
  const score = Number(results.value.score?.overall_score || 0)
  const tipsCount = results.value.score?.improvement_tips?.length || 0
  const salary = Number(results.value.salary?.prediction || 0)
  const jobCount = results.value.jobs?.length || 0

  return [
    {
      title: '竞争力判断',
      summary: `${Math.round(score)} 分`,
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

async function handleSmartAnalysis() {
  if (!form.value.targetJobType) {
    error.value = '请先填写目标岗位。'
    return
  }

  loading.value = true
  error.value = ''
  success.value = ''
  results.value = null

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

    results.value = {
      score: scoreRes,
      jobs: jobsRes?.items || [],
      salary: salaryRes
    }
    success.value = isStudent.value ? '学生求职分析已生成。' : '简历诊断结果已生成。'
    await loadPersonalizedPlan()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    loading.value = false
  }
}

onMounted(loadPersonalizedPlan)
</script>

<template>
  <div class="recommend-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">{{ isAdmin ? '管理员视角' : (isStudent ? '学生视角' : '教师视角') }}</span>
        <h1 class="page-intro-title">{{ isStudent ? '个人求职分析工作台' : '学生简历深度分析工作台' }}</h1>
        <p class="page-intro-text">
          {{ isStudent ? '这里不只是把匹配结果罗列出来，而是把简历评分、岗位机会和薪资预期转成求职阶段判断，帮助你知道下一步最该改什么。' : '输入学生的简历信息，系统将快速诊断其与市场岗位的匹配缺口，辅助您进行针对性的就业指导和教学重点调整。' }}
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">当前角色</span>
          <span class="intro-metric-value">{{ isStudent ? '学生' : '已登录用户' }}</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">推荐状态</span>
          <span class="intro-metric-value">{{ results ? '已生成' : '待分析' }}</span>
        </div>
      </div>
    </section>

    <div v-if="loginPrompt" class="login-banner glass-panel">
      <Bot :size="18" />
      <span>请先登录，再使用智能推荐与简历分析功能。</span>
    </div>

    <div v-if="error" class="error-banner glass-panel">{{ error }}</div>
    <div v-if="success" class="success-banner glass-panel">{{ success }}</div>

    <template v-if="!loginPrompt">
      <section class="recommend-grid top-grid">
        <PremiumCard :title="isStudent ? '学生专属行动建议' : (isAdmin ? '平台运营干预建议' : '教学辅导行动建议')" glowColor="primary">
          <div v-if="planLoading" class="loading-state">
            <div class="loader-ring"></div>
            <p>正在生成个性化行动方案...</p>
          </div>
          <div v-else class="advisory-panel">
            <div class="advisory-highlight">
              <Sparkles :size="18" />
              <div>
                <strong>{{ personalizedPlan?.advisory?.headline || '先完成一轮基础分析，再生成个性化建议。' }}</strong>
                <p>{{ personalizedPlan?.advisory?.summary || '系统会根据你的画像、技能和目标岗位给出更贴近学生求职阶段的建议。' }}</p>
              </div>
            </div>
            <div class="action-list">
              <div v-for="item in quickActions" :key="item" class="action-item">
                <span class="dot"></span>
                <span>{{ item }}</span>
              </div>
            </div>
            <div class="button-row">
              <GlowButton variant="ghost" @click="router.push('/reports')">查看报告中心</GlowButton>
              <GlowButton variant="ghost" @click="router.push('/ai')">进入 AI 助手</GlowButton>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard v-if="skillRadarOption || personalizedPlan?.skillGap?.missingCoreSkills?.length" title="能力图谱与缺口诊断" glowColor="teal">
          <div class="radar-container" style="display: flex; gap: 20px; flex-wrap: wrap;">
            <div v-if="skillRadarOption" style="flex: 1; min-width: 250px; height: 300px;">
              <VChart class="chart" :option="skillRadarOption" autoresize />
            </div>
            <div v-if="personalizedPlan?.skillGap?.missingCoreSkills?.length" style="flex: 1; min-width: 250px;">
              <h4 style="margin-top:0; color: var(--c-accent-secondary); display: flex; align-items: center; gap: 8px;">
                <AlertTriangle :size="16" /> 核心技能缺口预警
              </h4>
              <p style="color: var(--c-text-secondary); font-size: 13px;">这些是你目标岗位中极高频出现，但你目前简历尚未覆盖的技能：</p>
              <div class="job-tags" style="margin-top: 12px;">
                <span v-for="skill in personalizedPlan.skillGap.missingCoreSkills" :key="skill" class="tag" style="background: rgba(239, 68, 68, 0.15); color: #f87171;">
                  {{ skill }}
                </span>
              </div>
              <p style="color: var(--c-text-secondary); font-size: 13px; margin-top: 16px;">建议你优先将这些技能补齐，或在简历中突出你类似的项目经验。</p>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="简历导入与分析参数" glowColor="teal">
          <div class="upload-section">
            <input ref="fileInputRef" type="file" class="hidden-input" accept=".pdf,.png,.jpg,.jpeg,.txt" @change="handleParseResume">
            <button class="upload-button" @click="triggerFileUpload" :disabled="parsing">
              <Upload :size="28" class="upload-icon" />
              <strong>{{ parsing ? '正在解析简历...' : '上传简历文件' }}</strong>
              <p>支持 PDF、JPG、PNG、TXT。上传后会自动回填技能、学历和经历。</p>
            </button>
          </div>

          <div class="form-grid">
            <label class="field">
              <span><Target :size="14" /> 目标岗位</span>
              <input v-model="form.targetJobType" class="input-base" placeholder="例如：Java 开发、产品经理" />
            </label>
            <label class="field">
              <span><MapPin :size="14" /> 目标城市</span>
              <input v-model="form.targetCity" class="input-base" placeholder="例如：上海、深圳" />
            </label>
            <label class="field">
              <span><Briefcase :size="14" /> 行业方向</span>
              <input v-model="form.industry" class="input-base" placeholder="例如：互联网、金融科技" />
            </label>
            <label class="field">
              <span><GraduationCap :size="14" /> 学历</span>
              <select v-model="form.education" class="input-base">
                <option value="大专">大专</option>
                <option value="本科">本科</option>
                <option value="硕士">硕士</option>
                <option value="博士">博士</option>
              </select>
            </label>
            <label class="field">
              <span><Calculator :size="14" /> 经验年限</span>
              <input v-model="form.experienceYears" type="number" min="0" step="0.5" class="input-base" />
            </label>
            <label class="field full-width">
              <span><Sparkles :size="14" /> 核心技能</span>
              <textarea v-model="form.userSkills" rows="3" class="input-base" placeholder="例如：Java, Spring Boot, Vue, MySQL" />
            </label>
          </div>

          <GlowButton variant="primary" :loading="loading" @click="handleSmartAnalysis">一键生成求职分析</GlowButton>
        </PremiumCard>
      </section>

      <section v-if="results" class="recommend-grid result-grid">
        <PremiumCard title="求职诊断结论" glowColor="purple">
          <div class="insight-list">
            <div v-for="item in studentInsights" :key="item.title" class="insight-card">
              <div class="insight-head">
                <strong>{{ item.title }}</strong>
                <span class="pill">{{ item.summary }}</span>
              </div>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="简历匹配评分" glowColor="secondary">
          <div class="score-card">
            <div class="score-ring">
              <strong>{{ formatScore(results.score?.overall_score) }}</strong>
              <span>/100</span>
            </div>
            <div class="score-copy">
              <h3>评级：{{ results.score?.grade || '--' }}</h3>
              <p>{{ results.score?.summary || '暂无摘要' }}</p>
            </div>
          </div>
          <div v-if="results.score?.improvement_tips?.length" class="tips-list">
            <div v-for="tip in results.score.improvement_tips" :key="tip.tip" class="tip-item">
              <Lightbulb :size="16" />
              <span>{{ tip.tip }}</span>
              <strong>+{{ tip.expected_gain }}%</strong>
            </div>
          </div>
        </PremiumCard>

        <PremiumCard title="薪资预测与解释" glowColor="secondary">
          <div v-if="results.salary" class="salary-card">
            <strong class="salary-value">{{ formatMoney(results.salary.prediction) }}</strong>
            <span class="salary-unit">元 / 月</span>
            <p>
              预测区间：
              {{ formatMoney(results.salary?.confidenceInterval?.[0]) }}
              -
              {{ formatMoney(results.salary?.confidenceInterval?.[1]) }}
            </p>
            <p>{{ results.salary?.insights?.[0] || '系统已结合城市、技能和经验进行估算。' }}</p>
          </div>
          <div v-else class="empty-state">
            <p>暂未获取到薪资预测结果。</p>
          </div>
        </PremiumCard>

        <PremiumCard title="推荐岗位与机会判断" glowColor="teal" class="full-width">
          <div v-if="results.jobs?.length" class="jobs-grid">
            <div v-for="job in results.jobs" :key="job.id" class="job-card">
              <div class="job-head">
                <strong>{{ job.title }}</strong>
                <span class="job-salary">{{ job.salaryText || '薪资面议' }}</span>
              </div>
              <p>{{ job.companyName || '未知企业' }}</p>
              <div class="job-tags">
                <span class="tag">{{ job.city || '城市未知' }}</span>
                <span v-if="job.education" class="tag">{{ job.education }}</span>
                <span v-if="job.experience" class="tag">{{ job.experience }}</span>
              </div>
              <div class="job-reason">
                <span>匹配度 {{ formatPercent(job.score) }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <p>暂未找到高匹配岗位，建议调整目标岗位、城市或补充技能。</p>
          </div>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.recommend-page,
.recommend-grid,
.form-grid,
.action-list,
.tips-list,
.insight-list {
  display: grid;
  gap: 24px;
}

.top-grid {
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.15fr);
}

.result-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.full-width {
  grid-column: 1 / -1;
}

.login-banner,
.error-banner,
.success-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
}

.chart {
  width: 100%;
  height: 100%;
}

.login-banner {
  color: var(--c-accent-primary);
  border: 1px solid rgba(56, 189, 248, 0.22);
}

.error-banner {
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.22);
}

.success-banner {
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.22);
}

.advisory-panel,
.upload-section {
  display: grid;
  gap: 16px;
}

.advisory-highlight,
.action-item,
.tip-item,
.insight-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.advisory-highlight {
  grid-template-columns: 18px 1fr;
}

.action-item,
.tip-item {
  grid-template-columns: 16px 1fr auto;
}

.insight-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.advisory-highlight strong,
.insight-head strong,
.job-head strong {
  color: var(--c-text-primary);
}

.advisory-highlight p,
.upload-button p,
.score-copy p,
.salary-card p,
.job-card p,
.insight-card p {
  color: var(--c-text-secondary);
}

.dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 999px;
  background: var(--c-accent-primary);
}

.pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.button-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hidden-input {
  display: none;
}

.upload-button {
  width: 100%;
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 26px 18px;
  border-radius: var(--radius-lg);
  border: 1px dashed rgba(56, 189, 248, 0.32);
  background: rgba(255, 255, 255, 0.03);
  color: var(--c-text-primary);
}

.upload-icon {
  color: var(--c-accent-primary);
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--c-text-secondary);
}

.field.full-width {
  grid-column: 1 / -1;
}

.input-base {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

textarea.input-base {
  resize: vertical;
}

.score-card {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 20px;
  align-items: center;
}

.score-ring {
  width: 120px;
  height: 120px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: radial-gradient(circle at center, rgba(56, 189, 248, 0.18), rgba(56, 189, 248, 0.04));
  border: 1px solid rgba(56, 189, 248, 0.22);
}

.score-ring strong {
  font-size: 36px;
  line-height: 1;
}

.score-ring span {
  color: var(--c-text-muted);
}

.tip-item strong {
  color: var(--c-accent-teal);
}

.salary-card {
  display: grid;
  gap: 10px;
}

.salary-value {
  font-size: 40px;
  line-height: 1;
}

.salary-unit {
  color: var(--c-text-muted);
}

.jobs-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.job-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.job-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.job-salary {
  color: var(--c-accent-secondary);
  font-weight: 700;
}

.job-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
}

.job-reason {
  color: var(--c-text-secondary);
  font-size: 13px;
}

@media (max-width: 1100px) {
  .top-grid,
  .result-grid,
  .jobs-grid,
  .form-grid,
  .score-card {
    grid-template-columns: 1fr;
  }

  .full-width {
    grid-column: auto;
  }
}
</style>
