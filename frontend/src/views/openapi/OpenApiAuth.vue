<script setup>
import { inject, onMounted } from 'vue'
import { authRules } from './data.js'

const sections = [
  { id: 'rules', label: '鉴权规则' },
  { id: 'signature', label: '签名算法' }
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
      <p class="docs-lede">使用 X-App-Key / X-Timestamp / X-Signature 完成签名鉴权，所有接口都要求下列请求头。</p>

      <ul class="auth-list">
        <li v-for="item in authRules" :key="item.title" class="auth-item">
          <strong class="auth-title">{{ item.title }}</strong>
          <p class="auth-detail">{{ item.detail }}</p>
        </li>
      </ul>
    </section>

    <section id="signature" class="docs-section">
      <h2 class="docs-h2">签名算法</h2>
      <p class="docs-body">平台采用 HMAC-SHA256 签名方案，保证请求在传输过程中不可伪造。具体步骤如下：</p>

      <ol class="auth-list">
        <li class="auth-item">
          <strong class="auth-title">拼接待签名字符串</strong>
          <p class="auth-detail">按顺序拼接 <code>HTTP 方法 + 请求路径 + X-Timestamp + 请求体（如无则为空串）</code>，使用换行符分隔。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">使用 AppSecret 计算摘要</strong>
          <p class="auth-detail">以 AppSecret 为 key，对上一步拼接出的字符串执行 SHA256 HMAC 计算，得到 64 位十六进制摘要。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">写入请求头</strong>
          <p class="auth-detail">将摘要填入 <code>X-Signature</code>，并确认 <code>X-Timestamp</code> 使用秒级 Unix 时间戳，时钟偏差控制在 5 分钟以内。</p>
        </li>
      </ol>

      <div class="callout callout--info">
        <div class="callout-body">
          <p><strong>提示：</strong>如果你使用官方 SDK，上述步骤会自动处理，只需配置 AppKey 与 AppSecret 即可。</p>
        </div>
      </div>
    </section>
  </div>
</template>
