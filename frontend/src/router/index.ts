import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/projects',
      children: [
        { path: 'projects', name: 'projects', component: () => import('@/views/ProjectListView.vue') },
        { path: 'projects/:id', name: 'project-workspace', component: () => import('@/views/ProjectWorkspaceView.vue'), props: true },
        { path: 'reports', name: 'report-history', component: () => import('@/views/ReportHistoryView.vue') },
        { path: 'uml-editor', name: 'uml-editor', component: () => import('@/views/UmlEditorView.vue') },
        { path: 'ai-chat/:projectId?', name: 'ai-chat', component: () => import('@/views/AiChatView.vue'), props: true },
        { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if (to.name === 'login' && token) {
    next({ name: 'projects' })
  } else {
    next()
  }
})

export default router
