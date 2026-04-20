import { createRouter, createWebHistory } from 'vue-router'

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
    meta: { title: '智能推荐', requiresAuth: true }
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
    meta: { title: '个人主页' }
  },
  {
    path: '/crawler',
    name: 'Crawler',
    component: () => import('../views/DataCollectorView.vue'),
    meta: { title: '数据采集', requiresAuth: true }
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
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('careerPlatform-access-token')
  if (to.meta.requiresAuth && !token) {
    next('/profile?login=true')
    return
  }

  document.title = to.meta.title ? `${to.meta.title} | ${APP_TITLE}` : APP_TITLE
  next()
})

export default router
