<script setup>
import { inject, onMounted } from 'vue'
import { authRules } from './data.js'

const sections = [
  { id: 'rules', label: '鉴权规则' },
  { id: 'headers', label: '请求头示例' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="rules" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">入门</div>
      <h1 class="docs-title">认证与密钥</h1>
      <p class="docs-lede">开放接口统一使用 <code>X-API-Key</code> 作为密钥头，不使用 AppKey + Signature 签名方案。</p>

      <ul class="auth-list">
        <li v-for="item in authRules" :key="item.title" class="auth-item">
          <strong class="auth-title">{{ item.title }}</strong>
          <p class="auth-detail">{{ item.detail }}</p>
        </li>
      </ul>
    </section>

    <section id="headers" class="docs-section">
      <h2 class="docs-h2">请求头示例</h2>
      <p class="docs-body">读取类开放接口可匿名访问；当你需要配额、租户范围与审计上下文时，请传入 <code>X-API-Key</code>：</p>

      <ol class="auth-list">
        <li class="auth-item">
          <strong class="auth-title">公开读取接口（可匿名）</strong>
          <p class="auth-detail"><code>GET /api/v1/open/analysis/overview</code>、<code>/jobs</code>、<code>/reports/public</code> 等接口可直接调用。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">带密钥调用（推荐）</strong>
          <p class="auth-detail">在请求头中添加 <code>X-API-Key: YOUR_API_KEY</code>，服务端会附带限流和租户范围响应头。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">管理员接口</strong>
          <p class="auth-detail"><code>/api/v1/open/api-keys*</code> 仅管理员可访问，通常通过平台控制台登录态调用，不对匿名调用开放。</p>
        </li>
      </ol>

      <div class="callout callout--info">
        <div class="callout-body">
          <p><strong>提示：</strong>开放接口返回 <code>X-Request-Id</code>，建议在异常日志里连同该值一起记录，便于快速追踪问题。</p>
        </div>
      </div>
    </section>
  </div>
</template>
