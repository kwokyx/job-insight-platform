<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { navGroups, tagClass } from './data.js'

const props = defineProps({
  searchQuery: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:searchQuery', 'navigate'])

const route = useRoute()

// Parse an item's `to` field (e.g. "/openapi/intro#foo") into path/hash parts
// so we can compare against the current route without doing string equality
// on a full path that may differ by trailing hash/slash.
function parseTo(to) {
  if (!to) return { path: '', hash: '' }
  const idx = to.indexOf('#')
  if (idx === -1) return { path: to, hash: '' }
  return { path: to.slice(0, idx), hash: to.slice(idx) }
}

function isActive(item) {
  if (!item.to) return false
  const { path, hash } = parseTo(item.to)
  if (route.path !== path) return false
  // If the nav item targets a specific hash, require it to match exactly.
  // If it has no hash, match when the current route has no hash (tolerate empty).
  if (hash) return route.hash === hash
  return !route.hash
}

// Search filters each group's items by label (case-insensitive). When every
// item in a group is filtered out, the group is hidden entirely.
const visibleNavGroups = computed(() => {
  const q = props.searchQuery.trim().toLowerCase()
  return navGroups
    .map((group) => ({
      title: group.title,
      items: q
        ? group.items.filter((item) => item.label.toLowerCase().includes(q))
        : group.items
    }))
    .filter((group) => group.items.length > 0)
})

function onInput(event) {
  emit('update:searchQuery', event.target.value)
}

function onLinkClick() {
  emit('navigate')
}
</script>

<template>
  <div class="docs-sidebar-inner">
    <div class="docs-search">
      <Search class="docs-search-icon" :size="14" :stroke-width="1.8" aria-hidden="true" />
      <input
        :value="searchQuery"
        class="docs-search-input"
        type="search"
        placeholder="在全文中搜索"
        aria-label="在文档全文中搜索"
        @input="onInput"
      />
    </div>

    <nav class="docs-nav" aria-label="文档章节导航">
      <div
        v-for="group in visibleNavGroups"
        :key="group.title"
        class="docs-nav-group"
      >
        <div class="docs-nav-group-label">{{ group.title }}</div>
        <ul class="docs-nav-list">
          <li v-for="item in group.items" :key="item.id">
            <router-link
              v-if="item.to && !item.soon"
              :to="item.to"
              class="docs-nav-link"
              :class="{ 'is-active': isActive(item) }"
              @click="onLinkClick"
            >
              <span class="docs-nav-link-label">{{ item.label }}</span>
              <span
                v-if="item.tag"
                class="nav-tag"
                :class="tagClass(item.tag)"
              >{{ item.tag }}</span>
            </router-link>
            <span
              v-else
              class="docs-nav-link is-soon"
              title="内容建设中"
              aria-disabled="true"
            >
              <span class="docs-nav-link-label">{{ item.label }}</span>
              <span
                v-if="item.tag"
                class="nav-tag"
                :class="tagClass(item.tag)"
              >{{ item.tag }}</span>
            </span>
          </li>
        </ul>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.docs-sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 16px 32px;
}

.docs-search {
  position: relative;
  display: flex;
  align-items: center;
}
.docs-search-icon {
  position: absolute;
  left: 10px;
  color: var(--c-text-muted);
  pointer-events: none;
}
.docs-search-input {
  width: 100%;
  padding: 8px 12px 8px 32px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.4;
  outline: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}
.docs-search-input::placeholder {
  color: var(--c-text-muted);
}
.docs-search-input:focus {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px var(--c-accent-primary-glow);
}

.docs-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.docs-nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.docs-nav-group-label {
  padding: 0 8px 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.docs-nav-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.docs-nav-link {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px 7px 12px;
  border-radius: 6px;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  font-weight: 400;
  line-height: 1.4;
  text-decoration: none;
  transition: background-color 140ms ease, color 140ms ease;
  cursor: pointer;
}
.docs-nav-link-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.docs-nav-link:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.docs-nav-link.is-active {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-weight: 600;
}
.docs-nav-link.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2px;
  border-radius: 2px;
  background: var(--c-accent-primary);
}
.docs-nav-link.is-soon {
  color: var(--c-text-muted);
  opacity: 0.65;
  cursor: not-allowed;
}
.docs-nav-link.is-soon:hover {
  background: transparent;
  color: var(--c-text-muted);
}

.nav-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
}
.nav-tag.tag--new {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.nav-tag.tag--beta {
  background: #ffeadf;
  color: #a45e05;
}
.nav-tag.tag--hot {
  background: #ffecec;
  color: #c0392b;
}

/* Dark mode overrides — the beta/hot tag backgrounds are peach / rose
   literals that look washed-out on a dark panel; recolor with
   translucent accents so they still read as warm/warning without
   glowing white. */
[data-theme="dark"] .nav-tag.tag--beta {
  background: rgba(255, 205, 120, 0.18);
  color: #ffcd78;
}
[data-theme="dark"] .nav-tag.tag--hot {
  background: rgba(255, 144, 144, 0.18);
  color: #ff9090;
}
</style>
