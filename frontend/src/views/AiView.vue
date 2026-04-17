<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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
import { Bot, LoaderCircle, MoreHorizontal, RefreshCw, Send, Sparkles, Trash2, WandSparkles } from 'lucide-vue-next'

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

const showWelcomeState = computed(() => {
  return !currentSessionId.value &&
    messages.value.length === 1 &&
    messages.value[0]?.role === 'assistant' &&
    messages.value[0]?.content === defaultAssistantMessage
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
  <div class="ai-page">
    <section class="chat-shell">
      <aside class="chat-sidebar">
        <div class="sidebar-top">
          <button class="new-chat-button" :disabled="!authStore.token" @click="resetConversation">
            <Sparkles :size="15" />
            新建对话
          </button>
        </div>

        <div class="sidebar-body">
          <div v-if="!authStore.token" class="sidebar-empty">
            登录后查看历史会话并开始对话。
          </div>

          <div
            v-for="item in conversations"
            :key="item.sessionId"
            class="conversation-row"
            :class="{ active: item.sessionId === currentSessionId }"
          >
            <button
              class="conversation-card"
              :disabled="historyLoading || deletingSessionId === item.sessionId"
              @click="openConversation(item.sessionId)"
            >
              <span class="conversation-title">{{ item.title || item.contextType || item.sessionId }}</span>
              <span class="conversation-snippet">{{ item.contextType || '普通对话' }}</span>
              <span class="conversation-time">{{ formatConversationTime(item.updatedAt || item.createdAt) }}</span>
            </button>

            <div class="session-menu-wrap">
              <button
                class="conversation-menu-trigger"
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

          <div v-if="authStore.token && !historyLoading && !conversations.length" class="sidebar-empty">
            还没有历史会话。
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <header class="chat-topbar">
          <div class="topbar-left">
            <span class="chat-title">AI 助手</span>
            <div class="mode-switch">
              <button class="mode-btn" :class="{ active: aiMode === 'chat' }" @click="aiMode = 'chat'">
                <Sparkles :size="14" />
                对话
              </button>
              <button class="mode-btn" :class="{ active: aiMode === 'agent' }" @click="aiMode = 'agent'">
                <WandSparkles :size="14" />
                智能代理
              </button>
              <select v-if="aiMode === 'agent'" v-model="selectedTool" class="mode-select">
                <option v-for="option in toolOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
          </div>

          <button class="topbar-icon-btn" @click="bootstrap">
            <RefreshCw :size="15" />
          </button>
        </header>

        <div ref="chatHistoryRef" class="chat-scroll">
          <div v-if="error" class="inline-banner error-banner">{{ error }}</div>

          <div v-if="showWelcomeState" class="welcome-stage">
            <div class="welcome-copy">
              <p class="welcome-kicker">{{ authStore.isLoggedIn ? '已就绪' : '需要登录' }}</p>
              <h1>今天想聊什么？</h1>
              <p>
                {{ authStore.isLoggedIn
                  ? '可以直接问岗位、薪资、技能与报告，也可以切换到智能代理模式。'
                  : '登录后即可开始对话。' }}
              </p>
            </div>

            <div class="starter-grid">
              <button
                v-for="question in quickQuestions"
                :key="question.label"
                class="starter-card"
                :disabled="loading || !authStore.token"
                @click="sendMessage(question.prompt)"
              >
                <strong>{{ question.label }}</strong>
                <span>{{ question.prompt }}</span>
              </button>
            </div>
          </div>

          <div v-else class="message-column">
            <article
              v-for="(item, index) in messages"
              :key="`${item.role}-${index}`"
              class="message-row"
              :class="item.role"
            >
              <div v-if="item.role === 'assistant'" class="message-avatar assistant">
                <Bot :size="16" />
              </div>

              <div class="message-card" :class="item.role">
                <div
                  v-if="item.role === 'assistant' && loading && index === messages.length - 1 && !item.content.trim()"
                  class="thinking-placeholder"
                >
                  <LoaderCircle :size="14" class="spin" />
                  正在思考...
                </div>
                <div v-else v-html="renderMarkdown(item.content)"></div>
              </div>
            </article>
          </div>
        </div>

        <footer class="composer-area">
          <div class="composer-shell">
            <textarea
              v-model="message"
              class="composer-input"
              rows="4"
              :disabled="loading || !authStore.token"
              placeholder="给 AI 助手发送消息..."
              @keydown.ctrl.enter.prevent="sendMessage()"
            />

            <div class="composer-footer">
              <div class="composer-meta">
                <span>Ctrl + Enter 发送</span>
                <span v-if="bootstrapping">正在同步会话列表...</span>
              </div>

              <button
                class="send-button"
                :disabled="loading || !authStore.token || !message.trim()"
                @click="sendMessage()"
              >
                <Send :size="15" />
              </button>
            </div>
          </div>
        </footer>
      </main>
    </section>
  </div>
</template>

<style scoped>
.ai-page {
  height: calc(100dvh - 112px);
  min-height: 720px;
}

.chat-shell {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(193, 198, 215, 0.52);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.06);
}

.chat-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid rgba(193, 198, 215, 0.42);
  background: #f7f7f8;
}

