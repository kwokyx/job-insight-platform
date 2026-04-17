<script setup>
import { BookOpen, Clock3, KeyRound, Layers3, Shield, TerminalSquare, Webhook } from 'lucide-vue-next'

const accessStages = [
  {
    title: '申请开通',
    status: '当前入口',
    description: '当前由平台统一受理开放申请，先确认合作场景和数据范围。'
  },
  {
    title: '分配凭证',
    status: '待开放',
    description: '后续会在这里承接 AppKey、AppSecret、配额和状态管理。'
  },
  {
    title: '联调上线',
    status: '预留位',
    description: '接入说明、错误码、调用约定和上线流程会随开放同步补齐。'
  }
]

const capabilityCatalog = [
  {
    name: '职业能力数据',
    scope: '面向合作方输出平台当前可见的职业能力与市场概览信息。',
    delivery: '结构化摘要 / 列表结果',
    status: '说明层已可见'
  },
  {
    name: '公开报告结果',
    scope: '承接公开报告的浏览、摘要和后续导出说明。',
    delivery: '报告摘要 / 状态回执',
    status: '原型已预留'
  },
  {
    name: '推荐与分析结果',
    scope: '对应推荐、分析和助手等工作区的结构化输出能力。',
    delivery: '结果面板 / 任务状态',
    status: '接入口预留'
  }
]

const deliveryModes = [
  {
    label: '鉴权方式',
    value: '平台统一分配',
    detail: '当前只展示开放状态，不提供创建、轮换和停用操作。'
  },
  {
    label: '返回形态',
    value: '摘要 + 列表 + 状态',
    detail: '优先对齐平台现有前端中已经出现的结构化结果表达。'
  },
  {
    label: '开放节奏',
    value: '说明先行',
    detail: '先明确能力边界，再逐步开放凭证、调用与配额配置。'
  }
]
</script>

<template>
  <div class="openapi-page page-shell">
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <span class="eyebrow">开放接口</span>
        <h1>把开发者入口先做成一版完整的开放能力原型</h1>
        <p>当前还不开放真实凭证，但会先把接入状态、开放节奏和能力边界讲清楚。</p>
      </div>

      <div class="hero-aside">
        <div class="hero-metrics">
          <div class="metric-tile">
            <span>当前状态</span>
            <strong>说明可见</strong>
          </div>
          <div class="metric-tile">
            <span>凭证管理</span>
            <strong>待开放</strong>
          </div>
          <div class="metric-tile">
            <span>能力范围</span>
            <strong>3 类入口</strong>
          </div>
        </div>

        <div class="status-strip">
          <Webhook :size="16" />
          <div>
            <strong>开放说明页已建立</strong>
            <p>当前先承接开放状态与能力说明，后续再接凭证与配额管理。</p>
          </div>
        </div>
      </div>
    </section>

    <section class="workspace-grid">
      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><KeyRound :size="13" /> 接入路径</span>
            <h2>开放节奏</h2>
            <p>先明确入口，再逐步开放凭证、联调和上线承接。</p>
          </div>
        </div>

        <div class="timeline-list">
          <article v-for="item in accessStages" :key="item.title" class="timeline-item">
            <div class="timeline-head">
              <strong>{{ item.title }}</strong>
              <span>{{ item.status }}</span>
            </div>
            <p>{{ item.description }}</p>
          </article>
        </div>
      </article>

      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><TerminalSquare :size="13" /> 凭证状态</span>
            <h2>鉴权与管理</h2>
            <p>保留未来凭证管理的落点，但不再重复解释同一件事。</p>
          </div>
        </div>

        <div class="credential-card">
          <Shield :size="20" />
          <div>
            <strong>AppKey / AppSecret 管理模块暂未开放</strong>
            <p>当前不提供创建、重置、轮换和停用操作，页面先承接状态说明与开放路径。</p>
          </div>
        </div>

        <div class="delivery-grid">
          <div v-for="item in deliveryModes" :key="item.label" class="delivery-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <p>{{ item.detail }}</p>
          </div>
        </div>
      </article>
    </section>

    <section class="surface section-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow"><Layers3 :size="13" /> 能力边界</span>
          <h2>当前承接的开放能力</h2>
          <p>不伪造具体接口契约，先把能力范围、交付形态和开放状态定义清楚。</p>
        </div>
      </div>

      <div class="catalog-list">
        <article v-for="item in capabilityCatalog" :key="item.name" class="catalog-row">
          <div class="catalog-main">
            <strong>{{ item.name }}</strong>
            <p>{{ item.scope }}</p>
          </div>
          <div class="catalog-side">
            <span class="catalog-tag">{{ item.delivery }}</span>
            <span class="catalog-state">{{ item.status }}</span>
          </div>
        </article>
      </div>
    </section>

    <section class="surface section-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow"><BookOpen :size="13" /> 对外说明</span>
          <h2>这页当前承担什么</h2>
          <p>它不是接口管理后台，而是开发者入口的开放说明层。</p>
        </div>
      </div>

      <div class="note-grid">
        <div class="note-card">
          <Clock3 :size="18" />
          <div>
            <strong>说明先行</strong>
            <p>先确定开放能力和交付形态，避免后续接口层和前端表达脱节。</p>
          </div>
        </div>
        <div class="note-card">
          <Webhook :size="18" />
          <div>
            <strong>能力对齐现有产品</strong>
            <p>优先承接平台里已经存在的公开报告、推荐结果和分析结果表达。</p>
          </div>
        </div>
        <div class="note-card">
          <Shield :size="18" />
          <div>
            <strong>后续承接凭证与配额</strong>
            <p>等后端开放后，这一页再进一步扩展到凭证、状态和调用管理。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.workspace-hero,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 20px;
  padding: 22px;
  border-radius: 18px;
}

