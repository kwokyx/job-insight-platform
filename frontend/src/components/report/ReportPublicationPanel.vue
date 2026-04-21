<script setup>
import { onMounted, ref } from 'vue'
import GlowButton from '../common/GlowButton.vue'
import EmptyState from '../common/EmptyState.vue'
import { ShieldCheck, Globe, ArchiveRestore, Check, X } from 'lucide-vue-next'
import {
  fetchPublicationQueue,
  reviewReport,
  publishReport,
  unpublishReport,
  normalizeError
} from '../../api'

const props = defineProps({
  token: { type: String, default: '' },
  reportTypeLabel: { type: Function, default: (code) => code || '--' }
})
const emit = defineEmits(['error', 'success', 'select'])

const queue = ref([])
const loading = ref(false)
const busyId = ref(null)

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

function lifecycleLabel(item) {
  return item?.reportLifecycle?.stateLabel || '草稿'
}
function lifecycleState(item) {
  return item?.reportLifecycle?.state || 'DRAFT'
}

async function refresh() {
  loading.value = true
  try {
    const res = await fetchPublicationQueue(props.token, { page: 1, pageSize: 20 })
    queue.value = res.data || []
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    loading.value = false
  }
}

async function runAction(item, action) {
  busyId.value = item.id
  try {
    if (action === 'approve') {
      await reviewReport(props.token, item.id, { action: 'APPROVE', comment: '' })
      emit('success', '已审核通过')
    } else if (action === 'reject') {
      const comment = prompt('驳回原因（可选）') || ''
      await reviewReport(props.token, item.id, { action: 'REJECT', comment })
      emit('success', '已驳回')
    } else if (action === 'publish') {
      await publishReport(props.token, item.id)
      emit('success', '已发布')
    } else if (action === 'unpublish') {
      await unpublishReport(props.token, item.id)
      emit('success', '已撤回公开')
    }
    await refresh()
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    busyId.value = null
  }
}

onMounted(refresh)
defineExpose({ refresh })
</script>

<template>
  <article class="surface section-panel workspace-module-panel">
    <div class="panel-head workspace-panel-head">
      <div class="workspace-panel-copy">
        <h2 class="workspace-panel-title inline-icon"><ShieldCheck :size="15" /> 公开发布审核</h2>
      </div>
      <GlowButton variant="ghost" @click="refresh">刷新</GlowButton>
    </div>
    <div class="card-list">
      <div v-if="!queue.length && !loading" class="empty-state-wrapper">
        <EmptyState icon="inbox" title="暂无待审核报告" description="教师或学生提交审核的报告会出现在这里。" />
      </div>
      <div v-for="item in queue" :key="item.id" class="list-item">
        <div class="list-main" @click="emit('select', item)">
          <strong>{{ item.reportName || `报告 #${item.id}` }}</strong>
          <p>{{ reportTypeLabel(item.reportType) }} · {{ formatDateTime(item.generatedAt) }}</p>
          <p class="muted">生命周期：{{ lifecycleLabel(item) }}</p>
        </div>
        <div class="inline-actions">
          <template v-if="lifecycleState(item) === 'IN_REVIEW'">
            <button class="icon-btn good" :disabled="busyId === item.id" @click="runAction(item, 'approve')" title="通过">
              <Check :size="15" />
            </button>
            <button class="icon-btn danger" :disabled="busyId === item.id" @click="runAction(item, 'reject')" title="驳回">
              <X :size="15" />
            </button>
          </template>
          <button
            v-if="lifecycleState(item) === 'APPROVED'"
            class="icon-btn primary"
            :disabled="busyId === item.id"
            @click="runAction(item, 'publish')"
            title="发布"
          >
            <Globe :size="15" />
          </button>
          <button
            v-if="lifecycleState(item) === 'PUBLISHED' || item.isPublic === 1"
            class="icon-btn"
            :disabled="busyId === item.id"
            @click="runAction(item, 'unpublish')"
            title="撤回公开"
          >
            <ArchiveRestore :size="15" />
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card-list { display: flex; flex-direction: column; gap: 10px; max-height: 420px; overflow-y: auto; padding-right: 4px; }
.list-item {
  display: flex; justify-content: space-between; gap: 12px;
  padding: 14px 16px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
}
.list-main { min-width: 0; cursor: pointer; flex: 1; }
.list-main p { margin: 2px 0 0; font-size: 12px; color: var(--c-text-secondary); }
.list-main p.muted { color: var(--c-text-muted); }
.inline-actions { display: inline-flex; align-items: center; gap: 6px; }
.icon-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border-radius: 8px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary); cursor: pointer;
}
.icon-btn.good:hover { color: #16a34a; border-color: rgba(34, 197, 94, 0.45); }
.icon-btn.danger:hover { color: #dc2626; border-color: rgba(220, 38, 38, 0.45); }
.icon-btn.primary:hover { color: var(--c-accent-primary); border-color: rgba(30, 117, 255, 0.45); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.empty-state-wrapper { min-height: 120px; display: flex; align-items: center; justify-content: center; }
</style>
