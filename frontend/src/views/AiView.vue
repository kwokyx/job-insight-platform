<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import { Bot, History, RefreshCw, Send, Sparkles, User, WandSparkles } from 'lucide-vue-next'
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

const authStore = useAuthStore()
const defaultAssistantMessage =
  '可以直接问岗位、薪资、技能差距、推荐理由、报告解读；也可以切换到 Agent 模式，让系统基于平台内部数据和工具给出更可执行的分析。'

const messages = ref([{ role: 'assistant', content: defaultAssistantMessage }])
const conversations = ref([])
const quota = ref({ used: 0, limit: 0, remaining: 0 })
const currentSessionId = ref('')
const message = ref('')
const aiMode = ref('chat')
const selectedTool = ref('auto')
const bootstrapping = ref(false)
const loading = ref(false)
const historyLoading = ref(false)
const error = ref('')
const chatHistoryRef = ref(null)

const toolOptions = [
  { value: 'auto', label: '自动判断' },
  { value: 'market_overview', label: '市场概览' },
  { value: 'profile_snapshot', label: '画像快照' },
  { value: 'salary_insight', label: '薪资洞察' },
  { value: 'skill_gap', label: '技能差距' },
  { value: 'job_match', label: '岗位匹配' },
  { value: 'career_path', label: '成长路径' }
]

const quickQuestions = [
  '结合我当前画像，告诉我最适合优先冲刺的岗位方向。',
  '帮我看一下上海后端开发岗位最近的薪资趋势。',
  '把我当前技能和高级 Java 后端岗位做一个差距分析。',
  '给我一份从 Java 开发走向架构方向的 90 天行动计划。'
]

const quotaText = computed(() => {
  if (!quota.value.limit) return '额度信息暂不可用'
  return `已用 ${quota.value.used} / ${quota.value.limit}，剩余 ${quota.value.remaining}`
})

const modeDescription = computed(() =>
  aiMode.value === 'agent'
    ? 'Agent 会调用平台内部数据与分析工具，不只是普通对话。'
    : '对话模式适合快速追问、解读报告和生成说明。'
)

function renderMarkdown(text) {
  return marked(text || '')
}

function sanitizeAssistantContent(text) {
  if (!text) return ''
  return String(text)
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<\/?think>/gi, '')
    .replace(/\r/g, '')
    .replace(/^(okay|ok|alright|sure|so)\b[\s,:-]*/i, '')
    .trim()
}

function normalizeList(value) {
  return Array.isArray(value) ? value.filter(Boolean) : []
}

function formatAgentResponse(agentResult) {
  const answer = sanitizeAssistantContent(agentResult?.answer || '')
  const tool = agentResult?.tool || 'auto'
  const toolResult = agentResult?.toolResult || {}
  const sections = []

  if (answer) sections.push(answer)
  if (toolResult.executiveSummary) sections.push(`## 核心结论\n${toolResult.executiveSummary}`)
  if (normalizeList(toolResult.evidence).length) {
    sections.push(`## 证据依据\n${toolResult.evidence.map((item) => `- ${item}`).join('\n')}`)
  }
  if (normalizeList(toolResult.risks).length) {
    sections.push(`## 风险点\n${toolResult.risks.map((item) => `- ${item}`).join('\n')}`)
  }
  if (normalizeList(toolResult.prioritySkills).length) {
    sections.push(`## 优先技能\n${toolResult.prioritySkills.map((item) => `- ${item}`).join('\n')}`)
  }
  if (normalizeList(toolResult.nextSteps).length) {
    sections.push(`## 下一步行动\n${toolResult.nextSteps.map((item, index) => `${index + 1}. ${item}`).join('\n')}`)
  }
  if (normalizeList(toolResult.items).length) {
    const preview = toolResult.items.slice(0, 5).map((item) => {
      const title = item.title || item.label || item.skill || item.period || '结果项'
      const extra = [item.companyName, item.city, item.salaryText, item.value].filter(Boolean).join(' / ')
      return `- ${title}${extra ? `（${extra}）` : ''}`
    })
    sections.push(`## 结果预览\n${preview.join('\n')}`)
  }

  if (!sections.length) {
    sections.push(`Agent 已完成 \`${tool}\` 分析，但当前没有返回可展示内容。请换一个问题重试，或补充目标岗位、城市、技能等信息。`)
  }
  return sections.join('\n\n')
}

