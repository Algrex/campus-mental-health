<script setup>
import {ref, computed} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import {localDay} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import {trendOption} from '../utils/charts.js';
import Chart from '../components/Chart.vue';
import Icon from '../components/Icon.vue';
import PageState from '../components/PageState.vue';
const data = ref(null);
const saving = ref(false);
const draft = ref({mood: 3, sleepHours: 7.5, stress: 3, note: ''});
const today = localDay();
const moods = [
  {value: 1, emoji: '😔', name: '低落'},
  {value: 2, emoji: '🙁', name: '偏低'},
  {value: 3, emoji: '😌', name: '平静'},
  {value: 4, emoji: '🙂', name: '愉快'},
  {value: 5, emoji: '😊', name: '很好'},
];
const {busy, error, reload} = useLoad(async () => {
  data.value = await api.profile();
  const saved = data.value.checkIns.find((r) => r.checkDate === today);
  if (saved)
    draft.value = {
      mood: saved.mood,
      sleepHours: Number(saved.sleepHours),
      stress: saved.stress,
      note: saved.note,
    };
});
const hasToday = computed(() =>
  data.value?.checkIns.some((r) => r.checkDate === today),
);
async function save() {
  saving.value = true;
  try {
    await api.checkIn({
      ...draft.value,
      sleepHours: Number(draft.value.sleepHours),
    });
    await reload();
    ElMessage.success('今天的感受已记录，愿你拥有轻松的一天');
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">DAILY LITTLE MOMENTS</span>
      <h1>今天，你还好吗？</h1>
      <p>不必每一天都很好。允许感受，也照顾自己。</p>
    </div>
    <span class="subtle-tag">{{ today }}</span>
  </div>
  <PageState :busy="busy" :error="error" @retry="reload"
    ><div v-if="data" class="checkin-layout">
      <form class="card checkin-form" @submit.prevent="save">
        <div class="card-heading">
          <div>
            <h2>记录今天的状态</h2>
            <p>
              {{
                hasToday
                  ? '今日已打卡，再次保存会更新这一天的记录。'
                  : '每天一条记录，看见属于自己的节奏。'
              }}
            </p>
          </div>
          <span v-if="hasToday" class="subtle-tag"
            ><Icon name="check" :size="15" />已打卡</span
          >
        </div>
        <h3>此刻的心情</h3>
        <div class="mood-options">
          <button
            v-for="mood in moods"
            :key="mood.value"
            type="button"
            :class="{selected: draft.mood === mood.value}"
            @click="draft.mood = mood.value"
          >
            <span>{{ mood.emoji }}</span
            ><strong>{{ mood.name }}</strong>
          </button>
        </div>
        <div class="range-heading">
          <h3>昨晚睡了多久？</h3>
          <strong>{{ draft.sleepHours }} <small>小时</small></strong>
        </div>
        <input
          v-model.number="draft.sleepHours"
          type="range"
          min="0"
          max="24"
          step="0.5"
          aria-label="睡眠时长"
        />
        <div class="range-labels">
          <span>0 小时</span><span>12 小时</span><span>24 小时</span>
        </div>
        <div class="range-heading">
          <h3>今天的压力感受</h3>
          <strong>{{
            ['', '很轻松', '较轻松', '一般', '较大', '很大'][draft.stress]
          }}</strong>
        </div>
        <div class="stress-options">
          <button
            v-for="(label, index) in [
              '很轻松',
              '较轻松',
              '一般',
              '较大',
              '很大',
            ]"
            :key="label"
            type="button"
            :class="{selected: draft.stress === index + 1}"
            @click="draft.stress = index + 1"
          >
            {{ label }}
          </button>
        </div>
        <label class="field note-field"
          >想对今天说点什么？<span class="muted">（可选）</span
          ><textarea
            v-model="draft.note"
            rows="4"
            maxlength="600"
            placeholder="一件小事、一个感受，或一个想好好休息的理由…"
          ></textarea
          ><small class="muted">{{ draft.note.length }} / 600</small></label
        ><button class="btn primary full-width" :disabled="saving">
          {{ saving ? '正在记录…' : hasToday ? '更新今日打卡' : '保存今日心情'
          }}<Icon name="check" :size="18" />
        </button>
      </form>
      <aside>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>睡眠小趋势</h2>
              <p>最近 7 次打卡</p>
            </div>
            <Icon name="sun" />
          </div>
          <Chart
            v-if="data.checkIns.length"
            :option="
              trendOption(
                data.checkIns.slice(0, 7).reverse(),
                'sleepHours',
                '睡眠时长（小时）',
              )
            "
            :height="200"
          />
          <div v-else class="state">记录后会显示趋势</div>
        </article>
        <article class="card recent-checkins">
          <div class="card-heading">
            <div>
              <h2>最近的日常</h2>
              <p>点滴变化，值得记住</p>
            </div>
          </div>
          <div
            v-for="row in data.checkIns.slice(0, 6)"
            :key="row.id"
            class="checkin-record"
          >
            <span>{{ moods[row.mood - 1].emoji }}</span>
            <div>
              <strong>{{ row.checkDate }}</strong
              ><small
                >{{ moods[row.mood - 1].name }} · 睡眠
                {{ row.sleepHours }} 小时</small
              >
            </div>
            <span class="muted">压力 {{ row.stress }}/5</span>
          </div>
          <div v-if="!data.checkIns.length" class="state">
            从今天的第一条打卡开始
          </div>
        </article>
      </aside>
    </div></PageState
  >
</template>
