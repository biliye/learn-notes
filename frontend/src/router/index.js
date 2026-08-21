import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/docs' },
      { path: 'docs', name: 'docs', component: () => import('../views/DocsHomeView.vue') },
      // 新建页必须放在 docs/:id 之前，否则 "new" 会被当成文档 id
      { path: 'docs/new', name: 'docNew', component: () => import('../views/DocEditView.vue') },
      { path: 'docs/:id', name: 'doc', component: () => import('../views/DocView.vue') },
      { path: 'docs/:id/edit', name: 'docEdit', component: () => import('../views/DocEditView.vue') },
      { path: 'catalog', name: 'catalog', component: () => import('../views/CatalogManageView.vue') },
      { path: 'inbox', name: 'inbox', component: () => import('../views/InboxView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/docs' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局登录守卫（R25）：无 token 且非公开页 → 跳登录并带 redirect
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.token) {
    return { name: 'docs' }
  }
  return true
})

export default router
