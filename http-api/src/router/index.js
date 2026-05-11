import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView.vue'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/player/:name',
    name: 'player',
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: () => import('../views/PlayerView.vue')
  },
  {
    path: '/npc-spawns',
    name: 'npc-spawns',
    component: () => import('../views/NpcSpawnsView.vue')
  },
  {
    path: '/qa',
    name: 'qa',
    component: () => import('../views/QaView.vue')
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

export default router