.sidebar-top {
  padding: 14px;
  border-bottom: 1px solid rgba(193, 198, 215, 0.4);
}

.new-chat-button {
  display: inline-flex;
  width: 100%;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 11px 14px;
  border: 1px solid rgba(193, 198, 215, 0.56);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.94);
  color: var(--c-text-primary);
  font-size: 13px;
  font-weight: 700;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.new-chat-button:hover:not(:disabled) {
  border-color: rgba(30, 117, 255, 0.25);
  background: #fff;
  transform: translateY(-1px);
}

.new-chat-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.sidebar-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
  padding: 10px;
}

.sidebar-empty {
  padding: 14px;
  border-radius: 14px;
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.conversation-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
  align-items: stretch;
  padding: 3px;
  border-radius: 14px;
}

.conversation-row.active {
  background: rgba(30, 117, 255, 0.08);
}

.conversation-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  padding: 10px 11px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.conversation-row:hover .conversation-card,
.conversation-row.active .conversation-card {
  background: rgba(255, 255, 255, 0.92);
  border-color: rgba(193, 198, 215, 0.48);
}

.conversation-title {
  color: var(--c-text-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  word-break: break-word;
}

.conversation-snippet,
.conversation-time {
  color: var(--c-text-secondary);
  font-size: 11.5px;
  line-height: 1.35;
}

.session-menu-wrap {
  position: relative;
  align-self: center;
}

.conversation-menu-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 10px;
  color: var(--c-text-muted);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.conversation-menu-trigger:hover {
  background: rgba(30, 117, 255, 0.08);
  color: var(--c-accent-primary);
}

.session-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 10;
  min-width: 126px;
  padding: 6px;
  border: 1px solid rgba(193, 198, 215, 0.56);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.12);
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

.chat-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  background: #ffffff;
}

.chat-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 22px;
  border-bottom: 1px solid rgba(193, 198, 215, 0.42);
}

.topbar-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.chat-title {
  color: var(--c-text-primary);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.mode-switch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.mode-btn,
.mode-select {
  border: 1px solid rgba(193, 198, 215, 0.5);
  background: #fff;
  color: var(--c-text-primary);
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
}

.mode-btn.active {
  border-color: rgba(30, 117, 255, 0.24);
  background: rgba(30, 117, 255, 0.08);
  color: var(--c-accent-primary);
}

.mode-select {
  min-width: 180px;
  padding: 9px 12px;
  border-radius: 999px;
  font-size: 12.5px;
}

.topbar-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 999px;
  color: var(--c-text-secondary);
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.topbar-icon-btn:hover {
  border-color: rgba(30, 117, 255, 0.24);
  background: rgba(30, 117, 255, 0.08);
  color: var(--c-accent-primary);
}

.chat-scroll {
  min-height: 0;
  overflow: auto;
  padding: 24px 0 18px;
}

.inline-banner {
  width: min(880px, calc(100% - 32px));
  margin: 0 auto 16px;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 13px;
}

.error-banner {
  color: #b42318;
  background: rgba(254, 226, 226, 0.84);
}

.welcome-stage {
  display: flex;
  width: min(920px, calc(100% - 40px));
  margin: 0 auto;
  flex-direction: column;
  gap: 28px;
  padding: 44px 0 18px;
}

.welcome-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  text-align: center;
}

