<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteAiConversation,
  fetchAiConversation,
  fetchAiConversations,
  renameAiConversation,
  runAiAgentQuery,
  streamAiChat
} from '../api'
import { useAuthStore } from '../store/auth'
import {
  filterToolsByRole,
  isToolAllowed,
  normalizeToolKey,
  safeDefaultTool
} from '../constants/aiToolWhitelist'
import { mapErrorMessage } from '../utils/errorMap'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
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
const openSessionMenuId = ref('')
const deleteDialog = ref({
  open: false,
  sessionId: '',
  title: '',
  loading: false
})

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
const chatHistoryRef = ref(null)
const currentSessionId = ref('')
const conversations = ref([])
const message = ref('')
const aiMode = ref('chat')
const selectedTool = ref('skill_gap')

const defaultAssistantMessage = '我可以围绕岗位匹配、薪资趋势、技能差距、课程供需和报告辅助，帮你把平台数据转成下一步行动。'

const messages = ref([{ role: 'assistant', content: defaultAssistantMessage }])

// 按文档《一、12 AI 助手》的工具枚举维护，角色过滤在下面的 computed 里做
// legacy key（如 skill_gap）会被 normalizeToolKey 映射到文档大写枚举
const ALL_TOOL_OPTIONS = [
  // 学生工具
  { value: 'resume_parse', label: '简历解析' },
  { value: 'profile_snapshot', label: '个人画像' },
  { value: 'job_match', label: '岗位匹配' },
  { value: 'skill_gap', label: '技能差距' },
  { value: 'salary_insight', label: '薪资洞察' },
  { value: 'career_path', label: '职业路径' },
  // 教师工具
  { value: 'course_match', label: '课程匹配' },
  { value: 'syllabus_analyze', label: '教学大纲分析' },
  { value: 'teaching_reform', label: '教改建议' },
  { value: 'report_assist', label: '报告辅助' },
  // 管理员工具
  { value: 'ops_insight', label: '运营洞察' },
  { value: 'user_governance', label: '用户治理' },
  { value: 'data_quality_check', label: '数据质量巡检' },
  { value: 'report_governance', label: '报告治理' },
  // 通用
  { value: 'auto', label: '自动选择' }
]

const HOME_PROMPT_CARDS = [
  {
    kicker: '岗位匹配',
    title: '定位更适合的岗位方向',
    description: '结合画像与岗位库，整理匹配原因、风险和行动建议。',
    prompt: '结合我的画像和平台岗位数据，推荐 3 个适合我的岗位方向，并说明匹配原因、潜在风险和下一步行动。'
  },
  {
    kicker: '技能差距',
    title: '拆解能力补齐路径',
    description: '把岗位要求转成技能优先级、学习顺序和作品集建议。',
    prompt: '请基于当前热门岗位要求，分析我需要优先补齐的技能差距，并给出 4 周学习与作品集提升计划。'
  },
  {
    kicker: '薪资洞察',
    title: '查看城市与岗位薪资趋势',
    description: '对比岗位、城市和经验段，快速判断机会窗口。',
    prompt: '帮我分析目标岗位在不同城市的薪资趋势、经验要求和机会密度，并给出择城建议。'
  },
  {
    kicker: '报告辅助',
    title: '生成就业分析报告框架',
    description: '面向教师或管理端，梳理数据口径、结论和改进建议。',
    prompt: '请帮我生成一份就业岗位洞察报告框架，包含核心指标、数据解读、风险提醒和教学改进建议。'
  }
]

const currentRole = computed(() => authStore.user?.roleType ?? 0)
// 按当前角色过滤出可选工具（auto 永远保留）
const toolOptions = computed(() => filterToolsByRole(ALL_TOOL_OPTIONS, currentRole.value))

// 角色切换或登录态变更时，确保 selectedTool 落在白名单内；首次 immediate 同步兜底
watch(
  () => [currentRole.value, authStore.isLoggedIn],
  () => {
    if (!isToolAllowed(selectedTool.value, currentRole.value)) {
      selectedTool.value = safeDefaultTool(ALL_TOOL_OPTIONS, currentRole.value)
    }
  },
  { immediate: true }
)

const showHomeState = computed(() => !currentSessionId.value && messages.value.length === 1)

