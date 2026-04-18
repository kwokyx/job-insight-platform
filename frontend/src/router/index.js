import { createRouter, createWebHistory } from 'vue-router'

const APP_TITLE = 'Career Intelligence Platform'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: 'Dashboard' }
  },
  {
    path: '/jobs',
    name: 'Jobs',
    component: () => import('../views/JobsView.vue'),
    meta: { title: 'Jobs' }
  },
  {
    path: '/insights',
    name: 'Insights',
    component: () => import('../views/InsightsView.vue'),
    meta: { title: 'Insights' }
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportCenterView.vue'),
    meta: { title: 'Reports', requiresAuth: true }
  },
  {
    path: '/recommend',
    name: 'Recommend',
    component: () => import('../views/RecommendView.vue'),
    meta: { title: 'Recommendations', requiresAuth: true }
  },
  {
    path: '/ai',
    name: 'AiAssistant',
    component: () => import('../views/AiView.vue'),
    meta: { title: 'AI Assistant', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/ProfileView.vue'),
    meta: { title: 'Profile' }
  },
  {
    path: '/crawler',
    name: 'Crawler',
    component: () => import('../views/DataCollectorView.vue'),
    meta: { title: 'Data Collector', requiresAuth: true }
  },
  {
    path: '/openapi',
    name: 'OpenAPI',
    component: () => import('../views/OpenApiView.vue'),
    meta: { title: 'Open API' }
  },
  {
    path: '/admin',
    name: 'AdminDashboard',
    component: () => import('../views/AdminView.vue'),
    meta: { title: 'Admin Dashboard', requiresAuth: true, roleAuth: 1 }
  },
  {
    path: '/teacher',
    name: 'TeacherDashboard',
    component: () => import('../views/TeacherView.vue'),
    meta: { title: 'Teacher Dashboard', requiresAuth: true, roleAuth: 2 }
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('careerPlatform-access-token')
  const userStr = localStorage.getItem('careerPlatform-user')
  let user = null
  if (userStr) {
    try {
      user = JSON.parse(userStr)
    } catch (e) {}
  }

  if (to.meta.requiresAuth && !token) {
    next('/profile?login=true')
    return
  }

  if (to.meta.roleAuth !== undefined && user && user.roleType !== to.meta.roleAuth) {
    next('/')
    return
  }

  document.title = to.meta.title ? `${to.meta.title} | ${APP_TITLE}` : APP_TITLE
  next()
})

export default router
