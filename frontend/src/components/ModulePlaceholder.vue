<script setup>
defineProps({
  title: {type: String, required: true},
  owner: {type: String, required: true},
  description: {type: String, required: true},
});
</script>

<template>
  <section class="panel">
    <p class="eyebrow">功能模块 · 负责人 {{ owner }}</p>
    <h1>{{ title }}</h1>
    <p class="muted">{{ description }}</p>
    <el-alert
      title="模块骨架已就绪，业务功能待开发"
      type="info"
      :closable="false"
      show-icon
    />
    <p class="muted">后续按分工文档实现接口、页面与测试。</p>
  </section>
</template>
