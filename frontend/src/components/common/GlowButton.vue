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
  },
  disabled: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <button 
    class="glow-button" 
    :class="[`var-${variant}`, { 'has-glow': glow, 'is-loading': loading }]"
    :disabled="loading || disabled"
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
  background: rgba(0, 89, 199, 0.94);
  border-color: rgba(0, 89, 199, 0.3);
  color: #fff;
  box-shadow: 0 10px 22px rgba(0, 89, 199, 0.12);
}

.var-primary.has-glow:not(:disabled):hover {
  background: rgba(0, 89, 199, 1);
  border-color: rgba(0, 89, 199, 0.38);
  box-shadow: 0 14px 28px rgba(0, 89, 199, 0.16);
  transform: translateY(-1px);
}

/* --- Secondary Variant --- */
.var-secondary {
  background: rgba(255, 255, 255, 0.82);
  border-color: rgba(193, 198, 215, 0.56);
  color: var(--c-text-secondary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.var-secondary.has-glow:not(:disabled):hover {
  background: rgba(30, 117, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.22);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    0 10px 20px rgba(30, 117, 255, 0.06);
  transform: translateY(-1px);
}

/* --- Ghost Variant --- */
.var-ghost {
  background: rgba(255, 255, 255, 0.74);
  color: var(--c-text-secondary);
  border-color: rgba(193, 198, 215, 0.56);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.42);
}

.var-ghost:not(:disabled):hover {
  background: rgba(30, 117, 255, 0.06);
  border-color: rgba(30, 117, 255, 0.22);
  color: var(--c-accent-primary);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.5),
    0 10px 20px rgba(30, 117, 255, 0.06);
  transform: translateY(-1px);
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
