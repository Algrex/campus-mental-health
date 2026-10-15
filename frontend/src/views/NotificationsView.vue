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
// 保存通知关联的预警 ID，供工作人员在本页打开个案详情。
const selected = ref('');
const saving = ref(false);
// 收件箱使用共享会话状态，消息列表与 App 中的未读提醒读取同一份数据。
const {busy, error, reload} = useLoad(refreshInbox);
// 已读状态变化后自动更新未读集合；标签切换只筛选本地收件箱。
const unread = computed(() => session.inbox.filter((r) => !r.isRead));
const filtered = computed(() =>
  tab.value === 'unread' ? unread.value : session.inbox,
);
// 仅工作人员展示个案入口；通知内容与个案访问范围仍由后端授权控制。
const staff = computed(() =>
  ['CENTER', 'COUNSELOR'].includes(session.user.role),
);
// 单条已读请求成功后才更新共享记录，失败时保留原状态并提示错误。
async function read(row) {
  try {
    await api.read(row.id);
    row.isRead = true;
  } catch (e) {
    ElMessage.error(e.message);
  }
}
// 并行提交当前未读通知，全部成功后重新获取收件箱以同步服务端状态。
// Promise.all 失败不会撤销已成功的请求；saving 在 finally 中恢复。
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
    <!-- 批量操作期间或没有未读消息时禁用按钮，避免重复触发批量请求。 -->
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
  <!-- 学生与联系人不挂载个案抽屉；工作人员点击通知时打开详情并提交已读请求。 -->
  <CaseDrawer v-if="staff" v-model="selected" />
</template>
