<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import StatWidget from '../components/common/StatWidget.vue'
import { fetchSalaryAnalysis, fetchSalaryTrend, fetchJobsByEducation, fetchJobsByExperience } from '../api'
import { DollarSign, TrendingUp, BarChart3, MapPin, Calculator, Cpu, ArrowRight } from 'lucide-vue-next'

use([CanvasRenderer, BarChart, LineChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const isLoading = ref(true)
const cityData = ref([])
const educationData = ref([])
const experienceData = ref([])
const trendData = ref(null)
const trendCity = ref('')
const trendIndustry = ref('')

const darkTheme = {
  tooltipBg: 'rgba(15, 23, 42, 0.95)',
  splitLine: 'rgba(255,255,255,0.06)'
}

onMounted(async () => {
  try {
    const [cityRes, eduRes, expRes, trendRes] = await Promise.all([
      fetchSalaryAnalysis('city', 15),
      fetchJobsByEducation(),
      fetchJobsByExperience(),
      fetchSalaryTrend()
    ])
    cityData.value = cityRes.data || []
    educationData.value = eduRes || []
    experienceData.value = expRes || []
    trendData.value = trendRes
  } catch (e) {
    console.error('加载薪资分析失败', e)
  } finally {
    isLoading.value = false
  }
})

const loadTrend = async () => {
  try {
    trendData.value = await fetchSalaryTrend({ city: trendCity.value, industry: trendIndustry.value })
  } catch (e) {
    console.error('加载趋势失败', e)
  }
}

// 城市薪资柱状图
const citySalaryChart = ref(null)
watch(() => cityData.value, (data) => {
  if (!data.length) return
  const sorted = [...data].filter(d => d.avgSalary).sort((a, b) => b.avgSalary - a.avgSalary).slice(0, 12)
  citySalaryChart.value = {
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: darkTheme.tooltipBg,
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#F8FAFC' },
      formatter: (p) => `${p[0].name}<br/>平均薪资: <b>${p[0].value}K</b><br/>岗位数: ${sorted[p[0].dataIndex]?.count || '-'}`
    },
    grid: { left: '4%', right: '4%', bottom: '15%', top: '6%', containLabel: true },
    xAxis: {
      type: 'category', data: sorted.map(d => d.city),
      axisLabel: { color: '#CBD5E1', fontSize: 12, rotate: 30 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94A3B8', formatter: '{value}K' },
      splitLine: { lineStyle: { color: darkTheme.splitLine } }
    },
    series: [{
      type: 'bar', barWidth: '55%',
      data: sorted.map(d => ({
        value: d.avgSalary,
        itemStyle: {
          color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: '#F97316' }, { offset: 1, color: 'rgba(249,115,22,0.25)' }]
          },
          borderRadius: [6, 6, 0, 0]
        }
      }))
    }]
  }
}, { immediate: true })

// 学历-薪资对比图
const eduSalaryChart = ref(null)
watch(() => educationData.value, (data) => {
  if (!data.length) return
  const order = ['大专', '本科', '硕士', '博士']
  const sorted = [...data].filter(d => d.avgSalary).sort((a, b) => {
    const ia = order.indexOf(a.education)
    const ib = order.indexOf(b.education)
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib)
  })
  eduSalaryChart.value = {
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: darkTheme.tooltipBg,
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#F8FAFC' },
      formatter: (p) => `${p[0].name}<br/>平均薪资: <b>${p[0].value}K</b><br/>岗位数: <b>${p[1]?.value || '-'}</b>`
    },
    legend: { data: ['平均薪资', '岗位数量'], textStyle: { color: '#CBD5E1' }, top: 0 },
    grid: { left: '4%', right: '4%', bottom: '8%', top: '14%', containLabel: true },
    xAxis: {
      type: 'category', data: sorted.map(d => d.education),
      axisLabel: { color: '#CBD5E1' },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    yAxis: [
      { type: 'value', name: '薪资(K)', axisLabel: { color: '#94A3B8' }, splitLine: { lineStyle: { color: darkTheme.splitLine } } },
      { type: 'value', name: '岗位数', axisLabel: { color: '#94A3B8' }, splitLine: { show: false } }
    ],
    series: [
      {
        name: '平均薪资', type: 'bar', yAxisIndex: 0, barWidth: '35%',
        data: sorted.map(d => ({
          value: d.avgSalary,
          itemStyle: { color: '#A855F7', borderRadius: [4, 4, 0, 0] }
        }))
      },
      {
        name: '岗位数量', type: 'line', yAxisIndex: 1, smooth: true,
        data: sorted.map(d => d.count),
        itemStyle: { color: '#2DD4BF' },
        lineStyle: { width: 3 }
      }
    ]
  }
}, { immediate: true })

