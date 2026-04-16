<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import InsightPanel from '../components/insights/InsightPanel.vue'
import { fetchAnalysisOverview, fetchSalaryTrend } from '../api'

import SalaryView from './SalaryView.vue'
import SkillMapView from './SkillMapView.vue'
import { BarChart3, Award, DollarSign } from 'lucide-vue-next'
import { useThemeStore } from '../store/theme'

use([
  CanvasRenderer, PieChart, BarChart, LineChart, RadarChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent
])

const themeStore = useThemeStore()
const isLoading = ref(true)
const overview = ref(null)
const salaryTrendData = ref(null)
const activeTab = ref('overview')
const topCity = computed(() => overview.value?.topCities?.[0] || null)
const topIndustry = computed(() => overview.value?.topIndustries?.[0] || null)
const topSkill = computed(() => overview.value?.topSkills?.[0] || null)
const formattedTotalJobs = computed(() => formatNumber(overview.value?.totalJobs) || '暂无数据')
const formattedSalaryRange = computed(() => {
  const min = formatSalaryValue(overview.value?.avgSalaryMin)
  const max = formatSalaryValue(overview.value?.avgSalaryMax)

  return min && max ? `${min}~${max}K` : '暂无数据'
})
const topCityLabel = computed(() => topCity.value?.city || '暂无数据')
const topIndustryLabel = computed(() => topIndustry.value?.industryName || topIndustry.value?.industry || '暂无数据')
const overviewHighlights = computed(() => [
  {
    label: '样本岗位',
    value: formattedTotalJobs.value,
    note: '分析样本',
    tone: 'primary'
  },
  {
    label: '热门城市',
    value: topCityLabel.value,
    note: topCity.value ? `${formatNumber(topCity.value.count)} 岗位` : '等待同步',
    tone: 'secondary'
  },
  {
    label: '核心行业',
    value: topIndustryLabel.value,
    note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步',
    tone: 'purple'
  },
  {
    label: '平均薪资',
    value: formattedSalaryRange.value,
    note: topSkill.value ? `高频：${topSkill.value.skill}` : '薪资区间',
    tone: 'amber'
  }
])

const getEchartsTheme = () => {
  return themeStore.isDark ? {
    textColor: '#CBD5E1',
    splitLineColor: 'rgba(255,255,255,0.06)',
    tooltipBg: 'rgba(15, 23, 42, 0.95)',
  } : {
    textColor: '#475569',
    splitLineColor: 'rgba(0,0,0,0.06)',
    tooltipBg: 'rgba(255, 255, 255, 0.95)',
  }
}

onMounted(async () => {
  try {
    const [ov, trend] = await Promise.all([
      fetchAnalysisOverview(),
      fetchSalaryTrend()
    ])
    overview.value = ov
    salaryTrendData.value = trend
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    isLoading.value = false
  }
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

const cityPieOption = computed(() => {
  if (!overview.value?.topCities?.length) return null
  const t = getEchartsTheme()
  const palette = ['#3B82F6', '#8B5CF6', '#2DD4BF', '#F97316', '#10B981', '#EC4899', '#F59E0B', '#6366F1', '#14B8A6', '#EF4444']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { show: false },
    series: [{
      type: 'pie', radius: ['38%', '72%'],
      label: { show: true, color: t.textColor, formatter: '{b}\n{d}%' },
      data: overview.value.topCities.slice(0, 8).map((c, i) => ({ value: c.count, name: c.city, itemStyle: { color: palette[i % palette.length] } }))
    }]
  }
})

const industryPieOption = computed(() => {
  if (!overview.value?.topIndustries?.length) return null
  const t = getEchartsTheme()
  const palette = ['#F97316', '#3B82F6', '#A855F7', '#10B981', '#EF4444', '#2DD4BF', '#F59E0B', '#EC4899']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    series: [{
      type: 'pie', radius: ['42%', '70%'], roseType: 'area',
      label: { show: true, color: t.textColor, formatter: '{b}' },
      data: overview.value.topIndustries.slice(0, 8).map((ind, i) => ({ value: ind.count, name: ind.industryName || ind.industry, itemStyle: { color: palette[i % palette.length] } }))
    }]
  }
})

const educationBarOption = computed(() => {
  if (!overview.value?.educationDistribution?.length) return null
  const t = getEchartsTheme()
  const sorted = [...overview.value.educationDistribution].sort((a, b) => b.count - a.count)
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    grid: { left: '4%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: sorted.map(e => e.education), axisLabel: { color: t.textColor } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'bar', barWidth: '50%', data: sorted.map(e => ({ value: e.count })) }]
  }
})

