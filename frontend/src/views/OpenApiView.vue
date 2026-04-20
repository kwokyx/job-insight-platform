<script setup>
import { computed, onMounted, ref } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  createAdminApiKey,
  fetchAdminApiKeys,
  fetchAdminApiLogs,
  fetchOpenApiCapabilities,
  fetchOpenApiMeta,
  fetchOpenApiSubscriptionMeta,
  normalizeError,
  toggleAdminApiKey
} from '../api'
import { useAuthStore } from '../store/auth'
import { useToast } from '../composables/useToast'
import { BookKey, FileCode2, LockKeyhole, Radar, ShieldCheck, Webhook, Activity, RefreshCw } from 'lucide-vue-next'

const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(false)
const keyLoading = ref(false)
const logLoading = ref(false)
const meta = ref({})
const capabilities = ref({})
const subscriptionMeta = ref({})
const apiKeys = ref([])
const apiLogs = ref([])
const tenantScopeExamples = ['public', 'city:Shanghai', 'industry:AI', 'source:boss', 'major:软件工程']
const permissionOptions = [
  { value: 'basic', label: '基础字段集' },
  { value: 'extended', label: '扩展字段集' }
]
const fieldOptions = [
  'id',
  'title',
  'companyName',
  'city',
  'industryName',
  'education',
  'experience',
  'salaryText',
  'publishDate',
  'jobLabels',
  'jobBenefits',
  'sourceSite',
  'companySize',
  'companyFinance'
]

const logPager = ref({
  page: 1,
  pageSize: 10,
  total: 0
})

const form = ref({
  keyName: 'Campus Governance Key',
  rateLimitQps: 10,
  dailyQuota: 1000,
  permissionProfile: 'basic',
  tenantScope: 'public',
  allowedJobFields: ['id', 'title', 'companyName', 'city', 'industryName', 'salaryText', 'publishDate']
})

const topSummary = computed(() => ({
  activeKeys: apiKeys.value.filter((item) => item.isActive === 1).length,
  totalKeys: apiKeys.value.length,
  totalLogs: logPager.value.total,
  authModes: Array.isArray(meta.value.authModes) ? meta.value.authModes.length : 0
}))

async function loadOpenApiMeta() {
  meta.value = await fetchOpenApiMeta()
  capabilities.value = await fetchOpenApiCapabilities()
  subscriptionMeta.value = await fetchOpenApiSubscriptionMeta()
}

async function loadApiKeys() {
  keyLoading.value = true
  try {
    apiKeys.value = await fetchAdminApiKeys(authStore.token)
  } catch (e) {
    error(normalizeError(e))
  } finally {
    keyLoading.value = false
  }
}

async function loadApiLogs(page = logPager.value.page) {
  logLoading.value = true
  try {
    const result = await fetchAdminApiLogs(authStore.token, {
      page,
      pageSize: logPager.value.pageSize
    })
    apiLogs.value = result.data
    logPager.value.page = result.page
    logPager.value.pageSize = result.pageSize
    logPager.value.total = result.total
  } catch (e) {
    error(normalizeError(e))
  } finally {
    logLoading.value = false
  }
}

async function handleCreateKey() {
  loading.value = true
  try {
    await createAdminApiKey(authStore.token, form.value)
    success('API Key 已创建')
    await loadApiKeys()
  } catch (e) {
    error(normalizeError(e))
  } finally {
    loading.value = false
  }
}

async function handleToggleKey(item) {
  try {
    await toggleAdminApiKey(authStore.token, item.id, item.isActive !== 1)
    success(item.isActive === 1 ? 'API Key 已停用' : 'API Key 已启用')
    await loadApiKeys()
  } catch (e) {
    error(normalizeError(e))
  }
}

function toggleField(field) {
  const hasField = form.value.allowedJobFields.includes(field)
  if (hasField) {
    form.value.allowedJobFields = form.value.allowedJobFields.filter((item) => item !== field)
  } else {
    form.value.allowedJobFields = [...form.value.allowedJobFields, field]
  }
}

onMounted(async () => {
  await loadOpenApiMeta()
  await loadApiKeys()
  await loadApiLogs(1)
})
</script>

