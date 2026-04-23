<script setup>
import { computed, ref, watch } from 'vue'
import GlowButton from '../common/GlowButton.vue'
import EmptyState from '../common/EmptyState.vue'
import { CalendarClock, Info, Pause, Play, Trash2 } from 'lucide-vue-next'
import {
  createReportSchedule,
  deleteReportSchedule,
  fetchReportSchedule,
  normalizeError,
  toggleReportSchedule
} from '../../api'

const props = defineProps({
  schedules: { type: Array, default: () => [] },
  token: { type: String, default: '' },
  reportTypes: { type: Array, default: () => [] },
  defaultReportType: { type: String, default: 'COMPREHENSIVE' },
  reportTypeLabel: { type: Function, default: (code) => code || '--' }
})
const emit = defineEmits(['refresh', 'error', 'success'])

const singleReportType = computed(() => props.reportTypes.length <= 1)
const activeReportTypeCode = computed(
  () => props.reportTypes[0]?.code || props.defaultReportType || 'COMPREHENSIVE'
)
const activeReportTypeLabel = computed(
  () => props.reportTypes[0]?.label || props.reportTypeLabel(activeReportTypeCode.value)
)
const weekdayOptions = [
  { value: 'MON', label: '周一' },
  { value: 'TUE', label: '周二' },
  { value: 'WED', label: '周三' },
  { value: 'THU', label: '周四' },
  { value: 'FRI', label: '周五' },
  { value: 'SAT', label: '周六' },
  { value: 'SUN', label: '周日' }
]

const form = ref({
  scheduleName: '',
  reportType: activeReportTypeCode.value,
  frequency: 'DAILY',
  weekday: 'MON',
  time: '08:00'
})
const busy = ref(false)

watch(
  activeReportTypeCode,
  (nextCode) => {
    if (singleReportType.value) {
      form.value.reportType = nextCode
    }
  },
  { immediate: true }
)

const cronExpr = computed(() => buildCronExpr())

const expandedId = ref('')
const detailLoadingId = ref('')
const detailCache = ref({})

function buildCronExpr() {
  const [hourText = '8', minuteText = '0'] = `${form.value.time || '08:00'}`.split(':')
  const hour = Number.parseInt(hourText, 10)
  const minute = Number.parseInt(minuteText, 10)
  const safeHour = Number.isFinite(hour) ? Math.min(Math.max(hour, 0), 23) : 8
  const safeMinute = Number.isFinite(minute) ? Math.min(Math.max(minute, 0), 59) : 0

  if (form.value.frequency === 'WEEKLY') {
    const dow = form.value.weekday || 'MON'
    return `0 ${safeMinute} ${safeHour} ? * ${dow}`
  }
  return `0 ${safeMinute} ${safeHour} * * ?`
}

function frequencyLabel(item) {
  if (item.frequencyMode === 'WEEKLY') {
    const target = weekdayOptions.find((w) => w.value === item.frequencyDayOfWeek)
    const weekday = target ? target.label : '每周'
    return `每周 ${weekday} ${item.frequencyTimeLabel || ''}`.trim()
  }
  return `每天 ${item.frequencyTimeLabel || ''}`.trim()
}

function detailRowsFor(id) {
  const d = detailCache.value[id]
  if (!d) return []
  return [
    ['调度名称', d.scheduleName || '--'],
    ['报告类型', d.reportType || '--'],
    ['执行频率', frequencyLabel(d)],
    ['Cron 表达式', d.cronExpr || '--'],
    ['状态', d.isActive === 1 ? '启用' : '停用'],
    ['下次运行', formatDateTime(d.nextRunAt)],
    ['最近执行', formatDateTime(d.lastRunAt)],
    ['执行次数', d.runCount ?? d.totalRuns ?? '--'],
    ['创建时间', formatDateTime(d.createdAt)],
    ['更新时间', formatDateTime(d.updatedAt)]
  ]
}

function formatDateTime(value) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

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

