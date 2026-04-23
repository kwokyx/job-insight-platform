<script setup>
import { computed, ref, watch } from 'vue'
import GlowButton from '../common/GlowButton.vue'
import EmptyState from '../common/EmptyState.vue'
import ConfirmDialog from '../common/ConfirmDialog.vue'
import { CalendarClock, Info, Trash2, Play, Pause } from 'lucide-vue-next'
import {
  createReportSchedule,
  fetchReportSchedule,
  toggleReportSchedule,
  deleteReportSchedule,
  normalizeError
} from '../../api'

const props = defineProps({
  schedules: { type: Array, default: () => [] },
  token: { type: String, default: '' },
  reportTypes: { type: Array, default: () => [] },
  defaultReportType: { type: String, default: 'COMPREHENSIVE' },
  reportTypeLabel: { type: Function, default: (code) => code || '--' }
})
const emit = defineEmits(['refresh', 'error', 'success'])

const form = ref({
  scheduleName: '',
  reportType: props.defaultReportType,
  cronExpr: '0 0 8 * * ?'
})
const busy = ref(false)

const currentReportTypeLabel = computed(() => (
  props.reportTypes.find((item) => item.code === form.value.reportType)?.label
  || props.reportTypeLabel(form.value.reportType)
))

watch(() => props.defaultReportType, (nextType) => {
  if (nextType) form.value.reportType = nextType
}, { immediate: true })

// 展开查看调度详情：点击「详情」时拉 fetchReportSchedule，把返回字段内联展示
const expandedId = ref('')
const detailLoadingId = ref('')
const detailCache = ref({}) // { [id]: scheduleDetail }

async function handleShowDetail(item) {
  if (!item?.id) return
  if (expandedId.value === item.id) {
    expandedId.value = ''
    return
  }
  expandedId.value = item.id
  if (detailCache.value[item.id]) return
  detailLoadingId.value = item.id
  try {
    const data = await fetchReportSchedule(props.token, item.id)
    detailCache.value = { ...detailCache.value, [item.id]: data || {} }
  } catch (e) {
    emit('error', normalizeError(e))
    expandedId.value = ''
  } finally {
    detailLoadingId.value = ''
  }
}

function detailRowsFor(id) {
  const d = detailCache.value[id]
  if (!d) return []
  const rows = [
    ['调度名称', d.scheduleName || '--'],
    ['报告类型', d.reportType || '--'],
    ['Cron 表达式', d.cronExpr || '--'],
    ['状态', d.isActive === 1 ? '启用' : '停用'],
    ['下次运行', formatDateTime(d.nextRunAt)],
    ['最近执行', formatDateTime(d.lastRunAt)],
    ['执行次数', d.runCount ?? d.totalRuns ?? '--'],
    ['创建时间', formatDateTime(d.createdAt)],
    ['更新时间', formatDateTime(d.updatedAt)]
  ]
  return rows
}

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

async function handleCreate() {
  if (!form.value.cronExpr?.trim()) {
    emit('error', '请填写 Cron 表达式（Spring 格式，如 0 0 8 * * ?）')
    return
  }
  busy.value = true
  try {
    await createReportSchedule(props.token, {
      scheduleName: form.value.scheduleName?.trim() || '',
      reportType: form.value.reportType,
      cronExpr: form.value.cronExpr.trim(),
      params: {}
    })
    emit('success', '调度计划已创建')
    form.value.scheduleName = ''
    emit('refresh')
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    busy.value = false
  }
}

async function handleToggle(item) {
  busy.value = true
  try {
    await toggleReportSchedule(props.token, item.id)
    emit('success', item.isActive === 1 ? '已停用调度' : '已启用调度')
    emit('refresh')
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    busy.value = false
  }
}

// 删除确认弹窗
const deleteDialog = ref({ open: false, id: null, name: '', loading: false })

function requestDelete(item) {
  deleteDialog.value = {
    open: true,
    id: item.id,
    name: item.scheduleName || `调度 #${item.id}`,
    loading: false
  }
}

function cancelDelete() {
  if (deleteDialog.value.loading) return
  deleteDialog.value.open = false
}

async function confirmDelete() {
  const { id } = deleteDialog.value
  if (!id) return
  deleteDialog.value.loading = true
  busy.value = true
  try {
    await deleteReportSchedule(props.token, id)
    emit('success', '调度已删除')
    deleteDialog.value.open = false
    emit('refresh')
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
    deleteDialog.value.loading = false
    busy.value = false
  }
}
</script>

