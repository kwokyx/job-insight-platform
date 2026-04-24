<script setup>
import { inject, onMounted } from 'vue'
import { errorCodes } from './data.js'

const sections = [
  { id: 'overview', label: '错误码说明' },
  { id: 'codes', label: '错误码列表' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="overview" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">参考</div>
      <h1 class="docs-title">错误码</h1>
      <p class="docs-lede">统一错误码映射与排查建议，结合响应中的 requestId 可快速定位问题日志。</p>
      <p class="docs-body">所有接口在异常时都会以非 0 的 <code>code</code> 返回，并附带人类可读的 <code>message</code>。建议在生产环境中对以下错误码做分类处理，便于监控告警与自动重试。</p>
    </section>

    <section id="codes" class="docs-section">
      <h2 class="docs-h2">错误码列表</h2>
      <p class="docs-body">遇到未列出的错误码时，请携带 requestId 联系平台支持，我们会在日志中追踪该次请求。</p>

      <div class="error-table">
        <div class="error-row error-head">
          <span>错误码</span>
          <span>含义</span>
          <span>处理建议</span>
        </div>
        <div v-for="item in errorCodes" :key="item.code" class="error-row">
          <span><code class="error-code">{{ item.code }}</code></span>
          <span>{{ item.meaning }}</span>
          <span class="error-action">{{ item.action }}</span>
        </div>
      </div>
    </section>
  </div>
</template>