const experienceRadarOption = computed(() => {
  if (!overview.value?.experienceDistribution?.length) return null
  const t = getEchartsTheme()
  const data = overview.value.experienceDistribution.slice(0, 8)
  const maxVal = Math.max(...data.map(d => d.count))
  return {
    tooltip: { backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    radar: {
      indicator: data.map(d => ({ name: d.experience, max: maxVal * 1.2 })),
      splitArea: { areaStyle: { color: ['rgba(0,0,0,0)', 'rgba(0,0,0,0.05)'] } },
      axisName: { color: t.textColor }
    },
    series: [{ type: 'radar', areaStyle: { color: 'rgba(45, 212, 191, 0.2)' }, lineStyle: { color: '#2DD4BF' }, itemStyle: { color: '#2DD4BF' }, data: [{ value: data.map(d => d.count), name: '岗位数量' }] }]
  }
})

const salaryTrendOption = computed(() => {
  if (!salaryTrendData.value?.xAxis?.length) return null
  const t = getEchartsTheme()
  const trend = salaryTrendData.value
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { data: ['平均薪资下限', '平均薪资上限'], textStyle: { color: t.textColor }, top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '14%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trend.xAxis, axisLabel: { color: t.textColor }, axisLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor, formatter: '{value}K' }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [
      { name: '平均薪资上限', type: 'line', smooth: true, itemStyle: { color: '#F97316' }, data: trend.series?.find(s => s.name === 'avgSalaryMax')?.data || [] },
      { name: '平均薪资下限', type: 'line', smooth: true, itemStyle: { color: '#3B82F6' }, data: trend.series?.find(s => s.name === 'avgSalaryMin')?.data || [] }
    ]
  }
})

const skillBarOption = computed(() => {
  if (!overview.value?.topSkills?.length) return null
  const t = getEchartsTheme()
  const skills = [...overview.value.topSkills].slice(0, 15).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    grid: { left: '4%', right: '8%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'category', data: skills.map(s => s.skill), axisLabel: { color: t.textColor } },
    series: [{ type: 'bar', barWidth: '60%', itemStyle: { color: '#3B82F6', borderRadius: [0, 4, 4, 0] }, data: skills.map(s => ({ value: s.count })) }]
  }
})

const citySalaryOption = computed(() => {
  if (!overview.value?.topCities?.length) return null
  const t = getEchartsTheme()
  const cities = overview.value.topCities.filter(c => c.avgSalary).slice(0, 10)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    grid: { left: '4%', right: '4%', bottom: '12%', top: '8%', containLabel: true },
    xAxis: { type: 'category', data: cities.map(c => c.city), axisLabel: { color: t.textColor, rotate: 30 } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor, formatter: '{value}K' }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'bar', barWidth: '55%', itemStyle: { color: '#F97316', borderRadius: [6, 6, 0, 0] }, data: cities.map(c => ({ value: c.avgSalary })) }]
  }
})
</script>

