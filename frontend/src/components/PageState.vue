<script setup>
defineProps({
  busy: Boolean,
  error: {type: String, default: ''},
  empty: Boolean,
});
defineEmits(['retry']);
</script>
<template>
  <div v-if="error" class="state error" role="alert">
    <strong>暂时无法加载</strong>
    <p>{{ error }}</p>
    <button class="btn" @click="$emit('retry')">重新加载</button>
  </div>
  <div v-else-if="busy" class="state">
    <span class="spinner"></span> 正在加载…
  </div>
  <div v-else-if="empty" class="state">
    <strong>这里暂时没有记录</strong>
    <p>调整筛选条件，或添加第一条记录。</p>
  </div>
  <slot v-else />
</template>
