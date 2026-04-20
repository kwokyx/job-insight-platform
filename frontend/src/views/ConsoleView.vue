<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  KeyRound,
  Plus,
  Copy,
  Ban,
  CheckCircle2,
  BookOpen,
  ArrowLeft,
  AlertTriangle
} from 'lucide-vue-next'
import {
  mockApiKeys,
  mockUsage7d,
  mockQuota
} from './openapi/data.js'

use([CanvasRenderer, LineChart, TitleComponent, TooltipComponent, GridComponent])

const router = useRouter()

// ---- Local state ----
const keys = ref(mockApiKeys.map((k) => ({ ...k })))
const quota = mockQuota
const usage = mockUsage7d

const activeKeyCount = computed(() => keys.value.filter((k) => k.status === 'active').length)
const totalKeyCount = computed(() => keys.value.length)
const todayCalls = computed(() => usage[usage.length - 1]?.calls ?? 0)
const yesterdayCalls = computed(() => usage[usage.length - 2]?.calls ?? 0)
const todayDelta = computed(() => {
  if (!yesterdayCalls.value) return null
  const pct = ((todayCalls.value - yesterdayCalls.value) / yesterdayCalls.value) * 100
  return { pct: Math.round(pct * 10) / 10, dir: pct >= 0 ? 'up' : 'down' }
})
const quotaPercent = computed(() => Math.min(100, Math.round((quota.used / quota.total) * 100)))

// ---- Create-key modal ----
const showCreateModal = ref(false)
const creatingName = ref('')
const creatingScope = ref('read-only')
const revealedKey = ref(null)
const copiedField = ref('')

function openCreateModal() {
  creatingName.value = ''
  creatingScope.value = 'read-only'
  revealedKey.value = null
  showCreateModal.value = true
}
function closeCreateModal() {
  showCreateModal.value = false
  revealedKey.value = null
}
function confirmCreateKey() {
  const name = creatingName.value.trim()
  if (!name) return
  const rand = Math.random().toString(36).slice(2, 14)
  const secret = `jc-live-${rand}${Math.random().toString(36).slice(2, 10)}`
  const id = `ak_${Math.random().toString(36).slice(2, 8)}`
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const newKey = {
    id,
    name,
    prefix: `${secret.slice(0, 14)}…`,
    createdAt: today,
    lastUsedAt: '—',
    status: 'active',
    scope: creatingScope.value
  }
  keys.value.unshift(newKey)
  revealedKey.value = { name, secret, prefix: newKey.prefix }
}

// ---- Revoke-key confirm modal ----
const revokingKey = ref(null)
function revokeKey(id) {
  const k = keys.value.find((x) => x.id === id)
  if (!k) return
  revokingKey.value = k
}
function closeRevokeModal() {
  revokingKey.value = null
}
function confirmRevoke() {
  if (!revokingKey.value) return
  revokingKey.value.status = 'revoked'
  revokingKey.value = null
}

async function copyText(text, fieldKey) {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copiedField.value = fieldKey
    setTimeout(() => {
      if (copiedField.value === fieldKey) copiedField.value = ''
    }, 1500)
  } catch {
    // ignore
  }
}

const usageOption = computed(() => ({
  grid: { left: 44, right: 18, top: 20, bottom: 30, containLabel: false },
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#ffffff',
    borderColor: 'rgba(24,27,35,0.08)',
    borderWidth: 1,
    textStyle: { color: '#181b23', fontSize: 12 },
    padding: [8, 12]
  },
  xAxis: {
    type: 'category',
    data: usage.map((d) => d.date),
    axisLine: { lineStyle: { color: 'rgba(24,27,35,0.08)' } },
    axisTick: { show: false },
    axisLabel: { color: '#727786', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: 'rgba(24,27,35,0.05)' } },
    axisLabel: { color: '#727786', fontSize: 11 }
  },
  series: [
    {
      name: '调用数',
      data: usage.map((d) => d.calls),
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2, color: '#0057c2' },
      itemStyle: { color: '#0057c2' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(0, 87, 194, 0.22)' },
            { offset: 1, color: 'rgba(0, 87, 194, 0)' }
          ]
        }
      }
    }
  ]
}))

