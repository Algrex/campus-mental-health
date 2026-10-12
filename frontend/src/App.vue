<script setup>
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ElMessage} from 'element-plus';
import {session, logout, refreshInbox, roleNames} from './state/session.js';
import Icon from './components/Icon.vue';
const route = useRoute();
const router = useRouter();
const menuOpen = ref(false);
const staffLinks = [
  {path: '/', title: '总览工作台', icon: 'grid'},
  {path: '/students', title: '学生档案', icon: 'users'},
  {path: '/risks', title: '关注与趋势', icon: 'chart'},
  {path: '/models', title: '模型实验室', icon: 'chart'},
  {path: '/alerts', title: '预警管理', icon: 'shield'},
  {path: '/follow-ups', title: '干预随访', icon: 'calendar'},
];
const studentLinks = [
  {path: '/profile', title: '我的健康档案', icon: 'heart'},
  {path: '/assessments', title: '心理自评', icon: 'clipboard'},
  {path: '/check-ins', title: '每日打卡', icon: 'sun'},
];
const links = computed(() => [
  ...(session.user?.role === 'STUDENT'
    ? studentLinks
    : session.user?.role === 'CONTACT'
      ? []
      : staffLinks),
  {path: '/notifications', title: '消息中心', icon: 'mail'},
]);
const unread = computed(() => session.inbox.filter((n) => !n.isRead).length);
const today = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date());
watch(
  () => route.path,
  async () => {
    menuOpen.value = false;
    if (session.user) {
      try {
        await refreshInbox();
      } catch {
        /* The destination page displays service errors. */
      }
    }
  },
  {immediate: true},
);
async function signOut() {
  try {
    await logout();
    router.replace('/login');
  } catch (e) {
    ElMessage.error(e.message);
  }
}
</script>
<template>
  <router-view v-if="route.path === '/login'" />
  <div v-else-if="session.user" class="app-shell">
    <div v-if="menuOpen" class="nav-backdrop" @click="menuOpen = false"></div>
    <aside class="sidebar" :class="{open: menuOpen}">
      <router-link :to="links[0].path" class="brand"
        ><span class="brand-symbol"><Icon name="leaf" :size="25" /></span
        ><span>心晴校园<small>CAMPUS WELLBEING</small></span></router-link
      >
      <div class="nav-caption">
        {{
          session.user.role === 'STUDENT'
            ? '我的空间'
            : session.user.role === 'CONTACT'
              ? '关怀支持'
              : '校园健康管理'
        }}
      </div>
      <nav>
        <router-link
          v-for="link in links"
          :key="link.path"
          :to="link.path"
          class="nav-link"
          :class="{selected: route.path === link.path}"
          ><Icon :name="link.icon" /><span>{{ link.title }}</span
          ><span
            v-if="link.path === '/notifications' && unread"
            class="nav-count"
            >{{ unread }}</span
          ></router-link
        >
      </nav>
      <div class="sidebar-bottom">
        <div class="care-card">
          <Icon name="heart" :size="22" /><strong
            >每一份感受，都值得被看见</strong
          >
          <p>从一次倾听开始，让关怀更近一点。</p>
        </div>
        <span class="demo-mark"><i></i> 本地演示环境</span>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="topbar-title">
          <button
            class="icon-button mobile-menu"
            aria-label="打开导航"
            @click="menuOpen = !menuOpen"
          >
            <Icon name="menu" /></button
          ><span>校园心理健康平台</span><span class="topbar-divider">/</span
          ><strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-right">
          <span class="today">{{ today }}</span
          ><router-link
            to="/notifications"
            class="icon-button notification-button"
            aria-label="查看消息"
            ><Icon name="bell" /><i v-if="unread"></i></router-link
          ><span class="avatar">{{
            session.user.displayName.slice(0, 1)
          }}</span>
          <div class="user-label">
            <strong>{{ session.user.displayName }}</strong
            ><small>{{ roleNames[session.user.role] }}</small>
          </div>
          <button
            class="icon-button"
            aria-label="退出登录"
            title="退出登录"
            @click="signOut"
          >
            <Icon name="logout" :size="18" />
          </button>
        </div>
      </header>
      <main class="main-content"><router-view /></main>
      <footer class="app-footer">
        <span>心晴校园 · 让关怀触手可及</span
        ><span>演示数据 · 辅助关注与人工复核，不替代诊断</span>
      </footer>
    </div>
  </div>
</template>
