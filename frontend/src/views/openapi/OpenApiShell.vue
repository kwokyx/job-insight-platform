<script setup>
import { nextTick, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { List, Menu, X } from 'lucide-vue-next'
import OpenApiSidebar from './OpenApiSidebar.vue'

const route = useRoute()

const mainRef = ref(null)
const searchQuery = ref('')
const drawerOpen = ref(false)

// TOC state — children register their section list via inject'ed setSections.
const tocSections = ref([])
const activeId = ref('')

let observer = null

// Wire up an IntersectionObserver scoped to the scrolling main column. Any
// time children re-register sections (or the route changes the mounted page),
// we disconnect and rebuild observers against the new <section id="..."> nodes.
function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  if (observer) {
    observer.disconnect()
    observer = null
  }
  const root = mainRef.value
  if (!root) return
  const targets = root.querySelectorAll('section[id]')
  if (!targets.length) {
    activeId.value = tocSections.value[0]?.id || ''
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .map((e) => ({ id: e.target.id, top: e.boundingClientRect.top }))
      if (visible.length === 0) return
      visible.sort((a, b) => a.top - b.top)
      activeId.value = visible[0].id
    },
    {
      root,
      rootMargin: '-80px 0px -60% 0px',
      threshold: 0
    }
  )
  targets.forEach((t) => observer.observe(t))
}

async function setSections(sections) {
  tocSections.value = Array.isArray(sections) ? sections : []
  activeId.value = tocSections.value[0]?.id || ''
  await nextTick()
  setupObserver()
}

provide('openApiPage', { setSections })

function scrollToSection(id) {
  const el = document.getElementById(id)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeId.value = id
  drawerOpen.value = false
}

// Scroll the main column back to the top whenever the route changes — the
// hash-based scroll (when the nav item targets an anchor) is handled in a
// dedicated watcher below so it wins over this one.
watch(
  () => route.path,
  async () => {
    await nextTick()
    if (mainRef.value) mainRef.value.scrollTop = 0
    drawerOpen.value = false
  }
)

// If the URL carries a hash (e.g. /openapi/intro#module-jobs), wait for the
// target page to mount and then scroll to the element. This runs after the
// path-level scroll-to-top so hash navigation lands on the right anchor.
watch(
  () => route.fullPath,
  async () => {
    await nextTick()
    // Give the destination component one more tick to register sections.
    await nextTick()
    if (route.hash) {
      const id = route.hash.slice(1)
      const el = document.getElementById(id)
      if (el) {
        el.scrollIntoView({ behavior: 'auto', block: 'start' })
        activeId.value = id
      }
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})
</script>

<template>
  <div class="docs-shell">
    <button
      class="docs-drawer-toggle"
      type="button"
      aria-label="切换文档目录"
      @click="drawerOpen = !drawerOpen"
    >
      <X v-if="drawerOpen" :size="16" :stroke-width="1.8" aria-hidden="true" />
      <Menu v-else :size="16" :stroke-width="1.8" aria-hidden="true" />
      <span>目录</span>
    </button>

    <aside
      class="docs-sidebar"
      :class="{ 'is-open': drawerOpen }"
      aria-label="文档目录"
    >
      <OpenApiSidebar
        v-model:searchQuery="searchQuery"
        @navigate="drawerOpen = false"
      />
    </aside>

    <main ref="mainRef" class="docs-main">
      <article class="docs-article">
        <router-view v-slot="{ Component, route: childRoute }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="childRoute.fullPath" />
          </transition>
        </router-view>
      </article>
    </main>

    <aside v-if="tocSections.length" class="docs-outline" aria-label="On this page">
      <div class="docs-outline-label">
        <List :size="12" :stroke-width="1.8" aria-hidden="true" />
        On this page
      </div>
      <ul class="docs-outline-list">
        <li v-for="s in tocSections" :key="s.id">
          <a
            :href="`#${s.id}`"
            :class="{ 'is-active': activeId === s.id }"
            @click.prevent="scrollToSection(s.id)"
          >{{ s.label }}</a>
        </li>
      </ul>
    </aside>
  </div>
</template>

<style scoped>
.docs-shell {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr) 220px;
  width: 100%;
  height: 100%;
  min-height: 0;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
}

/* ---------- Left sidebar column ---------- */
.docs-sidebar {
  position: sticky;
  top: 0;
  align-self: stretch;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  border-right: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  scrollbar-width: none;
}
.docs-sidebar::-webkit-scrollbar {
  display: none;
}

