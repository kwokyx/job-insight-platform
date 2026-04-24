<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteAiConversation,
  fetchAiConversation,
  fetchAiConversations,
  renameAiConversation,
  runAiAgentQuery,
  streamAiAgentQuery,
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
const selectedTool = ref('auto')

// Agent 模式的分阶段等待提示，轮播式不是真实进度，但比静态文案体感好。
const agentPendingStage = ref('')
const AGENT_STAGES = [
  '分析问题中…',
  '判断是否需要平台数据…',
  '整理数据中…',
  '组织回答中…'
]
let agentStageTimer = null
function startAgentStages() {
  let i = 0
  agentPendingStage.value = AGENT_STAGES[0]
  stopAgentStages()
  agentStageTimer = setInterval(() => {
    i = (i + 1) % AGENT_STAGES.length
    agentPendingStage.value = AGENT_STAGES[i]
  }, 2500)
}
function stopAgentStages() {
  if (agentStageTimer) {
    clearInterval(agentStageTimer)
    agentStageTimer = null
  }
  agentPendingStage.value = ''
}

const defaultAssistantMessage = '我可以围绕岗位匹配、薪资趋势、技能差距、课程供需和报告辅助，帮你把平台数据转成下一步行动。'
// 保留 1 条占位消息，配合下面 showHomeState 的 length === 1 触发欢迎页
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

function renderMarkdown(text, repair = true) {
  return sanitizeRenderedHtml(
    marked.parse(repair ? prepareMarkdownForDisplay(text) : unwrapMarkdownFence(text), {
      breaks: true,
      gfm: true,
      renderer: markdownRenderer
    })
  )
}

function prepareMarkdownForDisplay(text) {
  return repairMarkdownFormatting(unwrapMarkdownFence(text))
}

