<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarClock, Info, Pause, Play, Trash2 } from 'lucide-vue-next'
import GlowButton from '../common/GlowButton.vue'
import EmptyState from '../common/EmptyState.vue'
import ConfirmDialog from '../common/ConfirmDialog.vue'
import {
  createReportSchedule,
  deleteReportSchedule,
  fetchReportSchedule,
  normalizeError,
  toggleReportSchedule
} from '../../api'

const WEEKDAY_OPTIONS = [
  { value: 'MON', label: '周一', cron: 'MON' },
  { value: 'TUE', label: '周二', cron: 'TUE' },
  { value: 'WED', label: '周三', cron: 'WED' },
  { value: 'THU', label: '周四', cron: 'THU' },
  { value: 'FRI', label: '周五', cron: 'FRI' },
  { value: 'SAT', label: '周六', cron: 'SAT' },
  { value: 'SUN', label: '周日', cron: 'SUN' }
]

const MONTH_DAY_OPTIONS = Array.from({ length: 28 }, (_, index) => {
  const value = String(index + 1)
  return { value, label: `${value} 日` }
})

const props = defineProps({
  schedules: { type: Array, default: () => [] },
  token: { type: String, default: '' },
  reportTypes: { type: Array, default: () => [] },
  defaultReportType: { type: String, default: '' },
  reportTypeLabel: { type: Function, default: (value) => value || '--' }
})

const emit = defineEmits(['refresh', 'error', 'success'])

function createDefaultForm(reportType) {
  return {
    scheduleName: '',
    reportType: reportType || '',
    frequency: 'DAILY',
    weekday: 'MON',
    dayOfMonth: '1',
    time: '08:00'
  }
}

const form = ref(createDefaultForm(props.defaultReportType))
const busy = ref(false)
const expandedId = ref('')
const detailLoadingId = ref('')
const detailCache = ref({})
const deleteDialog = ref({ open: false, id: null, name: '', loading: false })

watch(
  () => props.defaultReportType,
  (nextType) => {
    if (nextType && !form.value.reportType) {
      form.value.reportType = nextType
    }
  },
  { immediate: true }
)

watch(
  () => form.value.frequency,
  (nextValue) => {
    if (nextValue === 'WEEKLY' && !form.value.weekday) form.value.weekday = 'MON'
    if (nextValue === 'MONTHLY' && !form.value.dayOfMonth) form.value.dayOfMonth = '1'
  }
)

const normalizedSchedules = computed(() => (Array.isArray(props.schedules) ? props.schedules : []))
const currentReportTypeLabel = computed(() => {
  return props.reportTypes.find((item) => item.code === form.value.reportType)?.label
    || props.reportTypeLabel(form.value.reportType)
})
const humanScheduleDescription = computed(() => describeRule(form.value))

function parseTimeParts(value) {
  const raw = typeof value === 'string' ? value.trim() : ''
  const [hourText = '08', minuteText = '00'] = raw.split(':')
  const hour = Number(hourText)
  const minute = Number(minuteText)
  return {
    hour: Number.isInteger(hour) && hour >= 0 && hour <= 23 ? hour : 8,
    minute: Number.isInteger(minute) && minute >= 0 && minute <= 59 ? minute : 0
  }
}

function buildCronExpr(rule) {
  const { hour, minute } = parseTimeParts(rule.time)
  if (rule.frequency === 'WEEKLY') {
    const weekday = WEEKDAY_OPTIONS.find((item) => item.value === rule.weekday)?.cron || 'MON'
    return `0 ${minute} ${hour} ? * ${weekday}`
  }
  if (rule.frequency === 'MONTHLY') {
    const day = Number(rule.dayOfMonth)
    const safeDay = Number.isInteger(day) && day >= 1 && day <= 28 ? day : 1
    return `0 ${minute} ${hour} ${safeDay} * ?`
  }
  return `0 ${minute} ${hour} * * ?`
}

function normalizeCronExpr(cronExpr) {
  return typeof cronExpr === 'string' ? cronExpr.trim().replace(/\s+/g, ' ') : ''
}

function parseCronExpr(cronExpr) {
  const normalized = normalizeCronExpr(cronExpr)
  const parts = normalized.split(' ')
  if (parts.length < 6) {
    return { summary: normalized || '--' }
  }

  const [, minute = '0', hour = '8', dayOfMonth = '*', , dayOfWeek = '?'] = parts
  const time = `${String(Number(hour) || 0).padStart(2, '0')}:${String(Number(minute) || 0).padStart(2, '0')}`

  if (dayOfMonth === '?' && dayOfWeek !== '?' && dayOfWeek !== '*') {
    const weekdayLabel = WEEKDAY_OPTIONS.find((item) => item.cron === dayOfWeek.toUpperCase())?.label || dayOfWeek
    return { summary: `每周 ${weekdayLabel} ${time}` }
  }

  if (dayOfMonth !== '?' && dayOfMonth !== '*') {
    return { summary: `每月 ${dayOfMonth} 日 ${time}` }
  }

  return { summary: `每天 ${time}` }
}

