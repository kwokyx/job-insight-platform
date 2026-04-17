<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  fetchAiConversation,
  fetchAiConversations,
  fetchAiQuota,
  normalizeError,
  runAiAgentQuery,
  streamAiChat
} from '../api'
import { useAuthStore } from '../store/auth'
import { marked } from 'marked'
import { Bot, History, RefreshCw, Send, Sparkles, User, WandSparkles } from 'lucide-vue-next'

const authStore = useAuthStore()

const bootstrapping = ref(false)
const loading = ref(false)
const historyLoading = ref(false)
const error = ref('')
const chatHistoryRef = ref(null)
const currentSessionId = ref('')
const conversations = ref([])
const quota = ref({ used: 0, limit: 0, remaining: 0 })
const message = ref('')
const aiMode = ref('chat')
const selectedTool = ref('skill_gap')

const defaultAssistantMessage = '你可以询问职位、薪资、技能、报告，也可以切换到智能代理模式获取带工具支持的回答。'

const messages = ref([{ role: 'assistant', content: defaultAssistantMessage }])

const toolOptions = [
  { value: 'market_overview', label: '市场概览' },
  { value: 'profile_snapshot', label: '个人画像' },
  { value: 'salary_insight', label: '薪资洞察' },
  { value: 'skill_gap', label: '技能差距' },
  { value: 'job_match', label: '岗位匹配' },
  { value: 'career_path', label: '职业路径' },
  { value: 'auto', label: '自动选择' }
]

const quickQuestions = [
  '我现在掌握 Java 和 Spring Boot，适合哪些后端岗位？',
  '帮我看看上海数据分析岗位的大致薪资区间。',
  '把我当前技能和高级后端工程师岗位要求做个对比。',
  '帮我制定一个从 Java 开发转向架构师的 90 天计划。'
]

const quotaText = computed(() => {
  if (!quota.value.limit) {
    return '额度信息暂不可用'
  }
  return `已使用 ${quota.value.used} / ${quota.value.limit}，剩余 ${quota.value.remaining}`
})

const conversationCountText = computed(() => {
  if (!authStore.token) {
    return '未登录'
  }
  return `${conversations.value.length} 个会话`
})

const modeLabel = computed(() => (aiMode.value === 'agent' ? '智能代理' : '对话'))

function sanitizeRenderedHtml(html) {
  if (typeof window === 'undefined') {
    return html
  }

  const doc = new DOMParser().parseFromString(html || '', 'text/html')
  doc.querySelectorAll('script, iframe, object, embed, link, meta').forEach((node) => node.remove())
  doc.body.querySelectorAll('*').forEach((node) => {
    Array.from(node.attributes).forEach((attr) => {
      if (/^on/i.test(attr.name)) {
        node.removeAttribute(attr.name)
      }

      if ((attr.name === 'href' || attr.name === 'src') && /^javascript:/i.test(attr.value)) {
        node.removeAttribute(attr.name)
      }
    })
  })
  return doc.body.innerHTML
}

function renderMarkdown(text) {
  return sanitizeRenderedHtml(marked.parse(text || '', { breaks: true }))
}

function sanitizeAssistantContent(text) {
  if (!text) return ''

  let cleaned = String(text)
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<\/?think>/gi, '')
    .replace(/\r/g, '')
    .trim()

  const boilerplatePatterns = [
    /^the user\b.*$/i,
    /^i need to\b.*$/i,
    /^i should\b.*$/i,
    /^let me\b.*$/i,
    /^based on the prompt\b.*$/i,
    /^looking at\b.*$/i
  ]

  if (boilerplatePatterns.some((pattern) => pattern.test(cleaned))) {
    const blocks = cleaned.split(/\n\s*\n/).map((item) => item.trim()).filter(Boolean)
    cleaned = blocks[blocks.length - 1] || cleaned
  }

  return cleaned.replace(/^(okay|ok|alright|sure|so)\b[\s,:-]*/i, '').trim()
}

function summarizeToolValue(value) {
  if (Array.isArray(value)) {
    return `${value.length} 项`
  }

  if (value && typeof value === 'object') {
    return `${Object.keys(value).length} 个字段`
  }

  if (typeof value === 'string') {
    return value.length > 48 ? `${value.slice(0, 48)}...` : value
  }

  if (value === null || value === undefined || value === '') {
    return '已返回'
  }

  return String(value)
}

