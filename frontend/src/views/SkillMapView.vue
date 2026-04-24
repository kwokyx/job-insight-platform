<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GraphChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchSkillsRanking, fetchSkillGraph } from '../api'
import { chartPalette, withAlpha } from '../constants/chartPalette'
import { useThemeStore } from '../store/theme'
import { mapErrorMessage } from '../utils/errorMap'

use([CanvasRenderer, GraphChart, TitleComponent, TooltipComponent])

const themeStore = useThemeStore()

const skills = ref([])
const graphData = ref(null)
const isLoading = ref(true)
const pageError = ref('')
const warningMessage = ref('')

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

const maxCount = computed(() => skills.value[0]?.count || 1)
const graphNodes = computed(() => Array.isArray(graphData.value?.nodes) ? graphData.value.nodes : [])
const graphEdges = computed(() => {
  const edges = Array.isArray(graphData.value?.edges) ? graphData.value.edges : []
  return [...edges].sort((a, b) => Number(b.weight || 0) - Number(a.weight || 0))
})
const graphNodeMeta = computed(() => {
  const map = new Map()
  graphNodes.value.forEach((node) => {
    const name = node.label || node.name || String(node.id)
    const type = node.type || 'skill'
    map.set(name, { type, category: node.category || '', name })
    map.set(String(node.id), { type, category: node.category || '', name })
  })
  return map
})
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
  const domainPalette = {
    软件开发: '#2563EB',
    数据智能: '#7C3AED',
    云平台运维: '#0F766E',
    制造自动化: '#D97706',
    工程建设: '#DC2626',
    医疗健康: '#059669',
    财务风控: '#9333EA',
    供应链物流: '#0891B2',
    通用专业: '#475569'
  }
  const resolvedNames = new Map()
  const mappedNodes = nodes.map((node, index) => {
    const name = resolveName(node)
    resolvedNames.set(String(node.id), name)
    resolvedNames.set(Number(node.id), name)
    const nodeType = node.type || 'skill'
    const category = node.category || '通用专业'
    const isDomain = nodeType === 'domain'
    const color = isDomain
      ? (domainPalette[category] || domainPalette[name] || '#475569')
      : chartPalette.series[index % chartPalette.series.length]
    const value = Number(node.value || 0)
    return {
      name,
      value,
      category,
      type: nodeType,
      symbolSize: isDomain ? 54 : 16 + (value / maxValue) * 28,
      itemStyle: isDomain ? {
        color,
        borderColor: withAlpha('#FFFFFF', themeStore.isDark ? 0.24 : 0.72),
        borderWidth: 1.2,
        shadowBlur: 18,
        shadowColor: withAlpha(color, 0.32)
      } : {
        color
      },
      label: {
        fontSize: isDomain ? 15 : 12,
        fontWeight: isDomain ? 700 : 500
      }
    }
  })
  const mappedLinks = edges.map((edge) => ({
    source: resolvedNames.get(String(edge.source)) || resolvedNames.get(edge.source) || edge.source,
    target: resolvedNames.get(String(edge.target)) || resolvedNames.get(edge.target) || edge.target,
    value: Number(edge.weight || 0),
    edgeType: (
      (graphNodeMeta.value.get(String(edge.source))?.type || graphNodeMeta.value.get(edge.source)?.type) === 'domain'
      || (graphNodeMeta.value.get(String(edge.target))?.type || graphNodeMeta.value.get(edge.target)?.type) === 'domain'
    ) ? 'domain-skill' : 'skill-skill',
    lineStyle: (
      (graphNodeMeta.value.get(String(edge.source))?.type || graphNodeMeta.value.get(edge.source)?.type) === 'domain'
      || (graphNodeMeta.value.get(String(edge.target))?.type || graphNodeMeta.value.get(edge.target)?.type) === 'domain'
    ) ? {
      color: themeStore.isDark ? 'rgba(148, 163, 184, 0.55)' : 'rgba(71, 85, 105, 0.38)',
      width: 1.6,
      type: 'dashed',
      opacity: 0.9,
      curveness: 0.1
    } : {
      color: themeStore.isDark ? 'rgba(96, 165, 250, 0.72)' : 'rgba(37, 99, 235, 0.58)',
      width: Math.max(2, Math.min(Number(edge.weight || 0) / 10, 4.5)),
      opacity: 0.95,
      curveness: 0.16
    }
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
        const nodeType = params.data.type === 'domain' ? '领域' : '技能'
        const category = params.data.category ? `<br/>分类：${params.data.category}` : ''
        return `${params.data.name}<br/>节点类型：${nodeType}${category}<br/>出现次数：${params.data.value}`
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
        formatter: '{b}'
      },
      lineStyle: {
        color: (params) => {
          if (params.data?.edgeType === 'domain-skill') {
            return themeStore.isDark ? 'rgba(148, 163, 184, 0.45)' : 'rgba(71, 85, 105, 0.28)'
          }
          return themeStore.isDark ? 'rgba(148, 163, 184, 0.32)' : 'rgba(59, 130, 246, 0.25)'
        },
        opacity: 0.5,
        width: (params) => {
          return params.data?.edgeType === 'domain-skill' ? 1.2 : 2
        },
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
          <PremiumCard title="热门技能 TOP 10" glowColor="secondary" class="top-skills-card">
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

        </div>
      </div>
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

.main-content {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 18px;
  align-items: stretch;
}

.graph-shell {
  height: 600px;
  width: 100%;
}

.chart {
  height: 100%;
  width: 100%;
}

.side-panel {
  display: flex;
  flex-direction: column;
  align-self: stretch;
  min-height: 0;
}

.top-skills-card {
  flex: 1;
  min-height: 0;
}

.top-skills-card :deep(.card-body) {
  height: 100%;
  min-height: 0;
}

.top-skills-list {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  height: 100%;
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
  .graph-shell {
    height: 420px;
  }

  .skill-name {
    width: 70px;
  }
}
</style>