<template>
  <div class="openapi-page page-shell">
    <section class="page-intro glass-panel">
      <div class="page-intro-main">
        <span class="page-eyebrow">管理员视角</span>
        <h1 class="page-intro-title">开放平台与能力输出中心</h1>
        <p class="page-intro-text">
          这里集中管理开放 API 凭证、字段权限、租户范围、订阅契约与调用审计。当前页面已经能支撑平台对外接口的基础治理，不再只是演示型接口展示页。
        </p>
      </div>
      <div class="page-intro-meta">
        <div class="intro-metric">
          <span class="intro-metric-label">启用凭证</span>
          <span class="intro-metric-value">{{ topSummary.activeKeys }}</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">调用日志</span>
          <span class="intro-metric-value">{{ topSummary.totalLogs }}</span>
        </div>
        <div class="intro-metric">
          <span class="intro-metric-label">鉴权模式</span>
          <span class="intro-metric-value">{{ topSummary.authModes }}</span>
        </div>
      </div>
    </section>

    <section class="grid three-col">
      <PremiumCard title="创建 API Key" glowColor="primary">
        <div class="form-stack">
          <label class="field">
            <span><BookKey :size="14" /> 名称</span>
            <input v-model="form.keyName" class="glass-input" placeholder="例如：Governance Data API" />
          </label>

          <div class="inline-grid">
            <label class="field">
              <span><LockKeyhole :size="14" /> QPS</span>
              <input v-model.number="form.rateLimitQps" type="number" min="1" max="200" class="glass-input" />
            </label>
            <label class="field">
              <span><ShieldCheck :size="14" /> 日配额</span>
              <input v-model.number="form.dailyQuota" type="number" min="100" max="100000" class="glass-input" />
            </label>
          </div>

          <div class="inline-grid">
            <label class="field">
              <span><FileCode2 :size="14" /> 权限档位</span>
              <select v-model="form.permissionProfile" class="glass-input">
                <option v-for="item in permissionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
            </label>
            <label class="field">
              <span><Webhook :size="14" /> 租户范围</span>
              <input v-model="form.tenantScope" class="glass-input" placeholder="public / city:Shanghai / industry:AI" />
            </label>
          </div>

          <div class="tenant-examples">
            <span class="muted">范围示例</span>
            <div class="field-chip-group">
              <button
                v-for="scope in tenantScopeExamples"
                :key="scope"
                type="button"
                class="field-chip"
                @click="form.tenantScope = scope"
              >
                {{ scope }}
              </button>
            </div>
          </div>

          <div class="field">
            <span><Radar :size="14" /> 可见岗位字段</span>
            <div class="field-chip-group">
              <button
                v-for="field in fieldOptions"
                :key="field"
                type="button"
                class="field-chip"
                :class="{ active: form.allowedJobFields.includes(field) }"
                @click="toggleField(field)"
              >
                {{ field }}
              </button>
            </div>
          </div>

          <GlowButton variant="primary" :loading="loading" @click="handleCreateKey">创建凭证</GlowButton>
        </div>
      </PremiumCard>

      <PremiumCard title="平台治理元数据" glowColor="secondary">
        <div class="meta-list">
          <div class="meta-item">
            <span>版本</span>
            <strong>{{ meta.apiVersion || 'v1' }}</strong>
          </div>
          <div class="meta-item">
            <span>租户模式</span>
            <strong>{{ meta.tenantMode || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>字段权限</span>
            <strong>{{ meta.fieldPermission || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>SLA 等级</span>
            <strong>{{ meta.slaClass || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>审计对象</span>
            <strong>{{ meta.audit || '-' }}</strong>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="能力清单" glowColor="teal">
        <div class="capability-list">
          <div v-for="resource in capabilities.resources || []" :key="resource.code" class="capability-item">
            <Activity :size="18" />
            <div>
              <strong>{{ resource.code }}</strong>
              <p>过滤条件：{{ (resource.filters || []).join(' / ') || '无' }}</p>
            </div>
          </div>
          <EmptyState
            v-if="!(capabilities.resources || []).length"
            icon="activity"
            title="暂无能力资源"
            description="当前环境还没有返回开放能力清单。"
          />
        </div>
      </PremiumCard>
    </section>

    <section class="grid two-col">
      <PremiumCard title="订阅与推送契约" glowColor="secondary">
        <div class="meta-list">
          <div class="meta-item">
            <span>交付模式</span>
            <strong>{{ (subscriptionMeta.deliveryModes || []).join(' / ') || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>签名方式</span>
            <strong>{{ subscriptionMeta.signing || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>重试策略</span>
            <strong>{{ subscriptionMeta.recommendedRetryPolicy || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>治理文档</span>
            <strong>{{ subscriptionMeta.documentation || '-' }}</strong>
          </div>
        </div>
      </PremiumCard>

      <PremiumCard title="事件目录" glowColor="teal">
        <div class="capability-list">
          <div v-for="eventName in subscriptionMeta.supportedEvents || []" :key="eventName" class="capability-item">
            <Webhook :size="18" />
            <div>
              <strong>{{ eventName }}</strong>
              <p>适用于行业快照、报告发布和岗位增量同步等场景。</p>
            </div>
          </div>
          <EmptyState
            v-if="!(subscriptionMeta.supportedEvents || []).length"
            icon="webhook"
            title="暂无订阅事件"
            description="当前环境尚未返回订阅事件目录。"
          />
        </div>
      </PremiumCard>
    </section>

    <section class="grid two-col">
      <PremiumCard title="凭证列表" glowColor="primary">
        <template v-if="apiKeys.length">
          <div class="table-list">
            <div v-for="item in apiKeys" :key="item.id" class="table-item">
              <div class="table-main">
                <strong>{{ item.keyName }}</strong>
                <p>{{ item.apiKey }}</p>
                <span class="muted">
                  QPS {{ item.rateLimitQps }} / 日配额 {{ item.dailyQuota }} / {{ item.permissions || '未配置权限' }}
                </span>
              </div>
              <div class="table-actions">
                <span class="status-chip" :class="{ off: item.isActive !== 1 }">
                  {{ item.isActive === 1 ? '启用中' : '已停用' }}
                </span>
                <GlowButton variant="ghost" @click="handleToggleKey(item)">
                  {{ item.isActive === 1 ? '停用' : '启用' }}
                </GlowButton>
              </div>
            </div>
          </div>
        </template>
        <EmptyState
          v-else-if="!keyLoading"
          icon="shield"
          title="暂无 API Key"
          description="先创建一个开放平台凭证，再向外部分发。"
        />
      </PremiumCard>

      <PremiumCard title="调用审计日志" glowColor="teal">
        <div class="card-toolbar">
          <span class="muted">最近 {{ apiLogs.length }} 条 / 总计 {{ logPager.total }} 条</span>
          <GlowButton variant="ghost" @click="loadApiLogs(logPager.page)">
            <RefreshCw :size="14" /> 刷新
          </GlowButton>
        </div>

        <template v-if="apiLogs.length">
          <div class="log-list">
            <div v-for="item in apiLogs" :key="item.id" class="log-item">
              <div class="log-head">
                <strong>{{ item.method }} {{ item.endpoint }}</strong>
                <span>{{ item.responseCode }} / {{ item.responseTime }}ms</span>
              </div>
              <p class="muted">requestId={{ item.requestId || '-' }} / apiKeyId={{ item.apiKeyId }}</p>
              <p class="muted">IP={{ item.ipAddress || '-' }} / {{ item.createdAt || '-' }}</p>
            </div>
          </div>
          <div class="pager">
            <GlowButton variant="ghost" :disabled="logPager.page <= 1" @click="loadApiLogs(logPager.page - 1)">上一页</GlowButton>
            <span>第 {{ logPager.page }} 页</span>
            <GlowButton
              variant="ghost"
              :disabled="logPager.page * logPager.pageSize >= logPager.total"
              @click="loadApiLogs(logPager.page + 1)"
            >
              下一页
            </GlowButton>
          </div>
        </template>
        <EmptyState
          v-else-if="!logLoading"
          icon="activity"
          title="暂无审计日志"
          description="当前环境还没有 API 调用记录，外部系统开始调用后会在这里展示。"
        />
      </PremiumCard>
    </section>
  </div>
</template>

<style scoped>
.openapi-page,
.form-stack,
.capability-list,
.meta-list,
.table-list,
.log-list {
  display: grid;
  gap: 20px;
}

.grid {
  display: grid;
  gap: 24px;
}

.three-col {
  grid-template-columns: 1.2fr 0.8fr 1fr;
}

.two-col {
  grid-template-columns: 1fr 1fr;
}

.inline-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field,
.tenant-examples {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field span,
.muted {
  color: var(--c-text-secondary);
}

.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.field-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.field-chip {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-secondary);
  border-radius: 999px;
  padding: 8px 12px;
  cursor: pointer;
}

.field-chip.active {
  color: var(--c-text-primary);
  background: rgba(30, 117, 255, 0.14);
  border-color: rgba(30, 117, 255, 0.36);
}

.capability-item,
.table-item,
.log-item,
.meta-item {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
}

.capability-item {
  display: grid;
  grid-template-columns: 20px 1fr;
  gap: 12px;
}

.capability-item p {
  margin-top: 6px;
  color: var(--c-text-secondary);
}

.meta-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.table-item,
.log-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.table-main,
.table-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.table-main p {
  word-break: break-all;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.14);
  color: #a7f3d0;
}

.status-chip.off {
  background: rgba(239, 68, 68, 0.12);
  color: #fecaca;
}

.log-item {
  flex-direction: column;
}

.log-head,
.card-toolbar,
.pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1200px) {
  .three-col,
  .two-col {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .inline-grid,
  .log-head,
  .card-toolbar,
  .pager,
  .table-item {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