// gpt-5 会把标题/加粗/列表挤在一行，这里恢复块级分隔，尽量不破坏表格和代码块。
function repairMarkdownFormatting(text) {
  let out = String(text || '').replace(/\r/g, '')
  // 保护代码块，避免下面规则乱改代码
  const codeBlocks = []
  out = out.replace(/```[\s\S]*?```/g, (match) => {
    codeBlocks.push(match)
    return `\u0000CODE${codeBlocks.length - 1}\u0000`
  })
  // 模型偶尔会把标题直接贴在上一行末尾（`10.17K/月### 用户与角色`），先把这种粘连的 `##`/`###` 断行。
  // 要求前面是非换行非 # 的字符，后面是 `[^\n#]`（可能是空格也可能是正文），且至少 2 个 `#` 才算标题——
  // 避免误伤 `#1`/`#foo` 这种 tag 场景。
  out = out.replace(/([^\n#])(#{2,6})(?=[^\n#])/g, '$1\n\n$2')
  // `###标题` → `### 标题`
  out = out.replace(/(^|\n)(#{1,6})(?=[^\s#])/g, '$1$2 ')
  // 模型偶尔会把空格塞进 ** 里（`** 225096 **`），这种 CommonMark 不认作 bold，会渲染成字面星号。
  // 去掉内层首尾空白，变回 `**225096**`
  out = out.replace(/\*\*\s+([^\*\n][^\*\n]*?)\s+\*\*/g, '**$1**')
  out = out.replace(/\*\*\s+([^\*\n]+?)\*\*/g, '**$1**')
  out = out.replace(/\*\*([^\*\n]+?)\s+\*\*/g, '**$1**')
  // CommonMark/marked 严格规则：`**加粗**` 闭合后紧贴 CJK 会导致 ** 不闭合。
  // 例如 `**C++**这类` 渲染成字面星号；把 `**` 和相邻中文之间补一个普通空格是最通用的修法。
  out = out.replace(/(\*\*[^*\n]+?\*\*)(?=[\u4e00-\u9fff])/g, '$1 ')
  out = out.replace(/(?<=[\u4e00-\u9fff])(\*\*[^*\n]+?\*\*)/g, ' $1')
  // 单星 `*xx*` 同理；但对 C++ 这类本身带 + 的词避免误伤，这里要求闭合前不是空白、闭合两侧都存在非空内容
  out = out.replace(/([^*\s])(\*[^*\n]+?\*)(?=[\u4e00-\u9fff])/g, '$1$2 ')
  // 标题紧跟正文：`### 标题` 后紧跟 `**xxx**` 或多字句时，把后续挪到下一行
  out = out.replace(/(^|\n)(#{1,6}\s+[^\n]*?)(\*\*[^\n*]+\*\*)/g, '$1$2\n\n$3')
  // 列表项粘在上一行末尾：`...：- 项` 或 `...。- 项` → `...：\n\n- 项`
  // 要求 `-` 前是非换行非空白非 `-` 的字符（避免 `---` 分隔线被拆），`-` 后带空格且下一个字符不是空白/`-`
  out = out.replace(/([^\n\s\-])\s*(-)(?=\s+[^\s\-])/g, '$1\n\n$2')
  // 列表项 `-xxx` → `- xxx`（但保留 `---` 分隔线）
  out = out.replace(/(^|\n)(-)(?=[^\s\-])/g, '$1- ')
  // 列表项和前一行紧挨着时补空行
  out = out.replace(/([^\n])\n(\s*[-*]\s)/g, '$1\n\n$2')
  // 标题前后补空行
  out = out.replace(/([^\n])\n(#{1,6}\s)/g, '$1\n\n$2')
  out = out.replace(/(^|\n)(#{1,6}\s[^\n]+)\n(?!\n)/g, '$1$2\n\n')
  // 折叠 3+ 个空行为 2 个
  out = out.replace(/\n{3,}/g, '\n\n')
  // 还原代码块
  out = out.replace(/\u0000CODE(\d+)\u0000/g, (_m, i) => codeBlocks[Number(i)] || '')
  return out.trim()
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
  // 思考链（reasoning_content / <think>）完整透传：只做最基本的清理，保留原始 Markdown 结构，
  // 让用户在折叠面板里看到模型真实的推理过程，而不是被压缩成一句空话。
  return String(text || '')
    .replace(/\r/g, '')
    .replace(/<\/?think(?:ing)?\b[^>]*>/gi, '')
    .replace(/^\s*(reasoning_content|reasoning|thinking)\s*[:：]\s*/i, '')
    .replace(/^\n+|\n+$/g, '')
}

function appendReasoningSummary(target, rawText) {
  const chunk = sanitizeReasoningSummary(rawText)
  if (!chunk || !target) {
    return
  }
  // 流式片段按顺序追加；相邻重复只在完全相同的连续片段时跳过，避免吞掉正常重复内容。
  const existing = target.reasoning || ''
  if (existing.endsWith(chunk)) {
    return
  }
  target.reasoning = existing ? `${existing}${chunk}` : chunk
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
    .replace(/([^\n#])(#{1,6})(?=[A-Za-z\u4e00-\u9fa5])/g, '$1\n\n$2 ')
    .replace(/(^|\n)(#{1,6})(?=[A-Za-z\u4e00-\u9fa5])/g, '$1$2 ')
    .replace(/([^\n#])(?=#{1,6}\s)/g, '$1\n\n')
    .replace(/([:：])\s*-\s*(?=\*\*)/g, '$1\n- ')
    .replace(/([:：])\s*-\s*(?=[A-Za-z\u4e00-\u9fa5])/g, '$1\n- ')
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

function splitAssistantParts(rawContent, _rawReasoning = '') {
  // gpt-5.4 经 sub2api 走 Chat Completions 协议没有 reasoning 字段，历史启发式也
  // 会误伤正常中文开头，这里只做最基础清理——不再切分"思考过程"。
  let content = normalizeLineBreaks(rawContent)
  // 仍剥掉可能泄漏的 <think> 块，保险起见
  content = content.replace(/<think\b[^>]*>[\s\S]*?(?:<\/think>|$)/gi, '')
  return {
    content: sanitizeAssistantContent(content),
    reasoning: ''
  }
}

function applyAssistantParts(target, rawContent, _rawReasoning = '') {
  const parts = splitAssistantParts(rawContent)
  target.content = parts.content
  target.reasoning = ''
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
  // 后端流式层已把 <think>…</think> 与平台上下文剥离并路由到 reasoning 事件，
  // 前端不再做启发式剪枝，只做轻量清理以保留模型原生 Markdown。
  return unwrapMarkdownFence(String(text))
    .replace(/\r/g, '')
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

// 用户手动往上滚阅读历史时应当暂停自动下滑；滚回底部附近（<64px）又会恢复。
const autoScrollPinned = ref(true)
function onChatScroll() {
  const el = chatHistoryRef.value
  if (!el) return
  autoScrollPinned.value = el.scrollHeight - el.scrollTop - el.clientHeight < 64
}

/**
 * 输入框键盘策略：
 *   - 纯 Enter      → 发送（阻止默认换行）
 *   - Ctrl/Cmd+Enter → 在光标处插入换行
 *   - Shift+Enter    → 默认行为（textarea 自带换行）
 *   - IME 组词时不拦截（e.isComposing 或 keyCode 229）
 */
function onComposerKeydown(e) {
  if (e.key !== 'Enter') return
  if (e.isComposing || e.keyCode === 229) return
  if (e.shiftKey) return
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    const ta = e.target
    if (!ta || typeof ta.selectionStart !== 'number') {
      message.value = (message.value || '') + '\n'
      return
    }
    const start = ta.selectionStart
    const end = ta.selectionEnd
    const before = message.value.slice(0, start)
    const after = message.value.slice(end)
    message.value = before + '\n' + after
    nextTick(() => {
      ta.focus()
      ta.selectionStart = ta.selectionEnd = start + 1
      autoGrowComposer(ta)
    })
    return
  }
  e.preventDefault()
  sendMessage()
}

async function scrollToBottom(force = false) {
  if (!force && !autoScrollPinned.value) return
  await nextTick()
  // 双 rAF 确保 Vue 完成提交、浏览器已完成布局/绘制后再定位，避免内容长时 scrollTop 落后
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      const el = chatHistoryRef.value
      if (!el) return
      el.scrollTop = el.scrollHeight
    })
  })
}

// 最后一条消息内容或工具列表变化时自动贴底
watch(
  () => {
    const last = messages.value[messages.value.length - 1]
    if (!last) return ''
    return `${last.content?.length || 0}|${last.tools?.length || 0}|${last.streaming ? 1 : 0}`
  },
  () => scrollToBottom()
)

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
  // 进入对话前清掉用于触发欢迎页的占位 assistant 消息，避免它出现在真实对话里
  if (showHomeState.value) {
    messages.value = []
  }
  messages.value.push({ role: 'user', content })
  message.value = ''
  loading.value = true
  await scrollToBottom()

  const aiIndex = messages.value.push({
    role: 'assistant',
    content: '',
    reasoning: '',
    tools: [],
    streaming: true,
    renderedHtml: ''
  }) - 1

  // 每个 SSE chunk 立即更新 content；renderedHtml（真正喂给 v-html 的）则 80ms 节流一次——
  // 既能边吐字边看到格式，又不会每 token 都跑一次 marked.parse 让输出期卡顿。
  const createAppender = () => {
    let hasFirstToken = false
    let renderTimer = null
    const renderNow = () => {
      renderTimer = null
      const msg = messages.value[aiIndex]
      if (!msg) return
      msg.renderedHtml = renderMarkdown(msg.content, true)
    }
    const scheduleRender = () => {
      if (renderTimer) return
      renderTimer = setTimeout(renderNow, 80)
    }
    const push = (chunk) => {
      if (!chunk) return
      if (!hasFirstToken) {
        hasFirstToken = true
        stopAgentStages()
      }
      messages.value[aiIndex].content += String(chunk).replace(/\r/g, '')
      scheduleRender()
    }
    const finalize = () => {
      if (renderTimer) {
        clearTimeout(renderTimer)
        renderTimer = null
      }
      // 结束时立刻 fullscreen 渲染一次，确保最终 markdown 完整
      const msg = messages.value[aiIndex]
      if (msg) {
        msg.renderedHtml = renderMarkdown(msg.content, true)
        msg.streaming = false
      }
    }
    return { push, finalize }
  }

  if (aiMode.value === 'agent') {
    // 智能代理现在统一交给模型 function calling 自主判断，不再允许手动指定工具。
    const appender = createAppender()
    try {
      startAgentStages()
      let doneResult = null

      await streamAiAgentQuery(authStore.token, {
        message: content,
        sessionId: currentSessionId.value || undefined
        // tool 字段留空 → 后端走 function calling，由模型自主选工具
      }, {
        onSession: (data) => {
          if (data?.sessionId) currentSessionId.value = data.sessionId
        },
        onTyping: () => {
          // 复用阶段轮播，等 tool 事件或 content 事件到达再停
        },
        onToolCall: (data) => {
          // 模型决定调用某个工具，先把它以"调用中"的样子放进 tools 区
          stopAgentStages()
          const tool = data?.tool || ''
          if (!tool) return
          const existing = Array.isArray(messages.value[aiIndex].tools) ? messages.value[aiIndex].tools : []
          messages.value[aiIndex].tools = [
            ...existing,
            { tool, label: toolLabelFor(tool), summary: '调用中…', pending: true }
          ]
          scrollToBottom()
        },
        onToolResult: (data) => {
          // 工具执行完毕：找到对应的 pending 条目，填上 summary
          const tool = data?.tool || ''
          const list = Array.isArray(messages.value[aiIndex].tools) ? [...messages.value[aiIndex].tools] : []
          const idx = [...list].reverse().findIndex((t) => t.tool === tool && t.pending)
          if (idx !== -1) {
            const realIdx = list.length - 1 - idx
            list[realIdx] = {
              tool,
              label: data?.label || toolLabelFor(tool),
              summary: data?.summary || '已完成调用',
              pending: false
            }
          } else {
            list.push({
              tool,
              label: data?.label || toolLabelFor(tool),
              summary: data?.summary || '已完成调用',
              pending: false
            })
          }
          messages.value[aiIndex].tools = list
          scrollToBottom()
        },
        onMessage: (data) => {
          appender.push(readStreamText(data, 'content'))
        },
        onDone: (data) => {
          doneResult = data || {}
        },
        onError: (data) => {
          error.value = mapErrorMessage(data)
        }
      })

      appender.finalize()
      stopAgentStages()
      if (doneResult && Array.isArray(doneResult.toolTrace) && doneResult.toolTrace.length) {
        messages.value[aiIndex].tools = doneResult.toolTrace.map((t) => ({
          tool: t.tool,
          label: t.label || toolLabelFor(t.tool),
          summary: t.summary || '已完成调用',
          pending: false
        }))
      }
      applyAssistantParts(messages.value[aiIndex], messages.value[aiIndex].content)
      if (!messages.value[aiIndex].content.trim()) {
        messages.value[aiIndex].content = 'AI 没有返回内容，请稍后重试。'
      }

      await loadConversations()
    } catch (e) {
      appender.finalize()
      messages.value[aiIndex].content = `智能代理请求失败：${mapErrorMessage(e)}`
    } finally {
      appender.finalize()
      stopAgentStages()
      loading.value = false
      await scrollToBottom()
    }
    return
  }

  // Chat 路径复用和 agent 相同的 rAF 批次追加，保证两种模式输出平滑一致。
  const chatAppender = createAppender()

  try {
    await streamAiChat(
      authStore.token,
      {
        message: content,
        sessionId: currentSessionId.value || undefined
      },
      {
        onSession: (data) => {
          if (data?.sessionId) currentSessionId.value = data.sessionId
        },
        onTyping: () => {},
        onReasoning: () => {},       // gpt-5.4 via sub2api 不返回 reasoning，丢弃即可
        onTool: (data) => {
          appendToolTrace(messages.value[aiIndex], data)
          scrollToBottom()
        },
        onMessage: (data) => {
          appendToolTrace(messages.value[aiIndex], data)
          chatAppender.push(readStreamText(data, 'content'))
        },
        onDone: () => {
          loadConversations().catch((loadError) => {
            error.value = mapErrorMessage(loadError)
          })
        },
        onError: (data) => {
          error.value = mapErrorMessage(data)
        }
      }
    )

    chatAppender.finalize()
    applyAssistantParts(messages.value[aiIndex], messages.value[aiIndex].content)

    if (!messages.value[aiIndex].content.trim()) {
      messages.value[aiIndex].content = 'AI 返回了空内容，请稍后重试。'
    }
  } catch (e) {
    chatAppender.finalize()
    messages.value[aiIndex].content = `AI 请求失败：${mapErrorMessage(e)}`
  } finally {
    chatAppender.finalize()
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

// 思考链 UI 已移除（gpt-5.4 via sub2api 不返回 reasoning 字段）。

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
                @keydown="onComposerKeydown"
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
            <div ref="chatHistoryRef" class="chat-scroll" @click="handleThreadClick" @scroll.passive="onChatScroll">
              <div class="chat-thread">
                <article
                  v-for="(item, index) in messages"
                  :key="`${item.role}-${index}`"
                  class="msg"
                  :class="item.role"
                >
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
                        :class="{ pending: tool.pending }"
                      >
                        <span class="tool-call-index">
                          <LoaderCircle v-if="tool.pending" :size="12" class="spin" />
                          <template v-else>{{ toolIndex + 1 }}</template>
                        </span>
                        <div class="tool-call-main">
                          <div class="tool-call-name">{{ tool.label || toolLabelFor(tool.tool) }}</div>
                          <div class="tool-call-summary">
                            {{ tool.summary }}<span v-if="tool.pending" class="tool-dots" aria-hidden="true"></span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="msg-content">
                    <div
                      v-if="item.role === 'assistant' && loading && index === messages.length - 1 && !item.content.trim()"
                      class="thinking-placeholder"
                    >
                      <LoaderCircle :size="14" class="spin" />
                      {{ aiMode === 'agent' && agentPendingStage ? agentPendingStage : '正在组织回答…' }}
                    </div>
                    <div
                      v-else-if="item.role === 'assistant' && item.content.trim()"
                      class="msg-markdown"
                      :class="{ streaming: item.streaming }"
                    >
                      <div v-html="item.renderedHtml || renderMarkdown(item.content, true)"></div>
                      <span v-if="item.streaming" class="stream-caret" aria-hidden="true">▍</span>
                    </div>
                    <div
                      v-else-if="item.content.trim()"
                      v-html="renderMarkdown(item.content, item.role === 'assistant')"
                    ></div>
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
                  @keydown="onComposerKeydown"
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
                  </div>

                  <button class="send-icon-btn" type="submit" :disabled="loading || !authStore.isLoggedIn" :title="sendLabel">
                    <LoaderCircle v-if="loading" :size="16" class="spin" />
                    <SendHorizontal v-else :size="16" />
                  </button>
                </div>
              </form>
              <div class="composer-hint-line">Enter 发送 · Ctrl + Enter 换行 · 职涯 OS 智能助手可能出错，请核对关键信息</div>
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
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 32px 16px;
}

/* hero 贴在 composer 上方一小段距离，一起随父级 justify-content: center 垂直居中 */
.home-hero-copy {
  display: flex;
  width: min(760px, 100%);
  flex-direction: column;
  align-items: center;
  margin-bottom: 18px;
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
  scroll-behavior: smooth;  /* 切换对话 + 流式追加时滑动，不硬跳 */
}

.chat-thread {
  display: flex;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  flex-direction: column;
  gap: 28px;
}

/* 切换历史对话时整条消息列表做淡入，避免 Vue diff 出来的"闪一下" */
.chat-thread > .msg {
  animation: msg-fade-in 180ms cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes msg-fade-in {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 主面板在 home <-> thread 模式切换时也来一个柔性过渡 */
.main-panel > .home-stage,
.main-panel > .thread-shell {
  animation: panel-fade-in 220ms ease-out;
}
@keyframes panel-fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
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
  padding: 10px 14px;
  border-radius: 12px;
  background: linear-gradient(
    90deg,
    var(--c-bg-surface-hover) 0%,
    var(--c-bg-base-elevated) 50%,
    var(--c-bg-surface-hover) 100%
  );
  background-size: 200% 100%;
  animation: placeholder-shimmer 1.6s ease-in-out infinite;
  color: var(--c-text-secondary);
  font-size: 14px;
}
@keyframes placeholder-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
/* 流式期间把 markdown 解析节流到 80ms 一次，这里的容器把解析结果和闪烁光标包一起；
   结束后 `streaming` 类消失，光标随之移除。 */
.msg-markdown {
  position: relative;
}
.msg-markdown.streaming :deep(> :last-child) {
  /* 让最后一个块级元素给光标留一点空间，避免换行跳动 */
  display: inline-block;
  min-width: calc(100% - 12px);
  vertical-align: bottom;
}
.stream-caret {
  display: inline-block;
  margin-left: 2px;
  color: var(--c-accent-primary);
  font-weight: 700;
  vertical-align: baseline;
  animation: stream-caret-blink 1s steps(2, end) infinite;
}
@keyframes stream-caret-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
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
.tool-call.pending .tool-call-name {
  color: var(--c-accent-primary);
}
.tool-call.pending .tool-call-index {
  background: transparent;
  color: var(--c-accent-primary);
}
.tool-dots::after {
  content: '';
  display: inline-block;
  width: 1em;
  text-align: left;
  animation: tool-dots 1.2s steps(4, end) infinite;
}
@keyframes tool-dots {
  0%   { content: ''; }
  25%  { content: '.'; }
  50%  { content: '..'; }
  75%  { content: '...'; }
  100% { content: ''; }
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
  padding-left: 22px;
}
/* 全局 base.css 把列表 list-style 重置成 none，这里在消息正文里恢复，
   让 `- 项` 渲染成圆点、`1.` 渲染成数字 */
.msg-content :deep(ul) {
  list-style: disc outside;
}
.msg-content :deep(ol) {
  list-style: decimal outside;
}
.msg-content :deep(ul ul) {
  list-style: circle outside;
}
.msg-content :deep(li) {
  margin-left: 0;
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
