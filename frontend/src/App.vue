<script setup>
const navigation = [
  ['/', '工程概览'],
  ['/auth/login', '账号与权限'],
  ['/student/profile', '学生档案'],
  ['/student/assessments', '心理测评'],
  ['/student/check-ins', '自评打卡'],
  ['/workspace/students', '学生管理'],
  ['/workspace/risks', '风险与趋势'],
  ['/workspace/alerts', '预警通知'],
  ['/workspace/interventions', '干预随访'],
  ['/contact/notifications', '联系人通知'],
];
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">校园心理健康<span>Campus Mental Health</span></div>
      <nav aria-label="主导航">
        <RouterLink v-for="[path, label] in navigation" :key="path" :to="path">
          {{ label }}
        </RouterLink>
      </nav>
      <p class="sidebar-note">课程设计 · 工程骨架</p>
    </aside>
    <main class="content"><RouterView /></main>
  </div>
</template>
