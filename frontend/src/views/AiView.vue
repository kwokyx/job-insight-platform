<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import PremiumCard from '../components/common/PremiumCard.vue'
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

const messages = ref([
  { role: 'assistant', content: defaultAssistantMessage }
])

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

function renderMarkdown(text) {
  return marked(text || '')
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
    messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
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
        ? `${answer}\n\n\`\`\`json\n${JSON.stringify(agentResult.toolResult, null, 2)}\n\`\`\``
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
    }
  }
)

onMounted(() => {
  bootstrap()
})
</script>

<template>
  <div class="ai-page">
    <PremiumCard class="session-panel fixed-card" glowColor="secondary">
      <template #header>
        <div class="panel-header">
          <div class="title-row">
            <History :size="20" />
            <div>
              <h2>会话记录</h2>
              <span>{{ quotaText }}</span>
            </div>
          </div>
          <GlowButton variant="ghost" @click="bootstrap">
            <RefreshCw :size="14" />
            刷新
          </GlowButton>
        </div>
      </template>

      <div class="session-body">
        <div class="quota-box">
          <div>
            <span class="meta-label">已使用</span>
            <strong>{{ quota.used }}</strong>
          </div>
          <div>
            <span class="meta-label">剩余</span>
            <strong>{{ quota.remaining }}</strong>
          </div>
        </div>

        <div v-if="!authStore.token" class="empty-state">
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
    </PremiumCard>

    <PremiumCard class="chat-panel" glowColor="primary">
      <template #header>
        <div class="panel-header">
          <div class="title-row">
            <Bot :size="20" />
            <div>
              <h2>AI 工作台</h2>
              <span>可直接对话，也可切换到智能代理模式获取带工具支持的回答</span>
            </div>
          </div>
          <GlowButton variant="ghost" @click="resetConversation">新建对话</GlowButton>
        </div>
      </template>

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

      <div v-if="error" class="error-banner glass-panel">{{ error }}</div>

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
    </PremiumCard>
  </div>
</template>

<style scoped>
.ai-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
}

.fixed-card,
.session-panel,
.chat-panel {
  min-height: 720px;
}

.panel-header,
.title-row,
.quota-box,
.mode-switch,
.composer {
  display: flex;
  align-items: center;
}

.panel-header,
.mode-switch,
.composer {
  justify-content: space-between;
  gap: 12px;
}

.title-row {
  gap: 12px;
}

.title-row h2 {
  margin: 0;
  font-size: 18px;
}

.title-row span,
.meta-label,
.session-item small {
  color: var(--c-text-secondary);
}

.session-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.quota-box {
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
}

.empty-state {
  padding: 20px;
  border: 1px dashed var(--c-border-glass);
  border-radius: 16px;
  color: var(--c-text-secondary);
}

.session-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--c-border-glass);
  text-align: left;
}

.session-name {
  font-weight: 600;
  word-break: break-word;
}

.mode-btn,
.quick-chip {
  border: 1px solid var(--c-border-glass);
  background: rgba(255, 255, 255, 0.04);
  color: var(--c-text-primary);
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
}

.mode-btn.active {
  background: rgba(30, 117, 255, 0.14);
  border-color: rgba(30, 117, 255, 0.4);
}

.tool-select,
.glass-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-primary);
}

.chat-history {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 420px;
  max-height: 420px;
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
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.05);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.chat-message.user .bubble {
  background: rgba(30, 117, 255, 0.14);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.quick-chip {
  padding: 9px 12px;
  border-radius: 999px;
}

.composer {
  align-items: flex-end;
}

.composer-input {
  min-height: 96px;
  resize: vertical;
}

.error-banner {
  padding: 12px 14px;
  border-radius: 14px;
  color: #fecaca;
}

@media (max-width: 1100px) {
  .ai-page {
    grid-template-columns: 1fr;
  }

  .fixed-card,
  .session-panel,
  .chat-panel {
    min-height: auto;
  }

  .chat-history {
    max-height: 360px;
  }
}
</style>
