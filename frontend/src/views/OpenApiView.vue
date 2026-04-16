<script setup>
import { BookOpen, Shield, TerminalSquare, Webhook } from 'lucide-vue-next'
</script>

<template>
  <div class="openapi-page page-shell">
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <span class="eyebrow">开放接口</span>
        <h1>对外公开的 API 入口</h1>
        <p>这里只说明接入能力和状态，不做真实凭证配置。</p>
      </div>

      <div class="hero-aside">
        <div class="status-strip">
          <Webhook :size="16" />
          <div>
            <strong>开放状态</strong>
            <p>公开说明页已可见，凭证暂未开放。</p>
          </div>
        </div>
        <div class="status-strip muted">
          <Shield :size="16" />
          <div>
            <strong>鉴权说明</strong>
            <p>后续开放 AppKey / AppSecret 时在这里承接。</p>
          </div>
        </div>
      </div>
    </section>

    <section class="workspace-grid">
      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><TerminalSquare :size="13" /> 开发者凭证</span>
            <h2>鉴权与接入</h2>
            <p>先保留明确容器，方便后续承接凭证和配置。</p>
          </div>
        </div>

        <div class="placeholder-card">
          <TerminalSquare :size="26" />
          <div>
            <strong>AppKey 和 AppSecret 管理模块暂未开放</strong>
            <p>当前只展示接入说明，不提供创建、重置或轮换能力。</p>
          </div>
        </div>
      </article>

      <article class="surface section-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow"><BookOpen :size="13" /> 接入说明</span>
            <h2>页面结构</h2>
            <p>预先分区公开能力、说明和扩展位置。</p>
          </div>
        </div>

        <ol class="step-list">
          <li>
            <strong>申请访问</strong>
            <p>由平台统一开放入口，当前仅展示说明。</p>
          </li>
          <li>
            <strong>配置凭证</strong>
            <p>未来在这里管理 AppKey、AppSecret 和状态。</p>
          </li>
          <li>
            <strong>开始调试</strong>
            <p>接入方式明确后，可查看调用说明和反馈。</p>
          </li>
        </ol>
      </article>
    </section>

    <section class="surface section-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">当前可见能力</span>
          <h2>公开能力说明</h2>
          <p>只保留页面里已经明确提到的能力。</p>
        </div>
      </div>

      <div class="capability-grid">
        <div class="capability-card">
          <span>职业能力数据</span>
          <strong>对外说明层</strong>
          <p>展示平台可对外提供的职业数据能力。</p>
        </div>
        <div class="capability-card">
          <span>简历解析</span>
          <strong>已预留接入口</strong>
          <p>保留这一项，方便后续对接解析接口。</p>
        </div>
        <div class="capability-card">
          <span>分析能力</span>
          <strong>分析与输出</strong>
          <p>保持和平台现有分析能力一致。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.workspace-hero,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 20px;
  padding: 24px;
  border-radius: 20px;
}

.hero-copy,
.hero-aside,
.section-panel,
.placeholder-card,
.step-list li,
.capability-card {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 10px;
}

.hero-copy h1,
.panel-head h2,
.placeholder-card strong,
.capability-card strong {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(24px, 2.4vw, 32px);
  line-height: 1.08;
  letter-spacing: -0.05em;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.placeholder-card p,
.step-list p,
.capability-card p {
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
  gap: 10px;
}

.status-strip,
.placeholder-card,
.step-list li,
.capability-card {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.54);
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

.status-strip.muted {
  background: rgba(255, 255, 255, 0.42);
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.section-panel {
  gap: 18px;
  min-width: 0;
  padding: 22px;
  border-radius: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.placeholder-card {
  gap: 12px;
  align-items: flex-start;
  padding: 16px;
}

.step-list {
  display: grid;
  gap: 12px;
  padding-left: 18px;
  margin: 0;
}

.step-list li {
  gap: 6px;
  padding: 12px 14px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.capability-card {
  gap: 8px;
  padding: 14px;
}

.capability-card span {
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .workspace-hero,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .capability-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .section-panel {
    padding: 20px;
    border-radius: 18px;
  }

  .capability-grid {
    grid-template-columns: 1fr;
  }
}
</style>
