<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUpRight,
  Briefcase,
  Building2,
  DatabaseZap,
  Flame,
  LineChart,
  MapPin,
  ScrollText,
  Sparkles
} from 'lucide-vue-next'
import { fetchHotJobs, fetchOverview, fetchSkills } from '../api'

const router = useRouter()

const stats = ref(null)
const hotJobs = ref([])
const topSkills = ref([])
const isLoading = ref(true)
const taskDraft = ref('')

const quickPrompts = [
  {
    label: '上海后端趋势',
    prompt: '帮我分析上海 Java 后端岗位的近阶段需求和薪资区间。'
  },
  {
    label: '技能差距',
    prompt: '对比高级数据分析师岗位要求，告诉我还缺哪些关键技能。'
  },
  {
    label: '生成汇报结论',
    prompt: '把当前市场概览整理成适合周报汇报的 5 条结论。'
  },
  {
    label: '岗位匹配',
    prompt: '根据我已有的 Java、Spring Boot、MySQL 经验，推荐更合适的岗位方向。'
  }
]

const workflowEntries = [
  {
    title: 'AI 助手',
    desc: '从自然语言任务直接发起分析。',
    path: '/ai',
    icon: Sparkles
  },
  {
    title: '洞察分析',
    desc: '查看城市、行业与岗位结构变化。',
    path: '/insights',
    icon: LineChart
  },
  {
    title: '分析报告',
    desc: '把结果收敛成可导出的正式输出。',
    path: '/reports',
    icon: ScrollText
  },
  {
    title: '数据采集',
    desc: '维护职位样本来源和同步节奏。',
    path: '/crawler',
    icon: DatabaseZap
  }
]

onMounted(async () => {
  try {
    const [overview, jobs, skills] = await Promise.all([
      fetchOverview(),
      fetchHotJobs(8),
      fetchSkills(14)
    ])

    stats.value = overview
    hotJobs.value = jobs
    topSkills.value = skills
  } catch (error) {
    console.error('加载概览数据失败', error)
  } finally {
    isLoading.value = false
  }
})

const topCity = computed(() => stats.value?.topCities?.[0])
const topIndustry = computed(() => stats.value?.topIndustries?.[0])

const topCityRows = computed(() => (stats.value?.topCities || []).slice(0, 5).map((item) => ({
  label: item.city,
  count: item.count
})))

const topIndustryRows = computed(() => (stats.value?.topIndustries || []).slice(0, 5).map((item) => ({
  label: item.industryName || item.industry,
  count: item.count
})))

const cityMax = computed(() => Math.max(...topCityRows.value.map((item) => item.count), 1))
const industryMax = computed(() => Math.max(...topIndustryRows.value.map((item) => item.count), 1))

