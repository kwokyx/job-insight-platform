<script setup>
import { ref, onMounted, computed } from 'vue'
import StatWidget from '../components/common/StatWidget.vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import { fetchOverview, fetchHotJobs, fetchSkills } from '../api'
import { Briefcase, TrendingUp, MapPin, Building2, Flame, ArrowRight } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

const router = useRouter()

const stats = ref(null)
const hotJobs = ref([])
const topSkills = ref([])
const isLoading = ref(true)

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
  <div class="dashboard-layout">
    <!-- Hero / Welcome -->
    <div class="hero-banner glass-panel">
      <div class="hero-content">
        <h1 class="hero-title">职业能力大数据服务平台</h1>
        <p class="hero-subtitle">
          基于全网海量真实招聘数据，深度洞察就业市场趋势。<br/>解码核心技能需求，量化薪资分布结构，用数据驱动你的职业决策。
        </p>
        <div class="hero-actions">
          <GlowButton variant="primary" @click="router.push('/jobs')">
            <span class="pulse-dot"></span> 浏览岗位
          </GlowButton>
          <GlowButton variant="ghost" @click="router.push('/insights')">
            查看洞察 <ArrowRight :size="16" />
          </GlowButton>
        </div>
      </div>
      <div class="hero-bg-effect"></div>
    </div>

    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载平台数据...</p>
    </div>

    <template v-else-if="stats">
      <!-- KPIs -->
      <div class="kpi-grid">
        <StatWidget 
          label="在库职位总数" 
          :value="stats.totalJobs?.toLocaleString?.() || stats.totalJobs || '0'" 
          note="覆盖多渠道真实数据" 
          glowColor="primary"
        />
        <StatWidget 
          label="市场均薪区间" 
          :value="stats.avgSalaryMin && stats.avgSalaryMax ? `${stats.avgSalaryMin}~${stats.avgSalaryMax}K` : '暂无数据'" 
          note="月薪基线（千元）" 
          glowColor="secondary"
        />
        <StatWidget 
          label="最热门城市" 
          :value="topCity?.city || '暂无'" 
          :note="topCity ? `${topCity.count} 个岗位` : ''" 
          glowColor="teal"
        />
        <StatWidget 
          label="核心行业" 
          :value="topIndustry?.industryName || topIndustry?.industry || '暂无'" 
          :note="topIndustry ? `${topIndustry.count} 个岗位` : ''" 
          glowColor="purple"
        />
      </div>

      <!-- 主体区域 -->
      <div class="content-grid">
        <!-- 左侧：城市分布 + 行业分布 -->
        <div class="left-column">
          <PremiumCard title="城市岗位分布 TOP 10" glowColor="teal">
            <div class="bar-list">
              <div v-for="(item, i) in (stats.topCities || []).slice(0, 10)" :key="item.city" class="bar-item">
                <span class="bar-rank" :class="{ top3: i < 3 }">{{ i + 1 }}</span>
                <span class="bar-name">{{ item.city }}</span>
                <div class="bar-track">
                  <div 
                    class="bar-fill teal" 
                    :style="{ width: `${(item.count / (stats.topCities[0]?.count || 1)) * 100}%` }"
                  ></div>
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
                  <div 
                    class="bar-fill purple" 
                    :style="{ width: `${(item.count / (stats.topIndustries[0]?.count || 1)) * 100}%` }"
                  ></div>
                </div>
                <strong class="bar-value">{{ item.count }}</strong>
              </div>
            </div>
          </PremiumCard>
        </div>

        <!-- 右侧：热门岗位 + 技能热榜 -->
        <div class="right-column">
          <PremiumCard title="热门岗位" glowColor="secondary">
            <div class="hot-jobs-list">
              <div v-for="job in hotJobs" :key="job.id" class="hot-job-item" @click="router.push('/jobs')">
                <div class="job-info">
                  <h4 class="job-title-text">{{ job.title }}</h4>
                  <div class="job-sub">
                    <span>{{ job.companyName }}</span>
                    <span class="dot">·</span>
                    <span>{{ job.city }}</span>
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
              <span 
                v-for="(skill, i) in topSkills" 
                :key="skill.skill" 
                class="skill-chip"
                :class="{ hot: i < 3 }"
              >
                <Flame v-if="i < 3" :size="12" />
                {{ skill.skill }}
                <small>{{ skill.count }}</small>
              </span>
            </div>
          </PremiumCard>
        </div>
      </div>
    </template>

    <!-- 空数据 -->
    <div v-else class="empty-state glass-panel">
      <p>暂无数据，请确保后端服务已启动且数据库中有职位数据。</p>
    </div>
  </div>
