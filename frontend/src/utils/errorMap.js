// 统一错误文案映射 —— 前端所有网络错误都应走这里
// 对应接口文档《二、2) 用户友好报错》

export const ERROR_TEXT = {
  UNAUTHORIZED: '登录状态已失效，请重新登录',
  FORBIDDEN: '权限不足，当前账号无法访问该功能',
  RATE_LIMITED: '操作过于频繁，请稍后重试',
  SERVER_ERROR: '系统繁忙，请稍后重试',
  NETWORK_ERROR: '网络连接异常，请检查网络后重试',
  NOT_FOUND: '请求的资源不存在',
  BAD_REQUEST: '请求参数有误，请检查后重试'
}

// AI Agent 工具白名单拒绝的专用错误码（文档 223 行）
const BUSINESS_CODE_TEXT = {
  AI_TOOL_FORBIDDEN: '权限不足，当前账号无法访问该能力'
}

// 把 ApiError 或任意 Error 映射为用户可读文案
// 规则优先级：业务 errorCode > HTTP status > 原始 message > 兜底
export function mapErrorMessage(err) {
  if (!err) return ERROR_TEXT.SERVER_ERROR

  const errorCode = err.errorCode
  if (errorCode && BUSINESS_CODE_TEXT[errorCode]) {
    return BUSINESS_CODE_TEXT[errorCode]
  }

  const status = err.status
  if (status === 401) return ERROR_TEXT.UNAUTHORIZED
  if (status === 403) return ERROR_TEXT.FORBIDDEN
  if (status === 404) return ERROR_TEXT.NOT_FOUND
  if (status === 429) return ERROR_TEXT.RATE_LIMITED
  if (status === 400 || status === 422) {
    return err.message || ERROR_TEXT.BAD_REQUEST
  }
  if (status >= 500) return ERROR_TEXT.SERVER_ERROR

  if (err.isNetworkError) return ERROR_TEXT.NETWORK_ERROR

  return err.message || ERROR_TEXT.SERVER_ERROR
}

// 判断错误是否属于"鉴权失效"类，路由守卫/顶层拦截用来决定是否踢到登录页
export function isAuthError(err) {
  return err?.status === 401
}

// 判断是否属于"角色无权"，用来决定是否跳 /403
export function isForbiddenError(err) {
  return err?.status === 403
}
