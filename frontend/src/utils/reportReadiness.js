// 报告前置分析就绪状态的判断工具
//
// 当前策略：
//   - 前端优先使用后端 GET /reports/readiness
//   - 后端暂不可用时，再走本地兜底
//
// 本地兜底策略：
//   - 教师：直接使用现有真接口 fetchTeacherMaterialStatus 的 ready 字段
//   - 学生：用 localStorage 打标——用户在智能推荐页成功跑完一次推荐即视为"已完成"
//   - 管理员：无前置
// 本文件的 local 标记只在后端返回 null 时作为兜底。

const STUDENT_RECOMMEND_KEY_PREFIX = 'report.recommendDone.'

function safeStorage() {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch {
    return null
  }
}

export function markStudentRecommendDone(userId) {
  const storage = safeStorage()
  if (!storage || !userId) return
  storage.setItem(`${STUDENT_RECOMMEND_KEY_PREFIX}${userId}`, String(Date.now()))
}

export function clearStudentRecommendMark(userId) {
  const storage = safeStorage()
  if (!storage || !userId) return
  storage.removeItem(`${STUDENT_RECOMMEND_KEY_PREFIX}${userId}`)
}

export function isStudentRecommendDoneLocal(userId) {
  const storage = safeStorage()
  if (!storage || !userId) return false
  return Boolean(storage.getItem(`${STUDENT_RECOMMEND_KEY_PREFIX}${userId}`))
}

// 角色对应的默认且唯一的报告类型（与后端 ReportGenerationService 默认保持一致）
export const ROLE_REPORT_TYPE = {
  0: 'JOB_SEEKING',     // 学生
  1: 'OPERATIONS',      // 管理员
  2: 'SUPPLY_DEMAND'    // 教师
}

export const ROLE_REPORT_LABEL = {
  0: '个人求职分析报告',
  1: '平台运营分析报告',
  2: '班级供需分析报告'
}

// 角色 → 未就绪时的引导目标
export const ROLE_CTA = {
  0: { label: '去完成智能推荐', route: '/recommend', missingHint: '你还没有完成智能推荐分析，先生成一次推荐再来生成你的个人报告。' },
  2: { label: '去准备教学资料', route: '/teacher', missingHint: '生成教学供需报告前，请先在教学工作台上传课程、教学大纲和学生情况三份 Excel。' }
}
