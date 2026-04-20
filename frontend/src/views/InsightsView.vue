<script setup>
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { BarChart3, Award, DollarSign, Target } from 'lucide-vue-next'

import InsightPanel from '../components/insights/InsightPanel.vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import SalaryView from './SalaryView.vue'
import SkillMapView from './SkillMapView.vue'
import SupplyDemandView from './SupplyDemandView.vue'
import { useThemeStore } from '../store/theme'
import { useAuthStore } from '../store/auth'
import {
  fetchAnalysisOverview,
  fetchSalaryTrend,
  fetchWelfareDistribution,
  fetchCompanySizeDistribution,
  fetchFinanceStageDistribution,
  fetchDeepMarketInsights
} from '../api'
import { chartPalette, withAlpha } from '../constants/chartPalette'

use([
  CanvasRenderer,
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const themeStore = useThemeStore()
const authStore = useAuthStore()

const isLoading = ref(true)
const activeTab = ref('overview')
const overview = ref({})
const trend = ref({})
const welfareData = ref([])
const companySizeData = ref([])
const financeStageData = ref([])
const deepInsights = ref({})

const themeTokens = computed(() => {
  if (themeStore.isDark) {
    return {
      text: '#CBD5E1',
      split: 'rgba(148, 163, 184, 0.16)',
      tooltip: 'rgba(15, 23, 42, 0.96)'
    }
  }
  return {
    text: '#475569',
    split: 'rgba(148, 163, 184, 0.16)',
    tooltip: 'rgba(255, 255, 255, 0.96)'
  }
})

const sampleMeta = computed(() => deepInsights.value.sample || {})
const marketPulse = computed(() => deepInsights.value.marketPulse || {})
const cityConcentration = computed(() => deepInsights.value.cityConcentration || {})
const skillsInsight = computed(() => deepInsights.value.skillsInsight || {})
const industryMomentum = computed(() => deepInsights.value.industryMomentum || {})
const structuralInsights = computed(() => deepInsights.value.structuralInsights || [])
const recommendations = computed(() => deepInsights.value.recommendations || [])

const headlineSummary = computed(() => {
  const totalJobs = formatNumber(sampleMeta.value.totalJobs)
  const confidence = sampleMeta.value.confidenceLabel || '低'
  const demandMomentum = formatSignedPct(marketPulse.value.demandMomentumPct)
  return {
    badge: `${confidence}置信度`,
    title: totalJobs ? `${totalJobs} 个岗位样本` : '样本待同步',
    subtitle: `近周期需求动量 ${demandMomentum}，不再只是展示数据，而是给出结构判断与治理建议。`
  }
})

const summaryCards = computed(() => [
  {
    title: '需求动量',
    value: formatSignedPct(marketPulse.value.demandMomentumPct),
    note: '最近窗口与上一窗口岗位量对比'
  },
  {
    title: '薪资波动',
    value: formatPct(marketPulse.value.salaryVolatility),
    note: '均薪波动系数，越高说明市场分层越明显'
  },
  {
    title: '城市集中度',
    value: `${cityConcentration.value.topCity || '未知'} ${formatPct(cityConcentration.value.topCityShare)}`,
    note: cityConcentration.value.riskLevel || '空间分布待分析'
  },
  {
    title: '技能集中度',
    value: formatPct(skillsInsight.value.topSkillShare),
    note: '前五技能累计占比'
  }
])

const cityPieOption = computed(() => {
  const rows = overview.value.topCities || []
  if (!rows.length) return null
  return {
    tooltip: baseTooltip(),
    series: [{
      type: 'pie',
      radius: ['38%', '72%'],
      label: { color: themeTokens.value.text, formatter: '{b}\n{d}%' },
      data: rows.slice(0, 8).map((item, index) => ({
        value: item.count,
        name: item.city,
        itemStyle: { color: chartPalette.series[index % chartPalette.series.length] }
      }))
    }]
  }
})

const industryPieOption = computed(() => {
  const rows = overview.value.topIndustries || []
  if (!rows.length) return null
  return {
    tooltip: baseTooltip(),
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      roseType: 'area',
      label: { color: themeTokens.value.text, formatter: '{b}' },
      data: rows.slice(0, 8).map((item, index) => ({
        value: item.count,
        name: item.industryName || item.industry,
        itemStyle: { color: chartPalette.series[(index + 2) % chartPalette.series.length] }
      }))
    }]
  }
})

