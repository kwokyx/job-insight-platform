<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'

const props = defineProps({
  modelValue: { type: [String, Number, Boolean], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  width: { type: [String, Number], default: '' },
  panelMinWidth: { type: [String, Number], default: '' },
  size: { type: String, default: 'md' },
  join: { type: String, default: 'none' },
  align: { type: String, default: 'left' },
  ariaLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const rootRef = ref(null)

const iconSize = computed(() => (props.size === 'sm' ? 14 : 15))
const caretSize = computed(() => (props.size === 'sm' ? 13 : 14))
const componentStyle = computed(() => {
  const style = {}
  if (props.width !== '') style['--floating-select-width'] = toCssSize(props.width)
  if (props.panelMinWidth !== '') style['--floating-select-panel-min-width'] = toCssSize(props.panelMinWidth)
  return style
})
const selectedOption = computed(
  () => props.options.find((option) => normalizeValue(option?.value) === normalizeValue(props.modelValue)) || null
)
const displayLabel = computed(() => selectedOption.value?.label || props.placeholder)

function toCssSize(value) {
  return typeof value === 'number' ? `${value}px` : value
}

function normalizeValue(value) {
  if (value === '' || value === null || value === undefined) return ''
  return String(value)
}

function isSelected(value) {
  return normalizeValue(value) === normalizeValue(props.modelValue)
}

function toggleOpen() {
  if (props.disabled) return
  open.value = !open.value
}

function closeOpen() {
  open.value = false
}

function selectOption(option) {
  emit('update:modelValue', option.value)
  emit('change', option.value)
  closeOpen()
}

function handleDocumentClick(event) {
  if (!open.value) return
  const el = rootRef.value
  if (el && !el.contains(event.target)) closeOpen()
}

function handleDocumentKeydown(event) {
  if (event.key === 'Escape') closeOpen()
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <div
    ref="rootRef"
    class="floating-select"
    :class="[
      `size-${size}`,
      `join-${join}`,
      `align-${align}`,
      { open, disabled, 'has-icon': Boolean(selectedOption?.icon) }
    ]"
    :style="componentStyle"
  >
    <button
      type="button"
      class="floating-select-trigger"
      :disabled="disabled"
      :aria-label="ariaLabel || displayLabel"
      aria-haspopup="listbox"
      :aria-expanded="open"
      @click.stop="toggleOpen"
    >
      <component
        :is="selectedOption.icon"
        v-if="selectedOption?.icon"
        :size="iconSize"
        class="floating-select-icon"
      />
      <span class="floating-select-label">{{ displayLabel }}</span>
      <ChevronDown :size="caretSize" class="floating-select-caret" />
    </button>

    <div class="floating-select-panel" role="listbox">
      <button
        v-for="option in options"
        :key="String(option.value)"
        type="button"
        role="option"
        class="floating-select-item"
        :class="{ active: isSelected(option.value) }"
        :aria-selected="isSelected(option.value)"
        @click="selectOption(option)"
      >
        <component
          :is="option.icon"
          v-if="option.icon"
          :size="iconSize"
          class="floating-select-item-icon"
        />
        <span class="floating-select-item-label">{{ option.label }}</span>
        <Check
          v-if="isSelected(option.value)"
          :size="14"
          class="floating-select-item-check"
        />
      </button>
    </div>
  </div>
</template>

<style scoped>
.floating-select {
  position: relative;
  display: inline-flex;
  width: var(--floating-select-width, 100%);
  min-width: 0;
}

.floating-select-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  height: var(--floating-select-height, 38px);
  padding: 0 12px;
  border-radius: var(--floating-select-radius, 10px);
  border: 1px solid var(--c-border-glass);
  background: var(--floating-select-bg, var(--c-bg-base-elevated));
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: var(--floating-select-font-size, 13px);
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background-color 140ms ease,
    box-shadow 140ms ease,
    color 140ms ease;
}

.floating-select-trigger:hover:not(:disabled) {
  border-color: rgba(30, 117, 255, 0.32);
  background: rgba(30, 117, 255, 0.06);
  color: var(--c-accent-primary);
}

.floating-select.open .floating-select-trigger {
  border-color: rgba(30, 117, 255, 0.38);
  background: rgba(30, 117, 255, 0.08);
  color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(30, 117, 255, 0.12);
}

.floating-select-trigger:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.floating-select-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floating-select.align-left .floating-select-label {
  text-align: left;
}

.floating-select.align-center .floating-select-label {
  text-align: center;
}

.floating-select-icon {
  flex-shrink: 0;
  color: var(--c-text-muted);
}

.floating-select.open .floating-select-icon,
.floating-select:hover .floating-select-icon {
  color: var(--c-accent-primary);
}

.floating-select-caret {
  flex-shrink: 0;
  color: var(--c-text-muted);
  transition: transform 180ms ease, color 140ms ease;
}

.floating-select.open .floating-select-caret {
  transform: rotate(180deg);
  color: var(--c-accent-primary);
}

.floating-select-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: max(100%, var(--floating-select-panel-min-width, 100%));
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: #ffffff;
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.14),
    0 2px 6px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-direction: column;
  gap: 1px;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(-4px);
  transition:
    opacity 140ms ease,
    transform 140ms ease,
    visibility 0s linear 140ms;
  z-index: 50;
}

.floating-select.open .floating-select-panel {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
  transition:
    opacity 140ms ease,
    transform 140ms ease,
    visibility 0s linear 0s;
}

:global([data-theme='dark']) .floating-select-panel {
  background: #1a1f2d;
  border-color: var(--c-border-glass);
  box-shadow:
    0 16px 36px rgba(0, 0, 0, 0.45),
    0 2px 6px rgba(0, 0, 0, 0.35);
}

.floating-select-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}

.floating-select-item:hover {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}

.floating-select-item.active {
  background: var(--c-bg-surface-strong);
  color: var(--c-accent-primary);
  box-shadow: var(--shadow-card-quiet);
}

:global([data-theme='dark']) .floating-select-item.active {
  background: rgba(30, 117, 255, 0.18);
}

.floating-select-item-icon {
  flex-shrink: 0;
  color: var(--c-text-muted);
}

.floating-select-item:hover .floating-select-item-icon,
.floating-select-item.active .floating-select-item-icon {
  color: var(--c-accent-primary);
}

.floating-select-item-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.floating-select-item.active .floating-select-item-label {
  font-weight: 700;
}

.floating-select-item-check {
  flex-shrink: 0;
  color: var(--c-accent-primary);
}

.floating-select.size-sm .floating-select-trigger {
  height: 34px;
  padding: 0 10px;
  font-size: 12.5px;
}

.floating-select.join-left {
  width: var(--floating-select-width, 128px);
}

.floating-select.join-left .floating-select-trigger {
  height: 36px;
  border: none;
  border-radius: 10px 0 0 10px;
  background: transparent;
  font-size: 12.5px;
  box-shadow: none;
}

.floating-select.join-left.open .floating-select-trigger,
.floating-select.join-left .floating-select-trigger:hover:not(:disabled) {
  background: rgba(30, 117, 255, 0.08);
}
</style>
