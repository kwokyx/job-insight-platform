<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import InsightPanel from '../components/insights/InsightPanel.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchAnalysisOverview, fetchSalaryTrend, fetchWelfareDistribution, fetchCompanySizeDistribution, fetchFinanceStageDistribution } from '../api'
import { chartPalette, withAlpha } from '../constants/chartPalette'
import { mapErrorMessage } from '../utils/errorMap'

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
const overviewError = ref('')
const trendError = ref('')
const partialErrors = ref([])
const salaryTrendData = ref(null)
const welfareData = ref(null)
const companySizeData = ref(null)
const financeStageData = ref(null)
const activeTab = ref('overview')

const tabs = [
  { id: 'overview', label: '市场大盘', desc: '全局供需与分布', icon: BarChart3, accent: '#0057c2', accentSoft: 'rgba(0, 89, 199, 0.12)' },
  { id: 'skills', label: '技能图谱', desc: '高频技能与能力栈', icon: Award, accent: '#8b5cf6', accentSoft: 'rgba(139, 92, 246, 0.14)' },
  { id: 'salary', label: '薪资分析', desc: '区间、城市与趋势', icon: DollarSign, accent: '#f97316', accentSoft: 'rgba(249, 115, 22, 0.14)' }
]
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
const topIndustryLabel = computed(() => topIndustry.value?.industry || topIndustry.value?.industryName || '暂无数据')
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
        { label: '热门行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
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
        { label: '热门行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
        { label: '高频技能', value: topSkill.value?.skill || '暂无数据', note: topSkill.value ? `${formatNumber(topSkill.value.count)} 次出现` : '等待同步' }
      ]
    }
  }

  return {
    kicker: '',
    headline: topCityLabel.value,
    badge: formattedSalaryRange.value,
    summary: topCity.value
      ? `${topCityLabel.value} 仍然是当前最活跃的岗位中心。`
      : '等待市场信号同步。',
    rows: [
      { label: '样本岗位', value: formattedTotalJobs.value, note: '分析样本' },
      { label: '热门行业', value: topIndustryLabel.value, note: topIndustry.value ? `${formatNumber(topIndustry.value.count)} 岗位` : '等待同步' },
      { label: '高频技能', value: topSkill.value?.skill || '暂无数据', note: topSkill.value ? `${formatNumber(topSkill.value.count)} 次出现` : '等待同步' }
    ]
  }
})

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

