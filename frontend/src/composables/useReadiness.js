// 角色化前置条件统一入口（对应接口文档《二、4) 前端角色化流程优化》）
//
// 后端契约（GET /api/v1/readiness/{roleType}，2026-04-22 上线）：
//   { roleType, ready, missingFields[], nextAction:{path,label,detail}, updatedAt, dataVersion }
//
// 本 composable 默认走真接口。若后端返回失败（老环境或网络），再走已有接口的本地兜底逻辑。

import { ref, computed } from 'vue'
import {
  fetchCareerProfile,
  fetchCrawlQuality,
  fetchRoleReadiness,
  fetchTeacherMaterialStatus
} from '../api'
import { ROLE } from '../utils/role'

// 后端 missingFields 是字段名（profileSummary/skills/courses…），包一层可读标签
const STUDENT_LABELS = {
  profileSummary: '个人简介',
  targetCityCode: '目标城市',
  skills: '至少一项技能'
}
const TEACHER_LABELS = {
  courses: '课程数据',
  syllabus: '教学大纲',
  studentStatus: '学生情况'
}
const ADMIN_LABELS = {
  jobs: '采集数据'
}

function humanize(role, fields) {
  const dict = role === ROLE.TEACHER ? TEACHER_LABELS : role === ROLE.ADMIN ? ADMIN_LABELS : STUDENT_LABELS
  return (fields || []).map((f) => dict[f] || f)
}

// 把后端返回原样收敛到前端统一形态：nextAction 统一为 {label, route, hint}
// 兼容后端字段 path→route、detail→hint
function normalizeReadiness(raw, role) {
  const nextAction = raw?.nextAction
  const normalizedAction = nextAction
    ? {
        label: nextAction.label || '继续',
        route: nextAction.route || nextAction.path || null,
        hint: nextAction.hint || nextAction.detail || ''
      }
    : null
  return {
    ready: Boolean(raw?.ready),
    missingFields: humanize(role, raw?.missingFields),
    nextAction: raw?.ready ? null : normalizedAction,
    updatedAt: raw?.updatedAt || null,
    dataVersion: raw?.dataVersion || null
  }
}

// —— 以下 evalXxxReadiness 为后端接口不可用时的本地兜底 —— //

function evalStudentReadiness(profile) {
  const missing = []
  if (!profile?.majorId && !profile?.profile?.majorId) missing.push('专业方向')
  const targetCity = profile?.targetCityCode || profile?.profile?.targetCityCode
  if (!targetCity) missing.push('目标城市')
  const skills = profile?.skills || profile?.profile?.skills || []
  if (!Array.isArray(skills) || skills.length < 3) missing.push('至少 3 项技能')

  return {
    ready: missing.length === 0,
    missingFields: missing,
    nextAction: missing.length
      ? { label: '去完善简历与画像', route: '/profile', hint: '先补齐简历或画像字段，再开放推荐与报告。' }
      : null,
    updatedAt: profile?.updatedAt || null,
    dataVersion: profile?.dataVersion || null
  }
}

function evalTeacherReadiness(status) {
  const missing = []
  if (!status?.hasCurriculum) missing.push('课程数据')
  if (!status?.hasSyllabus) missing.push('教学大纲')
  if (!status?.hasStudentStatus) missing.push('学生情况')

  return {
    ready: missing.length === 0,
    missingFields: missing,
    nextAction: missing.length
      ? { label: '去教学工作台上传资料', route: '/teacher', hint: '上传课程、教学大纲、学生情况三份 Excel 后开放教学分析。' }
      : null,
    updatedAt: status?.updatedAt || null,
    dataVersion: status?.dataVersion || null
  }
}

function evalAdminReadiness(quality) {
  const total = quality?.totalRecords ?? quality?.total ?? 0
  const ready = total > 0
  return {
    ready,
    missingFields: ready ? [] : ['采集数据'],
    nextAction: ready
      ? null
      : { label: '去采集控制台', route: '/crawler', hint: '当前还没有采集到业务数据，先运行采集任务再开放运营分析。' },
    updatedAt: quality?.updatedAt || null,
    dataVersion: quality?.dataVersion || null
  }
}

const ROLE_API_KEY = {
  [ROLE.STUDENT]: 'STUDENT',
  [ROLE.TEACHER]: 'TEACHER',
  [ROLE.ADMIN]: 'ADMIN'
}

export function useReadiness(role, token) {
  const loading = ref(false)
  const error = ref(null)
  const state = ref({
    ready: false,
    missingFields: [],
    nextAction: null,
    updatedAt: null,
    dataVersion: null
  })

  async function loadFallback() {
    if (role === ROLE.STUDENT) {
      return evalStudentReadiness(await fetchCareerProfile(token))
    }
    if (role === ROLE.TEACHER) {
      return evalTeacherReadiness(await fetchTeacherMaterialStatus(token))
    }
    if (role === ROLE.ADMIN) {
      return evalAdminReadiness(await fetchCrawlQuality(token))
    }
    return { ready: true, missingFields: [], nextAction: null, updatedAt: null, dataVersion: null }
  }

  async function load() {
    if (!token) {
      error.value = new Error('未登录')
      return state.value
    }
    loading.value = true
    error.value = null
    try {
      // 优先走真接口 /readiness/{roleType}；返回体不是契约形态时回退本地
      const apiKey = ROLE_API_KEY[role]
      if (apiKey) {
        try {
          const raw = await fetchRoleReadiness(token, apiKey)
          if (raw && 'ready' in raw) {
            state.value = normalizeReadiness(raw, role)
            return state.value
          }
        } catch {
          // 后端未就绪或接口异常 → 走本地兜底，不抛到 UI
        }
      }
      state.value = await loadFallback()
    } catch (e) {
      error.value = e
      state.value = {
        ready: false,
        missingFields: ['数据加载失败'],
        nextAction: { label: '重试', route: null, hint: '前置状态加载失败，稍后再试。' },
        updatedAt: null,
        dataVersion: null
      }
    } finally {
      loading.value = false
    }
    return state.value
  }

  return {
    loading,
    error,
    state,
    ready: computed(() => state.value.ready),
    missingFields: computed(() => state.value.missingFields),
    nextAction: computed(() => state.value.nextAction),
    load
  }
}
