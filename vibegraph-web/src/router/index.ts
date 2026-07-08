import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'
import HomeView from '@/views/HomeView.vue'
import LandingView from '@/views/LandingView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: LandingView,
    },
    {
      // Project management workspace ("dashboard") — import flows + project list.
      path: '/dashboard',
      name: 'dashboard',
      component: HomeView,
    },
    {
      path: '/projects/:projectId/graph',
      name: 'graph',
      component: GraphView,
    },
  ],
})

export default router
