<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  glowColor: {
    type: String,
    default: 'teal'
  },
  items: {
    type: Array,
    default: () => []
  },
  emptyText: {
    type: String,
    default: '暂无数据'
  }
})

const rows = computed(() => (Array.isArray(props.items) ? props.items.slice(0, 10) : []))
const maxValue = computed(() => {
  const firstRow = rows.value[0]
  const firstValue = Number(firstRow?.count)
  return Number.isFinite(firstValue) && firstValue > 0 ? firstValue : 1
})

function formatCount(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value.toLocaleString('zh-CN')
  }

  return value ?? '0'
}
</script>

<template>
  <section class="ranking-panel">
    <header class="ranking-panel-head">
      <h3>{{ title }}</h3>
      <span class="ranking-panel-kicker">TOP 10</span>
    </header>

    <div v-if="rows.length" class="bar-list">
      <div v-for="(item, index) in rows" :key="`${item.label}-${index}`" class="bar-item">
        <span class="bar-rank" :class="{ top3: index < 3 }">{{ index + 1 }}</span>
        <span class="bar-name">{{ item.label }}</span>
        <div class="bar-track" aria-hidden="true">
          <div
            class="bar-fill"
            :class="glowColor"
            :style="{ width: `${(Number(item.count) / maxValue) * 100}%` }"
          ></div>
        </div>
        <strong class="bar-value">{{ formatCount(item.count) }}</strong>
      </div>
    </div>

    <p v-else class="ranking-empty">{{ emptyText }}</p>
  </section>
</template>

<style scoped>
.ranking-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 24px;
  border-radius: 20px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 255, 0.88)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
}

.ranking-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.ranking-panel-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--c-text-primary);
}

.ranking-panel-kicker {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(217, 226, 255, 0.9);
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.bar-rank {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  flex-shrink: 0;
}

.bar-rank.top3 {
  background: rgba(217, 226, 255, 0.95);
  color: var(--c-accent-primary);
}

.bar-name {
  min-width: 0;
  color: var(--c-text-secondary);
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-track {
  height: 8px;
  background: #ecedf9;
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.8s var(--ease-out);
}

.bar-fill.teal {
  background: linear-gradient(90deg, rgba(66, 93, 151, 0.32), rgba(0, 89, 199, 0.96));
}

.bar-fill.purple {
  background: linear-gradient(90deg, rgba(66, 93, 151, 0.2), rgba(66, 93, 151, 0.88));
}

.bar-fill.secondary {
  background: linear-gradient(90deg, rgba(0, 89, 199, 0.22), rgba(0, 89, 199, 0.72));
}

.bar-value {
  width: 56px;
  text-align: right;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--c-text-primary);
}

.ranking-empty {
  margin: 0;
  padding: 12px 0 2px;
  color: var(--c-text-muted);
  font-size: 14px;
}

@media (max-width: 768px) {
  .ranking-panel {
    padding: 18px 18px 20px;
  }

  .bar-item {
    grid-template-columns: 24px minmax(0, 1fr);
    gap: 8px 10px;
  }

  .bar-track {
    grid-column: 2 / -1;
  }

  .bar-value {
    width: auto;
    justify-self: end;
  }
}
</style>
