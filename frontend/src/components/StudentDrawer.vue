<script setup>
import {ref, watch} from 'vue';
import {api} from '../api/portal.js';
import {date} from '../utils/format.js';
import {trendOption} from '../utils/charts.js';
import Chart from './Chart.vue';
import LevelBadge from './LevelBadge.vue';
import PageState from './PageState.vue';
const props = defineProps({modelValue: {type: String, default: ''}});
const emit = defineEmits(['update:modelValue']);
const data = ref(null);
const busy = ref(false);
const error = ref('');
const tab = ref('overview');
async function load() {
  const id = props.modelValue;
  if (!id) return;
  busy.value = true;
  error.value = '';
  try {
    const response = await api.student(id);
    if (id === props.modelValue) data.value = response;
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
}
watch(
  () => props.modelValue,
  () => {
    data.value = null;
    tab.value = 'overview';
    load();
  },
);
</script>
<template>
  <ElDrawer
    :model-value="!!modelValue"
    title="学生健康档案"
    size="min(720px, 96vw)"
    @update:model-value="emit('update:modelValue', '')"
    ><PageState :busy="busy" :error="error" @retry="load"
      ><div v-if="data" class="detail-content">
        <div class="profile-summary">
          <span class="avatar large">{{ data.student.displayName[0] }}</span>
          <div>
            <h2>{{ data.student.displayName }}</h2>
            <p>{{ data.student.college }} · {{ data.student.major }}</p>
            <small
              >{{ data.student.studentCode }} /
              {{ data.student.yearName }}</small
            >
          </div>
          <LevelBadge :level="data.student.riskLevel" />
        </div>
        <div class="detail-facts">
          <div>
            <small>最近测评分数</small
            ><strong>{{ data.student.currentScore }}<span> / 27</span></strong>
          </div>
          <div>
            <small>累计测评</small
            ><strong>{{ data.assessments.length }}<span> 次</span></strong>
          </div>
          <div>
            <small>近 30 条打卡</small
            ><strong>{{ data.checkIns.length }}<span> 条</span></strong>
          </div>
        </div>
        <div class="tabs">
          <button
            v-for="item in [
              {id: 'overview', label: '状态趋势'},
              {id: 'assessments', label: '测评记录'},
              {id: 'checkins', label: '日常打卡'},
            ]"
            :key="item.id"
            :class="{active: tab === item.id}"
            @click="tab = item.id"
          >
            {{ item.label }}
          </button>
        </div>
        <template v-if="tab === 'overview'"
          ><h3>测评与关注变化</h3>
          <Chart
            v-if="data.trend.length"
            :option="trendOption(data.trend.slice(-30), 'score', '测评分数')"
          />
          <p v-else class="state">暂无趋势记录</p>
          <div class="info-note">
            来源：{{
              data.student.sourceName
            }}。姓名与身份为虚构，初始测评与打卡历史为演示生成。
          </div></template
        ><template v-if="tab === 'assessments'"
          ><div
            v-for="item in data.assessments"
            :key="item.id"
            class="record-row"
          >
            <span class="score-circle">{{ item.score }}</span>
            <div>
              <strong>PHQ-9 心理自评</strong
              ><small
                >{{ date(item.createdAt, true) }} · {{ item.sourceName }}</small
              >
            </div>
            <LevelBadge :level="item.riskLevel" />
          </div>
          <div v-if="!data.assessments.length" class="state">
            暂无测评记录
          </div></template
        ><template v-if="tab === 'checkins'"
          ><div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>心情</th>
                  <th>睡眠</th>
                  <th>压力</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in data.checkIns" :key="item.id">
                  <td>
                    {{ item.checkDate
                    }}<small class="cell-note">{{ item.note }}</small>
                  </td>
                  <td>
                    {{
                      ['', '低落', '偏低', '平静', '愉快', '很好'][item.mood]
                    }}
                  </td>
                  <td>{{ item.sleepHours }} 小时</td>
                  <td>{{ item.stress }} / 5</td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
      </div></PageState
    ></ElDrawer
  >
</template>
