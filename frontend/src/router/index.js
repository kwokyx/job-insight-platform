import { createRouter, createWebHistory } from 'vue-router'

const APP_TITLE = '职业情报平台'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '仪表盘' }
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
    meta: { title: 'AI 助手', requiresAuth: true }
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
    path: '/openapi',
    name: 'OpenAPI',
    component: () => import('../views/OpenApiView.vue'),
    meta: { title: '开放 API' }
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
