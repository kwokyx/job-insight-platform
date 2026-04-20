<script setup>
import { computed, onMounted, ref } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import {
  fetchDeepCurriculumGapAnalysis,
  fetchDeepSalaryPremiumAnalysis,
  fetchDeepSupplyDemandAnalysis,
  fetchDeepTrendForecast
} from '../api'

const props = defineProps({
  token: { type: String, default: '' }
})

const loading = ref(false)
const supplyDemandResult = ref(null)
const curriculumGapResult = ref(null)
const salaryPremiumResult = ref(null)
const trendForecastResult = ref(null)

const topMissingSkills = computed(() => supplyDemandResult.value?.missingInSchool?.slice(0, 8) || [])
const capabilityDimensions = computed(() => supplyDemandResult.value?.capabilityDimensions?.slice(0, 6) || [])
const educationPremium = computed(() => salaryPremiumResult.value?.educationPremium?.slice(0, 6) || [])
const experiencePremium = computed(() => salaryPremiumResult.value?.experiencePremium?.slice(0, 6) || [])
const forecastRows = computed(() => trendForecastResult.value?.forecast?.slice(0, 4) || [])

function formatSalary(value) {
  const num = Number(value)
  if (Number.isNaN(num) || !Number.isFinite(num)) return '--'
  return `${num.toFixed(2)}K`
}

async function loadData() {
  loading.value = true
  try {
    const [supply, gap, premium, forecast] = await Promise.all([
      fetchDeepSupplyDemandAnalysis(),
      fetchDeepCurriculumGapAnalysis(props.token, {}),
      fetchDeepSalaryPremiumAnalysis(),
      fetchDeepTrendForecast({ months: 3 })
    ])
    supplyDemandResult.value = supply
    curriculumGapResult.value = gap
    salaryPremiumResult.value = premium
    trendForecastResult.value = forecast
  } catch (e) {
    console.error('Failed to load deep analysis:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="supply-demand-view">
    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在加载深度分析...</p>
    </div>

    <template v-else>
      <section class="summary-grid">
        <PremiumCard title="供需剪刀差" class="full-width" glowColor="teal">
          <div class="score-card">
            <div class="score-item">
              <div class="score-label">课程关键词</div>
              <div class="score-value">{{ supplyDemandResult?.courseKeywordsCount || 0 }}</div>
            </div>
            <div class="score-item">
              <div class="score-label">匹配技能数</div>
              <div class="score-value">{{ supplyDemandResult?.matchedSkillCount || 0 }}</div>
            </div>
            <div class="score-item">
              <div class="score-label">课程命中率</div>
              <div class="score-value">{{ supplyDemandResult?.matchRate || '0%' }}</div>
            </div>
          </div>
          <p class="summary-text">
            {{ supplyDemandResult?.summary || '当前页展示课程与市场技能的直接对照，便于快速判断供需错位。' }}
          </p>
        </PremiumCard>

        <PremiumCard title="课程差距重点" class="full-width" glowColor="secondary">
          <div class="tag-list" v-if="topMissingSkills.length">
            <div v-for="item in topMissingSkills" :key="item.skill" class="tag-item">
              <strong>{{ item.skill }}</strong>
              <span>市场需求 {{ item.marketDemand || 0 }}</span>
            </div>
          </div>
          <p v-else class="empty-copy">暂无待补齐的高频技能。</p>
        </PremiumCard>
      </section>

      <section class="grid two-col">
        <PremiumCard title="能力维度" glowColor="primary">
          <div v-if="capabilityDimensions.length" class="data-list">
            <article v-for="item in capabilityDimensions" :key="item.dimension" class="data-row">
              <div>
                <strong>{{ item.dimension }}</strong>
                <p>优先级 {{ item.priority }}</p>
              </div>
              <span>{{ item.count }}</span>
            </article>
          </div>
          <p v-else class="empty-copy">暂无能力维度数据。</p>
        </PremiumCard>

        <PremiumCard title="毕业要求映射" glowColor="amber">
          <div v-if="curriculumGapResult?.graduationRequirements?.length" class="data-list">
            <article v-for="item in curriculumGapResult.graduationRequirements.slice(0, 5)" :key="item.code" class="data-row">
              <div>
                <strong>{{ item.code }} · {{ item.name }}</strong>
                <p>{{ item.requirement }}</p>
              </div>
              <span>{{ item.priority }}</span>
            </article>
          </div>
          <p v-else class="empty-copy">暂无毕业要求映射。</p>
        </PremiumCard>
      </section>

      <section class="grid two-col">
        <PremiumCard title="学历溢价" glowColor="purple">
          <div v-if="educationPremium.length" class="data-list">
            <article v-for="item in educationPremium" :key="item.education" class="data-row">
              <div>
                <strong>{{ item.education || '未标注' }}</strong>
                <p>{{ item.jobCount || 0 }} 个岗位</p>
              </div>
              <span>{{ formatSalary(item.avgSalary) }}</span>
            </article>
          </div>
          <p v-else class="empty-copy">暂无学历溢价数据。</p>
        </PremiumCard>

        <PremiumCard title="经验溢价" glowColor="teal">
          <div v-if="experiencePremium.length" class="data-list">
            <article v-for="item in experiencePremium" :key="item.experience" class="data-row">
              <div>
                <strong>{{ item.experience || '未标注' }}</strong>
                <p>{{ item.jobCount || 0 }} 个岗位</p>
              </div>
              <span>{{ formatSalary(item.avgSalary) }}</span>
            </article>
          </div>
          <p v-else class="empty-copy">暂无经验溢价数据。</p>
        </PremiumCard>
      </section>

      <section class="grid one-col">
        <PremiumCard title="趋势预测" glowColor="secondary">
          <div v-if="forecastRows.length" class="forecast-grid">
            <article v-for="item in forecastRows" :key="item.period" class="forecast-item">
              <strong>{{ item.period }}</strong>
              <p>岗位量 {{ item.jobCount || '--' }}</p>
              <p>下限 {{ formatSalary(item.avgSalaryMin) }}</p>
              <p>上限 {{ formatSalary(item.avgSalaryMax) }}</p>
            </article>
          </div>
          <p v-else class="empty-copy">暂无趋势预测数据。</p>
        </PremiumCard>
      </section>
    </template>
  </div>
</template>

<style scoped>
.supply-demand-view,
.summary-grid,
.grid,
.data-list,
.forecast-grid,
.tag-list {
  display: grid;
  gap: 24px;
}

.one-col {
  grid-template-columns: 1fr;
}

.two-col {
  grid-template-columns: 1fr 1fr;
}

.summary-text,
.empty-copy,
.data-row p,
.tag-item span,
.forecast-item p {
  color: var(--c-text-secondary);
}

.summary-text,
.empty-copy {
  margin: 0;
  line-height: 1.6;
}

.score-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.score-item,
.data-row,
.forecast-item,
.tag-item {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.03);
}

.score-label {
  font-size: 12px;
  color: var(--c-text-muted);
}

.score-value {
  margin-top: 10px;
  font-size: 28px;
  line-height: 1;
  color: var(--c-text-primary);
}

.data-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.data-row span {
  color: var(--c-text-primary);
  font-weight: 700;
}

.forecast-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.tag-list {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.tag-item,
.forecast-item {
  display: grid;
  gap: 6px;
}

@media (max-width: 960px) {
  .two-col,
  .score-card,
  .forecast-grid,
  .tag-list {
    grid-template-columns: 1fr;
  }
}
</style>