// 经验-薪资对比图
const expSalaryChart = ref(null)
watch(() => experienceData.value, (data) => {
  if (!data.length) return
  const sorted = [...data].filter(d => d.avgSalary).sort((a, b) => a.avgSalary - b.avgSalary)
  expSalaryChart.value = {
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: darkTheme.tooltipBg,
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#F8FAFC' },
      formatter: (p) => `${p[0].name}<br/>平均薪资: <b>${p[0].value}K</b><br/>岗位数: ${sorted[p[0].dataIndex]?.count || '-'}`
    },
    grid: { left: '4%', right: '4%', bottom: '8%', top: '6%', containLabel: true },
    xAxis: {
      type: 'category', data: sorted.map(d => d.experience),
      axisLabel: { color: '#CBD5E1', fontSize: 12 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94A3B8', formatter: '{value}K' },
      splitLine: { lineStyle: { color: darkTheme.splitLine } }
    },
    series: [{
      type: 'bar', barWidth: '50%',
      data: sorted.map(d => ({
        value: d.avgSalary,
        itemStyle: {
          color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: '#2DD4BF' }, { offset: 1, color: 'rgba(45,212,191,0.25)' }]
          },
          borderRadius: [6, 6, 0, 0]
        }
      }))
    }]
  }
}, { immediate: true })

// 薪资趋势折线
const trendChart = ref(null)
watch(() => trendData.value, (trend) => {
  if (!trend?.xAxis?.length) { trendChart.value = null; return }
  trendChart.value = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: darkTheme.tooltipBg,
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#F8FAFC' }
    },
    legend: { data: ['薪资下限', '薪资上限', '岗位数'], textStyle: { color: '#CBD5E1' }, top: 0 },
    grid: { left: '3%', right: '6%', bottom: '3%', top: '14%', containLabel: true },
    xAxis: {
      type: 'category', boundaryGap: false, data: trend.xAxis,
      axisLabel: { color: '#CBD5E1' },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    yAxis: [
      { type: 'value', axisLabel: { color: '#94A3B8', formatter: '{value}K' }, splitLine: { lineStyle: { color: darkTheme.splitLine } } },
      { type: 'value', name: '岗位数', axisLabel: { color: '#94A3B8' }, splitLine: { show: false } }
    ],
    series: [
      {
        name: '薪资上限', type: 'line', smooth: true, yAxisIndex: 0,
        data: trend.series?.find(s => s.name === 'avgSalaryMax')?.data || [],
        itemStyle: { color: '#F97316' }, lineStyle: { width: 3 },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(249,115,22,0.3)' }, { offset: 1, color: 'rgba(249,115,22,0)' }] } }
      },
      {
        name: '薪资下限', type: 'line', smooth: true, yAxisIndex: 0,
        data: trend.series?.find(s => s.name === 'avgSalaryMin')?.data || [],
        itemStyle: { color: '#3B82F6' }, lineStyle: { width: 3 },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.3)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } }
      },
      {
        name: '岗位数', type: 'bar', yAxisIndex: 1, barWidth: '30%',
        data: trend.series?.find(s => s.name === 'jobCount')?.data || [],
        itemStyle: { color: 'rgba(168, 85, 247, 0.3)', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }
}, { immediate: true })

