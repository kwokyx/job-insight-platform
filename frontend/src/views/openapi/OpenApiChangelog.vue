<script setup>
import { inject, onMounted } from 'vue'

const sections = [
  { id: 'recent', label: '近期更新' },
  { id: 'compatibility', label: '兼容性说明' }
]

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="recent" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">参考</div>
      <h1 class="docs-title">更新日志</h1>
      <p class="docs-lede">仅记录当前仓库已落地并可访问的开放接口与控制台能力调整。</p>

      <div class="auth-list">
        <div class="auth-item">
          <strong class="auth-title">2026-04-23</strong>
          <p class="auth-detail">文档导航补齐“限流与配额 / 字段字典 / 更新日志”，并全部改为已上线页面路由，不再标记“即将上线”。</p>
        </div>
        <div class="auth-item">
          <strong class="auth-title">2026-04-23</strong>
          <p class="auth-detail">认证说明统一为 <code>X-API-Key</code>，移除不适用于本项目的 <code>X-App-Key/X-Signature</code> 描述。</p>
        </div>
        <div class="auth-item">
          <strong class="auth-title">2026-04-23</strong>
          <p class="auth-detail">API 控制台请求示例改为真实管理接口路径 <code>/api/v1/open/api-keys*</code>，并与管理员登录态一致。</p>
        </div>
        <div class="auth-item">
          <strong class="auth-title">2026-04-23</strong>
          <p class="auth-detail">推荐页“技能差距 / 职业路径 / 技能雷达”切换为实时接口调用，失败返回真实错误，不再回落示例结果。</p>
        </div>
      </div>
    </section>

    <section id="compatibility" class="docs-section">
      <h2 class="docs-h2">兼容性说明</h2>
      <ul class="auth-list">
        <li class="auth-item">
          <strong class="auth-title">成功判定</strong>
          <p class="auth-detail">前端按 <code>code === 200</code> 判定成功，非 200 统一走错误处理。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">分页结构</strong>
          <p class="auth-detail">分页接口依赖 <code>total/page/pageSize</code>，对接时请保留这三个字段。</p>
        </li>
        <li class="auth-item">
          <strong class="auth-title">追踪字段</strong>
          <p class="auth-detail">建议保留响应头 <code>X-Request-Id</code> 以便联调与线上排查。</p>
        </li>
      </ul>
    </section>
  </div>
</template>
