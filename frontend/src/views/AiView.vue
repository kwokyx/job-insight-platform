<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  deleteAiConversation,
  fetchAiConversation,
  fetchAiConversations,
  normalizeError,
  runAiAgentQuery,
  streamAiChat
} from '../api'
import { useAuthStore } from '../store/auth'
import { marked } from 'marked'
import { Bot, BrainCircuit, History, LoaderCircle, MoreHorizontal, RefreshCw, Send, Sparkles, Trash2, User, WandSparkles } from 'lucide-vue-next'

const authStore = useAuthStore()

const bootstrapping = ref(false)
const loading = ref(false)
const historyLoading = ref(false)
const deletingSessionId = ref('')
const openSessionMenuId = ref('')
const error = ref('')
const chatHistoryRef = ref(null)
const currentSessionId = ref('')
const conversations = ref([])
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

const headerStatus = computed(() => {
  if (!authStore.isLoggedIn) {
    return '登录后开启 AI 对话'
  }

  return '开始对话，或切换到智能代理模式。'
})

const chatPanelHint = computed(() => {
  if (!authStore.isLoggedIn) {
    return '登录后即可开始新对话。'
  }

  return '消息区内部滚动，输入区固定在底部。'
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

function formatConversationTime(value) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
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

async function loadConversations() {
  conversations.value = await fetchAiConversations(authStore.token)
}

async function bootstrap() {
  if (!authStore.token) {
    conversations.value = []
    messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
    currentSessionId.value = ''
    return
  }

  bootstrapping.value = true
  error.value = ''

  try {
    await loadConversations()
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

  openSessionMenuId.value = ''
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

      await loadConversations()
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
          await loadConversations()
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
  openSessionMenuId.value = ''
  error.value = ''
}

function toggleSessionMenu(sessionId) {
  openSessionMenuId.value = openSessionMenuId.value === sessionId ? '' : sessionId
}

async function handleDeleteConversation(sessionId) {
  if (!authStore.token || !sessionId || deletingSessionId.value) {
    return
  }

  if (typeof window !== 'undefined' && !window.confirm('删除这个对话？')) {
    return
  }

  deletingSessionId.value = sessionId
  openSessionMenuId.value = ''
  error.value = ''

  try {
    await deleteAiConversation(authStore.token, sessionId)

    if (currentSessionId.value === sessionId) {
      resetConversation()
    }

    await loadConversations()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    deletingSessionId.value = ''
  }
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
    <header class="ai-topbar workspace-page-head">
      <div class="workspace-page-row ai-header-row">
        <div class="workspace-page-copy">
          <h1 class="workspace-page-title">AI 助手</h1>
          <p class="workspace-page-subtitle">{{ headerStatus }}</p>
        </div>

        <div class="ai-header-tools">
          <div class="mode-switch topbar-mode-switch">
            <button class="mode-btn" :class="{ active: aiMode === 'chat' }" @click="aiMode = 'chat'">
              <Sparkles :size="14" />
              对话
            </button>
            <button class="mode-btn" :class="{ active: aiMode === 'agent' }" @click="aiMode = 'agent'">
              <WandSparkles :size="14" />
              智能代理
            </button>
            <select v-if="aiMode === 'agent'" v-model="selectedTool" class="tool-select topbar-tool-select">
              <option v-for="option in toolOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>

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
          <div class="panel-head session-head">
            <div>
              <h2 class="panel-title"><History :size="15" /> 会话</h2>
              <p>从历史对话继续。</p>
            </div>
          </div>

          <div class="session-body">
            <div v-if="!authStore.token" class="empty-state large">
              请先登录后再使用 AI 对话和智能代理模式。
            </div>

            <div
              v-for="item in conversations"
              :key="item.sessionId"
              class="session-entry"
              :class="{ active: item.sessionId === currentSessionId }"
            >
              <button
                class="session-item"
                :disabled="historyLoading || deletingSessionId === item.sessionId"
                @click="openConversation(item.sessionId)"
              >
                <div class="session-item-top">
                  <span class="session-name">{{ item.title || item.contextType || item.sessionId }}</span>
                  <span class="session-time">{{ formatConversationTime(item.updatedAt || item.createdAt) }}</span>
                </div>
                <span class="session-preview">{{ item.contextType || '普通对话' }}</span>
              </button>

              <div class="session-menu-wrap">
                <button
                  class="session-delete"
                  :disabled="historyLoading || deletingSessionId === item.sessionId"
                  @click.stop="toggleSessionMenu(item.sessionId)"
                >
                  <LoaderCircle v-if="deletingSessionId === item.sessionId" :size="14" class="spin" />
                  <MoreHorizontal v-else :size="14" />
                </button>

                <div
                  v-if="openSessionMenuId === item.sessionId && deletingSessionId !== item.sessionId"
                  class="session-menu"
                >
                  <button class="session-menu-item danger" @click.stop="handleDeleteConversation(item.sessionId)">
                    <Trash2 :size="14" />
                    删除对话
                  </button>
                </div>
              </div>
            </div>

            <div v-if="authStore.token && !historyLoading && !conversations.length" class="empty-state">
              还没有历史会话，先从右侧发起第一轮对话。
            </div>
          </div>
        </aside>

        <article class="section-panel chat-panel">
          <div class="panel-head chat-head">
            <div>
              <h2 class="panel-title"><Bot :size="15" /> 对话</h2>
              <p>{{ chatPanelHint }}</p>
            </div>
          </div>

          <div ref="chatHistoryRef" class="chat-history">
            <div class="chat-history-inner">
              <article
                v-for="(item, index) in messages"
                :key="`${item.role}-${index}`"
                class="chat-message"
                :class="item.role"
              >
                <div class="avatar" :class="item.role">
                  <Bot v-if="item.role === 'assistant'" :size="16" />
                  <User v-else :size="16" />
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
          </div>

          <div class="chat-controls">
            <div class="chat-rail-tip">
              <span>输入框支持 Ctrl + Enter 发送。</span>
              <span v-if="bootstrapping">正在同步会话列表...</span>
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

            <div class="composer">
              <textarea
                v-model="message"
                class="composer-input"
                rows="4"
                :disabled="loading || !authStore.token"
                placeholder="给 AI 助手发送消息..."
                @keydown.ctrl.enter.prevent="sendMessage()"
              />
              <GlowButton variant="primary" :loading="loading" @click="sendMessage()">
                <Send :size="14" />
                发送
              </GlowButton>
            </div>
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

.workspace-shell,
.surface {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-card-soft);
}

.ai-topbar {
  gap: 0;
  padding: 0;
}

.ai-header-row {
  align-items: center;
}

.ai-header-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.workspace-shell {
  padding: 0;
  overflow: hidden;
  height: clamp(620px, calc(100dvh - 188px), 820px);
}

.section-panel,
.session-body {
  display: flex;
  flex-direction: column;
}
.panel-title,
.panel-head h2,
.panel-head h3 {
  margin: 0;
}

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

.status-strip,
.composer,
.panel-head {
  display: flex;
  gap: 12px;
}

.composer,
.panel-head {
  align-items: center;
  justify-content: space-between;
}

.session-item,
.empty-state,
.tool-select,
.glass-input,
.chat-rail-tip {
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 14px;
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
  grid-template-columns: minmax(256px, 286px) minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
  min-height: 0;
  height: 100%;
}

.session-panel {
  gap: 16px;
  height: 100%;
  min-height: 0;
  padding: 14px;
  border-radius: 0;
  background: rgba(248, 250, 255, 0.78);
  border-right: 1px solid rgba(193, 198, 215, 0.46);
  overflow: hidden;
}

.chat-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 14px;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: 18px 22px;
  border-radius: 0;
  background: rgba(255, 255, 255, 0.92);
  overflow: hidden;
}

.session-head,
.chat-head {
  padding: 4px 4px 0;
}

.session-body {
  gap: 8px;
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding-right: 2px;
}

.empty-state {
  padding: 16px;
  background: rgba(255, 255, 255, 0.54);
}

.empty-state.large {
  min-height: 124px;
  display: flex;
  align-items: center;
}

.session-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: stretch;
  padding: 4px;
  border-radius: 16px;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.session-entry.active {
  background: rgba(30, 117, 255, 0.08);
}

.session-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 12px 12px 11px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.session-entry:hover .session-item,
.session-entry.active .session-item {
  background: rgba(255, 255, 255, 0.74);
  border-color: rgba(193, 198, 215, 0.5);
}

.session-item-top {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-name {
  font-weight: 700;
  word-break: break-word;
  color: var(--c-text-primary);
}

.session-time {
  flex: none;
  color: var(--c-text-muted);
  font-size: 11px;
  white-space: nowrap;
}

.session-preview {
  font-size: 12px;
}

.session-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--c-text-muted);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.session-delete:hover {
  background: rgba(30, 117, 255, 0.08);
  color: var(--c-accent-primary);
}

.session-menu-wrap {
  position: relative;
  align-self: center;
}

.session-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 10;
  min-width: 124px;
  padding: 6px;
  border: 1px solid rgba(193, 198, 215, 0.58);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
}

.session-menu-item {
  display: inline-flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  color: var(--c-text-primary);
  font-size: 12.5px;
  text-align: left;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.session-menu-item:hover {
  background: rgba(30, 117, 255, 0.08);
}

.session-menu-item.danger {
  color: #b42318;
}

.mode-btn,
.quick-chip,
.tool-select {
  background: rgba(255, 255, 255, 0.76);
  color: var(--c-text-primary);
}

.chat-controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid rgba(193, 198, 215, 0.42);
}

.mode-switch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.topbar-mode-switch {
  justify-content: flex-end;
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

.tool-select {
  flex: 0 0 220px;
  max-width: 100%;
}

.topbar-tool-select {
  min-width: 220px;
}

.tool-select {
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
  min-height: 0;
  overflow: auto;
  padding: 8px 0 10px;
}

.chat-history-inner {
  display: flex;
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  flex-direction: column;
  gap: 18px;
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
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: rgba(247, 249, 255, 0.96);
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
  max-width: min(84%, 760px);
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
  padding: 0;
  border-radius: 0;
  background: transparent;
  line-height: 1.6;
  overflow-wrap: anywhere;
  border: none;
}

.chat-message.user .bubble {
  padding: 14px 16px;
  border-radius: 18px;
  background: #eef4ff;
  border: 1px solid rgba(30, 117, 255, 0.14);
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
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-chip {
  padding: 9px 12px;
  border: 1px solid rgba(193, 198, 215, 0.5);
  border-radius: 999px;
  text-align: center;
  font-size: 12.5px;
  line-height: 1.3;
  font-weight: 700;
}

.composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 12px;
  border: 1px solid rgba(193, 198, 215, 0.52);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.composer-input {
  width: 100%;
  min-height: 96px;
  max-height: 180px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--c-text-primary);
  resize: none;
}

.composer-input:focus {
  outline: none;
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

@media (max-width: 760px) {
  .session-panel,
  .chat-panel {
    padding: 16px;
    border-radius: 16px;
  }

  .workspace-shell {
    height: auto;
    min-height: 720px;
  }

  .ai-header-tools,
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

  .quick-actions {
    width: 100%;
  }

  .bubble-wrap {
    max-width: 100%;
  }
}

@media (max-width: 560px) {
  .composer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
