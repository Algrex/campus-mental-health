<script setup>
import {ref, watch} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import {date, statuses, localDay} from '../utils/format.js';
import {refreshInbox} from '../state/session.js';
import LevelBadge from './LevelBadge.vue';
import PageState from './PageState.vue';
import Icon from './Icon.vue';
const props = defineProps({modelValue: {type: String, default: ''}});
const emit = defineEmits(['update:modelValue', 'changed']);
const data = ref(null);
const busy = ref(false);
const saving = ref(false);
const error = ref('');
const editing = ref(false);
const closing = ref(false);
const reason = ref('');
const draft = ref({kind: '沟通访谈', content: '', nextDate: ''});
async function load() {
  const id = props.modelValue;
  if (!id) return;
  busy.value = true;
  error.value = '';
  try {
    const response = await api.alert(id);
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
    editing.value = false;
    closing.value = false;
    reason.value = '';
    draft.value = {kind: '沟通访谈', content: '', nextDate: ''};
    load();
  },
  {immediate: true},
);
async function action(name) {
  saving.value = true;
  try {
    await api.transition(props.modelValue, name, reason.value);
    closing.value = false;
    await load();
    await refreshInbox();
    emit('changed');
    ElMessage.success('个案状态已更新');
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    await api.intervention(props.modelValue, {
      ...draft.value,
      nextDate: draft.value.nextDate || null,
    });
    editing.value = false;
    draft.value = {kind: '沟通访谈', content: '', nextDate: ''};
    await load();
    emit('changed');
    ElMessage.success('随访记录已保存');
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
const steps = [
  'PENDING',
  'ACCEPTED',
  'INTERVENING',
  'AWAITING_REASSESSMENT',
  'CLOSED',
];
</script>
<template>
  <ElDrawer
    :model-value="!!modelValue"
    title="关怀个案"
    size="min(760px, 96vw)"
    @update:model-value="emit('update:modelValue', '')"
    ><PageState :busy="busy" :error="error" @retry="load"
      ><div v-if="data" class="detail-content">
        <div class="profile-summary">
          <span class="avatar large">{{ data.student.displayName[0] }}</span>
          <div>
            <h2>{{ data.student.displayName }}</h2>
            <p>{{ data.student.college }} · {{ data.student.yearName }}</p>
            <small>{{ data.student.studentCode }}</small>
          </div>
          <LevelBadge :level="data.alert.riskLevel" />
        </div>
        <div class="case-steps">
          <div
            v-for="(step, index) in steps"
            :key="step"
            :class="{complete: steps.indexOf(data.alert.status) >= index}"
          >
            <span>{{ index + 1 }}</span
            ><small>{{ statuses[step] }}</small>
          </div>
        </div>
        <div class="case-reason">
          <Icon name="shield" :size="20" />
          <div>
            <strong>关注原因</strong>
            <p>{{ data.alert.reason }}</p>
            <small
              >创建于 {{ date(data.alert.createdAt, true) }} ·
              {{ data.alert.assignedTo || '尚未分配' }}</small
            >
          </div>
        </div>
        <div class="action-row">
          <button
            v-if="data.alert.status === 'PENDING'"
            class="btn primary"
            :disabled="saving"
            @click="action('accept')"
          >
            受理并跟进</button
          ><button
            v-if="
              ['ACCEPTED', 'INTERVENING', 'AWAITING_REASSESSMENT'].includes(
                data.alert.status,
              )
            "
            class="btn primary"
            @click="editing = !editing"
          >
            <Icon name="plus" :size="17" />添加干预记录</button
          ><button
            v-if="data.alert.status === 'INTERVENING'"
            class="btn"
            :disabled="saving"
            @click="action('review')"
          >
            转入复评</button
          ><button
            v-if="data.alert.status === 'AWAITING_REASSESSMENT'"
            class="btn"
            @click="closing = !closing"
          >
            复评并关闭</button
          ><span v-if="data.alert.status === 'CLOSED'" class="completed-note"
            ><Icon name="check" :size="18" />这个个案已完成</span
          >
        </div>
        <form v-if="editing" class="inline-form" @submit.prevent="save">
          <h3>记录一次关怀行动</h3>
          <div class="form-grid">
            <label class="field"
              >行动类型<select v-model="draft.kind">
                <option>沟通访谈</option>
                <option>心理咨询</option>
                <option>电话随访</option>
                <option>家校沟通</option>
                <option>生活支持</option>
              </select></label
            ><label class="field"
              >下次随访日期<input
                v-model="draft.nextDate"
                type="date"
                :min="localDay()"
            /></label>
          </div>
          <label class="field"
            >沟通内容与后续安排<textarea
              v-model="draft.content"
              required
              minlength="5"
              maxlength="1200"
              rows="4"
              placeholder="记录了解的情况、采取的措施及下一步支持计划…"
            ></textarea>
          </label>
          <div class="action-row">
            <button class="btn primary" :disabled="saving">保存记录</button
            ><button type="button" class="btn" @click="editing = false">
              取消
            </button>
          </div>
        </form>
        <form
          v-if="closing"
          class="inline-form"
          @submit.prevent="action('close')"
        >
          <h3>人工复评与关闭说明</h3>
          <p class="muted">
            请记录当前情况、复评依据与后续支持安排。关闭个案不会删除历史记录，也不会清除学生关注等级。
          </p>
          <label class="field">
            <textarea
              v-model="reason"
              required
              minlength="5"
              maxlength="600"
              rows="4"
              placeholder="填写复评结果与关闭原因…"
            ></textarea></label
          ><button class="btn primary" :disabled="saving">确认完成个案</button>
        </form>
        <h3 class="section-title">
          跟进时间线 <span>{{ data.records.length }} 条记录</span>
        </h3>
        <div v-if="!data.records.length" class="state">
          尚无干预记录，受理后可以开始记录。
        </div>
        <div class="timeline">
          <article v-for="item in data.records" :key="item.id">
            <span class="timeline-dot"></span>
            <div class="timeline-top">
              <strong>{{ item.kind }}</strong
              ><small>{{ date(item.createdAt, true) }}</small>
            </div>
            <p>{{ item.content }}</p>
            <div class="timeline-meta">
              {{ item.authorName
              }}<span v-if="item.nextDate">下次随访 · {{ item.nextDate }}</span>
            </div>
          </article>
        </div>
        <div v-if="data.alert.closeReason" class="info-note">
          <strong>关闭说明</strong>
          <p>{{ data.alert.closeReason }}</p>
        </div>
      </div></PageState
    ></ElDrawer
  >
</template>
