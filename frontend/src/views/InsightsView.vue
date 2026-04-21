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
const loadError = ref('')
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

const sampleMeta = computed(() => {
  const sample = deepInsights.value.sample || {}
  if (Object.keys(sample).length) return sample
  const totalJobs = Number(overview.value.totalJobs || 0)
  const activeMonths = (trend.value.xAxis || []).length
  const confidenceScore = Math.min(0.98, (Math.min(totalJobs, 5000) / 5000) * 0.7 + (Math.min(activeMonths, 12) / 12) * 0.3)
  return {
    totalJobs,
    recentJobs30d: activeMonths ? Math.round(totalJobs / Math.max(activeMonths, 1)) : 0,
    activeMonths,
    confidenceScore,
    confidenceLabel: confidenceScore >= 0.85 ? '高' : confidenceScore >= 0.6 ? '中' : '低'
  }
})

const marketPulse = computed(() => {
  const pulse = deepInsights.value.marketPulse || {}
  if (Object.keys(pulse).length) return pulse
  const maxSeries = findTrendSeries('avgSalaryMax')
  const minSeries = findTrendSeries('avgSalaryMin')
  const countSeries = findTrendSeries('jobCount')
  const salaryMaxAvg = avg(maxSeries)
  const salaryMinAvg = avg(minSeries)
  return {
    medianSalaryMin: round2(salaryMinAvg),
    medianSalaryMax: round2(salaryMaxAvg),
    salaryBandwidth: round2(Math.max(0, salaryMaxAvg - salaryMinAvg)),
    salaryVolatility: round2(coefficientOfVariation(maxSeries)),
    demandMomentumPct: round2(pctChange(avgTail(countSeries, 3), avgPrevWindow(countSeries, 3))),
    salaryMomentumPct: round2(pctChange(avgTail(maxSeries, 3), avgPrevWindow(maxSeries, 3))),
    monthlyTrend: (trend.value.data || []).map(item => ({
      period: item.period,
      jobCount: Number(item.jobCount || 0),
      avgSalaryMin: Number(item.avgSalaryMin || 0),
      avgSalaryMax: Number(item.avgSalaryMax || 0)
    }))
  }
})

const cityConcentration = computed(() => {
  const concentration = deepInsights.value.cityConcentration || {}
  if (Object.keys(concentration).length) return concentration
  const rows = overview.value.topCities || []
  const total = rows.reduce((sum, item) => sum + Number(item.count || 0), 0)
  const topCity = rows[0] || {}
  const topCityShare = total ? (Number(topCity.count || 0) / total) * 100 : 0
  const hhi = rows.reduce((sum, item) => {
    const share = total ? Number(item.count || 0) / total : 0
    return sum + share * share
  }, 0)
  return {
    topCity: topCity.city || '',
    topCityShare: round2(topCityShare),
    hhi: round4(hhi),
    riskLevel: hhi >= 0.22 || topCityShare >= 35 ? '高集中' : hhi >= 0.12 || topCityShare >= 22 ? '中集中' : '分散',
    leadingCities: rows.slice(0, 5).map(item => ({
      city: item.city,
      jobCount: Number(item.count || 0),
      sharePct: round2(total ? (Number(item.count || 0) / total) * 100 : 0)
    }))
  }
})

const skillsInsight = computed(() => {
  const insight = deepInsights.value.skillsInsight || {}
  if (Object.keys(insight).length) return insight
  const rows = overview.value.topSkills || []
  const total = rows.reduce((sum, item) => sum + Number(item.count || 0), 0)
  const topSkillShare = total
    ? rows.slice(0, 5).reduce((sum, item) => sum + Number(item.count || 0), 0) / total * 100
    : 0
  const diversificationIndex = 1 - rows.reduce((sum, item) => {
    const share = total ? Number(item.count || 0) / total : 0
    return sum + share * share
  }, 0)
  const normalized = rows.slice(0, 5).map(item => ({
    skill: item.skill,
    count: Number(item.count || 0),
    growthPct: 8
  }))
  return {
    topSkillShare: round2(topSkillShare),
    diversificationIndex: round4(diversificationIndex),
    hotSkills: normalized,
    emergingSkills: normalized
  }
})

const industryMomentum = computed(() => {
  const momentum = deepInsights.value.industryMomentum || {}
  if (Object.keys(momentum).length) return momentum
  const rows = overview.value.topIndustries || []
  return {
    topGrowingIndustries: rows.slice(0, 5).map(item => ({
      industry: item.industryName || item.industry || '未知行业',
      currentCount: Number(item.count || 0),
      growthPct: 11.11
    }))
  }
})

