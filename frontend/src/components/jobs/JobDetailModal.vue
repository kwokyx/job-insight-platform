<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import {
  Briefcase,
  Building2,
  Clock,
  ExternalLink,
  GraduationCap,
  Inbox,
  X
} from 'lucide-vue-next'
import SkeletonCard from '../common/SkeletonCard.vue'

const props = defineProps({
  job: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  errorMessage: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['close'])

const selectedJobBenefits = computed(() => parseArrayField(props.job?.jobBenefits))
const selectedJobLabels = computed(() => parseArrayField(props.job?.jobLabels))
const hasDetailContent = computed(() =>
  !!props.job?.description || selectedJobBenefits.value.length > 0 || selectedJobLabels.value.length > 0
)

function handleKeydown(event) {
  if (event.key === 'Escape' && props.job) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})

function renderDetailHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/\n/g, '<br/>')
}

function trimDecimal(value) {
  if (!Number.isFinite(value)) return ''
  return value.toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
}

function formatSalary(job) {
  const salaryText = typeof job?.salaryText === 'string' ? job.salaryText.trim() : ''
  if (salaryText) return salaryText

  const min = Number(job?.salaryMin)
  const max = Number(job?.salaryMax)
  if (Number.isFinite(min) && Number.isFinite(max)) {
    return `${trimDecimal(min)}-${trimDecimal(max)}K`
  }

  return job?.salaryText || '面议'
}

function parseArrayField(value) {
  if (!value) return []

  let source = value
  if (typeof value === 'string') {
    try {
      source = JSON.parse(value)
    } catch {
      source = value
    }
  }

  const normalized = (Array.isArray(source) ? source : [source])
    .flatMap((item) => `${item ?? ''}`.split(/[,\n，、]+/))
    .map((item) => item.trim())
    .filter(Boolean)

  return [...new Set(normalized)]
}
</script>

