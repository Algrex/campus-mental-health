<script setup>
import {computed, ref} from 'vue';
import {api} from '../api/portal.js';
import {date, localDay} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import {trendOption} from '../utils/charts.js';
import Icon from '../components/Icon.vue';
import Chart from '../components/Chart.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
const data = ref(null);
const {busy, error, reload} = useLoad(async () => {
  data.value = await api.profile();
});
const latestAssessmentLevel = computed(() => {
  const latest = data.value?.assessments[0];
  return latest?.ruleRiskLevel ?? latest?.riskLevel;
});
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">MY WELLBEING SPACE</span>
      <h1>照顾自己，从了解开始。</h1>
      <p>记录一点点感受，让每一天的变化都有迹可循。</p>
    </div>
  </div>
  <PageState :busy="busy" :error="error" @retry="reload"
    ><template v-if="data"
      ><section class="welcome-banner profile-banner">
        <div class="person">
          <span class="avatar large">{{ data.student.displayName[0] }}</span>
          <div>
            <h2>{{ data.student.displayName }}</h2>
            <p>{{ data.student.college }} · {{ data.student.major }}</p>
            <small
              >{{ data.student.yearName }} /
              {{ data.student.studentCode }}</small
            >
          </div>
        </div>
        <LevelBadge :level="data.student.riskLevel" />
      </section>
      <div class="student-actions">
        <router-link to="/assessments" class="card student-action"
          ><span class="stat-icon green"
            ><Icon name="clipboard" :size="26"
          /></span>
          <div>
            <h2>给自己一次倾听</h2>
            <p>用约 3 分钟，了解近两周的感受。</p>
            <span class="text-button"
              >开始心理自评<Icon name="arrow" :size="16"
            /></span></div></router-link
        ><router-link to="/check-ins" class="card student-action"
          ><span class="stat-icon amber"><Icon name="sun" :size="26" /></span>
          <div>
            <h2>今天，你还好吗？</h2>
            <p>
              {{
                data.checkIns.some((r) => r.checkDate === localDay())
                  ? '今天已经打卡，可以继续完善记录。'
                  : '记录心情、睡眠和压力，关照当下。'
              }}
            </p>
            <span class="text-button"
              >记录今日心情<Icon name="arrow" :size="16"
            /></span></div
        ></router-link>
      </div>
      <div class="dashboard-charts">
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>我的状态轨迹</h2>
              <p>分数只是线索，感受更值得被理解</p>
            </div>
            <span class="subtle-tag">最近记录</span>
          </div>
          <Chart
            :option="trendOption(data.trend.slice(-14), 'score', '自评分数')"
          />
          <div class="info-note">
            初始历史为演示生成；提交后的自评与打卡会保存到个人档案。
          </div>
        </article>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>最近心理自评</h2>
              <p>PHQ-9 · 近两周感受</p>
            </div>
            <Icon name="heart" />
          </div>
          <template v-if="data.assessments.length"
            ><div class="assessment-score">
              <strong>{{ data.assessments[0].score }}</strong
              ><span>/ 27</span>
            </div>
            <div class="centered">
              <LevelBadge :level="latestAssessmentLevel" />
              <p class="muted">关注等级综合参考提交时的自评与近期打卡记录。</p>
              <p class="muted">
                {{ date(data.assessments[0].createdAt, true) }}
              </p>
            </div>
            <p class="wellbeing-tip">
              {{
                latestAssessmentLevel === 'LOW' &&
                data.student.riskLevel === 'LOW'
                  ? '保持适合自己的生活节奏，也给休息留一点空间。'
                  : '如果最近感到困扰，可以主动联系学校心理中心或信任的人，获得支持。'
              }}
            </p></template
          >
          <div v-else class="state">完成一次自评，开始了解自己。</div>
        </article>
      </div>
      <article class="card">
        <div class="card-heading">
          <div>
            <h2>自评记录</h2>
            <p>每一次认真回应自己的感受</p>
          </div>
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>自评</th>
                <th>分数</th>
                <th>提交时关注</th>
                <th>来源</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.assessments" :key="row.id">
                <td>{{ date(row.createdAt, true) }}</td>
                <td>PHQ-9 心理自评</td>
                <td>{{ row.score }} / 27</td>
                <td>
                  <LevelBadge :level="row.ruleRiskLevel ?? row.riskLevel" />
                </td>
                <td class="muted">{{ row.sourceName }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!data.assessments.length" class="state">暂无自评记录</div>
      </article></template
    ></PageState
  >
</template>
