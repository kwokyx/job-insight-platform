<script setup>
import { DatabaseZap } from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
</script>

<template>
  <div class="data-collector-page page-animate">
    <section class="hero glass-panel">
      <div class="hero-main">
        <div class="icon-wrap">
          <DatabaseZap :size="32" stroke-width="1.5" />
        </div>
        <div>
          <h2>分布式数据采集控制台</h2>
          <p>实时监控、调度四大招聘平台的数据采集任务。平台每日新增解析高质量职位数据。</p>
        </div>
      </div>
    </section>

    <div class="grid two-col">
      <PremiumCard title="采集节点状态" glowColor="teal">
        <div class="status-placeholder">
          <em>等待集成数据获取层微服务架构展示...</em>
        </div>
      </PremiumCard>
      
      <PremiumCard title="实时抓取流水" glowColor="primary">
        <div class="status-placeholder">
          <em>日志总线等待连接...</em>
        </div>
      </PremiumCard>
    </div>
  </div>
</template>

<style scoped>
.data-collector-page { display: flex; flex-direction: column; gap: 24px; }
.hero { padding: 32px; background: var(--c-bg-surface-strong); border-radius: var(--radius-lg); }
.hero-main { display: flex; gap: 18px; align-items: center; }
.icon-wrap { 
  display: flex; align-items: center; justify-content: center; width: 64px; height: 64px; 
  border-radius: 18px; background: linear-gradient(135deg, var(--c-accent-teal), #059669); color: white;
  box-shadow: var(--shadow-glow);
}
.hero h2 { margin: 0 0 6px; font-size: 28px; }
.hero p { margin: 0; color: var(--c-text-secondary); line-height: 1.6; }
.grid { display: grid; gap: 20px; }
.two-col { grid-template-columns: 1fr 1fr; }
.status-placeholder {
  display: flex; align-items: center; justify-content: center; height: 200px;
  border-radius: 12px; border: 1px dashed var(--c-border-glass); background: rgba(255, 255, 255, 0.02);
  color: var(--c-text-muted); font-size: 14px;
}
</style>
