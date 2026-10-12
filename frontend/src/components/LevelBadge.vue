<script setup>
import {levelName} from '../utils/format.js';
defineProps({level: {type: String, default: 'LOW'}});
</script>
<template>
  <span class="level-badge" :class="level.toLowerCase()"
    ><i></i>{{ levelName(level) }}</span
  >
</template>
