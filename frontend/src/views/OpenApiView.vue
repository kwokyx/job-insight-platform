<script setup>
import PremiumCard from '../components/common/PremiumCard.vue'
import { BookKey, FileCode2, LockKeyhole, Radar, ShieldCheck, Webhook } from 'lucide-vue-next'

const capabilityList = [
  '岗位数据查询与筛选',
  '简历解析与结构化输出',
  '技能图谱与趋势分析',
  '报告生成与运营洞察'
]

const roadmapList = [
  '补齐 AppKey / AppSecret 管理界面和权限模型。',
  '接入 `sys_api_call_log`，展示调用量、失败率和租户维度统计。',
  '按能力分组生成对外 API 文档与示例代码。'
]
</script>

<template>
  <div class="openapi-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">管理员视角</span>
        <h1 class="page-intro-title">开放平台与能力输出中心</h1>
        <p class="page-intro-text">
          用于承接对外 API、凭证管理、调用监控和能力分发。当前先把管理员入口做成明确的后台能力页，后续再接入真实凭证与调用日志。
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">对外能力组</span>
          <span class="intro-metric-value">4</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">调用日志表</span>
          <span class="intro-metric-value">0</span>
        </div>
      </div>
    </section>

    <section class="grid three-col">
      <PremiumCard title="开放能力清单" glowColor="primary">
        <div class="capability-list">
          <div v-for="item in capabilityList" :key="item" class="capability-item">
            <FileCode2 :size="18" />
            <span>{{ item }}</span>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="凭证与安全" glowColor="purple">
        <div class="capability-list">
          <div class="capability-item">
            <BookKey :size="18" />
            <span>凭证管理 UI 仍未接入，需要管理员端新增发放、停用、轮换能力。</span>
          </div>
          <div class="capability-item">
            <LockKeyhole :size="18" />
            <span>当前安全侧还需要收紧 CORS、完善调用限流和敏感日志脱敏。</span>
          </div>
          <div class="capability-item">
            <ShieldCheck :size="18" />
            <span>建议把 API 权限和角色体系统一到一套管理模型里。</span>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="监控缺口" glowColor="teal">
        <div class="monitor-card">
          <Radar :size="20" />
          <div>
            <strong>`sys_api_call_log` 目前为 0 条</strong>
            <p>说明开放平台虽然已有过滤器与安全骨架，但还没有形成真正可运营的调用链路。</p>
          </div>
        </div>
        <div class="monitor-card">
          <Webhook :size="20" />
          <div>
            <strong>Webhook 与开放平台应打通</strong>
            <p>未来可以与岗位订阅、报告回调、外部系统联动一起形成对外能力闭环。</p>
          </div>
        </div>
      </PremiumCard>
    </section>

    <section class="grid two-col">
      <PremiumCard title="后续建设路线" glowColor="secondary">
        <div class="roadmap-list">
          <div v-for="item in roadmapList" :key="item" class="roadmap-item">
            <span class="dot"></span>
            <span>{{ item }}</span>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="当前判断" glowColor="teal">
        <div class="summary-card">
          <p>开放平台已经有安全配置和 API 过滤器基础，但管理员端还缺真正的可视化管理入口。</p>
          <p>本次先把角色差异化表现做出来，下一步就可以继续对接 AppKey、调用日志和 API 文档页面。</p>
        </div>
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.openapi-page,
.capability-list,
.roadmap-list {
  display: grid;
  gap: 24px;
}

.grid {
  display: grid;
  gap: 24px;
}

.three-col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.two-col {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.capability-item,
.roadmap-item,
.monitor-card {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
}

.monitor-card strong {
  display: block;
  margin-bottom: 6px;
  color: var(--c-text-primary);
}

.monitor-card p,
.summary-card p {
  color: var(--c-text-secondary);
}

.summary-card {
  display: grid;
  gap: 12px;
}

.dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 999px;
  background: var(--c-accent-primary);
}

@media (max-width: 1100px) {
  .three-col,
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
