import {levels} from './format.js';
export function trendOption(rows, field = 'score', label = '平均测评分数') {
  return {
    tooltip: {trigger: 'axis'},
    grid: {top: 20, bottom: 28, left: 35, right: 16},
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((r) =>
        (r.day || r.checkDate || r.createdAt?.slice(0, 10) || '').slice(5),
      ),
      axisLine: {show: false},
      axisTick: {show: false},
      axisLabel: {color: '#8a9692'},
    },
    yAxis: {
      type: 'value',
      min: 0,
      splitLine: {lineStyle: {color: '#edf1ef', type: 'dashed'}},
      axisLabel: {color: '#8a9692'},
    },
    series: [
      {
        name: label,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: {color: '#408474', width: 3},
        itemStyle: {color: '#408474'},
        areaStyle: {color: '#e2f1eb', opacity: 0.7},
        data: rows.map((r) => Math.round(Number(r[field]) * 10) / 10),
      },
    ],
  };
}
export function donutOption(rows) {
  return {
    tooltip: {trigger: 'item', formatter: '{b}：{c} 人 ({d}%)'},
    series: [
      {
        type: 'pie',
        radius: ['66%', '85%'],
        center: ['50%', '50%'],
        label: {show: false},
        itemStyle: {borderColor: '#fff', borderWidth: 4, borderRadius: 5},
        data: rows.map((r) => ({
          name: levels[r.level].name,
          value: r.value,
          itemStyle: {color: levels[r.level].color},
        })),
      },
    ],
  };
}
