<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { fetchSkillsRanking, fetchSkillEvolution } from '../api'
import { Award, TrendingUp, Zap, Target, Search, Activity } from 'lucide-vue-next'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, GridComponent])

const skills = ref([])
const isLoading = ref(true)
const displayCount = ref(30)
const activeTab = ref('ranking') // ranking, evolution

const evoSkills = ref('Java, Python, Go')
const evoLoading = ref(false)
const evoResult = ref(null)

onMounted(async () => {
  try {
    skills.value = await fetchSkillsRanking(50)
  } catch (e) {
    console.error('加载技能数据失败', e)
  } finally {
    isLoading.value = false
  }
})

async function runEvolution() {
  if (!evoSkills.value.trim() || evoLoading.value) return
  evoLoading.value = true
  try {
    const skillsList = evoSkills.value.split(/[,\n，、]+/).map(s => s.trim()).filter(Boolean)
    evoResult.value = await fetchSkillEvolution(skillsList, 12)
  } catch (e) {
    console.error('加载技能演进数据失败', e)
  } finally {
    evoLoading.value = false
  }
}

const topSkills = computed(() => skills.value.slice(0, displayCount.value))
const maxCount = computed(() => topSkills.value[0]?.count || 1)

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
                { offset: 0, color: ratio > 0.7 ? 'rgba(249,115,22,0.4)' : 'rgba(59,130,246,0.3)' },
                { offset: 1, color: ratio > 0.7 ? '#F97316' : '#3B82F6' }
              ]
            },
            borderRadius: [0, 4, 4, 0]
          }
        }
      }),
      barWidth: '60%',
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(59, 130, 246, 0.3)' }
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
    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeTab === 'ranking' }" @click="activeTab = 'ranking'">技能排行</button>
      <button class="tab-btn" :class="{ active: activeTab === 'evolution' }" @click="activeTab = 'evolution'">生命周期演进</button>
    </div>

    <!-- 加载中 -->
    <div v-if="isLoading" class="skeleton-page">
      <div class="stat-row">
        <SkeletonCard type="stat" v-for="i in 4" :key="i" />
      </div>
      <div class="main-content">
        <SkeletonCard type="chart" />
        <SkeletonCard type="list" :lines="6" />
      </div>
    </div>

    <template v-else-if="activeTab === 'ranking'">
      <!-- 统计指标 -->
      <div class="stat-row">
        <div class="mini-stat glass-panel">
          <Award :size="24" class="stat-icon-teal" />
          <div>
            <span class="stat-label">收录技能</span>
            <strong>{{ skills.length }}</strong>
          </div>
        </div>
        <div class="mini-stat glass-panel">
          <Zap :size="24" class="stat-icon-orange" />
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
          <Target :size="24" class="stat-icon-purple" />
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
                <span class="rank-badge" :class="{ gold: i === 0, silver: i === 1, bronze: i === 2 }">
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

    <template v-else-if="activeTab === 'evolution'">
      <PremiumCard title="技能生命周期诊断" glowColor="purple">
        <div class="evo-form">
          <input v-model="evoSkills" class="glass-input" placeholder="输入要诊断的技能名称，逗号分隔 (例如: React, Vue, Svelte)" @keydown.enter="runEvolution" />
          <GlowButton variant="primary" :loading="evoLoading" @click="runEvolution">
            <Activity :size="16" /> 开始诊断
          </GlowButton>
        </div>

        <div v-if="evoLoading" class="result-shell mt-4">
          <SkeletonCard type="list" :lines="4" />
        </div>
        <div v-else-if="evoResult" class="result-shell mt-4">
          <div class="evo-grid">
            <div v-for="(result, skillName) in evoResult" :key="skillName" class="evo-card glass-panel">
              <div class="evo-header">
                <h3>{{ skillName }}</h3>
                <span class="phase-badge" :class="result.lifecycle_phase">{{ result.lifecycle_phase_zh }}</span>
              </div>
              <div class="evo-metrics">
                <div class="metric">
                  <span>增长势能</span>
                  <strong>{{ result.growth_momentum > 0 ? '+' : '' }}{{ result.growth_momentum }}</strong>
                </div>
                <div class="metric">
                  <span>波动率</span>
                  <strong>{{ (result.volatility * 100).toFixed(1) }}%</strong>
                </div>
                <div class="metric">
                  <span>生命周期</span>
                  <strong>{{ result.lifecycle_phase_zh }}</strong>
                </div>
              </div>
              <div class="evo-advice">
                <h4>演进建议</h4>
                <p>{{ result.advice }}</p>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state-wrapper mt-4">
          <EmptyState icon="search" title="等待输入" description="输入你关心的技能名称，查看其在市场上的生命周期阶段。" />
        </div>
      </PremiumCard>
    </template>
  </div>