<template>
  <div class="insights-layout page-shell">

    <header class="page-header">
      <div class="page-copy">
        <p class="page-kicker">市场洞察</p>
        <h1>把岗位、城市、行业和技能放进一张分析桌</h1>
        <p>快速读取市场分布、结构变化和高频技能信号。</p>
      </div>
      <div class="summary-grid">
        <article
          v-for="card in overviewHighlights"
          :key="card.label"
          class="summary-card"
          :class="`tone-${card.tone}`"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <p>{{ card.note }}</p>
        </article>
      </div>
    </header>

    <nav class="tabs-nav" aria-label="洞察视图切换">
      <button :class="['tab-btn', { active: activeTab === 'overview' }]" @click="activeTab = 'overview'">
        <BarChart3 :size="18" /> 市场大盘
      </button>
      <button :class="['tab-btn', { active: activeTab === 'skills' }]" @click="activeTab = 'skills'">
        <Award :size="18" /> 技能图谱
      </button>
      <button :class="['tab-btn', { active: activeTab === 'salary' }]" @click="activeTab = 'salary'">
        <DollarSign :size="18" /> 薪资分析
      </button>
    </nav>

    <div class="tab-content">
      <transition name="fade" mode="out-in">
        <section v-if="activeTab === 'overview'" key="overview" class="overview-content">
          <div v-if="isLoading" class="loading-state">
            <div class="loader-ring"></div>
            <p>正在加载分析数据...</p>
          </div>
          <template v-else>
            <div class="chart-row two-col">
              <InsightPanel title="城市岗位分布" note="岗位集中度" tone="primary">
                <div class="chart-box"><v-chart v-if="cityPieOption" class="chart" :option="cityPieOption" autoresize /></div>
              </InsightPanel>
              <InsightPanel title="行业需求占比" note="行业结构" tone="purple">
                <div class="chart-box"><v-chart v-if="industryPieOption" class="chart" :option="industryPieOption" autoresize /></div>
              </InsightPanel>
            </div>
            <InsightPanel title="技能热度排行" note="岗位标签 Top 15" tone="teal">
              <div class="chart-box-wide"><v-chart v-if="skillBarOption" class="chart" :option="skillBarOption" autoresize /></div>
            </InsightPanel>
            <InsightPanel title="薪资趋势分析" note="平均薪资上下限" tone="secondary">
              <div class="chart-box-wide"><v-chart v-if="salaryTrendOption" class="chart" :option="salaryTrendOption" autoresize /></div>
            </InsightPanel>
            <div class="chart-row three-col">
              <InsightPanel title="学历需求" note="学历结构" tone="purple"><div class="chart-box"><v-chart v-if="educationBarOption" class="chart" :option="educationBarOption" autoresize /></div></InsightPanel>
              <InsightPanel title="经验要求" note="经验结构" tone="teal"><div class="chart-box"><v-chart v-if="experienceRadarOption" class="chart" :option="experienceRadarOption" autoresize /></div></InsightPanel>
              <InsightPanel title="城市薪资" note="城市平均薪资" tone="amber"><div class="chart-box"><v-chart v-if="citySalaryOption" class="chart" :option="citySalaryOption" autoresize /></div></InsightPanel>
            </div>
          </template>
        </section>

        <section v-else-if="activeTab === 'skills'" key="skills" class="tab-wrapper">
          <SkillMapView />
        </section>

        <section v-else-if="activeTab === 'salary'" key="salary" class="tab-wrapper">
          <SalaryView />
        </section>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.insights-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: stretch;
}

.page-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 18px 0;
}

.page-kicker {
  margin: 0;
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-copy h1 {
  margin: 0;
  color: var(--c-text-primary);
  font-size: clamp(28px, 3vw, 42px);
  line-height: 1.12;
  letter-spacing: -0.03em;
}

.page-copy p {
  margin: 0;
  max-width: 44ch;
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 16px 14px;
  border-radius: 16px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
}

.summary-card span {
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-card strong {
  color: var(--c-text-primary);
  font-size: 24px;
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.summary-card p {
  margin: 0;
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.summary-card.tone-primary { border-top: 2px solid rgba(0, 89, 199, 0.78); }
.summary-card.tone-secondary { border-top: 2px solid rgba(14, 165, 233, 0.78); }
.summary-card.tone-purple { border-top: 2px solid rgba(124, 58, 237, 0.78); }
.summary-card.tone-amber { border-top: 2px solid rgba(249, 115, 22, 0.78); }

.tabs-nav {
  display: flex;
  gap: 8px;
  padding: 8px;
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: 20px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
}
.tabs-nav::-webkit-scrollbar {
  display: none;
}

.tab-btn {
  flex-shrink: 0;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 18px;
  border-radius: 14px;
  font-weight: 600;
  color: var(--c-text-secondary);
  transition: all var(--duration-fast);
}

.tab-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.tab-btn.active {
  background: linear-gradient(135deg, var(--c-accent-primary), #0ea5e9);
  color: white;
  box-shadow: 0 10px 24px rgba(2, 132, 199, 0.22);
}

.tab-content {
  display: flex;
  flex-direction: column;
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tab-wrapper {
  animation: slideFadeIn var(--duration-normal) var(--ease-out);
}

@keyframes slideFadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.chart-row {
  display: grid;
  gap: 20px;
}
.two-col { grid-template-columns: 1fr 1fr; }
.three-col { grid-template-columns: 1fr 1fr 1fr; }

.chart-box {
  height: 360px;
  width: 100%;
  min-width: 0;
}

.chart-box-wide {
  height: 400px;
  width: 100%;
  min-width: 0;
}

.chart {
  height: 100%;
  width: 100%;
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  font-size: 15px;
}

@media (max-width: 1200px) {
  .page-header {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .three-col { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 768px) {
  .page-copy {
    padding: 0;
  }

  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .two-col,
  .three-col {
    grid-template-columns: 1fr;
  }
  .chart-box { height: 300px; }
  .chart-box-wide { height: 340px; }
}
</style>
