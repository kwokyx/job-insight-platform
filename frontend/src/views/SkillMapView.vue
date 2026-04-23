<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, GraphChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchSkillsRanking, fetchSkillGraph } from '../api'
import { chartPalette, withAlpha } from '../constants/chartPalette'
import { useThemeStore } from '../store/theme'
import { Award, TrendingUp, Zap, Share2 } from 'lucide-vue-next'
import { mapErrorMessage } from '../utils/errorMap'

use([CanvasRenderer, BarChart, GraphChart, TitleComponent, TooltipComponent, GridComponent])

const themeStore = useThemeStore()

const skills = ref([])
const graphData = ref(null)
const isLoading = ref(true)
const pageError = ref('')
const warningMessage = ref('')
const displayCount = ref(30)

const chartTheme = computed(() => themeStore.isDark ? {
  tooltipBg: 'rgba(15, 23, 42, 0.95)',
  tooltipText: '#F8FAFC',
  tooltipBorder: 'rgba(255,255,255,0.08)',
  splitLine: 'rgba(255,255,255,0.05)',
  axisLine: 'rgba(255,255,255,0.1)',
  axisLabel: '#CBD5E1',
  axisLabelMuted: '#94A3B8'
} : {
  tooltipBg: 'rgba(255,255,255,0.96)',
  tooltipText: '#181b23',
  tooltipBorder: 'rgba(24,27,35,0.08)',
  splitLine: 'rgba(24,27,35,0.05)',
  axisLine: 'rgba(24,27,35,0.1)',
  axisLabel: '#414755',
  axisLabelMuted: '#727786'
})

onMounted(async () => {
  pageError.value = ''
  warningMessage.value = ''
  try {
    const [rankingRes, graphRes] = await Promise.allSettled([
      fetchSkillsRanking(50),
      fetchSkillGraph(30)
    ])

    const errors = []
    let successCount = 0

    if (rankingRes.status === 'fulfilled') {
      skills.value = Array.isArray(rankingRes.value) ? rankingRes.value : []
      successCount += 1
    } else {
      skills.value = []
      errors.push(`技能排行：${mapErrorMessage(rankingRes.reason)}`)
    }

    if (graphRes.status === 'fulfilled') {
      graphData.value = graphRes.value || {}
      successCount += 1
    } else {
      graphData.value = null
      errors.push(`技能图谱：${mapErrorMessage(graphRes.reason)}`)
    }

    if (successCount === 0) {
      pageError.value = errors[0] || '技能图谱加载失败'
    } else if (errors.length) {
      warningMessage.value = errors.join('；')
    }
  } finally {
    isLoading.value = false
  }
})

const topSkills = computed(() => skills.value.slice(0, displayCount.value))
const maxCount = computed(() => topSkills.value[0]?.count || 1)
const graphNodes = computed(() => Array.isArray(graphData.value?.nodes) ? graphData.value.nodes : [])
const graphEdges = computed(() => {
  const edges = Array.isArray(graphData.value?.edges) ? graphData.value.edges : []
  return [...edges].sort((a, b) => Number(b.weight || 0) - Number(a.weight || 0))
})
const relationPairs = computed(() => graphEdges.value.slice(0, 10))

// Build a reactive ID→name lookup from skills ranking
const skillIdMap = computed(() => {
  const map = new Map()
  skills.value.forEach((s, i) => {
    if (s.skill) {
      map.set(String(s.id ?? s.skillId ?? i + 1), s.skill)
      map.set(Number(s.id ?? s.skillId ?? i + 1), s.skill)
    }
  })
  return map
})
function resolveEdgeName(val) {
  if (typeof val === 'string' && isNaN(val)) return val
  return skillIdMap.value.get(val) || skillIdMap.value.get(String(val)) || String(val)
}

const rankingChart = ref(null)
watch([() => topSkills.value, () => themeStore.isDark], ([list]) => {
  if (!list.length) {
    rankingChart.value = null
    return
  }

  const reversed = [...list].reverse()
  rankingChart.value = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: chartTheme.value.tooltipBg,
      borderColor: chartTheme.value.tooltipBorder,
      textStyle: { color: chartTheme.value.tooltipText },
      formatter: (params) => {
        const p = params[0]
        return `<b>${p.name}</b><br/>出现次数: ${p.value}`
      }
    },
    grid: { left: '3%', right: '6%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: chartTheme.value.axisLabelMuted },
      splitLine: { lineStyle: { color: chartTheme.value.splitLine } }
    },
    yAxis: {
      type: 'category',
      data: reversed.map((skill) => skill.skill),
      axisLabel: { color: chartTheme.value.axisLabel, fontSize: 13 },
      axisLine: { lineStyle: { color: chartTheme.value.axisLine } }
    },
    series: [{
      type: 'bar',
      barWidth: '60%',
      data: reversed.map((skill, index) => {
        const ratio = index / reversed.length
        return {
          value: skill.count,
          itemStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: ratio > 0.7 ? withAlpha(chartPalette.coral, 0.2) : withAlpha(chartPalette.blue, 0.18) },
                { offset: 1, color: ratio > 0.7 ? chartPalette.coral : chartPalette.blue }
              ]
            },
            borderRadius: [0, 4, 4, 0]
          }
        }
      })
    }]
  }
}, { immediate: true })

