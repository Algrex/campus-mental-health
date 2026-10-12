<script setup>
import {ref} from 'vue';
import {useRouter} from 'vue-router';
import {login, home} from '../state/session.js';
import Icon from '../components/Icon.vue';
const router = useRouter();
const username = ref('center');
const password = ref('Campus2026!');
const busy = ref(false);
const error = ref('');
const roles = [
  {
    id: 'center',
    name: '心理中心',
    description: '全校健康与关怀管理',
    icon: 'grid',
  },
  {
    id: 'counselor',
    name: '学院辅导员',
    description: '学生关注与日常随访',
    icon: 'users',
  },
  {
    id: 'student',
    name: '学生',
    description: '记录感受，认识自己',
    icon: 'heart',
  },
  {
    id: 'contact',
    name: '联系人',
    description: '接收学校关怀提醒',
    icon: 'mail',
  },
];
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    await login(username.value, password.value);
    await router.replace(home());
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="login-brand">
        <span class="brand-symbol"><Icon name="leaf" :size="28" /></span
        >心晴校园
      </div>
      <div class="story-body">
        <span class="eyebrow">A LITTLE CARE, A BRIGHTER CAMPUS</span>
        <h1>让每一份心情，<br />都有温暖的回应。</h1>
        <p>
          连接学生、辅导员与心理中心。<br />让日常记录成为理解的开始，让关怀及时抵达。
        </p>
        <div class="story-art">
          <div class="art-orbit orbit-one"></div>
          <div class="art-orbit orbit-two"></div>
          <div class="art-leaf leaf-one"></div>
          <div class="art-leaf leaf-two"></div>
          <div class="art-leaf leaf-three"></div>
          <div class="art-dot"></div>
          <span class="floating-note"
            ><Icon name="heart" :size="18" /> 在这里，安心做自己</span
          >
        </div>
      </div>
      <small class="story-footer">CAMPUS MENTAL HEALTH · 2026</small>
    </section>
    <section class="login-form-area">
      <div class="login-form">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>欢迎来到心晴校园</h2>
        <p class="muted">选择你的身份，开启今天的关怀之旅。</p>
        <div class="role-grid">
          <button
            v-for="role in roles"
            :key="role.id"
            class="role-card"
            :class="{active: username === role.id}"
            @click="username = role.id"
          >
            <Icon :name="role.icon" /><strong>{{ role.name }}</strong
            ><small>{{ role.description }}</small>
          </button>
        </div>
        <form @submit.prevent="submit">
          <label class="field"
            >登录账号<input
              v-model="username"
              autocomplete="username"
              required /></label
          ><label class="field"
            >密码<input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
          /></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="btn primary login-submit" :disabled="busy">
            {{ busy ? '正在登录…' : '进入平台'
            }}<Icon name="arrow" :size="18" />
          </button>
        </form>
        <div class="login-help">
          <Icon name="shield" :size="17" /><span
            >本地 Demo · 四个演示账号的初始密码为 <code>Campus2026!</code></span
          >
        </div>
        <p class="login-disclaimer">公开匿名样本与合成记录，仅用于功能演示。</p>
      </div>
    </section>
  </div>
</template>
