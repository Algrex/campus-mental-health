<script setup>
import {ref, computed} from 'vue';
import {api} from '../api/portal.js';
import {localDay, date, statuses} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import Icon from '../components/Icon.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
import CaseDrawer from '../components/CaseDrawer.vue';
const rows = ref([]);
const selected = ref('');
const tab = ref('active');
const today = localDay();
const followUpStatuses = {
  NONE: '未安排',
  PLANNED: '当前计划',
  SUPERSEDED: '已替代',
  CANCELLED: '已取消',
};
const {busy, error, reload} = useLoad(async () => {
  rows.value = await api.followUps();
});
const active = computed(() =>
  rows.value.filter(
    (r) => r.followUpStatus === 'PLANNED' && r.status !== 'CLOSED',
  ),
);
const due = computed(() =>
  active.value.filter((r) => r.nextDate && r.nextDate <= today),
);
const filtered = computed(() =>
  tab.value === 'due'
    ? due.value
    : tab.value === 'active'
      ? active.value
      : rows.value,
);
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">CONTINUOUS SUPPORT</span>
      <h1>干预随访</h1>
      <p>记录每一次支持，让关怀在时间里延续。</p>
    </div>
    <router-link to="/alerts" class="btn"
      ><Icon name="plus" :size="17" />从预警开始跟进</router-link
    >
  </div>
  <div class="followup-stats">
    <article class="card">
      <span class="stat-icon amber"><Icon name="calendar" /></span>
      <div>
        <span>今日及逾期安排</span
        ><strong>{{ due.length }}<small> 次</small></strong>
      </div>
    </article>
    <article class="card">
      <span class="stat-icon green"><Icon name="heart" /></span>
      <div>
        <span>正在持续跟进</span
        ><strong
          >{{ new Set(active.map((r) => r.alertId)).size
          }}<small> 个个案</small></strong
        >
      </div>
    </article>
    <article class="card">
      <span class="stat-icon blue"><Icon name="clipboard" /></span>
      <div>
        <span>累计关怀记录</span
        ><strong>{{ rows.length }}<small> 条</small></strong>
      </div>
    </article>
  </div>
  <section class="card">
    <div class="card-heading">
      <div>
        <h2>关怀记录与安排</h2>
        <p>点击个案可补充记录、安排下一次沟通</p>
      </div>
      <div class="tabs">
        <button
          v-for="item in [
            {id: 'active', label: '进行中'},
            {id: 'due', label: '今日及逾期'},
            {id: 'all', label: '全部记录'},
          ]"
          :key="item.id"
          :class="{active: tab === item.id}"
          @click="tab = item.id"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    <PageState
      :busy="busy"
      :error="error"
      :empty="!filtered.length"
      @retry="reload"
      ><div class="followup-grid">
        <article v-for="row in filtered" :key="row.id" class="followup-card">
          <div class="followup-card-top">
            <div class="person">
              <span class="avatar small">{{ row.displayName[0] }}</span>
              <div>
                <strong>{{ row.displayName }}</strong
                ><small>{{ row.college }}</small>
              </div>
            </div>
            <LevelBadge :level="row.riskLevel" />
          </div>
          <h3>{{ row.kind }}</h3>
          <p>{{ row.content }}</p>
          <div class="followup-card-meta">
            <span
              ><Icon name="calendar" :size="15" />{{
                row.nextDate || '未安排下次日期'
              }}</span
            ><span>{{ row.authorName }}</span>
          </div>
          <div class="followup-card-footer">
            <span class="subtle-tag">{{
              followUpStatuses[row.followUpStatus]
            }}</span
            ><span class="status-label" :class="row.status.toLowerCase()">{{
              statuses[row.status]
            }}</span
            ><button class="text-button" @click="selected = row.alertId">
              查看个案<Icon name="arrow" :size="15" />
            </button>
          </div>
          <small class="record-date"
            >记录于 {{ date(row.createdAt, true) }}</small
          >
        </article>
      </div></PageState
    >
  </section>
  <CaseDrawer v-model="selected" @changed="reload" />
</template>
