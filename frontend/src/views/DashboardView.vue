<script setup>
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  BadgeDollarSign,
  Briefcase,
  Building2,
  DatabaseZap,
  Flame,
  GraduationCap,
  LineChart,
  MapPin,
  ScrollText,
  Sparkles
} from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
import RankingList from '../components/dashboard/RankingList.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchOverview, fetchHotJobs, fetchSkills } from '../api'

const router = useRouter()
const stats = ref(null)
const hotJobs = ref([])
const topSkills = ref([])
const isLoading = ref(true)
const heroTitleFull = '职业能力大数据服务平台'
const heroTitleDisplay = ref('')
const heroTitleDone = ref(false)

let typingTimer = 0

const quickEntries = [
  {
    title: '院校深度分析',
    desc: '查看供需诊断、趋势预测和数仓分析结果。',
    badge: '深度分析',
    path: '/insights',
    icon: LineChart
  },
  {
    title: '技能图谱与分析',
    desc: '查看技能热度、岗位技能结构和知识图谱入口。',
    badge: '图谱入口',
    path: '/insights',
    icon: Sparkles
  },
  {
    title: '报告中心',
    desc: '集中查看、生成并导出各维度的职业分析报告。',
    badge: '核心能力',
    path: '/reports',
    icon: GraduationCap
  }
]

const heroShowcaseCards = [
  {
    title: '数据采集',
    meta: '23 个来源',
    desc: '多站点岗位、薪资、技能词实时汇聚。',
    path: '/crawler',
    icon: DatabaseZap,
    tone: 'primary',
    layer: 'layer-one'
  },
  {
    title: '洞察分析',
    meta: '趋势诊断',
    desc: '按城市、行业、学历与技能结构拆解。',
    path: '/insights',
    icon: LineChart,
    tone: 'secondary',
    layer: 'layer-two'
  },
  {
    title: '智能推荐',
    meta: '91% 匹配',
    desc: '从简历画像到岗位建议的闭环推荐。',
    path: '/recommend',
    icon: Sparkles,
    tone: 'accent',
    layer: 'layer-three'
  },
  {
    title: '报告中心',
    meta: '一键导出',
    desc: '院校分析、趋势报告与评估结论集中生成。',
    path: '/reports',
    icon: ScrollText,
    tone: 'glass',
    layer: 'layer-four'
  }
]