async function scrollToBottom() {
  await nextTick()
  if (chatHistoryRef.value) chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
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
  if (!sessionId || historyLoading.value) return
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

function resetConversation() {
  currentSessionId.value = ''
  messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
  error.value = ''
}

async function sendMessage(preset = '') {
  const content = (preset || message.value).trim()
  if (!content || loading.value || !authStore.token) return

  error.value = ''
  messages.value.push({ role: 'user', content })
  message.value = ''
  loading.value = true
  await scrollToBottom()

  const aiIndex = messages.value.push({ role: 'assistant', content: '' }) - 1

  if (aiMode.value === 'agent') {
    try {
      messages.value[aiIndex].content = 'Agent 正在调用平台工具并整理结果...'
      const agentResult = await runAiAgentQuery(authStore.token, {
        message: content,
        tool: selectedTool.value === 'auto' ? undefined : selectedTool.value
      })
      messages.value[aiIndex].content = formatAgentResponse(agentResult)
      await Promise.all([loadQuota(), loadConversations()])
    } catch (e) {
      messages.value[aiIndex].content = `Agent 请求失败：${normalizeError(e)}`
    } finally {
      loading.value = false
      await scrollToBottom()
    }
    return
  }

  try {
    await streamAiChat(
      authStore.token,
      { message: content, sessionId: currentSessionId.value || undefined },
      {
        onSession: (data) => {
          if (data?.sessionId) currentSessionId.value = data.sessionId
        },
        onMessage: (data) => {
          const chunk = sanitizeAssistantContent(data?.delta || data?.content || '')
          messages.value[aiIndex].content += chunk
        },
        onDone: async () => {
          messages.value[aiIndex].content = sanitizeAssistantContent(messages.value[aiIndex].content)
          loading.value = false
          await Promise.all([loadQuota(), loadConversations()])
          await scrollToBottom()
        },
        onError: (e) => {
          messages.value[aiIndex].content = `AI 请求失败：${normalizeError(e)}`
          loading.value = false
        }
      }
    )
  } catch (e) {
    messages.value[aiIndex].content = `AI 请求失败：${normalizeError(e)}`
    loading.value = false
  } finally {
    await scrollToBottom()
  }
}

watch(messages, () => scrollToBottom(), { deep: true })
onMounted(() => bootstrap())
</script>

<template>
  <div class="ai-page">
    <PremiumCard class="sidebar" glowColor="secondary">
      <template #header>
        <div class="panel-head">
          <div class="title-wrap">
            <History :size="18" />
            <div>
              <h2>会话历史</h2>
              <span>{{ quotaText }}</span>
            </div>
          </div>
          <GlowButton variant="ghost" :loading="bootstrapping" @click="bootstrap">
            <RefreshCw :size="14" />
            刷新
          </GlowButton>
        </div>
      </template>

      <div class="sidebar-body">
        <div class="quota-box">
          <div><span>已用</span><strong>{{ quota.used }}</strong></div>
          <div><span>剩余</span><strong>{{ quota.remaining }}</strong></div>
        </div>
        <div v-if="!authStore.token" class="empty-state">请先登录，再使用 AI 对话和 Agent 模式。</div>
        <button
          v-for="item in conversations"
          :key="item.sessionId"
          class="session-item"
          :disabled="historyLoading"
          @click="openConversation(item.sessionId)"
        >
          <span>{{ item.title || item.contextType || item.sessionId }}</span>
          <small>{{ item.updatedAt || item.createdAt }}</small>
        </button>
      </div>
    </PremiumCard>

    <PremiumCard class="chat" glowColor="primary">
      <template #header>
        <div class="panel-head">
          <div class="title-wrap">
            <Bot :size="18" />
            <div>
              <h2>AI 助手</h2>
              <span>{{ modeDescription }}</span>
            </div>
          </div>
          <GlowButton variant="ghost" @click="resetConversation">新会话</GlowButton>
        </div>
      </template>

      <div class="toolbar">
        <div class="mode-toggle">
          <button class="mode-btn" :class="{ active: aiMode === 'chat' }" @click="aiMode = 'chat'">
            <Sparkles :size="14" /> 对话
          </button>
          <button class="mode-btn" :class="{ active: aiMode === 'agent' }" @click="aiMode = 'agent'">
            <WandSparkles :size="14" /> Agent
          </button>
        </div>

        <label v-if="aiMode === 'agent'" class="tool-select">
          <span>工具</span>
          <select v-model="selectedTool">
            <option v-for="item in toolOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
      </div>

      <div v-if="error" class="error-banner">{{ error }}</div>

      <div ref="chatHistoryRef" class="chat-history">
        <div v-for="(item, index) in messages" :key="index" class="message" :class="item.role">
          <div class="message-avatar">
            <User v-if="item.role === 'user'" :size="16" />
            <Bot v-else :size="16" />
          </div>
          <div class="message-body">
            <div class="message-role">{{ item.role === 'user' ? '你' : 'AI' }}</div>
            <div class="message-content markdown-body" v-html="renderMarkdown(item.content)" />
          </div>
        </div>
      </div>

      <div class="quick-questions">
        <button v-for="item in quickQuestions" :key="item" @click="sendMessage(item)">
          {{ item }}
        </button>
      </div>

      <div class="composer">
        <textarea
          v-model="message"
          rows="4"
          placeholder="输入你的问题，或者让 Agent 结合平台数据给出更强的分析..."
          @keydown.enter.exact.prevent="sendMessage()"
        />
        <GlowButton :loading="loading" @click="sendMessage()">
          <Send :size="16" />
          发送
        </GlowButton>
      </div>
    </PremiumCard>
  </div>
</template>

<style scoped>
.ai-page { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 24px; }
.sidebar, .chat { min-height: 720px; }
.panel-head, .title-wrap, .toolbar, .mode-toggle, .quota-box, .composer { display: flex; align-items: center; }
.panel-head, .toolbar, .composer { justify-content: space-between; gap: 16px; }
.title-wrap { gap: 12px; }
.title-wrap h2 { margin: 0; }
.title-wrap span { font-size: 12px; opacity: 0.72; }
.sidebar-body { display: flex; flex-direction: column; gap: 12px; }
.quota-box { justify-content: space-between; padding: 14px 16px; border-radius: 16px; background: rgba(17, 24, 39, 0.08); }
.quota-box span { display: block; font-size: 12px; opacity: 0.7; }
.session-item { width: 100%; text-align: left; padding: 12px 14px; border: 1px solid rgba(148, 163, 184, 0.22); border-radius: 14px; background: rgba(255, 255, 255, 0.72); }
.session-item span, .session-item small { display: block; }
.session-item small { margin-top: 6px; opacity: 0.65; }
.mode-toggle { gap: 12px; }
.mode-btn { display: inline-flex; align-items: center; gap: 6px; padding: 10px 14px; border-radius: 999px; border: 1px solid rgba(148, 163, 184, 0.28); background: rgba(255, 255, 255, 0.7); }
.mode-btn.active { background: rgba(15, 118, 110, 0.12); border-color: rgba(15, 118, 110, 0.45); }
.tool-select { display: flex; align-items: center; gap: 10px; }
.tool-select select, .composer textarea { width: 100%; border-radius: 16px; border: 1px solid rgba(148, 163, 184, 0.28); padding: 12px 14px; background: rgba(255, 255, 255, 0.85); }
.error-banner { margin-top: 12px; padding: 12px 14px; border-radius: 14px; background: rgba(220, 38, 38, 0.1); color: #991b1b; }
.chat-history { margin-top: 16px; height: 430px; overflow: auto; display: flex; flex-direction: column; gap: 16px; padding-right: 6px; }
.message { display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: 12px; }
.message-avatar { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(15, 23, 42, 0.08); }
.message-body { padding: 14px 16px; border-radius: 18px; background: rgba(255, 255, 255, 0.82); border: 1px solid rgba(148, 163, 184, 0.18); }
.message.user .message-body { background: rgba(14, 116, 144, 0.1); }
.message-role { font-size: 12px; opacity: 0.68; margin-bottom: 8px; }
.message-content :deep(p:first-child) { margin-top: 0; }
.message-content :deep(p:last-child) { margin-bottom: 0; }
.quick-questions { margin-top: 16px; display: flex; flex-wrap: wrap; gap: 10px; }
.quick-questions button { padding: 10px 12px; border-radius: 999px; border: 1px solid rgba(148, 163, 184, 0.22); background: rgba(255, 255, 255, 0.75); }
.composer { margin-top: 18px; gap: 14px; align-items: flex-end; }
.composer textarea { min-height: 108px; resize: vertical; }
.empty-state { padding: 14px 12px; border-radius: 14px; background: rgba(15, 23, 42, 0.06); opacity: 0.72; }
@media (max-width: 960px) {
  .ai-page { grid-template-columns: 1fr; }
  .sidebar, .chat { min-height: auto; }
}
</style>
