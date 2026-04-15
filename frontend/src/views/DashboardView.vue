<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Briefcase,
  Building2,
  Flame,
  GraduationCap,
  LineChart,
  MapPin,
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
</script>

<template>
  <div class="dashboard-layout page-shell">
    <section class="hero-banner">
      <div class="hero-content">
        <span class="hero-kicker">职业能力大数据服务平台</span>
        <h1 class="hero-title">面向院校决策与学生发展的职业数据中枢</h1>
        <div class="hero-actions">
          <GlowButton variant="primary" @click="router.push('/reports')">
            <span class="pulse-dot"></span> 进入报告中心
          </GlowButton>
          <GlowButton variant="ghost" @click="router.push('/jobs')">
            浏览岗位大厅 <ArrowRight :size="16" />
          </GlowButton>
        </div>
      </div>
      <div class="hero-side glass-panel">
        <div class="hero-side-label">当前关注</div>
        <div class="hero-side-item">
          <span>热点城市</span>
          <strong>{{ topCity?.city || '等待数据' }}</strong>
        </div>
        <div class="hero-side-item">
          <span>核心行业</span>
          <strong>{{ topIndustry?.industryName || topIndustry?.industry || '等待数据' }}</strong>
        </div>
        <div class="hero-side-item">
          <span>岗位规模</span>
          <strong>{{ stats?.totalJobs?.toLocaleString?.() || stats?.totalJobs || '0' }}</strong>
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
      <section class="section-heading">
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
        <PremiumCard v-for="entry in quickEntries" :key="entry.path" :title="entry.title" glowColor="primary" class="entry-card" padding="22px">
          <div class="entry-card-body" @click="router.push(entry.path)">
            <div class="entry-top">
              <component :is="entry.icon" :size="20" />
              <span class="entry-badge">{{ entry.badge }}</span>
            </div>
            <p>{{ entry.desc }}</p>
            <span class="entry-link">进入模块 <ArrowRight :size="14" /></span>
          </div>
        </PremiumCard>
      </TransitionGroup>

      <section class="section-heading">
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
  padding: 42px;
  border-radius: var(--radius-xl);
  background:
    linear-gradient(135deg, rgba(56, 189, 248, 0.12), transparent 38%),
    var(--c-bg-surface-strong);
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
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(0, 0, 0, 0.1);
  color: #000000;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.05em;
  width: fit-content;
}
.hero-title {
  font-family: var(--font-display); 
  font-size: clamp(34px, 5.5vw, 56px); 
  font-weight: 900; 
  margin-bottom: 22px; 
  color: #000000;
  line-height: 1.1;
  letter-spacing: -0.01em;
}
.hero-subtitle { font-size: 16px; color: rgba(226, 232, 240, 0.88); line-height: 1.9; margin-bottom: 28px; max-width: 640px; }
.hero-actions { display: flex; gap: 14px; }
.hero-side {
  position: relative;
  z-index: 2;
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100%;
}
.hero-side-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-faint);
}
.hero-side-item {
  padding: 14px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.hero-side-item:last-child { border-bottom: none; }
.hero-side-item span {
  display: block;
  color: var(--c-text-muted);
  font-size: 13px;
}
.hero-side-item strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  color: var(--c-text-primary);
  font-family: var(--font-display);
}
.hero-glass-orb { position: absolute; border-radius: 50%; filter: blur(18px); opacity: 0.35; }
.orb-primary { top: -20px; right: 8%; width: 220px; height: 220px; background: radial-gradient(circle, rgba(59, 130, 246, 0.75), transparent 70%); }
.orb-secondary { left: 8%; bottom: -60px; width: 260px; height: 260px; background: radial-gradient(circle, rgba(168, 85, 247, 0.55), transparent 72%); }
.pulse-dot {
  display: inline-block; width: 8px; height: 8px; background: #fff; border-radius: 50%; margin-right: 6px;
  box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7); animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(255, 255, 255, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; }
