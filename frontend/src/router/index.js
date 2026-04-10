import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '概览大盘' }
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
    meta: { title: '数据洞察' }
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
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

// Navigation Guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('career-platform-access-token')
  if (to.meta.requiresAuth && !token) {
    next('/profile?login=true')
  } else {
    document.title = `${to.meta.title} - 职业能力大数据平台`
    next()
  }
})

export default router
