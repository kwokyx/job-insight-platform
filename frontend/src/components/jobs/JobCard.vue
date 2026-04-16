<script setup>
import { ArrowRight, Building2, Clock, GraduationCap, MapPin } from 'lucide-vue-next'

defineProps({
  job: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['open'])
</script>

<template>
  <button type="button" class="job-card" @click="emit('open', job)">
    <div class="job-card-surface">
      <div class="job-card-top">
        <div class="job-title-group">
          <h3 class="job-title">{{ job.title }}</h3>
          <p class="job-company">{{ job.companyName }}</p>
        </div>
        <span class="job-salary">{{ job.salaryText || '面议' }}</span>
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
  border-radius: 18px;
  border: 1px solid var(--c-border-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 250, 255, 0.9)),
    var(--c-bg-surface);
  box-shadow: var(--shadow-panel);
  text-align: left;
  cursor: pointer;
  transition:
    transform var(--duration-normal) var(--ease-out),
    box-shadow var(--duration-normal) var(--ease-out),
    border-color var(--duration-normal) var(--ease-out);
  overflow: hidden;
  perspective: 900px;
}

.job-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 89, 199, 0.05), rgba(255, 255, 255, 0));
  opacity: 0;
  transition: opacity var(--duration-normal) var(--ease-out);
  pointer-events: none;
}

.job-card-surface {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 22px 20px;
  transform-style: preserve-3d;
}

.job-card:hover {
  transform: translateY(-4px);
  border-color: rgba(0, 89, 199, 0.2);
  box-shadow: var(--shadow-card-raised);
}

.job-card:hover::before {
  opacity: 1;
}

.job-card-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.job-title-group {
  min-width: 0;
}

.job-title {
  margin: 0 0 6px;
  color: var(--c-text-primary);
  font-size: 20px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
  transition: color var(--duration-normal) var(--ease-out);
}

.job-company {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.job-salary {
  flex-shrink: 0;
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 900;
  color: var(--c-accent-primary);
  white-space: nowrap;
}

.job-card:hover .job-title {
  color: var(--c-accent-primary);
}

.job-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(193, 198, 215, 0.65);
  color: var(--c-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.job-snippet-wrap {
  position: relative;
  min-height: 74px;
  padding-top: 2px;
}

.job-snippet {
  margin: 0;
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition:
    opacity var(--duration-normal) var(--ease-out),
    filter var(--duration-normal) var(--ease-out),
    transform var(--duration-normal) var(--ease-out);
}

.job-card-footer {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  width: fit-content;
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid rgba(0, 89, 199, 0.16);
  background: rgba(255, 255, 255, 0.82);
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 8px 18px rgba(12, 39, 82, 0.04);
  transform: translateY(10px) rotateX(10deg);
  transform-origin: center bottom;
  opacity: 0;
  transition:
    opacity var(--duration-normal) var(--ease-out),
    transform var(--duration-normal) var(--ease-out),
    background var(--duration-normal) var(--ease-out),
    border-color var(--duration-normal) var(--ease-out);
}

.job-card:hover .job-snippet {
  opacity: 0.48;
  filter: blur(0.7px);
  transform: translateY(-1px);
}

.job-card:hover .job-card-footer {
  opacity: 1;
  transform: translateY(0) rotateX(0deg);
}

.job-card:focus-visible {
  outline: 2px solid rgba(0, 89, 199, 0.32);
  outline-offset: 3px;
}

.job-card:hover .job-card-footer {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(0, 89, 199, 0.22);
}

@media (max-width: 768px) {
  .job-card {
    border-radius: 16px;
  }

  .job-card-surface {
    padding: 18px;
    gap: 14px;
  }

  .job-card-top {
    flex-direction: column;
    gap: 8px;
  }

  .job-salary {
    font-size: 18px;
  }

  .job-snippet-wrap {
    min-height: 68px;
  }

  .job-card-footer {
    margin-top: 12px;
  }
}
</style>
