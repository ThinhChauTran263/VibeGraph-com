import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'
import HomeView from '@/views/HomeView.vue'
import LandingView from '@/views/LandingView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'

/**
 * Route meta:
 * - requiresAuth: route requires a valid token (redirect to /login otherwise)
 * - requiresAdmin: route requires the stored session user to have ADMIN role
 * - guestOnly: route is only for unauthenticated users (redirect to the role dashboard if logged in)
 */
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
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
          redirect: { name: 'settings' },
        },
        {
          path: 'settings',
          name: 'settings',
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
          path: 'subscription',
          name: 'subscription',
          component: () => import('../views/user/SubscriptionView.vue'),
        },
        {
          path: 'reports',
          name: 'reports',
          component: () => import('../views/user/ReportsView.vue'),
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('../views/user/NotificationsView.vue'),
        },
        {
          path: 'tutorial',
          name: 'tutorial',
          component: () => import('../views/user/TutorialView.vue'),
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('../components/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
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
        },
        {
          path: 'plans-credits',
          name: 'admin-plans-credits',
          component: () => import('../views/admin/PlansCreditsView.vue'),
        },
        {
          path: 'security',
          name: 'admin-security',
          component: () => import('../views/admin/SecurityView.vue'),
        },
        {
          path: 'audit',
          name: 'admin-audit',
          component: () => import('../views/admin/AuditView.vue'),
        },
        {
          path: 'system',
          name: 'admin-feature-flags',
          component: () => import('../views/admin/FeatureFlagsView.vue'),
        },
        {
          path: 'feature-flags',
          redirect: { name: 'admin-feature-flags' },
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: () => import('../views/admin/AnnouncementsView.vue'),
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: () => import('../views/admin/SettingsView.vue'),
        },
      ],
    },
  ],
})

// ─── Navigation guard ──────────────────────────────────────────────────────────
router.beforeEach((to) => {
  const isAuthenticated = !!getStoredUserRole()

  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && getStoredUserRole() !== 'ADMIN') {
    return { name: 'dashboard' }
  }

  if (to.meta.guestOnly && isAuthenticated) {
    return getStoredUserRole() === 'ADMIN' ? { name: 'admin-dashboard' } : { name: 'dashboard' }
  }
})

function getStoredUserRole(): string {
  const raw = localStorage.getItem('vg_user')
  if (!raw) return ''
  try {
    const parsed = JSON.parse(raw) as { role?: unknown }
    return typeof parsed.role === 'string' ? parsed.role.toUpperCase() : ''
  } catch {
    return ''
  }
}

export default router
