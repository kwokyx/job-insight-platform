<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import {
  BarChart3,
  Bot,
  BrainCircuit,
  Briefcase,
  History,
  LoaderCircle,
  MoreHorizontal,
  RefreshCw,
  ScrollText,
  SendHorizontal,
  Sparkles,
  Trash2,
  User,
  WandSparkles
} from 'lucide-vue-next'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

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

const homeSuggestions = [
  {
    label: '岗位匹配',
    desc: '根据已有技能快速判断最适配的岗位方向。',
    prompt: '根据我现在掌握的 Java、Spring Boot、MySQL 能力，推荐更适合的岗位方向。',
    icon: Briefcase
  },
  {
    label: '市场趋势',
    desc: '提取城市、行业和薪资变化的关键信号。',
    prompt: '帮我分析上海后端岗位近阶段需求和薪资区间变化。',
    icon: BarChart3
  },
  {
    label: '技能差距',
    desc: '对照目标岗位要求找出当前缺口。',
    prompt: '对比高级后端工程师岗位要求，告诉我还缺哪些关键技能。',
    icon: Sparkles
  },
  {
    label: '汇报输出',
    desc: '把分析结果收敛成适合周报或汇报的结论。',
    prompt: '把当前就业市场概览整理成适合周报汇报的 5 条结论。',
    icon: ScrollText
  }
]

const showHomeState = computed(() => !currentSessionId.value && messages.value.length === 1)

const headerStatus = computed(() => {
  if (!authStore.isLoggedIn) {
    return '登录后开启 AI 对话与智能代理。'
  }

  return aiMode.value === 'agent'
    ? '当前为智能代理模式，可自动调用工具。'
    : '当前为对话模式，适合持续追问和迭代分析。'
})

const activeConversationTitle = computed(() => {
  if (!currentSessionId.value) {
    return '新建任务'
  }

  const activeConversation = conversations.value.find((item) => item.sessionId === currentSessionId.value)
  return activeConversation?.title || activeConversation?.contextType || currentSessionId.value
})

