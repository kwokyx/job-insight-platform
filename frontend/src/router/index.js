import { createRouter, createWebHistory } from 'vue-router'
import { getRoleLabel, hasRequiredRole, ROLE } from '../utils/role'
import { onApiError } from '../api'
import { isAuthError, isForbiddenError, mapErrorMessage } from '../utils/errorMap'
import { useToast } from '../composables/useToast'

const APP_TITLE = '职业情报平台'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('../views/JobsView.vue'),
    meta: { title: '职位' }
  },
  {
    path: '/insights',
    name: 'Insights',
    component: () => import('../views/InsightsView.vue'),
    meta: { title: '洞察分析' }
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportCenterView.vue'),
    meta: { title: '分析报告', requiresAuth: true }
  },
  {
    path: '/recommend',
    name: 'Recommend',
    component: () => import('../views/RecommendView.vue'),
    meta: { title: '智能推荐', requiresAuth: true, allowedRoles: [ROLE.STUDENT, ROLE.TEACHER, ROLE.ADMIN] }
  },
  {
    path: '/ai',
    name: 'AiAssistant',
    component: () => import('../views/AiView.vue'),
    meta: { title: '智能助手', requiresAuth: true, fullBleed: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/ProfileView.vue'),
    // fullBleed：去掉外层 1360px 居中约束和 main padding，让左侧栏直接贴到视口左边
    meta: { title: '个人主页', fullBleed: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    // fullBleed 让顶栏 + 主区不再叠加 padding，登录卡可以独立掌控居中布局
    meta: { title: '登录', fullBleed: true }
  },
  {
    path: '/crawler',
    name: 'Crawler',
    component: () => import('../views/DataCollectorView.vue'),
    meta: { title: '数据采集', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  },
  {
    path: '/console',
    name: 'Console',
    component: () => import('../views/ConsoleView.vue'),
    meta: { title: 'API 控制台', requiresAuth: true }
  },
  {
    path: '/openapi',
    component: () => import('../views/openapi/OpenApiShell.vue'),
    meta: { title: '开放 API', fullBleed: true },
    redirect: '/openapi/intro',
    children: [
      {
        path: 'intro',
        name: 'OpenApiIntro',
        component: () => import('../views/openapi/OpenApiIntro.vue'),
        meta: { title: 'API 总览' }
      },
      {
        path: 'quickstart',
        name: 'OpenApiQuickstart',
        component: () => import('../views/openapi/OpenApiQuickstart.vue'),
        meta: { title: '快速开始' }
      },
      {
        path: 'auth',
        name: 'OpenApiAuth',
        component: () => import('../views/openapi/OpenApiAuth.vue'),
        meta: { title: '认证与密钥' }
      },
      {
        path: 'examples',
        name: 'OpenApiExamples',
        component: () => import('../views/openapi/OpenApiExamples.vue'),
        meta: { title: '示例接口' }
      },
      {
        path: 'errors',
        name: 'OpenApiErrors',
        component: () => import('../views/openapi/OpenApiErrors.vue'),
        meta: { title: '错误码' }
      }
    ]
  },
  {
    path: '/admin',
    name: 'AdminDashboard',
    component: () => import('../views/AdminView.vue'),
    meta: { title: '运营面板', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  },
  {
    path: '/admin/users',
    name: 'UserManage',
    component: () => import('../views/UserManageView.vue'),
    meta: { title: '用户管理', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  },
  {
    path: '/teacher',
    name: 'TeacherDashboard',
    component: () => import('../views/TeacherView.vue'),
    meta: { title: '教师工作台', requiresAuth: true, allowedRoles: [ROLE.TEACHER, ROLE.ADMIN] }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/ForbiddenView.vue'),
    meta: { title: '无权限访问' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('careerPlatform-access-token')
  const user = readStoredUser()

  if (to.meta.requiresAuth && !token) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  if (to.meta.allowedRoles && token && !hasRequiredRole(user, to.meta.allowedRoles)) {
    next({
      path: '/403',
      query: {
        from: to.fullPath,
        required: to.meta.allowedRoles.map(getRoleLabel).join(' / ')
      }
    })
    return
  }

  document.title = to.meta.title ? `${to.meta.title} | ${APP_TITLE}` : APP_TITLE
  next()
})

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem('careerPlatform-user') || 'null')
  } catch {
    localStorage.removeItem('careerPlatform-user')
    return null
  }
}

// 全局 API 错误 → 路由联动 + Toast
// 401：清登录态，跳 /login 并带 redirect；403：跳 /403；其他错误统一 Toast 文案
// 短时间内重复错误做节流，避免一次页面加载触发多次跳转/弹窗
let lastAuthRedirectAt = 0
let lastForbiddenRedirectAt = 0
let lastToastAt = 0
const REDIRECT_DEBOUNCE_MS = 1500
const TOAST_DEBOUNCE_MS = 800

onApiError((err) => {
  const now = Date.now()
  if (isAuthError(err)) {
    if (now - lastAuthRedirectAt < REDIRECT_DEBOUNCE_MS) return
    lastAuthRedirectAt = now
    try {
      localStorage.removeItem('careerPlatform-access-token')
      localStorage.removeItem('careerPlatform-refresh-token')
      localStorage.removeItem('careerPlatform-user')
      localStorage.removeItem('careerPlatform-access-expires')
    } catch { /* localStorage 不可用时降级忽略 */ }
    useToast().error(mapErrorMessage(err))
    const current = router.currentRoute.value
    if (current.name !== 'Login') {
      router.replace(`/login?redirect=${encodeURIComponent(current.fullPath)}`)
    }
    return
  }

  if (isForbiddenError(err)) {
    if (now - lastForbiddenRedirectAt < REDIRECT_DEBOUNCE_MS) return
    lastForbiddenRedirectAt = now
    useToast().error(mapErrorMessage(err))
    const current = router.currentRoute.value
    if (current.name !== 'Forbidden') {
      router.replace({ path: '/403', query: { from: current.fullPath } })
    }
    return
  }

  if (now - lastToastAt < TOAST_DEBOUNCE_MS) return
  lastToastAt = now
  useToast().error(mapErrorMessage(err))
})

export default router