function formatNumber(n) {
  return Number(n).toLocaleString('zh-CN')
}
function goToDocs() {
  router.push('/openapi/intro')
}
</script>

<template>
  <div class="console-shell">
    <header class="console-top">
      <div class="console-top-left">
        <button class="console-back" type="button" @click="goToDocs">
          <ArrowLeft :size="14" :stroke-width="1.8" />
          返回文档
        </button>
        <div class="console-title-block">
          <h1 class="console-title">API 控制台</h1>
        </div>
      </div>
      <div class="console-top-right">
        <button class="console-secondary-btn" type="button" @click="goToDocs">
          <BookOpen :size="14" :stroke-width="1.8" />
          查看 API 文档
        </button>
        <button class="console-primary-btn" type="button" @click="openCreateModal">
          <Plus :size="14" :stroke-width="2" />
          创建新 Key
        </button>
      </div>
    </header>

    <section class="console-metrics">
      <div class="metric">
        <span class="metric-label">本月配额</span>
        <span class="metric-value">
          <strong>{{ formatNumber(quota.used) }}</strong>
          <span class="metric-suffix">/ {{ formatNumber(quota.total) }}</span>
        </span>
        <div class="metric-bar">
          <span class="metric-bar-fill" :style="{ width: quotaPercent + '%' }" />
        </div>
        <span class="metric-sub">已用 {{ quotaPercent }}% · {{ quota.resetAt }} 重置</span>
      </div>

      <div class="metric">
        <span class="metric-label">今日调用</span>
        <span class="metric-value">
          <strong>{{ formatNumber(todayCalls) }}</strong>
          <span v-if="todayDelta" :class="`metric-delta metric-delta--${todayDelta.dir}`">
            {{ todayDelta.dir === 'up' ? '↑' : '↓' }} {{ Math.abs(todayDelta.pct) }}%
          </span>
        </span>
        <span class="metric-sub">相比昨日</span>
      </div>

      <div class="metric">
        <span class="metric-label">当前 RPS</span>
        <span class="metric-value">
          <strong>{{ quota.rpsCurrent }}</strong>
          <span class="metric-suffix">/ {{ quota.rpsLimit }} req/s</span>
        </span>
        <span class="metric-sub">超出返回 429</span>
      </div>

      <div class="metric">
        <span class="metric-label">Key 数量</span>
        <span class="metric-value">
          <strong>{{ activeKeyCount }}</strong>
          <span class="metric-suffix">活跃 · {{ totalKeyCount }} 总</span>
        </span>
        <span class="metric-sub">保留 {{ quota.retentionDays }} 天日志</span>
      </div>
    </section>

    <section class="console-panel">
      <header class="console-panel-head">
        <div>
          <h2 class="console-panel-title">API Keys</h2>
          <p class="console-panel-sub">按环境或应用分别创建独立 Key。撤销后立即失效，不可恢复。</p>
        </div>
      </header>

      <div class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>Key</th>
              <th>权限</th>
              <th>创建时间</th>
              <th>最近使用</th>
              <th>状态</th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in keys" :key="k.id" :class="{ 'is-revoked': k.status === 'revoked' }">
              <td class="col-name" data-label="名称">
                <span class="col-name-icon"><KeyRound :size="13" :stroke-width="1.8" /></span>
                {{ k.name }}
              </td>
              <td class="col-prefix" data-label="Key">
                <code>{{ k.prefix }}</code>
                <button
                  class="inline-icon-btn"
                  type="button"
                  :title="copiedField === k.id ? '已复制' : '复制前缀'"
                  @click="copyText(k.prefix, k.id)"
                >
                  <Copy :size="12" :stroke-width="1.7" />
                </button>
              </td>
              <td data-label="权限"><span class="pill pill-muted">{{ k.scope === 'full' ? '全部' : '只读' }}</span></td>
              <td data-label="创建时间">{{ k.createdAt }}</td>
              <td data-label="最近使用">{{ k.lastUsedAt }}</td>
              <td data-label="状态">
                <span class="pill" :class="k.status === 'active' ? 'pill-success' : 'pill-danger'">
                  {{ k.status === 'active' ? '启用' : '已撤销' }}
                </span>
              </td>
              <td class="col-actions" data-label="操作">
                <button
                  v-if="k.status === 'active'"
                  class="row-action danger"
                  type="button"
                  @click="revokeKey(k.id)"
                >
                  <Ban :size="12" :stroke-width="1.7" />
                  撤销
                </button>
                <span v-else class="row-muted">—</span>
              </td>
            </tr>
            <tr v-if="keys.length === 0">
              <td colspan="7" class="console-empty">
                还没有 Key。点击右上角「创建新 Key」开始。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="console-panel">
      <header class="console-panel-head">
        <div>
          <h2 class="console-panel-title">近 7 天用量</h2>
          <p class="console-panel-sub">按自然日统计的调用总数。要按 Key 分组，请联系平台运营升级到 Team 配额。</p>
        </div>
      </header>
      <div class="console-chart-wrap">
        <VChart class="console-chart" :option="usageOption" autoresize />
      </div>
    </section>

    <!-- Create-key modal -->
    <div v-if="showCreateModal" class="console-modal-mask" @click.self="closeCreateModal">
      <div class="console-modal">
        <template v-if="!revealedKey">
          <h3 class="console-modal-title">创建新的 API Key</h3>
          <p class="console-modal-lead">为本 Key 填一个便于辨认的名称，并选择对应的访问权限。</p>

          <label class="console-field">
            <span class="console-field-label">名称</span>
            <input
              v-model="creatingName"
              class="console-input"
              type="text"
              placeholder="如：教研数据看板"
              maxlength="40"
              autofocus
            />
          </label>

          <label class="console-field">
            <span class="console-field-label">权限</span>
            <select v-model="creatingScope" class="console-input">
              <option value="read-only">只读（推荐）</option>
              <option value="full">完整读写</option>
            </select>
          </label>

          <div class="console-modal-actions">
            <button class="console-secondary-btn" type="button" @click="closeCreateModal">取消</button>
            <button
              class="console-primary-btn"
              type="button"
              :disabled="!creatingName.trim()"
              @click="confirmCreateKey"
            >
              创建
            </button>
          </div>
        </template>

        <template v-else>
          <h3 class="console-modal-title">
            <CheckCircle2 :size="18" :stroke-width="2" style="color:#1e8a5b; vertical-align: -3px;" />
            Key 已生成
          </h3>
          <p class="console-modal-lead">
            这是「<strong>{{ revealedKey.name }}</strong>」的完整密钥。关闭本窗口后将无法再次查看，请立即保存到安全的地方。
          </p>

          <div class="console-secret-box">
            <code class="console-secret">{{ revealedKey.secret }}</code>
            <button
              class="inline-icon-btn on-dark"
              type="button"
              :title="copiedField === 'revealed' ? '已复制' : '复制完整 Key'"
              @click="copyText(revealedKey.secret, 'revealed')"
            >
              <Copy :size="14" :stroke-width="1.8" />
            </button>
          </div>

          <p class="console-secret-hint">
            请求时用 HTTPS 头 <code>Authorization: Bearer &lt;your-key&gt;</code> 携带。Key 泄露请回到列表立即撤销并创建新的。
          </p>

          <div class="console-modal-actions">
            <button class="console-primary-btn" type="button" @click="closeCreateModal">我已保存</button>
          </div>
        </template>
      </div>
    </div>

    <!-- Revoke-key confirm modal -->
    <div v-if="revokingKey" class="console-modal-mask" @click.self="closeRevokeModal">
      <div class="console-modal is-danger" role="alertdialog" aria-labelledby="revoke-title">
        <div class="revoke-icon-wrap">
          <AlertTriangle :size="22" :stroke-width="2" />
        </div>
        <h3 id="revoke-title" class="console-modal-title">撤销「{{ revokingKey.name }}」？</h3>
        <p class="console-modal-lead">
          撤销后，依赖此 Key 的应用将立刻收到 <code>401 Unauthorized</code>，且操作<strong>无法恢复</strong>。
          如果只是临时停用，建议先在调用方替换为新 Key，再回来撤销旧的。
        </p>

        <div class="revoke-detail">
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">Key</span>
            <code>{{ revokingKey.prefix }}</code>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">权限</span>
            <span>{{ revokingKey.scope === 'full' ? '完整读写' : '只读' }}</span>
          </div>
          <div class="revoke-detail-row">
            <span class="revoke-detail-label">最近使用</span>
            <span>{{ revokingKey.lastUsedAt }}</span>
          </div>
        </div>

        <div class="console-modal-actions">
          <button class="console-secondary-btn" type="button" @click="closeRevokeModal">取消</button>
          <button class="console-danger-btn" type="button" @click="confirmRevoke">
            <Ban :size="13" :stroke-width="1.9" />
            确认撤销
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.console-shell {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px 48px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ---------- Top header ---------- */
.console-top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 8px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
  padding-bottom: 20px;
}
.console-top-left {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.console-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px 3px 6px;
  border: none;
  background: transparent;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  border-radius: 6px;
  align-self: flex-start;
  transition: background-color 140ms, color 140ms;
}
.console-back:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}
.console-title-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.console-title {
  font-family: var(--font-serif);
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
  line-height: 1.15;
  margin: 0;
}
.console-top-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- Buttons ---------- */
.console-primary-btn,
.console-secondary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 9px;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms, border-color 140ms, color 140ms;
  line-height: 1;
}
.console-primary-btn {
  border: none;
  background: var(--c-accent-primary);
  color: #ffffff;
}
.console-primary-btn:hover {
  background: var(--c-accent-primary-hover);
}
.console-primary-btn:disabled {
  background: #c4cad8;
  cursor: not-allowed;
}
.console-secondary-btn {
  border: 1px solid var(--c-border-glass);
  background: #ffffff;
  color: var(--c-text-secondary);
}
.console-secondary-btn:hover {
  background: var(--c-bg-surface-hover);
  border-color: var(--c-text-faint);
  color: var(--c-text-primary);
}
.inline-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  transition: background-color 140ms, color 140ms, border-color 140ms;
}
.inline-icon-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  border-color: rgba(24, 27, 35, 0.08);
}
.inline-icon-btn.on-dark {
  color: rgba(255, 255, 255, 0.72);
}
.inline-icon-btn.on-dark:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.16);
}