function formatAgentToolResult(toolResult) {
  if (!toolResult || typeof toolResult !== 'object') {
    return ''
  }

  const entries = Object.entries(toolResult)
    .slice(0, 6)
    .map(([key, value]) => `- ${key}: ${summarizeToolValue(value)}`)

  if (!entries.length) {
    return ''
  }

  return `\n\n### 工具结果概览\n${entries.join('\n')}`
}

async function scrollToBottom() {
  await nextTick()
  if (chatHistoryRef.value) {
    chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
  }
}

async function loadQuota() {
  quota.value = await fetchAiQuota(authStore.token)
}

async function loadConversations() {
  conversations.value = await fetchAiConversations(authStore.token)
}

async function bootstrap() {
  if (!authStore.token) {
    conversations.value = []
    quota.value = { used: 0, limit: 0, remaining: 0 }
    messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
    currentSessionId.value = ''
    return
  }

  bootstrapping.value = true
  error.value = ''

  try {
    await Promise.all([loadQuota(), loadConversations()])
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    bootstrapping.value = false
  }
}

async function openConversation(sessionId) {
  if (!sessionId || historyLoading.value) {
    return
  }

  historyLoading.value = true
  error.value = ''

  try {
    const payload = await fetchAiConversation(authStore.token, sessionId)
    currentSessionId.value = payload.conversation?.sessionId || sessionId
    messages.value = (payload.messages || []).map((item) => ({
      role: item.role,
      content: item.role === 'assistant' ? sanitizeAssistantContent(item.content) : item.content
    }))

    if (!messages.value.length) {
      messages.value = [{ role: 'assistant', content: '当前会话还没有历史消息。' }]
    }

    await scrollToBottom()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    historyLoading.value = false
  }
}

