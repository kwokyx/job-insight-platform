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
  reportTypeLabel: { type: Function, default: (code) => code || '--' },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['error', 'success', 'select'])

const queue = ref([])
const loading = ref(false)
const busyId = ref(null)
const rejectingId = ref(null)
const rejectComment = ref('')

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

function lifecycleMeta(item) {
  const state = lifecycleState(item)
  if (state === 'IN_REVIEW') {
    return {
      label: '审核中',
      tone: 'review',
      nextStep: '下一步：通过审核或驳回',
      description: '这份报告已经送审，现在由管理员决定是否通过。'
    }
  }
  if (state === 'APPROVED') {
    return {
      label: '已审核可发布',
      tone: 'approved',
      nextStep: '下一步：公开发布',
      description: '审核已经完成，只差最后一步公开发布。'
    }
  }
  return {
    label: '已公开',
    tone: 'public',
    nextStep: '下一步：如需下架，可撤回公开',
    description: '这份报告已经进入公开报告库。'
  }
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

function startReject(item) {
  rejectingId.value = item.id
  rejectComment.value = ''
}

function cancelReject() {
  rejectingId.value = null
  rejectComment.value = ''
}

async function runAction(item, action) {
  busyId.value = item.id
  try {
    if (action === 'approve') {
      await reviewReport(props.token, item.id, { action: 'APPROVE', comment: '' })
      emit('success', '已审核通过')
    } else if (action === 'publish') {
      await publishReport(props.token, item.id)
      emit('success', '已发布')
    } else if (action === 'unpublish') {
      await unpublishReport(props.token, item.id)
      emit('success', '已撤回公开')
    }
    cancelReject()
    await refresh()
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    busyId.value = null
  }
}

async function confirmReject(item) {
  busyId.value = item.id
  try {
    await reviewReport(props.token, item.id, {
      action: 'REJECT',
      comment: rejectComment.value.trim()
    })
    emit('success', '已驳回')
    cancelReject()
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
  <component :is="embedded ? 'div' : 'article'" :class="embedded ? 'publication-embed' : 'surface section-panel workspace-module-panel'">
    <div v-if="!embedded" class="panel-head workspace-panel-head">
      <div class="workspace-panel-copy">
        <h2 class="workspace-panel-title inline-icon"><ShieldCheck :size="15" /> 审核 / 发布</h2>
      </div>
      <GlowButton variant="ghost" @click="refresh">刷新</GlowButton>
    </div>
    <div class="card-list">
      <div v-if="!queue.length && !loading" class="empty-state-wrapper">
        <EmptyState icon="inbox" title="暂无可审核或发布的报告" description="已送审、已审核或已公开的报告会出现在这里。" />
      </div>
      <div v-for="item in queue" :key="item.id" class="list-item">
        <div class="list-item-top">
          <div class="list-main" @click="emit('select', item)">
            <strong>{{ item.reportName || `报告 #${item.id}` }}</strong>
            <div class="status-row">
              <span class="state-badge" :class="`is-${lifecycleMeta(item).tone}`">{{ lifecycleMeta(item).label }}</span>
              <span class="next-step-pill">{{ lifecycleMeta(item).nextStep }}</span>
            </div>
            <p>{{ reportTypeLabel(item.reportType) }} · {{ formatDateTime(item.generatedAt) }}</p>
            <p class="muted">{{ lifecycleMeta(item).description }}</p>
          </div>
          <div class="action-stack">
            <template v-if="lifecycleState(item) === 'IN_REVIEW'">
              <button class="flow-action-btn primary" :disabled="busyId === item.id" @click="runAction(item, 'approve')">
                <Check :size="15" />
                <span>审核通过</span>
              </button>
              <button class="flow-action-btn danger" :disabled="busyId === item.id" @click="startReject(item)">
                <X :size="15" />
                <span>驳回</span>
              </button>
            </template>
            <button
              v-if="lifecycleState(item) === 'APPROVED'"
              class="flow-action-btn primary"
              :disabled="busyId === item.id"
              @click="runAction(item, 'publish')"
            >
              <Globe :size="15" />
              <span>公开发布</span>
            </button>
            <button
              v-if="lifecycleState(item) === 'PUBLISHED' || item.isPublic === 1"
              class="flow-action-btn subtle"
              :disabled="busyId === item.id"
              @click="runAction(item, 'unpublish')"
            >
              <ArchiveRestore :size="15" />
              <span>撤回公开</span>
            </button>
          </div>
        </div>
        <div v-if="rejectingId === item.id" class="reject-box">
          <label class="reject-field">
            <span>驳回原因</span>
            <textarea
              v-model="rejectComment"
              rows="3"
              class="reject-textarea"
              placeholder="可选。填写后，修改报告的人会更清楚需要调整什么。"
            />
          </label>
          <div class="reject-actions">
            <button type="button" class="flow-action-btn" :disabled="busyId === item.id" @click="cancelReject">
              取消
            </button>
            <button type="button" class="flow-action-btn danger" :disabled="busyId === item.id" @click="confirmReject(item)">
              <X :size="15" />
              <span>确认驳回</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </component>
</template>

<style scoped>
/* 固定高度，内容不足时也撑满；溢出才出滚动条 */
.card-list {
  display: flex; flex-direction: column; gap: 10px;
  height: 640px; overflow-y: auto; padding-right: 4px;
  align-content: flex-start;
}
@media (max-width: 900px) { .card-list { height: auto; } }
.list-item {
  display: grid;
  gap: 12px;
  padding: 14px 16px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
}
.list-item-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.list-main { min-width: 0; cursor: pointer; flex: 1; }
.list-main p { margin: 2px 0 0; font-size: 12px; color: var(--c-text-secondary); }
.list-main p.muted {
  color: var(--c-text-secondary);
  line-height: 1.55;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}
.state-badge,
.next-step-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.state-badge {
  border: 1px solid transparent;
}
.state-badge.is-review {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.22);
}
.state-badge.is-approved {
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.22);
}
.state-badge.is-public {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
  border-color: rgba(22, 163, 74, 0.2);
}
.next-step-pill {
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-text-secondary);
}
.action-stack,
.reject-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}
.flow-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.72);
  color: var(--c-text-secondary);
  cursor: pointer;
  font-size: 11.5px;
  font-weight: 600;
  transition: border-color .16s, background-color .16s, color .16s, transform .1s;
}
.flow-action-btn:hover { border-color: rgba(30, 117, 255, 0.34); color: var(--c-accent-primary); }
.flow-action-btn:active { transform: translateY(1px); }
.flow-action-btn.primary {
  background: rgba(30, 117, 255, 0.1);
  color: var(--c-accent-primary);
  border-color: rgba(30, 117, 255, 0.22);
}
.flow-action-btn.subtle {
  background: rgba(245, 158, 11, 0.08);
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.18);
}
.flow-action-btn.danger {
  background: rgba(220, 38, 38, 0.06);
  color: #b91c1c;
  border-color: rgba(220, 38, 38, 0.16);
}
.flow-action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.reject-box {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(220, 38, 38, 0.14);
  background: rgba(220, 38, 38, 0.04);
}
.reject-field {
  display: grid;
  gap: 6px;
}
.reject-field span {
  color: var(--c-text-primary);
  font-size: 12px;
  font-weight: 600;
}
.reject-textarea {
  width: 100%;
  resize: vertical;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(193, 198, 215, 0.55);
  background: rgba(255, 255, 255, 0.84);
  color: var(--c-text-primary);
  font-size: 12.5px;
  font-family: inherit;
}
.empty-state-wrapper { min-height: 120px; display: flex; align-items: center; justify-content: center; }
@media (max-width: 900px) {
  .list-item-top { flex-direction: column; }
  .action-stack, .reject-actions { justify-content: flex-start; }
}
</style>