/* ---------- Middle article column ---------- */
.docs-main {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  border-left: 1px solid var(--c-border-glass);
  border-right: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
}
.docs-article {
  position: relative;
  max-width: 820px;
  margin: 0 auto;
  padding: 56px 48px 96px;
}

/* ---------- Right outline / TOC column ---------- */
.docs-outline {
  position: sticky;
  top: 0;
  align-self: stretch;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 56px 20px 32px 24px;
  background: var(--c-bg-base-elevated);
  scrollbar-width: none;
}
.docs-outline::-webkit-scrollbar {
  display: none;
}
.docs-outline-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.docs-outline-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.docs-outline-list a {
  display: block;
  padding: 4px 10px;
  border-left: 2px solid transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.5;
  text-decoration: none;
  transition: color 140ms ease, border-color 140ms ease;
}
.docs-outline-list a:hover {
  color: var(--c-text-primary);
}
.docs-outline-list a.is-active {
  border-left-color: var(--c-accent-primary);
  color: var(--c-accent-primary);
  font-weight: 600;
}

/* ---------- Drawer toggle (mobile) ---------- */
.docs-drawer-toggle {
  display: none;
  position: fixed;
  top: 14px;
  left: 14px;
  z-index: 40;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  box-shadow: var(--shadow-card-quiet);
}
/* ---------- Route transition ---------- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ---------- Responsive ---------- */
@media (max-width: 1279px) {
  .docs-shell {
    grid-template-columns: 272px minmax(0, 1fr);
  }
  .docs-outline {
    display: none;
  }
}

@media (max-width: 1023px) {
  .docs-shell {
    grid-template-columns: minmax(0, 1fr);
  }
  .docs-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 50;
    width: 280px;
    height: 100%;
    transform: translateX(-100%);
    transition: transform 220ms ease;
    box-shadow: var(--shadow-panel);
  }
  .docs-sidebar.is-open {
    transform: translateX(0);
  }
  .docs-drawer-toggle {
    display: inline-flex;
  }
  .docs-main {
    border-left: none;
    border-right: none;
  }
  .docs-article {
    padding: 72px 32px 80px;
  }
}

@media (max-width: 767px) {
  .docs-article {
    padding: 72px 20px 64px;
  }
}

/* =========================================================================
   Shared docs content styles — applied to child views via :deep().
   These cover every layout class the 5 sub-pages reuse (breadcrumb / lede /
   callouts / feature-cards / path-cards / caps / compare-table / code-doc /
   param-table / error-table / method-badge / required-pill / etc.).
   ========================================================================= */