// 统计
const avgSalary = computed(() => {
  const valid = cityData.value.filter(d => d.avgSalary)
  if (!valid.length) return '-'
  const avg = valid.reduce((sum, d) => sum + Number(d.avgSalary), 0) / valid.length
  return avg.toFixed(1) + 'K'
})
const highestCity = computed(() => {
  const sorted = [...cityData.value].filter(d => d.avgSalary).sort((a, b) => b.avgSalary - a.avgSalary)
  return sorted[0] || null
})
</script>

<template>
  <div class="salary-page">
    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载薪资分析...</p>
    </div>

    <template v-else>
      <!-- 概览指标 -->
      <div class="kpi-row">
        <StatWidget label="全市场均薪" :value="avgSalary" note="基于全部城市平均" glowColor="secondary" />
        <StatWidget 
          label="最高薪城市" 
          :value="highestCity?.city || '-'" 
          :note="highestCity ? `均薪 ${highestCity.avgSalary}K` : ''" 
          glowColor="primary" 
        />
        <StatWidget label="覆盖城市" :value="cityData.length" note="有数据的城市数量" glowColor="teal" />
      </div>

      <!-- 城市薪资 -->
      <PremiumCard title="城市薪资排行" glowColor="secondary">
        <div class="chart-wide">
          <v-chart v-if="citySalaryChart" class="chart" :option="citySalaryChart" autoresize />
          <div v-else class="empty-chart">暂无城市薪资数据</div>
        </div>
      </PremiumCard>

      <!-- 学历×薪资 + 经验×薪资 -->
      <div class="chart-grid-2">
        <PremiumCard title="学历与薪资对照" glowColor="purple">
          <div class="chart-mid">
            <v-chart v-if="eduSalaryChart" class="chart" :option="eduSalaryChart" autoresize />
            <div v-else class="empty-chart">暂无学历薪资数据</div>
          </div>
        </PremiumCard>

        <PremiumCard title="经验与薪资对照" glowColor="teal">
          <div class="chart-mid">
            <v-chart v-if="expSalaryChart" class="chart" :option="expSalaryChart" autoresize />
            <div v-else class="empty-chart">暂无经验薪资数据</div>
          </div>
        </PremiumCard>
      </div>

      <!-- 薪资趋势 -->
      <PremiumCard title="薪资趋势分析" glowColor="primary">
        <div class="trend-filters">
          <input v-model="trendCity" placeholder="按城市筛选" class="glass-input-sm" />
          <input v-model="trendIndustry" placeholder="按行业筛选" class="glass-input-sm" />
          <GlowButton variant="primary" @click="loadTrend">查询</GlowButton>
        </div>
        <div class="chart-wide">
          <v-chart v-if="trendChart" class="chart" :option="trendChart" autoresize />
          <div v-else class="empty-chart">暂无趋势数据</div>
        </div>
      </PremiumCard>
    </template>
  </div>
</template>

<style scoped>
.salary-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
}

.chart-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.chart-wide { height: 400px; width: 100%; }
.chart-mid { height: 360px; width: 100%; }
.chart { height: 100%; width: 100%; }

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
}

.trend-filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.glass-input-sm {
  padding: 10px 14px;
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--c-border-glass);
  border-radius: var(--radius-sm);
  color: var(--c-text-primary);
  font-size: 14px;
  width: 180px;
}
.glass-input-sm:focus {
  border-color: rgba(30, 117, 255, 0.5);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 20px;
  color: var(--c-text-muted);
}

.loader-ring {
  width: 48px; height: 48px;
  border: 3px solid rgba(255,255,255,0.08);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .chart-grid-2 { grid-template-columns: 1fr; }
  .chart-wide { height: 340px; }
  .chart-mid { height: 300px; }
  .glass-input-sm { width: 100%; }
  .form-row { flex-direction: column; }
  .result-main { flex-direction: column; gap: 16px; }
  .result-value { font-size: 24px; }
}
</style>
