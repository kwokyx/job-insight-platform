<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  title: {
    type: String,
    default: '功能目录'
  },
  items: {
    type: Array,
    default: () => []
  }
})

const activeId = ref('')

function normalizeId(value) {
  if (!value) return ''
  return String(value).replace(/^#/, '')
}

function resolveHref(item) {
  const id = normalizeId(item?.id)
  return id ? `#${id}` : '#'
}

function syncActiveByHash() {
  const hashId = normalizeId(window.location.hash)
  activeId.value = hashId || normalizeId(props.items[0]?.id)
}

function handleSelect(item) {
  const id = normalizeId(item?.id)
  if (!id) return
  activeId.value = id
  const target = document.getElementById(id)
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  window.history.replaceState(null, '', `#${id}`)
}

onMounted(() => {
  syncActiveByHash()
  window.addEventListener('hashchange', syncActiveByHash)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncActiveByHash)
})
</script>

<template>
  <nav class="page-directory" aria-label="页内功能目录">
    <div class="page-directory-head">
      <span class="page-directory-label">{{ title }}</span>
    </div>

    <div class="page-directory-tabs" role="tablist">
      <button
        v-for="item in props.items"
        :key="item.id || item.label"
        class="page-directory-tab"
        :class="{ active: activeId === normalizeId(item.id) }"
        role="tab"
        :aria-selected="activeId === normalizeId(item.id)"
        @click="handleSelect(item)"
      >
        <span>{{ item.label }}</span>
      </button>
    </div>

    <p v-if="props.items.length" class="page-directory-copy">
      {{ props.items.find((item) => normalizeId(item.id) === activeId)?.hint || props.items[0]?.hint || '' }}
    </p>
  </nav>
</template>

<style scoped>
.page-directory {
  display: grid;
  gap: 10px;
}

.page-directory-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-directory-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  line-height: 1.2;
}

.page-directory-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-surface-hover);
}

.page-directory-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.page-directory-tab:hover {
  background: rgba(0, 87, 194, 0.06);
  color: var(--c-accent-primary);
}

.page-directory-tab.active {
  background: #ffffff;
  border-color: rgba(0, 87, 194, 0.3);
  color: var(--c-accent-primary);
  box-shadow: 0 4px 12px rgba(0, 87, 194, 0.08);
}

.page-directory-tab:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

.page-directory-copy {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-muted);
}

@media (max-width: 768px) {
  .page-directory-tabs {
    gap: 6px;
  }

  .page-directory-tab {
    flex: 1 1 calc(50% - 6px);
    justify-content: center;
  }
}
</style>
