<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  batchDeleteConversations,
  deleteAiConversation,
  fetchAiConversation,
  fetchAiConversations,
  fetchRoleReadiness,
  normalizeError,
  renameAiConversation,
  runAiAgentQuery,
  streamAiChat
} from '../api'
import { useAuthStore } from '../store/auth'
import { ROLE, normalizeRoleType } from '../utils/role'
import { marked } from 'marked'
import hljs from '../utils/highlight'
import 'highlight.js/styles/github.css'
import {
  ChevronDown,
  Copy,
  LoaderCircle,
  MoreHorizontal,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  SendHorizontal,
  Sparkles,
  Trash2,
  WandSparkles
} from 'lucide-vue-next'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const bootstrapping = ref(false)
const loading = ref(false)
const historyLoading = ref(false)
const deletingSessionId = ref('')
const deletingAllConversations = ref(false)
const openSessionMenuId = ref('')

const HISTORY_COLLAPSED_KEY = 'ai-history-collapsed'
const historyCollapsed = ref(
  typeof window !== 'undefined' && window.localStorage?.getItem(HISTORY_COLLAPSED_KEY) === '1'
)
function toggleHistoryPanel() {
  historyCollapsed.value = !historyCollapsed.value
  try {
    window.localStorage?.setItem(HISTORY_COLLAPSED_KEY, historyCollapsed.value ? '1' : '0')
  } catch {}
}
const error = ref('')
const readiness = ref(null)
const readinessLoading = ref(false)
const chatHistoryRef = ref(null)
const currentSessionId = ref('')
const conversations = ref([])
const message = ref('')
const aiMode = ref('chat')
const selectedTool = ref('skill_gap')

const defaultAssistantMessage = '可以直接询问职位、薪资、技能、报告，也可以切换到智能代理模式。'

const messages = ref([{ role: 'assistant', content: defaultAssistantMessage }])

const currentRoleType = computed(() => normalizeRoleType(authStore.user?.roleType))
const hasConversations = computed(() => conversations.value.length > 0)
const toolOptions = computed(() => {
  if (currentRoleType.value === ROLE.TEACHER) {
    return [
      { value: 'market_overview', label: '课程市场匹配' },
      { value: 'skill_gap', label: '能力缺口分析' },
      { value: 'career_path', label: '教改建议' },
      { value: 'auto', label: '自动选择' }
    ]
  }
  if (currentRoleType.value === ROLE.ADMIN) {
    return [
      { value: 'market_overview', label: '运营洞察' },
      { value: 'salary_insight', label: '薪资结构' },
      { value: 'job_match', label: '岗位供给概览' },
      { value: 'auto', label: '自动选择' }
    ]
  }
  return [
    { value: 'market_overview', label: '市场概览' },
    { value: 'profile_snapshot', label: '个人画像' },
    { value: 'salary_insight', label: '薪资洞察' },
    { value: 'skill_gap', label: '技能差距' },
    { value: 'job_match', label: '岗位匹配' },
    { value: 'career_path', label: '职业路径' },
    { value: 'auto', label: '自动选择' }
  ]
})

const showHomeState = computed(() => !currentSessionId.value && messages.value.length === 1)
const readinessReady = computed(() => readiness.value?.assistant?.ready !== false)
const readinessMessage = computed(() => readiness.value?.assistant?.message || '')
const readinessPrimaryAction = computed(() => {
  const primary = readiness.value?.primaryAction || {}
  const nextPath = readiness.value?.assistant?.nextPath
  return {
    path: nextPath || primary.path || '',
    label: primary.label || '前往完成前置步骤'
  }
})

const sendLabel = computed(() => {
  if (loading.value) {
    return aiMode.value === 'agent' ? '执行中' : '发送中'
  }

  return aiMode.value === 'agent' ? '运行代理' : '发送'
})

watch(toolOptions, (options) => {
  if (!options.some((item) => item.value === selectedTool.value)) {
    selectedTool.value = options[0]?.value || 'auto'
  }
}, { immediate: true })

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

const markdownRenderer = new marked.Renderer()
const originalCodeRenderer = markdownRenderer.code.bind(markdownRenderer)
markdownRenderer.code = function codeBlock(input) {
  const text = typeof input === 'string' ? input : input?.text ?? ''
  const lang = typeof input === 'string' ? arguments[1] : input?.lang ?? ''
  const language = lang && hljs.getLanguage(lang) ? lang : ''
  let highlighted
  try {
    highlighted = language
      ? hljs.highlight(text, { language, ignoreIllegals: true }).value
      : hljs.highlightAuto(text).value
  } catch {
    return originalCodeRenderer(input, lang)
  }
  const displayLang = language || 'plaintext'
  return (
    '<pre class="code-block">' +
      '<div class="code-block-head">' +
        '<span class="code-block-lang">' + displayLang + '</span>' +
        '<button class="code-block-copy" type="button" data-copied="false" aria-label="复制代码">复制</button>' +
      '</div>' +
      '<code class="hljs language-' + (language || 'plaintext') + '">' + highlighted + '</code>' +
    '</pre>'
  )
}