const graphChart = ref(null)
watch([graphNodes, graphEdges, () => themeStore.isDark, skills], ([nodes, edges]) => {
  if (!nodes.length) {
    graphChart.value = null
    return
  }

  // Build ID→name lookup from skills ranking so numeric node IDs resolve to skill names
  const idToName = new Map()
  skills.value.forEach((s, i) => {
    if (s.skill) {
      idToName.set(String(s.id ?? s.skillId ?? i + 1), s.skill)
      idToName.set(Number(s.id ?? s.skillId ?? i + 1), s.skill)
    }
  })
  function resolveName(node) {
    if (node.label && isNaN(node.label)) return node.label
    if (node.name && isNaN(node.name)) return node.name
    const byId = idToName.get(node.id) || idToName.get(String(node.id))
    if (byId) return byId
    return node.label || node.name || String(node.id)
  }

  const maxValue = Math.max(...nodes.map((node) => Number(node.value || 0)), 1)
  const resolvedNames = new Map()
  const mappedNodes = nodes.map((node, index) => {
    const name = resolveName(node)
    resolvedNames.set(String(node.id), name)
    resolvedNames.set(Number(node.id), name)
    return {
      name,
      value: Number(node.value || 0),
      symbolSize: 18 + (Number(node.value || 0) / maxValue) * 36,
      itemStyle: {
        color: chartPalette.series[index % chartPalette.series.length]
      }
    }
  })
  const mappedLinks = edges.map((edge) => ({
    source: resolvedNames.get(String(edge.source)) || resolvedNames.get(edge.source) || edge.source,
    target: resolvedNames.get(String(edge.target)) || resolvedNames.get(edge.target) || edge.target,
    value: Number(edge.weight || 0)
  }))

  graphChart.value = {
    tooltip: {
      backgroundColor: chartTheme.value.tooltipBg,
      borderColor: chartTheme.value.tooltipBorder,
      textStyle: { color: chartTheme.value.tooltipText },
      formatter: (params) => {
        if (params.dataType === 'edge') {
          return `${params.data.source} → ${params.data.target}<br/>关联强度: ${params.data.value}`
        }
        return `${params.data.name}<br/>出现次数: ${params.data.value}`
      }
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      animation: false,
      force: {
        repulsion: 220,
        edgeLength: [90, 180],
        gravity: 0.08
      },
      label: {
        show: true,
        color: chartTheme.value.axisLabel,
        fontSize: 12
      },
      lineStyle: {
        color: 'source',
        opacity: 0.35,
        width: 1.4,
        curveness: 0.12
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          opacity: 0.8,
          width: 2
        }
      },
      data: mappedNodes,
      links: mappedLinks
    }]
  }
}, { immediate: true })
</script>

<template>
  <div class="skill-page">
    <div v-if="isLoading" class="skeleton-page">
      <div class="stat-row">
        <SkeletonCard v-for="i in 4" :key="`stat-${i}`" type="stat" />
      </div>
      <div class="main-content">
        <SkeletonCard type="chart" />
        <SkeletonCard type="list" :lines="6" />
      </div>
      <SkeletonCard type="chart" />
    </div>

    <div v-else-if="pageError" class="empty-state-wrapper glass-panel">
      <EmptyState icon="error" title="技能图谱加载失败" :description="pageError" />
    </div>

    <template v-else>
      <div v-if="warningMessage" class="status-banner warning-banner">{{ warningMessage }}</div>

      <div class="stat-row">
        <div class="mini-stat glass-panel">
          <Award :size="24" class="stat-icon-blue" />
          <div>
            <span class="stat-label">收录技能</span>
            <strong>{{ skills.length }}</strong>
          </div>
        </div>
        <div class="mini-stat glass-panel">
          <Zap :size="24" class="stat-icon-indigo" />
          <div>
            <span class="stat-label">最热门技能</span>
            <strong>{{ skills[0]?.skill || '-' }}</strong>
          </div>
        </div>
        <div class="mini-stat glass-panel">
          <TrendingUp :size="24" class="stat-icon-blue" />
          <div>
            <span class="stat-label">最高出现次数</span>
            <strong>{{ skills[0]?.count || 0 }}</strong>
          </div>
        </div>
        <div class="mini-stat glass-panel">
          <Share2 :size="24" class="stat-icon-teal" />
          <div>
            <span class="stat-label">图谱关联边</span>
            <strong>{{ graphEdges.length }}</strong>
          </div>
        </div>
      </div>

      <div class="main-content">
        <PremiumCard title="技能关联图谱" glowColor="primary" class="graph-card">
          <div class="graph-shell">
            <v-chart v-if="graphChart" class="chart" :option="graphChart" autoresize />
            <div v-else class="empty-state-wrapper card-empty">
              <EmptyState icon="search" title="暂无图谱数据" description="当前没有可展示的技能关联结果。" />
            </div>
          </div>
        </PremiumCard>

        <div class="side-panel">
          <PremiumCard title="热门技能 TOP 10" glowColor="secondary">
            <div class="top-skills-list">
              <div v-for="(skill, index) in skills.slice(0, 10)" :key="skill.skill" class="top-skill-item">
                <span class="rank-badge" :class="{ first: index === 0, second: index === 1, third: index === 2 }">
                  {{ index + 1 }}
                </span>
                <span class="skill-name">{{ skill.skill }}</span>
                <div class="skill-bar-mini">
                  <div class="skill-fill" :style="{ width: `${(skill.count / maxCount) * 100}%` }"></div>
                </div>
                <span class="skill-count">{{ skill.count }}</span>
              </div>
            </div>
          </PremiumCard>

          <PremiumCard title="强关联技能对" glowColor="teal">
            <div v-if="relationPairs.length" class="relation-list">
              <div v-for="edge in relationPairs" :key="`${edge.source}-${edge.target}`" class="relation-item">
                <div class="relation-pair">
                  <strong>{{ resolveEdgeName(edge.source) }}</strong>
                  <span>→</span>
                  <strong>{{ resolveEdgeName(edge.target) }}</strong>
                </div>
                <span class="relation-weight">权重 {{ edge.weight }}</span>
              </div>
            </div>
            <div v-else class="empty-state-wrapper card-empty">
              <EmptyState icon="search" title="暂无关联边" description="当前技能图谱还没有可展示的技能关系。" />
            </div>
          </PremiumCard>
        </div>
      </div>

      <PremiumCard title="技能需求排行" glowColor="primary" class="chart-card">
        <div class="chart-controls">
          <button
            v-for="count in [15, 30, 50]"
            :key="count"
            class="count-btn"
            :class="{ active: displayCount === count }"
            @click="displayCount = count"
          >TOP {{ count }}</button>
        </div>
        <div class="skill-chart-box">
          <v-chart v-if="rankingChart" class="chart" :option="rankingChart" autoresize />
          <div v-else class="empty-chart">暂无技能排行数据</div>
        </div>
      </PremiumCard>
    </template>
  </div>