function describeRule(rule) {
  if (!rule) return '--'
  if (rule.frequency === 'WEEKLY') {
    const weekdayLabel = WEEKDAY_OPTIONS.find((item) => item.value === rule.weekday)?.label || '周一'
    return `每周 ${weekdayLabel} ${rule.time || '08:00'}`
  }
  if (rule.frequency === 'MONTHLY') {
    return `每月 ${rule.dayOfMonth || '1'} 日 ${rule.time || '08:00'}`
  }
  return `每天 ${rule.time || '08:00'}`
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function detailRowsFor(id) {
  const detail = detailCache.value[id]
  if (!detail) return []
  return [
    ['调度名称', detail.scheduleName || '--'],
    ['执行规则', parseCronExpr(detail.cronExpr).summary || '--'],
    ['状态', Number(detail.isActive) === 1 ? '启用' : '停用'],
    ['下次运行', formatDateTime(detail.nextRunAt)],
    ['最近执行', formatDateTime(detail.lastRunAt)],
    ['执行次数', detail.runCount ?? detail.totalRuns ?? '--'],
    ['创建时间', formatDateTime(detail.createdAt)],
    ['更新时间', formatDateTime(detail.updatedAt)]
  ]
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
  } catch (error) {
    emit('error', normalizeError(error))
    expandedId.value = ''
  } finally {
    detailLoadingId.value = ''
  }
}

async function handleCreate() {
  if (!props.token) return
  if (!form.value.time) {
    emit('error', '请选择执行时间。')
    return
  }

  busy.value = true
  try {
    await createReportSchedule(props.token, {
      scheduleName: form.value.scheduleName.trim(),
      reportType: form.value.reportType || props.defaultReportType,
      cronExpr: buildCronExpr(form.value),
      params: {}
    })
    emit('success', `调度计划已创建：${humanScheduleDescription.value}`)
    form.value = createDefaultForm(form.value.reportType || props.defaultReportType)
    emit('refresh')
  } catch (error) {
    emit('error', normalizeError(error))
  } finally {
    busy.value = false
  }
}

async function handleToggle(item) {
  if (!props.token || !item?.id) return
  busy.value = true
  try {
    await toggleReportSchedule(props.token, item.id)
    emit('success', Number(item.isActive) === 1 ? '已停用调度' : '已启用调度')
    emit('refresh')
  } catch (error) {
    emit('error', normalizeError(error))
  } finally {
    busy.value = false
  }
}

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
  if (!props.token || !id) return

  deleteDialog.value.loading = true
  busy.value = true
  try {
    await deleteReportSchedule(props.token, id)
    emit('success', '调度已删除')
    deleteDialog.value.open = false
    emit('refresh')
  } catch (error) {
    emit('error', normalizeError(error))
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
        <p class="workspace-panel-description">
          当前角色只有一个报告类型，创建时只需要选择执行周期和时间。
        </p>
      </div>
    </div>

    <div class="card-list">
      <div class="schedule-form">
        <input
          v-model="form.scheduleName"
          class="glass-input"
          type="text"
          maxlength="80"
          placeholder="调度名称（可留空，使用默认）"
        />

        <select v-model="form.frequency" class="glass-input">
          <option value="DAILY">每天执行</option>
          <option value="WEEKLY">每周执行</option>
          <option value="MONTHLY">每月执行</option>
        </select>

        <select v-if="form.frequency === 'WEEKLY'" v-model="form.weekday" class="glass-input">
          <option v-for="item in WEEKDAY_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <select v-else-if="form.frequency === 'MONTHLY'" v-model="form.dayOfMonth" class="glass-input">
          <option v-for="item in MONTH_DAY_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <div v-else class="glass-input static-field">每日执行</div>

        <input v-model="form.time" class="glass-input" type="time" step="60" />

        <GlowButton variant="primary" :loading="busy" @click="handleCreate">新建调度</GlowButton>
      </div>

      <div class="schedule-hint">
        <span class="hint-label">执行规则</span>
        <strong>{{ humanScheduleDescription }}</strong>
      </div>

      <div v-if="!normalizedSchedules.length" class="empty-state-wrapper">
        <EmptyState icon="calendar" title="暂无调度计划" description="选择执行周期和时间后即可创建周期报告任务。" />
      </div>

      <div v-for="item in normalizedSchedules" :key="item.id" class="schedule-row-wrap">
        <div class="list-item">
          <div class="list-main">
            <strong>{{ item.scheduleName || currentReportTypeLabel || `调度 #${item.id}` }}</strong>
            <p>{{ parseCronExpr(item.cronExpr).summary || '--' }}</p>
            <p>下次运行：{{ formatDateTime(item.nextRunAt) }}</p>
          </div>

          <div class="inline-actions">
            <span class="pill" :class="{ good: Number(item.isActive) === 1 }">
              {{ Number(item.isActive) === 1 ? '运行中' : '已停用' }}
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
              :title="Number(item.isActive) === 1 ? '停用' : '启用'"
              @click="handleToggle(item)"
            >
              <component :is="Number(item.isActive) === 1 ? Pause : Play" :size="15" />
            </button>

            <button class="icon-btn danger" :disabled="busy" title="删除" @click="requestDelete(item)">
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
      @update:open="(value) => (deleteDialog.open = value)"
    />
  </article>
</template>

<style scoped>
.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.schedule-form {
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(220px, 1.3fr) minmax(140px, 0.9fr) minmax(140px, 0.9fr) minmax(120px, 0.8fr) auto;
}

.glass-input {
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--c-bg-surface-strong);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
  font-size: 13px;
}

.static-field {
  display: inline-flex;
  align-items: center;
  color: var(--c-text-secondary);
}

.schedule-hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(30, 117, 255, 0.05);
  border: 1px dashed rgba(30, 117, 255, 0.24);
}

.hint-label {
  color: var(--c-text-muted);
  font-size: 12px;
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

.inline-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

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

.icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-state-wrapper {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.schedule-row-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.schedule-detail {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(30, 117, 255, 0.04);
  border: 1px dashed rgba(30, 117, 255, 0.28);
}

.schedule-detail-loading {
  font-size: 12px;
  color: var(--c-text-muted);
}

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

@media (max-width: 980px) {
  .schedule-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .schedule-form,
  .schedule-detail-grid {
    grid-template-columns: 1fr;
  }

  .schedule-hint,
  .list-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
