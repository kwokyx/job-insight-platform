<script setup>
const props = defineProps({
  title: { type: String, default: '目录' },
  items: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

const emit = defineEmits(['select'])

function handleSelect(id) {
  if (!id) return
  emit('select', id)
}
</script>

<template>
  <section class="section-directory glass-panel">
    <div class="directory-head">
      <strong>{{ props.title }}</strong>
    </div>
    <div class="directory-list">
      <button
        v-for="item in props.items"
        :key="item.id"
        type="button"
        class="directory-item"
        :class="{ active: item.id === props.activeId }"
        @click="handleSelect(item.id)"
      >
        <span class="directory-label">{{ item.label || item.id }}</span>
        <small v-if="item.hint">{{ item.hint }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.section-directory {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.directory-head strong {
  font-size: 14px;
  color: var(--c-text-primary);
}

.directory-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
}

.directory-item {
  display: grid;
  gap: 4px;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  cursor: pointer;
}

.directory-item.active {
  border-color: rgba(30, 117, 255, 0.45);
  background: rgba(30, 117, 255, 0.1);
}

.directory-label {
  font-size: 13px;
  color: var(--c-text-primary);
}

.directory-item small {
  font-size: 11px;
  color: var(--c-text-muted);
}
</style>
