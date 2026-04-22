<script setup>
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart, RadarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../common/PremiumCard.vue'
import GlowButton from '../common/GlowButton.vue'
import {
  Download,
  Eye,
  FileBarChart,
  FileText,
  Sparkles,
  Target,
  TrendingUp
} from 'lucide-vue-next'
import { useThemeStore } from '../../store/theme'

use([
  CanvasRenderer, PieChart, BarChart, LineChart, RadarChart,
  TitleComponent, TooltipComponent, LegendComponent, GridComponent, RadarComponent
])

const props = defineProps({
  report: { type: Object, default: null },
  exportFormat: { type: String, default: 'pdf' },
  reportTypeLabel: { type: Function, default: (code) => code || '--' }
})
const emit = defineEmits(['update:exportFormat', 'preview', 'export'])

const themeStore = useThemeStore()

const sections = computed(() => props.report?.sections || {})
const reportTypeCode = computed(() => String(props.report?.reportType || '').toUpperCase())

function listify(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}
function formatNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString('zh-CN') : '--'
}
function formatSalaryValue(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '--'
  return `${num.toFixed(2)}K`
}
function formatPercent(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  return `${num >= 0 ? '+' : ''}${num.toFixed(1)}%`
}
function comparisonLevelLabel(level) {
  if (level === 'good') return '表现良好'
  if (level === 'warn') return '需要补位'
  if (level === 'risk') return '重点风险'
  return '中性观察'
}

const topSkills = computed(() => listify(sections.value.topSkills).slice(0, 12))
const topCities = computed(() => listify(sections.value.topCities).slice(0, 8))
const topIndustries = computed(() => listify(sections.value.topIndustries).slice(0, 8))
const educationDist = computed(() => listify(sections.value.educationDist).slice(0, 6))
const experienceDist = computed(() => listify(sections.value.experienceDist).slice(0, 6))
const salaryTrendRows = computed(() => listify(sections.value.salaryTrend).slice(-8))
const teachingReformSection = computed(() => (sections.value.teachingReform && typeof sections.value.teachingReform === 'object')
  ? sections.value.teachingReform
  : {}
)
const teachingBlueprint = computed(() => (teachingReformSection.value.blueprint && typeof teachingReformSection.value.blueprint === 'object')
  ? teachingReformSection.value.blueprint
  : {}
)
const teachingScorecard = computed(() => (teachingReformSection.value.governanceScorecard && typeof teachingReformSection.value.governanceScorecard === 'object')
  ? teachingReformSection.value.governanceScorecard
  : {}
)
const teachingDimensions = computed(() => listify(teachingScorecard.value.dimensions))
const teachingRisks = computed(() => listify(teachingScorecard.value.risks))
const teachingMaterialReadiness = computed(() => listify(teachingBlueprint.value.materialReadiness))
const teachingCoursePlans = computed(() => listify(teachingBlueprint.value.courseActionPlans).slice(0, 6))
const isTeacherReport = computed(() => {
  if (Number(props.report?.roleTemplate?.roleType) === 2) return true
  return ['SUPPLY_DEMAND', 'TEACHING_ADVICE', 'SKILL', 'COMPREHENSIVE'].includes(reportTypeCode.value)
})
const teachingInsightAvailable = computed(
  () =>
    teachingDimensions.value.length > 0 ||
    teachingMaterialReadiness.value.length > 0 ||
    teachingCoursePlans.value.length > 0 ||
    !!teachingScorecard.value.summary
)

const salaryTrendChart = computed(() => {
  const rows = salaryTrendRows.value
    .map((item) => ({
      period: item.period || '--',
      avgMin: Number(item.salaryAvg ?? item.avgSalaryMin ?? item.avgMin ?? 0),
      avgMax: Number(item.avgSalaryMax ?? item.avgMax ?? 0),
      jobCount: Number(item.jobCount ?? item.count ?? 0)
    }))
    .filter((item) => item.avgMin > 0 || item.avgMax > 0)

  if (!rows.length) return { rows: [], latest: null, changePct: null }
  const earliest = rows[0]
  const latest = rows[rows.length - 1]
  const changePct = earliest.avgMin > 0 ? ((latest.avgMin - earliest.avgMin) / earliest.avgMin) * 100 : null
  return { rows, latest, changePct }
})

