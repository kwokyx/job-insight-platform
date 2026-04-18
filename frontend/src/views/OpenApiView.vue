<script setup>
import { Webhook } from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
</script>

<template>
  <div class="openapi-page page-animate">
    <section class="hero glass-panel">
      <div class="hero-main">
        <div class="icon-wrap">
          <Webhook :size="32" stroke-width="1.5" />
        </div>
        <div>
          <h2>对外公开的API平台</h2>
          <p>能力输出层：帮助第三方企业/开发者系统对接职业能力数据、使用强大的简历解析及分析能力。</p>
        </div>
      </div>
    </section>

    <div class="grid">
      <PremiumCard title="开发者凭证与鉴权" glowColor="purple">
        <EmptyState icon="error" title="暂未开放" description="AppKey 和 AppSecret 管理模块暂未开放" />
      </PremiumCard>
    </div>
  </div>
</template>

<style scoped>
.openapi-page { display: flex; flex-direction: column; gap: 24px; }
.hero { padding: 32px; background: var(--c-bg-surface-strong); border-radius: var(--radius-lg); }
.hero-main { display: flex; gap: 18px; align-items: center; }
.icon-wrap { 
  display: flex; align-items: center; justify-content: center; width: 64px; height: 64px; 
  border-radius: 18px; background: linear-gradient(135deg, var(--c-accent-purple), #c026d3); color: white;
  box-shadow: var(--shadow-glow);
}
.hero h2 { margin: 0 0 6px; font-size: 28px; }
.hero p { margin: 0; color: var(--c-text-secondary); line-height: 1.6; }
.grid { display: grid; gap: 20px; }
</style>