function renderMarkdown(text) {
  return sanitizeRenderedHtml(marked.parse(text || '', { breaks: true, renderer: markdownRenderer }))
}

function handleThreadClick(event) {
  const btn = event.target?.closest?.('.code-block-copy')
  if (!btn) return
  const pre = btn.closest('.code-block')
  const codeNode = pre?.querySelector('code')
  const text = codeNode?.textContent ?? ''
  const finish = () => {
    btn.textContent = '已复制'
    btn.dataset.copied = 'true'
    setTimeout(() => {
      btn.textContent = '复制'
      btn.dataset.copied = 'false'
    }, 1500)
  }
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(finish).catch(finish)
  } else {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    finish()
  }
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

  // NOTE: <think> tags are not stripped here anymore; the streaming
  // parser in sendMessage routes them into `reasoning` so the UI can
  // show the thinking process separately from the final answer.
  let cleaned = String(text)
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

function adaptReadinessPayload(payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }
  const nextAction = payload.nextAction || payload.primaryAction || {}
  const ready = payload.ready !== false
  const missingFields = Array.isArray(payload.missingFields) ? payload.missingFields : []
  return {
    ...payload,
    primaryAction: nextAction,
    assistant: {
      ready,
      nextPath: nextAction.path || '',
      message: ready
        ? '前置数据已就绪，可直接使用 AI 助手。'
        : `当前资料不足，请先完成“${nextAction.label || '前置准备'}”。`,
      missingFields
    }
  }
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

async function loadReadiness() {
  if (!authStore.token) {
    readiness.value = null
    return
  }
  readinessLoading.value = true
  try {
    const payload = await fetchRoleReadiness(authStore.token, currentRoleType.value)
    readiness.value = adaptReadinessPayload(payload)
  } catch {
    readiness.value = null
  } finally {
    readinessLoading.value = false
  }
}

function goToPath(path) {
  if (!path) return
  router.push(path)
}

function shouldGateAiRequest(content) {
  if (aiMode.value === 'agent') return true
  return /报告|分析|解读|推荐|供需|教学|岗位匹配|课程/.test(content || '')
}

