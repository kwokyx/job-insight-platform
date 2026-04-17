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
import { Bot, BrainCircuit, History, LoaderCircle, RefreshCw, Send, Sparkles, User, WandSparkles } from 'lucide-vue-next'

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

const defaultAssistantMessage = '可以直接询问职位、薪资、技能、报告，也可以切换到智能代理模式。'

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
  {
    label: '后端岗位',
    prompt: '我现在掌握 Java 和 Spring Boot，适合哪些后端岗位？'
  },
  {
    label: '上海薪资',
    prompt: '帮我看看上海数据分析岗位的大致薪资区间。'
  },
  {
    label: '技能差距',
    prompt: '把我当前技能和高级后端工程师岗位要求做个对比。'
  },
  {
    label: '90 天计划',
    prompt: '帮我制定一个从 Java 开发转向架构师的 90 天计划。'
  }
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
const headerStatus = computed(() => {
  if (!authStore.isLoggedIn) {
    return '登录后开启 AI 对话'
  }

  return `${modeLabel.value}已就绪，${conversationCountText.value}`
})

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
    <header class="ai-topbar surface">
      <div class="topbar-copy">
        <div class="topbar-title-row">
          <h1>AI 助手</h1>
          <span class="topbar-badge">{{ modeLabel }}</span>
        </div>
        <p>{{ headerStatus }}</p>
      </div>

      <div class="topbar-side">
        <div class="topbar-metrics">
          <div class="metric-pill">
            <span>剩余额度</span>
            <strong>{{ authStore.isLoggedIn ? quota.remaining : '--' }}</strong>
          </div>
          <div class="metric-pill">
            <span>会话数</span>
            <strong>{{ authStore.isLoggedIn ? conversations.length : '--' }}</strong>
          </div>
        </div>

        <div class="topbar-actions">
          <GlowButton variant="ghost" @click="bootstrap">
            <RefreshCw :size="14" />
            刷新
          </GlowButton>
          <GlowButton variant="ghost" @click="resetConversation">新建对话</GlowButton>
        </div>
      </div>
    </header>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>

    <section class="workspace-shell surface">
      <div class="workspace-grid">
      <aside class="section-panel session-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title"><History :size="15" /> 会话记录</h2>
            <p>从最近会话继续。</p>
          </div>
        </div>

        <div class="session-body">
          <div v-if="!authStore.token" class="empty-state large">
            请先登录后再使用 AI 对话和智能代理模式。
          </div>

          <button
            v-for="item in conversations"
            :key="item.sessionId"
            class="session-item"
            :class="{ active: item.sessionId === currentSessionId }"
            :disabled="historyLoading"
            @click="openConversation(item.sessionId)"
          >
            <span class="session-name">{{ item.title || item.contextType || item.sessionId }}</span>
            <span class="session-preview">{{ item.contextType || '普通对话' }}</span>
            <small>{{ item.updatedAt || item.createdAt }}</small>
          </button>

          <div v-if="authStore.token && !historyLoading && !conversations.length" class="empty-state">
            还没有历史会话，先从右侧发起第一轮对话。
          </div>
        </div>
      </aside>

      <article class="section-panel chat-panel">
        <div class="panel-head">
          <div>
            <h2 class="panel-title"><Bot :size="15" /> 对话</h2>
            <p>{{ quotaText }}</p>
          </div>
        </div>

        <div class="chat-controls">
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

          <div class="quick-actions">
            <button
              v-for="question in quickQuestions"
              :key="question.label"
              class="quick-chip"
              :disabled="loading || !authStore.token"
              @click="sendMessage(question.prompt)"
            >
              {{ question.label }}
            </button>
          </div>
        </div>

        <div ref="chatHistoryRef" class="chat-history">
          <article
            v-for="(item, index) in messages"
            :key="`${item.role}-${index}`"
            class="chat-message"
            :class="item.role"
          >
            <div class="avatar" :class="item.role">
              <Bot v-if="item.role === 'assistant'" :size="18" />
              <User v-else :size="18" />
            </div>
            <div class="bubble-wrap">
              <div class="bubble-meta" :class="item.role">
                <span class="bubble-author">
                  <BrainCircuit v-if="item.role === 'assistant'" :size="13" />
                  {{ item.role === 'assistant' ? 'AI 助手' : '你' }}
                </span>
                <span
                  v-if="item.role === 'assistant' && loading && index === messages.length - 1"
                  class="bubble-thinking"
                >
                  <LoaderCircle :size="13" class="spin" />
                  思考中
                </span>
              </div>
              <div class="bubble">
                <div
                  v-if="item.role === 'assistant' && loading && index === messages.length - 1 && !item.content.trim()"
                  class="thinking-placeholder"
                >
                  <Sparkles :size="14" />
                  正在组织回答...
                </div>
                <div v-else v-html="renderMarkdown(item.content)"></div>
              </div>
            </div>
          </article>
        </div>
      </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-topbar,
.workspace-shell,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.ai-topbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;
  border-radius: 16px;
}

.workspace-shell {
  padding: 0;
  overflow: hidden;
  min-height: clamp(560px, calc(100vh - 220px), 720px);
}

.topbar-copy,
.topbar-side,
.section-panel,
.session-body {
  display: flex;
  flex-direction: column;
}

