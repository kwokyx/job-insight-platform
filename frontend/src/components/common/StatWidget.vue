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
      <strong class="widget-value text-gradient" :class="`text-gradient-${glowColor}`">{{ value }}</strong>
    </div>

    <div v-if="note" class="widget-footer">
      <span class="widget-note">{{ note }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat-widget {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  overflow: hidden;
  transition: transform var(--duration-normal) var(--ease-spring);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--c-border-glass);
  box-shadow: var(--shadow-card-quiet);
}

.stat-widget:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-card-soft);
}

.stat-widget::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 4px;
}
.stat-widget.glow-primary::before { background: linear-gradient(90deg, transparent, var(--c-accent-primary), transparent); }
.stat-widget.glow-secondary::before { background: linear-gradient(90deg, transparent, var(--c-accent-secondary), transparent); }
.stat-widget.glow-teal::before { background: linear-gradient(90deg, transparent, var(--c-accent-teal), transparent); }
.stat-widget.glow-purple::before { background: linear-gradient(90deg, transparent, var(--c-accent-purple), transparent); }

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.widget-label {
  font-size: 13px;
  color: var(--c-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
}

.widget-trend {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(242, 243, 255, 0.95);
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
  margin-top: 4px;
}

.widget-value {
  font-size: clamp(28px, 3.4vw, 36px);
  font-weight: 700;
  line-height: 1;
  font-family: var(--font-display);
}

.widget-footer {
  margin-top: auto;
  padding-top: 8px;
}

.widget-note {
  font-size: 13px;
  color: var(--c-text-muted);
}
</style>
