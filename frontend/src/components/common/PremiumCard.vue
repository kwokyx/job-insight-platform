<script setup>
defineProps({
  title: String,
  glowColor: {
    type: String,
    default: 'primary' // 'primary', 'secondary', 'teal', 'purple'
  },
  padding: {
    type: String,
    default: '24px'
  }
})
</script>

<template>
  <div class="premium-card glass-panel" :class="`glow-${glowColor}`">
    <div v-if="title || $slots.header" class="card-header">
      <slot name="header">
        <h2 v-if="title" class="card-title">{{ title }}</h2>
      </slot>
    </div>
    <div class="card-body" :style="{ padding: padding }">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.premium-card {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: transform var(--duration-normal) var(--ease-spring), box-shadow var(--duration-normal) var(--ease-out);
  border-radius: var(--radius-xl);
  background: linear-gradient(180deg, var(--c-bg-surface-strong), var(--c-bg-surface));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--c-border-glass);
  box-shadow: var(--shadow-panel);
}

.premium-card:hover {
  transform: translateY(-2px);
}

/* Base structural ring */
.premium-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(135deg, rgba(255,255,255,0.15) 0%, rgba(255,255,255,0.02) 100%);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}

/* Corner Glow Effect */
.premium-card::after {
  content: '';
  position: absolute;
  top: -24px;
  right: -24px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  filter: blur(30px);
  opacity: 0.12;
  transition: opacity var(--duration-normal) var(--ease-out);
  pointer-events: none;
}

.premium-card:hover::after { opacity: 0.2; }

/* Glow Variants */
.glow-primary::after { background: var(--c-accent-primary); }
.glow-secondary::after { background: var(--c-accent-secondary); }
.glow-teal::after { background: var(--c-accent-teal); }
.glow-purple::after { background: var(--c-accent-purple); }

.premium-card:hover.glow-primary { box-shadow: 0 10px 40px -10px var(--c-accent-primary-glow); }
.premium-card:hover.glow-secondary { box-shadow: 0 10px 40px -10px var(--c-accent-secondary-glow); }
.premium-card:hover.glow-teal { box-shadow: 0 10px 40px -10px rgba(45, 212, 191, 0.4); }
.premium-card:hover.glow-purple { box-shadow: 0 10px 40px -10px rgba(168, 85, 247, 0.4); }

.card-header {
  padding: 20px 24px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding-bottom: 16px;
  position: relative;
  z-index: 1;
}

.card-title {
  font-size: 17px;
  margin: 0;
  color: var(--c-text-primary);
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.card-body {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}
</style>
