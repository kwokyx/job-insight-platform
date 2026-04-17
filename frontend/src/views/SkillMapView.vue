<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import { fetchSkillsRanking } from '../api'
import { Award, TrendingUp, Zap, Target } from 'lucide-vue-next'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, GridComponent])

const skills = ref([])
const isLoading = ref(true)
const displayCount = ref(30)

onMounted(async () => {
  try {
    skills.value = await fetchSkillsRanking(50)
  } catch (e) {
    console.error('加载技能数据失败', e)
  } finally {
    isLoading.value = false
  }
})

const topSkills = computed(() => skills.value.slice(0, displayCount.value))
const maxCount = computed(() => topSkills.value[0]?.count || 1)

const chartPalette = {
  primary: '#285b9f',
  secondary: '#3e74b6',
  indigo: '#5c86b5',
  teal: '#68768a',
  slate: '#73839a'
}

// ECharts 横向柱状图
const chartOption = ref(null)
watch(() => topSkills.value, (list) => {
  if (!list.length) return
  const reversed = [...list].reverse()
  chartOption.value = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: 'rgba(255,255,255,0.08)',
      textStyle: { color: '#F8FAFC' },
      formatter: (params) => {
        const p = params[0]
        return `<b>${p.name}</b><br/>出现次数: ${p.value}`
      }
    },
    grid: { left: '3%', right: '6%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#94A3B8' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } }
    },
    yAxis: {
      type: 'category',
      data: reversed.map(s => s.skill),
      axisLabel: { color: '#CBD5E1', fontSize: 13 },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } }
    },
    series: [{
      type: 'bar',
      data: reversed.map((s, i) => {
        const ratio = i / reversed.length
        return {
          value: s.count,
          itemStyle: {
            color: {
              type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: ratio > 0.7 ? 'rgba(92,134,181,0.24)' : 'rgba(40,91,159,0.18)' },
                { offset: 1, color: ratio > 0.7 ? chartPalette.indigo : chartPalette.primary }
              ]
            },
            borderRadius: [0, 4, 4, 0]
          }
        }
      }),
      barWidth: '60%',
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(62, 116, 182, 0.22)' }
      }
    }]
  }
}, { immediate: true })

// 技能分类统计
const categories = computed(() => {
  const cats = {}
  topSkills.value.forEach(s => {
    const cat = s.category || '其他'
    if (!cats[cat]) cats[cat] = { name: cat, count: 0, skills: [] }
    cats[cat].count++
    cats[cat].skills.push(s.skill)
  })
  return Object.values(cats).sort((a, b) => b.count - a.count)
})
</script>

<template>
  <div class="skill-page">
    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载技能数据...</p>
    </div>

    <template v-else>
      <!-- 统计指标 -->
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
            <span class="stat-label">技能分类</span>
            <strong>{{ categories.length }}</strong>
          </div>
        </div>
      </div>

      <div class="main-content">
        <!-- 左侧：图表 -->
        <PremiumCard title="技能需求排行" glowColor="primary" class="chart-card">
          <div class="chart-controls">
            <button 
              v-for="n in [15, 30, 50]" 
              :key="n" 
              class="count-btn"
              :class="{ active: displayCount === n }"
              @click="displayCount = n"
            >TOP {{ n }}</button>
          </div>
          <div class="skill-chart-box">
            <v-chart v-if="chartOption" class="chart" :option="chartOption" autoresize />
          </div>
        </PremiumCard>

        <!-- 右侧：技能卡片列表 -->
        <div class="side-panel">
          <PremiumCard title="热门技能 TOP 10" glowColor="secondary">
            <div class="top-skills-list">
              <div v-for="(s, i) in skills.slice(0, 10)" :key="s.skill" class="top-skill-item">
                <span class="rank-badge" :class="{ first: i === 0, second: i === 1, third: i === 2 }">
                  {{ i + 1 }}
                </span>
                <span class="skill-name">{{ s.skill }}</span>
                <div class="skill-bar-mini">
                  <div class="skill-fill" :style="{ width: `${(s.count / maxCount) * 100}%` }"></div>
                </div>
                <span class="skill-count">{{ s.count }}</span>
              </div>
            </div>
          </PremiumCard>

          <PremiumCard title="技能标签云" glowColor="teal">
            <div class="tag-cloud">
              <span 
                v-for="(s, i) in skills.slice(0, 25)" 
                :key="s.skill"
                class="cloud-tag"
                :style="{
                  fontSize: `${Math.max(12, 12 + (s.count / maxCount) * 14)}px`,
                  opacity: 0.5 + (s.count / maxCount) * 0.5
                }"
              >{{ s.skill }}</span>
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

/* Stats */
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
  border: 1px solid rgba(193, 198, 215, 0.56);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
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
.stat-icon-blue { color: var(--c-accent-primary); }
.stat-icon-indigo { color: #4f46e5; }
.stat-icon-slate { color: #64748b; }

/* Main Content */
.main-content {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 18px;
}

/* Chart */
.chart-controls {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  padding: 8px;
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.56);
  width: fit-content;
  max-width: 100%;
}

.count-btn {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(193, 198, 215, 0.46);
  color: var(--c-text-secondary);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}
.count-btn:hover {
  background: rgba(30, 117, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.22);
  color: var(--c-accent-primary);
  transform: translateY(-1px);
}
.count-btn.active {
  background: rgba(30, 117, 255, 0.12);
  border-color: rgba(30, 117, 255, 0.3);
  color: var(--c-accent-primary);
}

.skill-chart-box {
  height: 600px;
  width: 100%;
}
.chart {
  height: 100%;
  width: 100%;
}

/* Side Panel */
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
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(193, 198, 215, 0.46);
  color: var(--c-text-muted);
  flex-shrink: 0;
}
.rank-badge.first { background: rgba(29, 78, 216, 0.14); color: #1d4ed8; border-color: rgba(29, 78, 216, 0.24); }
.rank-badge.second { background: rgba(59, 130, 246, 0.12); color: #2563eb; border-color: rgba(59, 130, 246, 0.2); }
.rank-badge.third { background: rgba(79, 70, 229, 0.1); color: #4f46e5; border-color: rgba(79, 70, 229, 0.18); }

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
  background: rgba(209, 219, 232, 0.5);
  border-radius: 3px;
  overflow: hidden;
}

.skill-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.3), #1d4ed8);
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

/* Tag Cloud */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  padding: 16px 0;
}

.cloud-tag {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(193, 198, 215, 0.46);
  color: #48627f;
  white-space: nowrap;
  transition: all var(--duration-fast);
}
.cloud-tag:hover {
  background: rgba(30, 117, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.22);
  color: var(--c-accent-primary);
  transform: scale(1.05);
}

/* Loading */
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
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255,255,255,0.08);
  border-top-color: var(--c-accent-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1024px) {
  .main-content { grid-template-columns: 1fr; }
  .skill-chart-box { height: 500px; }
}

@media (max-width: 768px) {
  .stat-row { grid-template-columns: 1fr 1fr; }
  .skill-chart-box { height: 400px; }
  .skill-name { width: 70px; }
}
</style>