</template>

<style scoped>
.skill-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Stats */
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.mini-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
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
.stat-icon-teal { color: var(--c-accent-teal); }
.stat-icon-orange { color: var(--c-accent-secondary); }
.stat-icon-blue { color: var(--c-accent-primary); }
.stat-icon-purple { color: var(--c-accent-purple); }

/* Main Content */
.main-content {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 24px;
}

/* Chart */
.chart-controls {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.count-btn {
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 13px;
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  transition: all var(--duration-fast);
}
.count-btn:hover {
  background: rgba(255,255,255,0.08);
}
.count-btn.active {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.4);
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
  gap: 24px;
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
  background: rgba(255,255,255,0.05);
  color: var(--c-text-muted);
  flex-shrink: 0;
}
.rank-badge.gold { background: linear-gradient(135deg, #F59E0B, #D97706); color: #fff; }
.rank-badge.silver { background: linear-gradient(135deg, #94A3B8, #64748B); color: #fff; }
.rank-badge.bronze { background: linear-gradient(135deg, #CD7F32, #A0522D); color: #fff; }

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
  background: rgba(255,255,255,0.05);
  border-radius: 3px;
  overflow: hidden;
}

.skill-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.4), #3B82F6);
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
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-accent-teal);
  white-space: nowrap;
  transition: all var(--duration-fast);
}
.cloud-tag:hover {
  background: rgba(45, 212, 191, 0.1);
  border-color: rgba(45, 212, 191, 0.3);
  transform: scale(1.05);
}

/* Tabs */
.tabs { display: flex; gap: 12px; margin-bottom: 8px; }
.tab-btn { display: inline-flex; align-items: center; padding: 12px 20px; border-radius: 999px; background: rgba(255, 255, 255, 0.4); border: 1px solid var(--c-border-glass); color: var(--c-text-secondary); font-weight: 600; font-size: 14px; backdrop-filter: blur(8px); transition: all 0.3s; cursor: pointer; }
.tab-btn:hover { background: rgba(255, 255, 255, 0.8); transform: translateY(-2px); color: var(--c-text-primary); }
.tab-btn.active { background: linear-gradient(135deg, rgba(56, 189, 248, 0.15), rgba(168, 85, 247, 0.1)); border-color: rgba(56, 189, 248, 0.4); color: var(--c-accent-primary); transform: translateY(-2px); }

/* Evolution */
.evo-form { display: flex; gap: 12px; }
.glass-input { flex: 1; padding: 14px 18px; border-radius: 14px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--c-border-glass); color: var(--c-text-primary); }
.evo-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.evo-card { padding: 24px; border-radius: 16px; display: flex; flex-direction: column; gap: 16px; transition: transform 0.2s; }
.evo-card:hover { transform: translateY(-2px); border-color: rgba(168, 85, 247, 0.3); }
.evo-header { display: flex; justify-content: space-between; align-items: center; }
.evo-header h3 { margin: 0; font-size: 20px; color: var(--c-text-primary); }
.phase-badge { padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.phase-badge.emerging { background: rgba(59, 130, 246, 0.15); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.3); }
.phase-badge.growing { background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }
.phase-badge.stable { background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); }
.phase-badge.declining { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }
.evo-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; padding: 16px; background: rgba(15, 23, 42, 0.2); border-radius: 12px; }
.metric { display: flex; flex-direction: column; gap: 4px; }
.metric span { font-size: 11px; color: var(--c-text-muted); text-transform: uppercase; }
.metric strong { font-size: 15px; color: var(--c-text-primary); }
.evo-advice h4 { margin: 0 0 8px; font-size: 14px; color: var(--c-text-primary); }
.evo-advice p { margin: 0; font-size: 13px; color: var(--c-text-secondary); line-height: 1.6; }

.skeleton-page { display: flex; flex-direction: column; gap: 24px; }
.empty-state-wrapper { min-height: 300px; }
.mt-4 { margin-top: 16px; }

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
