<script setup>
import { inject, onMounted } from 'vue'

const sections = [
  { id: 'quota', label: '配额与限流' },
  { id: 'headers', label: '响应头说明' },
  { id: 'advice', label: '接入建议' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="quota" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">参考</div>
      <h1 class="docs-title">限流与配额</h1>
      <p class="docs-lede">开放接口按 API Key 执行 QPS 与每日配额控制，匿名调用不享受独立配额上下文。</p>
      <p class="docs-body">管理员在控制台创建 API Key 时可配置 <code>rateLimitQps</code> 和 <code>dailyQuota</code>，后端会在请求链路中实时校验。</p>
    </section>

    <section id="headers" class="docs-section">
      <h2 class="docs-h2">响应头说明</h2>
      <div class="error-table">
        <div class="error-row error-head">
          <span>Header</span>
          <span>含义</span>
          <span>说明</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">X-RateLimit-Limit</code></span>
          <span>每日上限</span>
          <span>当前 API Key 的 dailyQuota 值。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">X-RateLimit-Remaining</code></span>
          <span>剩余额度</span>
          <span>今日剩余可调用次数。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">X-Tenant-Scope</code></span>
          <span>租户范围</span>
          <span>当前请求生效的数据范围，默认 public。</span>
        </div>
      </div>
    </section>

    <section id="advice" class="docs-section">
      <h2 class="docs-h2">接入建议</h2>
      <ul class="auth-list">
        <li class="auth-item">
          <strong class="auth-title">并发控制</strong>
          <p class="auth-detail">客户端按 Key 做本地限流，避免瞬时并发直接打满 QPS。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">错误重试</strong>
          <p class="auth-detail">遇到 <code>429</code> 建议退避重试；遇到 <code>401</code> 先检查 Key 状态再重试。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">审计排查</strong>
          <p class="auth-detail">保留 <code>X-Request-Id</code> 并与控制台审计日志联查。</p>
        </li>
      </ul>
    </section>
  </div>
</template>