/* ---------- Metric strip ---------- */
.console-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1px;
  background: rgba(24, 27, 35, 0.08);
  border: 1px solid rgba(24, 27, 35, 0.08);
  border-radius: 12px;
  overflow: hidden;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  background: #ffffff;
}
.metric-label {
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.metric-value {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-family: var(--font-serif);
  color: var(--c-text-primary);
  line-height: 1.1;
}
.metric-value strong {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  font-feature-settings: 'tnum' 1;
  font-variant-numeric: tabular-nums;
}
.metric-suffix {
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--c-text-muted);
  font-weight: 500;
}
.metric-delta {
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
}
.metric-delta--up { color: #1e8a5b; }
.metric-delta--down { color: #b23b2e; }
.metric-bar {
  width: 100%;
  height: 3px;
  border-radius: 999px;
  background: var(--c-bg-surface-hover);
  overflow: hidden;
}
.metric-bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--c-accent-primary), var(--c-accent-primary-hover));
  border-radius: 999px;
  transition: width 200ms ease;
}
.metric-sub {
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
}

/* ---------- Panels (card) ---------- */
.console-panel {
  background: #ffffff;
  border: 1px solid rgba(24, 27, 35, 0.08);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(24, 27, 35, 0.03);
}
.console-panel-head {
  padding: 18px 22px 14px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
}
.console-panel-title {
  font-family: var(--font-serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--c-text-primary);
  line-height: 1.25;
  margin: 0 0 2px;
}
.console-panel-sub {
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-muted);
  line-height: 1.5;
  margin: 0;
}

