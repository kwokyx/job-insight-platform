<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary' // 'primary', 'secondary', 'ghost'
  },
  glow: {
    type: Boolean,
    default: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <button 
    class="glow-button" 
    :class="[`var-${variant}`, { 'has-glow': glow, 'is-loading': loading }]"
    :disabled="loading"
  >
    <div class="btn-content" :class="{ 'opacity-0': loading }">
      <slot></slot>
    </div>
    
    <!-- Loading spinner overlay -->
    <div v-if="loading" class="spinner-container">
      <svg class="spinner" viewBox="0 0 50 50">
        <circle class="path" cx="25" cy="25" r="20" fill="none" stroke-width="5"></circle>
      </svg>
    </div>
  </button>
</template>

<style scoped>
.glow-button {
  position: relative;
  padding: 11px 18px;
  border-radius: 12px;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
  border: 1px solid transparent;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.glow-button:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.btn-content {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  transition: opacity 0.2s;
}

.opacity-0 {
  opacity: 0;
}

/* Common active push effect */
.glow-button:not(:disabled):active {
  transform: translateY(0);
}

/* --- Primary Variant --- */
.var-primary {
  background: var(--c-accent-primary);
  border-color: var(--c-border-glass-hover);
  color: #fff;
  box-shadow: 0 10px 22px var(--c-accent-primary-glow);
}

.var-primary.has-glow:not(:disabled):hover {
  background: var(--c-accent-primary-hover);
  border-color: var(--c-border-glass-hover);
  box-shadow: 0 14px 28px var(--c-accent-primary-glow);
  transform: translateY(-1px);
}

/* --- Secondary Variant --- */
.var-secondary {
  background: var(--c-bg-surface-strong);
  border-color: var(--c-border-glass);
  color: var(--c-text-secondary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.var-secondary.has-glow:not(:disabled):hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    0 10px 20px var(--c-accent-primary-glow);
  transform: translateY(-1px);
}

/* --- Ghost Variant --- */
.var-ghost {
  background: var(--c-bg-surface);
  color: var(--c-text-secondary);
  border-color: var(--c-border-glass);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.var-ghost:not(:disabled):hover {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    0 10px 20px var(--c-accent-primary-glow);
  transform: translateY(-1px);
}

/* Dark mode overrides — --c-accent-primary flips to a pale lavender
   (#afc6ff) in dark mode, so the white label on the primary button
   washes out. Swap to the dark base text for readable contrast, and
   drop the inner light-sheen box-shadow that only makes sense on a
   white card. */
[data-theme="dark"] .var-primary {
  color: #0f1420;
}
[data-theme="dark"] .var-primary.has-glow:not(:disabled):hover {
  color: #0f1420;
}
[data-theme="dark"] .var-secondary,
[data-theme="dark"] .var-ghost {
  box-shadow: none;
}
[data-theme="dark"] .var-secondary.has-glow:not(:disabled):hover,
[data-theme="dark"] .var-ghost:not(:disabled):hover {
  box-shadow: 0 10px 20px var(--c-accent-primary-glow);
}

/* Spinner */
.spinner-container {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 20px;
  height: 20px;
  animation: rotate 2s linear infinite;
}

.spinner .path {
  stroke: currentColor;
  stroke-linecap: round;
  animation: dash 1.5s ease-in-out infinite;
}

@keyframes rotate {
  100% { transform: rotate(360deg); }
}

@keyframes dash {
  0% { stroke-dasharray: 1, 150; stroke-dashoffset: 0; }
  50% { stroke-dasharray: 90, 150; stroke-dashoffset: -35; }
  100% { stroke-dasharray: 90, 150; stroke-dashoffset: -124; }
}
</style>