const trendSummaryCards = computed(() => {
  const latest = salaryTrendChart.value.latest
  if (!latest) return []
  return [
    { label: '最新薪资下限', value: formatSalaryValue(latest.avgMin) },
    { label: '最新薪资上限', value: formatSalaryValue(latest.avgMax) },
    { label: '最新岗位样本', value: formatNumber(latest.jobCount) },
    { label: '阶段变化', value: formatPercent(salaryTrendChart.value.changePct) }
  ]
})

function theme() {
  return themeStore.isDark ? {
    textColor: '#CBD5E1',
    splitLineColor: 'rgba(255,255,255,0.08)',
    tooltipBg: 'rgba(15, 23, 42, 0.96)',
    borderColor: 'rgba(255,255,255,0.08)'
  } : {
    textColor: '#475569',
    splitLineColor: 'rgba(15,23,42,0.08)',
    tooltipBg: 'rgba(255,255,255,0.96)',
    borderColor: 'rgba(15,23,42,0.08)'
  }
}

const cityPie = computed(() => {
  if (!topCities.value.length) return null
  const t = theme()
  const palette = ['#38BDF8', '#22C55E', '#F59E0B', '#A855F7', '#F97316', '#14B8A6', '#6366F1', '#EC4899']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    series: [{
      type: 'pie',
      radius: ['40%', '74%'],
      label: { color: t.textColor, formatter: '{b}\n{d}%' },
      itemStyle: { borderRadius: 10, borderColor: 'transparent', borderWidth: 4 },
      data: topCities.value.map((item, i) => ({ value: Number(item.count || 0), name: item.city, itemStyle: { color: palette[i % palette.length] } }))
    }]
  }
})
const industryPie = computed(() => {
  if (!topIndustries.value.length) return null
  const t = theme()
  const palette = ['#F97316', '#3B82F6', '#A855F7', '#10B981', '#EF4444', '#2DD4BF', '#F59E0B', '#EC4899']
  return {
    tooltip: { trigger: 'item', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    series: [{
      type: 'pie',
      radius: ['28%', '78%'],
      roseType: 'area',
      label: { color: t.textColor, formatter: '{b}' },
      itemStyle: { borderRadius: 10, borderColor: 'transparent', borderWidth: 4 },
      data: topIndustries.value.map((item, i) => ({ value: Number(item.count || 0), name: item.industry, itemStyle: { color: palette[i % palette.length] } }))
    }]
  }
})
const skillBar = computed(() => {
  if (!topSkills.value.length) return null
  const t = theme()
  const rows = [...topSkills.value].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    grid: { left: '3%', right: '4%', top: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'category', data: rows.map((item) => item.skill), axisLabel: { color: t.textColor, width: 90, overflow: 'truncate' } },
    series: [{ type: 'bar', barWidth: '56%', data: rows.map((item) => Number(item.count || 0)), itemStyle: { borderRadius: [0, 8, 8, 0], color: '#3B82F6' } }]
  }
})
const educationBar = computed(() => {
  if (!educationDist.value.length) return null
  const t = theme()
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    grid: { left: '4%', right: '4%', top: '8%', bottom: '12%', containLabel: true },
    xAxis: { type: 'category', data: educationDist.value.map((item) => item.education), axisLabel: { color: t.textColor } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'bar', barWidth: '52%', data: educationDist.value.map((item) => Number(item.count || 0)), itemStyle: { borderRadius: [8, 8, 0, 0], color: '#8B5CF6' } }]
  }
})
const experienceRadar = computed(() => {
  if (!experienceDist.value.length) return null
  const t = theme()
  const rows = experienceDist.value.slice(0, 6)
  const maxVal = Math.max(...rows.map((item) => Number(item.count || 0)), 1)
  return {
    tooltip: { backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    radar: { indicator: rows.map((item) => ({ name: item.experience, max: maxVal * 1.2 })), axisName: { color: t.textColor }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [{ type: 'radar', data: [{ value: rows.map((item) => Number(item.count || 0)), name: '岗位数量' }], lineStyle: { color: '#14B8A6', width: 2 }, itemStyle: { color: '#14B8A6' }, areaStyle: { color: 'rgba(45,212,191,0.18)' } }]
  }
})
const salaryTrendOption = computed(() => {
  if (!salaryTrendChart.value.rows.length) return null
  const t = theme()
  const rows = salaryTrendChart.value.rows
  return {
    tooltip: { trigger: 'axis', backgroundColor: t.tooltipBg, textStyle: { color: t.textColor }, borderColor: t.borderColor },
    legend: { top: 0, textStyle: { color: t.textColor }, data: ['平均薪资上限', '平均薪资下限'] },
    grid: { left: '3%', right: '4%', top: '16%', bottom: '4%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: rows.map((item) => item.period), axisLabel: { color: t.textColor }, axisLine: { lineStyle: { color: t.splitLineColor } } },
    yAxis: { type: 'value', axisLabel: { color: t.textColor, formatter: '{value}K' }, splitLine: { lineStyle: { color: t.splitLineColor } } },
    series: [
      { name: '平均薪资上限', type: 'line', smooth: true, showSymbol: false, data: rows.map((item) => item.avgMax), itemStyle: { color: '#F97316' }, areaStyle: { color: 'rgba(249,115,22,0.10)' } },
      { name: '平均薪资下限', type: 'line', smooth: true, showSymbol: false, data: rows.map((item) => item.avgMin), itemStyle: { color: '#3B82F6' }, areaStyle: { color: 'rgba(59,130,246,0.14)' } }
    ]
  }
})

function reportId() {
  return props.report?.reportId || props.report?.id
}
</script>

<template>
  <PremiumCard v-if="report" title="报告详情" glowColor="primary" class="detail-card">
    <div class="report-detail">
      <div class="detail-header">
        <div class="detail-main">
          <h3>{{ report.reportName || `报告 #${reportId()}` }}</h3>
          <p>{{ report.summary || '暂无摘要。' }}</p>
          <p class="template-copy">{{ report.templateDescription || report.reportMeta?.templateDescription }}</p>
        </div>
        <div class="inline-actions" style="gap: 8px;">
          <GlowButton variant="ghost" @click="emit('preview', reportId())"><Eye :size="14" />预览 PDF</GlowButton>
          <select :value="exportFormat" @change="emit('update:exportFormat', $event.target.value)" class="glass-input compact-input">
            <option value="pdf">PDF</option>
            <option value="md">Markdown</option>
            <option value="html">HTML</option>
          </select>
          <GlowButton variant="primary" style="height: 36px;" @click="emit('export', { id: reportId(), reportName: report.reportName })"><Download :size="14" />导出</GlowButton>
        </div>
      </div>

      <div class="summary-strip">
        <div class="summary-box"><span>目标读者</span><strong>{{ report.targetAudience || '报告使用者' }}</strong></div>
        <div class="summary-box"><span>报告重点</span><strong>{{ report.reportFocus || '--' }}</strong></div>
        <div class="summary-box"><span>报告类型</span><strong>{{ reportTypeLabel(report.reportType) }}</strong></div>
      </div>

      <section v-if="isTeacherReport" class="report-section">
        <div class="section-head"><FileText :size="16" /><h4>教学治理洞察</h4></div>
        <div v-if="teachingInsightAvailable" class="teacher-insight-grid">
          <div class="teacher-insight-card">
            <h5>治理摘要</h5>
            <p>{{ teachingScorecard.summary || '当前报告暂无治理摘要。' }}</p>
            <div class="teacher-mini-metrics">
              <span>综合得分：{{ formatNumber(teachingScorecard.overallScore) }}</span>
              <span>执行准备：{{ formatNumber(teachingScorecard.governanceExecutionScore) }}</span>
            </div>
          </div>
          <div v-if="teachingDimensions.length" class="teacher-insight-card">
            <h5>治理维度</h5>
            <div class="teacher-dimension-list">
              <div v-for="item in teachingDimensions" :key="item.label || item.name" class="teacher-dimension-item">
                <strong>{{ item.label || item.name || '--' }}</strong>
                <span>{{ formatNumber(item.score) }}</span>
                <p>{{ item.evidence || item.interpretation || '--' }}</p>
              </div>
            </div>
            <ul v-if="teachingRisks.length" class="bullet-list">
              <li v-for="(risk, index) in teachingRisks" :key="`teaching-risk-${index}`">{{ risk }}</li>
            </ul>
          </div>
          <div v-if="teachingMaterialReadiness.length" class="teacher-insight-card">
            <h5>资料证据链</h5>
            <div class="teacher-material-list">
              <div v-for="item in teachingMaterialReadiness" :key="item.name || item.type" class="teacher-material-item">
                <strong>{{ item.name || item.type || '--' }}</strong>
                <span>{{ item.ready ? '已就绪' : '待补齐' }}</span>
                <p>{{ item.detail || '--' }}</p>
              </div>
            </div>
          </div>
          <div v-if="teachingCoursePlans.length" class="teacher-insight-card">
            <h5>课程整改优先级</h5>
            <div class="teacher-plan-list">
              <div v-for="(item, index) in teachingCoursePlans" :key="`${item.courseName || 'course'}-${index}`" class="teacher-plan-item">
                <div class="teacher-plan-head">
                  <strong>{{ item.courseName || `课程 ${index + 1}` }}</strong>
                  <span>{{ item.priority || 'P2' }}</span>
                </div>
                <p>{{ item.reason || '--' }}</p>
                <p class="teacher-plan-sub">风险分：{{ formatNumber(item.riskScore) }}</p>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="chart-surface">
          <p>当前报告未包含教学治理数据，请优先生成教学建议或供需分析报告。</p>
        </div>
      </section>

      <section v-if="salaryTrendChart.rows.length" class="report-section">
        <div class="section-head"><TrendingUp :size="16" /><h4>薪资趋势图</h4></div>
        <div class="insight-grid insight-grid-salary">
          <div class="chart-surface">
            <div class="chart-surface-head">
              <h5>阶段薪资区间</h5>
              <p>按时间回看岗位平均薪资上下限变化</p>
            </div>
            <div class="report-chart-box report-chart-box-wide">
              <VChart v-if="salaryTrendOption" class="chart" :option="salaryTrendOption" autoresize />
            </div>
          </div>
          <div class="metric-stack">
            <div v-for="item in trendSummaryCards" :key="item.label" class="trend-stat">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section v-if="topSkills.length || topCities.length || topIndustries.length" class="report-section">
        <div class="section-head"><FileBarChart :size="16" /><h4>市场结构图表</h4></div>
        <div class="insight-grid insight-grid-structure">
          <div v-if="skillBar" class="chart-surface">
            <div class="chart-surface-head"><h5>高频技能热度</h5><p>岗位样本中最常出现的能力标签</p></div>
            <div class="report-chart-box report-chart-box-tall"><VChart class="chart" :option="skillBar" autoresize /></div>
          </div>
          <div v-if="cityPie" class="chart-surface">
            <div class="chart-surface-head"><h5>城市机会分布</h5><p>岗位需求更集中的城市</p></div>
            <div class="report-chart-box"><VChart class="chart" :option="cityPie" autoresize /></div>
          </div>
          <div v-if="industryPie" class="chart-surface">
            <div class="chart-surface-head"><h5>重点岗位赛道</h5><p>当前值得持续跟踪的行业或岗位方向</p></div>
            <div class="report-chart-box"><VChart class="chart" :option="industryPie" autoresize /></div>
          </div>
        </div>
      </section>

      <section v-if="educationDist.length || experienceDist.length" class="report-section">
        <div class="section-head"><Target :size="16" /><h4>岗位门槛分布</h4></div>
        <div class="insight-grid insight-grid-distribution">
          <div v-if="educationBar" class="chart-surface">
            <div class="chart-surface-head"><h5>学历要求分布</h5><p>不同学历要求对应的岗位数量</p></div>
            <div class="report-chart-box"><VChart class="chart" :option="educationBar" autoresize /></div>
          </div>
          <div v-if="experienceRadar" class="chart-surface">
            <div class="chart-surface-head"><h5>经验要求重心</h5><p>不同经验阶段的市场吸纳强度</p></div>
            <div class="report-chart-box"><VChart class="chart" :option="experienceRadar" autoresize /></div>
          </div>
        </div>
      </section>

      <section v-if="listify(report.comparisonItems).length" class="report-section">
        <div class="section-head"><Target :size="16" /><h4>关键对比项</h4></div>
        <div class="comparison-list">
          <div
            v-for="item in listify(report.comparisonItems)"
            :key="item.label"
            class="comparison-item"
            :class="`comparison-${item.level || 'neutral'}`"
          >
            <div class="comparison-head">
              <strong>{{ item.label }}</strong>
              <span class="comparison-badge">{{ comparisonLevelLabel(item.level) }}</span>
            </div>
            <p>当前：{{ item.mine || '--' }}</p>
            <p>目标：{{ item.market || '--' }}</p>
            <p>{{ item.insight || '--' }}</p>
          </div>
        </div>
      </section>

      <section v-if="listify(report.chartInsights).length" class="report-section">
        <div class="section-head"><TrendingUp :size="16" /><h4>图表洞察</h4></div>
        <ul class="bullet-list">
          <li v-for="item in listify(report.chartInsights)" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="listify(report.recommendations).length" class="report-section">
        <div class="section-head"><Sparkles :size="16" /><h4>建议清单</h4></div>
        <ul class="bullet-list">
          <li v-for="item in listify(report.recommendations)" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section v-if="listify(report.actionPlan).length" class="report-section">
        <div class="section-head"><FileText :size="16" /><h4>行动计划</h4></div>
        <div class="action-list">
          <div v-for="item in listify(report.actionPlan)" :key="`${item.priority}-${item.title}`" class="action-item">
            <span class="priority">{{ item.priority || 'P' }}</span>
            <div class="detail-main">
              <strong>{{ item.title || '行动项' }}</strong>
              <p>{{ item.detail || '--' }}</p>
            </div>
          </div>
        </div>
      </section>

      <section v-if="listify(report.jobSamples).length" class="report-section">
        <div class="section-head"><FileBarChart :size="16" /><h4>岗位样本</h4></div>
        <div class="job-sample-list">
          <div v-for="item in listify(report.jobSamples)" :key="item.id || item.title" class="job-sample">
            <strong>{{ item.title || '--' }}</strong>
            <p>{{ item.companyName || '--' }} / {{ item.city || '--' }}</p>
            <p>{{ item.salaryText || '--' }}</p>
          </div>
        </div>
      </section>
    </div>
  </PremiumCard>
</template>

<style scoped>
.detail-card { grid-column: 1 / -1; }
.report-detail { display: flex; flex-direction: column; gap: 14px; }
.detail-header, .section-head, .comparison-head, .inline-actions {
  display: flex; align-items: center; gap: 12px;
}
.detail-header, .comparison-head { justify-content: space-between; }
.report-detail h3, .report-detail h4 { margin: 0; }
.template-copy { font-size: 13px; line-height: 1.7; color: var(--c-text-secondary); }
.report-detail p { margin: 0; color: var(--c-text-secondary); }
.summary-strip { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.summary-box {
  padding: 16px; border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.summary-box span { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 8px; }
.summary-box strong { font-size: 18px; color: var(--c-text-primary); }
.report-section { display: grid; gap: 12px; }
.insight-grid { display: grid; gap: 14px; }
.teacher-insight-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.insight-grid-salary { grid-template-columns: minmax(0, 1.8fr) 280px; }
.insight-grid-structure, .insight-grid-distribution, .comparison-list, .job-sample-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.comparison-list, .job-sample-list { display: grid; gap: 14px; }
.chart-surface {
  padding: 16px; border-radius: 20px; overflow: hidden;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: grid; gap: 14px;
}
.chart-surface-head h5, .chart-surface-head p { margin: 0; }
.chart-surface-head p { color: var(--c-text-secondary); }
.report-chart-box {
  height: 320px; overflow: hidden; border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(30, 117, 255, .08), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, .7), rgba(244, 248, 255, .8));
}
.report-chart-box-wide { height: 340px; }
.report-chart-box-tall { height: 390px; }
.chart { width: 100%; height: 100%; }
.metric-stack { display: grid; gap: 12px; }
.comparison-item, .action-item, .job-sample, .trend-stat {
  padding: 16px; border-radius: 18px; overflow: hidden;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
}
.teacher-insight-card {
  padding: 16px; border-radius: 18px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  display: grid; gap: 10px;
}
.teacher-insight-card h5, .teacher-insight-card p { margin: 0; }
.teacher-mini-metrics { display: flex; flex-wrap: wrap; gap: 10px; font-size: 12px; color: var(--c-text-muted); }
.teacher-dimension-list, .teacher-material-list, .teacher-plan-list { display: grid; gap: 8px; }
.teacher-dimension-item, .teacher-material-item, .teacher-plan-item {
  padding: 10px 12px; border-radius: 12px;
  border: 1px solid rgba(193, 198, 215, 0.45);
  background: rgba(255, 255, 255, 0.6);
  display: grid; gap: 6px;
}
.teacher-dimension-item strong,
.teacher-material-item strong,
.teacher-plan-item strong { color: var(--c-text-primary); }
.teacher-dimension-item span,
.teacher-material-item span,
.teacher-plan-head span { font-size: 12px; color: var(--c-text-muted); }
.teacher-plan-head { display: flex; justify-content: space-between; gap: 10px; }
.teacher-plan-sub { font-size: 12px; color: var(--c-text-muted); }
.comparison-badge {
  display: inline-flex; padding: 4px 10px; border-radius: 999px;
  font-size: 12px; font-weight: 700; background: rgba(242, 244, 250, 0.96);
}
.comparison-good { border-color: rgba(34, 197, 94, .35); background: rgba(34, 197, 94, .08); }
.comparison-warn { border-color: rgba(245, 158, 11, .35); background: rgba(245, 158, 11, .08); }
.comparison-risk { border-color: rgba(239, 68, 68, .35); background: rgba(239, 68, 68, .08); }
.bullet-list { margin: 0; padding-left: 20px; color: var(--c-text-secondary); }
.bullet-list li { margin-bottom: 8px; }
.action-item { display: grid; grid-template-columns: 40px 1fr; gap: 12px; }
.priority {
  width: 32px; height: 32px; border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(30, 117, 255, .12);
  color: var(--c-accent-primary); font-weight: 800;
}
.trend-stat span { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 6px; }
.trend-stat strong { font-size: 18px; color: var(--c-text-primary); }
.glass-input {
  padding: 12px 14px; border-radius: 12px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}
.compact-input { width: 110px; padding: 6px 10px; height: 36px; border-radius: 8px; }
@media (max-width: 1100px) {
  .summary-strip, .insight-grid-salary, .insight-grid-structure, .insight-grid-distribution, .comparison-list, .job-sample-list, .teacher-insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