.docs-main :deep(.docs-section) {
  margin-top: 48px;
  scroll-margin-top: 72px;
}
.docs-main :deep(.docs-section-lead) {
  margin-top: 0;
}
.docs-main :deep(.docs-breadcrumb) {
  margin-bottom: 10px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.docs-main :deep(.docs-title) {
  margin: 0 0 16px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: clamp(28px, 3.5vw, 36px);
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.01em;
}
.docs-main :deep(.docs-lede) {
  margin: 0 0 24px;
  color: var(--c-text-secondary);
  font-family: var(--font-serif);
  font-size: 17px;
  line-height: 1.6;
}
.docs-main :deep(.docs-h2) {
  margin: 0 0 14px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.005em;
}
.docs-main :deep(.docs-h3) {
  margin: 28px 0 10px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
}
.docs-main :deep(.docs-h4) {
  margin: 0 0 10px;
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.02em;
}
.docs-main :deep(.docs-body) {
  margin: 0 0 16px;
  color: var(--c-text-secondary);
  font-family: var(--font-serif);
  font-size: 15.5px;
  line-height: 1.7;
}

/* Callouts */
.docs-main :deep(.callout) {
  display: flex;
  gap: 12px;
  margin: 20px 0;
  padding: 14px 16px;
  border: 1px solid transparent;
  border-left-width: 3px;
  border-radius: 10px;
  font-family: var(--font-serif);
  font-size: 14.5px;
  line-height: 1.65;
}
.docs-main :deep(.callout--tip) {
  background: #fff8e7;
  border-color: #f5dfa4;
  border-left-color: #d9a400;
  color: #6b4e0f;
}
.docs-main :deep(.callout--info) {
  background: #ecf4ff;
  border-color: #cfe0fb;
  border-left-color: var(--c-accent-primary);
  color: #1a4b99;
}
.docs-main :deep(.callout--success) {
  background: #eaf7ee;
  border-color: #bce2c7;
  border-left-color: #2f8f4c;
  color: #1f6a37;
}
.docs-main :deep(.callout-icon) {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 2px;
  color: inherit;
}
.docs-main :deep(.callout-body) {
  flex: 1;
  min-width: 0;
  color: inherit;
}
.docs-main :deep(.callout-body p) {
  margin: 0;
  color: inherit;
  font-family: var(--font-serif);
}
.docs-main :deep(.callout-body p + p) {
  margin-top: 6px;
}
.docs-main :deep(.callout-lead) {
  margin-bottom: 6px !important;
}
.docs-main :deep(.callout-list) {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: inherit;
  font-family: var(--font-serif);
}
.docs-main :deep(.callout-list li) {
  color: inherit;
  line-height: 1.7;
}
.docs-main :deep(.callout-list strong) {
  color: inherit;
}
.docs-main :deep(.callout-link) {
  margin-left: 6px;
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
}
.docs-main :deep(.callout-link:hover) {
  text-decoration: underline;
}
.docs-main :deep(.callout--tip .callout-link) {
  color: #8a6206;
}
.docs-main :deep(.callout--success .callout-link) {
  color: #1f6a37;
}

/* Compare table */
.docs-main :deep(.compare-table-wrap) {
  margin: 8px 0 24px;
  overflow-x: auto;
}
.docs-main :deep(.compare-table) {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-family: var(--font-serif);
  font-size: 14.5px;
}
.docs-main :deep(.compare-table th),
.docs-main :deep(.compare-table td) {
  padding: 14px 18px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--c-border-glass);
}
.docs-main :deep(.compare-table thead th) {
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.docs-main :deep(.compare-table tbody th) {
  width: 108px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
  border-right: 1px solid var(--c-border-glass);
}
.docs-main :deep(.compare-table tbody td) {
  color: var(--c-text-secondary);
  font-family: var(--font-serif);
  line-height: 1.65;
}
.docs-main :deep(.compare-table tbody tr:last-child th),
.docs-main :deep(.compare-table tbody tr:last-child td) {
  border-bottom: none;
}
.docs-main :deep(.compare-link) {
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  text-decoration: none;
}
.docs-main :deep(.compare-link:hover) {
  text-decoration: underline;
}

/* Feature cards */
.docs-main :deep(.feature-cards) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
  margin: 20px 0 8px;
}
.docs-main :deep(.feature-card) {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 18px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background: var(--c-bg-base-elevated);
  color: inherit;
  text-decoration: none;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
  overflow: hidden;
  scroll-margin-top: 72px;
}
.docs-main :deep(.feature-card::after) {
  content: '';
  position: absolute;
  top: -48px;
  right: -48px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--c-accent-primary-glow), transparent 70%);
  pointer-events: none;
}
.docs-main :deep(.feature-card:hover) {
  transform: translateY(-2px);
  border-color: var(--c-border-glass-hover);
  box-shadow: var(--shadow-card-raised);
}
.docs-main :deep(.feature-card-head) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 2px;
}
.docs-main :deep(.feature-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.docs-main :deep(.feature-card-title) {
  flex: 1;
  min-width: 0;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.3;
}
.docs-main :deep(.feature-card-tag) {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 7px;
  border-radius: 4px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.3;
}
.docs-main :deep(.feature-card-tag.tag--hot) {
  background: #ffecec;
  color: #c0392b;
}
.docs-main :deep(.feature-card-tag.tag--new) {
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
}
.docs-main :deep(.feature-card-tag.tag--beta) {
  background: #ffeadf;
  color: #a45e05;
}
.docs-main :deep(.feature-card-desc) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.65;
}
.docs-main :deep(.feature-card-meta) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
}
.docs-main :deep(.feature-card-meta .meta-path) {
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  background: transparent;
  padding: 0;
  border: 0;
}
.docs-main :deep(.meta-dot) {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--c-text-faint);
}

/* Path cards */
.docs-main :deep(.path-cards) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 18px 0 0;
}
.docs-main :deep(.path-card) {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: var(--c-accent-primary-glow);
  transition: border-color 160ms ease, background-color 160ms ease;
}
.docs-main :deep(.path-card:hover) {
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}
.docs-main :deep(.path-num) {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--c-accent-primary);
  color: #ffffff;
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}
.docs-main :deep(.path-body) {
  min-width: 0;
}
.docs-main :deep(.path-body h4) {
  margin: 0 0 4px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
}
.docs-main :deep(.path-body p) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.6;
}