const sendLabel = computed(() => {
  if (loading.value) {
    return aiMode.value === 'agent' ? '执行中' : '发送中'
  }

  return aiMode.value === 'agent' ? '运行代理' : '发送'
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
  message.value = ''
}

function toggleSessionMenu(sessionId) {
  openSessionMenuId.value = openSessionMenuId.value === sessionId ? '' : sessionId
}

function applyRouteDraft(rawDraft) {
  const draft = Array.isArray(rawDraft) ? rawDraft[0] : rawDraft

  if (typeof draft !== 'string' || !draft.trim()) {
    return
  }

  message.value = draft.trim()

  const nextQuery = { ...route.query }
  delete nextQuery.draft

  router.replace({ path: route.path, query: nextQuery }).catch(() => {})
}

function useSuggestion(prompt) {
  message.value = prompt
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

watch(
  () => route.query.draft,
  (draft) => {
    applyRouteDraft(draft)
  },
  { immediate: true }
)

onMounted(() => {
  bootstrap()
})
</script>

<template>
  <div class="ai-page">
    <header class="ai-header">
      <div class="ai-header-copy">
        <span class="ai-header-kicker">Agent Workspace</span>
        <h2>AI 助手</h2>
        <p>{{ headerStatus }}</p>
      </div>

      <div class="ai-header-actions">
        <button class="header-btn" type="button" @click="bootstrap">
          <RefreshCw :size="14" />
          刷新
        </button>
        <button class="header-btn primary" type="button" @click="resetConversation">
          <Sparkles :size="14" />
          新建任务
        </button>
      </div>
    </header>

    <div v-if="error" class="status-banner error-banner">{{ error }}</div>

    <section class="ai-shell">
      <aside class="history-panel">
        <div class="history-panel-head">
          <div class="history-head-copy">
            <span class="history-kicker">Sessions</span>
            <h3><History :size="15" /> 历史会话</h3>
            <p>从已有对话继续推进任务。</p>
          </div>
          <button class="history-new-btn" type="button" @click="resetConversation">新建</button>
        </div>

        <div class="history-panel-body">
          <div v-if="!authStore.isLoggedIn" class="history-empty">
            请先登录后再使用 AI 对话和智能代理模式。
          </div>

          <div v-else-if="bootstrapping" class="history-empty loading">
            <LoaderCircle :size="15" class="spin" />
            正在同步会话列表...
          </div>

          <template v-else>
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

            <div v-if="!conversations.length" class="history-empty">
              还没有历史会话，从右侧发起第一轮任务。
            </div>
          </template>
        </div>
      </aside>

      <main class="main-panel" :class="{ home: showHomeState }">
        <template v-if="showHomeState">
          <div class="home-stage">
            <div class="home-stage-copy">
              <span class="home-badge">职业情报 Agent</span>
              <h3>我能为你做什么？</h3>
              <p>把岗位分析、技能差距、市场趋势和报告需求放进这个工作台，剩下的交给 AI。</p>
            </div>

            <div class="composer-zone home">
              <form class="composer-card" @submit.prevent="sendMessage()">
                <textarea
                  v-model="message"
                  class="composer-input home"
                  rows="4"
                  :disabled="loading || !authStore.isLoggedIn"
                  placeholder="分配一个任务或提问任何问题"
                  @keydown.ctrl.enter.prevent="sendMessage()"
                />

                <div class="composer-footer">
                  <div class="composer-controls">
                    <button
                      class="mode-chip"
                      :class="{ active: aiMode === 'chat' }"
                      type="button"
                      @click="aiMode = 'chat'"
                    >
                      <Sparkles :size="14" />
                      对话
                    </button>
                    <button
                      class="mode-chip"
                      :class="{ active: aiMode === 'agent' }"
                      type="button"
                      @click="aiMode = 'agent'"
                    >
                      <WandSparkles :size="14" />
                      智能代理
                    </button>
                    <select v-if="aiMode === 'agent'" v-model="selectedTool" class="tool-select">
                      <option v-for="option in toolOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </div>

                  <div class="composer-actions">
                    <span class="composer-hint">Ctrl + Enter 发送</span>
                    <button class="send-btn" type="submit" :disabled="loading || !authStore.isLoggedIn">
                      <LoaderCircle v-if="loading" :size="15" class="spin" />
                      <SendHorizontal v-else :size="15" />
                      {{ sendLabel }}
                    </button>
                  </div>
                </div>
              </form>

              <div class="suggestion-grid">
                <button
                  v-for="item in homeSuggestions"
                  :key="item.label"
                  class="suggestion-card"
                  type="button"
                  :disabled="loading || !authStore.isLoggedIn"
                  @click="useSuggestion(item.prompt)"
                >
                  <div class="suggestion-icon">
                    <component :is="item.icon" :size="17" />
                  </div>
                  <div class="suggestion-copy">
                    <strong>{{ item.label }}</strong>
                    <span>{{ item.desc }}</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="thread-shell">
            <div class="thread-head">
              <div class="thread-title">
                <span class="thread-kicker">{{ aiMode === 'agent' ? 'Agent Mode' : 'Chat Mode' }}</span>
                <strong>{{ activeConversationTitle }}</strong>
              </div>
              <div class="thread-status">
                <span v-if="bootstrapping">同步历史中...</span>
                <span v-else-if="loading">生成回答中...</span>
                <span v-else>上下文已就绪</span>
              </div>
            </div>

            <div ref="chatHistoryRef" class="chat-scroll">
              <div class="chat-thread">
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

                    <div class="bubble" :class="item.role">
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

            <div class="composer-zone thread">
              <div class="thread-quick-row">
                <button
                  v-for="question in quickQuestions"
                  :key="question.label"
                  class="thread-quick-chip"
                  type="button"
                  :disabled="loading || !authStore.isLoggedIn"
                  @click="sendMessage(question.prompt)"
                >
                  {{ question.label }}
                </button>
              </div>

              <form class="composer-card thread" @submit.prevent="sendMessage()">
                <textarea
                  v-model="message"
                  class="composer-input"
                  rows="3"
                  :disabled="loading || !authStore.isLoggedIn"
                  placeholder="继续追问，或给 AI 分配新的分析任务"
                  @keydown.ctrl.enter.prevent="sendMessage()"
                />

                <div class="composer-footer">
                  <div class="composer-controls">
                    <button
                      class="mode-chip"
                      :class="{ active: aiMode === 'chat' }"
                      type="button"
                      @click="aiMode = 'chat'"
                    >
                      <Sparkles :size="14" />
                      对话
                    </button>
                    <button
                      class="mode-chip"
                      :class="{ active: aiMode === 'agent' }"
                      type="button"
                      @click="aiMode = 'agent'"
                    >
                      <WandSparkles :size="14" />
                      智能代理
                    </button>
                    <select v-if="aiMode === 'agent'" v-model="selectedTool" class="tool-select">
                      <option v-for="option in toolOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </option>
                    </select>
                  </div>

                  <div class="composer-actions">
                    <span class="composer-hint">Ctrl + Enter 发送</span>
                    <button class="send-btn" type="submit" :disabled="loading || !authStore.isLoggedIn">
                      <LoaderCircle v-if="loading" :size="15" class="spin" />
                      <SendHorizontal v-else :size="15" />
                      {{ sendLabel }}
                    </button>
                  </div>
                </div>
              </form>
            </div>
          </div>
        </template>
      </main>
    </section>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 24px;
}

.ai-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.ai-header-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.ai-header-kicker,
.history-kicker,
.thread-kicker,
.home-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.ai-header-kicker,
.history-kicker,
.thread-kicker {
  color: var(--c-text-faint);
}

.home-badge {
  color: var(--c-accent-primary);
}

.ai-header-copy h2 {
  margin: 0;
  font-size: clamp(28px, 3vw, 34px);
  line-height: 1.08;
}

.ai-header-copy p {
  color: var(--c-text-muted);
  font-size: 14px;
}

.ai-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.header-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.78);
  color: var(--c-text-secondary);
  font-size: 13px;
  font-weight: 600;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.header-btn:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.header-btn.primary {
  background: #161616;
  color: #ffffff;
  border-color: #161616;
}