async function bootstrap() {
  if (!authStore.token) {
    conversations.value = []
    messages.value = [{ role: 'assistant', content: defaultAssistantMessage }]
    currentSessionId.value = ''
    readiness.value = null
    return
  }

  bootstrapping.value = true
  error.value = ''

  try {
    await Promise.all([loadConversations(), loadReadiness()])
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

  // On mobile (≤ 920 px) the history panel is an overlay drawer; close
  // it after selecting a conversation so the user sees the thread.
  if (typeof window !== 'undefined' && window.matchMedia?.('(max-width: 920px)').matches) {
    historyCollapsed.value = true
  }

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
  if (!readinessReady.value && shouldGateAiRequest(content)) {
    const action = readinessPrimaryAction.value
    error.value = readinessMessage.value || '请先完成前置数据准备后再使用 AI 深度分析。'
    messages.value.push({
      role: 'assistant',
      content: (readinessMessage.value || '当前资料不足。') + (action.path ? `\n\n请先：${action.label}` : '')
    })
    if (action.path) {
      setTimeout(() => goToPath(action.path), 300)
    }
    await scrollToBottom()
    return
  }
  error.value = ''
  messages.value.push({ role: 'user', content })
  message.value = ''
  loading.value = true
  await scrollToBottom()

  const aiIndex = messages.value.push({ role: 'assistant', content: '', reasoning: '' }) - 1

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

  let thinkOpen = false
  let pendingBuffer = ''
  const OPEN_TAG = '<think>'
  const CLOSE_TAG = '</think>'

  const flushRouted = (flushAll = false) => {
    while (pendingBuffer.length) {
      if (thinkOpen) {
        const closeIdx = pendingBuffer.indexOf(CLOSE_TAG)
        if (closeIdx !== -1) {
          messages.value[aiIndex].reasoning += pendingBuffer.slice(0, closeIdx)
          pendingBuffer = pendingBuffer.slice(closeIdx + CLOSE_TAG.length)
          thinkOpen = false
          continue
        }
        // keep a tail in case </think> is split across chunks
        const safeLen = flushAll ? pendingBuffer.length : Math.max(0, pendingBuffer.length - (CLOSE_TAG.length - 1))
        if (safeLen > 0) {
          messages.value[aiIndex].reasoning += pendingBuffer.slice(0, safeLen)
          pendingBuffer = pendingBuffer.slice(safeLen)
        }
        break
      } else {
        const openIdx = pendingBuffer.indexOf(OPEN_TAG)
        if (openIdx !== -1) {
          messages.value[aiIndex].content += pendingBuffer.slice(0, openIdx)
          pendingBuffer = pendingBuffer.slice(openIdx + OPEN_TAG.length)
          thinkOpen = true
          continue
        }
        const safeLen = flushAll ? pendingBuffer.length : Math.max(0, pendingBuffer.length - (OPEN_TAG.length - 1))
        if (safeLen > 0) {
          messages.value[aiIndex].content += pendingBuffer.slice(0, safeLen)
          pendingBuffer = pendingBuffer.slice(safeLen)
        }
        break
      }
    }
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
          // Prefer explicit reasoning/thinking fields if the backend sends them;
          // otherwise fall back to parsing <think> tags inline in the content stream.
          const reasoningField = data.reasoning || data.thinking
          if (reasoningField) {
            messages.value[aiIndex].reasoning += String(reasoningField)
          }
          const raw = data.content || data.raw || ''
          if (raw) {
            pendingBuffer += String(raw).replace(/\r/g, '')
            flushRouted(false)
          }
          scrollToBottom()
        },
        onDone: async () => {
          flushRouted(true)
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

const copiedMessageIndex = ref(-1)
async function copyMessageContent(content, index) {
  const text = (content || '').toString()
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copiedMessageIndex.value = index
    setTimeout(() => {
      if (copiedMessageIndex.value === index) copiedMessageIndex.value = -1
    }, 1400)
  } catch {
    // ignore
  }
}

function editUserMessage(content) {
  message.value = (content || '').toString()
  nextTick(() => {
    const el = composerInputRef.value || document.querySelector('.composer-input')
    if (el) {
      el.focus()
      autoGrowComposer(el)
    }
  })
}

const composerInputRef = ref(null)
const COMPOSER_MAX_LINES = 8
function autoGrowComposer(el) {
  const node = el || composerInputRef.value
  if (!node) return
  const style = window.getComputedStyle(node)
  const lineHeight = parseFloat(style.lineHeight) || 24
  const paddingTop = parseFloat(style.paddingTop) || 0
  const paddingBottom = parseFloat(style.paddingBottom) || 0
  const max = Math.round(lineHeight * COMPOSER_MAX_LINES + paddingTop + paddingBottom)
  node.style.height = 'auto'
  const next = Math.min(node.scrollHeight, max)
  node.style.height = next + 'px'
  node.style.overflowY = node.scrollHeight > max ? 'auto' : 'hidden'
}
watch(message, () => {
  nextTick(() => autoGrowComposer())
})

function removeMessage(index) {
  if (loading.value) return
  messages.value.splice(index, 1)
}

const manualReasoningOpen = ref(new Set())
function isReasoningOpen(index, item) {
  // While streaming and answer hasn't started yet, keep reasoning expanded
  if (loading.value && index === messages.value.length - 1 && !item.content.trim()) {
    return true
  }
  return manualReasoningOpen.value.has(index)
}
function toggleReasoning(index) {
  const next = new Set(manualReasoningOpen.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  manualReasoningOpen.value = next
}

const editingSessionId = ref('')
const editingTitle = ref('')
const renamingSessionId = ref('')

function startRenameConversation(item) {
  openSessionMenuId.value = ''
  editingSessionId.value = item.sessionId
  editingTitle.value = item.title || item.contextType || ''
}

function cancelRenameConversation() {
  editingSessionId.value = ''
  editingTitle.value = ''
}

async function commitRenameConversation(item) {
  const sessionId = item.sessionId
  const nextTitle = (editingTitle.value || '').trim()
  if (!sessionId || !nextTitle || renamingSessionId.value) {
    cancelRenameConversation()
    return
  }
  if (nextTitle === (item.title || '')) {
    cancelRenameConversation()
    return
  }
  const idx = conversations.value.findIndex((c) => c.sessionId === sessionId)
  if (idx === -1) {
    cancelRenameConversation()
    return
  }

  renamingSessionId.value = sessionId
  const previousTitle = conversations.value[idx].title
  conversations.value[idx] = { ...conversations.value[idx], title: nextTitle }

  try {
    await renameAiConversation(authStore.token, sessionId, nextTitle)
  } catch {
    // backend may not support rename yet — keep the optimistic local update
    // so the user still sees their rename for this session
    void previousTitle
  } finally {
    renamingSessionId.value = ''
    cancelRenameConversation()
  }
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

async function handleBatchDeleteConversations() {
  if (!authStore.token || deletingAllConversations.value || !conversations.value.length) {
    return
  }
  const sessionIds = conversations.value.map((item) => item.sessionId).filter(Boolean)
  if (!sessionIds.length) {
    return
  }
  if (typeof window !== 'undefined' && !window.confirm(`确定一键删除全部 ${sessionIds.length} 个对话吗？删除后无法恢复。`)) {
    return
  }
  deletingAllConversations.value = true
  openSessionMenuId.value = ''
  error.value = ''
  try {
    await batchDeleteConversations(authStore.token, sessionIds)
    resetConversation()
    await loadConversations()
  } catch (e) {
    error.value = normalizeError(e)
  } finally {
    deletingAllConversations.value = false
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
    <div v-if="error" class="status-banner error-banner">{{ error }}</div>
    <div v-if="authStore.isLoggedIn && readinessLoading" class="status-banner info-banner">正在校验 AI 助手前置数据...</div>
    <div v-else-if="authStore.isLoggedIn && readiness && !readinessReady" class="status-banner warning-banner">
      <span>{{ readinessMessage || '当前资料不足，请先完成前置步骤。' }}</span>
      <button
        v-if="readinessPrimaryAction.path"
        type="button"
        class="warning-action"
        @click="goToPath(readinessPrimaryAction.path)"
      >
        {{ readinessPrimaryAction.label || '前往处理' }}
      </button>
    </div>

    <section class="ai-shell" :class="{ 'history-collapsed': historyCollapsed }">
      <!-- Scrim appears on mobile when the history drawer is open; tapping
           it closes the drawer. Hidden via CSS on wider screens. -->
      <div
        class="history-scrim"
        :aria-hidden="historyCollapsed"
        @click="historyCollapsed || toggleHistoryPanel()"
      ></div>
      <aside class="history-panel" :aria-hidden="historyCollapsed">
        <div class="history-panel-head">
          <div class="history-head-lead">
            <button
              class="history-toggle"
              type="button"
              title="收起侧栏"
              aria-label="收起侧栏"
              @click="toggleHistoryPanel"
            >
              <PanelLeftClose :size="16" />
            </button>
            <h3 class="history-title">历史会话</h3>
          </div>
          <div class="history-head-actions">
            <button class="history-new-btn" type="button" @click="resetConversation">新建</button>
            <button
              v-if="authStore.isLoggedIn && hasConversations"
              class="history-danger-btn"
              type="button"
              :disabled="deletingAllConversations"
              @click="handleBatchDeleteConversations"
            >
              <LoaderCircle v-if="deletingAllConversations" :size="12" class="spin" />
              <Trash2 v-else :size="12" />
              一键删除
            </button>
          </div>
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
              :class="{ active: item.sessionId === currentSessionId, editing: editingSessionId === item.sessionId }"
              @dblclick="startRenameConversation(item)"
            >
              <div
                v-if="editingSessionId === item.sessionId"
                class="session-rename"
                @click.stop
              >
                <input
                  v-model="editingTitle"
                  class="session-rename-input"
                  autofocus
                  :disabled="renamingSessionId === item.sessionId"
                  @keydown.enter.prevent="commitRenameConversation(item)"
                  @keydown.esc.prevent="cancelRenameConversation"
                  @blur="commitRenameConversation(item)"
                />
              </div>
              <button
                v-else
                class="session-item"
                :disabled="historyLoading || deletingSessionId === item.sessionId"
                @click="openConversation(item.sessionId)"
              >
                <div class="session-item-top">
                  <span class="session-name">{{ item.title || item.contextType || item.sessionId }}</span>
                </div>
              </button>

              <div
                v-if="editingSessionId !== item.sessionId"
                class="session-menu-wrap"
                :class="{ 'menu-open': openSessionMenuId === item.sessionId }"
              >
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
                  <button class="session-menu-item" @click.stop="startRenameConversation(item)">
                    <Pencil :size="14" />
                    重命名
                  </button>
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
        <button
          v-if="historyCollapsed"
          class="history-expand-btn"
          type="button"
          title="展开侧栏"
          aria-label="展开侧栏"
          @click="toggleHistoryPanel"
        >
          <PanelLeftOpen :size="16" />
        </button>
        <template v-if="showHomeState">
          <div class="home-stage">
            <h1 class="home-heading">今天想聊点什么？</h1>

            <form class="composer composer--home" @submit.prevent="sendMessage()">
              <textarea
                v-model="message"
                class="composer-input"
                rows="1"
                :disabled="loading || !authStore.isLoggedIn"
                placeholder="向职涯 OS 提出任何问题"
                @input="autoGrowComposer($event.target)"
                @keydown.ctrl.enter.prevent="sendMessage()"
              />

              <div class="composer-bar">
                <div class="composer-modes">
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

                <button class="send-icon-btn" type="submit" :disabled="loading || !authStore.isLoggedIn" :title="sendLabel">
                  <LoaderCircle v-if="loading" :size="16" class="spin" />
                  <SendHorizontal v-else :size="16" />
                </button>
              </div>
            </form>
          </div>
        </template>

        <template v-else>
          <div class="thread-shell">
            <div ref="chatHistoryRef" class="chat-scroll" @click="handleThreadClick">
              <div class="chat-thread">
                <article
                  v-for="(item, index) in messages"
                  :key="`${item.role}-${index}`"
                  class="msg"
                  :class="item.role"
                >
                  <div
                    v-if="item.role === 'assistant' && item.reasoning"
                    class="reasoning"
                    :class="{ streaming: loading && index === messages.length - 1 && !item.content.trim() }"
                  >
                    <button
                      type="button"
                      class="reasoning-head"
                      @click="toggleReasoning(index)"
                    >
                      <LoaderCircle
                        v-if="loading && index === messages.length - 1 && !item.content.trim()"
                        :size="13"
                        class="spin"
                      />
                      <ChevronDown
                        v-else
                        :size="13"
                        class="reasoning-caret"
                        :class="{ rotated: isReasoningOpen(index, item) }"
                      />
                      <span>
                        {{
                          loading && index === messages.length - 1 && !item.content.trim()
                            ? '思考中…'
                            : '查看思考过程'
                        }}
                      </span>
                    </button>
                    <div
                      v-if="isReasoningOpen(index, item)"
                      class="reasoning-body"
                    >{{ item.reasoning }}</div>
                  </div>

                  <div class="msg-content">
                    <div
                      v-if="item.role === 'assistant' && loading && index === messages.length - 1 && !item.content.trim() && !item.reasoning"
                      class="thinking-placeholder"
                    >
                      <LoaderCircle :size="14" class="spin" />
                      正在组织回答…
                    </div>
                    <div v-else-if="item.content.trim()" v-html="renderMarkdown(item.content)"></div>
                  </div>

                  <div
                    v-if="!(loading && index === messages.length - 1 && item.role === 'assistant')"
                    class="msg-actions"
                    :class="item.role"
                  >
                    <button
                      class="msg-action"
                      type="button"
                      :title="copiedMessageIndex === index ? '已复制' : '复制'"
                      @click="copyMessageContent(item.content, index)"
                    >
                      <Copy :size="13" />
                    </button>
                    <button
                      v-if="item.role === 'user'"
                      class="msg-action"
                      type="button"
                      title="编辑并重新发送"
                      @click="editUserMessage(item.content)"
                    >
                      <Pencil :size="13" />
                    </button>
                    <button
                      v-if="index !== 0 || item.role !== 'assistant'"
                      class="msg-action"
                      type="button"
                      title="从当前对话中删除"
                      @click="removeMessage(index)"
                    >
                      <Trash2 :size="13" />
                    </button>
                  </div>
                </article>
              </div>
            </div>

            <div class="composer-dock">
              <form class="composer composer--thread" @submit.prevent="sendMessage()">
                <textarea
                  ref="composerInputRef"
                  v-model="message"
                  class="composer-input"
                  rows="1"
                  :disabled="loading || !authStore.isLoggedIn"
                  placeholder="给职涯 OS 发送消息"
                  @input="autoGrowComposer($event.target)"
                  @keydown.ctrl.enter.prevent="sendMessage()"
                />

                <div class="composer-bar">
                  <div class="composer-modes">
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

                  <button class="send-icon-btn" type="submit" :disabled="loading || !authStore.isLoggedIn" :title="sendLabel">
                    <LoaderCircle v-if="loading" :size="16" class="spin" />
                    <SendHorizontal v-else :size="16" />
                  </button>
                </div>
              </form>
              <div class="composer-hint-line">Ctrl + Enter 发送 · 职涯 OS 智能助手可能出错，请核对关键信息</div>
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
  min-height: 100%;
  height: 100%;
  gap: 0;
  padding: 0;
}

.status-banner {
  margin: 10px 12px 0;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
}

.error-banner {
  border: 1px solid rgba(179, 38, 30, 0.16);
  background: rgba(179, 38, 30, 0.08);
  color: #b3261e;
}

.info-banner {
  border: 1px solid rgba(37, 99, 235, 0.2);
  background: rgba(219, 234, 254, 0.7);
  color: #1d4ed8;
}

.warning-banner {
  border: 1px solid rgba(245, 158, 11, 0.36);
  background: rgba(254, 243, 199, 0.88);
  color: #92400e;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.warning-action {
  border: 1px solid rgba(217, 119, 6, 0.42);
  background: rgba(255, 255, 255, 0.78);
  color: #9a3412;
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}
[data-theme="dark"] .info-banner {
  border-color: rgba(96, 165, 250, 0.38);
  background: rgba(30, 58, 138, 0.35);
  color: #93c5fd;
}
[data-theme="dark"] .warning-banner {
  border-color: rgba(245, 158, 11, 0.45);
  background: rgba(120, 53, 15, 0.35);
  color: #fbbf24;
}
[data-theme="dark"] .warning-action {
  border-color: rgba(245, 158, 11, 0.5);
  background: rgba(30, 41, 59, 0.4);
  color: #fbbf24;
}

.ai-shell {
  display: flex;
  min-width: 0;
  min-height: 0;
  height: 100%;
  flex: 1;
  overflow: hidden;
  position: relative;
}

.history-panel {
  display: flex;
  flex: 0 0 260px;
  width: 260px;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--c-border-glass);
  background: var(--c-bg-base);
  overflow: hidden;
  transition:
    flex-basis 220ms var(--ease-out),
    width 220ms var(--ease-out),
    opacity 160ms var(--ease-out),
    border-right-color 200ms var(--ease-out);
}

.ai-shell.history-collapsed .history-panel {
  flex-basis: 0;
  width: 0;
  opacity: 0;
  border-right-color: transparent;
  pointer-events: none;
}

.history-head-lead {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.history-toggle,
.history-expand-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: transparent;
  color: var(--c-text-muted);
  border: 1px solid transparent;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.history-toggle:hover,
.history-expand-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.history-expand-btn {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 3;
  background: var(--c-bg-base-elevated);
  border-color: var(--c-border-glass);
}

.history-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 14px 12px;
}

.history-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.history-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-primary);
  letter-spacing: -0.005em;
}

.history-new-btn {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-base-elevated);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.history-new-btn:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.history-danger-btn {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(179, 38, 30, 0.24);
  background: rgba(179, 38, 30, 0.08);
  color: #b3261e;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.history-danger-btn:hover:not(:disabled) {
  background: rgba(179, 38, 30, 0.14);
  border-color: rgba(179, 38, 30, 0.35);
}

.history-danger-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.history-panel-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px 12px;
  overflow-y: auto;
}

.history-empty {
  display: flex;
  min-height: 96px;
  align-items: center;
  justify-content: center;
  padding: 16px;
  color: var(--c-text-muted);
  font-size: 12.5px;
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
  gap: 2px;
  align-items: center;
  padding: 0 2px;
  border-radius: 10px;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.session-entry:hover,
.session-entry.active {
  background: var(--c-bg-surface-hover);
}

.session-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.session-item-top {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-rename {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid var(--c-accent-primary);
  border-radius: 8px;
  background: var(--c-bg-base-elevated);
}
.session-rename-input {
  width: 100%;
  padding: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 13px;
  line-height: 1.3;
}

.session-name {
  flex: 1;
  min-width: 0;
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--c-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  flex: none;
  color: var(--c-text-faint);
  font-size: 10.5px;
  white-space: nowrap;
}

.session-preview {
  display: none;
}

.session-menu-wrap {
  position: relative;
  align-self: center;
  margin-right: 4px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}

.session-entry:hover .session-menu-wrap,
.session-entry:focus-within .session-menu-wrap,
.session-entry.active .session-menu-wrap,
.session-menu-wrap.menu-open {
  opacity: 1;
}

.session-delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  color: var(--c-text-muted);
  background: transparent;
  border: none;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}

.session-delete:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.session-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 20;
  min-width: 124px;
  padding: 4px;
  border: 1px solid var(--c-border-glass);
  border-radius: 10px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-raised);
}

