<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { AlertTriangle, X } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  description: { type: String, default: '' },
  detail: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  variant: { type: String, default: 'danger' }, // danger | primary
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['confirm', 'cancel', 'update:open'])

const iconTone = computed(() => (props.variant === 'danger' ? 'tone-danger' : 'tone-primary'))

function close() {
  if (props.loading) return
  emit('update:open', false)
  emit('cancel')
}
function confirm() {
  emit('confirm')
}

function onKey(e) {
  if (!props.open) return
  if (e.key === 'Escape') close()
  else if (e.key === 'Enter') confirm()
}

onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))

// 打开时锁定页面滚动
watch(() => props.open, (v) => {
  if (typeof document === 'undefined') return
  document.body.style.overflow = v ? 'hidden' : ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div v-if="open" class="confirm-root" role="dialog" aria-modal="true" :aria-label="title">
        <div class="confirm-backdrop" @click="close"></div>
        <div class="confirm-dialog" :class="`is-${variant}`">
          <button type="button" class="confirm-close" :disabled="loading" @click="close" aria-label="关闭">
            <X :size="16" />
          </button>

          <div class="confirm-head">
            <span class="confirm-icon" :class="iconTone">
              <AlertTriangle :size="20" />
            </span>
            <div class="confirm-copy">
              <h3>{{ title }}</h3>
              <p v-if="description">{{ description }}</p>
            </div>
          </div>

          <div v-if="detail" class="confirm-detail">
            {{ detail }}
          </div>

          <div class="confirm-actions">
            <button type="button" class="confirm-btn ghost" :disabled="loading" @click="close">
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="confirm-btn"
              :class="variant === 'danger' ? 'danger' : 'primary'"
              :disabled="loading"
              @click="confirm"
            >
              <span v-if="loading" class="confirm-spinner"></span>
              <span>{{ loading ? '处理中…' : confirmText }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-root {
  position: fixed; inset: 0; z-index: 1200;
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.confirm-backdrop {
  position: absolute; inset: 0;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(6px);
}
.confirm-dialog {
  position: relative;
  width: min(100%, 420px);
  padding: 24px 24px 20px;
  border-radius: 18px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow:
    0 20px 48px rgba(15, 23, 42, 0.18),
    0 2px 6px rgba(15, 23, 42, 0.08);
}
[data-theme="dark"] .confirm-dialog {
  box-shadow:
    0 20px 48px rgba(0, 0, 0, 0.55),
    0 2px 6px rgba(0, 0, 0, 0.35);
}

.confirm-close {
  position: absolute; top: 12px; right: 12px;
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  border: none; border-radius: 8px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.confirm-close:hover:not(:disabled) {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.confirm-close:disabled { opacity: 0.4; cursor: not-allowed; }

.confirm-head {
  display: flex; align-items: flex-start; gap: 14px;
  padding-right: 24px;
}
.confirm-icon {
  flex-shrink: 0;
  display: inline-flex; align-items: center; justify-content: center;
  width: 40px; height: 40px; border-radius: 12px;
}
.confirm-icon.tone-danger {
  background: rgba(178, 59, 46, 0.12);
  color: #b23b2e;
}
.confirm-icon.tone-primary {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.confirm-copy { min-width: 0; flex: 1; padding-top: 2px; }
.confirm-copy h3 {
  margin: 0 0 6px;
  font-family: var(--font-serif);
  font-size: 16px; font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
}
.confirm-copy p {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 13.5px; line-height: 1.55;
  color: var(--c-text-secondary);
}

.confirm-detail {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 12.5px; line-height: 1.5;
  word-break: break-all;
}

.confirm-actions {
  display: flex; justify-content: flex-end; gap: 10px;
  margin-top: 20px;
}
.confirm-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-family: var(--font-sans);
  font-size: 13px; font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.1s ease;
}
.confirm-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.confirm-btn:not(:disabled):active { transform: translateY(1px); }

.confirm-btn.ghost {
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  border-color: var(--c-border-glass);
}
.confirm-btn.ghost:hover:not(:disabled) {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.confirm-btn.danger {
  background: #b23b2e;
  color: #fff;
}
.confirm-btn.danger:hover:not(:disabled) { background: #9e3328; }
.confirm-btn.primary {
  background: var(--c-accent-primary);
  color: #fff;
}
.confirm-btn.primary:hover:not(:disabled) { filter: brightness(0.95); }

.confirm-spinner {
  width: 12px; height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  border-radius: 50%;
  animation: confirm-spin 0.7s linear infinite;
}
@keyframes confirm-spin { to { transform: rotate(360deg); } }

.confirm-fade-enter-active,
.confirm-fade-leave-active { transition: opacity 0.18s ease; }
.confirm-fade-enter-active .confirm-dialog,
.confirm-fade-leave-active .confirm-dialog {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.confirm-fade-enter-from,
.confirm-fade-leave-to { opacity: 0; }
.confirm-fade-enter-from .confirm-dialog,
.confirm-fade-leave-to .confirm-dialog {
  transform: translateY(6px) scale(0.98);
  opacity: 0;
}
</style>
