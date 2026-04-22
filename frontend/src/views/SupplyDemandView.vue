<script setup>
import { computed, ref, watch } from 'vue'
import { fetchSupplyDemand } from '../api'
import PremiumCard from '../components/common/PremiumCard.vue'
import SkeletonCard from '../components/common/SkeletonCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import { mapErrorMessage } from '../utils/errorMap'

const props = defineProps({
  token: { type: String, default: '' }
})

const loading = ref(false)
const pageError = ref('')
const supplyDemandResult = ref(null)

const hasToken = computed(() => Boolean(props.token?.trim()))
const summaryText = computed(() => readText(supplyDemandResult.value?.summary))
const matchedSkills = computed(() => normalizeStringList(supplyDemandResult.value?.matchedSkills))
const missingSkills = computed(() =>
  normalizeGapList(supplyDemandResult.value?.schoolMissingSkills || supplyDemandResult.value?.missingInSchool)
)
const redundantSkills = computed(() =>
  normalizeStringList(
    supplyDemandResult.value?.schoolRedundantSkills ||
      supplyDemandResult.value?.redundantInSchool ||
      supplyDemandResult.value?.courseKeywordsSample
  )
)
const capabilityDimensions = computed(() =>
  normalizeDimensionList(supplyDemandResult.value?.capabilityDimensions)
)
const curriculumActions = computed(() => normalizeActionList(supplyDemandResult.value?.curriculumActions))
const jobFamilies = computed(() => normalizeJobFamilyList(supplyDemandResult.value?.jobFamilies))
const graduationRequirements = computed(() =>
  normalizeRequirementList(supplyDemandResult.value?.graduationRequirements)
)
const statItems = computed(() => {
  const result = supplyDemandResult.value || {}
  return [
    {
      label: '课程关键词',
      value: formatCount(result.courseKeywordsCount ?? result.sampleMeta?.courseKeywordCount),
      hint: '课程侧抽取能力词'
    },
    {
      label: '市场技能池',
      value: formatCount(result.marketSkillsCount ?? result.sampleMeta?.marketSkillCount),
      hint: '岗位侧热门技能样本'
    },
    {
      label: '已匹配技能',
      value: formatCount(result.matchedSkillCount ?? matchedSkills.value.length),
      hint: matchedSkills.value.length ? `当前展示 ${matchedSkills.value.length} 项` : '暂无命中技能'
    },
    {
      label: '匹配率',
      value: readText(result.matchRate) || '--',
      hint: formatConfidence(result.confidenceScore)
    }
  ]
})
const hasContent = computed(() => {
  return Boolean(
    summaryText.value ||
      matchedSkills.value.length ||
      missingSkills.value.length ||
      redundantSkills.value.length ||
      capabilityDimensions.value.length ||
      curriculumActions.value.length ||
      jobFamilies.value.length ||
      graduationRequirements.value.length
  )
})

watch(
  () => props.token,
  () => {
    loadData()
  },
  { immediate: true }
)

async function loadData() {
  if (!hasToken.value) {
    loading.value = false
    supplyDemandResult.value = null
    pageError.value = '请先登录后查看供需分析结果。'
    return
  }

  loading.value = true
  pageError.value = ''

  try {
    supplyDemandResult.value = await fetchSupplyDemand(props.token)
  } catch (error) {
    supplyDemandResult.value = null
    pageError.value = mapErrorMessage(error)
  } finally {
    loading.value = false
  }
}

function readText(value) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeStringList(value) {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean)
}

function normalizeGapList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      skill: readText(item?.skill),
      demand: formatCount(item?.marketDemand),
      diagnosis: readText(item?.diagnosis)
    }))
    .filter((item) => item.skill)
}

function normalizeDimensionList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      name: readText(item?.dimension),
      count: formatCount(item?.count),
      priority: readText(item?.priority)
    }))
    .filter((item) => item.name)
}

function normalizeActionList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      title: readText(item?.title),
      detail: readText(item?.detail),
      priority: readText(item?.priority)
    }))
    .filter((item) => item.title || item.detail)
}

