<script setup>
defineProps({
  title: String,
  glowColor: {
    type: String,
    default: 'primary' // 'primary', 'secondary', 'teal', 'purple'
  },
  padding: {
    type: String,
    default: '28px'
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
  transition:
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
  border-radius: 16px;
  background:
    linear-gradient(180deg, var(--c-glass-panel-highlight), transparent 22%),
    var(--c-glass-panel-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--c-glass-panel-border);
  box-shadow: var(--shadow-card-soft);
}

.premium-card:hover {
  transform: translateY(-1px);
  border-color: var(--c-border-glass-hover);
  box-shadow: var(--shadow-card-raised);
}

.premium-card::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 2px;
  background: var(--panel-accent, rgba(0, 89, 199, 0.75));
  opacity: 0.9;
}

.glow-primary { --panel-accent: rgba(130, 176, 210, 0.86); }
.glow-secondary { --panel-accent: rgba(250, 127, 111, 0.82); }
.glow-teal { --panel-accent: rgba(142, 207, 201, 0.86); }
.glow-purple { --panel-accent: rgba(190, 184, 220, 0.86); }

.card-header {
  padding: 18px 18px 0;
  border-bottom: 1px solid var(--c-border-glass);
  padding-bottom: 14px;
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
