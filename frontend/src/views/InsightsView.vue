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
const contextualSignal = computed(() => {
  if (activeTab.value === 'skills') {
    return {
      kicker: '技能热度',
      headline: topSkill.value?.skill || '技能图谱',
      badge: topSkill.value ? `${formatNumber(topSkill.value.count)} 次出现` : '等待同步',
      summary: topSkill.value?.skill
        ? `${topSkill.value.skill} 仍然维持高频，需求主要落在 ${topIndustryLabel.value}。`
        : '等待技能热度同步。',
      rows: [
        { label: '热门城市', value: topCityLabel.value, note: topCity.value ? `${formatNumber(topCity.value.count)} 岗位` : '等待同步' },
        { label: '核心行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
        { label: '样本岗位', value: formattedTotalJobs.value, note: '分析样本' }
      ]
    }
  }

  if (activeTab.value === 'salary') {
    return {
      kicker: '薪资观察',
      headline: formattedSalaryRange.value,
      badge: topCityLabel.value,
      summary: formattedSalaryRange.value !== '暂无数据'
        ? `${topCityLabel.value} 仍然位于更活跃的薪资样本中心。`
        : '等待薪资区间同步。',
      rows: [
        { label: '热门城市', value: topCityLabel.value, note: topCity.value ? `${formatNumber(topCity.value.count)} 岗位` : '等待同步' },
        { label: '核心行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
        { label: '高频技能', value: topSkill.value?.skill || '暂无数据', note: topSkill.value ? `${formatNumber(topSkill.value.count)} 次出现` : '等待同步' }
      ]
    }
  }

  return {
    kicker: '市场信号',
    headline: topCityLabel.value,
    badge: formattedSalaryRange.value,
    summary: topCity.value
      ? `${topCityLabel.value} 仍然是当前最活跃的岗位中心，${topIndustryLabel.value} 承接主要需求。`
      : '等待市场信号同步。',
    rows: [
      { label: '样本岗位', value: formattedTotalJobs.value, note: '分析样本' },
      { label: '核心行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
      { label: '高频技能', value: topSkill.value?.skill || '暂无数据', note: topSkill.value ? `${formatNumber(topSkill.value.count)} 次出现` : '等待同步' }
    ]
  }
})

const chartPalette = {
  primary: '#285b9f',
  secondary: '#3e74b6',
  tertiary: '#5c86b5',
  accent: '#7f97b9',
  slate: '#68768a',
  slateLight: '#aab7c7',
  ink: '#46566f',
  blueScale: ['#285b9f', '#3568ab', '#4879b7', '#5c86b5', '#7f97b9', '#94a8c0', '#68768a', '#46566f']
}

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
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { show: false },
    series: [{
      type: 'pie', radius: ['38%', '72%'],
      label: { show: true, color: t.textColor, formatter: '{b}\n{d}%' },
      data: overview.value.topCities.slice(0, 8).map((c, i) => ({ value: c.count, name: c.city, itemStyle: { color: chartPalette.blueScale[i % chartPalette.blueScale.length] } }))
    }]
  }
})

