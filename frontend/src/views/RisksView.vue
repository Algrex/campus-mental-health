<script setup>
import {ref, computed} from 'vue';
import {api} from '../api/portal.js';
import {levels} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import {trendOption, donutOption} from '../utils/charts.js';
import Chart from '../components/Chart.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
import StudentDrawer from '../components/StudentDrawer.vue';
import Icon from '../components/Icon.vue';
// dashboard 数据用于趋势和等级分布，学生列表用于下方的关注名单。
const data = ref(null);
const rows = ref([]);
// 空字符串表示不限定关注等级；选择等级后只筛选名单，不改变上方统计和图表。
const level = ref('');
// 点击“查看变化”时保存学生 ID，由 StudentDrawer 加载对应档案详情。
const selected = ref('');
// 两个独立接口并行加载，均成功后更新页面数据；加载和失败重试由 useLoad 管理。
const {busy, error, reload} = useLoad(async () => {
  [data.value, rows.value] = await Promise.all([
    api.dashboard(),
    api.students(),
  ]);
});
// 名单始终排除 LOW（常规关注），再按所选等级过滤后端已授权的学生数据。
// riskLevel 来自后端业务规则，本页不调用机器学习模型重新计算等级。
const attention = computed(() =>
  rows.value.filter(
    (r) =>
      r.riskLevel !== 'LOW' && (!level.value || r.riskLevel === level.value),
  ),
);
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">INSIGHTS & CARE</span>
      <h1>关注与趋势</h1>
      <p>结合自评与日常记录，为主动关怀提供线索。</p>
    </div>
    <span class="subtle-tag"
      ><Icon name="shield" :size="15" />规则辅助评估</span
    >
  </div>
  <PageState :busy="busy" :error="error" @retry="reload"
    ><template v-if="data"
      ><div class="stat-grid">
        <article
          v-for="item in data.distribution"
          :key="item.level"
          class="card risk-stat"
          :style="{'--risk-color': levels[item.level].color}"
        >
          <span
            class="dot"
            :style="{background: levels[item.level].color}"
          ></span
          ><span>{{ levels[item.level].name }}</span
          ><strong>{{ item.value }}<small>人</small></strong>
          <p>
            {{
              {
                LOW: '保持日常关怀',
                MEDIUM: '建议主动沟通',
                HIGH: '安排重点跟进',
                CRITICAL: '优先人工联系',
              }[item.level]
            }}
          </p>
        </article>
      </div>
      <!-- 趋势使用每日测评分数均值，分布使用学生最新关注等级，统计含义不同。 -->
      <div class="dashboard-charts">
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>测评趋势</h2>
              <p>最近 14 天 · 每日测评分数均值</p>
            </div>
            <span class="subtle-tag">PHQ-9</span>
          </div>
          <Chart :option="trendOption(data.trend)" />
          <p class="chart-footnote">
            历史趋势为演示生成；新提交的自评和打卡会写入真实操作记录。
          </p>
        </article>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>等级分布</h2>
              <p>在册学生的最新关注等级</p>
            </div>
          </div>
          <Chart :option="donutOption(data.distribution)" :height="220" />
          <div class="legend-grid">
            <div v-for="item in data.distribution" :key="item.level">
              <span
                class="dot"
                :style="{background: levels[item.level].color}"
              ></span
              >{{ levels[item.level].name }}<strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>
      </div>
      <article class="card">
        <div class="card-heading">
          <div>
            <h2>关注名单</h2>
            <p>查看学生近期记录，进一步了解变化原因</p>
          </div>
          <select v-model="level" aria-label="筛选等级">
            <option value="">全部关注等级</option>
            <option
              v-for="key in ['MEDIUM', 'HIGH', 'CRITICAL']"
              :key="key"
              :value="key"
            >
              {{ levels[key].name }}
            </option>
          </select>
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>学生</th>
                <th>学院</th>
                <th>测评分数</th>
                <th>关注等级</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in attention" :key="row.id">
                <td>
                  <div class="person">
                    <span class="avatar small">{{ row.displayName[0] }}</span
                    ><strong>{{ row.displayName }}</strong>
                  </div>
                </td>
                <td>{{ row.college }}</td>
                <td>{{ row.currentScore }} / 27</td>
                <td><LevelBadge :level="row.riskLevel" /></td>
                <td>
                  <button class="text-button" @click="selected = row.id">
                    查看变化<Icon name="arrow" :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!attention.length" class="state">当前筛选没有关注记录</div>
      </article>
      <!-- 此处只展示规则说明，实际计分、打卡评估和预警更新由后端执行。 -->
      <div class="rule-note">
        <Icon name="shield" :size="20" />
        <div>
          <strong>关注规则透明可解释</strong>
          <p>
            测评总分 10 / 15 / 20 对应适度 / 重点 / 优先关注；第 9
            项非零优先人工复核。近 7 天至少 3 次睡眠不足 5
            小时，提升至重点关注；低心情与高压力同时出现，至少适度关注。分级用于演示工作流，不作为诊断。
          </p>
        </div>
      </div></template
    ></PageState
  ><StudentDrawer v-model="selected" />
</template>