const deleteDialogDetail = computed(() => {
  const label = deleteDialog.value.title || deleteDialog.value.sessionId
  return label ? `将删除：${label}` : ''
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

    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noreferrer noopener')
    }
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

function looksLikeMarkdownBody(text) {
  return /(^|\n)(#{1,6}\s|\s*[-*+]\s|\s*\d+\.\s|>\s|```|\|.+\|)/.test(text || '')
}

function unwrapMarkdownFence(text) {
  const raw = String(text || '').trim()
  const match = raw.match(/^```([a-zA-Z0-9_-]*)[ \t]*\n([\s\S]*?)\n?```[ \t]*$/)
  if (!match) {
    return raw
  }

  const lang = (match[1] || '').toLowerCase()
  const body = match[2] || ''
  if (!lang || lang === 'markdown' || lang === 'md' || looksLikeMarkdownBody(body)) {
    return body.trim()
  }

  return raw
}

function renderMarkdown(text) {
  return sanitizeRenderedHtml(
    marked.parse(prepareMarkdownForDisplay(text), {
      breaks: true,
      gfm: true,
      renderer: markdownRenderer
    })
  )
}

function prepareMarkdownForDisplay(text) {
  return stripLeakedPromptContext(repairCollapsedMarkdown(unwrapMarkdownFence(text)))
}

function firstTextValue(...values) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value
    if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  }
  return ''
}

function readStreamText(data, kind = 'content') {
  if (!data || typeof data !== 'object') {
    return typeof data === 'string' ? data : ''
  }

  if (kind === 'reasoning') {
    return firstTextValue(
      data.reasoning,
      data.reasoning_content,
      data.reasoningContent,
      data.thinking,
      data.thinking_content,
      data.thinkingContent,
      data.delta?.reasoning,
      data.delta?.reasoning_content,
      data.delta?.reasoningContent,
      data.choices?.[0]?.delta?.reasoning,
      data.choices?.[0]?.delta?.reasoning_content,
      data.choices?.[0]?.message?.reasoning,
      data.choices?.[0]?.message?.reasoning_content
    )
  }

  return firstTextValue(
    data.content,
    data.text,
    data.answer,
    data.output_text,
    data.outputText,
    data.delta,
    data.delta?.content,
    data.delta?.text,
    data.delta?.output_text,
    data.message?.content,
    data.choices?.[0]?.delta?.content,
    data.choices?.[0]?.delta?.text,
    data.choices?.[0]?.message?.content,
    data.raw
  )
}

function readReasoningSummary(data) {
  if (!data || typeof data !== 'object') {
    return typeof data === 'string' ? data : ''
  }

  const status = firstTextValue(data.status, data.stage, data.phase)
  const summary = firstTextValue(
    data.reasoningSummary,
    data.reasoning_summary,
    data.summary,
    data.displayText,
    data.message,
    data.text
  )

  if (summary) {
    return summary
  }

  if (status === 'thinking') {
    return '正在理解问题，并整理可用的会话上下文与平台数据。'
  }

  if (status === 'tool_calling' || status === 'tool') {
    return '正在调用平台工具补充数据依据。'
  }

  return ''
}

function sanitizeReasoningSummary(text) {
  let cleaned = unwrapMarkdownFence(normalizeLineBreaks(text))
    .replace(/<\/?think(?:ing)?\b[^>]*>/gi, '')
    .replace(/^\s*(reasoning_content|reasoning|thinking)\s*[:：]\s*/i, '')
    .trim()

  if (!cleaned) {
    return ''
  }

  if (looksLikeInternalReasoning(cleaned) || cleaned.length > 700) {
    return '已完成问题意图分析、上下文梳理和回答组织。'
  }

  const lines = cleaned
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !looksLikeInternalReasoning(line))
    .slice(0, 6)

  return lines.join('\n').trim()
}

function appendReasoningSummary(target, rawText) {
  const summary = sanitizeReasoningSummary(rawText)
  if (!summary || !target) {
    return
  }

  const existing = target.reasoning || ''
  if (existing.includes(summary)) {
    return
  }

  target.reasoning = existing ? `${existing}\n${summary}` : summary
}

function normalizeLineBreaks(text) {
  return String(text || '').replace(/\r/g, '').trim()
}