const salaryTrendOption = computed(() => {
  const xAxis = trend.value.xAxis || []
  if (!xAxis.length) return null
  return {
    tooltip: { ...baseTooltip(), trigger: 'axis' },
    legend: {
      top: 0,
      textStyle: { color: themeTokens.value.text },
      data: ['平均薪资上限', '平均薪资下限', '岗位量']
    },
    grid: { left: '4%', right: '4%', top: '16%', bottom: '6%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xAxis,
      axisLabel: { color: themeTokens.value.text },
      axisLine: { lineStyle: { color: themeTokens.value.split } }
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: { color: themeTokens.value.text, formatter: '{value}K' },
        splitLine: { lineStyle: { color: themeTokens.value.split } }
      },
      {
        type: 'value',
        axisLabel: { color: themeTokens.value.text },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '平均薪资上限',
        type: 'line',
        smooth: true,
        itemStyle: { color: chartPalette.coral },
        lineStyle: { color: chartPalette.coral, width: 3 },
        data: findTrendSeries('avgSalaryMax')
      },
      {
        name: '平均薪资下限',
        type: 'line',
        smooth: true,
        itemStyle: { color: chartPalette.blue },
        lineStyle: { color: chartPalette.blue, width: 2.5 },
        data: findTrendSeries('avgSalaryMin')
      },
      {
        name: '岗位量',
        type: 'bar',
        yAxisIndex: 1,
        itemStyle: { color: withAlpha(chartPalette.teal, 0.55), borderRadius: [4, 4, 0, 0] },
        data: findTrendSeries('jobCount')
      }
    ]
  }
})

const skillBarOption = computed(() => {
  const rows = overview.value.topSkills || []
  if (!rows.length) return null
  const data = [...rows].slice(0, 12).reverse()
  return {
    tooltip: { ...baseTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '4%', right: '6%', top: '3%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: themeTokens.value.text },
      splitLine: { lineStyle: { color: themeTokens.value.split } }
    },
    yAxis: {
      type: 'category',
      data,
      axisLabel: {
        color: themeTokens.value.text,
        formatter: (value) => value.skill || value
      }
    },
    series: [{
      type: 'bar',
      barWidth: '60%',
      itemStyle: { color: chartPalette.coral, borderRadius: [0, 4, 4, 0] },
      data: data.map(item => ({ value: item.count, skill: item.skill }))
    }]
  }
})

const welfareBarOption = computed(() => {
  if (!welfareData.value.length) return null
  const data = [...welfareData.value].sort((a, b) => a.count - b.count)
  return {
    tooltip: { ...baseTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '4%', right: '8%', top: '3%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: themeTokens.value.text },
      splitLine: { lineStyle: { color: themeTokens.value.split } }
    },
    yAxis: {
      type: 'category',
      data: data.map(item => item.welfare),
      axisLabel: { color: themeTokens.value.text }
    },
    series: [{
      type: 'bar',
      barWidth: '58%',
      itemStyle: { color: '#8B5CF6', borderRadius: [0, 4, 4, 0] },
      data: data.map(item => item.count)
    }]
  }
})

const companySizePieOption = computed(() => buildBasicPie(companySizeData.value, 'companySize'))
const financeStagePieOption = computed(() => buildBasicPie(financeStageData.value, 'financeStage'))

const insightTrendOption = computed(() => {
  const rows = marketPulse.value.monthlyTrend || []
  if (!rows.length) return null
  return {
    tooltip: { ...baseTooltip(), trigger: 'axis' },
    grid: { left: '4%', right: '4%', top: '10%', bottom: '6%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map(item => item.period),
      axisLabel: { color: themeTokens.value.text },
      axisLine: { lineStyle: { color: themeTokens.value.split } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: themeTokens.value.text },
      splitLine: { lineStyle: { color: themeTokens.value.split } }
    },
    series: [{
      type: 'line',
      smooth: true,
      areaStyle: { color: withAlpha(chartPalette.teal, 0.18) },
      itemStyle: { color: chartPalette.teal },
      lineStyle: { color: chartPalette.teal, width: 3 },
      data: rows.map(item => item.jobCount)
    }]
  }
})

