<script setup>
import {ref, onMounted, onBeforeUnmount, watch} from 'vue';
import * as echarts from 'echarts/core';
import {LineChart, PieChart, BarChart} from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';
echarts.use([
  LineChart,
  PieChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
]);
const props = defineProps({
  option: {type: Object, required: true},
  height: {type: [String, Number], default: 260},
});
const element = ref(null);
let chart;
let observer;
onMounted(() => {
  chart = echarts.init(element.value);
  chart.setOption(props.option);
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(element.value);
});
watch(
  () => props.option,
  (value) => chart?.setOption(value, true),
  {deep: true},
);
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="element"
    class="chart"
    :style="{height: `${height}px`}"
    role="img"
    aria-label="数据统计图表"
  ></div>
</template>
