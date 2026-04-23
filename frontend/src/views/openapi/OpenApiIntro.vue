<script setup>
import { inject, onMounted } from 'vue'
import {
  Briefcase,
  BarChart3,
  Sparkles,
  GraduationCap,
  Compass,
  Wand,
  ScrollText,
  CloudDownload,
  RefreshCw,
  Grid2X2,
  Shield,
  Terminal,
  Lightbulb,
  Info,
  CheckCircle2
} from 'lucide-vue-next'
import {
  ledeText,
  openDataItems,
  accessModes,
  coreModules,
  pathSteps,
  keyCapabilities,
  supportText,
  tagClass
} from './data.js'

const sections = [
  { id: 'intro', label: '平台介绍' },
  { id: 'access-modes', label: '两种接入方式' },
  { id: 'modules', label: '核心能力模块' },
  { id: 'path', label: '推荐学习路径' },
  { id: 'capabilities', label: '关键能力' },
  { id: 'support', label: '获取支持' }
]

const iconMap = {
  briefcase: Briefcase,
  'bar-chart-3': BarChart3,
  sparkles: Sparkles,
  'graduation-cap': GraduationCap,
  compass: Compass,
  wand: Wand,
  'scroll-text': ScrollText,
  'cloud-download': CloudDownload,
  'refresh-cw': RefreshCw,
  'grid-2x2': Grid2X2,
  shield: Shield,
  terminal: Terminal,
  lightbulb: Lightbulb,
  info: Info,
  'check-circle-2': CheckCircle2
}

function resolveIcon(name) {
  return iconMap[name] || Info
}

const page = inject('openApiPage', null)
onMounted(() => {
  page?.setSections(sections)
})
</script>

<template>
  <div class="docs-header-row">
    <section id="intro" class="docs-section docs-section-lead">
      <div class="docs-breadcrumb">入门</div>
      <h1 class="docs-title">API 总览</h1>
      <p class="docs-lede">{{ ledeText }}</p>

      <div class="callout callout--tip">
        <span class="callout-icon">
          <component :is="resolveIcon('lightbulb')" :size="18" />
        </span>
        <div class="callout-body">
          <p class="callout-lead"><strong>当前开放的数据能力：</strong></p>
          <ul class="callout-list">
            <li v-for="item in openDataItems" :key="item.title">
              <strong>{{ item.title }}</strong> — {{ item.desc }}
              <a class="callout-link" href="#">查看详情</a>
            </li>
          </ul>
        </div>
      </div>

      <div class="callout callout--info">
        <span class="callout-icon">
          <component :is="resolveIcon('info')" :size="18" />
        </span>
        <div class="callout-body">
          <p>准备好调用了？前往 <router-link class="callout-link" to="/console">API 控制台</router-link> 管理 API Key 与调用审计日志。</p>
        </div>
      </div>
    </section>

    <section id="access-modes" class="docs-section">
      <h2 class="docs-h2">两种接入方式</h2>
      <p class="docs-body">当前仓库分为开放读取接口与管理员控制台接口两类接入方式：</p>

      <div class="compare-table-wrap">
        <table class="compare-table">
          <thead>
            <tr>
              <th></th>
              <th>开放读取接口</th>
              <th>管理员控制台接口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in accessModes" :key="row.label">
              <th>{{ row.label }}</th>
              <td v-if="row.label === '文档'">
                <a class="compare-link" href="#">{{ row.data }}</a>
              </td>
              <td v-else>{{ row.data }}</td>
              <td v-if="row.label === '文档'">
                <a class="compare-link" href="#">{{ row.intel }}</a>
              </td>
              <td v-else>{{ row.intel }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section id="modules" class="docs-section">
      <h2 class="docs-h2">核心能力模块</h2>
      <p class="docs-body">以下模块均对应当前后端真实接口，可独立调用或按场景组合。</p>

      <div class="feature-cards">
        <a
          v-for="mod in coreModules"
          :id="mod.id"
          :key="mod.id"
          class="feature-card"
          href="#"
          @click.prevent
        >
          <div class="feature-card-head">
            <span class="feature-icon">
              <component :is="resolveIcon(mod.icon)" :size="18" />
            </span>
            <span class="feature-card-title">{{ mod.title }}</span>
            <span
              v-if="mod.tag"
              class="feature-card-tag"
              :class="tagClass(mod.tag)"
            >{{ mod.tag }}</span>
          </div>
          <p class="feature-card-desc">{{ mod.desc }}</p>
          <div class="feature-card-meta">
            <code class="meta-path">{{ mod.method }} {{ mod.path }}</code>
            <span class="meta-dot"></span>
            <span>{{ mod.count }}</span>
          </div>
        </a>
      </div>
    </section>

    <section id="path" class="docs-section">
      <h2 class="docs-h2">推荐学习路径</h2>
      <p class="docs-body">按顺序阅读，约 15 分钟完成第一次端到端调用。</p>

      <div class="path-cards">
        <div v-for="step in pathSteps" :key="step.n" class="path-card">
          <span class="path-num">{{ step.n }}</span>
          <div class="path-body">
            <h4>{{ step.title }}</h4>
            <p>{{ step.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section id="capabilities" class="docs-section">
      <h2 class="docs-h2">关键能力</h2>

      <div class="caps">
        <div v-for="cap in keyCapabilities" :key="cap.title" class="cap">
          <div class="cap-head">
            <span class="cap-icon">
              <component :is="resolveIcon(cap.icon)" :size="14" />
            </span>
            <span class="cap-title">{{ cap.title }}</span>
          </div>
          <p class="cap-desc">{{ cap.desc }}</p>
        </div>
      </div>
    </section>

    <section id="support" class="docs-section">
      <h2 class="docs-h2">获取支持</h2>
      <p class="docs-body">{{ supportText }}</p>

      <div class="callout callout--success">
        <span class="callout-icon">
          <component :is="resolveIcon('check-circle-2')" :size="18" />
        </span>
        <div class="callout-body">
          <p><strong>建议先用只读接口联调。</strong> 联调通过后再由管理员创建 API Key，并结合控制台审计日志做验收。</p>
        </div>
      </div>
    </section>
  </div>
</template>
