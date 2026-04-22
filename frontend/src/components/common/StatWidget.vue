<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: String,
  value: [String, Number],
  note: String,
  trend: {
    type: Number,
    default: 0 // positive or negative percentage
  },
  glowColor: {
    type: String,
    default: 'primary'
  }
})

const formattedTrend = computed(() => {
  if (!props.trend) return null
  const isPositive = props.trend > 0
  return {
    text: `${isPositive ? '+' : ''}${props.trend}%`,
    class: isPositive ? 'trend-up' : 'trend-down'
  }
})
</script>

<template>
  <div class="stat-widget glass-panel" :class="`glow-${glowColor}`">
    <div class="widget-header">
      <span class="widget-label">{{ label }}</span>
      <div v-if="formattedTrend" class="widget-trend" :class="formattedTrend.class">
        {{ formattedTrend.text }}
      </div>
    </div>
    
    <div class="widget-body">
      <strong class="widget-value">{{ value }}</strong>
    </div>

    <div v-if="note" class="widget-footer">
      <span class="widget-note">{{ note }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat-widget {
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  overflow: hidden;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
  border-radius: 16px;
  background: var(--c-bg-surface);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--c-border-glass);
  box-shadow: var(--shadow-card-soft);
}

.stat-widget:hover {
  transform: translateY(-1px);
  border-color: var(--c-border-glass-hover);
  box-shadow: var(--shadow-card-raised);
}

.stat-widget::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 2px;
}
.stat-widget.glow-primary::before { background: var(--c-accent-primary); }
.stat-widget.glow-secondary::before { background: var(--c-accent-secondary); }
.stat-widget.glow-teal::before { background: var(--c-accent-teal); }
.stat-widget.glow-purple::before { background: var(--c-accent-purple); }

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.widget-label {
  font-size: 12px;
  color: var(--c-text-muted);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.widget-trend {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
}

.trend-up {
  color: #10B981;
  background: rgba(16, 185, 129, 0.1);
}

.trend-down {
  color: #EF4444;
  background: rgba(239, 68, 68, 0.1);
}

.widget-body {
  margin-top: 2px;
}

.widget-value {
  color: var(--c-text-primary);
  font-size: clamp(28px, 3.2vw, 38px);
  font-weight: 800;
  line-height: 1;
  font-family: var(--font-display);
  letter-spacing: -0.04em;
}

.widget-footer {
  margin-top: auto;
  padding-top: 2px;
}

.widget-note {
  font-size: 13px;
  color: var(--c-text-muted);
  line-height: 1.5;
}
</style>