/* ---------- Table ---------- */
.console-table-wrap {
  overflow-x: auto;
}
.console-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-sans);
  font-size: 13px;
}
.console-table thead th {
  padding: 10px 18px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
  background: rgba(24, 27, 35, 0.02);
  border-bottom: 1px solid rgba(24, 27, 35, 0.06);
  text-align: left;
}
.console-table tbody td {
  padding: 12px 18px;
  border-bottom: 1px solid rgba(24, 27, 35, 0.04);
  color: var(--c-text-primary);
  vertical-align: middle;
}
.console-table tbody tr:last-child td { border-bottom: none; }
.console-table tbody tr.is-revoked td { color: var(--c-text-faint); }
.col-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}
.col-name-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: rgba(0, 87, 194, 0.08);
  color: var(--c-accent-primary);
}
.col-prefix {
  display: flex;
  align-items: center;
  gap: 6px;
}
.col-prefix code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 5px;
  background: rgba(24, 27, 35, 0.04);
  color: var(--c-text-primary);
}

.pill {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.pill-muted { background: var(--c-bg-surface-hover); color: var(--c-text-secondary); }
.pill-success { background: rgba(30, 138, 91, 0.12); color: #1e8a5b; }
.pill-danger { background: rgba(178, 59, 46, 0.1); color: #b23b2e; }

.col-actions { text-align: right; }
.row-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid rgba(24, 27, 35, 0.1);
  border-radius: 6px;
  background: #ffffff;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 140ms, color 140ms, background-color 140ms;
}
.row-action.danger:hover {
  color: #b23b2e;
  border-color: #e8b7b0;
  background: #fff5f3;
}
.row-muted { color: var(--c-text-faint); font-size: 13px; }
.console-empty {
  text-align: center;
  padding: 32px 20px;
  color: var(--c-text-muted);
}

/* ---------- Chart ---------- */
.console-chart-wrap {
  padding: 8px 12px 12px;
}
.console-chart {
  width: 100%;
  height: 240px;
}

/* ---------- Modal ---------- */
.console-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 20, 32, 0.42);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.console-modal {
  width: min(100%, 480px);
  background: #ffffff;
  border-radius: 14px;
  padding: 22px 24px 20px;
  box-shadow: 0 24px 64px rgba(15, 20, 32, 0.24);
}
.console-modal-title {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 700;
  color: var(--c-text-primary);
  margin: 0 0 6px;
}
.console-modal-lead {
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--c-text-secondary);
  margin: 0 0 14px;
}
.console-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.console-field-label {
  font-family: var(--font-sans);
  font-size: 11.5px;
  font-weight: 600;
  color: var(--c-text-secondary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.console-input {
  padding: 8px 11px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  border-radius: 7px;
  background: #ffffff;
  font-family: var(--font-sans);
  font-size: 13px;
  color: var(--c-text-primary);
  outline: none;
  transition: border-color 140ms, box-shadow 140ms;
}
.console-input:focus {
  border-color: var(--c-accent-primary);
  box-shadow: 0 0 0 3px rgba(0, 87, 194, 0.12);
}
.console-modal-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
.console-secret-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 11px;
  border: 1px solid rgba(24, 27, 35, 0.12);
  border-radius: 9px;
  background: #0f1420;
}
.console-secret {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: #e6edf3;
  word-break: break-all;
  line-height: 1.5;
}
.console-secret-hint {
  font-family: var(--font-sans);
  font-size: 11.5px;
  color: var(--c-text-muted);
  line-height: 1.55;
  margin: 10px 0 0;
}
.console-secret-hint code {
  font-family: var(--font-mono);
  font-size: 11.5px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--c-bg-surface-hover);
}