onMounted(async () => {
  try {
    const [overviewRes, trendRes, welfareRes, companyRes, financeRes, deepRes] = await Promise.all([
      fetchAnalysisOverview(),
      fetchSalaryTrend(),
      fetchWelfareDistribution(15),
      fetchCompanySizeDistribution(),
      fetchFinanceStageDistribution(),
      fetchDeepMarketInsights({ months: 12 })
    ])

    overview.value = overviewRes || {}
    trend.value = trendRes || {}
    welfareData.value = welfareRes.data || []
    companySizeData.value = companyRes.data || []
    financeStageData.value = financeRes.data || []
    deepInsights.value = deepRes || {}
  } catch (error) {
    console.error('加载洞察数据失败', error)
  } finally {
    isLoading.value = false
  }
})

function baseTooltip() {
  return {
    backgroundColor: themeTokens.value.tooltip,
    borderColor: themeTokens.value.split,
    textStyle: { color: themeTokens.value.text }
  }
}

function buildBasicPie(rows, field) {
  if (!rows?.length) return null
  const palette = ['#3B82F6', '#8B5CF6', '#2DD4BF', '#F97316', '#10B981', '#EC4899']
  return {
    tooltip: baseTooltip(),
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: { color: themeTokens.value.text, formatter: '{b}\n{d}%' },
      data: rows.map((item, index) => ({
        value: item.count,
        name: item[field] || '未知',
        itemStyle: { color: palette[index % palette.length] }
      }))
    }]
  }
}

function findTrendSeries(name) {
  return trend.value.series?.find(item => item.name === name)?.data || []
}

function formatNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '--'
}