const industryPieOption = computed(() => {
  if (!overview.value?.topIndustries?.length) return null
  const t = getEchartsTheme()
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    series: [{
      type: 'pie', radius: ['42%', '70%'], roseType: 'area',
      label: { show: true, color: t.textColor, formatter: '{b}' },
      data: overview.value.topIndustries.slice(0, 8).map((ind, i) => ({ value: ind.count, name: ind.industryName || ind.industry, itemStyle: { color: chartPalette.blueScale[(i + 1) % chartPalette.blueScale.length] } }))
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
    series: [{ type: 'bar', barWidth: '50%', itemStyle: { color: chartPalette.tertiary, borderRadius: [5, 5, 0, 0] }, data: sorted.map(e => ({ value: e.count })) }]
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
    series: [{ type: 'radar', areaStyle: { color: 'rgba(62, 116, 182, 0.12)' }, lineStyle: { color: chartPalette.secondary }, itemStyle: { color: chartPalette.secondary }, data: [{ value: data.map(d => d.count), name: '岗位数量' }] }]
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
      { name: '平均薪资上限', type: 'line', smooth: true, itemStyle: { color: chartPalette.primary }, lineStyle: { color: chartPalette.primary, width: 3 }, data: trend.series?.find(s => s.name === 'avgSalaryMax')?.data || [] },
      { name: '平均薪资下限', type: 'line', smooth: true, itemStyle: { color: chartPalette.slate }, lineStyle: { color: chartPalette.slate, width: 2.5 }, data: trend.series?.find(s => s.name === 'avgSalaryMin')?.data || [] }
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
    series: [{ type: 'bar', barWidth: '60%', itemStyle: { color: chartPalette.primary, borderRadius: [0, 4, 4, 0] }, data: skills.map(s => ({ value: s.count })) }]
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
    series: [{ type: 'bar', barWidth: '55%', itemStyle: { color: chartPalette.secondary, borderRadius: [6, 6, 0, 0] }, data: cities.map(c => ({ value: c.avgSalary })) }]
  }
})
</script>

<template>
  <div class="insights-layout page-shell">

    <header class="page-header">
      <div class="page-copy">
        <p class="page-kicker">市场洞察</p>
        <h1>市场洞察</h1>
        <p>城市、行业、技能与薪资信号。</p>
      </div>
      <div class="page-side">
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

        <article class="signal-board">
          <div class="signal-copy-block">
            <div class="signal-board-head">
              <span class="signal-kicker">{{ contextualSignal.kicker }}</span>
              <span class="signal-badge">{{ contextualSignal.badge }}</span>
            </div>
            <strong class="signal-city">{{ contextualSignal.headline }}</strong>
            <p class="signal-summary">{{ contextualSignal.summary }}</p>
          </div>

          <div class="signal-list">
            <div v-for="item in contextualSignal.rows" :key="item.label" class="signal-row">
              <span class="signal-row-label">{{ item.label }}</span>
              <strong class="signal-row-value">{{ item.value }}</strong>
              <small class="signal-row-note">{{ item.note }}</small>
            </div>
          </div>
        </article>
      </div>
    </header>

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
  grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
  gap: 16px;
  align-items: start;
}

.page-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  font-size: clamp(22px, 2.1vw, 28px);
  line-height: 1.12;
  letter-spacing: -0.03em;
}

.page-copy p {
  margin: 0;
  max-width: 34ch;
  color: var(--c-text-secondary);
  font-size: 13.5px;
  line-height: 1.55;
}

.signal-board {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(168px, 196px);
  gap: 16px;
  align-items: start;
  padding: 15px 16px;
  border-radius: 14px;
  border: 1px solid rgba(193, 198, 215, 0.48);
  background: rgba(255, 255, 255, 0.66);
}

.signal-copy-block {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
}

.signal-board-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.signal-kicker {
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.signal-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(40, 91, 159, 0.08);
  color: #285b9f;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.signal-city {
  color: var(--c-text-primary);
  font-size: clamp(20px, 1.8vw, 24px);
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.signal-summary,
.signal-row-note {
  margin: 0;
  color: var(--c-text-secondary);
}

.signal-summary {
  font-size: 13px;
  line-height: 1.55;
}

.signal-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
  min-width: 0;
  padding-left: 16px;
  border-left: 1px solid rgba(193, 198, 215, 0.42);
}

.signal-row {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 3px 0 8px;
}

.signal-row + .signal-row {
  border-top: 1px solid rgba(193, 198, 215, 0.3);
  padding-top: 8px;
}

.signal-row-label {
  color: var(--c-text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.signal-row-value {
  color: var(--c-text-primary);
  font-size: 16px;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.signal-row-note {
  font-size: 12px;
  line-height: 1.4;
}

.tabs-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid rgba(193, 198, 215, 0.42);
  background: rgba(255, 255, 255, 0.52);
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
  padding: 9px 12px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 700;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.tab-btn:hover {
  border-color: rgba(30, 117, 255, 0.22);
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}

.tab-btn.active {
  border-color: rgba(30, 117, 255, 0.3);
  background: rgba(30, 117, 255, 0.1);
  color: var(--c-accent-primary);
}

.tab-btn :deep(svg) {
  color: inherit;
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

  .signal-board {
    grid-template-columns: 1fr;
  }

  .signal-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px 12px;
    padding-top: 12px;
    padding-left: 0;
    border-top: 1px solid rgba(193, 198, 215, 0.42);
    border-left: none;
  }

  .signal-row,
  .signal-row + .signal-row {
    border-top: none;
    padding: 0;
  }

  .three-col { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 768px) {
  .page-copy {
    padding: 0;
  }

  .signal-list {
    grid-template-columns: 1fr;
  }

  .two-col,
  .three-col {
    grid-template-columns: 1fr;
  }
  .chart-box { height: 300px; }
  .chart-box-wide { height: 340px; }
}
</style>