onMounted(async () => {
  let index = 0
  typingTimer = window.setInterval(() => {
    index += 1
    heroTitleDisplay.value = heroTitleFull.slice(0, index)
    if (index >= heroTitleFull.length) {
      heroTitleDone.value = true
      window.clearInterval(typingTimer)
    }
  }, 110)

  try {
    const [overview, jobs, skills] = await Promise.all([
      fetchOverview(),
      fetchHotJobs(8),
      fetchSkills(12)
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

onBeforeUnmount(() => {
  window.clearInterval(typingTimer)
})

const topCity = computed(() => stats.value?.topCities?.[0])
const topIndustry = computed(() => stats.value?.topIndustries?.[0])
const topCityRows = computed(() => (stats.value?.topCities || []).slice(0, 10).map((item) => ({
  label: item.city,
  count: item.count
})))
const topIndustryRows = computed(() => (stats.value?.topIndustries || []).slice(0, 10).map((item) => ({
  label: item.industryName || item.industry,
  count: item.count
})))
const formattedTotalJobs = computed(() => formatNumber(stats.value?.totalJobs) || '等待数据')
const formattedSalaryRange = computed(() => {
  const min = formatSalaryValue(stats.value?.avgSalaryMin)
  const max = formatSalaryValue(stats.value?.avgSalaryMax)

  return min && max ? `${min}~${max}K` : '等待数据'
})
const primaryIndustryLabel = computed(() => topIndustry.value?.industryName || topIndustry.value?.industry || '等待数据')

function formatNumber(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toLocaleString('zh-CN')
  }

  if (value === 0) {
    return '0'
  }

  return null
}

function formatSalaryValue(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toFixed(2)
  }

  return null
}

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  if (!element) return

  const scrollContainer = document.querySelector('.page-container')

  if (scrollContainer instanceof HTMLElement) {
    const containerRect = scrollContainer.getBoundingClientRect()
    const elementRect = element.getBoundingClientRect()
    const targetTop = scrollContainer.scrollTop + elementRect.top - containerRect.top - 12

    scrollContainer.scrollTo({
      top: Math.max(targetTop, 0),
      behavior: 'smooth'
    })
    return
  }

  const targetTop = element.getBoundingClientRect().top + window.scrollY - 12
  window.scrollTo({ top: Math.max(targetTop, 0), behavior: 'smooth' })
}
</script>

<template>
  <div class="dashboard-layout page-shell">
    <section class="hero-banner">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="hero-title-typewriter" :class="{ done: heroTitleDone }">
            {{ heroTitleDisplay }}
          </span>
        </h1>
        <p class="hero-subtitle">
          把采集、洞察、推荐和报告串成一条可操作的路径。
        </p>
        <button class="hero-scroll-hint" type="button" @click="scrollToSection('dashboard-quick')">
          <span class="hero-scroll-kicker">继续浏览</span>
          <span>查看工作区入口与市场结构</span>
          <ArrowRight :size="15" />
        </button>
        <div class="hero-motion-stage" aria-label="平台能力概览">
          <button
            v-for="card in heroShowcaseCards"
            :key="card.title"
            class="hero-float-card"
            :class="[card.layer, `tone-${card.tone}`]"
            type="button"
            @click="router.push(card.path)"
          >
            <div class="hero-float-card-top">
              <component :is="card.icon" :size="18" stroke-width="2" />
              <span>{{ card.meta }}</span>
            </div>
            <strong>{{ card.title }}</strong>
            <p>{{ card.desc }}</p>
            <span class="hero-float-link">进入模块 <ArrowRight :size="14" /></span>
          </button>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-side-head">
          <span class="hero-side-label">核心指标</span>
        </div>
        <div class="hero-kpi-stack" role="list" aria-label="核心指标">
          <div class="kpi-tile" role="listitem">
            <span class="kpi-eyebrow">
              <BadgeDollarSign class="kpi-icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
              平均薪资
            </span>
            <strong class="kpi-value kpi-value-grad">{{ formattedSalaryRange }}</strong>
          </div>
          <div class="kpi-tile" role="listitem">
            <span class="kpi-eyebrow">
              <Briefcase class="kpi-icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
              在库岗位
            </span>
            <strong class="kpi-value">{{ formattedTotalJobs }}<span class="kpi-value-unit">条</span></strong>
          </div>
          <div class="kpi-tile kpi-tile-text" role="listitem">
            <span class="kpi-eyebrow">
              <Building2 class="kpi-icon" :size="16" :stroke-width="1.75" aria-hidden="true" />
              核心行业
            </span>
            <strong class="kpi-value kpi-value-text">{{ primaryIndustryLabel }}</strong>
          </div>
        </div>
      </div>
      <div class="hero-glass-orb orb-primary"></div>
      <div class="hero-glass-orb orb-secondary"></div>
    </section>

    <div v-if="isLoading" class="skeleton-dashboard">
      <div class="kpi-grid">
        <SkeletonCard type="stat" v-for="i in 4" :key="i" />
      </div>
      <div class="entry-strip">
        <SkeletonCard type="card" :lines="2" v-for="i in 3" :key="i" />
      </div>
      <div class="content-grid">
        <SkeletonCard type="list" :lines="5" />
        <SkeletonCard type="list" :lines="5" />
      </div>
    </div>

    <template v-else-if="stats">
      <section id="dashboard-quick" class="surface section-panel workspace-module-panel dashboard-section-panel">
        <div class="panel-head workspace-panel-head">
          <div class="workspace-panel-copy">
            <h2 class="workspace-panel-title inline-icon"><Sparkles :size="15" /> 高频入口</h2>
          </div>
        </div>

        <TransitionGroup name="list" tag="div" class="entry-strip">
          <PremiumCard v-for="entry in quickEntries" :key="entry.path" :title="entry.title" glowColor="primary" class="entry-card" padding="28px">
            <div class="entry-card-body" @click="router.push(entry.path)">
              <div class="entry-top">
                <component :is="entry.icon" :size="24" />
                <span class="entry-badge">{{ entry.badge }}</span>
              </div>
              <p>{{ entry.desc }}</p>
              <span class="entry-link">进入模块 <ArrowRight :size="16" /></span>
            </div>
          </PremiumCard>
        </TransitionGroup>
      </section>

      <section id="dashboard-market" class="surface section-panel workspace-module-panel dashboard-section-panel">
        <div class="panel-head workspace-panel-head">
          <div class="workspace-panel-copy">
            <h2 class="workspace-panel-title inline-icon"><LineChart :size="15" /> 市场结构</h2>
          </div>
        </div>

        <div class="content-grid">
          <div class="left-column">
            <RankingList title="城市岗位分布 TOP 10" glowColor="teal" :items="topCityRows" />

            <RankingList title="行业需求 TOP 10" glowColor="purple" :items="topIndustryRows" />
          </div>

          <div class="right-column">
            <PremiumCard title="热门岗位" glowColor="secondary">
              <div class="hot-jobs-list">
                <div v-for="job in hotJobs" :key="job.id" class="hot-job-item" @click="router.push('/jobs')">
                  <div class="job-info">
                    <h4 class="job-title-text">{{ job.title }}</h4>
                    <div class="job-sub">
                      <span>{{ job.companyName }}</span>
                      <span class="dot">·</span>
                      <span><MapPin :size="12" /> {{ job.city }}</span>
                      <span class="dot">·</span>
                      <span><Building2 :size="12" /> 岗位样本</span>
                    </div>
                  </div>
                  <span class="job-salary-badge">{{ job.salaryText || '面议' }}</span>
                </div>
              </div>
              <div class="card-footer-action" @click="router.push('/jobs')">
                查看全部岗位 <ArrowRight :size="14" />
              </div>
            </PremiumCard>

            <PremiumCard title="技能热度榜" glowColor="primary">
              <div class="skill-tags">
                <span v-for="(skill, i) in topSkills" :key="skill.skill" class="skill-chip" :class="{ hot: i < 3 }">
                  <Flame v-if="i < 3" :size="12" />
                  {{ skill.skill }}
                  <small>{{ skill.count }}</small>
                </span>
              </div>
            </PremiumCard>
          </div>
        </div>
      </section>
    </template>

    <div v-else class="empty-state-wrapper glass-panel">
      <EmptyState icon="error" title="无概览数据" description="暂无概览数据，请确认后端服务与数据库已正常启动。" />
    </div>
  </div>
</template>

<style scoped>
.dashboard-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
.section-panel {
  gap: 18px;
}
.hero-banner {
  position: relative;
  overflow: hidden;
  padding: 36px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.05), transparent 28%),
    var(--c-bg-surface);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid var(--c-border-strong);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: stretch;
  box-shadow: var(--shadow-panel);
  isolation: isolate;
}
.hero-content { position: relative; z-index: 2; display: flex; flex-direction: column; justify-content: center; max-width: 740px; }
.hero-kicker {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  margin-bottom: 24px;
  border-radius: 999px;
  background: rgba(217, 226, 255, 0.9);
  border: 1px solid rgba(0, 89, 199, 0.12);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  width: fit-content;
}
.hero-title {
  font-family: var(--font-display); 
  font-size: clamp(30px, 4.2vw, 44px); 
  font-weight: 700; 
  margin-bottom: 22px; 
  color: var(--c-text-primary);
  line-height: 1.1;
  letter-spacing: -0.03em;
}
.hero-title-typewriter {
  display: inline-block;
  position: relative;
  border-right: 2px solid rgba(17, 24, 39, 0.92);
  padding-right: 6px;
  min-height: 1.1em;
  white-space: nowrap;
  animation: hero-caret 900ms steps(1, end) infinite;
}
.hero-title-typewriter::after {
  content: '';
  position: absolute;
  right: -1px;
  bottom: 0.06em;
  width: 22px;
  height: 2px;
  background: rgba(17, 24, 39, 0.92);
  transform: translateX(100%);
  animation: hero-caret 900ms steps(1, end) infinite;
}
@keyframes hero-caret {
  0%, 45% { border-right-color: rgba(17, 24, 39, 0.92); }
  46%, 100% { border-right-color: transparent; }
}
.hero-title-typewriter.done {
  border-right-color: rgba(17, 24, 39, 0.92);
  animation: hero-caret 900ms steps(1, end) infinite;
}
.hero-title-typewriter.done::after {
  background: rgba(17, 24, 39, 0.92);
}
.hero-subtitle { font-size: 16px; color: var(--c-text-secondary); line-height: 1.7; margin-bottom: 18px; max-width: 520px; }
.hero-scroll-hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  padding: 0;
  margin-bottom: 6px;
  border: none;
  background: transparent;
  color: var(--c-text-secondary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
}
.hero-scroll-hint:hover {
  color: var(--c-accent-primary);
  transform: translateX(2px);
}
.hero-scroll-kicker {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(217, 226, 255, 0.82);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.hero-motion-stage {
  position: relative;
  width: min(100%, 700px);
  height: 344px;
  margin-top: 30px;
  perspective: 1600px;
}
.hero-motion-stage::before {
  content: '';
  position: absolute;
  inset: 52px 34px 36px;
  border-radius: 28px;
  background:
    radial-gradient(circle at 18% 28%, rgba(0, 89, 199, 0.12), transparent 30%),
    radial-gradient(circle at 84% 72%, rgba(66, 93, 151, 0.1), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(236, 241, 255, 0.12));
  box-shadow: none;
  filter: blur(2px);
  opacity: 0.9;
}
.hero-float-card {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 20px 18px;
  text-align: left;
  color: var(--c-text-primary);
  border: 1px solid rgba(193, 198, 215, 0.8);
  border-radius: 16px;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: var(--shadow-card-soft);
  transform-style: preserve-3d;
  transform: rotateY(-18deg) rotateX(16deg) translate3d(0, 0, 0);
  transition:
    transform 700ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 300ms var(--ease-out),
    border-color 300ms var(--ease-out),
    background 300ms var(--ease-out);
  cursor: pointer;
}
.hero-motion-stage:hover .hero-float-card.layer-one { transform: rotateY(-10deg) rotateX(10deg) translate3d(-36px, -28px, 0); }
.hero-motion-stage:hover .hero-float-card.layer-two { transform: rotateY(-8deg) rotateX(10deg) translate3d(8px, -32px, 0); }
.hero-motion-stage:hover .hero-float-card.layer-three { transform: rotateY(-6deg) rotateX(8deg) translate3d(16px, 0, 0); }
.hero-motion-stage:hover .hero-float-card.layer-four { transform: rotateY(-4deg) rotateX(8deg) translate3d(58px, 26px, 0); }
.hero-float-card:hover,
.hero-float-card:focus-visible {
  box-shadow: var(--shadow-card-raised);
  border-color: rgba(0, 89, 199, 0.24);
  outline: none;
}
.hero-float-card:hover { transform: rotateY(-6deg) rotateX(8deg) translate3d(0, -6px, 0) scale(1.015); }
.hero-float-card-top {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--c-text-muted);
}
.hero-float-card strong {
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1.05;
  letter-spacing: -0.03em;
}
.hero-float-card p {
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.hero-float-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
}
.hero-float-card.layer-one {
  top: 0;
  left: 10px;
  width: 228px;
  min-height: 146px;
}
.hero-float-card.layer-two {
  top: 20px;
  left: 254px;
  width: 240px;
  min-height: 156px;
}
.hero-float-card.layer-three {
  top: 126px;
  left: 118px;
  width: 336px;
  min-height: 168px;
}
.hero-float-card.layer-four {
  top: 160px;
  left: 430px;
  width: 208px;
  min-height: 142px;
}
.hero-float-card.tone-primary {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.95), rgba(236, 243, 255, 0.9)),
    rgba(255, 255, 255, 0.88);
}
.hero-float-card.tone-secondary {
  background:
    linear-gradient(160deg, rgba(241, 246, 255, 0.96), rgba(224, 235, 255, 0.88)),
    rgba(255, 255, 255, 0.88);
}
.hero-float-card.tone-accent {
  background:
    linear-gradient(145deg, rgba(0, 89, 199, 0.92), rgba(40, 121, 243, 0.86)),
    rgba(0, 89, 199, 0.9);
  color: #fff;
  border-color: rgba(0, 89, 199, 0.18);
}
.hero-float-card.tone-accent .hero-float-card-top,
.hero-float-card.tone-accent p {
  color: rgba(255, 255, 255, 0.78);
}
.hero-float-card.tone-accent .hero-float-link {
  color: #fff;
}
.hero-float-card.tone-glass {
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.86), rgba(248, 250, 255, 0.82)),
    rgba(255, 255, 255, 0.78);
}
.hero-side {
  position: relative;
  z-index: 2;
  padding: 12px 0 0 22px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 100%;
  color: var(--c-text-primary);
  background: transparent;
  border: none;
  border-left: 1px solid rgba(193, 198, 215, 0.48);
  border-radius: 0;
  box-shadow: none;
}
.hero-side-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 4px;
}
.hero-side-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.hero-side-caption {
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.65;
}
.hero-kpi-stack {
  display: flex;
  flex-direction: column;
}
.kpi-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 0;
  background: transparent;
}
.kpi-tile + .kpi-tile {
  border-top: 1px solid rgba(193, 198, 215, 0.4);
}
.kpi-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: #5f6576;
}
.kpi-eyebrow .kpi-icon {
  color: #0057c2;
}
.kpi-value {
  font-family: var(--font-serif);
  font-feature-settings: 'tnum' 1;
  font-variant-numeric: tabular-nums;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.025em;
  color: #181b23;
}
.kpi-value-unit {
  font-size: 0.55em;
  font-weight: 500;
  color: var(--c-text-muted);
  margin-left: 3px;
  letter-spacing: normal;
}
.kpi-value-grad {
  background: linear-gradient(135deg, #0057c2 0%, #006ef2 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.kpi-value-text {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.hero-glass-orb { position: absolute; border-radius: 50%; filter: blur(28px); opacity: 0.18; }
.orb-primary { top: -24px; right: 10%; width: 220px; height: 220px; background: radial-gradient(circle, rgba(0, 89, 199, 0.4), transparent 70%); }
.orb-secondary { left: 4%; bottom: -70px; width: 260px; height: 260px; background: radial-gradient(circle, rgba(175, 198, 255, 0.6), transparent 72%); }
.pulse-dot {
  display: inline-block; width: 8px; height: 8px; background: #fff; border-radius: 50%; margin-right: 6px;
  box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7); animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(255, 255, 255, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}
.dashboard-section-panel {
  border-color: rgba(0, 89, 199, 0.12);
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 250, 255, 0.86)),
    var(--c-bg-surface);
}