const overviewCards = computed(() => [
  {
    label: '岗位总量',
    value: formatNumber(stats.value?.totalJobs) || '等待数据',
    note: '当前在库岗位样本',
    tone: 'primary'
  },
  {
    label: '平均薪资区间',
    value: formatSalaryRange(),
    note: '按岗位样本估算的月薪范围',
    tone: 'neutral'
  },
  {
    label: '最热城市',
    value: topCity.value?.city || '等待数据',
    note: topCity.value ? `${formatNumber(topCity.value.count)} 个岗位` : '等待数据',
    tone: 'neutral'
  },
  {
    label: '核心行业',
    value: topIndustry.value?.industryName || topIndustry.value?.industry || '等待数据',
    note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 个岗位` : '等待数据',
    tone: 'neutral'
  }
])

const marketLead = computed(() => {
  if (!topCity.value?.city) {
    return '市场样本同步后，这里会优先展示当前最活跃城市与行业焦点。'
  }

  return `${topCity.value.city} 仍是当前最活跃的岗位市场，需求集中度保持领先。`
})

const marketSummary = computed(() => {
  const industry = topIndustry.value?.industryName || topIndustry.value?.industry
  const salary = formatSalaryRange()

  if (industry && salary !== '等待数据') {
    return `需求主要聚焦在 ${industry}，当前市场平均薪资区间约为 ${salary}。`
  }

  if (industry) {
    return `需求主要聚焦在 ${industry}，薪资区间会在更多样本同步后继续收敛。`
  }

  return '岗位规模、城市热度和行业分布会随样本同步后持续更新。'
})

function formatNumber(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toLocaleString('zh-CN')
  }

  if (value === 0) {
    return '0'
  }

  return null
}

function formatSalaryRange() {
  const min = stats.value?.avgSalaryMin
  const max = stats.value?.avgSalaryMax

  if (typeof min === 'number' && typeof max === 'number') {
    return `${min.toFixed(1)}K - ${max.toFixed(1)}K`
  }

  return '等待数据'
}

function openPath(path) {
  router.push(path)
}

function openAiWithDraft(draft = '') {
  const trimmed = draft.trim()
  router.push({
    path: '/ai',
    query: trimmed ? { draft: trimmed } : undefined
  })
}

function submitTask() {
  openAiWithDraft(taskDraft.value)
}

function getBarWidth(count, max) {
  return `${Math.max((count / max) * 100, 8)}%`
}
</script>

<template>
  <div class="dashboard-home page-shell">
    <section class="task-stage">
      <div class="task-stage-head">
        <span class="panel-eyebrow">Workspace</span>
        <h2>今天想推进哪项职涯任务？</h2>
        <p>从岗位趋势、技能差距到汇报结论，把入口收敛成一个任务操作台。</p>
      </div>

      <form class="task-composer" @submit.prevent="submitTask">
        <textarea
          v-model="taskDraft"
          rows="4"
          class="task-input"
          placeholder="例如：帮我分析上海 Java 后端岗位趋势，并整理成适合周报汇报的结论。"
        />

        <div class="task-composer-bar">
          <div class="task-hints">
            <span>进入 AI 工作台后会自动带上这段草稿。</span>
          </div>
          <button class="task-submit" type="submit">
            <Sparkles :size="16" />
            进入 AI 工作台
          </button>
        </div>
      </form>

      <div class="task-prompt-row">
        <button
          v-for="prompt in quickPrompts"
          :key="prompt.label"
          class="task-prompt-chip"
          type="button"
          @click="openAiWithDraft(prompt.prompt)"
        >
          {{ prompt.label }}
        </button>
      </div>

      <div class="workflow-strip">
        <button
          v-for="entry in workflowEntries"
          :key="entry.title"
          class="workflow-entry"
          type="button"
          @click="openPath(entry.path)"
        >
          <component :is="entry.icon" :size="17" />
          <div class="workflow-copy">
            <strong>{{ entry.title }}</strong>
            <span>{{ entry.desc }}</span>
          </div>
          <ArrowUpRight :size="15" />
        </button>
      </div>
    </section>

    <div v-if="isLoading" class="dashboard-loading workspace-panel">
      <div class="loader-dot"></div>
      <p>正在同步首页数据...</p>
    </div>

    <template v-else-if="stats">
      <section class="workspace-panel overview-panel">
        <div class="panel-header">
          <div>
            <span class="panel-eyebrow">Overview</span>
            <h3>今日市场概览</h3>
          </div>
          <button class="panel-link" type="button" @click="openPath('/insights')">查看完整洞察</button>
        </div>

        <div class="overview-grid">
          <article
            v-for="card in overviewCards"
            :key="card.label"
            class="overview-card"
            :class="card.tone"
          >
            <span class="overview-label">{{ card.label }}</span>
            <strong class="overview-value">{{ card.value }}</strong>
            <span class="overview-note">{{ card.note }}</span>
          </article>
        </div>
      </section>

      <div class="content-grid">
        <section class="workspace-panel summary-panel">
          <div class="panel-header">
            <div>
              <span class="panel-eyebrow">Signal</span>
              <h3>市场热度与结构</h3>
            </div>
          </div>

          <div class="summary-hero">
            <strong>{{ topCity?.city || '等待数据' }}</strong>
            <p>{{ marketLead }}</p>
            <span>{{ marketSummary }}</span>
          </div>

          <div class="summary-columns">
            <div class="summary-block">
              <div class="summary-block-head">
                <span>城市热度</span>
                <button type="button" class="panel-link subtle" @click="openPath('/jobs')">看岗位</button>
              </div>
              <div class="summary-list">
                <div v-for="item in topCityRows" :key="item.label" class="summary-row">
                  <div class="summary-row-meta">
                    <span>{{ item.label }}</span>
                    <strong>{{ formatNumber(item.count) }}</strong>
                  </div>
                  <div class="summary-bar-track">
                    <div class="summary-bar-fill" :style="{ width: getBarWidth(item.count, cityMax) }"></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="summary-block">
              <div class="summary-block-head">
                <span>行业分布</span>
                <button type="button" class="panel-link subtle" @click="openPath('/insights')">看分析</button>
              </div>
              <div class="summary-list">
                <div v-for="item in topIndustryRows" :key="item.label" class="summary-row">
                  <div class="summary-row-meta">
                    <span>{{ item.label }}</span>
                    <strong>{{ formatNumber(item.count) }}</strong>
                  </div>
                  <div class="summary-bar-track">
                    <div class="summary-bar-fill soft" :style="{ width: getBarWidth(item.count, industryMax) }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="workspace-panel jobs-panel">
          <div class="panel-header">
            <div>
              <span class="panel-eyebrow">Jobs</span>
              <h3>热门岗位样本</h3>
            </div>
            <button class="panel-link" type="button" @click="openPath('/jobs')">查看全部</button>
          </div>

          <div class="job-list">
            <button
              v-for="job in hotJobs"
              :key="job.id"
              class="job-row"
              type="button"
              @click="openPath('/jobs')"
            >
              <div class="job-row-copy">
                <strong>{{ job.title }}</strong>
                <div class="job-row-meta">
                  <span><Building2 :size="12" /> {{ job.companyName }}</span>
                  <span><MapPin :size="12" /> {{ job.city }}</span>
                </div>
              </div>
              <span class="job-row-salary">{{ job.salaryText || '面议' }}</span>
            </button>
          </div>
        </section>
      </div>

      <div class="content-grid utility-grid">
        <section class="workspace-panel skill-panel">
          <div class="panel-header">
            <div>
              <span class="panel-eyebrow">Skills</span>
              <h3>技能热点</h3>
            </div>
            <button class="panel-link" type="button" @click="openPath('/insights')">技能图谱</button>
          </div>

          <div class="skill-cloud">
            <span
              v-for="(skill, index) in topSkills"
              :key="skill.skill"
              class="skill-chip"
              :class="{ hot: index < 3 }"
            >
              <Flame v-if="index < 3" :size="12" />
              {{ skill.skill }}
              <small>{{ skill.count }}</small>
            </span>
          </div>
        </section>

        <section class="workspace-panel route-panel">
          <div class="panel-header">
            <div>
              <span class="panel-eyebrow">Actions</span>
              <h3>常用工作流</h3>
            </div>
          </div>

          <div class="route-list">
            <button
              v-for="entry in workflowEntries"
              :key="entry.title"
              class="route-row"
              type="button"
              @click="openPath(entry.path)"
            >
              <component :is="entry.icon" :size="16" />
              <div class="route-row-copy">
                <strong>{{ entry.title }}</strong>
                <span>{{ entry.desc }}</span>
              </div>
              <ArrowUpRight :size="14" />
            </button>
          </div>
        </section>
      </div>
    </template>

    <div v-else class="workspace-empty workspace-panel">
      <Briefcase :size="22" />
      <p>暂无概览数据，请确认后端服务与数据库已正常启动。</p>
    </div>
  </div>
</template>

<style scoped>
.dashboard-home {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 24px;
}

.workspace-panel {
  border: 1px solid var(--c-border-strong);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-panel);
}

.task-stage {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  padding: clamp(26px, 5vw, 54px) clamp(18px, 4vw, 42px);
  text-align: center;
  border: 1px solid var(--c-border-strong);
  border-radius: 24px;
  background:
    radial-gradient(circle at top, rgba(79, 108, 156, 0.06), transparent 32%),
    rgba(255, 255, 255, 0.92);
  box-shadow: var(--shadow-panel);
}

.task-stage-head {
  display: flex;
  max-width: 760px;
  flex-direction: column;
  gap: 8px;
}

.panel-eyebrow {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-faint);
}

.task-stage-head h2,
.panel-header h3 {
  margin: 0;
  font-size: clamp(28px, 4vw, 38px);
  line-height: 1.08;
}

.task-stage-head p {
  color: var(--c-text-muted);
  font-size: 15px;
}

.task-composer {
  display: flex;
  width: min(100%, 820px);
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid var(--c-border-strong);
  background: #ffffff;
}

.task-input {
  min-height: 132px;
  resize: vertical;
  color: var(--c-text-primary);
  font-size: 16px;
  line-height: 1.7;
}

.task-input::placeholder {
  color: var(--c-text-faint);
}

.task-composer-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--c-text-muted);
  font-size: 13px;
}

.task-submit {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border-radius: 14px;
  background: #161616;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
}

.task-prompt-row {
  display: flex;
  width: min(100%, 880px);
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.task-prompt-chip {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-text-secondary);
  font-size: 13px;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.task-prompt-chip:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.workflow-strip {
  display: grid;
  width: min(100%, 980px);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.workflow-entry {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  text-align: left;
  border-radius: 16px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.82);
  color: var(--c-text-primary);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.workflow-entry:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  transform: translateY(-1px);
}

.workflow-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.workflow-copy strong {
  font-size: 14px;
}

.workflow-copy span {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.dashboard-loading,
.workspace-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 120px;
  color: var(--c-text-muted);
}

.loader-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: var(--c-accent-primary);
  box-shadow: 18px 0 0 rgba(41, 85, 155, 0.45), -18px 0 0 rgba(41, 85, 155, 0.22);
  animation: pulse-dot 1s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% {
    transform: scale(0.88);
    opacity: 0.72;
  }
  50% {
    transform: scale(1);
    opacity: 1;
  }
}

.overview-panel,
.summary-panel,
.jobs-panel,
.skill-panel,
.route-panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.panel-header h3 {
  font-size: 22px;
}

.panel-link {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.8);
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.panel-link.subtle {
  min-height: auto;
  padding: 0;
  border: none;
  background: transparent;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 16px;
  background: var(--c-bg-surface-hover);
  border: 1px solid transparent;
}

.overview-card.primary {
  background: #161616;
  color: #ffffff;
}

.overview-card.primary .overview-note,
.overview-card.primary .overview-label {
  color: rgba(255, 255, 255, 0.72);
}

.overview-label {
  font-size: 12px;
  color: var(--c-text-muted);
}

.overview-value {
  font-size: 28px;
  line-height: 1.05;
}

.overview-note {
  color: var(--c-text-muted);
  font-size: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 18px;
}

.summary-hero {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
  margin-bottom: 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(247, 245, 238, 0.96), rgba(240, 238, 230, 0.92));
}

.summary-hero strong {
  font-size: 30px;
  line-height: 1;
}

.summary-hero p {
  font-size: 15px;
  color: var(--c-text-primary);
}

.summary-hero span {
  color: var(--c-text-muted);
  font-size: 13px;
}

.summary-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-secondary);
}

.summary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-row-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.summary-row-meta span {
  color: var(--c-text-secondary);
}

.summary-row-meta strong {
  color: var(--c-text-primary);
  font-size: 12px;
}

.summary-bar-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--c-bg-surface-active);
}

.summary-bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1f4c8f, #4d79b8);
}

.summary-bar-fill.soft {
  background: linear-gradient(90deg, #4d5870, #8b95aa);
}

.job-list,
.route-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.job-row,
.route-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  text-align: left;
  border-top: 1px solid rgba(216, 211, 196, 0.72);
  color: var(--c-text-primary);
}

.job-row:first-child,
.route-row:first-child {
  padding-top: 0;
  border-top: none;
}

.job-row-copy,
.route-row-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.job-row-copy strong,
.route-row-copy strong {
  font-size: 14px;
}

.job-row-meta,
.route-row-copy span {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.job-row-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.job-row-salary {
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.skill-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-size: 13px;
}

.skill-chip.hot {
  background: rgba(41, 85, 155, 0.1);
  color: var(--c-accent-primary);
}

.skill-chip small {
  opacity: 0.72;
  font-size: 11px;
}

@media (max-width: 1120px) {
  .workflow-strip,
  .overview-grid,
  .summary-columns,
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .utility-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .task-stage {
    align-items: stretch;
    text-align: left;
    padding: 22px 18px;
  }

  .task-composer,
  .workflow-strip,
  .task-prompt-row {
    width: 100%;
  }

  .task-composer-bar,
  .summary-columns,
  .content-grid,
  .workflow-strip,
  .overview-grid {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .task-composer-bar {
    align-items: stretch;
  }

  .task-submit {
    justify-content: center;
  }

  .overview-panel,
  .summary-panel,
  .jobs-panel,
  .skill-panel,
  .route-panel {
    padding: 16px;
  }

  .panel-header h3,
  .task-stage-head h2 {
    font-size: 24px;
  }
}
</style>