.header-btn.primary:hover {
  background: #2c2c2c;
  border-color: #2c2c2c;
  color: #ffffff;
}

.status-banner {
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 13px;
}

.error-banner {
  border: 1px solid rgba(179, 38, 30, 0.16);
  background: rgba(179, 38, 30, 0.08);
  color: #b3261e;
}

.ai-shell {
  display: grid;
  grid-template-columns: minmax(264px, 292px) minmax(0, 1fr);
  min-height: clamp(660px, calc(100dvh - 220px), 860px);
  overflow: hidden;
  border: 1px solid var(--c-border-strong);
  border-radius: 24px;
  background: rgba(250, 249, 245, 0.96);
  box-shadow: var(--shadow-panel);
}

.history-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--c-border-strong);
  background: rgba(244, 242, 234, 0.92);
}

.history-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 16px 14px;
  border-bottom: 1px solid rgba(207, 200, 185, 0.84);
}

.history-head-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-head-copy h3 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 17px;
}

.history-head-copy p {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.history-new-btn {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.82);
  color: var(--c-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.history-panel-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  overflow-y: auto;
}

.history-empty {
  display: flex;
  min-height: 108px;
  align-items: center;
  justify-content: center;
  padding: 18px;
  border: 1px dashed rgba(194, 186, 170, 0.9);
  border-radius: 16px;
  color: var(--c-text-muted);
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
}

.history-empty.loading {
  gap: 8px;
}

.session-entry {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: stretch;
  padding: 4px;
  border-radius: 16px;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.session-entry.active {
  background: rgba(41, 85, 155, 0.08);
}

.session-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 12px;
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
  background: rgba(255, 255, 255, 0.88);
  border-color: var(--c-border-glass);
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
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--c-text-primary);
  word-break: break-word;
}

