<script setup>
// 角色化前置条件闸门组件
// 用法：
//   <ReadinessGate :state="readiness.state.value" :loading="readiness.loading.value">
//     <ActualFeaturePanel />
//   </ReadinessGate>
// ready=true：渲染默认插槽；ready=false：渲染引导卡
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { AlertTriangle, ArrowRight, Loader2, CheckCircle2 } from 'lucide-vue-next'

const props = defineProps({
  state: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  // 加载中时是否直接显示占位，默认 true；false 则把加载状态交给外部
  showLoading: {
    type: Boolean,
    default: true
  }
})

const router = useRouter()

const hasAction = computed(() => !!props.state?.nextAction?.route)

function goNext() {
  const route = props.state?.nextAction?.route
  if (route) router.push(route)
}
</script>

<template>
  <div v-if="loading && showLoading" class="readiness-loading">
    <Loader2 :size="22" class="spin" />
    <span>正在检查前置条件…</span>
  </div>

  <slot v-else-if="state?.ready" />

  <div v-else class="readiness-guide">
    <div class="readiness-head">
      <span class="readiness-icon"><AlertTriangle :size="18" /></span>
      <div>
        <h3 class="readiness-title">前置条件未就绪</h3>
        <p class="readiness-hint">{{ state?.nextAction?.hint || '请先完成前置步骤再访问本功能。' }}</p>
      </div>
    </div>

    <ul v-if="state?.missingFields?.length" class="readiness-missing">
      <li v-for="item in state.missingFields" :key="item">
        <span class="dot" />{{ item }}
      </li>
    </ul>

    <div class="readiness-meta" v-if="state?.updatedAt || state?.dataVersion">
      <CheckCircle2 :size="14" />
      <span v-if="state.updatedAt">数据更新：{{ state.updatedAt }}</span>
      <span v-if="state.dataVersion">· 版本 {{ state.dataVersion }}</span>
    </div>

    <button v-if="hasAction" class="readiness-cta" @click="goNext">
      {{ state.nextAction.label }}
      <ArrowRight :size="16" />
    </button>
  </div>
</template>

<style scoped>
.readiness-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--c-text-muted);
  font-size: 14px;
  padding: 32px;
  justify-content: center;
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.readiness-guide {
  padding: 28px;
  border-radius: var(--radius-lg, 16px);
  border: 1px dashed rgba(255, 196, 87, 0.45);
  background: rgba(255, 196, 87, 0.06);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.readiness-head { display: flex; gap: 12px; align-items: flex-start; }
.readiness-icon {
  color: #f0a830;
  background: rgba(240, 168, 48, 0.15);
  border-radius: 999px;
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.readiness-title { margin: 0 0 4px; font-size: 15px; color: var(--c-text-primary); }
.readiness-hint { margin: 0; font-size: 13px; color: var(--c-text-muted); line-height: 1.6; }
.readiness-missing {
  list-style: none; margin: 0; padding: 0;
  display: flex; flex-wrap: wrap; gap: 8px 16px;
  font-size: 13px; color: var(--c-text-secondary, var(--c-text-primary));
}
.readiness-missing li { display: flex; align-items: center; gap: 6px; }
.dot { width: 6px; height: 6px; border-radius: 999px; background: #f0a830; display: inline-block; }
.readiness-meta {
  font-size: 12px; color: var(--c-text-muted);
  display: flex; gap: 6px; align-items: center;
}
.readiness-cta {
  align-self: flex-start;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 999px;
  border: 1px solid rgba(30, 117, 255, 0.35);
  background: rgba(30, 117, 255, 0.12);
  color: var(--c-accent-primary);
  cursor: pointer; font-size: 13px; font-weight: 500;
  transition: all .2s;
}
.readiness-cta:hover { background: rgba(30, 117, 255, 0.2); transform: translateY(-1px); }
</style>
