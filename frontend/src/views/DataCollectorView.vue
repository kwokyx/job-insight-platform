<script setup>
import PremiumCard from '../components/common/PremiumCard.vue'
import { Activity, AlarmClock, DatabaseZap, ShieldCheck, TriangleAlert } from 'lucide-vue-next'

const sourceCards = [
  { name: 'Boss / 招聘平台源', status: '运行中', volume: '264,390', risk: '低' },
  { name: '岗位标签关系', status: '运行中', volume: '2,098,384', risk: '低' },
  { name: '企业维表', status: '待监控增强', volume: '72,532', risk: '中' }
]

const pendingTasks = [
  '补齐 crawl_task 执行状态面板，接入成功率、重试次数和采集耗时。',
  '补齐低质量数据过滤规则，将 data_quality 接入采集后校验。',
  '把超过 30 天未更新岗位标记为不活跃，避免分析层继续使用陈旧数据。'
]
</script>

<template>
  <div class="collector-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">管理员视角</span>
        <h1 class="page-intro-title">数据采集监控面板</h1>
        <p class="page-intro-text">
          当前页面先承接管理员的采集监控入口，展示数据接入概况、风险项和下一步治理方向，避免继续停留在空白骨架页。
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">岗位总量</span>
          <span class="intro-metric-value">264,390</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">空白表告警</span>
          <span class="intro-metric-value">6</span>
        </div>
      </div>
    </section>

    <section class="grid three-col">
      <PremiumCard title="采集状态总览" glowColor="teal">
        <div class="stat-list">
          <div class="stat-row">
            <DatabaseZap :size="18" />
            <div>
              <strong>采集链路已接通</strong>
              <p>岗位主表、标签关系表和公司表已经具备可用数据基础。</p>
            </div>
          </div>
          <div class="stat-row">
            <Activity :size="18" />
            <div>
              <strong>监控粒度不足</strong>
              <p>当前仍缺少任务级成功率、失败原因和重试次数的可视化。</p>
            </div>
          </div>
          <div class="stat-row">
            <ShieldCheck :size="18" />
            <div>
              <strong>治理优先级明确</strong>
              <p>重点先做数据新鲜度、重复数据和低质量数据过滤三项。</p>
            </div>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="来源健康度" glowColor="primary">
        <div class="source-list">
          <div v-for="item in sourceCards" :key="item.name" class="source-item">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.volume }} 条</p>
            </div>
            <div class="source-meta">
              <span class="status-pill">{{ item.status }}</span>
              <span class="risk-pill" :class="item.risk === '低' ? 'ok' : 'warn'">风险 {{ item.risk }}</span>
            </div>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="待补建设施" glowColor="purple">
        <div class="todo-list">
          <div v-for="task in pendingTasks" :key="task" class="todo-item">
            <AlarmClock :size="16" />
            <span>{{ task }}</span>
          </div>
        </div>
      </PremiumCard>
    </section>

    <section class="grid two-col">
      <PremiumCard title="当前风险" glowColor="secondary">
        <div class="risk-card">
          <TriangleAlert :size="20" />
          <div>
            <strong>`crawl_job_posting` 与 `biz_job_posting` 数据量完全一致</strong>
            <p>需要进一步核查 ETL 是否仅做搬运，是否存在去重、清洗和状态更新缺失。</p>
          </div>
        </div>
        <div class="risk-card">
          <TriangleAlert :size="20" />
          <div>
            <strong>`biz_employment_indicator`、`biz_skill_relation` 等表仍为空</strong>
            <p>这些表缺失会直接影响教师分析、职业路径和运营报告的深度。</p>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="下一步接入建议" glowColor="teal">
        <div class="panel-note">
          <p>建议下一阶段直接对接后端采集任务日志接口，将本页升级成实时任务看板。</p>
          <p>如果你继续让我推进，我下一步可以直接把这个页面接上实际后端数据，而不是静态占位说明。</p>
        </div>
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.collector-page,
.stat-list,
.source-list,
.todo-list {
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

.stat-row,
.todo-item,
.risk-card {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
}

.source-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
}

.source-item strong,
.stat-row strong,
.risk-card strong {
  color: var(--c-text-primary);
}

.source-item p,
.stat-row p,
.risk-card p,
.panel-note p {
  color: var(--c-text-secondary);
}

.source-meta {
  display: grid;
  gap: 8px;
  justify-items: end;
}

.status-pill,
.risk-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill {
  background: rgba(56, 189, 248, 0.12);
  color: var(--c-accent-primary);
}

.risk-pill.ok {
  background: rgba(20, 184, 166, 0.12);
  color: var(--c-accent-teal);
}

.risk-pill.warn {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.panel-note {
  display: grid;
  gap: 12px;
}

@media (max-width: 1100px) {
  .three-col,
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