.session-menu-item {
  display: inline-flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--c-text-primary);
  background: transparent;
  border: none;
  font-family: var(--font-sans);
  font-size: 12px;
  text-align: left;
}

.session-menu-item:hover {
  background: var(--c-bg-surface-hover);
}

.session-menu-item.danger {
  color: #b3261e;
}

.main-panel {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  background: var(--c-bg-base-elevated);
  position: relative;
}

.main-panel.home {
  justify-content: center;
}

.home-stage {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 24px 16px;
}

.home-heading {
  margin: 0 0 24px;
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.01em;
  color: var(--c-text-primary);
  text-align: center;
}

.composer {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 10px 10px 8px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 24px;
  background: var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-soft);
  transition: box-shadow 160ms var(--ease-out), border-color 160ms var(--ease-out);
}

.composer:focus-within {
  border-color: var(--c-accent-primary);
  box-shadow:
    0 0 0 3px var(--c-accent-primary-glow),
    var(--shadow-card-soft);
}

.composer--home,
.composer--thread {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

.composer-input {
  width: 100%;
  min-height: 28px;
  padding: 8px 4px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.6;
}

.composer-input::placeholder {
  color: var(--c-text-faint);
}

.composer-input:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0 0;
}

.composer-modes {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.mode-chip:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.mode-chip.active {
  background: var(--c-accent-primary);
  color: #ffffff;
  font-weight: 600;
  border-color: transparent;
}

.mode-chip.active:hover {
  background: var(--c-accent-primary-hover, var(--c-accent-primary));
  color: #ffffff;
}

/* Dark: accent-primary is pale lavender, so white text washes out.
   Use the dark base color as the label for readable contrast. */
[data-theme="dark"] .mode-chip.active,
[data-theme="dark"] .mode-chip.active:hover {
  color: #0f1420;
}

.tool-select {
  height: 30px;
  min-width: 180px;
  max-width: 100%;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.tool-select:hover {
  background: var(--c-bg-surface-strong, var(--c-bg-surface-hover));
}

.send-icon-btn {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--c-accent-primary);
  color: #ffffff;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    opacity var(--duration-fast) var(--ease-out);
}

/* Dark: pale-lavender accent + dark icon color = correct contrast. */
[data-theme="dark"] .send-icon-btn {
  color: #0f1420;
}

.send-icon-btn:hover:not(:disabled) {
  background: var(--c-accent-primary-hover, var(--c-accent-primary));
}

.send-icon-btn:disabled {
  background: var(--c-text-faint);
  opacity: 0.5;
  cursor: not-allowed;
}

.thread-shell {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  height: 100%;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 24px 16px;
}

.chat-thread {
  display: flex;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  flex-direction: column;
  gap: 28px;
}

.msg {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
}
.msg.user {
  align-items: flex-end;
}
.msg.assistant {
  align-items: stretch;
}

.msg-content {
  min-width: 0;
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.75;
}
.msg.user .msg-content {
  max-width: 85%;
  padding: 10px 16px;
  border-radius: 20px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1.6;
}
.msg.assistant .msg-content {
  width: 100%;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 0;
}

.thinking-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--c-text-muted);
  font-size: 14px;
}

