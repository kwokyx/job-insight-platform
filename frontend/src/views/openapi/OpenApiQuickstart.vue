<script setup>
import { inject, onMounted, ref } from 'vue'
import { apiFacts, docHighlights, quickStartCommand } from './data.js'

const sections = [
  { id: 'overview', label: '概览' },
  { id: 'api-basics', label: '基础信息' },
  { id: 'first-call', label: '第一次调用' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})

const copiedKey = ref('')

async function copyCode(text, key) {
  let ok = false
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
      ok = true
    }
  } catch (_err) {
    ok = false
  }
  if (!ok) {
    try {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.setAttribute('readonly', '')
      ta.style.position = 'absolute'
      ta.style.left = '-9999px'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
      ok = true
    } catch (_err) {
      ok = false
    }
  }
  if (ok) {
    copiedKey.value = key
    setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = ''
    }, 1500)
  }
}
</script>

<template>
  <div class="docs-header-row">
    <section id="overview" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">入门</div>
      <h1 class="docs-title">快速开始</h1>
      <p class="docs-lede">用一条 cURL 请求快速验证开放接口是否可用，再按需接入 X-API-Key。</p>
      <p class="docs-body">下面的步骤全部基于仓库里当前后端已实现接口，不包含未上线能力。</p>
    </section>

    <section id="api-basics" class="docs-section">
      <h2 class="docs-h2">基础信息</h2>
      <p class="docs-body">在发起第一次调用前，先确认以下四项基础信息，对应到你应用侧的配置项。</p>

      <div class="facts-grid">
        <div v-for="item in apiFacts" :key="item.label" class="fact-item">
          <span class="fact-label">{{ item.label }}</span>
          <strong class="fact-value">{{ item.value }}</strong>
          <p class="fact-detail">{{ item.detail }}</p>
        </div>
      </div>

      <div class="facts-grid" style="margin-top: 20px;">
        <div v-for="item in docHighlights" :key="item.label" class="fact-item">
          <span class="fact-label">{{ item.label }}</span>
          <strong class="fact-value">{{ item.value }}</strong>
          <p class="fact-detail">{{ item.detail }}</p>
        </div>
      </div>
    </section>

    <section id="first-call" class="docs-section">
      <h2 class="docs-h2">第一次调用</h2>
      <p class="docs-body">先调用分析总览接口，确认网络、网关与响应结构正常：</p>

      <div class="code-doc-wrap">
        <button
          class="code-doc-copy"
          type="button"
          @click="copyCode(quickStartCommand, 'quickstart')"
        >
          {{ copiedKey === 'quickstart' ? '已复制' : '复制' }}
        </button>
        <pre class="code-doc-block"><code>{{ quickStartCommand }}</code></pre>
      </div>

      <p class="docs-body">请求成功后响应体应返回 <code>code: 200</code> 和可追溯的 <code>requestId</code>。接着可以参考示例接口页面继续接入岗位、报告等能力。</p>
    </section>
  </div>
</template>
