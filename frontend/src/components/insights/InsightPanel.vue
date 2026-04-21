<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  note: {
    type: String,
    default: ''
  },
  tone: {
    type: String,
    default: 'primary'
  }
})
</script>

<template>
  <section class="insight-panel" :class="`tone-${tone}`">
    <header v-if="$slots.header || title || note" class="insight-panel-head">
      <div class="insight-panel-head-text">
        <p v-if="note" class="insight-panel-note">{{ note }}</p>
        <h3 v-if="title" class="insight-panel-title">{{ title }}</h3>
      </div>
      <slot name="header" />
    </header>

    <div class="insight-panel-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.insight-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background:
    linear-gradient(180deg, var(--c-glass-panel-highlight), transparent 24%),
    var(--c-glass-panel-bg);
  box-shadow: var(--shadow-card-soft);
  overflow: hidden;
}

.insight-panel::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 2px;
  background: var(--panel-accent, rgba(0, 89, 199, 0.75));
  opacity: 0.9;
}

.insight-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.insight-panel-head-text {
  min-width: 0;
}

.insight-panel-note {
  margin: 0 0 6px;
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.insight-panel-title {
  margin: 0;
  color: var(--c-text-primary);
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.insight-panel-body {
  min-width: 0;
}

.insight-panel.tone-primary {
  --panel-accent: rgba(130, 176, 210, 0.86);
}

.insight-panel.tone-secondary {
  --panel-accent: rgba(250, 127, 111, 0.82);
}

.insight-panel.tone-purple {
  --panel-accent: rgba(190, 184, 220, 0.86);
}

.insight-panel.tone-teal {
  --panel-accent: rgba(142, 207, 201, 0.86);
}

.insight-panel.tone-amber {
  --panel-accent: rgba(255, 190, 122, 0.86);
}

@media (max-width: 768px) {
  .insight-panel {
    padding: 18px 18px 20px;
  }

  .insight-panel-head {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
