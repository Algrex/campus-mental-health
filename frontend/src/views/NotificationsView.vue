<script setup>
import {computed, ref} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import {session, refreshInbox} from '../state/session.js';
import {useLoad} from '../utils/useLoad.js';
import {date} from '../utils/format.js';
import Icon from '../components/Icon.vue';
import PageState from '../components/PageState.vue';
import CaseDrawer from '../components/CaseDrawer.vue';
const tab = ref('all');
const selected = ref('');
const saving = ref(false);
const {busy, error, reload} = useLoad(refreshInbox);
const unread = computed(() => session.inbox.filter((r) => !r.isRead));
const filtered = computed(() =>
  tab.value === 'unread' ? unread.value : session.inbox,
);
const staff = computed(() =>
  ['CENTER', 'COUNSELOR'].includes(session.user.role),
);
async function read(row) {
  try {
    await api.read(row.id);
    row.isRead = true;
  } catch (e) {
    ElMessage.error(e.message);
  }
}
async function readAll() {
  saving.value = true;
  try {
    await Promise.all(unread.value.map((row) => api.read(row.id)));
    await refreshInbox();
    ElMessage.success('全部消息已读');
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
      <span class="eyebrow">MESSAGES & CONNECTIONS</span>
      <h1>消息中心</h1>
      <p>
        {{
          session.user.role === 'CONTACT'
            ? '与学校保持沟通，一起为学生提供支持。'
            : '关注提醒与关怀进展，及时到达。'
        }}
      </p>
    </div>
    <button class="btn" :disabled="saving || !unread.length" @click="readAll">
      <Icon name="check" :size="17" />全部标为已读
    </button>
  </div>
  <div
    v-if="session.user.role === 'CONTACT'"
    class="welcome-banner contact-banner"
  >
    <div>
      <span class="banner-label">关怀支持</span>
      <h2>陪伴，让支持更有力量。</h2>
      <p>
        这里会显示学校希望您配合的联系提醒。保持日常交流，尊重学生的感受与节奏。
      </p>
    </div>
    <Icon name="heart" :size="80" />
  </div>
  <section class="card">
    <div class="filter-bar">
      <div class="tabs">
        <button :class="{active: tab === 'all'}" @click="tab = 'all'">
          全部消息 <small>{{ session.inbox.length }}</small></button
        ><button :class="{active: tab === 'unread'}" @click="tab = 'unread'">
          未读 <small>{{ unread.length }}</small>
        </button>
      </div>
      <span class="muted">站内提醒</span>
    </div>
    <PageState
      :busy="busy"
      :error="error"
      :empty="!filtered.length"
      @retry="reload"
      ><article
        v-for="row in filtered"
        :key="row.id"
        class="notification-row"
        :class="{unread: !row.isRead}"
      >
        <span class="notification-icon"
          ><Icon :name="staff ? 'shield' : 'heart'" :size="22"
        /></span>
        <div class="notification-body">
          <div class="notification-title">
            <strong>{{ row.title }}</strong
            ><span v-if="!row.isRead" class="unread-dot"></span
            ><small>{{ date(row.createdAt, true) }}</small>
          </div>
          <p>{{ row.content }}</p>
          <div class="action-row">
            <button
              v-if="staff && row.alertId"
              class="text-button"
              @click="
                selected = row.alertId;
                read(row);
              "
            >
              查看个案<Icon name="arrow" :size="15" /></button
            ><button
              v-if="!row.isRead"
              class="text-button muted"
              @click="read(row)"
            >
              标记已读</button
            ><span v-else class="muted small-text">已读</span>
          </div>
        </div>
      </article></PageState
    >
  </section>
  <CaseDrawer v-if="staff" v-model="selected" />
</template>
