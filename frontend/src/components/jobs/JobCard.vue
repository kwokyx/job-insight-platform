<script setup>
import { ArrowRight, Building2, Clock, GraduationCap, Heart, MapPin } from 'lucide-vue-next'

defineProps({
  job: {
    type: Object,
    required: true
  },
  // Whether this job is currently in the user's favorites set. Owned by
  // the parent (JobsView) so it can be batch-fetched once per mount
  // instead of one API call per card.
  isFavorite: {
    type: Boolean,
    default: false
  },
  // Gate the heart UI entirely on auth — not-logged-in users never see
  // the button at all, per design spec. The parent passes
  // `authStore.isLoggedIn`.
  loggedIn: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['open', 'favorite'])
</script>

<template>
  <button type="button" class="job-card" :class="{ 'has-favorite': loggedIn }" @click="emit('open', job)">
    <!-- Favorite button: absolutely positioned in the card's top-right
         corner so it overlays the .salary-block area visually without
         actually participating in its flex layout. `@click.stop` keeps
         the parent card's `@open` handler from firing when the heart is
         tapped. Only rendered for logged-in users per design spec. -->
    <span
      v-if="loggedIn"
      class="job-favorite-btn"
      :class="{ active: isFavorite }"
      role="button"
      tabindex="0"
      :aria-label="isFavorite ? '取消收藏' : '收藏岗位'"
      :aria-pressed="isFavorite"
      @click.stop="emit('favorite', job.id)"
      @keydown.enter.stop.prevent="emit('favorite', job.id)"
      @keydown.space.stop.prevent="emit('favorite', job.id)"
    >
      <Heart :size="16" :stroke-width="1.8" :fill="isFavorite ? 'currentColor' : 'none'" />
    </span>
    <div class="job-card-surface">
      <div class="job-card-top">
        <div class="job-title-group">
          <h3 class="job-title">{{ job.title }}</h3>
          <p class="job-company">{{ job.companyName }}</p>
        </div>
        <div class="salary-block">
          <span class="job-salary">{{ job.salaryText || '面议' }}</span>
          <span class="salary-label">月薪区间</span>
        </div>
      </div>

      <div class="job-meta-row">
        <span class="meta-pill">
          <MapPin :size="12" />
          {{ job.city || '全国' }}
        </span>
        <span class="meta-pill" v-if="job.industryName">
          <Building2 :size="12" />
          {{ job.industryName }}
        </span>
        <span class="meta-pill" v-if="job.experience">
          <Clock :size="12" />
          {{ job.experience }}
        </span>
        <span class="meta-pill" v-if="job.education">
          <GraduationCap :size="12" />
          {{ job.education }}
        </span>
      </div>

      <div class="job-snippet-wrap">
        <p class="job-snippet">
          {{ job.description || job.requirements || '岗位正在热招中，点击查看详情。' }}
        </p>

        <div class="job-card-footer" aria-hidden="true">
          <span>查看职位详情</span>
          <ArrowRight :size="14" />
        </div>
      </div>
    </div>
  </button>
</template>

<style scoped>
.job-card {
  position: relative;
  width: 100%;
  padding: 0;
  border-radius: 14px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-quiet);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    box-shadow 260ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    border-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1)),
    background-color 220ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}

.job-card-surface {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 20px 18px;
}

.job-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-border-glass-hover);
  background: var(--c-bg-surface-hover);
  box-shadow: var(--shadow-card-raised);
}

.job-card:hover .job-title {
  color: var(--c-accent-primary);
}

.job-card-top {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.job-title-group {
  min-width: 0;
  flex: 1 1 auto;
}

.job-title {
  margin: 0 0 4px;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -0.01em;
  transition: color 180ms var(--ease-out, cubic-bezier(0.2, 0.8, 0.2, 1));
}

.job-company {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
}

.salary-block {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  text-align: right;
}

/* When the favorite button is rendered it overlays the card's top-right
   corner; reserve some horizontal room in the salary block so the heart
   doesn't sit on top of the salary text. 38px covers the 30px heart +
   the 8px breathing room between heart and salary value. */
.job-card.has-favorite .salary-block {
  padding-right: 38px;
}

.job-salary {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--c-accent-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.salary-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 500;
  color: var(--c-text-muted);
  letter-spacing: 0.02em;
}

.job-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--c-accent-primary-glow);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
}

.meta-pill :deep(svg) {
  color: var(--c-accent-primary);
  opacity: 0.7;
}

.job-snippet-wrap {
  position: relative;
  min-height: 68px;
}

.job-snippet {
  margin: 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: opacity 220ms var(--ease-out, ease);
}

.job-card-footer {
  position: absolute;
  left: 0;
  bottom: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 220ms var(--ease-out, ease),
    transform 220ms var(--ease-out, ease);
}

.job-card:hover .job-snippet {
  opacity: 0.35;
}

.job-card:hover .job-card-footer {
  opacity: 1;
  transform: translateY(0);
}

.job-card:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

/* Favorite heart — absolutely positioned in the card's top-right corner.
   Uses a span with role="button" (not a real <button>) because the
   parent .job-card is already a <button>, and nesting interactive
   buttons is invalid HTML. Sits above the card-surface padding so it
   floats over the salary block's right edge without displacing any
   existing content. Warm accent (#e8556a) when active — picked over
   pure red to feel less alarming. */
.job-favorite-btn {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 2;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: transparent;
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-faint);
  cursor: pointer;
  transition: all 160ms ease;
}
.job-favorite-btn:hover {
  background: rgba(232, 85, 106, 0.08);
  border-color: rgba(232, 85, 106, 0.35);
  color: #e8556a;
}
.job-favorite-btn.active {
  color: #e8556a;
  background: rgba(232, 85, 106, 0.08);
  border-color: rgba(232, 85, 106, 0.35);
}
.job-favorite-btn.active:hover {
  background: rgba(232, 85, 106, 0.14);
  border-color: rgba(232, 85, 106, 0.5);
}
.job-favorite-btn:focus-visible {
  outline: 2px solid #e8556a;
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .job-card {
    border-radius: 12px;
  }

  .job-card-surface {
    padding: 16px;
    gap: 12px;
  }

  .job-card-top {
    flex-direction: column;
    gap: 8px;
  }

  .salary-block {
    align-items: flex-start;
    text-align: left;
  }

  /* On mobile the salary block flows below the title (not to the right),
     so it no longer collides with the top-right heart — drop the
     desktop-only reservation. */
  .job-card.has-favorite .salary-block {
    padding-right: 0;
  }

  .job-salary {
    font-size: 17px;
  }

  .job-snippet-wrap {
    min-height: 62px;
  }
}
</style>
