<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import { Bot, History, RefreshCw, Send, Sparkles, User, WandSparkles, Trash2 } from 'lucide-vue-next'
import PremiumCard from '../components/common/PremiumCard.vue'
import GlowButton from '../components/common/GlowButton.vue'
import {
  deleteAiConversation,
  fetchAiConversation,
  fetchAiConversations,
  fetchAiQuota,
  normalizeError,
  runAiAgentQuery,
  streamAiChat,
  batchDeleteConversations
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
const batchMode = ref(false)
const selectedSessions = ref(new Set())
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

marked.setOptions({
  breaks: true,
  gfm: true
})

function renderMarkdown(text) {
  if (!text) return ''
  return marked(text)
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
  if (!authStore.token) return
  historyLoading.value = true
  try {
    conversations.value = await fetchAiConversations(authStore.token)
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    historyLoading.value = false
  }
}

async function handleDeleteConversation(sessionId, event) {
  event.stopPropagation()
  if (!confirm('确定要删除这条对话历史吗？')) return
  historyLoading.value = true
  try {
    await deleteAiConversation(authStore.token, sessionId)
    if (currentSessionId.value === sessionId) {
      resetConversation()
    }
    await loadConversations()
  } catch (e) {
    error.value = normalizeError(e)
    historyLoading.value = false
  }
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  selectedSessions.value.clear()
}

function toggleSessionSelection(sessionId, event) {
  event.stopPropagation()
  if (selectedSessions.value.has(sessionId)) {
    selectedSessions.value.delete(sessionId)
  } else {
    selectedSessions.value.add(sessionId)
  }
}

async function handleBatchDelete() {
  if (selectedSessions.value.size === 0) return
  if (!confirm(`确定要删除选中的 ${selectedSessions.value.size} 条对话记录吗？`)) return
  
  historyLoading.value = true
  try {
    await batchDeleteConversations(authStore.token, Array.from(selectedSessions.value))
    if (selectedSessions.value.has(currentSessionId.value)) {
      resetConversation()
    }
    selectedSessions.value.clear()
    batchMode.value = false
    await loadConversations()
  } catch (e) {
    error.value = normalizeError(e)
    historyLoading.value = false
  }
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
  <div class="ai-page-claude">
    <!-- Sidebar -->
    <aside class="ai-sidebar">
      <div class="sidebar-header">
        <div class="brand">
          <Bot :size="20" class="brand-icon" />
          <span>AI 助手</span>
        </div>
        <button class="new-chat-btn" @click="resetConversation">
          <Sparkles :size="14" />
          新对话
        </button>
      </div>

      <div class="sidebar-scroll">
        <div class="section-label" style="display: flex; justify-content: space-between; align-items: center;">
          <span>历史记录</span>
          <button v-if="authStore.token && conversations.length > 0" class="batch-toggle-btn" @click="toggleBatchMode">
            {{ batchMode ? '取消' : '批量管理' }}
          </button>
        </div>
        <div v-if="!authStore.token" class="empty-text">登录后可保存和查看对话历史</div>
        <div class="history-list" v-else>
          <div
            v-for="item in conversations"
            :key="item.sessionId"
            class="history-item"
            :class="{ active: currentSessionId === item.sessionId && !batchMode, 'batch-selecting': batchMode }"
            :disabled="historyLoading"
            @click="batchMode ? toggleSessionSelection(item.sessionId, $event) : openConversation(item.sessionId)"
          >
            <div class="item-content">
              <span class="item-title">
                <input v-if="batchMode" type="checkbox" :checked="selectedSessions.has(item.sessionId)" class="batch-checkbox" />
                {{ item.title || item.contextType || item.sessionId }}
              </span>
              <span class="item-date">{{ item.updatedAt || item.createdAt }}</span>
            </div>
            <div class="item-actions" v-if="!batchMode">
              <Trash2 class="delete-icon" :size="14" @click="handleDeleteConversation(item.sessionId, $event)" />
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div v-if="batchMode" class="batch-footer-actions">
          <button class="batch-delete-btn" @click="handleBatchDelete" :disabled="selectedSessions.size === 0">
            删除选中 ({{ selectedSessions.size }})
          </button>
        </div>
        <div v-else style="display: flex; width: 100%; align-items: center; justify-content: space-between;">
          <div class="quota-info">
            <span>额度</span>
            <strong>{{ quota.remaining }} 次剩余</strong>
          </div>
          <button class="refresh-btn" @click="bootstrap" :disabled="bootstrapping">
            <RefreshCw :size="14" :class="{ spinning: bootstrapping }" />
          </button>
        </div>
      </div>
    </aside>

    <!-- Main Chat Area -->
    <main class="ai-main">
      <header class="chat-header">
        <div class="mode-switcher">
          <button class="mode-tab" :class="{ active: aiMode === 'chat' }" @click="aiMode = 'chat'">基础对话</button>
          <button class="mode-tab" :class="{ active: aiMode === 'agent' }" @click="aiMode = 'agent'">Agent 分析</button>
        </div>
        <div v-if="aiMode === 'agent'" class="agent-tool-select">
          <select v-model="selectedTool">
            <option v-for="item in toolOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
      </header>

      <div v-if="error" class="error-toast">{{ error }}</div>

      <div ref="chatHistoryRef" class="chat-scroll-area">
        <div class="chat-container">
          <div v-for="(item, index) in messages" :key="index" class="message-row" :class="item.role">
            <div v-if="item.role !== 'user'" class="avatar ai-avatar">
              <Bot :size="18" />
            </div>
            
            <div class="message-content-wrapper">
              <div v-if="item.role === 'user'" class="user-bubble">
                {{ item.content }}
              </div>
              <div v-else class="ai-response">
                <div v-if="item.isTyping" class="typing-dots">
                  <span></span><span></span><span></span>
                </div>
                <div v-else class="markdown-body clean-markdown" v-html="renderMarkdown(item.content)"></div>
              </div>
            </div>
            
            <div v-if="item.role === 'user'" class="avatar user-avatar">
              <User :size="18" />
            </div>
          </div>
          <div class="bottom-spacer"></div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="chat-container">
          <div v-if="messages.length <= 1" class="suggestions">
            <button v-for="item in quickQuestions" :key="item" class="suggestion-chip" @click="sendMessage(item)">
              {{ item }}
            </button>
          </div>
          <div class="input-box">
            <textarea
              v-model="message"
              rows="1"
              class="auto-resize-textarea"
              placeholder="在这里输入你的问题..."
              @keydown.enter.exact.prevent="sendMessage()"
            ></textarea>
            <button class="send-btn" :class="{ active: message.trim() }" :disabled="loading || !message.trim()" @click="sendMessage()">
              <Send :size="16" />
            </button>
          </div>
          <div class="input-footer">
            {{ modeDescription }} AI 可能会产生事实性错误，请核实重要信息。
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.ai-page-claude {
  display: flex;
  height: calc(100vh - 100px); /* Adjust based on your nav */
  background-color: var(--c-bg-primary);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--c-border-glass);
  box-shadow: 0 10px 30px rgba(0,0,0,0.02);
}

/* Sidebar */
.ai-sidebar {
  width: 280px;
  background-color: var(--c-bg-secondary);
  border-right: 1px solid var(--c-border-glass);
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  color: var(--c-text-primary);
}
.brand-icon {
  color: var(--c-accent-primary);
}
.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px;
  background-color: var(--c-bg-primary);
  border: 1px solid var(--c-border-glass);
  border-radius: 8px;
  color: var(--c-text-primary);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.new-chat-btn:hover {
  background-color: var(--c-bg-hover);
  border-color: var(--c-border-glass-hover);
}
.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-muted);
  padding: 8px;
  margin-top: 8px;
}
.empty-text {
  font-size: 12px;
  color: var(--c-text-muted);
  padding: 8px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.history-item {
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: background .2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.history-item:hover {
  background: var(--c-bg-tertiary);
}
.history-item.active {
  background: var(--c-bg-tertiary);
  color: var(--c-text-primary);
}
.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.item-actions {
  opacity: 0;
  transition: opacity .2s;
  display: flex;
  align-items: center;
}
.history-item:hover .item-actions {
  opacity: 1;
}
.delete-icon {
  color: var(--c-text-muted);
  cursor: pointer;
}
.delete-icon:hover {
  color: #ef4444;
}
.item-title {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-date {
  font-size: 11px;
  color: var(--c-text-muted);
}
.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--c-border-glass);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.quota-info {
  display: flex;
  flex-direction: column;
}
.quota-info span {
  font-size: 11px;
  color: var(--c-text-muted);
}
.quota-info strong {
  font-size: 13px;
  color: var(--c-text-primary);
}
.refresh-btn {
  background: transparent;
  border: none;
  color: var(--c-text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
}
.refresh-btn:hover {
  background: var(--c-bg-hover);
  color: var(--c-text-primary);
}
.spinning {
  animation: spin 1s linear infinite;
}
@keyframes spin { 100% { transform: rotate(360deg); } }
@keyframes typing {
  0%, 100% { opacity: 0.2; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-2px); }
}

.batch-toggle-btn {
  font-size: 12px;
  color: var(--c-text-muted);
  background: transparent;
  border: 1px solid var(--c-border-glass);
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.batch-toggle-btn:hover {
  background: var(--c-bg-hover);
  color: var(--c-text-primary);
}

.batch-selecting {
  border-left: 3px solid var(--c-primary) !important;
}

.batch-checkbox {
  margin-right: 8px;
  width: 14px;
  height: 14px;
  cursor: pointer;
}

.batch-footer-actions {
  width: 100%;
  padding-top: 8px;
}

.batch-delete-btn {
  width: 100%;
  padding: 8px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.2);
}

.batch-delete-btn:hover:not(:disabled) {
  background: #dc2626;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.batch-delete-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  filter: grayscale(1);
}

/* Main Chat */
.ai-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  background-color: var(--c-bg-primary);
}
.chat-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid transparent;
  padding: 0 20px;
  position: absolute;
  top: 0; left: 0; right: 0;
  z-index: 10;
}
.mode-switcher {
  display: flex;
  background: var(--c-bg-secondary);
  border-radius: 8px;
  padding: 4px;
  border: 1px solid var(--c-border-glass);
}
.mode-tab {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}
.mode-tab.active {
  background: var(--c-bg-primary);
  color: var(--c-text-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
.agent-tool-select {
  position: absolute;
  right: 20px;
}
.agent-tool-select select {
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-primary);
  color: var(--c-text-primary);
}

.chat-scroll-area {
  flex: 1;
  overflow-y: auto;
  padding-top: 60px;
  scroll-behavior: smooth;
}
.chat-container {
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  padding: 0 20px;
}
.bottom-spacer {
  height: 40px;
}
.message-row {
  display: flex;
  gap: 16px;
  padding: 24px 0;
}
.message-row.user {
  justify-content: flex-end;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ai-avatar {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
}
.user-avatar {
  background: var(--c-bg-secondary);
  color: var(--c-text-secondary);
  border: 1px solid var(--c-border-glass);
}
.message-content-wrapper {
  max-width: calc(100% - 100px);
}
.user-bubble {
  background-color: var(--c-bg-secondary);
  padding: 12px 16px;
  border-radius: 16px;
  border-top-right-radius: 4px;
  font-size: 15px;
  color: var(--c-text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  border: 1px solid var(--c-border-glass);
}
.ai-response {
  font-size: 15px;
  color: var(--c-text-primary);
  line-height: 1.7;
  padding-top: 4px;
}

/* Custom Markdown styling for Claude-like look */
.clean-markdown :deep(p) { margin-top: 0; margin-bottom: 1em; line-height: 1.7; }
.clean-markdown :deep(p:last-child) { margin-bottom: 0; }
.clean-markdown :deep(h1), .clean-markdown :deep(h2), .clean-markdown :deep(h3) { margin-top: 1.2em; margin-bottom: 0.6em; font-weight: 600; color: var(--c-text-primary); }
.clean-markdown :deep(h1) { font-size: 1.25rem; }
.clean-markdown :deep(h2) { font-size: 1.15rem; }
.clean-markdown :deep(h3) { font-size: 1.05rem; }
.clean-markdown :deep(ul), .clean-markdown :deep(ol) { padding-left: 1.5em; margin-bottom: 1em; }
.clean-markdown :deep(li) { margin-bottom: 0.4em; }
.clean-markdown :deep(pre) { background: rgba(0,0,0,0.3); padding: 16px; border-radius: 12px; overflow-x: auto; margin: 1em 0; border: 1px solid var(--c-border-glass); }
.clean-markdown :deep(code) { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 0.9em; }
.clean-markdown :deep(p > code) { background: var(--c-bg-hover); padding: 2px 6px; border-radius: 4px; color: var(--c-primary); }
.clean-markdown :deep(blockquote) { margin: 1em 0; padding-left: 1em; border-left: 4px solid var(--c-border-glass); color: var(--c-text-muted); font-style: italic; }
.clean-markdown :deep(table) { width: 100%; border-collapse: collapse; margin-bottom: 1em; }
.clean-markdown :deep(th), .clean-markdown :deep(td) { padding: 8px 12px; border: 1px solid var(--c-border-glass); text-align: left; }
.clean-markdown :deep(th) { background: var(--c-bg-hover); font-weight: 600; }

.typing-dots {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}
.typing-dots span {
  width: 6px; height: 6px; border-radius: 50%; background: var(--c-text-muted);
  animation: typing 1.4s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

/* Input Area */
.chat-input-area {
  padding: 0 0 24px;
  background: linear-gradient(to top, var(--c-bg-primary) 80%, transparent);
}
.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  justify-content: center;
}
.suggestion-chip {
  padding: 8px 16px;
  border-radius: 999px;
  background: var(--c-bg-secondary);
  border: 1px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.suggestion-chip:hover {
  background: var(--c-bg-hover);
  color: var(--c-text-primary);
}
.input-box {
  position: relative;
  background: var(--c-bg-secondary);
  border: 1px solid var(--c-border-glass);
  border-radius: 24px;
  padding: 12px 16px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  display: flex;
  align-items: flex-end;
  transition: border-color 0.2s;
}
.input-box:focus-within {
  border-color: rgba(56, 189, 248, 0.4);
}
.auto-resize-textarea {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--c-text-primary);
  font-size: 15px;
  line-height: 1.5;
  resize: none;
  max-height: 200px;
  padding-right: 40px;
  outline: none;
}
.auto-resize-textarea::placeholder {
  color: var(--c-text-muted);
}
.send-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: var(--c-bg-primary);
  color: var(--c-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}
.send-btn.active {
  background: var(--c-text-primary);
  color: var(--c-bg-primary);
}
.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.input-footer {
  text-align: center;
  font-size: 11px;
  color: var(--c-text-muted);
  margin-top: 12px;
}
.error-toast {
  position: absolute;
  top: 70px;
  left: 50%;
  transform: translateX(-50%);
  background: #ef4444;
  color: white;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 20;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
}
</style>