function normalizeChartDataset(payload) {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

function normalizeTrendSeries(trend, seriesName) {
  if (Array.isArray(trend?.series)) {
    const match = trend.series.find((item) => item.name === seriesName)
    if (Array.isArray(match?.data)) {
      return match.data
    }
  }

  if (Array.isArray(trend?.data)) {
    return trend.data.map((item) => item?.[seriesName] ?? null)
  }

  return []
}

// 先加载关键数据，快速显示页面
async function loadCoreData() {
  console.time('[Insights] overview')
  overviewError.value = ''
  try {
    overview.value = await fetchAnalysisOverview()
  } catch (e) {
    overview.value = null
    overviewError.value = mapErrorMessage(e)
  } finally {
    isLoading.value = false
    console.timeEnd('[Insights] overview')
  }
}

// 后台异步加载图表数据，不阻塞初始渲染
async function loadChartData() {
  console.time('[Insights] charts')
  partialErrors.value = []
  try {
    const results = await Promise.allSettled([
      fetchWelfareDistribution(15),
      fetchCompanySizeDistribution(),
      fetchFinanceStageDistribution()
    ])
    const [welf, cSize, fin] = results

    if (welf.status === 'fulfilled') {
      welfareData.value = normalizeChartDataset(welf.value)
    } else {
      welfareData.value = []
      partialErrors.value.push(`福利分布暂不可用：${mapErrorMessage(welf.reason)}`)
    }

    if (cSize.status === 'fulfilled') {
      companySizeData.value = normalizeChartDataset(cSize.value)
    } else {
      companySizeData.value = []
      partialErrors.value.push(`企业规模分布暂不可用：${mapErrorMessage(cSize.reason)}`)
    }

    if (fin.status === 'fulfilled') {
      financeStageData.value = normalizeChartDataset(fin.value)
    } else {
      financeStageData.value = []
      partialErrors.value.push(`融资阶段分布暂不可用：${mapErrorMessage(fin.reason)}`)
    }
  } catch (e) {
    console.warn('图表数据加载失败:', e)
  } finally {
    console.timeEnd('[Insights] charts')
  }
}

onMounted(async () => {
  await loadCoreData()
  // 后台加载图表，不阻塞UI
  loadChartData().catch(() => {})
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

async function loadSalaryTrendData() {
  if (salaryTrendData.value || trendLoading.value) return
  console.time('[Insights] salary-trend')
  trendLoading.value = true
  trendError.value = ''
  try {
    salaryTrendData.value = await fetchSalaryTrend()
  } catch (e) {
    salaryTrendData.value = null
    trendError.value = mapErrorMessage(e)
  } finally {
    trendLoading.value = false
    console.timeEnd('[Insights] salary-trend')
  }
}

watch(activeTab, (tab) => {
  if (tab === 'overview') {
    loadSalaryTrendData()
  }
}, { immediate: true })

const cityPieOption = computed(() => {
  if (!overview.value?.topCities?.length) return null
  const t = getEchartsTheme()
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { show: false },
    series: [{
      type: 'pie', radius: ['38%', '72%'],
      label: { show: true, color: t.textColor, formatter: '{b}\n{d}%' },
      data: overview.value.topCities.slice(0, 10).map((c, i) => ({ value: c.count, name: c.city, itemStyle: { color: chartPalette.series[i % chartPalette.series.length] } }))
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
      data: overview.value.topIndustries.slice(0, 10).map((ind, i) => ({ value: ind.count, name: ind.industryName || ind.industry, itemStyle: { color: chartPalette.series[(i + 3) % chartPalette.series.length] } }))
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
    series: [{ type: 'bar', barWidth: '50%', itemStyle: { color: chartPalette.lavender, borderRadius: [5, 5, 0, 0] }, data: sorted.map(e => ({ value: e.count })) }]
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
    series: [{ type: 'radar', areaStyle: { color: withAlpha(chartPalette.teal, 0.22) }, lineStyle: { color: chartPalette.teal }, itemStyle: { color: chartPalette.teal }, data: [{ value: data.map(d => d.count), name: '岗位数量' }] }]
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
      { name: '平均薪资上限', type: 'line', smooth: true, itemStyle: { color: chartPalette.coral }, lineStyle: { color: chartPalette.coral, width: 3 }, data: normalizeTrendSeries(trend, 'avgSalaryMax') },
      { name: '平均薪资下限', type: 'line', smooth: true, itemStyle: { color: chartPalette.blue }, lineStyle: { color: chartPalette.blue, width: 2.5 }, data: normalizeTrendSeries(trend, 'avgSalaryMin') }
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
    series: [{ type: 'bar', barWidth: '60%', itemStyle: { color: chartPalette.coral, borderRadius: [0, 4, 4, 0] }, data: skills.map(s => ({ value: s.count })) }]
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
    series: [{ type: 'bar', barWidth: '55%', itemStyle: { color: chartPalette.blue, borderRadius: [6, 6, 0, 0] }, data: cities.map(c => ({ value: c.avgSalary })) }]
  }
})

const welfareBarOption = computed(() => {
  if (!welfareData.value?.length) return null
  const t = getEchartsTheme()
  const data = [...welfareData.value].sort((a, b) => a.count - b.count)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    grid: { left: '4%', right: '8%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'category', data: data.map(w => w.welfare), axisLabel: { color: t.textColor } },
    series: [{ type: 'bar', barWidth: '60%', itemStyle: { color: chartPalette.mauve, borderRadius: [0, 4, 4, 0] }, data: data.map(w => ({ value: w.count })) }]
  }
})

const companySizePieOption = computed(() => {
  if (!companySizeData.value?.length) return null
  const t = getEchartsTheme()
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { show: false },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: true, color: t.textColor, formatter: '{b}\n{d}%' },
      // 与顶部"城市岗位分布"同一配色系，保持视觉一致
      data: companySizeData.value.map((c, i) => ({ value: c.count, name: c.companySize || '未知', itemStyle: { color: chartPalette.series[i % chartPalette.series.length] } }))
    }]
  }
})