async function sendMessage(preset = '') {
  const content = (preset || message.value).trim()
  if (!content || loading.value || !authStore.token) {
    return
  }

  error.value = ''
  messages.value.push({ role: 'user', content })
  message.value = ''
  loading.value = true
  await scrollToBottom()

  const aiIndex = messages.value.push({ role: 'assistant', content: '' }) - 1

  if (aiMode.value === 'agent') {
    try {
      messages.value[aiIndex].content = '智能代理处理中...'
      const agentResult = await runAiAgentQuery(authStore.token, {
        message: content,
        tool: selectedTool.value === 'auto' ? undefined : selectedTool.value
      })

      const answer = sanitizeAssistantContent(agentResult.answer || '未返回回答。')
      messages.value[aiIndex].content = agentResult.toolResult
        ? `${answer}${formatAgentToolResult(agentResult.toolResult)}`
        : answer

      await Promise.all([loadQuota(), loadConversations()])
    } catch (e) {
      messages.value[aiIndex].content = `智能代理请求失败：${normalizeError(e)}`
    } finally {
      loading.value = false
      await scrollToBottom()
    }
    return
  }

  try {
    await streamAiChat(
      authStore.token,
      {
        message: content,
        sessionId: currentSessionId.value || undefined
      },
      {
        onSession: (data) => {
          if (data?.sessionId) {
            currentSessionId.value = data.sessionId
          }
        },
        onMessage: (data) => {
          const text = sanitizeAssistantContent(data.content || data.raw || '')
          if (text) {
            messages.value[aiIndex].content += text
            scrollToBottom()
          }
        },
        onDone: async () => {
          await Promise.all([loadQuota(), loadConversations()])
        },
        onError: (data) => {
          error.value = data?.message || 'AI 服务异常'
        }
      }
    )

    if (!messages.value[aiIndex].content.trim()) {
      messages.value[aiIndex].content = 'AI 返回了空内容。建议先重试一次，仍无结果再切换到智能代理模式。'
    }
  } catch (e) {
    messages.value[aiIndex].content = `AI 请求失败：${normalizeError(e)}`
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function resetConversation() {
  currentSessionId.value = ''
  messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
  error.value = ''
}

watch(
  () => authStore.token,
  (token) => {
    if (token) {
      bootstrap()
    } else {
      resetConversation()
      bootstrap()
    }
  }
)

onMounted(() => {
  bootstrap()
})
</script>

<template>
  <div class="ai-page page-shell">
    <section class="workspace-hero surface">
      <div class="hero-copy">
        <h1>AI 工作台</h1>
        <p>会话、代理和追问都集中在这里。</p>
        <div class="hero-actions">
          <GlowButton variant="ghost" @click="bootstrap">
            <RefreshCw :size="14" />
            刷新会话
          </GlowButton>
          <div class="hero-note">
            <Bot :size="14" />
            <span>{{ modeLabel }}模式已就绪，{{ conversationCountText }}</span>
          </div>
        </div>
      </div>

      <div class="hero-aside">
        <div class="hero-metrics">
          <div class="metric-tile">
            <span>剩余额度</span>
            <strong>{{ authStore.isLoggedIn ? quota.remaining : '--' }}</strong>
          </div>
          <div class="metric-tile">
            <span>会话数量</span>
            <strong>{{ authStore.isLoggedIn ? conversations.length : '--' }}</strong>
          </div>
          <div class="metric-tile">
            <span>当前模式</span>
            <strong>{{ modeLabel }}</strong>
          </div>
        </div>
        <div class="status-strip">
          <Sparkles :size="16" />
          <div>
            <strong>{{ quotaText }}</strong>
            <p>左侧选会话，右侧继续提问。</p>
          </div>
        </div>
      </div>
    </section>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>

    <section class="workspace-grid">
      <aside class="surface section-panel session-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title"><History :size="15" /> 会话记录</h2>
            <p>选择后继续提问。</p>
          </div>
          <GlowButton variant="ghost" @click="bootstrap">
            <RefreshCw :size="14" />
            刷新
          </GlowButton>
        </div>

        <div class="session-body">
          <div class="quota-grid">
            <div class="quota-tile">
              <span class="meta-label">已使用</span>
              <strong>{{ quota.used }}</strong>
            </div>
            <div class="quota-tile">
              <span class="meta-label">剩余</span>
              <strong>{{ quota.remaining }}</strong>
            </div>
          </div>

          <div v-if="!authStore.token" class="empty-state large">
            请先登录后再使用 AI 对话和智能代理模式。
          </div>

          <button
            v-for="item in conversations"
            :key="item.sessionId"
            class="session-item"
            :disabled="historyLoading"
            @click="openConversation(item.sessionId)"
          >
            <span class="session-name">{{ item.title || item.contextType || item.sessionId }}</span>
            <small>{{ item.updatedAt || item.createdAt }}</small>
          </button>
        </div>
      </aside>

      <article class="surface section-panel chat-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title"><Bot :size="15" /> 对话区</h2>
            <p>直接对话或切换代理模式。</p>
          </div>
          <GlowButton variant="ghost" @click="resetConversation">新建对话</GlowButton>
        </div>

        <div class="mode-switch">
          <button class="mode-btn" :class="{ active: aiMode === 'chat' }" @click="aiMode = 'chat'">
            <Sparkles :size="14" />
            对话
          </button>
          <button class="mode-btn" :class="{ active: aiMode === 'agent' }" @click="aiMode = 'agent'">
            <WandSparkles :size="14" />
            智能代理
          </button>
          <select v-if="aiMode === 'agent'" v-model="selectedTool" class="tool-select">
            <option v-for="option in toolOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div class="chat-rail-tip">
          <span>输入框支持 Ctrl + Enter 发送。</span>
          <span v-if="bootstrapping">正在同步会话和额度信息...</span>
        </div>

        <div ref="chatHistoryRef" class="chat-history">
          <article
            v-for="(item, index) in messages"
            :key="`${item.role}-${index}`"
            class="chat-message"
            :class="item.role"
          >
            <div class="avatar">
              <Bot v-if="item.role === 'assistant'" :size="18" />
              <User v-else :size="18" />
            </div>
            <div class="bubble" v-html="renderMarkdown(item.content)"></div>
          </article>
        </div>

        <div class="quick-actions">
          <button
            v-for="question in quickQuestions"
            :key="question"
            class="quick-chip"
            :disabled="loading || !authStore.token"
            @click="sendMessage(question)"
          >
            {{ question }}
          </button>
        </div>

        <div class="composer">
          <textarea
            v-model="message"
            class="glass-input composer-input"
            rows="4"
            :disabled="loading || !authStore.token"
            placeholder="输入你想咨询的职位、薪资、技能、报告等内容..."
            @keydown.ctrl.enter.prevent="sendMessage()"
          />
          <GlowButton variant="primary" :loading="loading" @click="sendMessage()">
            <Send :size="14" />
            发送
          </GlowButton>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.workspace-hero,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.workspace-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 20px;
  padding: 20px;
  border-radius: 16px;
}

.hero-copy,
.hero-aside,
.section-panel,
.session-body {
  display: flex;
  flex-direction: column;
}

.hero-copy {
  gap: 8px;
}

.hero-copy h1,
.panel-title,
.panel-head h2,
.panel-head h3 {
  margin: 0;
}

.hero-copy h1 {
  font-size: clamp(22px, 1.95vw, 27px);
  line-height: 1.12;
  letter-spacing: -0.05em;
}

.panel-title,
.panel-head h2 {
  font-size: clamp(18px, 1.35vw, 21px);
  line-height: 1.18;
  letter-spacing: -0.03em;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hero-copy p,
.panel-head p,
.status-strip p,
.chat-rail-tip,
.session-item small,
.empty-state,
.meta-label {
  font-size: 0.93rem;
  line-height: 1.55;
  color: var(--c-text-secondary);
}

.hero-actions,
.status-strip,
.mode-switch,
.composer,
.panel-head {
  display: flex;
  gap: 12px;
}

.hero-actions,
.mode-switch,
.composer,
.panel-head {
  align-items: center;
  justify-content: space-between;
}

.hero-note,
.status-strip,
.quota-tile,
.session-item,
.empty-state,
.tool-select,
.glass-input,
.chat-rail-tip {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
}

.hero-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  color: var(--c-text-secondary);
  background: rgba(255, 255, 255, 0.54);
}

.hero-aside {
  gap: 12px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 13px 14px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.56);
}