.entry-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.entry-card { min-height: 184px; }
.entry-card-body { display: flex; flex-direction: column; gap: 16px; height: 100%; cursor: pointer; }
.entry-top { display: flex; align-items: center; justify-content: space-between; color: var(--c-text-primary); }
.entry-badge {
  padding: 6px 10px; border-radius: 999px; background: rgba(59, 130, 246, 0.14);
  border: 1px solid rgba(59, 130, 246, 0.22); color: #9ac2ff; font-size: 12px; font-weight: 600;
}
.entry-card p { color: var(--c-text-secondary); line-height: 1.75; flex: 1; }
.entry-link { display: inline-flex; align-items: center; gap: 6px; color: var(--c-accent-primary); font-size: 14px; font-weight: 600; }
.content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.left-column, .right-column { display: flex; flex-direction: column; gap: 24px; }
.bar-list { display: flex; flex-direction: column; gap: 14px; }
.bar-item { display: flex; align-items: center; gap: 12px; }
.bar-rank {
  width: 24px; height: 24px; border-radius: 6px; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; background: rgba(255, 255, 255, 0.05); color: var(--c-text-muted); flex-shrink: 0;
}
.bar-rank.top3 { background: linear-gradient(135deg, #f97316, #fb923c); color: #fff; }
.bar-name {
  width: 100px; color: var(--c-text-secondary); font-size: 14px; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis; flex-shrink: 0;
}
.bar-track { flex: 1; height: 8px; background: rgba(255, 255, 255, 0.05); border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 4px; transition: width 0.8s var(--ease-out); }
.bar-fill.teal { background: linear-gradient(90deg, rgba(45, 212, 191, 0.4), #2dd4bf); }
.bar-fill.purple { background: linear-gradient(90deg, rgba(168, 85, 247, 0.4), #a855f7); }
.bar-value { width: 60px; text-align: right; font-family: var(--font-display); font-size: 14px; flex-shrink: 0; }
.hot-jobs-list { display: flex; flex-direction: column; gap: 2px; }
.hot-job-item {
  display: flex; justify-content: space-between; align-items: center; padding: 14px 12px;
  border-radius: var(--radius-sm); cursor: pointer; transition: background var(--duration-fast);
}
.hot-job-item:hover { background: rgba(255, 255, 255, 0.04); }
.job-info { flex: 1; min-width: 0; }
.job-title-text { font-size: 15px; font-weight: 600; margin: 0 0 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.job-sub { font-size: 13px; color: var(--c-text-muted); display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.job-sub span { display: inline-flex; align-items: center; gap: 4px; }
.dot { opacity: 0.5; }
.job-salary-badge { font-size: 14px; font-weight: 700; color: var(--c-accent-secondary); white-space: nowrap; margin-left: 12px; }
.card-footer-action {
  display: flex; align-items: center; justify-content: center; gap: 6px; padding: 14px; margin-top: 8px;
  border-top: 1px solid var(--c-border-glass); color: var(--c-accent-primary); font-size: 14px; font-weight: 600; cursor: pointer; transition: color var(--duration-fast);
}
.card-footer-action:hover { color: var(--c-accent-primary-hover); }
.skill-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.skill-chip {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.04); border: 1px solid var(--c-border-glass); color: var(--c-text-secondary); font-size: 13px; transition: all var(--duration-fast);
}
.skill-chip:hover { background: rgba(255, 255, 255, 0.08); border-color: var(--c-border-glass-hover); color: var(--c-text-primary); }
.skill-chip.hot { background: rgba(249, 115, 22, 0.1); border-color: rgba(249, 115, 22, 0.3); color: var(--c-accent-secondary); }
.skill-chip small { opacity: 0.6; font-size: 11px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; padding: 60px 24px; color: var(--c-text-muted); }
@media (max-width: 1024px) {
  .hero-banner { grid-template-columns: 1fr; }
  .entry-strip, .content-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .hero-banner { padding: 28px 22px; border-radius: var(--radius-lg); }
  .hero-title { font-size: 24px; }
  .hero-subtitle { font-size: 14px; margin-bottom: 24px; }
  .hero-actions { flex-direction: column; width: 100%; }
  .hero-actions > * { width: 100%; text-align: center; }
  .kpi-grid { grid-template-columns: 1fr; gap: 16px; }
  .bar-name { width: 74px; font-size: 13px; }
  .bar-value { width: 45px; font-size: 13px; }
}
</style>
