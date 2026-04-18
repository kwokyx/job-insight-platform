<script setup>
import { useToast } from '../../composables/useToast'
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-vue-next'

const { toasts, remove } = useToast()

function getIcon(type) {
  switch (type) {
    case 'success': return CheckCircle2
    case 'error': return AlertCircle
    default: return Info
  }
}
</script>

<template>
  <div class="toast-container">
    <transition-group name="toast-list">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast glass-panel"
        :class="[`toast-${toast.type}`]"
      >
        <component :is="getIcon(toast.type)" class="toast-icon" :size="18" />
        <span class="toast-message">{{ toast.message }}</span>
        <button class="toast-close" @click="remove(toast.id)">
          <X :size="14" />
        </button>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  min-width: 300px;
  max-width: 400px;
  pointer-events: auto;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  border-left: 4px solid var(--c-accent-primary);
}

.toast-success {
  border-left-color: var(--c-accent-teal);
}
.toast-success .toast-icon { color: var(--c-accent-teal); }

.toast-error {
  border-left-color: var(--c-accent-rose);
}
.toast-error .toast-icon { color: var(--c-accent-rose); }

.toast-message {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--c-text-primary);
}

.toast-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  color: var(--c-text-muted);
  transition: all var(--duration-fast);
}

.toast-close:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

/* Animations */
.toast-list-enter-active,
.toast-list-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.toast-list-enter-from {
  opacity: 0;
  transform: translateX(40px) scale(0.95);
}
.toast-list-leave-to {
  opacity: 0;
  transform: translateX(40px) scale(0.95);
}
</style>
