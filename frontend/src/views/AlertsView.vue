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
const rows = ref([]);
const status = ref('');
const q = ref('');
const selected = ref(
  typeof route.query.case === 'string' ? route.query.case : '',
);
const {busy, error, reload} = useLoad(async () => {
  rows.value = await api.alerts();
});
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
  <CaseDrawer v-model="selected" @changed="reload" />
</template>