const financeStagePieOption = computed(() => {
  if (!financeStageData.value?.length) return null
  const t = getEchartsTheme()
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.splitLineColor },
    legend: { show: false },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: { show: true, color: t.textColor, formatter: '{b}\n{d}%' },
      // 与"行业需求占比"用同一偏移配色，避免两个饼图完全同色
      data: financeStageData.value.map((c, i) => ({ value: c.count, name: c.financeStage || '未知', itemStyle: { color: chartPalette.series[(i + 3) % chartPalette.series.length] } }))
    }]
  }
})
</script>

<template>
  <div class="insights-layout page-shell">
    <header class="page-header workspace-page-head">
      <nav class="hero-tabs" aria-label="洞察视图切换">
        <button
          v-for="t in tabs"
          :key="t.id"
          type="button"
          :class="['hero-tab', { active: activeTab === t.id }]"
          :style="{ '--tab-accent': t.accent, '--tab-accent-soft': t.accentSoft }"
          @click="activeTab = t.id"
        >
          <span class="hero-tab-icon"><component :is="t.icon" :size="20" /></span>
          <span class="hero-tab-label">
            <strong>{{ t.label }}</strong>
            <small>{{ t.desc }}</small>
          </span>
          <span v-if="activeTab === t.id" class="hero-tab-dot" aria-hidden="true" />
        </button>
      </nav>

      <article class="signal-board surface section-panel workspace-module-panel">
        <div class="panel-head workspace-panel-head">
          <div class="workspace-panel-copy">
            <h2 class="workspace-panel-title inline-icon"><BarChart3 :size="15" /> 市场信号</h2>
          </div>
          <span class="signal-badge">{{ contextualSignal.badge }}</span>
        </div>

        <div class="signal-board-body">
          <div class="signal-copy-block">
            <span v-if="contextualSignal.kicker" class="signal-kicker">{{ contextualSignal.kicker }}</span>
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
        </div>
      </article>
      <p v-if="partialErrors.length" class="signal-hint">{{ partialErrors.join('；') }}</p>
    </header>

    <div class="tab-content">
      <Transition name="insights-section" mode="out-in">
        <section v-if="activeTab === 'overview'" key="overview" class="overview-content">
          <div v-if="isLoading" class="insights-skel">
            <div class="insights-skel-row">
              <SkeletonCard type="chart" />
              <SkeletonCard type="chart" />
            </div>
            <SkeletonCard type="chart" />
          </div>
          <div v-else-if="overviewError" class="empty-state-wrapper glass-panel">
            <EmptyState icon="error" title="洞察数据加载失败" :description="overviewError" />
          </div>
          <template v-else>
            <div class="chart-row two-col">
              <InsightPanel title="城市岗位分布" tone="primary">
                <div class="chart-box">
                  <v-chart v-if="cityPieOption" class="chart" :option="cityPieOption" autoresize />
                  <div v-else class="empty-chart">暂无城市岗位分布数据</div>
                </div>
              </InsightPanel>
              <InsightPanel title="行业需求占比" tone="purple">
                <div class="chart-box">
                  <v-chart v-if="industryPieOption" class="chart" :option="industryPieOption" autoresize />
                  <div v-else class="empty-chart">暂无行业需求分布数据</div>
                </div>
              </InsightPanel>
            </div>
            <InsightPanel title="技能热度排行" tone="teal">
              <div class="chart-box-wide">
                <v-chart v-if="skillBarOption" class="chart" :option="skillBarOption" autoresize />
                <div v-else class="empty-chart">暂无技能热度排行数据</div>
              </div>
            </InsightPanel>
            <InsightPanel title="薪资趋势分析" tone="secondary">
              <div class="chart-box-wide">
                <v-chart v-if="salaryTrendOption" class="chart" :option="salaryTrendOption" autoresize />
                <div v-else class="empty-chart">{{ trendError || '暂无薪资趋势数据' }}</div>
              </div>
            </InsightPanel>
            <div class="chart-row three-col">
              <InsightPanel title="学历需求" tone="purple">
                <div class="chart-box">
                  <v-chart v-if="educationBarOption" class="chart" :option="educationBarOption" autoresize />
                  <div v-else class="empty-chart">暂无学历需求数据</div>
                </div>
              </InsightPanel>
              <InsightPanel title="经验要求" tone="teal">
                <div class="chart-box">
                  <v-chart v-if="experienceRadarOption" class="chart" :option="experienceRadarOption" autoresize />
                  <div v-else class="empty-chart">暂无经验要求数据</div>
                </div>
              </InsightPanel>
              <InsightPanel title="城市薪资" tone="amber">
                <div class="chart-box">
                  <v-chart v-if="citySalaryOption" class="chart" :option="citySalaryOption" autoresize />
                  <div v-else class="empty-chart">暂无城市薪资数据</div>
                </div>
              </InsightPanel>
            </div>
            <div class="chart-row two-col">
              <InsightPanel title="企业规模分布" tone="primary">
                <div class="chart-box">
                  <v-chart v-if="companySizePieOption" class="chart" :option="companySizePieOption" autoresize />
                  <div v-else class="empty-chart">暂无企业规模分布数据</div>
                </div>
              </InsightPanel>
              <InsightPanel title="融资阶段分布" tone="purple">
                <div class="chart-box">
                  <v-chart v-if="financeStagePieOption" class="chart" :option="financeStagePieOption" autoresize />
                  <div v-else class="empty-chart">暂无融资阶段分布数据</div>
                </div>
              </InsightPanel>
            </div>
            <InsightPanel title="热门福利词频" tone="teal">
              <div class="chart-box-wide">
                <v-chart v-if="welfareBarOption" class="chart" :option="welfareBarOption" autoresize />
                <div v-else class="empty-chart">暂无福利词频数据</div>
              </div>
            </InsightPanel>
          </template>
        </section>

        <section v-else-if="activeTab === 'skills'" key="skills" class="tab-wrapper">
          <SkillMapView />
        </section>

        <section v-else-if="activeTab === 'salary'" key="salary" class="tab-wrapper">
          <SalaryView />
        </section>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.insights-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.insights-skel { display: flex; flex-direction: column; gap: 16px; }
