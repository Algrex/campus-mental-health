<script setup>
import {ref, computed} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import {session} from '../state/session.js';
import {date, levels} from '../utils/format.js';
import {useLoad} from '../utils/useLoad.js';
import Icon from '../components/Icon.vue';
import LevelBadge from '../components/LevelBadge.vue';
import PageState from '../components/PageState.vue';
import StudentDrawer from '../components/StudentDrawer.vue';
// rows 保存接口返回的可访问档案，搜索条件与当前页码由页面独立维护。
const rows = ref([]);
const q = ref('');
const college = ref('');
const level = ref('');
const page = ref(1);
// selected 为详情抽屉使用的学生 ID，editingId 为新增或编辑弹窗使用的 ID。
const selected = ref('');
const dialog = ref(false);
const editingId = ref('');
const saving = ref(false);
const fileInput = ref(null);
const importResult = ref(null);
const {busy, error, reload} = useLoad(async () => {
  rows.value = await api.students();
});
// 学院选项从已加载档案去重生成，不额外请求学院列表。
const colleges = computed(() => [...new Set(rows.value.map((r) => r.college))]);
// 在后端已授权的数据范围内筛选：关键词、学院、关注等级三个条件同时生效。
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (!q.value ||
        `${r.studentCode}${r.displayName}${r.major}`.includes(
          q.value.trim(),
        )) &&
      (!college.value || r.college === college.value) &&
      (!level.value || r.riskLevel === level.value),
  ),
);
// 每页显示 12 条；无匹配结果时页数仍为 1，空状态交给 PageState 展示。
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 12)),
);
// 限制切片页码不超过总页数，避免数据刷新后旧页码导致列表显示为空。
const visible = computed(() =>
  filtered.value.slice(
    (Math.min(page.value, pages.value) - 1) * 12,
    Math.min(page.value, pages.value) * 12,
  ),
);
// 每次调用生成独立表单对象，默认学院优先使用当前账号绑定的学院。
const blank = () => ({
  studentCode: '',
  displayName: '',
  college: session.user.college || '计算机学院',
  major: '计算机科学与技术',
  yearName: '大一',
  gender: '未填写',
  age: 20,
  contactName: '演示联系人',
});
const draft = ref(blank());
// 新增和编辑复用弹窗；编辑时只复制表单字段，取消操作不会直接改动列表对象。
function edit(row) {
  editingId.value = row?.id || '';
  draft.value = row
    ? Object.fromEntries(Object.keys(blank()).map((key) => [key, row[key]]))
    : blank();
  dialog.value = true;
}
// 将表单与编辑 ID 交给接口区分新增和更新，保存成功后重新加载档案列表。
async function save() {
  saving.value = true;
  try {
    await api.saveStudent(draft.value, editingId.value);
    dialog.value = false;
    await reload();
    ElMessage.success('学生档案已保存');
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    saving.value = false;
  }
}
// 先校验文件大小，再将 CSV 文本交给后端解析；保留返回的成功数和逐行错误。
async function upload(event) {
  const file = event.target.files[0];
  if (!file) return;
  if (file.size > 200000) {
    ElMessage.error('CSV 文件不能超过 200 KB');
    event.target.value = '';
    return;
  }
  saving.value = true;
  try {
    importResult.value = await api.importStudents(await file.text());
    await reload();
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    // 清空文件选择，允许用户修正内容后再次选择同名 CSV。
    event.target.value = '';
    saving.value = false;
  }
}
// 在浏览器中生成 UTF-8 BOM 模板，便于表格软件识别中文；下载后释放临时 URL。
function sample() {
  const url = URL.createObjectURL(
    new Blob(
      [
        '\ufeff学号,姓名,学院,专业,年级,性别\nDEMO001,演示同学,计算机学院,计算机科学与技术,大一,女\n',
      ],
      {type: 'text/csv;charset=utf-8'},
    ),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = '学生导入模板.csv';
  link.click();
  URL.revokeObjectURL(url);
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">STUDENT PROFILES</span>
      <h1>学生档案</h1>
      <p>每一份档案，都是持续理解与关怀的起点。</p>
    </div>
    <div class="action-row">
      <button class="btn" :disabled="saving" @click="fileInput.click()">
        <Icon name="download" :size="17" />导入 CSV</button
      ><button class="btn primary" @click="edit()">
        <Icon name="plus" :size="17" />新增学生</button
      ><input
        ref="fileInput"
        type="file"
        accept=".csv"
        hidden
        @change="upload"
      />
    </div>
  </div>
  <section class="card">
    <div class="filter-bar">
      <div class="search-input">
        <Icon name="search" :size="18" /><input
          v-model="q"
          placeholder="搜索姓名、学号或专业"
          aria-label="搜索学生"
          @input="page = 1"
        />
      </div>
      <select v-model="college" aria-label="筛选学院" @change="page = 1">
        <option value="">全部学院</option>
        <option v-for="name in colleges" :key="name">{{ name }}</option></select
      ><select v-model="level" aria-label="筛选关注等级" @change="page = 1">
        <option value="">全部关注等级</option>
        <option v-for="(item, key) in levels" :key="key" :value="key">
          {{ item.name }}
        </option></select
      ><button class="text-button" @click="sample">下载导入模板</button>
    </div>
    <div v-if="importResult" class="import-result" role="status">
      <strong
        >导入完成：成功 {{ importResult.imported }} 条，失败
        {{ importResult.failed }} 条</strong
      >
      <p v-for="item in importResult.errors" :key="item.row">
        第 {{ item.row }} 行：{{ item.message }}
      </p>
      <button class="text-button" @click="importResult = null">收起</button>
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
              <th>学生信息</th>
              <th>学院 / 专业</th>
              <th>年级</th>
              <th>最近测评</th>
              <th>关注等级</th>
              <th>最近活动</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in visible" :key="row.id">
              <td>
                <button class="person person-button" @click="selected = row.id">
                  <span class="avatar small">{{ row.displayName[0] }}</span
                  ><span
                    ><strong>{{ row.displayName }}</strong
                    ><small>{{ row.studentCode }}</small></span
                  >
                </button>
              </td>
              <td>
                {{ row.college
                }}<small class="cell-note">{{ row.major }}</small>
              </td>
              <td>{{ row.yearName }}</td>
              <td>
                <strong class="table-score">{{ row.currentScore }}</strong
                ><span class="muted"> / 27</span>
              </td>
              <td><LevelBadge :level="row.riskLevel" /></td>
              <td class="muted">{{ date(row.lastActivity) }}</td>
              <td>
                <div class="action-row">
                  <button class="text-button" @click="selected = row.id">
                    档案</button
                  ><button class="text-button muted" @click="edit(row)">
                    编辑
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ filtered.length }} 位学生 · 每页 12 条</span>
        <div>
          <button class="btn compact" :disabled="page <= 1" @click="page--">
            上一页</button
          ><span>{{ Math.min(page, pages) }} / {{ pages }}</span
          ><button
            class="btn compact"
            :disabled="page >= pages"
            @click="page++"
          >
            下一页
          </button>
        </div>
      </div></PageState
    >
  </section>
  <StudentDrawer v-model="selected" /><ElDialog
    v-model="dialog"
    :title="editingId ? '编辑学生档案' : '新增学生档案'"
    width="min(620px, 95vw)"
    ><form @submit.prevent="save">
      <div class="form-grid">
        <label class="field"
          >学号<input
            v-model="draft.studentCode"
            required
            maxlength="64" /></label
        ><label class="field"
          >姓名<input
            v-model="draft.displayName"
            required
            maxlength="100" /></label
        ><label class="field"
          >学院<input
            v-model="draft.college"
            required
            maxlength="100"
            :readonly="session.user.role === 'COUNSELOR'" /></label
        ><label class="field"
          >专业<input v-model="draft.major" required maxlength="100" /></label
        ><label class="field"
          >年级<select v-model="draft.yearName">
            <option>大一</option>
            <option>大二</option>
            <option>大三</option>
            <option>大四</option>
            <option>研究生</option>
          </select></label
        ><label class="field"
          >性别<select v-model="draft.gender">
            <option>未填写</option>
            <option>女</option>
            <option>男</option>
          </select></label
        ><label class="field"
          >年龄<input
            v-model.number="draft.age"
            type="number"
            min="16"
            max="80"
            required /></label
        ><label class="field"
          >联系人称呼<input
            v-model="draft.contactName"
            required
            maxlength="100"
        /></label>
      </div>
      <p class="info-note">请使用虚构身份演示，勿录入真实学生个人资料。</p>
      <div class="dialog-actions">
        <button class="btn" type="button" @click="dialog = false">取消</button
        ><button class="btn primary" :disabled="saving">保存档案</button>
      </div>
    </form></ElDialog
  >
</template>
