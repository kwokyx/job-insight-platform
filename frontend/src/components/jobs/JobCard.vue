<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Building2, Clock, GraduationCap, Heart, MapPin } from 'lucide-vue-next'
import { addFavorite, checkFavorite, invalidateApiCache, normalizeError, removeFavorite } from '../../api'
import { useAuthStore } from '../../store/auth'
import { useToast } from '../../composables/useToast'

const props = defineProps({
  job: {
    type: Object,
    required: true
  },
  // 父组件可传入批量查询好的收藏态，避免列表 N+1；
  // null 表示未知，JobCard 会自行发起一次 checkFavorite
  initialFavorited: {
    type: [Boolean, Object],
    default: null,
    // 允许 true / false / null
    validator: (v) => v === null || typeof v === 'boolean'
  }
})

const emit = defineEmits(['open', 'favorite-change'])

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { success, error: toastError } = useToast()

const favorited = ref(props.initialFavorited === true)
const favLoading = ref(false)
// 标记本卡是否已经向后端确认过一次状态（避免重复 check）
const checkedOnce = ref(props.initialFavorited !== null)

const jobId = computed(() => props.job?.id)

async function ensureCheckedFromServer() {
  if (!authStore.isLoggedIn || !jobId.value) return
  if (checkedOnce.value) return
  try {
    favorited.value = await checkFavorite(authStore.token, jobId.value)
  } catch (e) {
    // 静默：列表级 check 失败不打扰用户
    console.warn('checkFavorite failed', e)
  } finally {
    checkedOnce.value = true
  }
}

// 登录状态变化时重新同步
watch(
  () => [authStore.isLoggedIn, jobId.value],
  ([logged, id]) => {
    if (!logged) {
      favorited.value = false
      checkedOnce.value = true
      return
    }
    if (props.initialFavorited !== null) {
      favorited.value = props.initialFavorited === true
      checkedOnce.value = true
      return
    }
    if (id) {
      checkedOnce.value = false
      ensureCheckedFromServer()
    }
  },
  { immediate: true }
)

watch(
  () => props.initialFavorited,
  (val) => {
    if (val !== null) {
      favorited.value = val === true
      checkedOnce.value = true
    }
  }
)

async function handleToggleFavorite(e) {
  e.stopPropagation()
  e.preventDefault()
  if (!jobId.value) return

  if (!authStore.isLoggedIn) {
    const redirect = route.fullPath || '/jobs'
    router.push({ path: '/login', query: { redirect } })
    return
  }

  if (favLoading.value) return
  favLoading.value = true
  const wasFavorited = favorited.value
  // 乐观更新
  favorited.value = !wasFavorited
  try {
    if (wasFavorited) {
      await removeFavorite(authStore.token, jobId.value)
      success('已取消收藏')
    } else {
      await addFavorite(authStore.token, jobId.value)
      success('已加入收藏')
    }
    // 列表缓存失效，避免 ProfileView 再打开时看到旧数据
    invalidateApiCache('/favorites')
    emit('favorite-change', { jobId: jobId.value, favorited: favorited.value })
  } catch (err) {
    // 回滚
    favorited.value = wasFavorited
    toastError(normalizeError(err))
  } finally {
    favLoading.value = false
  }
}
</script>

<template>
  <button type="button" class="job-card" @click="emit('open', job)">
    <!-- 右上角收藏按钮：阻止冒泡以免触发整卡打开详情 -->
    <button
      type="button"
      class="fav-btn"
      :class="{ active: favorited, loading: favLoading }"
      :aria-pressed="favorited"
      :aria-label="favorited ? '取消收藏' : '收藏此岗位'"
      :title="favorited ? '已收藏，点击取消' : '加入收藏'"
      @click.stop.prevent="handleToggleFavorite"
    >
      <Heart
        :size="16"
        :stroke-width="1.9"
        :fill="favorited ? 'currentColor' : 'none'"
      />
    </button>

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

/* 收藏按钮：绝对定位在卡片右下角，避免挤压顶部薪资块；
   主按钮 hover 时整张卡上浮，收藏按钮保持自身 hover 状态独立。 */
.fav-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-muted);
  cursor: pointer;
  transition:
    background-color 160ms var(--ease-out, ease),
    border-color 160ms var(--ease-out, ease),
    color 160ms var(--ease-out, ease),
    transform 160ms var(--ease-out, ease),
    box-shadow 160ms var(--ease-out, ease);
}

.fav-btn:hover {
  border-color: rgba(239, 68, 68, 0.4);
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
  transform: scale(1.08);
}

.fav-btn.active {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.45);
  background: rgba(239, 68, 68, 0.1);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.18);
}

.fav-btn.active:hover {
  background: rgba(239, 68, 68, 0.16);
}

.fav-btn.loading {
  opacity: 0.55;
  cursor: progress;
}

.fav-btn:focus-visible {
  outline: 2px solid #ef4444;
  outline-offset: 2px;
}

/* 卡片底部 footer pill 在 hover 时会出现；此处让收藏按钮
   在 hover 时略向左上移一点点，避免与 footer pill 视觉堆叠。 */
.job-card:hover .fav-btn {
  border-color: rgba(239, 68, 68, 0.35);
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

  .job-salary {
    font-size: 17px;
  }

  .job-snippet-wrap {
    min-height: 62px;
  }
}
</style>
