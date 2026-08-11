import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'
import HomeView from '@/views/HomeView.vue'
import LandingView from '@/views/LandingView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * Route meta:
 * - requiresAuth: route requires a valid token (redirect to /login otherwise)
 * - requiresAdmin: route requires the current session user to have ADMIN role
 * - requiresUser: route is for regular user product pages; admins are redirected to admin
 * - guestOnly: route is only for unauthenticated users (redirect to the role dashboard if logged in)
 */
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresAdmin?: boolean
    requiresUser?: boolean
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
      meta: { requiresAuth: true, requiresUser: true },
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
          path: 'trash',
          name: 'trash',
          component: () => import('../views/user/TrashView.vue'),
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
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if ((to.meta.requiresAuth || to.meta.guestOnly) && !auth.isLoggingOut) {
    await ensureSessionLoaded(auth)
  }
  const role = auth.user?.role?.toUpperCase() ?? ''
  const isAuthenticated = !!auth.user

  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && role !== 'ADMIN') {
    return { name: 'dashboard' }
  }

  if (to.meta.requiresUser && role === 'ADMIN') {
    return adminRedirectForUserRoute(String(to.name ?? ''))
  }

  if (to.meta.guestOnly && isAuthenticated) {
    return role === 'ADMIN' ? { name: 'admin-dashboard' } : { name: 'dashboard' }
  }
})

async function ensureSessionLoaded(auth: ReturnType<typeof useAuthStore>): Promise<void> {
  if (auth.user) return
  await auth.fetchCurrentUser()
}

function adminRedirectForUserRoute(routeName: string) {
  if (routeName === 'settings' || routeName === 'profile') return { name: 'admin-settings' }
  if (routeName === 'reports') return { name: 'admin-reports' }
  return { name: 'admin-dashboard' }
}

export default router
