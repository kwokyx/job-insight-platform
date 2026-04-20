<script setup>
defineProps({
  type: {
    type: String,
    default: 'card', // card, list, chart, stat
    validator: (v) => ['card', 'list', 'chart', 'stat'].includes(v)
  },
  lines: {
    type: Number,
    default: 3
  }
})
</script>

<template>
  <div class="skeleton-wrapper">
    <!-- Stat / KPI style -->
    <div v-if="type === 'stat'" class="skeleton-stat">
      <div class="skel-icon shine"></div>
      <div class="skel-content">
        <div class="skel-line shine w-1/2"></div>
        <div class="skel-line shine h-8 w-3/4 mt-2"></div>
      </div>
    </div>

    <!-- Chart style -->
    <div v-else-if="type === 'chart'" class="skeleton-chart shine">
      <div class="skel-line shine w-1/4 mb-4 bg-white/10"></div>
      <div class="skel-chart-area"></div>
    </div>

    <!-- List style -->
    <div v-else-if="type === 'list'" class="skeleton-list">
      <div v-for="i in lines" :key="i" class="skel-list-item shine"></div>
    </div>

    <!-- Default Card style -->
    <div v-else class="skeleton-card shine">
      <div class="skel-title shine w-1/3 mb-4 bg-white/10"></div>
      <div class="skel-lines">
        <div v-for="i in lines" :key="i" class="skel-line shine bg-white/5" :style="{ width: `${100 - (i * 10)}%` }"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-wrapper {
  width: 100%;
}

/* Base Shine Animation */
.shine {
  position: relative;
  overflow: hidden;
  background-color: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-sm);
}

.shine::after {
  content: "";
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  transform: translateX(-100%);
  background-image: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0) 0,
    rgba(255, 255, 255, 0.05) 20%,
    rgba(255, 255, 255, 0.1) 60%,
    rgba(255, 255, 255, 0)
  );
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

/* Layouts */
.skeleton-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: var(--radius-md);
  border: 1px solid rgba(255,255,255,0.05);
}
.skel-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
}
.skel-content {
  flex: 1;
}

.skeleton-chart {
  height: 300px;
  padding: 20px;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
}
.skel-chart-area {
  flex: 1;
  background: linear-gradient(to top, rgba(255,255,255,0.05), transparent);
  border-radius: 4px;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skel-list-item {
  height: 64px;
  border-radius: var(--radius-md);
}

.skeleton-card {
  padding: 24px;
  border-radius: var(--radius-lg);
}
.skel-title {
  height: 24px;
  border-radius: 4px;
}
.skel-lines {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.skel-line {
  height: 16px;
  border-radius: 4px;
}

/* Utilities */
.w-1\/2 { width: 50%; }
.w-1\/3 { width: 33.333%; }
.w-1\/4 { width: 25%; }
.w-3\/4 { width: 75%; }
.h-8 { height: 32px; }
.mt-2 { margin-top: 8px; }
.mb-4 { margin-bottom: 16px; }
.bg-white\/5 { background-color: rgba(255,255,255,0.05); }
.bg-white\/10 { background-color: rgba(255,255,255,0.1); }
</style>
