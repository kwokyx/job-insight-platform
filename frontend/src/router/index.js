import { createRouter, createWebHistory } from 'vue-router'
import { getRoleLabel, hasRequiredRole, ROLE } from '../utils/role'

const APP_TITLE = '职业能力大数据服务平台'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '数据大屏' }
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('../views/JobsView.vue'),
    meta: { title: '岗位大厅' }
  },
  {
    path: '/insights',
    name: 'Insights',
    component: () => import('../views/InsightsView.vue'),
    meta: { title: '行业洞察' }
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportCenterView.vue'),
    meta: { title: '报告中心', requiresAuth: true }
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
    meta: { title: 'AI 助手', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { title: '个人中心' }
  },
  {
    path: '/crawler',
    name: 'Crawler',
    component: () => import('../views/DataCollectorView.vue'),
    meta: { title: '数据采集监控', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  },
  {
    path: '/openapi',
    name: 'OpenAPI',
    component: () => import('../views/OpenApiView.vue'),
    meta: { title: '开放平台', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
  },
  {
    path: '/admin',
    name: 'AdminDashboard',
    component: () => import('../views/AdminView.vue'),
    meta: { title: '运营面板', requiresAuth: true, allowedRoles: [ROLE.ADMIN] }
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
    next(`/profile?login=true&redirect=${encodeURIComponent(to.fullPath)}`)
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

export default router