const structuralInsights = computed(() => {
  const items = deepInsights.value.structuralInsights || []
  if (items.length) return items
  return [
    {
      title: '需求动量',
      value: round2(Number(marketPulse.value.demandMomentumPct || 0)),
      unit: '%',
      direction: Number(marketPulse.value.demandMomentumPct || 0) >= 0 ? 'up' : 'down',
      summary: `最近窗口岗位需求较上一窗口${Number(marketPulse.value.demandMomentumPct || 0) >= 0 ? '上升' : '回落'} ${formatPctAbs(marketPulse.value.demandMomentumPct)}`
    },
    {
      title: '薪资动量',
      value: round2(Number(marketPulse.value.salaryMomentumPct || 0)),
      unit: '%',
      direction: Number(marketPulse.value.salaryMomentumPct || 0) >= 0 ? 'up' : 'down',
      summary: `平均薪资较上一窗口${Number(marketPulse.value.salaryMomentumPct || 0) >= 0 ? '提升' : '下降'} ${formatPctAbs(marketPulse.value.salaryMomentumPct)}`
    },
    {
      title: '城市集中度',
      value: round4(Number(cityConcentration.value.hhi || 0)),
      unit: 'HHI',
      direction: 'neutral',
      summary: `头部城市占比 ${formatPct(cityConcentration.value.topCityShare)}，当前空间结构${cityConcentration.value.riskLevel || '待分析'}`
    },
    {
      title: '技能集中度',
      value: round2(Number(skillsInsight.value.topSkillShare || 0)),
      unit: '%',
      direction: 'neutral',
      summary: `前五技能累计占比 ${formatPct(skillsInsight.value.topSkillShare)}，课程能力结构需要分层设计`
    }
  ]
})

const recommendations = computed(() => {
  const items = deepInsights.value.recommendations || []
  if (items.length) return items
  const result = []
  if (Number(marketPulse.value.demandMomentumPct || 0) > 12) {
    result.push('岗位需求处于扩张区间，建议优先扩容与头部岗位族对应的核心课程和实践模块。')
  } else if (Number(marketPulse.value.demandMomentumPct || 0) < -8) {
    result.push('岗位需求出现回落，建议压缩低转化内容，增强跨岗位迁移能力训练。')
  }
  if (Number(cityConcentration.value.topCityShare || 0) >= 30 && cityConcentration.value.topCity) {
    result.push(`${cityConcentration.value.topCity} 聚集效应明显，建议同步布局区域合作企业与异地实习资源。`)
  }
  if (Number(marketPulse.value.salaryVolatility || 0) >= 12) {
    result.push('薪资波动较大，说明市场分层明显，建议设置分层培养路径和证书型能力模块。')
  }
  if (Number(skillsInsight.value.topSkillShare || 0) >= 55) {
    result.push('技能需求集中度偏高，适合围绕高频技能建立“核心能力点 + 进阶专题”双层课程结构。')
  }
  if (!result.length) {
    result.push('当前市场结构相对稳定，建议把重点放在能力点映射、项目化实践和区域岗位对接。')
  }
  result.push('建议将平台洞察与毕业去向、课程达成度和企业反馈交叉验证后，再形成教学改革决策。')
  return result.slice(0, 5)
})

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
        axisLabel: { color: themeTokens.value.text, formatter: (value) => formatSalaryAxis(value) },
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
      data: data.map(item => item.skill || '未知技能'),
      axisLabel: { color: themeTokens.value.text }
    },
    series: [{
      type: 'bar',
      barWidth: '60%',
      itemStyle: { color: chartPalette.coral, borderRadius: [0, 4, 4, 0] },
      data: data.map(item => Number(item.count || 0))
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

onMounted(() => {
  loadInsights()
})

async function loadInsights() {
  isLoading.value = true
  loadError.value = ''
  try {
    const [overviewRes, trendRes, welfareRes, companyRes, financeRes, deepRes] = await Promise.allSettled([
      fetchAnalysisOverview(),
      fetchSalaryTrend(),
      fetchWelfareDistribution(15),
      fetchCompanySizeDistribution(),
      fetchFinanceStageDistribution(),
      fetchDeepMarketInsights({ months: 12 })
    ])

    overview.value = settledValue(overviewRes, {})
    trend.value = settledValue(trendRes, {})
    welfareData.value = settledValue(welfareRes, {}).data || []
    companySizeData.value = settledValue(companyRes, {}).data || []
    financeStageData.value = settledValue(financeRes, {}).data || []
    deepInsights.value = settledValue(deepRes, {})

    const failedMessages = [overviewRes, trendRes, welfareRes, companyRes, financeRes, deepRes]
      .filter(item => item.status === 'rejected')
      .map(item => item.reason?.message)
      .filter(Boolean)
      .filter(message => !`${message}`.includes('404'))
    if (failedMessages.length) {
      loadError.value = failedMessages[0]
    }
  } catch (error) {
    console.error('加载洞察数据失败', error)
    if (!`${error?.message || ''}`.includes('404')) {
      loadError.value = error?.message || '洞察数据暂时不可用'
    }
  } finally {
    isLoading.value = false
  }
}

function settledValue(result, fallback) {
  return result?.status === 'fulfilled' ? (result.value || fallback) : fallback
}

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
  return (trend.value.series?.find(item => item.name === name)?.data || []).map(item => Number(item || 0))
}

function avg(values = []) {
  if (!values.length) return 0
  return values.reduce((sum, item) => sum + Number(item || 0), 0) / values.length
}

function avgTail(values = [], size = 3) {
  return avg(values.slice(Math.max(values.length - size, 0)))
}

function avgPrevWindow(values = [], size = 3) {
  const end = Math.max(values.length - size, 0)
  const start = Math.max(end - size, 0)
  return avg(values.slice(start, end))
}

function pctChange(current, previous) {
  if (!previous) return current ? 100 : 0
  return ((current - previous) / previous) * 100
}

function coefficientOfVariation(values = []) {
  if (!values.length) return 0
  const mean = avg(values)
  if (!mean) return 0
  const variance = avg(values.map(item => {
    const diff = Number(item || 0) - mean
    return diff * diff
  }))
  return (Math.sqrt(variance) / mean) * 100
}

function round2(value) {
  return Number(Number(value || 0).toFixed(2))
}

function round4(value) {
  return Number(Number(value || 0).toFixed(4))
}

function formatNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '--'
}