/* ---------- Danger modal (revoke) ---------- */
.console-modal.is-danger {
  width: min(100%, 440px);
  padding: 22px 24px 18px;
}
.revoke-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(178, 59, 46, 0.1);
  color: #b23b2e;
  margin-bottom: 12px;
}
.console-modal.is-danger .console-modal-lead code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(178, 59, 46, 0.08);
  color: #b23b2e;
}
.console-modal.is-danger .console-modal-lead strong {
  color: #b23b2e;
  font-weight: 700;
}
.revoke-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(24, 27, 35, 0.03);
  margin-bottom: 14px;
}
.revoke-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-family: var(--font-sans);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}
.revoke-detail-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--c-text-muted);
}
.revoke-detail-row code {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 5px;
  background: rgba(24, 27, 35, 0.05);
  color: var(--c-text-primary);
}
.console-danger-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 9px;
  background: #b23b2e;
  color: #ffffff;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background-color 140ms;
}
.console-danger-btn:hover {
  background: #9b3126;
}

/* ---------- Responsive ---------- */
@media (max-width: 900px) {
  .console-shell {
    padding: 0 14px 32px;
  }
  .console-panel-head {
    padding: 16px 18px 12px;
  }
  .console-table thead th,
  .console-table tbody td {
    padding: 10px 14px;
  }
}

