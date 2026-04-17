<script setup>
import { ref, onMounted, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
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
const trendLoading = ref(false)
const overview = ref(null)
const salaryTrendData = ref(null)
const activeTab = ref('overview')

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
    overview.value = await fetchAnalysisOverview()
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    isLoading.value = false
  }
})

async function loadSalaryTrendData() {
  if (salaryTrendData.value || trendLoading.value) return
  trendLoading.value = true
  try {
    salaryTrendData.value = await fetchSalaryTrend()
  } catch (e) {
    console.error('薪资趋势加载失败', e)
  } finally {
    trendLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'overview') {
    loadSalaryTrendData()
  }
}, { immediate: true })

// === ECharts Options using watchEffect or computed ===
import { computed } from 'vue'

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


    <div class="tabs-nav glass-panel">
      <button :class="['tab-btn', { active: activeTab === 'overview' }]" @click="activeTab = 'overview'">
        <BarChart3 :size="18" /> 市场大盘
      </button>
      <button :class="['tab-btn', { active: activeTab === 'skills' }]" @click="activeTab = 'skills'">
        <Award :size="18" /> 技能图谱
      </button>
      <button :class="['tab-btn', { active: activeTab === 'salary' }]" @click="activeTab = 'salary'">
        <DollarSign :size="18" /> 薪资分析
      </button>
    </div>

    <!-- Active Tab Content -->
    <div class="tab-content">
      
      <!-- Overview Tab -->
      <transition name="fade" mode="out-in">
        <div v-if="activeTab === 'overview'" key="overview" class="overview-content">
          <div v-if="isLoading" class="loading-state">
            <div class="loader-ring"></div>
            <p>正在加载分析数据...</p>
          </div>
          <template v-else>
            <section class="section-heading">
              <div>
                <h2>市场分布</h2>
              </div>
            </section>
            <div class="chart-row two-col">
              <PremiumCard title="城市岗位分布" glowColor="primary">
                <div class="chart-box"><v-chart v-if="cityPieOption" class="chart" :option="cityPieOption" autoresize /></div>
              </PremiumCard>
              <PremiumCard title="行业需求占比" glowColor="purple">
                <div class="chart-box"><v-chart v-if="industryPieOption" class="chart" :option="industryPieOption" autoresize /></div>
              </PremiumCard>
            </div>
            <PremiumCard title="技能热度排行" glowColor="teal">
              <div class="chart-box-wide"><v-chart v-if="skillBarOption" class="chart" :option="skillBarOption" autoresize /></div>
            </PremiumCard>
            <section class="section-heading">
              <div>
                <h2>趋势与结构</h2>
              </div>
            </section>
            <PremiumCard title="薪资趋势分析" glowColor="secondary">
              <div class="chart-box-wide"><v-chart v-if="salaryTrendOption" class="chart" :option="salaryTrendOption" autoresize /></div>
            </PremiumCard>
            <div class="chart-row three-col">
              <PremiumCard title="学历需求" glowColor="purple"><div class="chart-box"><v-chart v-if="educationBarOption" class="chart" :option="educationBarOption" autoresize /></div></PremiumCard>
              <PremiumCard title="经验要求" glowColor="teal"><div class="chart-box"><v-chart v-if="experienceRadarOption" class="chart" :option="experienceRadarOption" autoresize /></div></PremiumCard>
              <PremiumCard title="城市薪资" glowColor="secondary"><div class="chart-box"><v-chart v-if="citySalaryOption" class="chart" :option="citySalaryOption" autoresize /></div></PremiumCard>
            </div>
          </template>
        </div>

        <!-- Skills Tab -->
        <div v-else-if="activeTab === 'skills'" key="skills" class="tab-wrapper">
          <SkillMapView />
        </div>

        <!-- Salary Tab -->
        <div v-else-if="activeTab === 'salary'" key="salary" class="tab-wrapper">
          <SalaryView />
        </div>
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

.tabs-nav {
  display: flex;
  gap: 8px;
  padding: 8px;
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: var(--radius-lg);
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
  border-radius: var(--radius-md);
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
  gap: 24px;
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
  gap: 24px;
}
.two-col { grid-template-columns: 1fr 1fr; }
.three-col { grid-template-columns: 1fr 1fr 1fr; }

.chart-box {
  height: 380px;
  width: 100%;
  min-width: 0;
}

.chart-box-wide {
  height: 420px;
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

/* 加载状态 */
@media (max-width: 1200px) {
  .three-col { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 768px) {
  .two-col,
  .three-col {
    grid-template-columns: 1fr;
  }
  .chart-box { height: 320px; }
  .chart-box-wide { height: 350px; }
}
</style>