.dashboard-section-panel .workspace-panel-title {
  color: var(--c-accent-primary);
}

.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; }
.entry-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.entry-card {
  min-height: 192px;
  transition:
    transform 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    box-shadow 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    border-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    background-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}
.entry-card :deep(.card-title) {
  transition: color 180ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}
.entry-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-border-glass-hover);
  background: var(--c-bg-surface-hover);
  box-shadow: var(--shadow-card-raised);
}
.entry-card:hover :deep(.card-title) {
  color: var(--c-accent-primary);
}
.entry-card-body { display: flex; flex-direction: column; gap: 18px; height: 100%; cursor: pointer; }
.entry-top { display: flex; align-items: center; justify-content: space-between; color: var(--c-text-primary); }
.entry-badge {
  padding: 6px 12px; border-radius: 999px; background: rgba(217, 226, 255, 0.9);
  border: 1px solid rgba(0, 89, 199, 0.1); color: var(--c-accent-primary); font-size: 13px; font-weight: 600;
}
.entry-card p { color: var(--c-text-secondary); line-height: 1.8; flex: 1; font-size: 15px; }
.entry-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--c-accent-primary);
  font-size: 15px;
  font-weight: 600;
  /* Hidden by default; fade in on hover. */
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    transform 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
  /* Pill-style look mirroring JobCard's floating footer. */
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(0, 87, 194, 0.22);
  background: rgba(0, 87, 194, 0.06);
  align-self: flex-start;
}
/* Match JobCard's interaction: on entry-card hover, fade out the
   descriptive paragraph and fade in the "进入模块" pill. */
