import {createRouter, createWebHistory} from 'vue-router';
import {session, loadSession, home} from '../state/session.js';
const staff = ['CENTER', 'COUNSELOR'];
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: {title: '登录'},
  },
  {
    path: '/',
    component: () => import('../views/OverviewView.vue'),
    meta: {title: '总览工作台', roles: staff},
  },
  {
    path: '/students',
    component: () => import('../views/StudentsView.vue'),
    meta: {title: '学生档案', roles: staff},
  },
  {
    path: '/risks',
    component: () => import('../views/RisksView.vue'),
    meta: {title: '关注与趋势', roles: staff},
  },
  {
    path: '/models',
    component: () => import('../views/ModelsView.vue'),
    meta: {title: '模型实验室', roles: staff},
  },
  {
    path: '/alerts',
    component: () => import('../views/AlertsView.vue'),
    meta: {title: '预警管理', roles: staff},
  },
  {
    path: '/follow-ups',
    component: () => import('../views/FollowUpsView.vue'),
    meta: {title: '干预随访', roles: staff},
  },
  {
    path: '/profile',
    component: () => import('../views/ProfileView.vue'),
    meta: {title: '我的健康档案', roles: ['STUDENT']},
  },
  {
    path: '/assessments',
    component: () => import('../views/AssessmentView.vue'),
    meta: {title: '心理自评', roles: ['STUDENT']},
  },
  {
    path: '/check-ins',
    component: () => import('../views/CheckInView.vue'),
    meta: {title: '每日打卡', roles: ['STUDENT']},
  },
  {
    path: '/notifications',
    component: () => import('../views/NotificationsView.vue'),
    meta: {
      title: '消息中心',
      roles: ['CENTER', 'COUNSELOR', 'STUDENT', 'CONTACT'],
    },
  },
  {path: '/:pathMatch(.*)*', redirect: () => home()},
];
const router = createRouter({history: createWebHistory(), routes});
router.beforeEach(async (to) => {
  if (!session.loaded) {
    try {
      await loadSession();
    } catch {
      if (to.path !== '/login') return '/login';
    }
  }
  if (to.path === '/login') return session.user ? home() : true;
  if (!session.user) return '/login';
  if (!to.meta.roles?.includes(session.user.role)) return home();
});
router.afterEach((to) => {
  document.title = `${to.meta.title || '工作台'} · 心晴校园`;
});
window.addEventListener('session-expired', () => {
  session.user = null;
  session.loaded = false;
  router.replace('/login');
});
export default router;