@media (max-width: 720px) {
  .console-top {
    align-items: flex-start;
    padding-bottom: 14px;
  }
  .console-title {
    font-size: 22px;
  }
  .console-top-right {
    width: 100%;
  }
  .console-top-right .console-primary-btn,
  .console-top-right .console-secondary-btn {
    flex: 1 1 auto;
    justify-content: center;
    padding: 10px 14px;
  }

  /* Metric strip: single column instead of squeezing 4 cards into a
     200px-minmax grid that leaves ugly half-cards on 375px screens. */
  .console-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .metric {
    padding: 12px 14px;
  }
  .metric-value strong {
    font-size: 18px;
  }

  /* Card-ified table: each row stacks into a labeled card. Data-label
     attributes on <td> become the key; the cell content is the value.
     Uses CSS only so the DOM stays a proper <table> for a11y. */
  .console-table,
  .console-table thead,
  .console-table tbody,
  .console-table tr,
  .console-table th,
  .console-table td {
    display: block;
  }
  .console-table thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
  .console-table tbody tr {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 6px 12px;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(24, 27, 35, 0.06);
  }
  .console-table tbody tr:last-child { border-bottom: none; }
  .console-table tbody td {
    padding: 0;
    border: none;
    display: flex;
    align-items: center;
    min-width: 0;
  }
  .console-table tbody td::before {
    content: attr(data-label);
    flex: 0 0 72px;
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--c-text-muted);
  }
  /* Name row = card header: full width, larger, no label */
  .console-table tbody td.col-name {
    grid-column: 1 / -1;
    font-size: 14px;
    font-weight: 600;
    padding-bottom: 4px;
    border-bottom: 1px dashed rgba(24, 27, 35, 0.06);
    margin-bottom: 4px;
  }
  .console-table tbody td.col-name::before { display: none; }
  .console-table tbody td.col-prefix code {
    font-size: 11.5px;
  }
  /* Actions: span full width, right-aligned button */
  .console-table tbody td.col-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
    padding-top: 4px;
  }
  .console-table tbody td.col-actions::before { display: none; }
  .row-action {
    padding: 6px 12px;
    font-size: 12.5px;
  }
}

@media (max-width: 480px) {
  .console-metrics {
    grid-template-columns: 1fr;
  }
  .console-modal {
    padding: 18px 18px 16px;
  }
  .console-modal-title {
    font-size: 16px;
  }
  .console-modal.is-danger {
    padding: 18px 18px 16px;
  }
}
</style>