</template>

<style scoped>
.dashboard-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Hero Clean Centered Style */
.hero-banner {
  position: relative;
  overflow: hidden;
  padding: 64px 48px;
  border-radius: var(--radius-xl);
  background: var(--c-bg-surface);
  border: 1px solid var(--c-border-glass);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 800px;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(22px, 5vw, 48px);
  font-weight: 800;
  margin-bottom: 20px;
  color: var(--c-text-primary);
  background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.hero-subtitle {
  font-size: 18px;
  color: var(--c-text-secondary);
  line-height: 1.8;
  margin-bottom: 36px;
  max-width: 650px;
  font-weight: 500;
}

.pulse-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #fff;
  border-radius: 50%;
  margin-right: 6px;
  box-shadow: 0 0 0 0 rgba(255,255,255, 0.7);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(255, 255, 255, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(255, 255, 255, 0); }
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.hero-bg-effect {
  position: absolute;
  top: 50%;
  right: 5%;
  transform: translateY(-50%);
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--c-accent-primary-glow) 0%, transparent 70%);
  filter: blur(40px);
  z-index: 1;
  border-radius: 50%;
  opacity: 0.5;
}

/* KPI */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
}

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.left-column, .right-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Bar List（城市/行业排行） */
.bar-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-rank {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: rgba(255,255,255,0.05);
  color: var(--c-text-muted);
  flex-shrink: 0;
}
.bar-rank.top3 {
  background: linear-gradient(135deg, #F97316, #FB923C);
  color: #fff;
}

.bar-name {
  width: 100px;
  color: var(--c-text-secondary);
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 8px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.8s var(--ease-out);
}
.bar-fill.teal { background: linear-gradient(90deg, rgba(45, 212, 191, 0.4), #2DD4BF); }
.bar-fill.purple { background: linear-gradient(90deg, rgba(168, 85, 247, 0.4), #A855F7); }
.bar-fill.primary { background: linear-gradient(90deg, rgba(37, 99, 235, 0.4), var(--c-accent-primary)); }
.bar-fill.secondary { background: linear-gradient(90deg, rgba(234, 88, 12, 0.4), var(--c-accent-secondary)); }

.bar-value {
  width: 60px;
  text-align: right;
  font-family: var(--font-display);
  font-size: 14px;
  flex-shrink: 0;
}

/* Hot Jobs */
.hot-jobs-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hot-job-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast);
}
.hot-job-item:hover {
  background: rgba(255,255,255,0.04);
}

.job-info {
  flex: 1;
  min-width: 0;
}

.job-title-text {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.job-sub {
  font-size: 13px;
  color: var(--c-text-muted);
  display: flex;
  gap: 6px;
  align-items: center;
}
.dot { opacity: 0.5; }

.job-salary-badge {
  font-size: 14px;
  font-weight: 700;
  color: var(--c-accent-secondary);
  white-space: nowrap;
  margin-left: 12px;
}

.card-footer-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 14px;
  margin-top: 8px;
  border-top: 1px solid var(--c-border-glass);
  color: var(--c-accent-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: color var(--duration-fast);
}
.card-footer-action:hover { color: var(--c-accent-primary-hover); }

/* Skill Tags */
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-size: 13px;
  transition: all var(--duration-fast);
}
.skill-chip:hover {
  background: rgba(255,255,255,0.08);
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}
.skill-chip.hot {
  background: rgba(249, 115, 22, 0.1);
  border-color: rgba(249, 115, 22, 0.3);
  color: var(--c-accent-secondary);
}
.skill-chip small {
  opacity: 0.6;
  font-size: 11px;
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 20px;
  color: var(--c-text-muted);
}

.loader-ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255,255,255,0.08);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Empty */
.empty-state {
  text-align: center;
  padding: 60px 24px;
  color: var(--c-text-muted);
}

@media (max-width: 1024px) {
  .content-grid { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .hero-banner {
    padding: 24px;
    border-radius: var(--radius-lg);
  }
  .hero-title { font-size: 20px; }
  .hero-subtitle { font-size: 13px; margin-bottom: 24px; }
  .hero-actions { flex-direction: column; width: 100%; }
  .hero-actions > * { width: 100%; text-align: center; }
  .kpi-grid { grid-template-columns: 1fr; gap: 16px; }
  .bar-name { width: 70px; font-size: 13px; }
  .bar-value { width: 45px; font-size: 13px; }
}
</style>