.topbar-copy {
  gap: 6px;
}

.topbar-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.topbar-copy h1,
.panel-title,
.panel-head h2,
.panel-head h3 {
  margin: 0;
}

.topbar-copy h1 {
  font-size: clamp(20px, 1.7vw, 25px);
  line-height: 1.12;
  letter-spacing: -0.05em;
}

.topbar-badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(30, 117, 255, 0.1);
  color: var(--c-accent-primary);
  font-size: 12px;
  font-weight: 700;
}

.topbar-copy p,
.panel-head p,
.status-strip p,
.chat-rail-tip,
.session-item small,
.empty-state,
.meta-label,
.session-preview {
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--c-text-secondary);
}

.topbar-side {
  gap: 10px;
  align-items: flex-end;
}

.topbar-metrics,
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-strip,
.mode-switch,
.composer,
.panel-head {
  display: flex;
  gap: 12px;
}

.mode-switch,
.composer,
.panel-head {
  align-items: center;
  justify-content: space-between;
}

.metric-pill,
.session-item,
.empty-state,
.tool-select,
.glass-input,
.chat-rail-tip {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 14px;
}

.metric-pill {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  min-width: 110px;
  background: rgba(255, 255, 255, 0.56);
}

.metric-pill span {
  color: var(--c-text-secondary);
  font-size: 12px;
}

.metric-pill strong {
  font-size: 16px;
  letter-spacing: -0.02em;
}

.panel-title,
.panel-head h2 {
  font-size: clamp(17px, 1.2vw, 20px);
  line-height: 1.18;
  letter-spacing: -0.03em;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-accent-primary);
}

.panel-title :deep(svg) {
  color: inherit;
  flex: none;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(214px, 236px) minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
  min-height: inherit;
}

.session-panel {
  gap: 16px;
  min-height: 0;
  padding: 16px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.5);
  border-right: 1px solid rgba(193, 198, 215, 0.46);
}

.chat-panel {
  gap: 16px;
  min-width: 0;
  padding: 16px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.74);
}

.session-body {
  gap: 12px;
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding-right: 2px;
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
  gap: 4px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.46);
  text-align: left;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.session-item.active {
  background: rgba(30, 117, 255, 0.1);
  border-color: rgba(30, 117, 255, 0.24);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.session-item:hover {
  background: rgba(30, 117, 255, 0.05);
  border-color: rgba(30, 117, 255, 0.18);
  box-shadow: 0 8px 16px rgba(30, 117, 255, 0.06);
}

.session-name {
  font-weight: 700;
  word-break: break-word;
  color: var(--c-text-primary);
}

.session-preview {
  font-size: 12px;
}

.mode-btn,
.quick-chip,
.tool-select,
.glass-input {
  background: rgba(255, 255, 255, 0.72);
  color: var(--c-text-primary);
}

.chat-controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(193, 198, 215, 0.42);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.5);
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

.mode-switch {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.tool-select {
  flex: 0 0 220px;
  max-width: 100%;
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
  padding: 0;
  border: none;
  background: transparent;
  font-size: 12.5px;
}

.chat-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  min-height: 280px;
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
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(193, 198, 215, 0.46);
  flex: 0 0 auto;
}

.avatar.assistant {
  color: var(--c-accent-primary);
  background: rgba(30, 117, 255, 0.08);
}

.avatar.user {
  color: var(--c-text-secondary);
}

.bubble-wrap {
  display: flex;
  min-width: 0;
  max-width: min(82%, 840px);
  flex-direction: column;
  gap: 6px;
}

.bubble-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.bubble-meta.user {
  flex-direction: row-reverse;
}

.bubble-author,
.bubble-thinking {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.bubble-thinking {
  color: var(--c-accent-primary);
}

.bubble {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  line-height: 1.6;
  overflow-wrap: anywhere;
  border: 1px solid rgba(193, 198, 215, 0.42);
}

.chat-message.user .bubble {
  background: rgba(30, 117, 255, 0.1);
  border-color: rgba(30, 117, 255, 0.16);
}

.thinking-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-accent-primary);
  font-size: 13px;
  font-weight: 700;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.quick-chip {
  padding: 9px 10px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 12px;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.3;
  font-weight: 700;
}

.composer {
  align-items: flex-end;
}

.composer-input {
  min-height: 72px;
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

.spin {
  animation: spin 1s linear infinite;
}

@media (max-width: 1240px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .session-panel {
    border-right: none;
    border-bottom: 1px solid rgba(193, 198, 215, 0.46);
  }
}

@media (max-width: 1080px) {
  .ai-topbar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .ai-topbar,
  .session-panel,
  .chat-panel {
    padding: 16px;
    border-radius: 16px;
  }

  .workspace-shell {
    min-height: auto;
  }

  .topbar-side,
  .mode-switch,
  .composer,
  .chat-rail-tip {
    flex-direction: column;
    align-items: flex-start;
  }

  .chat-controls {
    padding: 12px;
  }

  .tool-select {
    flex-basis: auto;
  }

  .chat-history {
    min-height: 300px;
  }

  .quick-actions {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bubble-wrap {
    max-width: 100%;
  }
}

@media (max-width: 560px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
