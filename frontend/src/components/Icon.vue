<script setup>
defineProps({
  name: {type: String, default: 'leaf'},
  size: {type: [Number, String], default: 20},
});
const paths = {
  leaf: 'M20 4c-8-2-16 2-16 8a8 8 0 0 0 8 8c6 0 10-8 8-16ZM4 20l10-10M8 16v-5M8 16h5',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M20 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  chart: 'M3 3v18h18M7 14l4-5 4 3 6-8',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  calendar: 'M4 5h16v16H4zM16 3v4M8 3v4M4 11h16M8 15h2M14 15h2',
  heart:
    'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  clipboard: 'M9 4H5v17h14V4h-4M9 2h6v4H9zM8 11h8M8 16h5',
  mail: 'M3 5h18v14H3zM3 5l9 7 9-7',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  search: 'M21 21l-5-5M18 10a8 8 0 1 0-16 0 8 8 0 0 0 16 0',
  logout: 'M9 3H3v18h6M9 12h12M16 7l5 5-5 5',
  plus: 'M12 5v14M5 12h14',
  check: 'M5 12l4 4L19 6',
  down: 'M6 9l6 6 6-6',
  shield: 'M12 3l9 4v5c0 5-9 9-9 9s-9-4-9-9V7zM8 12l3 3 5-6',
  sun: 'M12 3V1M12 23v-2M3 12H1M23 12h-2M5 5l-2-2M21 21l-2-2M5 19l-2 2M21 3l-2 2M17 12a5 5 0 1 0-10 0 5 5 0 0 0 10 0',
  menu: 'M3 6h18M3 12h18M3 18h18',
  download: 'M12 3v12M7 10l5 5 5-5M4 16v5h16v-5',
};
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.leaf" />
  </svg>
</template>