<template>
  <Teleport to="body">
    <transition name="modal-fade">
      <div
        v-if="job"
        class="modal-overlay"
        role="dialog"
        aria-modal="true"
        @click.self="emit('close')"
      >
        <div class="modal-wrapper">
          <div class="modal-content">
            <button class="modal-close" type="button" aria-label="关闭" @click="emit('close')">
              <X :size="18" :stroke-width="2" />
            </button>

            <div class="modal-header">
              <div class="header-main">
                <h2 class="modal-title">{{ job.title }}</h2>
                <div class="modal-meta-row">
                  <span class="company">{{ job.companyName || '企业信息待补充' }}</span>
                  <span class="dot">·</span>
                  <span class="location">{{ job.city || '全国' }}</span>
                </div>
              </div>
              <div class="salary-box">
                <span class="modal-salary">{{ formatSalary(job) }}</span>
              </div>
            </div>

            <div class="modal-tags">
              <div class="tag-group">
                <span class="detail-tag detail-tag--edu" v-if="job.education">
                  <GraduationCap :size="13" :stroke-width="1.8" />
                  {{ job.education }}
                </span>
                <span class="detail-tag detail-tag--exp" v-if="job.experience">
                  <Clock :size="13" :stroke-width="1.8" />
                  {{ job.experience }}
                </span>
                <span class="detail-tag detail-tag--industry" v-if="job.industryName">
                  <Building2 :size="13" :stroke-width="1.8" />
                  {{ job.industryName }}
                </span>
                <span class="detail-tag detail-tag--company" v-if="job.companySize">
                  <Briefcase :size="13" :stroke-width="1.8" />
                  {{ job.companySize }}
                </span>
                <span class="detail-tag detail-tag--finance" v-if="job.companyFinance">
                  <Briefcase :size="13" :stroke-width="1.8" />
                  {{ job.companyFinance }}
                </span>
              </div>
              <div class="time-stamp" v-if="job.publishDate">
                发布于 {{ job.publishDate }}
              </div>
            </div>

            <div class="modal-body">
              <div v-if="loading" class="detail-skeleton" aria-hidden="true">
                <SkeletonCard type="card" :lines="5" />
                <SkeletonCard type="card" :lines="4" />
              </div>
              <div v-else-if="errorMessage" class="modal-empty">
                <Inbox :size="28" :stroke-width="1.6" class="modal-empty-icon" />
                <p class="modal-empty-title">职位详情加载失败</p>
                <span class="modal-empty-sub">{{ errorMessage }}</span>
              </div>
              <template v-else>
                <div v-if="job.description" class="detail-section">
                  <div class="section-title">
                    <div class="title-indicator"></div>
                    <h3>岗位描述</h3>
                  </div>
                  <div class="detail-text" v-html="renderDetailHtml(job.description)"></div>
                </div>

                <div v-if="selectedJobBenefits.length" class="detail-section">
                  <div class="section-title">
                    <div class="title-indicator"></div>
                    <h3>福利亮点</h3>
                  </div>
                  <div class="detail-chip-list">
                    <span v-for="benefit in selectedJobBenefits" :key="benefit" class="detail-chip">
                      {{ benefit }}
                    </span>
                  </div>
                </div>

                <div v-if="selectedJobLabels.length" class="detail-section">
                  <div class="section-title">
                    <div class="title-indicator"></div>
                    <h3>职位标签</h3>
                  </div>
                  <div class="detail-chip-list">
                    <span v-for="label in selectedJobLabels" :key="label" class="detail-chip detail-chip--muted">
                      {{ label }}
                    </span>
                  </div>
                </div>

                <div v-if="!hasDetailContent" class="modal-empty">
                  <Inbox :size="28" :stroke-width="1.6" class="modal-empty-icon" />
                  <p class="modal-empty-title">暂无详细描述</p>
                  <span class="modal-empty-sub">
                    来源平台只保留了职位概要。可前往原始页面查看完整信息。
                  </span>
                  <a
                    v-if="job.sourceUrl"
                    :href="job.sourceUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="modal-empty-cta"
                  >
                    <ExternalLink :size="14" :stroke-width="1.8" />
                    查看原始页面
                  </a>
                </div>
              </template>
            </div>

            <div v-if="job.sourceUrl" class="modal-footer">
              <a
                :href="job.sourceUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="action-button primary"
              >
                <ExternalLink :size="14" :stroke-width="1.8" />
                查看原始页面
              </a>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(24, 27, 35, 0.42);
  backdrop-filter: blur(6px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-wrapper {
  width: 100%;
  max-width: 760px;
  animation: modalScaleUp 0.28s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes modalScaleUp {
  from { opacity: 0; transform: scale(0.96) translateY(12px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.modal-content {
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  border-radius: 16px;
  box-shadow: 0 20px 48px rgba(24, 27, 35, 0.14);
  overflow: hidden;
  position: relative;
  max-height: min(88vh, 720px);
  display: flex;
  flex-direction: column;
}

.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-muted);
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  cursor: pointer;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease;
  z-index: 5;
}

.modal-close:hover {
  background: rgba(0, 87, 194, 0.06);
  color: var(--c-accent-primary);
  border-color: rgba(0, 87, 194, 0.2);
}

.modal-header {
  padding: 28px 32px 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.header-main {
  flex: 1;
  min-width: 0;
  padding-right: 36px;
}

.modal-title {
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--c-text-primary);
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.modal-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-sans);
  font-size: 13.5px;
  color: var(--c-text-secondary);
}

.modal-meta-row .dot {
  color: var(--c-text-faint);
}

.salary-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  text-align: right;
}

.modal-salary {
  font-family: var(--font-serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
}

.modal-tags {
  padding: 14px 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: rgba(0, 87, 194, 0.02);
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}

.tag-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.detail-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
}

.detail-tag :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.8;
}

.detail-tag--edu {
  background: rgba(167, 139, 220, 0.08);
  border-color: rgba(167, 139, 220, 0.22);
}

.detail-tag--edu :deep(svg) {
  color: #8663c7;
  opacity: 1;
}

.detail-tag--exp {
  background: rgba(66, 166, 176, 0.08);
  border-color: rgba(66, 166, 176, 0.22);
}

.detail-tag--exp :deep(svg) {
  color: #3a8a92;
  opacity: 1;
}

