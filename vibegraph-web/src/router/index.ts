import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'
import HomeView from '@/views/HomeView.vue'
import LandingView from '@/views/LandingView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'

/**
 * Route meta:
 * - requiresAuth: route requires a valid token (redirect to /login otherwise)
 * - guestOnly: route is only for unauthenticated users (redirect to /dashboard if logged in)
 */
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: LandingView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true },
    },
    {
      // Project management workspace ("dashboard") — import flows + project list.
      path: '/dashboard',
      name: 'dashboard',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/projects/:projectId/graph',
      name: 'graph',
      component: GraphView,
      meta: { requiresAuth: true },
    },
  ],
})

// ─── Navigation guard ──────────────────────────────────────────────────────────
router.beforeEach((to) => {
  const token = localStorage.getItem('vg_token')
  const isAuthenticated = !!token

  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.guestOnly && isAuthenticated) {
    return { name: 'dashboard' }
  }
})

export default router