function formatPct(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)}%` : '--'
}

function formatSignedPct(value) {
  return Number.isFinite(Number(value))
    ? `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(2)}%`
    : '--'
}

function insightValue(item) {
  if (!item) return '--'
  return `${Number(item.value || 0).toFixed(item.unit === 'HHI' || item.unit === '指数' ? 4 : 2)}${item.unit || ''}`
}

function insightDirectionClass(direction) {
  if (direction === 'up') return 'is-up'
  if (direction === 'down') return 'is-down'
  return 'is-neutral'
}
</script>

<template>
  <div class="insights-layout page-shell">
    <header class="page-header workspace-page-head">
      <div class="workspace-page-row">
        <div class="workspace-page-copy">
          <h1 class="workspace-page-title">数据洞察中心</h1>
          <p class="workspace-page-subtitle">把岗位样本、薪资走势、技能结构和区域分布转成可执行的教学与治理判断。</p>
        </div>

        <nav class="tabs-nav" aria-label="洞察视图切换">
          <button :class="['tab-btn', { active: activeTab === 'overview' }]" @click="activeTab = 'overview'">
            <BarChart3 :size="18" /> 市场洞察
          </button>
          <button :class="['tab-btn', { active: activeTab === 'skills' }]" @click="activeTab = 'skills'">
            <Award :size="18" /> 技能图谱
          </button>
          <button :class="['tab-btn', { active: activeTab === 'salary' }]" @click="activeTab = 'salary'">
            <DollarSign :size="18" /> 薪资分析
          </button>
          <button v-if="authStore.isLoggedIn" :class="['tab-btn', { active: activeTab === 'supply' }]" @click="activeTab = 'supply'">
            <Target :size="18" /> 供需诊断
          </button>
        </nav>
      </div>

      <article class="hero-panel surface section-panel workspace-module-panel">
        <div class="hero-main">
          <div class="hero-copy">
            <span class="hero-kicker">DEEP INSIGHTS</span>
            <h2>{{ headlineSummary.title }}</h2>
            <p>{{ headlineSummary.subtitle }}</p>
          </div>
          <span class="hero-badge">{{ headlineSummary.badge }}</span>
        </div>

        <div class="summary-grid">
          <div v-for="card in summaryCards" :key="card.title" class="summary-card">
            <span class="summary-label">{{ card.title }}</span>
            <strong class="summary-value">{{ card.value }}</strong>
            <small class="summary-note">{{ card.note }}</small>
          </div>
        </div>
      </article>
    </header>

    <div class="tab-content">
      <section v-if="activeTab === 'overview'" class="overview-content">
        <div v-if="isLoading" class="loading-state">
          <div class="loader-ring"></div>
          <p>正在计算深度洞察...</p>
        </div>

        <template v-else>
          <div class="chart-row two-col">
            <PremiumCard title="结构性结论" glowColor="primary">
              <div class="insight-list">
                <article v-for="item in structuralInsights" :key="item.title" class="insight-item">
                  <div class="insight-item-head">
                    <span class="insight-title">{{ item.title }}</span>
                    <strong :class="['insight-value', insightDirectionClass(item.direction)]">{{ insightValue(item) }}</strong>
                  </div>
                  <p>{{ item.summary }}</p>
                </article>
              </div>
            </PremiumCard>

            <PremiumCard title="治理建议" glowColor="teal">
              <div class="recommendation-list">
                <article v-for="(item, index) in recommendations" :key="`${index}-${item}`" class="recommendation-item">
                  <span class="recommendation-index">0{{ index + 1 }}</span>
                  <p>{{ item }}</p>
                </article>
              </div>
            </PremiumCard>
          </div>

          <div class="chart-row two-col">
            <InsightPanel title="岗位需求月度脉冲" tone="teal">
              <div class="chart-box"><v-chart v-if="insightTrendOption" class="chart" :option="insightTrendOption" autoresize /></div>
            </InsightPanel>
            <InsightPanel title="核心技能热度" tone="primary">
              <div class="chart-box"><v-chart v-if="skillBarOption" class="chart" :option="skillBarOption" autoresize /></div>
            </InsightPanel>
          </div>

          <div class="analysis-grid">
            <PremiumCard title="区域集中度" glowColor="primary">
              <div class="mini-meta">
                <div>
                  <span class="mini-label">头部城市</span>
                  <strong>{{ cityConcentration.topCity || '--' }}</strong>
                </div>
                <div>
                  <span class="mini-label">占比</span>
                  <strong>{{ formatPct(cityConcentration.topCityShare) }}</strong>
                </div>
                <div>
                  <span class="mini-label">风险</span>
                  <strong>{{ cityConcentration.riskLevel || '--' }}</strong>
                </div>
              </div>
              <div class="mini-list">
                <div v-for="item in cityConcentration.leadingCities || []" :key="item.city" class="mini-row">
                  <span>{{ item.city }}</span>
                  <strong>{{ formatPct(item.sharePct) }}</strong>
                  <small>{{ formatNumber(item.jobCount) }} 岗位</small>
                </div>
              </div>
            </PremiumCard>

            <PremiumCard title="行业动量" glowColor="purple">
              <div class="mini-list">
                <div v-for="item in industryMomentum.topGrowingIndustries || []" :key="item.industry" class="mini-row">
                  <span>{{ item.industry }}</span>
                  <strong>{{ formatSignedPct(item.growthPct) }}</strong>
                  <small>{{ formatNumber(item.currentCount) }} 岗位</small>
                </div>
              </div>
            </PremiumCard>

            <PremiumCard title="技能演化" glowColor="teal">
              <div class="mini-meta">
                <div>
                  <span class="mini-label">前五技能占比</span>
                  <strong>{{ formatPct(skillsInsight.topSkillShare) }}</strong>
                </div>
                <div>
                  <span class="mini-label">多样性指数</span>
                  <strong>{{ Number(skillsInsight.diversificationIndex || 0).toFixed(4) }}</strong>
                </div>
              </div>
              <div class="mini-list">
                <div v-for="item in skillsInsight.emergingSkills || []" :key="item.skill" class="mini-row">
                  <span>{{ item.skill }}</span>
                  <strong>{{ formatSignedPct(item.growthPct) }}</strong>
                  <small>{{ formatNumber(item.count) }} 次出现</small>
                </div>
              </div>
            </PremiumCard>
          </div>

          <div class="chart-row two-col">
            <InsightPanel title="城市岗位分布" tone="primary">
              <div class="chart-box"><v-chart v-if="cityPieOption" class="chart" :option="cityPieOption" autoresize /></div>
            </InsightPanel>
            <InsightPanel title="行业需求占比" tone="purple">
              <div class="chart-box"><v-chart v-if="industryPieOption" class="chart" :option="industryPieOption" autoresize /></div>
            </InsightPanel>
          </div>

          <InsightPanel title="薪资趋势分析" tone="secondary">
            <div class="chart-box-wide"><v-chart v-if="salaryTrendOption" class="chart" :option="salaryTrendOption" autoresize /></div>
          </InsightPanel>

          <div class="chart-row two-col">
            <PremiumCard title="企业规模分布" glowColor="primary">
              <div class="chart-box"><v-chart v-if="companySizePieOption" class="chart" :option="companySizePieOption" autoresize /></div>
            </PremiumCard>
            <PremiumCard title="融资阶段分布" glowColor="purple">
              <div class="chart-box"><v-chart v-if="financeStagePieOption" class="chart" :option="financeStagePieOption" autoresize /></div>
            </PremiumCard>
          </div>

          <PremiumCard title="热门福利词频" glowColor="teal">
            <div class="chart-box-wide"><v-chart v-if="welfareBarOption" class="chart" :option="welfareBarOption" autoresize /></div>
          </PremiumCard>
        </template>
      </section>

      <section v-else-if="activeTab === 'skills'" class="tab-wrapper">
        <SkillMapView />
      </section>

      <section v-else-if="activeTab === 'salary'" class="tab-wrapper">
        <SalaryView />
      </section>

      <section v-else-if="activeTab === 'supply'" class="tab-wrapper">
        <SupplyDemandView :token="authStore.token" />
      </section>
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
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tabs-nav {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid rgba(193, 198, 215, 0.42);
  background: rgba(255, 255, 255, 0.52);
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  transition: all var(--duration-fast) var(--ease-out);
}

.tab-btn:hover,
.tab-btn.active {
  color: var(--c-accent-primary);
  border-color: rgba(30, 117, 255, 0.26);
  background: rgba(30, 117, 255, 0.08);
}

.hero-panel {
  gap: 18px;
  border-color: rgba(0, 89, 199, 0.12);
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(244, 249, 255, 0.9));
}

.hero-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hero-kicker,
.summary-label,
.mini-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-accent-primary);
}

.hero-copy h2 {
  margin: 0;
  font-size: clamp(22px, 2vw, 28px);
  letter-spacing: -0.04em;
  color: var(--c-text-primary);
}

.hero-copy p,
.insight-item p,
.recommendation-item p {
  margin: 0;
  color: var(--c-text-secondary);
  line-height: 1.6;
}

.hero-badge {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(0, 89, 199, 0.14);
  background: rgba(217, 226, 255, 0.72);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.summary-grid,
.analysis-grid,
.chart-row {
  display: grid;
  gap: 20px;
}

.summary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.summary-card,
.insight-item,
.recommendation-item,
.mini-row {
  border: 1px solid rgba(193, 198, 215, 0.34);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
}

.summary-value {
  font-size: 24px;
  color: var(--c-text-primary);
  letter-spacing: -0.03em;
}

.summary-note {
  color: var(--c-text-secondary);
  line-height: 1.5;
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.analysis-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.insight-list,
.recommendation-list,
.mini-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.insight-item,
.recommendation-item {
  padding: 14px 16px;
}

.insight-item-head,
.mini-row,
.mini-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.insight-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--c-text-secondary);
}

.insight-value {
  font-size: 16px;
  letter-spacing: -0.03em;
}

.is-up {
  color: #0f9f6e;
}

.is-down {
  color: #dc2626;
}

.is-neutral {
  color: var(--c-accent-primary);
}

.recommendation-item {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
}

.recommendation-index {
  color: var(--c-accent-primary);
  font-size: 16px;
  font-weight: 800;
}

.mini-meta {
  margin-bottom: 12px;
}

.mini-meta > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mini-meta strong,
.mini-row strong {
  color: var(--c-text-primary);
}

.mini-row {
  padding: 12px 14px;
}

.mini-row small {
  color: var(--c-text-secondary);
}

.chart-box {
  width: 100%;
  height: 340px;
}

.chart-box-wide {
  width: 100%;
  height: 400px;
}

.chart {
  width: 100%;
  height: 100%;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 360px;
  color: var(--c-text-secondary);
}

.loader-ring {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 4px solid rgba(30, 117, 255, 0.14);
  border-top-color: var(--c-accent-primary);
  animation: spin 1s linear infinite;
}

.tab-wrapper {
  animation: fade-slide var(--duration-normal) var(--ease-out);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes fade-slide {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .summary-grid,
  .analysis-grid,
  .two-col {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .hero-main,
  .summary-grid,
  .analysis-grid,
  .two-col {
    grid-template-columns: 1fr;
    display: grid;
  }

  .tabs-nav {
    flex-wrap: wrap;
  }

  .hero-badge {
    justify-self: start;
  }

  .chart-box {
    height: 300px;
  }

  .chart-box-wide {
    height: 340px;
  }
}
</style>
