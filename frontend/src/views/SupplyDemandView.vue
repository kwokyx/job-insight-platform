<script setup>
import { ref, onMounted } from 'vue'
import { request, buildQuery, authHeaders } from '../api'
import PremiumCard from '../components/common/PremiumCard.vue'

const props = defineProps({
  token: { type: String, default: '' }
})

const loading = ref(false)
const supplyDemandResult = ref(null)

async function loadData() {
  loading.value = true
  try {
    const res = await request('/analysis/deep/supply-demand', {
      method: 'POST',
      headers: authHeaders(props.token),
      body: JSON.stringify({})
    })
    supplyDemandResult.value = res.data
  } catch (e) {
    console.error('Failed to load supply demand:', e)
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
      <p>正在分析供需剪刀差...</p>
    </div>
    
    <template v-else-if="supplyDemandResult">
      <PremiumCard title="供需剪刀差诊断报告" class="full-width" glowColor="teal">
        <div class="markdown-body">
          <p class="summary-text">{{ supplyDemandResult.summary || '暂无总结' }}</p>
          <div class="score-card">
            <div class="score-item">
              <div class="score-label">综合供需指数</div>
              <div class="score-value">{{ supplyDemandResult.indexScore || 'N/A' }}</div>
            </div>
            <div class="score-item">
              <div class="score-label">人才缺口预警</div>
              <div class="score-value warning" v-if="supplyDemandResult.hasWarning">存在明显缺口</div>
              <div class="score-value safe" v-else>供需平稳</div>
            </div>
          </div>
          
          <div class="details-section">
            <h4>详细诊断分析</h4>
            <div class="detail-content" v-html="supplyDemandResult.details || '暂无详细分析'"></div>
          </div>
        </div>
      </PremiumCard>
    </template>
  </div>
</template>

<style scoped>
.supply-demand-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.summary-text {
  font-size: 15px;
  line-height: 1.6;
  color: var(--c-text-primary);
  margin-bottom: 24px;
}
.score-card {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}
.score-item {
  flex: 1;
  background: var(--c-bg-surface-hover);
  padding: 20px;
  border-radius: var(--radius-md);
  text-align: center;
}
.score-label {
  font-size: 13px;
  color: var(--c-text-secondary);
  margin-bottom: 8px;
}
.score-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--c-text-primary);
}
.score-value.warning { color: var(--c-accent-rose); }
.score-value.safe { color: var(--c-accent-teal); }

.details-section h4 {
  font-size: 16px;
  color: var(--c-text-primary);
  margin-bottom: 12px;
}
.detail-content {
  color: var(--c-text-secondary);
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
}
</style>