.entry-card:hover .entry-link,
.entry-card:focus-within .entry-link {
  opacity: 1;
  transform: translateY(0);
}
.entry-card :deep(.card-body) p,
.entry-card p {
  transition: opacity 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}
.entry-card:hover p {
  opacity: 0.45;
}
.content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.left-column, .right-column { display: flex; flex-direction: column; gap: 24px; }
.hot-jobs-list { display: flex; flex-direction: column; gap: 2px; }
.hot-job-item {
  display: flex; justify-content: space-between; align-items: center; padding: 14px 12px;
  border-radius: var(--radius-sm); cursor: pointer; transition: background var(--duration-fast);
}
.hot-job-item:hover { background: var(--c-bg-surface-hover); }
.job-info { flex: 1; min-width: 0; }
.job-title-text { font-size: 15px; font-weight: 600; margin: 0 0 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.job-sub { font-size: 13px; color: var(--c-text-muted); display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.job-sub span { display: inline-flex; align-items: center; gap: 4px; }
.dot { opacity: 0.5; }
.job-salary-badge { font-size: 14px; font-weight: 700; color: var(--c-accent-primary); white-space: nowrap; margin-left: 12px; }
.card-footer-action {
  display: flex; align-items: center; justify-content: center; gap: 6px; padding: 14px; margin-top: 8px;
  border-top: 1px solid var(--c-border-glass); color: var(--c-accent-primary); font-size: 14px; font-weight: 600; cursor: pointer; transition: color var(--duration-fast);
}
.card-footer-action:hover { color: var(--c-accent-primary-hover); }
.skill-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.skill-chip {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 999px;
  background: var(--c-bg-surface-hover); border: 1px solid var(--c-border-glass); color: var(--c-text-secondary); font-size: 13px; transition: all var(--duration-fast);
}
.skill-chip:hover { background: var(--c-bg-base-elevated); border-color: var(--c-border-glass-hover); color: var(--c-text-primary); }
.skill-chip.hot { background: rgba(217, 226, 255, 0.95); border-color: rgba(0, 89, 199, 0.16); color: var(--c-accent-primary); }
.skill-chip small { opacity: 0.6; font-size: 11px; }
.skeleton-dashboard { display: flex; flex-direction: column; gap: 24px; padding: 20px 0; }
.empty-state-wrapper { height: 400px; border-radius: var(--radius-lg); }
@media (max-width: 1024px) {
  .hero-banner { grid-template-columns: 1fr; }
  .hero-motion-stage { width: 100%; max-width: 620px; }
  .entry-strip, .content-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .hero-banner { padding: 28px 22px; border-radius: var(--radius-xl); }
  .hero-title { font-size: 24px; }
  .hero-subtitle { font-size: 14px; margin-bottom: 24px; }
  .hero-scroll-hint { width: 100%; justify-content: center; }
  .hero-side { padding: 20px 18px 18px; }
  .hero-side-caption { max-width: none; }
  .brief-row-value,
  .brief-row-value-text { max-width: none; text-align: left; }
  .hero-motion-stage {
    height: auto;
    margin-top: 24px;
    display: grid;
    gap: 14px;
    perspective: none;
  }
  .hero-motion-stage::before { display: none; }
  .hero-float-card {
    position: relative;
    top: auto;
    left: auto;
    width: 100%;
    min-height: 0;
    transform: none;
  }
  .hero-motion-stage:hover .hero-float-card.layer-one,
  .hero-motion-stage:hover .hero-float-card.layer-two,
  .hero-motion-stage:hover .hero-float-card.layer-three,
  .hero-motion-stage:hover .hero-float-card.layer-four,
  .hero-float-card:hover {
    transform: translateY(-2px);
  }
  .kpi-grid { grid-template-columns: 1fr; gap: 16px; }
  .bar-name { width: 74px; font-size: 13px; }
  .bar-value { width: 45px; font-size: 13px; }
}

/* ═══════════════════════════════════════════════════════════════════
   Dark-mode overrides — the hero-float decorative tiles (tone-primary
   / tone-secondary / tone-glass) use light-gradient backgrounds that
   look jarring over the dark app base. Map them to the surface-strong
   token (translucent panel) so they read as faint tiles in dark mode
   while preserving the original bright look in light mode. The accent
   tone keeps its blue gradient (works in both themes).
   ═══════════════════════════════════════════════════════════════════ */
[data-theme="dark"] .hero-float-card.tone-primary,
[data-theme="dark"] .hero-float-card.tone-secondary,
[data-theme="dark"] .hero-float-card.tone-glass {
  background: var(--c-bg-surface-strong);
  border-color: var(--c-border-glass);
}
</style>
