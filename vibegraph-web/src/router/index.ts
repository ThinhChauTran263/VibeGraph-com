import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
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