async function handleCreate() {
  if (!form.value.time) {
    emit('error', '请选择执行时间')
    return
  }
  const reportType = singleReportType.value ? activeReportTypeCode.value : form.value.reportType
  if (!reportType) {
    emit('error', '未找到可用的报告类型')
    return
  }
  busy.value = true
  try {
    await createReportSchedule(props.token, {
      scheduleName: (form.value.scheduleName || '').trim(),
      reportType,
      cronExpr: cronExpr.value,
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

async function handleDelete(item) {
  const name = item.scheduleName || `调度 #${item.id}`
  if (!confirm(`确定删除调度“${name}”吗？`)) return
  busy.value = true
  try {
    await deleteReportSchedule(props.token, item.id)
    emit('success', '调度已删除')
    emit('refresh')
  } catch (e) {
    emit('error', normalizeError(e))
  } finally {
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
        <div class="glass-input readonly-label">
          {{ activeReportTypeLabel }}
        </div>
        <select v-model="form.frequency" class="glass-input">
          <option value="DAILY">每天</option>
          <option value="WEEKLY">每周</option>
        </select>
        <select v-if="form.frequency === 'WEEKLY'" v-model="form.weekday" class="glass-input">
          <option v-for="item in weekdayOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <input v-model="form.time" type="time" class="glass-input" />
        <GlowButton variant="primary" :loading="busy" @click="handleCreate">新建调度</GlowButton>
      </div>

      <div v-if="!schedules.length" class="empty-state-wrapper">
        <EmptyState icon="calendar" title="暂无调度计划" description="选择执行频率和时间后可创建周期任务。" />
      </div>
      <div v-for="item in schedules" :key="item.id" class="schedule-row-wrap">
        <div class="list-item">
          <div class="list-main">
            <strong>{{ item.scheduleName || `调度 #${item.id}` }}</strong>
            <p>{{ reportTypeLabel(item.reportType) }} · {{ frequencyLabel(item) }}</p>
            <p>下次运行：{{ formatDateTime(item.nextRunAt) }}</p>
          </div>
          <div class="inline-actions">
            <span class="pill" :class="{ good: item.isActive === 1 }">
              {{ item.isActive === 1 ? '运行中' : '已停用' }}
            </span>
            <button
              class="icon-btn"
              :disabled="detailLoadingId === item.id"
              :title="expandedId === item.id ? '收起详情' : '查看详情'"
              @click="handleShowDetail(item)"
            >
              <Info :size="15" />
            </button>
            <button
              class="icon-btn"
              :disabled="busy"
              :title="item.isActive === 1 ? '停用' : '启用'"
              @click="handleToggle(item)"
            >
              <component :is="item.isActive === 1 ? Pause : Play" :size="15" />
            </button>
            <button class="icon-btn danger" :disabled="busy" title="删除" @click="handleDelete(item)">
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
  </article>
</template>

<style scoped>
.card-list { display: flex; flex-direction: column; gap: 12px; }
.schedule-form {
  display: grid;
  gap: 8px;
  grid-template-columns: 1.3fr 1fr 0.8fr 0.8fr 1fr auto;
}
.glass-input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  font-size: 13px;
}
.readonly-label {
  display: inline-flex;
  align-items: center;
}
.list-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(193, 198, 215, 0.5);
}
.list-main p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--c-text-secondary);
}
.inline-actions { display: inline-flex; align-items: center; gap: 8px; }
.pill {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}
.pill.good {
  background: rgba(34, 197, 94, 0.15);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.3);
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: rgba(255, 255, 255, 0.6);
  color: var(--c-text-secondary);
  cursor: pointer;
}
.icon-btn:hover {
  color: var(--c-accent-primary);
  border-color: rgba(30, 117, 255, 0.4);
}
.icon-btn.danger:hover {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.4);
}
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.empty-state-wrapper {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.schedule-row-wrap { display: flex; flex-direction: column; gap: 8px; }
.schedule-detail {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(30, 117, 255, 0.04);
  border: 1px dashed rgba(30, 117, 255, 0.28);
}
.schedule-detail-loading { font-size: 12px; color: var(--c-text-muted); }
.schedule-detail-grid {
  display: grid;
  gap: 6px 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}
.schedule-detail-row {
  display: flex;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
}
.schedule-detail-row dt {
  color: var(--c-text-muted);
  flex: 0 0 80px;
}
.schedule-detail-row dd {
  margin: 0;
  color: var(--c-text-primary);
  word-break: break-all;
}

@media (max-width: 1200px) {
  .schedule-form { grid-template-columns: 1fr 1fr 1fr 1fr; }
}

@media (max-width: 860px) {
  .schedule-form { grid-template-columns: 1fr; }
  .schedule-detail-grid { grid-template-columns: 1fr; }
}
</style>
