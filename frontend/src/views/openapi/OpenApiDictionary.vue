<script setup>
import { inject, onMounted } from 'vue'

const sections = [
  { id: 'envelope', label: '统一响应结构' },
  { id: 'jobs', label: '岗位字段' },
  { id: 'reports', label: '报告字段' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="envelope" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">参考</div>
      <h1 class="docs-title">字段字典</h1>
      <p class="docs-lede">以下字段均来自当前仓库后端开放接口的真实返回结构，用于前后端对齐与联调验收。</p>
      <p class="docs-body">分页接口通常返回 <code>code/message/data/total/page/pageSize/timestamp</code>，非分页接口返回 <code>code/message/data/timestamp</code>。</p>

      <div class="error-table">
        <div class="error-row error-head">
          <span>字段</span>
          <span>类型</span>
          <span>说明</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">code</code></span>
          <span>number</span>
          <span>业务状态码，成功为 200。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">message</code></span>
          <span>string</span>
          <span>可读信息，失败时用于提示原因。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">data</code></span>
          <span>object | array</span>
          <span>业务数据主体。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">total/page/pageSize</code></span>
          <span>number</span>
          <span>仅分页接口返回。</span>
        </div>
        <div class="error-row">
          <span><code class="error-code">timestamp</code></span>
          <span>string</span>
          <span>服务端响应时间。</span>
        </div>
      </div>
    </section>

    <section id="jobs" class="docs-section">
      <h2 class="docs-h2">岗位字段（/api/v1/open/jobs）</h2>
      <p class="docs-body">由 <code>JobPosting</code> 聚合并按权限过滤后返回。</p>

      <div class="error-table">
        <div class="error-row error-head">
          <span>字段</span>
          <span>类型</span>
          <span>说明</span>
        </div>
        <div class="error-row"><span><code class="error-code">id</code></span><span>number</span><span>岗位 ID</span></div>
        <div class="error-row"><span><code class="error-code">title</code></span><span>string</span><span>岗位名称</span></div>
        <div class="error-row"><span><code class="error-code">companyName</code></span><span>string</span><span>公司名称</span></div>
        <div class="error-row"><span><code class="error-code">city</code></span><span>string</span><span>城市</span></div>
        <div class="error-row"><span><code class="error-code">industryName</code></span><span>string</span><span>行业</span></div>
        <div class="error-row"><span><code class="error-code">education</code></span><span>string</span><span>学历要求</span></div>
        <div class="error-row"><span><code class="error-code">experience</code></span><span>string</span><span>经验要求</span></div>
        <div class="error-row"><span><code class="error-code">salaryText</code></span><span>string</span><span>薪资文本</span></div>
        <div class="error-row"><span><code class="error-code">publishDate</code></span><span>string</span><span>发布时间</span></div>
        <div class="error-row"><span><code class="error-code">jobLabels</code></span><span>string</span><span>岗位标签</span></div>
        <div class="error-row"><span><code class="error-code">jobBenefits</code></span><span>string</span><span>福利信息</span></div>
        <div class="error-row"><span><code class="error-code">sourceSite</code></span><span>string</span><span>来源站点</span></div>
        <div class="error-row"><span><code class="error-code">companySize</code></span><span>string</span><span>公司规模</span></div>
        <div class="error-row"><span><code class="error-code">companyFinance</code></span><span>string</span><span>融资阶段</span></div>
      </div>
    </section>

    <section id="reports" class="docs-section">
      <h2 class="docs-h2">报告字段（/api/v1/open/reports/*）</h2>
      <p class="docs-body">公开报告列表与详情接口字段如下，具体可见范围受租户策略与权限配置控制。</p>

      <div class="error-table">
        <div class="error-row error-head">
          <span>字段</span>
          <span>类型</span>
          <span>说明</span>
        </div>
        <div class="error-row"><span><code class="error-code">id</code></span><span>number</span><span>报告 ID</span></div>
        <div class="error-row"><span><code class="error-code">reportName</code></span><span>string</span><span>报告名称</span></div>
        <div class="error-row"><span><code class="error-code">reportType</code></span><span>string</span><span>报告类型</span></div>
        <div class="error-row"><span><code class="error-code">description</code></span><span>string</span><span>摘要描述</span></div>
        <div class="error-row"><span><code class="error-code">analysisData</code></span><span>object</span><span>报告数据明细（详情接口）。</span></div>
        <div class="error-row"><span><code class="error-code">generatedAt</code></span><span>string</span><span>生成时间</span></div>
        <div class="error-row"><span><code class="error-code">viewCount</code></span><span>number</span><span>查看次数</span></div>
        <div class="error-row"><span><code class="error-code">reportGovernance</code></span><span>object</span><span>治理元信息（列表接口可能返回）。</span></div>
      </div>
    </section>
  </div>
</template>
