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
  padding: 10px 18px;
  border-radius: var(--radius-sm);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  transition: all var(--duration-fast) var(--ease-out);
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
  transform: scale(0.96);
}

/* --- Primary Variant --- */
.var-primary {
  background: linear-gradient(135deg, var(--c-accent-primary), #0ea5e9);
  color: #fff;
  box-shadow: 0 10px 24px rgba(2, 132, 199, 0.2);
}

.var-primary.has-glow:not(:disabled):hover {
  background: var(--c-accent-primary-hover);
  box-shadow: 0 0 20px rgba(30, 117, 255, 0.5);
}

/* --- Secondary Variant --- */
.var-secondary {
  background: linear-gradient(135deg, var(--c-accent-secondary), #fb923c);
  color: #fff;
}

.var-secondary.has-glow:not(:disabled):hover {
  background: var(--c-accent-secondary-hover);
  box-shadow: 0 0 20px rgba(255, 95, 21, 0.5);
}

/* --- Ghost Variant --- */
.var-ghost {
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
  border: 1px solid var(--c-border-glass);
}

.var-ghost:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: var(--c-border-glass-hover);
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
