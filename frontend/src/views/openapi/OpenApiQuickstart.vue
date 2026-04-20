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
      <p class="docs-lede">5 行代码完成第一次调用，在几分钟内验证你的 API Key 能否通达平台数据。</p>
      <p class="docs-body">按照下面三个步骤，你会依次了解接口的基础信息、完成鉴权准备并发起一次真实请求，拿到平台返回的 JSON 响应。</p>
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
      <p class="docs-body">使用你的 API Key 向数据服务接口发起请求，以下 cURL 示例即可拿到最新的报告列表：</p>

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

      <p class="docs-body">请求成功后，响应体会返回 <code>code: 0</code>、一个可追溯的 <code>requestId</code>，以及按分页组织的报告列表。接着就可以参考示例接口页面组合调用更多能力。</p>
    </section>
  </div>
</template>
