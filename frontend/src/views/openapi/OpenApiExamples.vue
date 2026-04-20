<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import {
  endpointExamples,
  methodClass,
  requiredClass,
  slugifyTitle
} from './data.js'

// Each example gets a stable slug id so the TOC and deep links can reference
// it directly. Slug is derived once at module scope via map().
const examplesWithIds = endpointExamples.map((ex) => ({
  ...ex,
  sectionId: slugifyTitle(ex.title)
}))

const sections = computed(() =>
  examplesWithIds.map((ex) => ({ id: ex.sectionId, label: ex.title }))
)

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections.value)
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
    <section class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">接口</div>
      <h1 class="docs-title">示例接口</h1>
      <p class="docs-lede">3 个典型接口的完整请求 / 响应示例，展示请求头、参数与结果结构。</p>
    </section>

    <div class="example-stack">
      <section
        v-for="(ex, idx) in examplesWithIds"
        :id="ex.sectionId"
        :key="ex.path"
        class="example-block docs-section"
      >
        <div class="example-head">
          <div class="example-method-row">
            <span :class="methodClass(ex.method)">{{ ex.method }}</span>
            <code class="path-mono path-mono-lg">{{ ex.path }}</code>
          </div>
          <strong class="example-title">{{ ex.title }}</strong>
          <p class="example-summary">{{ ex.summary }}</p>
        </div>

        <div class="example-subsection">
          <h4 class="docs-h4">请求头</h4>
          <ul class="header-list">
            <li v-for="header in ex.headers" :key="header">{{ header }}</li>
          </ul>
        </div>

        <div class="example-subsection">
          <h4 class="docs-h4">参数</h4>
          <div class="param-table">
            <div class="param-row param-head">
              <span>字段</span>
              <span>类型</span>
              <span>必填</span>
              <span>说明</span>
            </div>
            <div
              v-for="param in ex.params"
              :key="`${ex.path}-${param.name}`"
              class="param-row"
            >
              <span><code class="param-name">{{ param.name }}</code></span>
              <span class="param-type">{{ param.type }}</span>
              <span><span :class="requiredClass(param.required)">{{ param.required }}</span></span>
              <span class="param-desc">{{ param.description }}</span>
            </div>
          </div>
        </div>

        <div class="example-subsection">
          <h4 class="docs-h4">请求示例</h4>
          <div class="code-doc-wrap">
            <button
              class="code-doc-copy"
              type="button"
              @click="copyCode(ex.requestExample, `req-${idx}`)"
            >
              {{ copiedKey === `req-${idx}` ? '已复制' : '复制' }}
            </button>
            <pre class="code-doc-block"><code>{{ ex.requestExample }}</code></pre>
          </div>
        </div>

        <div class="example-subsection">
          <h4 class="docs-h4">响应示例</h4>
          <div class="code-doc-wrap">
            <button
              class="code-doc-copy"
              type="button"
              @click="copyCode(ex.responseExample, `res-${idx}`)"
            >
              {{ copiedKey === `res-${idx}` ? '已复制' : '复制' }}
            </button>
            <pre class="code-doc-block"><code>{{ ex.responseExample }}</code></pre>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
