<script setup>
import {ref, computed} from 'vue';
import {useRoute} from 'vue-router';
import {api} from '../api/portal.js';
import {statuses, date} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import Icon from '../components/Icon.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
import CaseDrawer from '../components/CaseDrawer.vue';
const route = useRoute();
// 列表保存后端返回的可访问预警，状态统计与筛选结果均基于这份数据。
const rows = ref([]);
// 空状态表示全部；状态标签与搜索关键词共同决定表格显示的记录。
const status = ref('');
const q = ref('');
// 初次创建页面时读取 ?case= 中的个案 ID，支持从其他页面定位详情。
// selected 保存预警 ID，而非学生 ID，与 CaseDrawer 的数据接口对应。
const selected = ref(
  typeof route.query.case === 'string' ? route.query.case : '',
);
// 首次加载、失败重试和个案操作完成后，复用同一入口刷新预警列表。
const {busy, error, reload} = useLoad(async () => {
  rows.value = await api.alerts();
});
// 在已授权列表内按状态和姓名、学号或学院进行前端筛选，不发起额外请求。
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (!status.value || r.status === status.value) &&
      (!q.value ||
        `${r.displayName}${r.studentCode}${r.college}`.includes(q.value)),
  ),
);
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">EARLY RESPONSE</span>
      <h1>预警管理</h1>
      <p>从提醒到行动，形成完整的关怀闭环。</p>
    </div>
    <span class="subtle-tag"
      >{{
        rows.filter((r) => r.status !== 'CLOSED').length
      }}
      个进行中的个案</span
    >
  </div>
  <!-- 流程条展示各状态的记录数量；切换筛选条件不会改变这里的统计。 -->
  <div class="workflow-strip">
    <div v-for="(label, key, index) in statuses" :key="key">
      <span>{{ index + 1 }}</span
      ><strong>{{ label }}</strong
      ><small>{{ rows.filter((r) => r.status === key).length }} 个个案</small
      ><Icon v-if="index < 4" name="arrow" :size="18" />
    </div>
  </div>
  <section class="card">
    <div class="filter-bar">
      <div class="tabs">
        <button :class="{active: !status}" @click="status = ''">
          全部 <small>{{ rows.length }}</small></button
        ><button
          v-for="(label, key) in statuses"
          :key="key"
          :class="{active: status === key}"
          @click="status = key"
        >
          {{ label }}
        </button>
      </div>
      <div class="search-input">
        <Icon name="search" :size="17" /><input
          v-model="q"
          placeholder="搜索学生或学院"
          aria-label="搜索预警"
        />
      </div>
    </div>
    <PageState
      :busy="busy"
      :error="error"
      :empty="!filtered.length"
      @retry="reload"
      ><div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th>关注等级</th>
              <th>关注原因</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filtered" :key="row.id">
              <td>
                <div class="person">
                  <span class="avatar small">{{ row.displayName[0] }}</span>
                  <div>
                    <strong>{{ row.displayName }}</strong
                    ><small>{{ row.college }}</small>
                  </div>
                </div>
              </td>
              <td><LevelBadge :level="row.riskLevel" /></td>
              <td class="reason-cell">{{ row.reason }}</td>
              <td>
                <span class="status-label" :class="row.status.toLowerCase()">{{
                  statuses[row.status]
                }}</span>
              </td>
              <td class="muted">{{ date(row.createdAt) }}</td>
              <!-- “受理”按钮先打开详情，具体业务操作在个案抽屉中完成。 -->
              <td>
                <button class="text-button" @click="selected = row.id">
                  {{ row.status === 'PENDING' ? '受理' : '查看个案'
                  }}<Icon name="arrow" :size="15" />
                </button>
              </td>
            </tr>
          </tbody>
        </table></div
    ></PageState>
  </section>
  <!-- 抽屉通知个案已变更后重新获取列表，同步状态、数量及关注原因。 -->
  <CaseDrawer v-model="selected" @changed="reload" />
</template>