/* Capabilities grid */
.docs-main :deep(.caps) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 18px 0 0;
}
.docs-main :deep(.cap) {
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
}
.docs-main :deep(.cap-head) {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.docs-main :deep(.cap-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  border: 1px solid var(--c-border-glass);
}
.docs-main :deep(.cap-title) {
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.005em;
}
.docs-main :deep(.cap-desc) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.6;
}

/* Facts grid */
.docs-main :deep(.facts-grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 32px;
  margin: 24px 0 0;
}
.docs-main :deep(.fact-item) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.docs-main :deep(.fact-label) {
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.docs-main :deep(.fact-value) {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
  word-break: break-word;
}
.docs-main :deep(.fact-detail) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.55;
}

/* Auth list */
.docs-main :deep(.auth-list) {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.docs-main :deep(.auth-item) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 14px;
  border-left: 2px solid var(--c-border-glass);
}
.docs-main :deep(.auth-title) {
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
}
.docs-main :deep(.auth-detail) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-serif);
  font-size: 14.5px;
  line-height: 1.65;
}

/* Method badge */
.docs-main :deep(.method-badge) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 54px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  flex-shrink: 0;
}
.docs-main :deep(.method-badge.get) {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}
.docs-main :deep(.method-badge.post) {
  background: rgba(37, 99, 235, 0.12);
  color: #1d4ed8;
}
.docs-main :deep(.method-badge.put) {
  background: rgba(217, 119, 6, 0.14);
  color: #b45309;
}
.docs-main :deep(.method-badge.delete) {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}

.docs-main :deep(.path-mono) {
  display: inline-block;
  min-width: 0;
  color: var(--c-text-primary);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.4;
  word-break: break-all;
}
.docs-main :deep(.path-mono-lg) {
  font-size: 14px;
}

/* Example blocks */
.docs-main :deep(.example-stack) {
  display: flex;
  flex-direction: column;
  gap: 44px;
}
.docs-main :deep(.example-block) {
  display: flex;
  flex-direction: column;
  gap: 20px;
  scroll-margin-top: 72px;
}
.docs-main :deep(.example-head) {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--c-border-glass);
}
.docs-main :deep(.example-method-row) {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.docs-main :deep(.example-title) {
  margin-top: 6px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  line-height: 1.3;
}
.docs-main :deep(.example-summary) {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.6;
}
.docs-main :deep(.example-subsection) {
  display: flex;
  flex-direction: column;
}
.docs-main :deep(.header-list) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--c-text-secondary);
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.6;
}
.docs-main :deep(.header-list li) {
  padding: 2px 0;
}

/* Param table */
.docs-main :deep(.param-table) {
  display: flex;
  flex-direction: column;
}
.docs-main :deep(.param-row) {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr 0.6fr 2.4fr;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.55;
}
.docs-main :deep(.param-row:last-child) {
  border-bottom: none;
}
.docs-main :deep(.param-head) {
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.docs-main :deep(.param-name) {
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 12.5px;
}
.docs-main :deep(.param-type) {
  color: var(--c-text-primary);
  font-family: var(--font-mono);
  font-size: 12.5px;
}
.docs-main :deep(.param-desc) {
  color: var(--c-text-secondary);
}

/* Required pill */
.docs-main :deep(.required-pill) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}
.docs-main :deep(.required-pill.yes) {
  background: #ffe9c7;
  color: #b26a00;
}
.docs-main :deep(.required-pill.no) {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
}