.session-time {
  flex: none;
  color: var(--c-text-faint);
  font-size: 11px;
  white-space: nowrap;
}

.session-preview {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.session-menu-wrap {
  position: relative;
  align-self: center;
}

.session-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--c-text-muted);
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.session-delete:hover {
  background: rgba(41, 85, 155, 0.08);
  color: var(--c-accent-primary);
}

.session-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 20;
  min-width: 124px;
  padding: 6px;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 12px 28px rgba(24, 27, 35, 0.08);
}

.session-menu-item {
  display: inline-flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  color: var(--c-text-primary);
  font-size: 12px;
  text-align: left;
}

.session-menu-item:hover {
  background: rgba(41, 85, 155, 0.08);
}

.session-menu-item.danger {
  color: #b3261e;
}

.main-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  background: rgba(250, 249, 245, 0.98);
}

.main-panel.home {
  justify-content: center;
  padding: 28px;
}

.home-stage {
  display: flex;
  flex-direction: column;
  gap: 24px;
  align-items: center;
  width: 100%;
  max-width: 960px;
  margin: auto;
  text-align: center;
}

.home-stage-copy {
  display: flex;
  max-width: 700px;
  flex-direction: column;
  gap: 10px;
}

.home-stage-copy h3 {
  margin: 0;
  font-size: clamp(34px, 5vw, 46px);
  line-height: 1.03;
}

.home-stage-copy p {
  color: var(--c-text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.composer-zone {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 14px;
}

.composer-zone.home {
  max-width: 860px;
}

.composer-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--c-border-strong);
  border-radius: 24px;
  background: #ffffff;
  box-shadow: var(--shadow-card-soft);
}

.composer-card.thread {
  border-radius: 20px;
}

.composer-input {
  min-height: 96px;
  resize: none;
  color: var(--c-text-primary);
  font-size: 15px;
  line-height: 1.75;
}

.composer-input.home {
  min-height: 124px;
  font-size: 16px;
}

.composer-input::placeholder {
  color: var(--c-text-faint);
}

.composer-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.composer-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.mode-chip,
.tool-select,
.thread-quick-chip {
  border: 1px solid var(--c-border-glass);
  background: rgba(250, 249, 245, 0.9);
  color: var(--c-text-secondary);
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.mode-chip.active {
  border-color: #161616;
  background: #161616;
  color: #ffffff;
}

.tool-select {
  min-width: 220px;
  max-width: 100%;
  height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.composer-hint,
.thread-status {
  color: var(--c-text-faint);
  font-size: 12px;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 15px;
  border-radius: 14px;
  background: #161616;
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}

.send-btn:disabled {
  opacity: 0.58;
}

.suggestion-grid {
  display: grid;
  width: 100%;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.suggestion-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  text-align: left;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.suggestion-card:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  transform: translateY(-1px);
}

.suggestion-card:disabled {
  opacity: 0.56;
}

.suggestion-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(41, 85, 155, 0.08);
  color: var(--c-accent-primary);
  flex: none;
}

.suggestion-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.suggestion-copy strong {
  font-size: 14px;
  color: var(--c-text-primary);
}

.suggestion-copy span {
  color: var(--c-text-muted);
  font-size: 12px;
  line-height: 1.55;
}

.thread-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 0;
  min-height: 0;
  height: 100%;
}

.thread-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px 16px;
  border-bottom: 1px solid rgba(207, 200, 185, 0.84);
}

