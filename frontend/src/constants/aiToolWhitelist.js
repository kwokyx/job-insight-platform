// AI Agent 按角色的工具白名单
// 对应接口文档《一、12) AI 助手》及《二、4.4 AI 助手按角色走不同 Agent 模式》
//
// 后端最终以 errorCode=AI_TOOL_FORBIDDEN 统一拒绝越权调用（已在 errorMap.js 映射）
// 前端在提交前先按白名单过滤，是前置的 UX 保护，不替代后端校验

import { ROLE } from '../utils/role'

// 按文档 218-222 行定义
export const ROLE_TOOL_WHITELIST = {
  [ROLE.STUDENT]: ['RESUME_PARSE', 'PROFILE_IMPORT', 'JOB_MATCH', 'SKILL_GAP', 'SALARY_INSIGHT'],
  [ROLE.TEACHER]: ['COURSE_MATCH', 'SYLLABUS_ANALYZE', 'TEACHING_REFORM', 'REPORT_ASSIST'],
  [ROLE.ADMIN]: ['OPS_INSIGHT', 'USER_GOVERNANCE', 'DATA_QUALITY_CHECK', 'REPORT_GOVERNANCE']
}

// 前端历史上沿用的小写 tool key（如 skill_gap、job_match）到文档工具枚举的映射
// 让现有硬编码 toolOptions 能继续使用同一份白名单
const LEGACY_TOOL_TO_DOC = {
  skill_gap: 'SKILL_GAP',
  job_match: 'JOB_MATCH',
  salary_insight: 'SALARY_INSIGHT',
  profile_snapshot: 'PROFILE_IMPORT',
  resume_parse: 'RESUME_PARSE',
  career_path: 'SKILL_GAP',       // 职业路径归入技能差距语境
  market_overview: 'OPS_INSIGHT',
  course_match: 'COURSE_MATCH',
  syllabus_analyze: 'SYLLABUS_ANALYZE',
  teaching_reform: 'TEACHING_REFORM',
  report_assist: 'REPORT_ASSIST',
  ops_insight: 'OPS_INSIGHT',
  user_governance: 'USER_GOVERNANCE',
  data_quality_check: 'DATA_QUALITY_CHECK',
  report_governance: 'REPORT_GOVERNANCE',
  auto: 'AUTO'
}

export function normalizeToolKey(value) {
  if (!value) return ''
  const raw = String(value).toUpperCase()
  if (raw === 'AUTO') return 'AUTO'
  // 已经是文档大写枚举
  if (/^[A-Z_]+$/.test(value) && value === raw) return raw
  return LEGACY_TOOL_TO_DOC[String(value).toLowerCase()] || raw
}

// 判断某个 tool 对当前角色是否可用；auto 永远放行，由后端决定选哪个工具
export function isToolAllowed(toolValue, roleType) {
  const key = normalizeToolKey(toolValue)
  if (key === 'AUTO') return true
  const list = ROLE_TOOL_WHITELIST[roleType] || []
  return list.includes(key)
}

// 过滤一组 tool 选项，默认保留 auto 项
export function filterToolsByRole(options, roleType) {
  if (!Array.isArray(options)) return []
  return options.filter((opt) => isToolAllowed(opt.value || opt.tool || opt.key, roleType))
}

// 默认让模型自主决定是否调用工具（function calling），因此优先返回 auto
export function safeDefaultTool(options, roleType) {
  const allowed = filterToolsByRole(options, roleType)
  const autoOption = allowed.find((o) => (o.value || o.tool || o.key) === 'auto')
  if (autoOption) return 'auto'
  const first = allowed[0]
  return first ? (first.value || first.tool || first.key) : 'auto'
}