function normalizeJobFamilyList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      name: readText(item?.jobFamily),
      demand: formatCount(item?.demand),
      avgSalary: formatDecimal(item?.avgSalary)
    }))
    .filter((item) => item.name)
}

function normalizeRequirementList(items) {
  if (!Array.isArray(items)) return []
  return items
    .map((item) => ({
      code: readText(item?.code),
      name: readText(item?.name),
      requirement: readText(item?.requirement),
      priority: readText(item?.priority)
    }))
    .filter((item) => item.code || item.name || item.requirement)
}

function formatCount(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : '--'
}

function formatDecimal(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : '--'
}

function formatConfidence(value) {
  const number = Number(value)
  return Number.isFinite(number) ? `置信度 ${Math.round(number)}/100` : '供需结果已返回'
}
</script>

<template>
  <div class="supply-demand-view">
    <div v-if="loading" class="loading-skel">
      <div class="stat-skel-grid">
        <SkeletonCard v-for="item in 4" :key="item" type="stat" />
      </div>
      <SkeletonCard type="list" :lines="4" />
      <SkeletonCard type="list" :lines="4" />
    </div>

    <EmptyState
      v-else-if="pageError"
      icon="error"
      title="供需分析加载失败"
      :description="pageError"
      :action-text="hasToken ? '重新加载' : ''"
      @action="loadData"
    />

    <EmptyState
      v-else-if="!hasContent"
      icon="search"
      title="暂无供需分析结果"
      description="当前还没有可展示的课程与岗位供需分析。"
      action-text="重新加载"
      @action="loadData"
    />

    <template v-else>
      <PremiumCard title="供需剪刀差诊断" glowColor="teal">
        <div class="hero">
          <div class="hero-copy">
            <p class="summary-text">{{ summaryText || '后端暂未返回供需分析摘要。' }}</p>
            <div v-if="matchedSkills.length" class="chip-group">
              <span v-for="skill in matchedSkills" :key="skill" class="chip chip-positive">{{ skill }}</span>
            </div>
          </div>

          <div class="stat-grid">
            <article v-for="item in statItems" :key="item.label" class="stat-card">
              <div class="stat-label">{{ item.label }}</div>
              <strong class="stat-value">{{ item.value }}</strong>
              <p class="stat-hint">{{ item.hint }}</p>
            </article>
          </div>
        </div>
      </PremiumCard>

      <div class="panel-grid">
        <PremiumCard title="校内缺失能力" glowColor="secondary" padding="22px">
          <div v-if="missingSkills.length" class="list-stack">
            <article v-for="item in missingSkills" :key="`${item.skill}-${item.demand}`" class="list-item">
              <div class="item-main">
                <strong>{{ item.skill }}</strong>
                <span class="muted-text">市场需求 {{ item.demand }}</span>
              </div>
              <p class="item-desc">{{ item.diagnosis || '企业侧高频出现，课程侧尚未覆盖。' }}</p>
            </article>
          </div>
          <EmptyState
            v-else
            icon="search"
            title="暂无缺失能力"
            description="当前返回结果里没有待补齐的高需求能力。"
          />
        </PremiumCard>

        <PremiumCard title="校内低相关内容" glowColor="primary" padding="22px">
          <template v-if="redundantSkills.length">
            <p class="section-intro">课程侧高频出现、但市场命中偏低的内容如下。</p>
            <div class="chip-group">
              <span v-for="skill in redundantSkills" :key="skill" class="chip chip-muted">{{ skill }}</span>
            </div>
          </template>
          <EmptyState
            v-else
            icon="search"
            title="暂无低相关内容"
            description="当前返回结果里没有可收缩的课程内容。"
          />
        </PremiumCard>

        <PremiumCard title="能力维度" glowColor="teal" padding="22px">
          <div v-if="capabilityDimensions.length" class="list-stack">
            <article
              v-for="dimension in capabilityDimensions"
              :key="`${dimension.name}-${dimension.priority}`"
              class="dimension-item"
            >
              <div class="item-main">
                <strong>{{ dimension.name }}</strong>
                <span class="priority-pill">{{ dimension.priority || '待分级' }}</span>
              </div>
              <p class="item-desc">关联能力词 {{ dimension.count }}</p>
            </article>
          </div>
          <EmptyState
            v-else
            icon="search"
            title="暂无能力维度"
            description="后端暂未返回能力分层结果。"
          />
        </PremiumCard>

        <PremiumCard title="课程改进行动" glowColor="secondary" padding="22px">
          <div v-if="curriculumActions.length" class="list-stack">
            <article
              v-for="action in curriculumActions"
              :key="`${action.title}-${action.priority}`"
              class="action-item"
            >
              <div class="item-main">
                <strong>{{ action.title }}</strong>
                <span class="priority-pill">{{ action.priority || '待排期' }}</span>
              </div>
              <p class="item-desc">{{ action.detail }}</p>
            </article>
          </div>
          <EmptyState
            v-else
            icon="search"
            title="暂无改进行动"
            description="当前结果里没有返回课程改进建议。"
          />
        </PremiumCard>

        <PremiumCard title="重点岗位族" glowColor="primary" padding="22px">
          <div v-if="jobFamilies.length" class="list-stack">
            <article v-for="family in jobFamilies" :key="family.name" class="list-item">
              <div class="item-main">
                <strong>{{ family.name }}</strong>
                <span class="muted-text">需求 {{ family.demand }}</span>
              </div>
              <p class="item-desc">平均薪资指标 {{ family.avgSalary }}</p>
            </article>
          </div>
          <EmptyState
            v-else
            icon="search"
            title="暂无岗位族结果"
            description="当前供需分析里没有可展示的岗位族聚合。"
          />
        </PremiumCard>

        <PremiumCard title="毕业要求映射" glowColor="teal" padding="22px">
          <div v-if="graduationRequirements.length" class="list-stack">
            <article
              v-for="requirement in graduationRequirements"
              :key="`${requirement.code}-${requirement.name}`"
              class="requirement-item"
            >
              <div class="item-main">
                <strong>{{ requirement.code || requirement.name }}</strong>
                <span class="priority-pill">{{ requirement.priority || '未分级' }}</span>
              </div>
              <p class="item-desc">
                <span v-if="requirement.name && requirement.code !== requirement.name">{{ requirement.name }}：</span>
                {{ requirement.requirement || '后端未返回具体要求说明。' }}
              </p>
            </article>
          </div>
          <EmptyState
            v-else
            icon="search"
            title="暂无毕业要求映射"
            description="当前供需分析未返回毕业要求映射结果。"
          />
        </PremiumCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.supply-demand-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.loading-skel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-skel-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.hero {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--c-text-primary);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
    var(--c-bg-surface-hover);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  font-size: 13px;
  color: var(--c-text-secondary);
}

.stat-value {
  font-size: 28px;
  line-height: 1;
  color: var(--c-text-primary);
  font-weight: 700;
}

.stat-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.list-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.list-item,
.dimension-item,
.action-item,
.requirement-item {
  padding: 14px 16px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--c-border-strong);
  background: var(--c-bg-surface-hover);
}

.item-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.item-main strong {
  font-size: 15px;
  color: var(--c-text-primary);
}

.item-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.muted-text {
  font-size: 12px;
  color: var(--c-text-muted);
  white-space: nowrap;
}

.section-intro {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-secondary);
}

.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  line-height: 1;
}

.chip-positive {
  background: rgba(20, 184, 166, 0.12);
  color: var(--c-accent-teal);
  border: 1px solid rgba(20, 184, 166, 0.22);
}

.chip-muted {
  background: rgba(148, 163, 184, 0.12);
  color: var(--c-text-secondary);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.priority-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  color: var(--c-text-secondary);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--c-border-strong);
  white-space: nowrap;
}

@media (max-width: 960px) {
  .stat-skel-grid,
  .stat-grid,
  .panel-grid {
    grid-template-columns: 1fr;
  }

  .item-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .muted-text,
  .priority-pill {
    white-space: normal;
  }
}
</style>