/* Code block */
.docs-main :deep(.code-doc-wrap) {
  position: relative;
  margin: 0 0 16px;
}
.docs-main :deep(.code-doc-block) {
  margin: 0;
  padding: 16px 18px;
  overflow: auto;
  border-radius: 12px;
  background: #0f1a2e;
  color: #e6ecf6;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}
.docs-main :deep(.code-doc-block code) {
  display: block;
  color: inherit;
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
  white-space: pre;
}
.docs-main :deep(.code-doc-copy) {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  cursor: pointer;
  transition: background-color 140ms ease, color 140ms ease;
}
.docs-main :deep(.code-doc-copy:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
.docs-main :deep(.code-doc-copy:focus) {
  outline: none;
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
}

/* Error table */
.docs-main :deep(.error-table) {
  display: flex;
  flex-direction: column;
}
.docs-main :deep(.error-row) {
  display: grid;
  grid-template-columns: 0.7fr 1fr 2fr;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.55;
}
.docs-main :deep(.error-row:last-child) {
  border-bottom: none;
}
.docs-main :deep(.error-head) {
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.docs-main :deep(.error-code) {
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 12.5px;
  font-weight: 600;
}
.docs-main :deep(.error-action) {
  color: var(--c-text-secondary);
}

/* Sub-page header row — breadcrumb/title/subtitle at top of article */
.docs-main :deep(.docs-header-row) {
  display: flex;
  flex-direction: column;
}

/* ------------------------------------------------------------------
 * Dark mode overrides
 *
 * Callouts, path-card numerals, and some status tags use bespoke tints
 * (amber / blue / green) that sit on pale pastel backgrounds. On a dark
 * panel those pastel fills glow white. We swap them for translucent
 * fills that match the token palette while keeping each semantic color
 * (tip = amber, info = blue, success = green). The `.path-num` uses
 * `color: #ffffff` on the accent — in dark mode --c-accent-primary is a
 * pale lavender, so white would be invisible; swap to the dark base.
 * ------------------------------------------------------------------ */
[data-theme="dark"] .docs-main :deep(.callout--tip) {
  background: rgba(255, 205, 120, 0.14);
  border-color: rgba(255, 205, 120, 0.32);
  border-left-color: #ffcd78;
  color: #f3dfa9;
}
[data-theme="dark"] .docs-main :deep(.callout--info) {
  background: var(--c-accent-primary-glow);
  border-color: var(--c-border-glass-hover);
  border-left-color: var(--c-accent-primary);
  color: var(--c-text-primary);
}
[data-theme="dark"] .docs-main :deep(.callout--success) {
  background: rgba(90, 200, 140, 0.12);
  border-color: rgba(90, 200, 140, 0.32);
  border-left-color: #5ac88c;
  color: #b6e8c8;
}
[data-theme="dark"] .docs-main :deep(.callout--tip .callout-link) {
  color: #ffcd78;
}
[data-theme="dark"] .docs-main :deep(.callout--success .callout-link) {
  color: #5ac88c;
}
[data-theme="dark"] .docs-main :deep(.path-num) {
  color: #0f1420;
}
[data-theme="dark"] .docs-main :deep(.feature-card-tag.tag--beta) {
  background: rgba(255, 205, 120, 0.18);
  color: #ffcd78;
}
[data-theme="dark"] .docs-main :deep(.feature-card-tag.tag--hot) {
  background: rgba(255, 144, 144, 0.18);
  color: #ff9090;
}
[data-theme="dark"] .docs-main :deep(.required-pill.yes) {
  background: rgba(255, 205, 120, 0.18);
  color: #ffcd78;
}
[data-theme="dark"] .docs-main :deep(.method-badge.get) {
  background: rgba(90, 200, 140, 0.18);
  color: #7be0a5;
}
[data-theme="dark"] .docs-main :deep(.method-badge.post) {
  background: rgba(175, 198, 255, 0.2);
  color: #afc6ff;
}
[data-theme="dark"] .docs-main :deep(.method-badge.put) {
  background: rgba(255, 205, 120, 0.18);
  color: #ffcd78;
}
[data-theme="dark"] .docs-main :deep(.method-badge.delete) {
  background: rgba(255, 144, 144, 0.18);
  color: #ff9090;
}

/* Small responsive tweaks that apply across sub-pages */
@media (max-width: 767px) {
  .docs-main :deep(.docs-title) {
    font-size: 26px;
  }
  .docs-main :deep(.docs-h2) {
    font-size: 21px;
  }
  .docs-main :deep(.feature-cards),
  .docs-main :deep(.path-cards),
  .docs-main :deep(.caps),
  .docs-main :deep(.facts-grid) {
    grid-template-columns: 1fr;
    gap: 14px;
  }
  .docs-main :deep(.compare-table) {
    font-size: 13.5px;
  }
  .docs-main :deep(.compare-table th),
  .docs-main :deep(.compare-table td) {
    padding: 12px 14px;
  }
  .docs-main :deep(.param-row) {
    grid-template-columns: 1fr;
    gap: 6px;
    padding: 12px 0;
  }
  .docs-main :deep(.param-head) {
    display: none;
  }
  .docs-main :deep(.error-row) {
    grid-template-columns: 1fr;
    gap: 4px;
  }
  .docs-main :deep(.error-head) {
    display: none;
  }
}
</style>
