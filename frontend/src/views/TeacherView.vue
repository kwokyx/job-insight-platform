<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { fetchTeacherMarketMatch } from '../api'
import PremiumCard from '../components/common/PremiumCard.vue'

const authStore = useAuthStore()
const { error } = useToast()
const loading = ref(true)
const matchResult = ref(null)

onMounted(async () => {
  if (authStore.user?.roleType !== 2) return
  try {
    matchResult.value = await fetchTeacherMarketMatch(authStore.token)
  } catch (e) {
    error('加载市场匹配分析失败: ' + e.message)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="teacher-view page-shell">
    <div class="page-header">
      <div class="header-content">
        <h1>课程与市场分析</h1>
        <p>分析教学课程与市场招聘需求的匹配度</p>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loader-ring"></div>
      <p>正在分析课程匹配度...</p>
    </div>
    <template v-else-if="matchResult">
      <PremiumCard title="分析建议" glowColor="primary" class="mb-4">
        <ul class="suggestions">
          <li v-for="(rec, i) in matchResult.recommendations" :key="i">{{ rec }}</li>
        </ul>
      </PremiumCard>

      <div class="match-grid">
        <PremiumCard title="已覆盖的热门技能" glowColor="teal">
          <div class="skills-list">
            <span v-for="s in matchResult.coveredSkills" :key="s" class="skill-tag covered">{{ s }}</span>
            <div v-if="!matchResult.coveredSkills?.length" class="empty-state">无覆盖</div>
          </div>
        </PremiumCard>

        <PremiumCard title="建议新增的市场缺口" glowColor="rose">
          <div class="skills-list">
            <span v-for="g in matchResult.marketGaps" :key="g.skill" class="skill-tag gap">
              {{ g.skill }} <small>({{ g.marketDemand }}岗)</small>
            </span>
            <div v-if="!matchResult.marketGaps?.length" class="empty-state">无缺口</div>
          </div>
        </PremiumCard>

        <PremiumCard title="可能过时的技能" glowColor="purple">
          <div class="skills-list">
            <span v-for="s in matchResult.possiblyOutdated" :key="s" class="skill-tag outdated">{{ s }}</span>
            <div v-if="!matchResult.possiblyOutdated?.length" class="empty-state">无过时技能</div>
          </div>
        </PremiumCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.teacher-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.mb-4 { margin-bottom: 24px; }

.suggestions {
  margin: 0;
  padding-left: 20px;
  color: var(--c-text-primary);
  line-height: 1.6;
}
.suggestions li { margin-bottom: 8px; }

.match-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 24px;
}

.skills-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-tag {
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  background: var(--c-bg-surface-hover);
}

.skill-tag.covered {
  background: rgba(20, 184, 166, 0.1);
  color: #14b8a6;
  border: 1px solid rgba(20, 184, 166, 0.2);
}

.skill-tag.gap {
  background: rgba(244, 63, 94, 0.1);
  color: #f43f5e;
  border: 1px solid rgba(244, 63, 94, 0.2);
}

.skill-tag.outdated {
  background: rgba(168, 85, 247, 0.1);
  color: #a855f7;
  border: 1px solid rgba(168, 85, 247, 0.2);
}

.skill-tag small {
  opacity: 0.7;
  font-size: 11px;
}
</style>