.insights-skel-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 900px) { .insights-skel-row { grid-template-columns: 1fr; } }

.section-panel {
  gap: 16px;
}

.page-header {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: end;
}

.page-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0 0;
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
  gap: 16px;
  border-color: rgba(0, 89, 199, 0.12);
  background:
    radial-gradient(circle at top right, rgba(0, 89, 199, 0.08), transparent 34%),
    var(--c-bg-surface-strong);
}

.signal-board .workspace-panel-title {
  color: var(--c-accent-primary);
}

.signal-board-body {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.4fr);
  gap: 16px;
  align-items: start;
}

.signal-copy-block {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
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
  justify-content: center;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(0, 89, 199, 0.14);
  background: rgba(217, 226, 255, 0.62);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
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

.signal-hint {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(245, 158, 11, 0.2);
  background: rgba(255, 247, 237, 0.82);
  color: #9a6700;
  font-size: 13px;
  line-height: 1.5;
}

.signal-summary {
  font-size: 13px;
  line-height: 1.55;
}

.signal-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
  padding-left: 18px;
  border-left: 1px solid rgba(193, 198, 215, 0.42);
}

.signal-row {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 2px 0;
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

.hero-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 10px;
  border-radius: 18px;
  border: 1px solid var(--c-border-glass);
  background:
    linear-gradient(135deg, rgba(0, 89, 199, 0.05), rgba(139, 92, 246, 0.04) 55%, rgba(249, 115, 22, 0.05)),
    var(--c-bg-surface);
  backdrop-filter: blur(8px);
  box-shadow: 0 12px 28px -18px rgba(16, 24, 40, 0.14);
}