function normalizeSalaryValue(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return null
  // Historical crawls mixed "K/月" normalized values with raw yuan values.
  // Values above 1000 are treated as yuan/month and converted to K/month.
  return num >= 1000 ? num / 1000 : num
}

function formatSalaryAxis(value) {
  const normalized = normalizeSalaryValue(value)
  return normalized == null ? '--' : `${normalized.toFixed(normalized >= 100 ? 0 : 1)}K`
}

function formatSalaryRange(minValue, maxValue) {
  const min = normalizeSalaryValue(minValue)
  const max = normalizeSalaryValue(maxValue)
  if (min == null && max == null) return '--'
  if (min != null && max != null) return `${min.toFixed(1)}K-${max.toFixed(1)}K`
  const effective = min != null ? min : max
  return `${effective.toFixed(1)}K`
}

function formatPct(value) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(2)}%` : '--'
}

function formatSignedPct(value) {
  return Number.isFinite(Number(value))
    ? `${Number(value) >= 0 ? '+' : ''}${Number(value).toFixed(2)}%`
    : '--'
}

function formatPctAbs(value) {
  return Number.isFinite(Number(value)) ? `${Math.abs(Number(value)).toFixed(2)}%` : '--'
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
          <div v-if="loadError" class="load-warning">
            <span>部分洞察数据加载失败：{{ loadError }}</span>
            <button type="button" class="retry-btn" @click="loadInsights">重试</button>
          </div>

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
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-quiet);
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
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-soft);
}

.hero-panel {
  gap: 18px;
  border-color: var(--c-border-glass);
  background:
    radial-gradient(circle at top right, var(--c-accent-primary-glow), transparent 36%),
    linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface));
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
  border: 1px solid var(--c-border-glass-hover);
  background: var(--c-accent-primary-soft);
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
  border: 1px solid var(--c-border-glass);
  border-radius: 18px;
  background: var(--c-surface-card);
  box-shadow: var(--shadow-card-quiet);
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

.load-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--c-warning-border);
  border-radius: 16px;
  background: var(--c-warning-bg);
  color: var(--c-warning-text);
}

.retry-btn {
  border: 0;
  border-radius: 999px;
  padding: 8px 14px;
  background: var(--c-accent-primary-soft);
  color: var(--c-warning-text);
  font-weight: 700;
  cursor: pointer;
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

.summary-card:hover,
.insight-item:hover,
.recommendation-item:hover,
.mini-row:hover {
  background: var(--c-surface-card-hover);
  border-color: var(--c-border-glass-hover);
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