.metric-tile span {
  color: var(--c-text-secondary);
  font-size: 13px;
}

.metric-tile strong {
  font-size: 18px;
  letter-spacing: -0.03em;
}

.status-strip {
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.58);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(248px, 286px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.session-panel {
  gap: 16px;
  min-height: 0;
  position: sticky;
  top: 16px;
  padding: 20px;
  border-radius: 16px;
}

.chat-panel {
  gap: 16px;
  min-width: 0;
  padding: 20px;
  border-radius: 16px;
}

.session-body {
  gap: 12px;
}

.quota-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quota-tile {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.56);
}

.quota-tile strong {
  font-size: 24px;
  letter-spacing: -0.04em;
}

.empty-state {
  padding: 16px;
  background: rgba(255, 255, 255, 0.38);
}

.empty-state.large {
  min-height: 124px;
  display: flex;
  align-items: center;
}

.session-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.5);
  text-align: left;
}

.session-name {
  font-weight: 600;
  word-break: break-word;
}

.mode-btn,
.quick-chip,
.tool-select,
.glass-input {
  background: rgba(255, 255, 255, 0.72);
  color: var(--c-text-primary);
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 999px;
}

.mode-btn.active {
  background: rgba(30, 117, 255, 0.12);
  border-color: rgba(30, 117, 255, 0.28);
}

.tool-select,
.glass-input {
  width: 100%;
  padding: 12px 14px;
}

.chat-rail-tip {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.42);
  font-size: 13px;
}

.chat-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: clamp(340px, 52vh, 560px);
  overflow: auto;
  padding: 4px 2px;
}

.chat-message {
  display: flex;
  gap: 12px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  flex: 0 0 auto;
}

.bubble {
  max-width: min(80%, 840px);
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.22);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.chat-message.user .bubble {
  background: rgba(30, 117, 255, 0.14);
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-chip {
  padding: 10px 12px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 14px;
  text-align: left;
}

.composer {
  align-items: flex-end;
}

.composer-input {
  min-height: 96px;
  resize: vertical;
}

.status-banner {
  padding: 12px 14px;
  border-radius: 14px;
  color: #fecaca;
}

.error-banner {
  background: rgba(127, 29, 29, 0.16);
}

.panel-head {
  align-items: flex-start;
}

@media (max-width: 1080px) {
  .workspace-hero,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .session-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .workspace-hero,
  .session-panel,
  .chat-panel {
    padding: 18px;
    border-radius: 16px;
  }

  .hero-metrics,
  .quota-grid {
    grid-template-columns: 1fr;
  }

  .mode-switch,
  .composer,
  .chat-rail-tip {
    flex-direction: column;
    align-items: flex-start;
  }

  .chat-history {
    height: clamp(300px, 48vh, 420px);
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