function stripLeakedPromptContext(text) {
  let cleaned = unwrapMarkdownFence(normalizeLineBreaks(text))

  cleaned = cleaned.replace(
    /(^|\n)#{1,6}\s*Platform\s*Data\s*Overview\b[\s\S]*?(?=\n#{1,6}\s*(?:Recommendations?|建议|结论|行动|岗位|技能|薪资|职业|课程)\b|$)/gi,
    '$1'
  )

  cleaned = cleaned.replace(
    /^(?:#{1,6}\s*)?Career Analytics Platform Overview[\s\S]*?(?=(?:#{1,6}\s*)?(?:Key Recommendations|Next Steps|建议|行动|分析|结论)\b)/i,
    ''
  )

  cleaned = cleaned.replace(
    /\bUser Context:\s*profileSummary=.*?(?=(?:\n|---|Key Recommendations|Next Steps|$))/gis,
    ''
  )

  return cleaned.replace(/^\s*-{3,}\s*/g, '').trim()
}

function repairCollapsedMarkdown(text) {
  let value = String(text || '').replace(/\r/g, '')
  if (!value.trim()) {
    return ''
  }

  value = value
    .replace(/(#{1,6})(?=[A-Za-z\u4e00-\u9fa5])/g, '$1 ')
    .replace(/([^\n])(?=#{1,6}\s)/g, '$1\n\n')
    .replace(/([:：])\s*-\s*(?=\*\*)/g, '$1\n- ')
    .replace(/([^\n])-\s*(?=\*\*[^*\n]{1,80}\*\*)/g, '$1\n- ')
    .replace(/(^|\n)(\d+)\.(?=[A-Za-z\u4e00-\u9fa5])/g, '$1$2. ')
    .replace(/([^\n])(\d+)\.(?=[A-Za-z\u4e00-\u9fa5])/g, '$1\n\n$2. ')
    .replace(/\*\*([^*\n:：]{1,80}[:：])\*\*(?=\S)/g, '**$1** ')

  const labelFixes = [
    ['PlatformDataOverview', 'Platform Data Overview'],
    ['Totaljobs', 'Total jobs'],
    ['Averagesalaryrange', 'Average salary range'],
    ['UserContext', 'User Context'],
    ['ProfileSummary', 'Profile Summary'],
    ['ProfileCompleteness', 'Profile Completeness'],
    ['Recommendationsfor', 'Recommendations for '],
    ['JobOpportunities', 'Job Opportunities'],
    ['AlgorithmEngineer', 'Algorithm Engineer'],
    ['DataEngineer', 'Data Engineer'],
    ['DataAnalyst', 'Data Analyst'],
    ['SalaryRange', 'Salary Range'],
    ['SkillDevelopment', 'Skill Development'],
    ['CareerGrowth', 'Career Growth'],
    ['Lookforroles', 'Look for roles'],
    ['Focuson', 'Focus on'],
    ['Utilizeyour', 'Utilize your'],
    ['PythonandSQLskills', 'Python and SQL skills'],
    ['dataanalysisroles', 'data analysis roles']
  ]

  for (const [from, to] of labelFixes) {
    value = value.replaceAll(from, to)
  }

  return value.trim()
}

function looksLikeInternalReasoning(text) {
  const value = normalizeLineBreaks(text).toLowerCase()
  if (!value) return false

  const internalPatterns = [
    /^嗯[，,\s]*我/,
    /^好[的]?[，,\s]*(我|现在)/,
    /^我现在/,
    /^现在我/,
    /^我(?:需要|得|应该|要先|会先)/,
    /^让我/,
    /^首先[，,\s]*我/,
    /用户.*(?:提供|想|需要|可能|资料|背景)/,
    /我(?:需要|应该|得|会).*?(分析|理解|考虑|判断|帮用户)/,
    /^the user\b/i,
    /^i need\b/i,
    /^i should\b/i,
    /^let me\b/i,
    /^looking at\b/i,
    /^okay[,\s]+so\b/i
  ]

  return internalPatterns.some((pattern) => pattern.test(value))
}

function findUserFacingStart(text) {
  const markers = [
    '\n# ',
    '\n## ',
    '\n### ',
    '\n1. ',
    '\n- ',
    'Key Recommendations',
    'Next Steps',
    '最终建议',
    '建议如下',
    '以下是',
    '可以从',
    '结论',
    '行动建议'
  ]
  return markers.reduce((best, marker) => {
    const idx = text.indexOf(marker)
    if (idx <= 0) return best
    return best === -1 || idx < best ? idx : best
  }, -1)
}

function splitAssistantParts(rawContent, rawReasoning = '') {
  let content = normalizeLineBreaks(rawContent)
  const reasoningParts = []

  if (rawReasoning) {
    reasoningParts.push(normalizeLineBreaks(rawReasoning))
  }

  content = content.replace(/<think\b[^>]*>([\s\S]*?)(?:<\/think>|$)/gi, (_, thought) => {
    if (thought?.trim()) reasoningParts.push(thought.trim())
    return ''
  })

  const finalStart = content.search(/(?:^|\n)(?:#{1,6}\s*)?(?:Career Analytics Platform Overview|Key Recommendations|Next Steps)\b/i)
  if (finalStart > 0 && looksLikeInternalReasoning(content.slice(0, finalStart))) {
    reasoningParts.push(content.slice(0, finalStart).trim())
    content = content.slice(finalStart).trim()
  }

  if (looksLikeInternalReasoning(content)) {
    const answerStart = findUserFacingStart(content)
    if (answerStart > 0) {
      reasoningParts.push(content.slice(0, answerStart).trim())
      content = content.slice(answerStart).trim()
    }
  }

  content = stripLeakedPromptContext(content)

  return {
    content: sanitizeAssistantContent(content),
    reasoning: sanitizeReasoningSummary(reasoningParts.filter(Boolean).join('\n\n'))
  }
}

function applyAssistantParts(target, rawContent, rawReasoning = '') {
  const parts = splitAssistantParts(rawContent, rawReasoning)
  target.content = parts.content
  target.reasoning = parts.reasoning
  return target
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

function sanitizeAssistantContent(text) {
  if (!text) return ''

  // NOTE: <think> tags are not stripped here anymore; the streaming
  // parser in sendMessage routes them into `reasoning` so the UI can
  // show the thinking process separately from the final answer.
  let cleaned = unwrapMarkdownFence(String(text))
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

  return stripLeakedPromptContext(repairCollapsedMarkdown(cleaned))
    .replace(/^(okay|ok|alright|sure|so)\b[\s,:-]*/i, '')
    .trim()
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

function toolLabelFor(tool, fallback = '') {
  const key = String(tool || '').toLowerCase()
  const map = {
    market_overview: '市场概览',
    profile_snapshot: '画像快照',
    salary_insight: '薪资洞察',
    skill_gap: '技能差距',
    job_match: '岗位匹配',
    career_path: '职业路径',
    course_supply_demand: '课程供需',
    teaching_reform: '教改建议',
    user_governance: '用户治理',
    operations_dashboard: '运营面板'
  }
  if (map[key]) return map[key]

  const option = ALL_TOOL_OPTIONS.find((item) => normalizeToolKey(item.value).toLowerCase() === key || item.value === key)
  return option?.label || fallback || key || '平台工具'
}

function normalizeToolTrace(source) {
  if (!source) {
    return []
  }

  const rawList = Array.isArray(source)
    ? source
    : Array.isArray(source.toolCalls)
      ? source.toolCalls
      : Array.isArray(source.toolTrace)
        ? source.toolTrace
        : Array.isArray(source.toolPlan)
          ? source.toolPlan.map((tool) => ({ tool }))
          : []

  return rawList
    .map((item, index) => {
      const value = typeof item === 'string' ? { tool: item } : item || {}
      const tool = value.tool || value.name || value.id || `tool-${index + 1}`
      const label = value.label || value.displayName || toolLabelFor(tool)
      const summary = value.summary || value.message || value.status || value.detail || '已完成调用'
      return {
        tool,
        label,
        summary
      }
    })
    .filter((item) => item.tool || item.label || item.summary)
}

function appendToolTrace(target, source) {
  const tools = normalizeToolTrace(source)
  if (!tools.length || !target) {
    return
  }

  const existing = Array.isArray(target.tools) ? target.tools : []
  const next = [...existing]
  for (const tool of tools) {
    const duplicate = next.some((item) => item.tool === tool.tool && item.summary === tool.summary)
    if (!duplicate) {
      next.push(tool)
    }
  }
  target.tools = next
}

function parseJsonObject(value) {
  if (!value || typeof value !== 'string') {
    return {}
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function buildMessageFromHistory(item) {
  if (item.role !== 'assistant') {
    return { role: item.role, content: item.content || '' }
  }

  const metadata = parseJsonObject(item.metadata)
  const message = applyAssistantParts(
    { role: item.role, content: '', reasoning: '', tools: [] },
    item.content,
    item.reasoning || item.reasoning_content || item.reasoningContent || metadata.reasoningSummary
  )
  appendToolTrace(message, item.toolCalls || item.toolTrace || metadata.toolCalls || metadata.toolTrace)
  return message
}

async function scrollToBottom() {
  await nextTick()
  if (chatHistoryRef.value) {
    chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
  }
}

async function loadConversations() {
  const payload = await fetchAiConversations(authStore.token)
  conversations.value = Array.isArray(payload) ? payload : []
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
    error.value = mapErrorMessage(e)
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
    messages.value = (Array.isArray(payload.messages) ? payload.messages : []).map(buildMessageFromHistory)

    if (!messages.value.length) {
      messages.value = [{ role: 'assistant', content: '当前会话还没有历史消息。' }]
    }

    await scrollToBottom()
  } catch (e) {
    error.value = mapErrorMessage(e)
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

  const aiIndex = messages.value.push({ role: 'assistant', content: '', reasoning: '', tools: [] }) - 1

  if (aiMode.value === 'agent') {
    // 前置白名单校验：防止越权调用打到后端，错了也能给明确引导语
    if (!isToolAllowed(selectedTool.value, currentRole.value)) {
      messages.value[aiIndex].content = '权限不足，当前账号无法访问该能力，请切换为白名单内的工具再试。'
      loading.value = false
      await scrollToBottom()
      return
    }
    try {
      messages.value[aiIndex].content = '智能代理处理中...'
      const agentResult = await runAiAgentQuery(authStore.token, {
        message: content,
        tool: selectedTool.value === 'auto' ? undefined : normalizeToolKey(selectedTool.value)
      })

      const answer = agentResult.answer || formatAgentToolResult(agentResult.toolResult) || '已完成工具调用，但未返回可展示的回答。'
      applyAssistantParts(
        messages.value[aiIndex],
        answer,
        agentResult.reasoningSummary || agentResult.reasoning || '已按当前角色选择并调用平台工具，完成数据整理后生成回答。'
      )
      appendToolTrace(messages.value[aiIndex], agentResult)

      await loadConversations()
    } catch (e) {
      // AI_TOOL_FORBIDDEN 会被 mapErrorMessage 翻译成统一引导语（errorMap.js 已登记）
      const friendly = mapErrorMessage(e)
      messages.value[aiIndex].content = e?.errorCode === 'AI_TOOL_FORBIDDEN'
        ? friendly
        : `智能代理请求失败：${friendly}`
    } finally {
      loading.value = false
      await scrollToBottom()
    }
    return
  }

  let thinkOpen = false
  let pendingBuffer = ''
  const OPEN_TAG = '<think'
  const CLOSE_TAGS = ['</think>', '</thinking>']
  const OPEN_TAG_TAIL = OPEN_TAG.length - 1

  const flushRouted = (flushAll = false) => {
    while (pendingBuffer.length) {
      const lowerBuffer = pendingBuffer.toLowerCase()
      if (thinkOpen) {
        const closeMatch = CLOSE_TAGS
          .map((tag) => ({ tag, idx: lowerBuffer.indexOf(tag) }))
          .filter((item) => item.idx !== -1)
          .sort((a, b) => a.idx - b.idx)[0]
        if (closeMatch) {
          const closeIdx = closeMatch.idx
          appendReasoningSummary(messages.value[aiIndex], pendingBuffer.slice(0, closeIdx))
          pendingBuffer = pendingBuffer.slice(closeIdx + closeMatch.tag.length)
          thinkOpen = false
          continue
        }
        // keep a tail in case </think> is split across chunks
        const safeLen = flushAll ? pendingBuffer.length : Math.max(0, pendingBuffer.length - (Math.max(...CLOSE_TAGS.map((tag) => tag.length)) - 1))
        if (safeLen > 0) {
          appendReasoningSummary(messages.value[aiIndex], pendingBuffer.slice(0, safeLen))
          pendingBuffer = pendingBuffer.slice(safeLen)
        }
        break
      } else {
        const openIdx = lowerBuffer.indexOf(OPEN_TAG)
        if (openIdx !== -1) {
          const tagEnd = pendingBuffer.indexOf('>', openIdx)
          if (tagEnd === -1) {
            if (openIdx > 0) {
              messages.value[aiIndex].content += pendingBuffer.slice(0, openIdx)
              pendingBuffer = pendingBuffer.slice(openIdx)
            }
            break
          }
          messages.value[aiIndex].content += pendingBuffer.slice(0, openIdx)
          pendingBuffer = pendingBuffer.slice(tagEnd + 1)
          thinkOpen = true
          continue
        }
        const safeLen = flushAll ? pendingBuffer.length : Math.max(0, pendingBuffer.length - OPEN_TAG_TAIL)
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
        onTyping: (data) => {
          appendReasoningSummary(messages.value[aiIndex], readReasoningSummary(data))
          scrollToBottom()
        },
        onReasoning: (data) => {
          appendReasoningSummary(
            messages.value[aiIndex],
            readReasoningSummary(data) || readStreamText(data, 'reasoning')
          )
          scrollToBottom()
        },
        onTool: (data) => {
          appendToolTrace(messages.value[aiIndex], data)
          appendReasoningSummary(messages.value[aiIndex], readReasoningSummary(data))
          scrollToBottom()
        },
        onMessage: (data) => {
          // Prefer explicit reasoning/thinking fields if the backend sends them;
          // otherwise fall back to parsing <think> tags inline in the content stream.
          const reasoningField = readStreamText(data, 'reasoning')
          if (reasoningField) {
            appendReasoningSummary(messages.value[aiIndex], reasoningField)
          }
          appendToolTrace(messages.value[aiIndex], data)
          const raw = readStreamText(data, 'content')
          if (raw) {
            pendingBuffer += String(raw).replace(/\r/g, '')
            flushRouted(false)
          }
          scrollToBottom()
        },
        onDone: () => {
          flushRouted(true)
          loadConversations().catch((loadError) => {
            error.value = mapErrorMessage(loadError)
          })
        },
        onError: (data) => {
          error.value = mapErrorMessage(data)
        }
      }
    )

    applyAssistantParts(
      messages.value[aiIndex],
      messages.value[aiIndex].content,
      messages.value[aiIndex].reasoning
    )

    if (!messages.value[aiIndex].content.trim()) {
      messages.value[aiIndex].content = 'AI 返回了空内容。建议先重试一次，仍无结果再切换到智能代理模式。'
    }
  } catch (e) {
    messages.value[aiIndex].content = `AI 请求失败：${mapErrorMessage(e)}`
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

function conversationTitle(item) {
  return item?.title || item?.contextType || item?.sessionId || '未命名会话'
}

function requestDeleteConversation(item) {
  const sessionId = item?.sessionId
  if (!sessionId || deletingSessionId.value) {
    return
  }

  openSessionMenuId.value = ''
  deleteDialog.value = {
    open: true,
    sessionId,
    title: conversationTitle(item),
    loading: false
  }
}

function resetDeleteDialog() {
  deleteDialog.value = {
    open: false,
    sessionId: '',
    title: '',
    loading: false
  }
}

function closeDeleteDialog(open = false) {
  if (deleteDialog.value.loading) {
    return
  }

  if (!open) {
    resetDeleteDialog()
    return
  }

  deleteDialog.value.open = true
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

function applyHomePrompt(prompt) {
  if (loading.value || !authStore.isLoggedIn) {
    return
  }

  message.value = prompt
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
    await loadConversations()
  } catch (e) {
    conversations.value[idx] = { ...conversations.value[idx], title: previousTitle }
    error.value = mapErrorMessage(e)
  } finally {
    renamingSessionId.value = ''
    cancelRenameConversation()
  }
}

async function handleDeleteConversation() {
  const sessionId = deleteDialog.value.sessionId
  if (!authStore.token || !sessionId || deletingSessionId.value) {
    return
  }

  deletingSessionId.value = sessionId
  deleteDialog.value.loading = true
  openSessionMenuId.value = ''
  error.value = ''
  let deleted = false

  try {
    await deleteAiConversation(authStore.token, sessionId)

    if (currentSessionId.value === sessionId) {
      resetConversation()
    }

    await loadConversations()
    deleted = true
  } catch (e) {
    error.value = mapErrorMessage(e)
  } finally {
    deletingSessionId.value = ''
    if (deleted) {
      resetDeleteDialog()
    } else {
      deleteDialog.value.loading = false
    }
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
                  <button class="session-menu-item danger" @click.stop="requestDeleteConversation(item)">
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
            <div class="home-hero-copy">
              <p class="home-eyebrow">AI 就业洞察助手</p>
              <h1 class="home-heading">从岗位数据里找到下一步</h1>
              <p class="home-subtitle">
                围绕岗位匹配、薪资趋势、技能差距、课程供需和报告生成提问，让平台数据快速变成可执行建议。
              </p>
            </div>

            <form class="composer composer--home" @submit.prevent="sendMessage()">
              <textarea
                ref="composerInputRef"
                v-model="message"
                class="composer-input"
                rows="1"
                :disabled="loading || !authStore.isLoggedIn"
                placeholder="例如：帮我分析前端开发岗位的技能缺口和学习优先级"
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

            <div class="home-prompt-grid" aria-label="推荐提问">
              <button
                v-for="item in HOME_PROMPT_CARDS"
                :key="item.kicker"
                class="home-prompt-card"
                type="button"
                :disabled="loading || !authStore.isLoggedIn"
                @click="applyHomePrompt(item.prompt)"
              >
                <span class="home-prompt-kicker">{{ item.kicker }}</span>
                <span class="home-prompt-title">{{ item.title }}</span>
                <span class="home-prompt-desc">{{ item.description }}</span>
              </button>
            </div>
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
                            : '查看思考摘要'
                        }}
                      </span>
                    </button>
                    <div
                      v-if="isReasoningOpen(index, item)"
                      class="reasoning-body"
                      v-html="renderMarkdown(item.reasoning)"
                    ></div>
                  </div>

                  <div v-if="item.role === 'assistant' && item.tools?.length" class="tool-trace">
                    <div class="tool-trace-head">
                      <WandSparkles :size="13" />
                      <span>工具调用</span>
                    </div>
                    <div class="tool-call-list">
                      <div
                        v-for="(tool, toolIndex) in item.tools"
                        :key="`${tool.tool || tool.label}-${toolIndex}`"
                        class="tool-call"
                      >
                        <span class="tool-call-index">{{ toolIndex + 1 }}</span>
                        <div class="tool-call-main">
                          <div class="tool-call-name">{{ tool.label || toolLabelFor(tool.tool) }}</div>
                          <div class="tool-call-summary">{{ tool.summary }}</div>
                        </div>
                      </div>
                    </div>
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

    <ConfirmDialog
      :open="deleteDialog.open"
      title="删除这段历史会话？"
      description="删除后，该会话中的提问、回答和上下文记录将从历史列表移除，无法恢复。"
      :detail="deleteDialogDetail"
      confirm-text="删除会话"
      cancel-text="先保留"
      variant="danger"
      :loading="deleteDialog.loading"
      @confirm="handleDeleteConversation"
      @update:open="closeDeleteDialog"
    />
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
  padding: 32px 16px;
}

.home-hero-copy {
  display: flex;
  width: min(760px, 100%);
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  text-align: center;
}

.home-eyebrow {
  display: inline-flex;
  align-items: center;
  margin: 0 0 10px;
  padding: 6px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 999px;
  background:
    linear-gradient(135deg, rgba(51, 102, 255, 0.09), rgba(0, 143, 112, 0.08)),
    var(--c-bg-surface-hover);
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.home-heading {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--c-text-primary);
  text-align: center;
}

.home-subtitle {
  max-width: 620px;
  margin: 14px 0 0;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 14.5px;
  line-height: 1.7;
}

.home-prompt-grid {
  display: grid;
  width: min(760px, 100%);
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.home-prompt-card {
  display: flex;
  min-height: 112px;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--c-border-glass);
  border-radius: 18px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.58), rgba(255, 255, 255, 0)),
    var(--c-bg-base-elevated);
  color: var(--c-text-primary);
  text-align: left;
  box-shadow: var(--shadow-card-quiet);
  cursor: pointer;
  transition:
    transform var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
}

.home-prompt-card:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--c-border-glass-hover);
  background:
    linear-gradient(145deg, rgba(51, 102, 255, 0.08), rgba(0, 143, 112, 0.05)),
    var(--c-bg-base-elevated);
  box-shadow: var(--shadow-card-soft);
}

.home-prompt-card:focus-visible {
  outline: 2px solid var(--c-accent-primary);
  outline-offset: 2px;
}

.home-prompt-card:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.home-prompt-kicker {
  color: var(--c-accent-primary);
  font-family: var(--font-sans);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.home-prompt-title {
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
}

.home-prompt-desc {
  color: var(--c-text-muted);
  font-family: var(--font-serif);
  font-size: 13px;
  line-height: 1.55;
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
:global([data-theme="dark"]) .mode-chip.active,
:global([data-theme="dark"]) .mode-chip.active:hover {
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
:global([data-theme="dark"]) .send-icon-btn {
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
}
.reasoning-body :deep(p),
.reasoning-body :deep(ul),
.reasoning-body :deep(ol) {
  margin: 0;
}
.reasoning-body :deep(p + p),
.reasoning-body :deep(p + ul),
.reasoning-body :deep(ul + p),
.reasoning-body :deep(ol + p) {
  margin-top: 8px;
}

.tool-trace {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border: 1px solid var(--c-border-glass);
  border-radius: 14px;
  background:
    linear-gradient(135deg, rgba(51, 102, 255, 0.06), rgba(0, 143, 112, 0.05)),
    var(--c-bg-surface-hover);
}
.tool-trace-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--c-text-secondary);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 700;
}
.tool-call-list {
  display: grid;
  gap: 7px;
}
.tool-call {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
}
.tool-call-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--c-bg-base-elevated);
  color: var(--c-accent-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}
.tool-call-main {
  min-width: 0;
}
.tool-call-name {
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.35;
}
.tool-call-summary {
  margin-top: 2px;
  color: var(--c-text-muted);
  font-family: var(--font-sans);
  font-size: 12px;
  line-height: 1.5;
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
:global([data-theme="dark"]) .composer-dock {
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
.msg-content :deep(h1),
.msg-content :deep(h2),
.msg-content :deep(h3),
.msg-content :deep(h4),
.msg-content :deep(pre),
.msg-content :deep(blockquote),
.msg-content :deep(table) {
  margin: 0;
}

.msg-content :deep(h1),
.msg-content :deep(h2),
.msg-content :deep(h3),
.msg-content :deep(h4) {
  color: var(--c-text-primary);
  font-family: var(--font-serif);
  font-weight: 800;
  letter-spacing: -0.015em;
}

.msg-content :deep(h1) {
  font-size: 22px;
  line-height: 1.25;
}

.msg-content :deep(h2) {
  font-size: 19px;
  line-height: 1.3;
}

.msg-content :deep(h3) {
  font-size: 17px;
  line-height: 1.35;
}

.msg-content :deep(h4) {
  font-size: 15px;
  line-height: 1.4;
}

.msg-content :deep(p + p),
.msg-content :deep(p + ul),
.msg-content :deep(p + ol),
.msg-content :deep(ul + p),
.msg-content :deep(ol + p),
.msg-content :deep(ul + ul),
.msg-content :deep(ol + ol),
.msg-content :deep(h1 + p),
.msg-content :deep(h2 + p),
.msg-content :deep(h3 + p),
.msg-content :deep(p + h1),
.msg-content :deep(p + h2),
.msg-content :deep(p + h3),
.msg-content :deep(ul + h2),
.msg-content :deep(ol + h2),
.msg-content :deep(table + p),
.msg-content :deep(p + table),
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

.msg-content :deep(li > p) {
  margin: 0;
}

.msg-content :deep(blockquote) {
  padding: 10px 14px;
  border-left: 3px solid var(--c-accent-primary);
  border-radius: 10px;
  background: var(--c-bg-surface-hover);
  color: var(--c-text-secondary);
}

.msg-content :deep(hr) {
  height: 1px;
  margin: 16px 0;
  border: 0;
  background: var(--c-border-glass);
}

.msg-content :deep(table) {
  display: block;
  width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  border: 1px solid var(--c-border-glass);
  border-radius: 12px;
}

.msg-content :deep(th),
.msg-content :deep(td) {
  padding: 9px 11px;
  border-bottom: 1px solid var(--c-border-glass);
  border-right: 1px solid var(--c-border-glass);
  text-align: left;
  vertical-align: top;
}

.msg-content :deep(th) {
  background: var(--c-bg-surface-hover);
  color: var(--c-text-primary);
  font-family: var(--font-sans);
  font-size: 12.5px;
  font-weight: 700;
}

.msg-content :deep(td) {
  color: var(--c-text-secondary);
}

.msg-content :deep(tr:last-child td) {
  border-bottom: 0;
}

.msg-content :deep(th:last-child),
.msg-content :deep(td:last-child) {
  border-right: 0;
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
    padding-inline: 8px;
  }

  .home-stage {
    justify-content: flex-start;
    padding: 56px 14px 18px;
  }

  .home-hero-copy {
    margin-bottom: 18px;
  }

  .home-subtitle {
    font-size: 13.5px;
  }

  .home-prompt-grid {
    grid-template-columns: 1fr;
    margin-top: 14px;
  }

  .home-prompt-card {
    min-height: auto;
    padding: 12px 14px;
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
