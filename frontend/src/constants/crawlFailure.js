// 采集分片失败原因枚举（对应《分布式采集链路稳定性优化》观测规划）
// 后端待提供 /crawl/tasks/quality 的 failureCodes 字典；此处先作为前端默认常量
// 接入后端字典后，本常量仅作为 fallback

export const FAILURE_CODE = {
  NETWORK_ERROR: 'NETWORK_ERROR',        // 网络异常：超时、ConnectionReset 等
  AUTH_INVALID: 'AUTH_INVALID',          // 鉴权失效：Cookie/Token 过期
  RISK_CONTROL: 'RISK_CONTROL',          // 风控验证：验证码 / 429
  EMPTY_DATA: 'EMPTY_DATA',              // 数据为空：关键字段缺失
  PARSE_FAILED: 'PARSE_FAILED',          // 解析失败：页面结构变化
  RETRY_EXHAUSTED: 'RETRY_EXHAUSTED',    // 重试耗尽
  UNKNOWN: 'UNKNOWN'
}

export const FAILURE_LABEL = {
  [FAILURE_CODE.NETWORK_ERROR]: '网络异常',
  [FAILURE_CODE.AUTH_INVALID]: '鉴权失效',
  [FAILURE_CODE.RISK_CONTROL]: '风控/限流',
  [FAILURE_CODE.EMPTY_DATA]: '数据为空',
  [FAILURE_CODE.PARSE_FAILED]: '解析失败',
  [FAILURE_CODE.RETRY_EXHAUSTED]: '重试耗尽',
  [FAILURE_CODE.UNKNOWN]: '未知'
}

export const FAILURE_TONE = {
  [FAILURE_CODE.NETWORK_ERROR]: 'warn',
  [FAILURE_CODE.AUTH_INVALID]: 'danger',
  [FAILURE_CODE.RISK_CONTROL]: 'warn',
  [FAILURE_CODE.EMPTY_DATA]: 'info',
  [FAILURE_CODE.PARSE_FAILED]: 'danger',
  [FAILURE_CODE.RETRY_EXHAUSTED]: 'danger',
  [FAILURE_CODE.UNKNOWN]: 'info'
}

export const FAILURE_OPTIONS = Object.values(FAILURE_CODE).map((code) => ({
  value: code,
  label: FAILURE_LABEL[code],
  tone: FAILURE_TONE[code]
}))

export function resolveFailureLabel(code) {
  if (!code) return ''
  return FAILURE_LABEL[code] || code
}