.thread-title {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.thread-title strong {
  font-size: 18px;
  line-height: 1.2;
}

.chat-scroll {
  min-height: 0;
  overflow-y: auto;
  padding: 20px 6px 14px;
}

.chat-thread {
  display: flex;
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  flex-direction: column;
  gap: 20px;
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
  width: 34px;
  height: 34px;
  border-radius: 12px;
  border: 1px solid var(--c-border-glass);
  background: rgba(242, 239, 231, 0.96);
  color: var(--c-text-secondary);
  flex: none;
}

.avatar.assistant {
  color: var(--c-accent-primary);
  background: rgba(41, 85, 155, 0.08);
}

.avatar.user {
  border-color: #161616;
  background: #161616;
  color: #ffffff;
}

.bubble-wrap {
  display: flex;
  min-width: 0;
  max-width: min(84%, 760px);
  flex-direction: column;
  gap: 7px;
}

.bubble-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--c-text-faint);
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

.bubble {
  padding: 15px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 18px;
  background: #ffffff;
  color: var(--c-text-primary);
  line-height: 1.72;
}

.bubble.user {
  border-color: #161616;
  background: #161616;
  color: #ffffff;
}

.thinking-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: inherit;
}

.composer-zone.thread {
  gap: 10px;
  padding: 14px 22px 18px;
  border-top: 1px solid rgba(207, 200, 185, 0.84);
}

.thread-quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.thread-quick-chip {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.thread-quick-chip:hover {
  background: #ffffff;
  border-color: var(--c-border-glass-hover);
  color: var(--c-text-primary);
}

.thread-quick-chip:disabled {
  opacity: 0.56;
}

.bubble :deep(p),
.bubble :deep(ul),
.bubble :deep(ol),
.bubble :deep(pre),
.bubble :deep(blockquote),
.bubble :deep(table) {
  margin: 0;
}

.bubble :deep(p + p),
.bubble :deep(p + ul),
.bubble :deep(p + ol),
.bubble :deep(ul + p),
.bubble :deep(ol + p),
.bubble :deep(pre + p),
.bubble :deep(p + pre),
.bubble :deep(blockquote + p),
.bubble :deep(p + blockquote) {
  margin-top: 12px;
}

.bubble :deep(ul),
.bubble :deep(ol) {
  padding-left: 20px;
}

.bubble :deep(li + li) {
  margin-top: 6px;
}

.bubble :deep(pre) {
  overflow-x: auto;
  padding: 12px;
  border-radius: 12px;
  background: rgba(17, 17, 17, 0.06);
}

.bubble.user :deep(pre) {
  background: rgba(255, 255, 255, 0.12);
}

.bubble :deep(code) {
  font-size: 0.92em;
}

.bubble :deep(a) {
  color: var(--c-accent-primary);
}

.bubble.user :deep(a) {
  color: #d7e4ff;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1180px) {
  .ai-shell {
    grid-template-columns: 260px minmax(0, 1fr);
  }
}

@media (max-width: 920px) {
  .ai-shell {
    grid-template-columns: 1fr;
  }

  .history-panel {
    border-right: none;
    border-bottom: 1px solid var(--c-border-strong);
  }

  .main-panel.home {
    padding: 20px;
  }
}

@media (max-width: 680px) {
  .ai-header {
    flex-direction: column;
  }

  .ai-header-actions,
  .composer-footer,
  .composer-actions {
    width: 100%;
  }

  .ai-header-actions {
    align-items: stretch;
  }

  .header-btn,
  .send-btn {
    justify-content: center;
  }

  .suggestion-grid,
  .composer-controls {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .composer-controls,
  .thread-quick-row {
    flex-direction: column;
    align-items: stretch;
  }

  .tool-select {
    min-width: 0;
    width: 100%;
  }

  .thread-head,
  .composer-zone.thread {
    padding-inline: 16px;
  }

  .chat-message,
  .chat-message.user {
    flex-direction: column;
  }

  .chat-message.user {
    align-items: flex-end;
  }

  .bubble-wrap {
    max-width: 100%;
  }
}
</style>