.reasoning {
  margin-bottom: 10px;
  max-width: 100%;
}
.reasoning-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}
.reasoning-head:hover {
  background: var(--c-bg-surface-strong);
  color: var(--c-text-primary);
  border-color: var(--c-border-glass-hover);
}
.reasoning-caret {
  transition: transform var(--duration-fast) var(--ease-out);
}
.reasoning-caret.rotated {
  transform: rotate(-180deg);
}
.reasoning.streaming .reasoning-head {
  color: var(--c-accent-primary);
  border-color: var(--c-border-glass-hover);
  background: var(--c-accent-primary-glow);
}
.reasoning-body {
  margin-top: 10px;
  padding: 10px 0 2px 14px;
  border-left: 2px solid var(--c-border-glass);
  color: var(--c-text-secondary);
  font-family: var(--font-serif);
  font-size: 13.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.msg-actions.user {
  justify-content: flex-end;
}
.msg:hover .msg-actions,
.msg-actions:focus-within {
  opacity: 1;
}
.msg-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--c-text-faint);
  background: transparent;
  border: none;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}
.msg-action:hover {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
}

.composer-dock {
  flex-shrink: 0;
  padding: 12px 24px 18px;
  /* Fade-to-page-bg so long threads don't butt up against the composer
     hard edge. Light mode = white, dark mode = #1d212c. */
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0) 0%,
    rgba(255, 255, 255, 0.9) 30%,
    #ffffff 60%
  );
}
[data-theme="dark"] .composer-dock {
  background: linear-gradient(
    180deg,
    rgba(29, 33, 44, 0) 0%,
    rgba(29, 33, 44, 0.9) 30%,
    #1d212c 60%
  );
}
.composer-hint-line {
  max-width: 760px;
  margin: 8px auto 0;
  text-align: center;
  color: var(--c-text-faint);
  font-size: 11px;
  line-height: 1.4;
  font-family: var(--font-sans);
}

