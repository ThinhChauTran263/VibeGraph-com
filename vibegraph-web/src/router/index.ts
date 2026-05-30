import { createRouter, createWebHistory } from 'vue-router'
import GraphView from '@/views/GraphView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/projects/default/graph',
    },
    {
      path: '/projects/:projectId/graph',
      name: 'graph',
      component: GraphView,
    },
  ],
})

export default router
