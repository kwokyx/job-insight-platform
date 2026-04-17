<script setup>
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
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
import StatWidget from '../components/common/StatWidget.vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import HeroParticles from '../components/common/HeroParticles.vue'
import RankingList from '../components/dashboard/RankingList.vue'
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
const marketLead = computed(() => {
  if (!topCity.value?.city) {
    return '市场样本同步后，这里会优先展示当前最活跃城市与岗位热度。'
  }

  const jobCount = formatNumber(topCity.value.count) || '0'
  return `${topCity.value.city}当前最活跃，${jobCount}个岗位保持开放。`
})
const marketSummary = computed(() => {
  const industry = topIndustry.value?.industryName || topIndustry.value?.industry
  const salaryRange = formattedSalaryRange.value

  if (industry && salaryRange !== '等待数据') {
    return `需求主要集中在${industry}，市场平均月薪区间约为${salaryRange}。`
  }

  if (industry) {
    return `需求主要集中在${industry}，薪资区间会在更多样本同步后补齐。`
  }

  if (salaryRange !== '等待数据') {
    return `市场平均月薪区间约为${salaryRange}，行业集中度会在样本同步后补齐。`
  }

  return '岗位总量、行业集中度和薪资区间会随样本同步后逐步更新。'
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
      <HeroParticles />
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="hero-title-typewriter" :class="{ done: heroTitleDone }">
            {{ heroTitleDisplay }}
          </span>
        </h1>
        <p class="hero-subtitle">
          把采集、洞察、推荐和报告串成一条可操作的路径。
        </p>
        <button class="hero-scroll-hint" type="button" @click="scrollToSection('dashboard-metrics')">
          <span class="hero-scroll-kicker">继续浏览</span>
          <span>查看今日概览与市场结构</span>
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
          <span class="hero-side-label">市场摘要</span>
          <p class="hero-side-caption">把实时岗位样本压缩成一眼能读完的今日简报。</p>
        </div>
        <div class="hero-brief-panel">
          <div class="hero-brief-main">
            <div class="hero-brief-meta">
              <span class="hero-brief-meta-dot" aria-hidden="true"></span>
              <span>实时市场信号</span>
            </div>
            <strong class="hero-brief-city">{{ topCity?.city || '等待数据' }}</strong>
            <p class="hero-brief-lead">{{ marketLead }}</p>
            <p class="hero-brief-summary">{{ marketSummary }}</p>
          </div>

          <div class="hero-brief-list" role="list" aria-label="市场关键指标">
            <div class="brief-row" role="listitem">
              <span class="brief-row-label">平均薪资区间</span>
              <strong class="brief-row-value">{{ formattedSalaryRange }}</strong>
            </div>
            <div class="brief-row" role="listitem">
              <span class="brief-row-label">在库岗位规模</span>
              <strong class="brief-row-value">{{ formattedTotalJobs }}</strong>
            </div>
            <div class="brief-row" role="listitem">
              <span class="brief-row-label">核心行业</span>
              <strong class="brief-row-value brief-row-value-text">{{ primaryIndustryLabel }}</strong>
            </div>
          </div>
        </div>
      </div>
      <div class="hero-glass-orb orb-primary"></div>
      <div class="hero-glass-orb orb-secondary"></div>
    </section>

    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载平台概览数据...</p>
    </div>

    <template v-else-if="stats">
      <section id="dashboard-metrics" class="section-heading">
        <div>
          <h2>核心指标</h2>
        </div>
      </section>

      <TransitionGroup name="list" tag="div" class="kpi-grid">
        <StatWidget key="jobs" label="在库岗位总量" :value="stats.totalJobs?.toLocaleString?.() || stats.totalJobs || '0'" note="多渠道汇总后的真实岗位规模" glowColor="primary" />
        <StatWidget key="salary" label="市场平均薪资区间" :value="stats.avgSalaryMin && stats.avgSalaryMax ? `${stats.avgSalaryMin}~${stats.avgSalaryMax}K` : '暂无数据'" note="按岗位样本估算的月薪范围" glowColor="secondary" />
        <StatWidget key="cities" label="最热门城市" :value="topCity?.city || '暂无'" :note="topCity ? `${topCity.count} 个岗位` : ''" glowColor="teal" />
        <StatWidget key="industries" label="核心行业" :value="topIndustry?.industryName || topIndustry?.industry || '暂无'" :note="topIndustry ? `${topIndustry.count} 个岗位` : ''" glowColor="purple" />
      </TransitionGroup>

      <section class="section-heading">
        <div>
          <h2>高频入口</h2>
        </div>
      </section>

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

      <section id="dashboard-market" class="section-heading">
        <div>
          <h2>市场结构</h2>
        </div>
      </section>

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
    </template>

    <div v-else class="empty-state glass-panel">
      <Briefcase :size="28" />
      <p>暂无概览数据，请确认后端服务与数据库已正常启动。</p>
    </div>
  </div>
</template>

<style scoped>
.dashboard-layout { display: flex; flex-direction: column; gap: 24px; }
.hero-banner {
  position: relative;
  overflow: hidden;
  padding: 36px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.05), transparent 28%),
    rgba(255, 255, 255, 0.86);
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
  padding: 20px 20px 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  color: var(--c-text-primary);
  border-radius: 20px;
  border: 1px solid rgba(193, 198, 215, 0.62);
  box-shadow: none;
}
.hero-side-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(193, 198, 215, 0.38);
}
.hero-side-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-accent-primary);
}
.hero-side-caption {
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.65;
}
.hero-brief-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.hero-brief-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 16px;
  border-left: 2px solid rgba(0, 89, 199, 0.18);
}
.hero-brief-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.hero-brief-meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(0, 89, 199, 0.76);
  box-shadow: 0 0 0 4px rgba(0, 89, 199, 0.08);
}
.hero-brief-city {
  display: block;
  font-family: var(--font-display);
  font-size: clamp(32px, 3.4vw, 42px);
  line-height: 0.98;
  letter-spacing: -0.04em;
  color: var(--c-text-primary);
}
.hero-brief-lead {
  color: var(--c-text-primary);
  font-size: 16px;
  line-height: 1.65;
}
.hero-brief-summary {
  color: var(--c-text-muted);
  font-size: 13px;
  line-height: 1.7;
  max-width: 24ch;
}
.hero-brief-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid rgba(193, 198, 215, 0.38);
}
.brief-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(193, 198, 215, 0.38);
}
.brief-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}
.brief-row-label {
  color: var(--c-text-muted);
  font-size: 12px;
  letter-spacing: 0.03em;
}
.brief-row-value {
  color: var(--c-text-primary);
  font-size: 15px;
  line-height: 1.45;
  font-weight: 600;
  text-align: right;
}
.brief-row-value-text {
  max-width: 13ch;
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
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; }
.entry-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.entry-card { min-height: 192px; }
.entry-card-body { display: flex; flex-direction: column; gap: 18px; height: 100%; cursor: pointer; }
.entry-top { display: flex; align-items: center; justify-content: space-between; color: var(--c-text-primary); }
.entry-badge {
  padding: 6px 12px; border-radius: 999px; background: rgba(217, 226, 255, 0.9);
  border: 1px solid rgba(0, 89, 199, 0.1); color: var(--c-accent-primary); font-size: 13px; font-weight: 600;
}
.entry-card p { color: var(--c-text-secondary); line-height: 1.8; flex: 1; font-size: 15px; }
.entry-link { display: inline-flex; align-items: center; gap: 6px; color: var(--c-accent-primary); font-size: 15px; font-weight: 600; }
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
.skill-chip:hover { background: #ffffff; border-color: var(--c-border-glass-hover); color: var(--c-text-primary); }
.skill-chip.hot { background: rgba(217, 226, 255, 0.95); border-color: rgba(0, 89, 199, 0.16); color: var(--c-accent-primary); }
.skill-chip small { opacity: 0.6; font-size: 11px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; padding: 60px 24px; color: var(--c-text-muted); }
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
  .hero-side-caption,
  .hero-brief-summary { max-width: none; }
  .hero-brief-main { padding-left: 14px; }
  .hero-brief-lead { font-size: 15px; }
  .brief-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }
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
</style>
