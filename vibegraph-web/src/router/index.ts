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
      path: '/',
      component: () => import('../components/layouts/UserLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: HomeView,
        },
        {
          path: 'projects/:projectId/graph',
          name: 'graph',
          component: GraphView,
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../views/user/ProfileView.vue'),
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('../views/user/ProjectsView.vue'),
        },
        {
          path: 'api-keys',
          name: 'api-keys',
          component: () => import('../views/user/ApiKeysView.vue'),
        },
        {
          path: 'usage',
          name: 'usage',
          component: () => import('../views/user/UsageView.vue'),
        },
        {
          path: 'reports',
          name: 'reports',
          component: () => import('../views/user/ReportsView.vue'),
        }
      ]
    },
    {
      path: '/admin',
      component: () => import('../components/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true }, // Ideally should also check for admin role
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('../views/admin/DashboardView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('../views/admin/UsersTableView.vue'),
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('../views/admin/AdminReportsView.vue'),
        }
      ]
    }
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
