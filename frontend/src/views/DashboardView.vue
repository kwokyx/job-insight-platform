<script setup>
import { ref, onMounted, computed } from 'vue'
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
import GlowButton from '../components/common/GlowButton.vue'
import { fetchOverview, fetchHotJobs, fetchSkills } from '../api'

const router = useRouter()
const stats = ref(null)
const hotJobs = ref([])
const topSkills = ref([])
const isLoading = ref(true)

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

const topCity = computed(() => stats.value?.topCities?.[0])
const topIndustry = computed(() => stats.value?.topIndustries?.[0])

function scrollToSection(sectionId) {
  const element = document.getElementById(sectionId)
  element?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="dashboard-layout page-shell">
    <section class="hero-banner">
      <div class="hero-content">
        <h1 class="hero-title">
          <span class="hero-title-typewriter">职业能力大数据服务平台</span>
        </h1>
        <p class="hero-subtitle">
          用实时岗位数据、能力画像和趋势分析把采集、洞察、推荐、报告串成一个可操作的工作流。
        </p>
        <div class="hero-actions">
          <GlowButton variant="primary" @click="scrollToSection('dashboard-metrics')">
            <span class="pulse-dot"></span> 查看核心指标
          </GlowButton>
          <GlowButton variant="ghost" @click="scrollToSection('dashboard-market')">
            查看市场结构 <ArrowRight :size="16" />
          </GlowButton>
        </div>
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
      <div class="hero-side glass-panel">
        <div class="hero-side-head">
          <span class="hero-side-label">市场摘要</span>
          <p class="hero-side-caption">用一张终端式摘要卡收束当前最关键的市场信号。</p>
        </div>
        <div class="hero-terminal-card">
          <div class="hero-terminal-head">
            <span class="terminal-badge">重点信号</span>
            <span class="terminal-meta">实时更新</span>
          </div>

          <div class="hero-terminal-main">
            <span class="terminal-label">热点城市</span>
            <strong class="terminal-primary-value">{{ topCity?.city || '等待数据' }}</strong>
            <p class="terminal-supporting-text">
              {{ topCity ? `${topCity.count} 个岗位处于持续活跃状态` : '等待市场样本完成同步' }}
            </p>
          </div>

          <div class="hero-terminal-signal" aria-hidden="true">
            <span class="signal-line line-1"></span>
            <span class="signal-line line-2"></span>
            <span class="signal-line line-3"></span>
            <span class="signal-line line-4"></span>
          </div>

          <div class="hero-terminal-grid">
            <div class="terminal-metric">
              <span>核心行业</span>
              <strong>{{ topIndustry?.industryName || topIndustry?.industry || '等待数据' }}</strong>
            </div>
            <div class="terminal-metric">
              <span>岗位规模</span>
              <strong>{{ stats?.totalJobs?.toLocaleString?.() || stats?.totalJobs || '0' }}</strong>
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
          <PremiumCard title="城市岗位分布 TOP 10" glowColor="teal">
            <div class="bar-list">
              <div v-for="(item, i) in (stats.topCities || []).slice(0, 10)" :key="item.city" class="bar-item">
                <span class="bar-rank" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <span class="bar-name">{{ item.city }}</span>
                <div class="bar-track">
                  <div class="bar-fill teal" :style="{ width: `${(item.count / (stats.topCities[0]?.count || 1)) * 100}%` }"></div>
                </div>
                <strong class="bar-value">{{ item.count }}</strong>
              </div>
            </div>
          </PremiumCard>

          <PremiumCard title="行业需求 TOP 10" glowColor="purple">
            <div class="bar-list">
              <div v-for="(item, i) in (stats.topIndustries || []).slice(0, 10)" :key="item.industryName || item.industry" class="bar-item">
                <span class="bar-rank" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <span class="bar-name">{{ item.industryName || item.industry }}</span>
                <div class="bar-track">
                  <div class="bar-fill purple" :style="{ width: `${(item.count / (stats.topIndustries[0]?.count || 1)) * 100}%` }"></div>
                </div>
                <strong class="bar-value">{{ item.count }}</strong>
              </div>
            </div>
          </PremiumCard>
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
  padding: 40px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.06), transparent 28%),
    rgba(255, 255, 255, 0.8);
  border: 1px solid var(--c-border-strong);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  align-items: stretch;
  box-shadow: var(--shadow-glass);
}
.hero-content { position: relative; z-index: 2; display: flex; flex-direction: column; justify-content: center; max-width: 760px; }
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
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  border-right: 2px solid rgba(0, 89, 199, 0.72);
  animation:
    hero-typing 1.8s steps(11, end) 200ms both,
    hero-caret 900ms steps(1, end) infinite;
}
@keyframes hero-typing {
  from { width: 0; }
  to { width: 11ch; }
}
@keyframes hero-caret {
  0%, 45% { border-right-color: rgba(0, 89, 199, 0.72); }
  46%, 100% { border-right-color: transparent; }
}
.hero-subtitle { font-size: 17px; color: var(--c-text-secondary); line-height: 1.85; margin-bottom: 28px; max-width: 640px; }
.hero-actions { display: flex; gap: 14px; }
.hero-motion-stage {
  position: relative;
  width: min(100%, 700px);
  height: 372px;
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
  padding: 22px 22px 18px;
  text-align: left;
  color: var(--c-text-primary);
  border: 1px solid rgba(193, 198, 215, 0.8);
  border-radius: 14px;
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
  width: 236px;
  min-height: 152px;
}
.hero-float-card.layer-two {
  top: 20px;
  left: 254px;
  width: 248px;
  min-height: 164px;
}
.hero-float-card.layer-three {
  top: 132px;
  left: 116px;
  width: 356px;
  min-height: 174px;
}
.hero-float-card.layer-four {
  top: 164px;
  left: 446px;
  width: 216px;
  min-height: 146px;
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
  padding: 20px 20px 22px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: 14px;
  min-height: 100%;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(243, 247, 255, 0.76)),
    rgba(255, 255, 255, 0.8);
  color: var(--c-text-primary);
  border-radius: var(--radius-xl);
  border: 1px solid rgba(193, 198, 215, 0.78);
  box-shadow: var(--shadow-card-soft);
  overflow: visible;
}
.hero-side-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
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
  font-size: 12px;
  line-height: 1.55;
}
.hero-terminal-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  margin-top: 4px;
  border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.76);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(243, 247, 255, 0.88)),
    radial-gradient(circle at 100% 0, rgba(0, 89, 199, 0.08), transparent 36%);
  box-shadow: var(--shadow-card-soft);
  transition: box-shadow 280ms var(--ease-out), border-color 280ms var(--ease-out);
}
.hero-terminal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.terminal-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(224, 236, 255, 0.82);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.terminal-meta {
  color: var(--c-text-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.hero-terminal-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.terminal-label {
  color: var(--c-text-muted);
  font-size: 12px;
  letter-spacing: 0.04em;
}
.terminal-primary-value {
  font-family: var(--font-display);
  font-size: clamp(28px, 3vw, 36px);
  line-height: 1.02;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
}
.terminal-supporting-text {
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.hero-terminal-signal {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
  min-height: 44px;
}
.signal-line {
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(0, 89, 199, 0.14), rgba(0, 89, 199, 0.48));
}
.line-1 { height: 12px; }
.line-2 { height: 28px; }
.line-3 { height: 20px; }
.line-4 { height: 36px; }
.hero-terminal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.terminal-metric {
  padding: 14px 14px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(193, 198, 215, 0.6);
}
.terminal-metric span {
  display: block;
  color: var(--c-text-muted);
  font-size: 12px;
  letter-spacing: 0.03em;
}
.terminal-metric strong {
  display: block;
  margin-top: 8px;
  color: var(--c-text-primary);
  font-size: 20px;
  line-height: 1.2;
  font-family: var(--font-display);
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
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 24px; }
.entry-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; }
.entry-card { min-height: 220px; }
.entry-card-body { display: flex; flex-direction: column; gap: 18px; height: 100%; cursor: pointer; }
.entry-top { display: flex; align-items: center; justify-content: space-between; color: var(--c-text-primary); }
.entry-badge {
  padding: 6px 12px; border-radius: 999px; background: rgba(217, 226, 255, 0.9);
  border: 1px solid rgba(0, 89, 199, 0.1); color: var(--c-accent-primary); font-size: 13px; font-weight: 600;
}
.entry-card p { color: var(--c-text-secondary); line-height: 1.8; flex: 1; font-size: 15px; }
.entry-link { display: inline-flex; align-items: center; gap: 6px; color: var(--c-accent-primary); font-size: 15px; font-weight: 600; }
.content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 28px; }
.left-column, .right-column { display: flex; flex-direction: column; gap: 28px; }
.bar-list { display: flex; flex-direction: column; gap: 14px; }
.bar-item { display: flex; align-items: center; gap: 12px; }
.bar-rank {
  width: 24px; height: 24px; border-radius: 6px; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; background: var(--c-bg-surface-hover); color: var(--c-text-muted); flex-shrink: 0;
}
.bar-rank.top3 { background: rgba(217, 226, 255, 0.95); color: var(--c-accent-primary); }
.bar-name {
  width: 100px; color: var(--c-text-secondary); font-size: 14px; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis; flex-shrink: 0;
}
.bar-track { flex: 1; height: 8px; background: #ecedf9; border-radius: 999px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 4px; transition: width 0.8s var(--ease-out); }
.bar-fill.teal { background: linear-gradient(90deg, rgba(66, 93, 151, 0.32), rgba(0, 89, 199, 0.96)); }
.bar-fill.purple { background: linear-gradient(90deg, rgba(66, 93, 151, 0.2), rgba(66, 93, 151, 0.88)); }
.bar-value { width: 60px; text-align: right; font-family: var(--font-display); font-size: 14px; flex-shrink: 0; }
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
  .hero-actions { flex-direction: column; width: 100%; }
  .hero-actions > * { width: 100%; text-align: center; }
  .hero-terminal-head { align-items: flex-start; }
  .hero-terminal-grid { grid-template-columns: 1fr; }
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
