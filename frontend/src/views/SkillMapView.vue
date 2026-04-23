<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import { fetchSkillsRanking } from '../api'
import { chartPalette, withAlpha } from '../constants/chartPalette'
import { useThemeStore } from '../store/theme'
import { Award, Target, TrendingUp, Zap } from 'lucide-vue-next'

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])

const themeStore = useThemeStore()

const skills = ref([])
const isLoading = ref(true)
const displayCount = ref(30)
const chartOption = ref(null)

const topSkills = computed(() => skills.value.slice(0, displayCount.value))
const maxCount = computed(() => topSkills.value[0]?.count || 1)
const categories = computed(() => {
  const bucket = {}
  topSkills.value.forEach((item) => {
    const key = item.category || '其他'
    bucket[key] = (bucket[key] || 0) + 1
  })
  return Object.keys(bucket).length
})

const chartTheme = computed(() =>
  themeStore.isDark
    ? {
        tooltipBg: 'rgba(15, 23, 42, 0.95)',
        tooltipText: '#F8FAFC',
        tooltipBorder: 'rgba(255,255,255,0.08)',
        splitLine: 'rgba(255,255,255,0.05)',
        axisLine: 'rgba(255,255,255,0.1)',
        axisLabel: '#CBD5E1',
        axisLabelMuted: '#94A3B8'
      }
    : {
        tooltipBg: 'rgba(255,255,255,0.96)',
        tooltipText: '#181b23',
        tooltipBorder: 'rgba(24,27,35,0.08)',
        splitLine: 'rgba(24,27,35,0.05)',
        axisLine: 'rgba(24,27,35,0.1)',
        axisLabel: '#414755',
        axisLabelMuted: '#727786'
      }
)

onMounted(async () => {
  try {
    skills.value = await fetchSkillsRanking(50)
  } finally {
    isLoading.value = false
  }
})

watch(
  [() => topSkills.value, () => themeStore.isDark],
  ([list]) => {
    if (!list?.length) {
      chartOption.value = null
      return
    }
    const reversed = [...list].reverse()
    chartOption.value = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: chartTheme.value.tooltipBg,
        borderColor: chartTheme.value.tooltipBorder,
        textStyle: { color: chartTheme.value.tooltipText },
        formatter: (params) => {
          const p = params?.[0]
          if (!p) return ''
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
        data: reversed.map((item) => item.skill),
        axisLabel: { color: chartTheme.value.axisLabel, fontSize: 13 },
        axisLine: { lineStyle: { color: chartTheme.value.axisLine } }
      },
      series: [
        {
          type: 'bar',
          barWidth: '60%',
          data: reversed.map((item, idx) => {
            const ratio = idx / reversed.length
            return {
              value: item.count,
              itemStyle: {
                color: {
                  type: 'linear',
                  x: 0,
                  y: 0,
                  x2: 1,
                  y2: 0,
                  colorStops: [
                    {
                      offset: 0,
                      color:
                        ratio > 0.7
                          ? withAlpha(chartPalette.coral, 0.2)
                          : withAlpha(chartPalette.blue, 0.18)
                    },
                    {
                      offset: 1,
                      color: ratio > 0.7 ? chartPalette.coral : chartPalette.blue
                    }
                  ]
                },
                borderRadius: [0, 4, 4, 0]
              }
            }
          })
        }
      ]
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="skill-page">
    <div v-if="isLoading" class="skeleton-page">
      <div class="stat-row">
        <SkeletonCard v-for="i in 4" :key="i" type="stat" />
      </div>
      <div class="main-content">
        <SkeletonCard type="chart" />
        <SkeletonCard type="list" :lines="6" />
      </div>
    </div>

    <template v-else>
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
          <Target :size="24" class="stat-icon-slate" />
          <div>
            <span class="stat-label">技能分类数</span>
            <strong>{{ categories }}</strong>
          </div>
        </div>
      </div>

      <div class="main-content">
        <PremiumCard title="技能需求排行" glowColor="primary" class="chart-card">
          <div class="chart-controls">
            <button
              v-for="n in [15, 30, 50]"
              :key="n"
              class="count-btn"
              :class="{ active: displayCount === n }"
              @click="displayCount = n"
            >
              TOP {{ n }}
            </button>
          </div>
          <div class="skill-chart-box">
            <v-chart v-if="chartOption" class="chart" :option="chartOption" autoresize />
          </div>
        </PremiumCard>

        <div class="side-panel">
          <PremiumCard title="热门技能 TOP 10" glowColor="secondary">
            <div class="top-skills-list">
              <div v-for="(item, i) in skills.slice(0, 10)" :key="item.skill" class="top-skill-item">
                <span class="rank-badge" :class="{ first: i === 0, second: i === 1, third: i === 2 }">
                  {{ i + 1 }}
                </span>
                <span class="skill-name">{{ item.skill }}</span>
                <div class="skill-bar-mini">
                  <div class="skill-fill" :style="{ width: `${(item.count / maxCount) * 100}%` }"></div>
                </div>
                <span class="skill-count">{{ item.count }}</span>
              </div>
            </div>
          </PremiumCard>

          <PremiumCard title="技能标签云" glowColor="teal">
            <div class="tag-cloud">
              <span
                v-for="item in skills.slice(0, 25)"
                :key="item.skill"
                class="cloud-tag"
                :style="{
                  fontSize: `${Math.max(12, 12 + (item.count / maxCount) * 14)}px`,
                  opacity: 0.5 + (item.count / maxCount) * 0.5
                }"
              >
                {{ item.skill }}
              </span>
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

.stat-icon-blue { color: #82b0d2; }
.stat-icon-indigo { color: #beb8dc; }
.stat-icon-slate { color: #999999; }

.main-content {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 18px;
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

.count-btn:hover,
.count-btn.active {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
}

.count-btn:hover {
  transform: translateY(-1px);
}

.skill-chart-box {
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
  gap: 18px;
}

.top-skills-list {
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

.rank-badge.first {
  background: rgba(250, 127, 111, 0.14);
  color: #fa7f6f;
  border-color: rgba(250, 127, 111, 0.26);
}

.rank-badge.second {
  background: rgba(255, 190, 122, 0.16);
  color: #d38a29;
  border-color: rgba(255, 190, 122, 0.26);
}

.rank-badge.third {
  background: rgba(130, 176, 210, 0.16);
  color: #5d87aa;
  border-color: rgba(130, 176, 210, 0.26);
}

.skill-name {
  min-width: 86px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--c-text-primary);
}

.skill-bar-mini {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--c-bg-surface-strong);
  overflow: hidden;
}

.skill-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(130, 176, 210, 0.38) 0%, #82b0d2 100%);
}

.skill-count {
  min-width: 36px;
  text-align: right;
  font-weight: 700;
  color: var(--c-text-secondary);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
}

.cloud-tag {
  color: var(--c-text-primary);
  line-height: 1.1;
}

@media (max-width: 1100px) {
  .main-content {
    grid-template-columns: 1fr;
  }
}
</style>