.hero-tab {
  --tab-accent: var(--c-accent-primary);
  --tab-accent-soft: rgba(0, 89, 199, 0.1);
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--c-text-secondary);
  text-align: left;
  cursor: pointer;
  transition:
    background-color 220ms var(--ease-out),
    border-color 220ms var(--ease-out),
    color 220ms var(--ease-out),
    transform 220ms var(--ease-out),
    box-shadow 260ms var(--ease-out);
}

.hero-tab:hover {
  background: rgba(255, 255, 255, 0.55);
  border-color: rgba(193, 198, 215, 0.5);
  transform: translateY(-1px);
}

.hero-tab.active {
  background:
    linear-gradient(135deg, var(--tab-accent-soft), rgba(255, 255, 255, 0)) ,
    var(--c-bg-surface-strong);
  border-color: color-mix(in srgb, var(--tab-accent) 32%, transparent);
  color: var(--c-text-primary);
  box-shadow: 0 14px 32px -18px var(--tab-accent);
}

.hero-tab-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--tab-accent-soft);
  color: var(--tab-accent);
  transition:
    background-color 220ms var(--ease-out),
    color 220ms var(--ease-out),
    box-shadow 260ms var(--ease-out);
}

.hero-tab.active .hero-tab-icon {
  background: var(--tab-accent);
  color: #fff;
  box-shadow: 0 10px 22px -10px var(--tab-accent);
}

.hero-tab-label {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.hero-tab-label strong {
  color: var(--c-text-primary);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.hero-tab-label small {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.hero-tab.active .hero-tab-label strong {
  color: var(--tab-accent);
}

.hero-tab-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--tab-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--tab-accent) 22%, transparent);
}

.tab-content {
  display: flex;
  flex-direction: column;
}

.insights-section-enter-active,
.insights-section-leave-active {
  transition:
    opacity 220ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 280ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 280ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity, transform, filter;
  transform-origin: top left;
}

.insights-section-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
  filter: blur(10px);
}

.insights-section-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.992);
  filter: blur(8px);
}

.insights-section-enter-to,
.insights-section-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.tab-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
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

.empty-state-wrapper {
  min-height: 360px;
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  font-size: 15px;
  text-align: center;
  padding: 0 20px;
}

:global([data-theme="dark"]) .signal-hint {
  border-color: rgba(245, 158, 11, 0.18);
  background: rgba(120, 53, 15, 0.18);
  color: #f8d48a;
}

@media (prefers-reduced-motion: reduce) {
  .insights-section-enter-active,
  .insights-section-leave-active {
    transition: opacity 120ms ease;
  }

  .insights-section-enter-from,
  .insights-section-leave-to,
  .insights-section-enter-to,
  .insights-section-leave-from {
    transform: none;
    filter: none;
  }
}

@media (max-width: 1200px) {
  .page-header-top {
    grid-template-columns: 1fr;
  }

  .signal-board-body {
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

  .three-col { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 900px) {
  .hero-tabs {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-copy {
    padding: 0;
  }

  .signal-badge {
    white-space: normal;
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
