<script setup>
import {ref, computed} from 'vue';
import {useRouter} from 'vue-router';
import {api} from '../api/portal.js';
import {session} from '../state/session.js';
import {date, levels, statuses} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import {trendOption, donutOption} from '../utils/charts.js';
import Icon from '../components/Icon.vue';
import Chart from '../components/Chart.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
import CaseDrawer from '../components/CaseDrawer.vue';
const router = useRouter();
// dashboard 接口统一返回统计、趋势、等级分布、优先个案和随访数据。
const data = ref(null);
// selected 保存预警个案 ID；优先关注和随访列表共用同一个详情抽屉。
const selected = ref('');
// useLoad 管理加载和错误状态，reload 供页面刷新、失败重试及个案更新后复用。
const {busy, error, reload} = useLoad(async () => {
  data.value = await api.dashboard();
});
const followUps = computed(() =>
  (data.value?.followUps || []).filter(
    (item) => item.followUpStatus === 'PLANNED' && item.status !== 'CLOSED',
  ),
);
const stats = computed(() =>
  data.value
    ? [
        {
          label: '学生档案',
          value: data.value.stats.students,
          unit: '人',
          icon: 'users',
          tone: 'green',
          note: '在册学生健康档案',
        },
        {
          // 展示后端计算的覆盖率，前端仅添加百分号和对应进度条。
          label: '测评覆盖率',
          value: data.value.stats.assessmentRate,
          unit: '%',
          icon: 'clipboard',
          tone: 'blue',
          note: '已完成至少一次心理自评',
        },
        {
          // 关注人数来自业务规则分级，与模型实验室的预测概率不同。
          label: '需要关注',
          value: data.value.stats.attention,
          unit: '人',
          icon: 'heart',
          tone: 'amber',
          note: '按当前规则分级的学生',
        },
        {
          // pending 表示待受理数量，activeAlerts 表示仍在跟进的个案总数。
          label: '待受理预警',
          value: data.value.stats.pending,
          unit: '条',
          icon: 'shield',
          tone: 'rose',
          note: `${data.value.stats.activeAlerts} 个个案正在持续跟进`,
        },
      ]
    : [],
);
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">WELLBEING OVERVIEW</span>
      <h1>
        你好，{{ session.user.displayName }}<span class="greeting-dot">。</span>
      </h1>
      <p>看见变化，及时回应。一起照顾好校园里的每一个人。</p>
    </div>
    <button class="btn" @click="reload">
      <Icon name="calendar" :size="17" />最近 14 天
    </button>
  </div>
  <PageState :busy="busy" :error="error" @retry="reload"
    ><template v-if="data"
      ><section class="welcome-banner">
        <div>
          <span class="banner-label"><i></i> 今日关怀</span>
          <h2>让关注成为行动，让支持持续发生。</h2>
          <p>
            还有 <strong>{{ data.stats.pending }}</strong> 条提醒等待受理，{{
              data.stats.activeAlerts
            }}
            个个案正在跟进。
          </p>
          <button class="text-button" @click="router.push('/alerts')">
            查看关注名单 <Icon name="arrow" :size="17" />
          </button>
        </div>
        <div class="banner-art" aria-hidden="true">
          <div class="banner-circle"></div>
          <Icon name="leaf" :size="108" /><span class="banner-sprout">✦</span>
        </div>
      </section>
      <section class="stat-grid">
        <article v-for="stat in stats" :key="stat.label" class="card stat-card">
          <div class="stat-top">
            <span>{{ stat.label }}</span
            ><span class="stat-icon" :class="stat.tone"
              ><Icon :name="stat.icon" :size="21"
            /></span>
          </div>
          <div class="stat-number">
            {{ stat.value }}<small>{{ stat.unit }}</small>
          </div>
          <p>{{ stat.note }}</p>
          <div v-if="stat.unit === '%'" class="mini-progress">
            <i :style="{width: stat.value + '%'}"></i>
          </div>
        </article>
      </section>
      <section class="dashboard-charts">
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>整体状态趋势</h2>
              <p>每日平均测评分数 · 关注变化而非单次结果</p>
            </div>
            <span class="subtle-tag">近 14 天</span>
          </div>
          <Chart
            v-if="data.trend.length"
            :option="trendOption(data.trend)"
            :height="237"
          />
          <div v-else class="state">有记录后会显示趋势</div>
          <div class="chart-footnote">
            <span class="dot green-dot"></span>测评分数均值<span class="muted"
              >演示历史由合成记录生成</span
            >
          </div>
        </article>
        <article class="card distribution-card">
          <div class="card-heading">
            <div>
              <h2>关注等级分布</h2>
              <p>当前学生状态概览</p>
            </div>
            <Icon name="chart" :size="19" />
          </div>
          <div class="donut-wrap">
            <Chart :option="donutOption(data.distribution)" :height="180" />
            <div class="donut-center">
              <strong>{{ data.stats.students }}</strong
              ><span>学生总数</span>
            </div>
          </div>
          <div class="legend-grid">
            <div v-for="item in data.distribution" :key="item.level">
              <span
                class="dot"
                :style="{background: levels[item.level].color}"
              ></span
              ><span>{{ levels[item.level].name }}</span
              ><strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>
      </section>
      <section class="dashboard-bottom">
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>优先关注</h2>
              <p>先从一次主动联系开始</p>
            </div>
            <router-link to="/alerts" class="text-button"
              >全部预警<Icon name="arrow" :size="16"
            /></router-link>
          </div>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>学生</th>
                  <th>关注等级</th>
                  <th>当前状态</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in data.priority" :key="item.id">
                  <td>
                    <div class="person">
                      <span class="avatar small">{{
                        item.displayName[0]
                      }}</span>
                      <div>
                        <strong>{{ item.displayName }}</strong
                        ><small>{{ item.college }}</small>
                      </div>
                    </div>
                  </td>
                  <td><LevelBadge :level="item.riskLevel" /></td>
                  <td>
                    <span
                      class="status-label"
                      :class="item.status.toLowerCase()"
                      >{{ statuses[item.status] }}</span
                    >
                  </td>
                  <td>
                    <button class="text-button" @click="selected = item.id">
                      查看<Icon name="arrow" :size="15" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!data.priority.length" class="state">
              当前没有需要处理的预警
            </div>
          </div>
        </article>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>随访安排</h2>
              <p>持续关怀，每一步都算数</p>
            </div>
            <router-link
              to="/follow-ups"
              class="icon-button"
              aria-label="所有随访"
              ><Icon name="arrow" :size="18"
            /></router-link>
          </div>
          <div v-if="!followUps.length" class="state">暂无随访安排</div>
          <button
            v-for="item in followUps.slice(0, 3)"
            :key="item.id"
            class="followup-item"
            @click="selected = item.alertId"
          >
            <span class="date-tile">{{
              item.nextDate ? date(item.nextDate).slice(5) : '未定'
            }}</span
            ><span
              ><strong>{{ item.displayName }} · {{ item.kind }}</strong
              ><small
                >{{ item.authorName }} / {{ statuses[item.status] }}</small
              ></span
            ><Icon name="arrow" :size="16" />
          </button>
          <div class="care-quote">
            <Icon name="heart" :size="17" /> 倾听，是关怀最温柔的起点。
          </div>
        </article>
      </section>
      <div class="data-source-note">
        <Icon name="shield" :size="14" />{{ data.source }}
      </div></template
    ></PageState
  ><CaseDrawer v-model="selected" @changed="reload" />
</template>