.hero-copy,
.hero-aside,
.section-panel,
.timeline-list,
.credential-card,
.catalog-main,
.delivery-card,
.note-card {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 10px;
}

.hero-copy h1,
.panel-head h2,
.credential-card strong,
.delivery-card strong,
.catalog-main strong,
.note-card strong {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(24px, 2.3vw, 31px);
  line-height: 1.08;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.timeline-item p,
.credential-card p,
.delivery-card p,
.catalog-main p,
.note-card p {
  margin: 0;
  color: var(--c-text-secondary);
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero-aside {
  gap: 12px;
}

.hero-metrics,
.workspace-grid,
.delivery-grid,
.note-grid {
  display: grid;
  gap: 12px;
}

.hero-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-tile,
.status-strip,
.timeline-item,
.credential-card,
.delivery-card,
.note-card {
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.54);
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
}

.metric-tile span,
.delivery-card span {
  color: var(--c-text-secondary);
  font-size: 12px;
}

.metric-tile strong {
  font-size: 18px;
  color: var(--c-text-primary);
  letter-spacing: -0.03em;
}

.status-strip {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
}

.status-strip strong {
  display: block;
  margin-bottom: 4px;
}

.workspace-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.section-panel {
  gap: 18px;
  min-width: 0;
  padding: 20px;
  border-radius: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.timeline-list {
  gap: 10px;
}

.timeline-item {
  gap: 8px;
  padding: 14px;
}

.timeline-head,
.catalog-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.timeline-head strong,
.catalog-main strong {
  color: var(--c-text-primary);
}

.timeline-head span,
.catalog-state {
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.credential-card {
  gap: 10px;
  align-items: flex-start;
  padding: 16px;
}

.delivery-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.delivery-card {
  gap: 8px;
  padding: 14px;
  border: 1px solid rgba(193, 198, 215, 0.42);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.48);
}

.catalog-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid rgba(193, 198, 215, 0.48);
}

.catalog-row {
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid rgba(193, 198, 215, 0.48);
}

.catalog-main {
  gap: 4px;
  min-width: 0;
}

.catalog-side {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.catalog-tag,
.catalog-state {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(242, 244, 250, 0.96);
}

.catalog-tag {
  color: var(--c-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.note-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.note-card {
  gap: 10px;
  padding: 16px;
}

@media (max-width: 1100px) {
  .workspace-hero,
  .workspace-grid,
  .delivery-grid,
  .note-grid {
    grid-template-columns: 1fr;
  }

  .hero-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .section-panel {
    padding: 18px;
    border-radius: 16px;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }

  .timeline-head,
  .catalog-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .catalog-side {
    justify-content: flex-start;
  }
}
</style>
