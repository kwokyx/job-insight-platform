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
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--c-border-glass);
  box-shadow: var(--shadow-panel);
}

.premium-card:hover {
  transform: translateY(-1px);
}

/* Base structural ring */
.premium-card::before { display: none; }

/* Corner Glow Effect */
.premium-card::after {
  content: '';
  position: absolute;
  top: -36px;
  right: -36px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  filter: blur(36px);
  opacity: 0.08;
  transition: opacity var(--duration-normal) var(--ease-out);
  pointer-events: none;
}

.premium-card:hover::after { opacity: 0.14; }

/* Glow Variants */
.glow-primary::after { background: var(--c-accent-primary); }
.glow-secondary::after { background: var(--c-accent-secondary); }
.glow-teal::after { background: var(--c-accent-teal); }
.glow-purple::after { background: var(--c-accent-purple); }

.premium-card:hover.glow-primary { box-shadow: 0 16px 44px rgba(24, 27, 35, 0.08); }
.premium-card:hover.glow-secondary { box-shadow: 0 16px 44px rgba(24, 27, 35, 0.08); }
.premium-card:hover.glow-teal { box-shadow: 0 16px 44px rgba(24, 27, 35, 0.08); }
.premium-card:hover.glow-purple { box-shadow: 0 16px 44px rgba(24, 27, 35, 0.08); }

.card-header {
  padding: 20px 24px 0;
  border-bottom: 1px solid rgba(193, 198, 215, 0.5);
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
  letter-spacing: -0.02em;
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