<template>
  <article class="surface section-panel workspace-module-panel">
    <div class="panel-head workspace-panel-head">
      <div class="workspace-panel-copy">
        <h2 class="workspace-panel-title inline-icon"><CalendarClock :size="15" /> 调度任务</h2>
      </div>
    </div>
    <div class="card-list">
      <div class="schedule-form">
        <input v-model="form.scheduleName" class="glass-input" placeholder="调度名称（可留空，使用默认）" />
        <div class="report-type-static" aria-label="报告类型">
          <span>报告类型：</span>
          <strong>{{ currentReportTypeLabel }}</strong>
        </div>
        <input v-model="form.cronExpr" class="glass-input" placeholder="Cron 表达式，例如 0 0 8 * * ?" />
        <GlowButton variant="primary" :loading="busy" @click="handleCreate">新建调度</GlowButton>
      </div>

      <div v-if="!schedules.length" class="empty-state-wrapper">
        <EmptyState icon="calendar" title="暂无调度计划" description="填写 Cron 表达式即可创建周期报告任务。" />
      </div>
      <div v-for="item in schedules" :key="item.id" class="schedule-row-wrap">
        <div class="list-item">
          <div class="list-main">
            <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
            <p>{{ reportTypeLabel(item.reportType) }} · <code>{{ item.cronExpr || '--' }}</code></p>
            <p>下次运行：{{ formatDateTime(item.nextRunAt) }}</p>
          </div>
          <div class="inline-actions">
            <span class="pill" :class="{ good: item.isActive === 1 }">
              {{ item.isActive === 1 ? '运行中' : '已停用' }}
            </span>
            <button class="icon-btn" :disabled="detailLoadingId === item.id" @click="handleShowDetail(item)" :title="expandedId === item.id ? '收起详情' : '查看详情'">
              <Info :size="15" />
            </button>
            <button class="icon-btn" :disabled="busy" @click="handleToggle(item)" :title="item.isActive === 1 ? '停用' : '启用'">
              <component :is="item.isActive === 1 ? Pause : Play" :size="15" />
            </button>
            <button class="icon-btn danger" :disabled="busy" @click="requestDelete(item)" title="删除">
              <Trash2 :size="15" />
            </button>
          </div>
        </div>
        <div v-if="expandedId === item.id" class="schedule-detail">
          <div v-if="detailLoadingId === item.id" class="schedule-detail-loading">正在拉取详情...</div>
          <dl v-else class="schedule-detail-grid">
            <div v-for="[label, value] in detailRowsFor(item.id)" :key="label" class="schedule-detail-row">
              <dt>{{ label }}</dt>
              <dd>{{ value }}</dd>
            </div>
          </dl>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :open="deleteDialog.open"
      title="删除调度"
      description="删除后该调度计划将不再执行，且无法恢复。"
      :detail="deleteDialog.name"
      confirm-text="删除"
      cancel-text="取消"
      variant="danger"
      :loading="deleteDialog.loading"
      @confirm="confirmDelete"
      @cancel="cancelDelete"
      @update:open="(v) => (deleteDialog.open = v)"
    />
  </article>
</template>

<style scoped>
.card-list { display: flex; flex-direction: column; gap: 12px; }
.schedule-form {
  display: grid; gap: 8px;
  grid-template-columns: 1fr 1fr 1fr auto;
}
.glass-input {
  width: 100%; padding: 10px 12px; border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary); font-size: 13px;
}
.report-type-static {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-size: 13px;
}
.report-type-static strong {
  color: var(--c-text-primary);
  font-weight: 700;
}
.list-item {
  display: flex; justify-content: space-between; gap: 12px;
  padding: 14px 16px; border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
}
.list-main p { margin: 2px 0 0; font-size: 12px; color: var(--c-text-secondary); }
.list-main code { color: var(--c-text-muted); }
.inline-actions { display: inline-flex; align-items: center; gap: 8px; }
.pill {
  padding: 4px 10px; border-radius: 999px;
  font-size: 12px; background: var(--c-bg-surface-hover); color: var(--c-text-secondary);
}
.pill.good {
  background: rgba(34, 197, 94, .15);
  color: #16a34a; border: 1px solid rgba(34, 197, 94, .3);
}
.icon-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border-radius: 8px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary); cursor: pointer;
}
.icon-btn:hover { color: var(--c-accent-primary); border-color: rgba(30, 117, 255, 0.4); }
.icon-btn.danger:hover { color: #dc2626; border-color: rgba(220, 38, 38, 0.4); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.empty-state-wrapper { min-height: 120px; display: flex; align-items: center; justify-content: center; }
.schedule-row-wrap { display: flex; flex-direction: column; gap: 8px; }
.schedule-detail {
  padding: 12px 14px; border-radius: 12px;
  background: rgba(30, 117, 255, 0.04);
  border: 1px dashed rgba(30, 117, 255, 0.28);
}
.schedule-detail-loading { font-size: 12px; color: var(--c-text-muted); }
.schedule-detail-grid {
  display: grid; gap: 6px 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}
.schedule-detail-row {
  display: flex; gap: 6px; min-width: 0;
  font-size: 12px;
}
.schedule-detail-row dt {
  color: var(--c-text-muted); flex: 0 0 80px;
}
.schedule-detail-row dd {
  margin: 0; color: var(--c-text-primary);
  word-break: break-all;
}

@media (max-width: 860px) {
  .schedule-form { grid-template-columns: 1fr; }
  .schedule-detail-grid { grid-template-columns: 1fr; }
}
</style>