.msg-content :deep(p),
.msg-content :deep(ul),
.msg-content :deep(ol),
.msg-content :deep(pre),
.msg-content :deep(blockquote),
.msg-content :deep(table) {
  margin: 0;
}

.msg-content :deep(p + p),
.msg-content :deep(p + ul),
.msg-content :deep(p + ol),
.msg-content :deep(ul + p),
.msg-content :deep(ol + p),
.msg-content :deep(pre + p),
.msg-content :deep(p + pre),
.msg-content :deep(blockquote + p),
.msg-content :deep(p + blockquote) {
  margin-top: 12px;
}

.msg-content :deep(ul),
.msg-content :deep(ol) {
  padding-left: 20px;
}

.msg-content :deep(li + li) {
  margin-top: 6px;
}

.msg-content :deep(pre) {
  overflow-x: auto;
  padding: 0;
  border-radius: 12px;
  background: transparent;
}

.msg-content :deep(pre.code-block) {
  overflow: hidden;
  margin: 10px 0;
  border: 1px solid var(--c-border-glass);
  background: var(--c-bg-surface-hover);
}

.msg-content :deep(pre.code-block .code-block-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 6px 12px;
  background: var(--c-bg-surface-active);
  border-bottom: 1px solid var(--c-border-glass);
}