.welcome-kicker {
  color: var(--c-accent-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.welcome-copy h1 {
  margin: 0;
  color: var(--c-text-primary);
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.05;
  letter-spacing: -0.05em;
}

.welcome-copy p {
  max-width: 48ch;
  color: var(--c-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.starter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.starter-card {
  display: flex;
  min-height: 112px;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid rgba(193, 198, 215, 0.48);
  border-radius: 18px;
  background: rgba(248, 249, 252, 0.94);
  text-align: left;
  transition:
    border-color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.starter-card:hover:not(:disabled) {
  border-color: rgba(30, 117, 255, 0.24);
  background: #ffffff;
  transform: translateY(-1px);
}

.starter-card:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.starter-card strong {
  color: var(--c-text-primary);
  font-size: 14px;
  line-height: 1.35;
}

.starter-card span {
  color: var(--c-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.message-column {
  display: flex;
  width: min(880px, calc(100% - 40px));
  margin: 0 auto;
  flex-direction: column;
  gap: 28px;
}

.message-row {
  display: flex;
  gap: 14px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(193, 198, 215, 0.48);
  border-radius: 10px;
  background: rgba(246, 248, 255, 0.96);
  color: var(--c-accent-primary);
  flex: none;
}

.message-card {
  min-width: 0;
  color: var(--c-text-primary);
  font-size: 15px;
  line-height: 1.75;
}

.message-card.assistant {
  flex: 1;
  max-width: 100%;
}

.message-card.user {
  max-width: min(76%, 680px);
  padding: 13px 16px;
  border: 1px solid rgba(193, 198, 215, 0.46);
  border-radius: 20px;
  background: #f2f4f7;
}

.message-card :deep(p),
.message-card :deep(ul),
.message-card :deep(ol),
.message-card :deep(pre),
.message-card :deep(blockquote) {
  margin-top: 0;
  margin-bottom: 0.95em;
}

.message-card :deep(p:last-child),
.message-card :deep(ul:last-child),
.message-card :deep(ol:last-child),
.message-card :deep(pre:last-child),
.message-card :deep(blockquote:last-child) {
  margin-bottom: 0;
}

.thinking-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-secondary);
  font-size: 14px;
}

.composer-area {
  padding: 0 22px 22px;
}

.composer-shell {
  width: min(880px, 100%);
  margin: 0 auto;
  padding: 14px 16px 12px;
  border: 1px solid rgba(193, 198, 215, 0.54);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.composer-input {
  width: 100%;
  min-height: 104px;
  max-height: 220px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--c-text-primary);
  font-size: 15px;
  line-height: 1.7;
  resize: none;
}

.composer-input:focus {
  outline: none;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 10px;
}

.composer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  color: var(--c-text-muted);
  font-size: 12px;
}

.send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  transition:
    transform var(--duration-fast) var(--ease-out),
    opacity var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.send-button:hover:not(:disabled) {
  background: #1f2937;
  transform: translateY(-1px);
}

.send-button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1080px) {
  .chat-shell {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  .starter-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .ai-page {
    height: auto;
    min-height: 0;
  }

  .chat-shell {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat-sidebar {
    max-height: 260px;
    border-right: none;
    border-bottom: 1px solid rgba(193, 198, 215, 0.42);
  }

  .chat-main {
    min-height: 780px;
  }
}

@media (max-width: 640px) {
  .chat-topbar,
  .composer-area {
    padding-left: 16px;
    padding-right: 16px;
  }

  .topbar-left,
  .composer-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .mode-select {
    min-width: 0;
    width: 100%;
  }

  .message-column,
  .welcome-stage,
  .composer-shell,
  .inline-banner {
    width: calc(100% - 24px);
  }

  .message-card.user {
    max-width: 100%;
  }
}
</style>
