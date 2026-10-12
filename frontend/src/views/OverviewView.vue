<script setup>
import {onMounted, onUnmounted, ref} from 'vue';
import {getSystemInfo} from '../api/system.js';

const state = ref('loading');
const info = ref(null);
const error = ref('');
let controller;

async function refresh() {
  controller?.abort();
  controller = new AbortController();
  state.value = 'loading';
  error.value = '';
  try {
    info.value = await getSystemInfo({signal: controller.signal});
    state.value = 'ready';
  } catch (cause) {
    if (cause.name === 'AbortError') return;
    error.value = cause.message;
    state.value = 'error';
  }
}

onMounted(refresh);
onUnmounted(() => controller?.abort());
</script>

<template>
  <section class="panel">
    <p class="eyebrow">CAMPUS MENTAL HEALTH</p>
    <h1>校园心理健康监测平台</h1>
    <p class="muted">团队协作工程骨架 · 业务模块尚待实现</p>
    <div class="service-status">
      <el-tag v-if="state === 'loading'" type="info">正在连接后端</el-tag>
      <el-tag v-else-if="state === 'ready'" type="success">后端已连接</el-tag>
      <el-tag v-else type="warning">后端未连接</el-tag>
      <el-button size="small" :loading="state === 'loading'" @click="refresh">
        重新检查
      </el-button>
    </div>
    <p v-if="state === 'error'" role="alert" class="muted">
      {{ error }}。请启动后端后重新检查。
    </p>
    <p v-if="info" class="muted">{{ info.name }} · {{ info.stage }}</p>
  </section>
  <section class="module-grid" aria-label="团队模块">
    <article
      v-for="module in info?.modules ?? []"
      :key="module.key"
      class="panel"
    >
      <p class="eyebrow">{{ module.owner }}</p>
      <h2>{{ module.title }}</h2>
      <el-tag type="info">待开发</el-tag>
    </article>
  </section>
</template>
