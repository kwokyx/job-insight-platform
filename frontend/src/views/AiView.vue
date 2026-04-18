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

  const aiIndex = messages.value.push({ role: 'assistant', content: '', isTyping: true }) - 1

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
        onTyping: () => {
          if (!messages.value[aiIndex].content) {
            messages.value[aiIndex].isTyping = true
          }
        },
        onMessage: (data) => {
          messages.value[aiIndex].isTyping = false
          const chunk = sanitizeAssistantContent(data?.delta || data?.content || '')
          messages.value[aiIndex].content += chunk
        },
        onDone: async () => {
          messages.value[aiIndex].isTyping = false
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
            <div v-if="item.isTyping" class="typing-indicator">
              <span></span><span></span><span></span>
            </div>
            <div v-else class="message-content markdown-body" v-html="renderMarkdown(item.content)" />
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
.title-wrap h2 { margin: 0; font-size: 18px; font-weight: 700; background: linear-gradient(135deg, var(--c-text-primary), var(--c-text-secondary)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.title-wrap span { font-size: 12px; opacity: 0.72; }
.sidebar-body { display: flex; flex-direction: column; gap: 12px; }
.quota-box { justify-content: space-between; padding: 14px 16px; border-radius: 16px; background: rgba(17, 24, 39, 0.04); border: 1px solid rgba(255, 255, 255, 0.1); backdrop-filter: blur(10px); }
.quota-box span { display: block; font-size: 12px; opacity: 0.7; }
.quota-box strong { font-size: 16px; color: var(--c-accent-primary); }
.session-item { width: 100%; text-align: left; padding: 12px 14px; border: 1px solid var(--c-border-glass); border-radius: 14px; background: rgba(255, 255, 255, 0.4); backdrop-filter: blur(8px); transition: all var(--duration-fast) var(--ease-out); }
.session-item:hover { background: rgba(255, 255, 255, 0.8); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.session-item span, .session-item small { display: block; }
.session-item small { margin-top: 6px; opacity: 0.65; }
.mode-toggle { gap: 12px; }
.mode-btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 999px; border: 1px solid var(--c-border-glass); background: rgba(255, 255, 255, 0.4); backdrop-filter: blur(8px); transition: all var(--duration-fast); font-weight: 600; font-size: 13px; }
.mode-btn:hover { background: rgba(255, 255, 255, 0.8); }
.mode-btn.active { background: linear-gradient(135deg, rgba(56, 189, 248, 0.15), rgba(168, 85, 247, 0.1)); border-color: rgba(56, 189, 248, 0.4); color: var(--c-accent-primary); box-shadow: 0 2px 8px rgba(56, 189, 248, 0.1); }
.tool-select { display: flex; align-items: center; gap: 10px; font-size: 13px; font-weight: 500; }
.tool-select select, .composer textarea { width: 100%; border-radius: 16px; border: 1px solid var(--c-border-glass); padding: 12px 14px; background: rgba(255, 255, 255, 0.5); backdrop-filter: blur(10px); transition: all var(--duration-fast); font-family: inherit; }
.tool-select select:focus, .composer textarea:focus { outline: none; border-color: rgba(56, 189, 248, 0.5); background: rgba(255, 255, 255, 0.8); box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.1); }
.error-banner { margin-top: 12px; padding: 12px 14px; border-radius: 14px; background: rgba(239, 68, 68, 0.1); color: #b91c1c; border: 1px solid rgba(239, 68, 68, 0.2); }
.chat-history { margin-top: 16px; height: 430px; overflow: auto; display: flex; flex-direction: column; gap: 20px; padding-right: 6px; scroll-behavior: smooth; }
.chat-history::-webkit-scrollbar { width: 6px; }
.chat-history::-webkit-scrollbar-track { background: transparent; }
.chat-history::-webkit-scrollbar-thumb { background: var(--c-border-glass-hover); border-radius: 10px; }
.message { display: grid; grid-template-columns: 36px minmax(0, 1fr); gap: 12px; align-items: start; animation: slideUp 0.3s var(--ease-out); transform-origin: bottom; }
@keyframes slideUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.message-avatar { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border-radius: 12px; background: linear-gradient(135deg, rgba(255, 255, 255, 0.8), rgba(255, 255, 255, 0.4)); border: 1px solid var(--c-border-glass); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.message.user .message-avatar { background: linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-purple)); color: white; border: none; }
.message-body { padding: 14px 18px; border-radius: 18px; border-top-left-radius: 4px; background: rgba(255, 255, 255, 0.6); backdrop-filter: blur(12px); border: 1px solid var(--c-border-glass); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02); }
.message.user .message-body { border-top-left-radius: 18px; border-top-right-radius: 4px; background: linear-gradient(135deg, rgba(56, 189, 248, 0.1), rgba(168, 85, 247, 0.05)); border-color: rgba(56, 189, 248, 0.2); }
.message-role { font-size: 11px; opacity: 0.6; margin-bottom: 6px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; }
.message-content :deep(p:first-child) { margin-top: 0; }
.message-content :deep(p:last-child) { margin-bottom: 0; }
.message-content :deep(pre) { background: rgba(15, 23, 42, 0.8) !important; backdrop-filter: blur(8px); border-radius: 8px; border: 1px solid rgba(255, 255, 255, 0.1); }
.message-content :deep(code) { color: var(--c-accent-primary); background: rgba(56, 189, 248, 0.1); padding: 2px 4px; border-radius: 4px; }
.typing-indicator { display: flex; gap: 4px; padding: 4px 0; }
.typing-indicator span { display: block; width: 6px; height: 6px; border-radius: 50%; background: var(--c-accent-primary); animation: typing 1.4s infinite ease-in-out both; }
.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
@keyframes typing { 0%, 80%, 100% { transform: scale(0); opacity: 0.5; } 40% { transform: scale(1); opacity: 1; } }
.quick-questions { margin-top: 16px; display: flex; flex-wrap: wrap; gap: 10px; }
.quick-questions button { padding: 8px 14px; border-radius: 999px; border: 1px solid var(--c-border-glass); background: rgba(255, 255, 255, 0.4); backdrop-filter: blur(8px); color: var(--c-text-secondary); font-size: 13px; transition: all var(--duration-fast); }
.quick-questions button:hover { background: rgba(56, 189, 248, 0.1); border-color: rgba(56, 189, 248, 0.3); color: var(--c-accent-primary); transform: translateY(-1px); }
.composer { margin-top: 18px; gap: 14px; align-items: flex-end; position: relative; }
.composer textarea { min-height: 108px; resize: vertical; padding-right: 110px; }
.composer .glow-button { position: absolute; right: 12px; bottom: 12px; }
.empty-state { padding: 16px; border-radius: 14px; background: rgba(15, 23, 42, 0.04); border: 1px dashed var(--c-border-glass-hover); text-align: center; color: var(--c-text-muted); font-size: 13px; }
@media (max-width: 960px) {
  .ai-page { grid-template-columns: 1fr; }
  .sidebar, .chat { min-height: auto; }
}
</style>