</template>

<style scoped>
.skill-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.empty-state-wrapper {
  min-height: 300px;
}

.card-empty {
  width: 100%;
  height: 100%;
}

.status-banner {
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
}

.warning-banner {
  color: #9a6700;
  background: rgba(255, 247, 237, 0.82);
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.mini-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface);
  box-shadow: var(--shadow-card-soft);
}

.mini-stat strong {
  display: block;
  font-size: 22px;
  font-family: var(--font-display);
  margin-top: 2px;
}

.stat-label {
  font-size: 13px;
  color: var(--c-text-muted);
}

.stat-icon-blue { color: #82B0D2; }
.stat-icon-indigo { color: #BEB8DC; }
.stat-icon-teal { color: #8ECFC9; }

.main-content {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 18px;
}

.graph-shell {
  height: 600px;
  width: 100%;
}

.chart {
  height: 100%;
  width: 100%;
}

.chart-card {
  min-width: 0;
}

.chart-controls {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  padding: 8px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface);
  width: fit-content;
  max-width: 100%;
}

.count-btn {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.count-btn:hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}

.count-btn.active {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
}

.skill-chart-box {
  height: 560px;
  width: 100%;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.top-skills-list,
.relation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top-skill-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rank-badge {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
  flex-shrink: 0;
}

.rank-badge.first { background: rgba(250, 127, 111, 0.14); color: #FA7F6F; border-color: rgba(250, 127, 111, 0.26); }
.rank-badge.second { background: rgba(255, 190, 122, 0.16); color: #D38A29; border-color: rgba(255, 190, 122, 0.26); }
.rank-badge.third { background: rgba(190, 184, 220, 0.18); color: #8A7FC6; border-color: rgba(190, 184, 220, 0.3); }

.skill-name {
  width: 90px;
  font-size: 14px;
  color: var(--c-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.skill-bar-mini {
  flex: 1;
  height: 6px;
  background: var(--c-bg-surface-hover);
  border-radius: 3px;
  overflow: hidden;
}

.skill-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(142, 207, 201, 0.45), #82B0D2);
  transition: width 0.6s var(--ease-out);
}

.skill-count {
  width: 40px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-display);
  color: var(--c-text-primary);
}

.relation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface);
}

.relation-pair {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
  color: var(--c-text-primary);
}

.relation-pair strong {
  max-width: 110px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.relation-weight {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--c-text-muted);
}

.empty-chart {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
}

.skeleton-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

:global([data-theme="dark"]) .warning-banner {
  border-color: rgba(245, 158, 11, 0.18);
  background: rgba(120, 53, 15, 0.18);
  color: #f8d48a;
}

@media (max-width: 1024px) {
  .main-content {
    grid-template-columns: 1fr;
  }

  .graph-shell {
    height: 520px;
  }
}

@media (max-width: 768px) {
  .stat-row {
    grid-template-columns: 1fr 1fr;
  }

  .graph-shell {
    height: 420px;
  }

  .skill-chart-box {
    height: 420px;
  }

  .skill-name {
    width: 70px;
  }

  .relation-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