.detail-tag--industry {
  background: rgba(203, 149, 72, 0.08);
  border-color: rgba(203, 149, 72, 0.22);
}

.detail-tag--industry :deep(svg) {
  color: #a87229;
  opacity: 1;
}

.detail-tag--company {
  background: var(--c-accent-primary-glow);
  border-color: rgba(0, 87, 194, 0.22);
}

.detail-tag--company :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 1;
}

.detail-tag--finance {
  background: rgba(34, 197, 94, 0.08);
  border-color: rgba(34, 197, 94, 0.22);
}

.detail-tag--finance :deep(svg) {
  color: #15803d;
  opacity: 1;
}

.time-stamp {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 32px 28px;
  scrollbar-width: thin;
  scrollbar-color: var(--c-border-glass-hover) transparent;
}

.modal-body::-webkit-scrollbar {
  width: 6px;
}

.modal-body::-webkit-scrollbar-track {
  background: transparent;
}

.modal-body::-webkit-scrollbar-thumb {
  background: var(--c-border-glass-hover);
  border-radius: 999px;
}

.modal-body::-webkit-scrollbar-thumb:hover {
  background: var(--c-accent-primary);
}

.detail-section {
  margin-top: 20px;
}

.detail-section:first-child {
  margin-top: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.title-indicator {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}

.section-title h3 {
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--c-text-primary);
  letter-spacing: -0.01em;
}

.detail-text {
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--c-text-primary);
  white-space: pre-line;
  word-break: break-word;
}

.detail-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 600;
}

.detail-chip--muted {
  background: rgba(148, 163, 184, 0.14);
  color: var(--c-text-secondary);
}

.modal-footer {
  padding: 16px 32px;
  background: rgba(0, 87, 194, 0.02);
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid rgba(24, 27, 35, 0.06);
}

.action-button {
  padding: 9px 16px;
  border-radius: 10px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease,
    color 150ms ease;
  text-decoration: none;
}

.action-button.primary {
  background: var(--c-accent-primary);
  color: #ffffff;
  border: 1px solid var(--c-accent-primary);
}

.action-button.primary:hover {
  background: #004ba8;
  border-color: #004ba8;
}

.detail-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 2px 16px;
}

.modal-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px 28px;
  text-align: center;
}

.modal-empty-icon {
  color: var(--c-text-muted);
  opacity: 0.6;
  margin-bottom: 2px;
}

.modal-empty-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-primary);
}

.modal-empty-sub {
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-muted);
  line-height: 1.55;
  max-width: 380px;
}

.modal-empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 14px;
  border-radius: 8px;
  border: 1px solid var(--c-accent-primary);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background-color 140ms ease, color 140ms ease;
}

.modal-empty-cta:hover {
  background: var(--c-accent-primary);
  color: #ffffff;
}

:global([data-theme="dark"]) .modal-empty-cta:hover {
  color: #0f1420;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.22s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .modal-overlay {
    padding: 12px;
    padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
  }

  .modal-content {
    max-height: 92dvh;
    border-radius: 14px;
  }

  .modal-close {
    top: 12px;
    right: 12px;
    width: 30px;
    height: 30px;
  }

  .modal-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 22px 18px 14px;
    gap: 10px;
  }

  .header-main {
    padding-right: 36px;
  }

  .modal-title {
    font-size: 19px;
    line-height: 1.3;
  }

  .salary-box {
    align-items: flex-start;
    text-align: left;
  }

  .modal-tags {
    padding: 12px 18px;
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .modal-body {
    padding: 14px 18px 20px;
    font-size: 13.5px;
  }

  .modal-footer {
    padding: 12px 18px calc(12px + env(safe-area-inset-bottom, 0px));
    flex-direction: column-reverse;
    gap: 8px;
  }

  .action-button {
    width: 100%;
    justify-content: center;
    min-height: 44px;
  }
}

@media (max-width: 420px) {
  .modal-header {
    padding: 18px 14px 12px;
  }

  .modal-tags {
    padding: 10px 14px;
  }

  .modal-body {
    padding: 12px 14px 18px;
  }

  .modal-footer {
    padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px));
  }
}
</style>