.msg-content :deep(pre.code-block .code-block-lang) {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--c-text-muted);
  letter-spacing: 0.04em;
  text-transform: lowercase;
}

.msg-content :deep(pre.code-block .code-block-copy) {
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 11px;
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out);
}

.msg-content :deep(pre.code-block .code-block-copy:hover) {
  background: var(--c-bg-surface-strong);
  border-color: var(--c-border-glass);
  color: var(--c-accent-primary);
}

.msg-content :deep(pre.code-block .code-block-copy[data-copied="true"]) {
  color: var(--success, #008f4a);
}

.msg-content :deep(pre.code-block code) {
  display: block;
  padding: 12px 14px;
  overflow-x: auto;
  background: transparent;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
}

.msg-content :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--c-bg-surface-hover);
  font-family: var(--font-mono);
  font-size: 0.9em;
}
.msg-content :deep(pre.code-block code) {
  padding: 12px 14px;
  background: transparent;
}

.msg-content :deep(a) {
  color: var(--c-accent-primary);
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
  .history-panel {
    flex-basis: 240px;
    width: 240px;
  }
}

@media (max-width: 920px) {
  /* Sidebar becomes an overlay drawer: slides in from the left,
     floats over the chat instead of pushing it. This keeps the chat
     usable at narrow widths (no 260px history panel eating the top
     half of the viewport) while still letting the user browse past
     conversations. A scrim appears behind so tapping outside closes
     the drawer — see .ai-shell::before below. */
  .history-panel {
    position: absolute;
    top: 0;
    left: 0;
    bottom: 0;
    width: min(280px, 84vw);
    flex: 0 0 auto;
    border-right: 1px solid var(--c-border-glass);
    border-bottom: none;
    z-index: 40;
    transform: translateX(0);
    transition:
      transform 240ms var(--ease-out),
      opacity 180ms var(--ease-out);
    box-shadow: var(--shadow-panel);
  }

  .ai-shell.history-collapsed .history-panel {
    flex-basis: auto;
    width: min(280px, 84vw);
    height: auto;
    transform: translateX(-100%);
    box-shadow: none;
    pointer-events: none;
    opacity: 0;
  }

  /* Scrim: only visible when the drawer is open. Clicking it closes
     the drawer via the .history-scrim element we'll render in the
     template — see below. */
  .history-scrim {
    position: absolute;
    inset: 0;
    background: rgba(15, 23, 42, 0.32);
    backdrop-filter: blur(2px);
    -webkit-backdrop-filter: blur(2px);
    z-index: 35;
    opacity: 1;
    transition: opacity 180ms var(--ease-out);
  }
  .ai-shell.history-collapsed .history-scrim {
    opacity: 0;
    pointer-events: none;
  }

  .chat-scroll {
    padding: 24px 16px 12px;
  }

  .composer-dock {
    padding: 10px 16px 14px;
  }

  /* The persistent reopen button floats at the top-left of the chat
     area when the drawer is closed. */
  .history-expand-btn {
    position: absolute;
    top: 10px;
    left: 10px;
    z-index: 20;
    background: var(--c-bg-surface-strong);
    border: 1px solid var(--c-border-glass);
    box-shadow: var(--shadow-card-quiet);
  }
}

/* At desktop widths the scrim never renders, but guard against its
   CSS leaking: default it to hidden so the drawer-only element has no
   visual effect on wider screens. */
.history-scrim {
  display: none;
}
@media (max-width: 920px) {
  .history-scrim { display: block; }
}

@media (max-width: 680px) {
  .ai-page {
    padding-inline: 0;
    padding-top: 0;
  }

  .home-heading {
    font-size: 24px;
    margin-bottom: 18px;
    padding-inline: 8px;
  }

  .composer {
    border-radius: 20px;
    padding: 8px 8px 6px 14px;
  }

  .composer-modes {
    gap: 4px;
  }

  .mode-chip {
    padding: 0 10px;
    font-size: 12px;
  }

  .tool-select {
    min-width: 140px;
  }

  .msg.user .msg-content {
    max-width: 92%;
  }

  .composer-dock {
    padding: 8px 12px calc(10px + env(safe-area-inset-bottom, 0px));
  }

  .chat-scroll {
    padding: 16px 12px 12px;
  }
}
</style>
