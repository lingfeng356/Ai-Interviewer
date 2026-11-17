import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/storage'

const routes = [
  {
    path: '/',
    redirect: '/interview'
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/interview',
    name: 'interview',
    component: () => import('@/views/InterviewView.vue'),
    meta: { title: '面试主页' }
  },
  {
    path: '/report/:sessionId',
    name: 'report',
    component: () => import('@/views/ReportView.vue'),
    meta: { title: '面试报告' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/interview'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - AI 面试官平台` : 'AI 面试官平台'

  const hasToken = !!getToken()

  if (!to.meta.public && !hasToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && hasToken) {
    return { path: '/interview' }
  }
  return true
})

export default router
