<script setup>
import {ref, computed} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import {session} from '../state/session.js';
import {useLoad} from '../utils/useLoad.js';
import {date} from '../utils/format.js';
import Icon from '../components/Icon.vue';
import Chart from '../components/Chart.vue';
import PageState from '../components/PageState.vue';
const data = ref(null);
const training = ref(false);
const predicting = ref(false);
const prediction = ref(null);
const explanation = ref('j48');
const selectedDataset = ref('');
const draft = ref({
  age: 20,
  gender: 'Female',
  courseGroup: 'Computing',
  studyYear: 2,
  cgpa: 3.25,
  married: 'No',
  department: 'Science',
  sleepHours: 7.5,
  studyHours: 5,
  socialMediaHours: 3,
  physicalActivity: 90,
  stressLevel: 4,
});
const {busy, error, reload} = useLoad(async () => {
  data.value = await api.mlSummary(selectedDataset.value);
  if (data.value.dataset) selectedDataset.value = data.value.dataset.type;
});
function percent(value) {
  return value === null || value === undefined ? '—' : (value * 100).toFixed(1);
}
const isLifestyle = computed(() => data.value?.dataset?.type === 'lifestyle');
const modelText = computed(
  () =>
    data.value?.models?.find((model) => model.id === explanation.value)
      ?.explanation || '',
);
const roc = computed(() => ({
  tooltip: {trigger: 'axis'},
  grid: {top: 20, left: 45, right: 25, bottom: 45},
  legend: {bottom: 0, textStyle: {color: '#96a190', fontSize: 10}},
  xAxis: {
    type: 'value',
    min: 0,
    max: 1,
    name: 'FPR',
    nameTextStyle: {color: '#a1ad98'},
    splitLine: {show: false},
    axisLabel: {fontSize: 10, color: '#a1ad98'},
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 1,
    name: 'TPR',
    nameTextStyle: {color: '#a1ad98'},
    splitLine: {lineStyle: {color: '#edf1e6', type: 'dashed'}},
    axisLabel: {fontSize: 10, color: '#a1ad98'},
  },
  series: [
    ...(data.value?.models || [])
      .filter((model) => model.id !== 'baseline')
      .map((model, index) => ({
        type: 'line',
        name: model.name,
        showSymbol: false,
        lineStyle: {width: 2, color: ['#518979', '#c7a064', '#8799b0'][index]},
        itemStyle: {color: ['#518979', '#c7a064', '#8799b0'][index]},
        data: [...model.roc].sort((a, b) => a[0] - b[0] || a[1] - b[1]),
      })),
    {
      type: 'line',
      name: '随机参考',
      showSymbol: false,
      lineStyle: {type: 'dashed', color: '#c6cfc0'},
      itemStyle: {color: '#c6cfc0'},
      data: [
        [0, 0],
        [1, 1],
      ],
    },
  ],
}));
async function train() {
  training.value = true;
  try {
    data.value = await api.mlTrain(selectedDataset.value || 'lifestyle');
    prediction.value = null;
    ElMessage.success('模型训练与评估已完成');
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    training.value = false;
  }
}
async function predict() {
  predicting.value = true;
  try {
    prediction.value = await api.mlPredict({
      ...draft.value,
      dataset: selectedDataset.value,
    });
  } catch (e) {
    ElMessage.error(e.message);
  } finally {
    predicting.value = false;
  }
}
function download() {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data.value, null, 2)], {type: 'application/json'}),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = `${selectedDataset.value}-模型评估报告.json`;
  link.click();
  URL.revokeObjectURL(url);
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">INTERPRETABLE MACHINE LEARNING</span>
      <h1>模型实验室</h1>
      <p>用透明的实验设计，理解数据与预测的关系。</p>
    </div>
    <div class="action-row">
      <select
        v-model="selectedDataset"
        aria-label="选择实验数据集"
        :disabled="busy || training || predicting"
        @change="
          prediction = null;
          explanation = 'j48';
          reload();
        "
      >
        <option v-if="!selectedDataset" value="" disabled>选择实验数据</option>
        <option value="lifestyle">生活方式 · 100,000 条</option>
        <option value="survey">匿名问卷 · 101 条</option>
      </select>
      <button v-if="data?.status === 'READY'" class="btn" @click="download">
        <Icon name="download" :size="17" />导出评估</button
      ><button
        v-if="session.user.role === 'CENTER'"
        class="btn primary"
        :disabled="training || busy"
        @click="train"
      >
        <Icon name="chart" :size="17" />{{
          training ? '训练与评估中…' : '重新训练模型'
        }}
      </button>
    </div>
  </div>
  <PageState :busy="busy" :error="error" @retry="reload">
    <template v-if="data?.status === 'READY'">
      <div class="lab-data-banner">
        <div>
          <strong>{{ data.dataset.name }} · {{ data.dataset.nature }}</strong>
          <p>
            正类 Yes {{ data.dataset.positive }} 条 / 负类 No
            {{ data.dataset.negative }} 条 ·
            {{ data.dataset.missingValues }} 个缺失值 ·
            {{ data.dataset.license }}
          </p>
          <div class="model-method">
            <span class="subtle-tag">{{ data.validation.method }}</span
            ><span class="subtle-tag">固定种子 42</span
            ><span class="subtle-tag"
              >{{ data.features.length }} 个原始特征</span
            ><span class="subtle-tag">训练数据内拟合预处理</span>
          </div>
        </div>
        <div class="centered">
          <strong class="dataset-count">{{ data.dataset.rows }}</strong>
          <p>{{ isLifestyle ? '合成样本' : '匿名问卷' }}</p>
        </div>
      </div>
      <section
        class="model-grid"
        :style="{'--model-count': data.models.length}"
      >
        <article
          v-for="model in data.models"
          :key="model.id"
          class="card model-card"
          :class="{baseline: model.id === 'baseline'}"
        >
          <div class="card-heading">
            <div>
              <h2>{{ model.name }}</h2>
              <p class="model-type">
                {{ model.algorithm }} ·
                {{ model.id === 'baseline' ? 'BASELINE' : 'WEKA 3.8.6' }}
              </p>
            </div>
            <Icon
              :name="model.id === 'baseline' ? 'grid' : 'chart'"
              :size="22"
            />
          </div>
          <div class="model-metrics">
            <div class="primary-metric">
              <small>准确率 Accuracy</small
              ><strong
                >{{ percent(model.accuracy)
                }}<span v-if="model.accuracy !== null"> %</span></strong
              >
            </div>
            <div>
              <small>ROC AUC</small
              ><strong>{{
                model.auc === null ? '—' : model.auc.toFixed(3)
              }}</strong>
            </div>
            <div>
              <small>正类召回率 Recall</small
              ><strong
                >{{ percent(model.recall)
                }}<span v-if="model.recall !== null"> %</span></strong
              >
            </div>
            <div>
              <small>正类 F1</small
              ><strong>{{
                model.f1 === null ? '—' : model.f1.toFixed(3)
              }}</strong>
            </div>
          </div>
          <div class="confusion-table">
            <table>
              <thead>
                <tr>
                  <th>实际 / 预测</th>
                  <th>No</th>
                  <th>Yes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in model.confusionMatrix" :key="index">
                  <th>{{ index === 0 ? 'No' : 'Yes' }}</th>
                  <td
                    v-for="(cell, column) in row"
                    :key="column"
                    :class="{correct: index === column}"
                  >
                    {{ cell }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="experiment-note">
            精确率 {{ percent(model.precision)
            }}{{ model.precision === null ? '' : '%' }} · 总耗时
            {{ model.durationMs }} ms
          </p>
          <div class="info-note">{{ model.parameters }}</div>
        </article>
      </section>
      <article v-if="data.validation.trainRows" class="card operating-card">
        <div class="card-heading">
          <div>
            <h2>验证集选阈值后的测试表现</h2>
            <p>
              上方指标采用统一阈值 0.5；这里展示在验证集独立选择的操作阈值。
            </p>
          </div>
          <span class="subtle-tag"
            >独立测试 {{ data.validation.testRows }} 条</span
          >
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>模型</th>
                <th>验证阈值</th>
                <th>测试准确率</th>
                <th>精确率</th>
                <th>召回率</th>
                <th>F1</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="model in data.models" :key="model.id">
                <td>{{ model.name }}</td>
                <td>{{ model.operatingPoint.threshold }}</td>
                <td>{{ percent(model.operatingPoint.accuracy) }}%</td>
                <td>
                  {{ percent(model.operatingPoint.precision)
                  }}{{ model.operatingPoint.precision === null ? '' : '%' }}
                </td>
                <td>{{ percent(model.operatingPoint.recall) }}%</td>
                <td>{{ model.operatingPoint.f1?.toFixed(3) ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
      <section class="ml-experiment">
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>ROC 曲线</h2>
              <p>
                {{
                  data.validation.trainRows
                    ? '独立测试集预测'
                    : '汇总各验证折预测'
                }}
                · 正类 Yes
              </p>
            </div>
            <span class="subtle-tag">{{
              data.validation.trainRows ? 'HOLDOUT TEST' : 'OUT-OF-FOLD'
            }}</span>
          </div>
          <Chart :option="roc" :height="300" />
          <p class="experiment-note">
            展示不同阈值下的灵敏度与误报率；模型比较需同时关注正类召回率，不能只看准确率。
          </p>
        </article>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>实验设计</h2>
              <p>保证比较方法一致，避免目标信息泄漏</p>
            </div>
            <Icon name="shield" />
          </div>
          <div class="timeline">
            <article>
              <span class="timeline-dot"></span
              ><strong>原始数据 → 固定特征模式</strong>
              <p>
                {{ data.features.join('、') }}。排除：{{
                  data.excluded.join('、')
                }}。
              </p>
            </article>
            <article>
              <span class="timeline-dot"></span
              ><strong>同一分层划分 → 训练内预处理</strong>
              <p>
                {{
                  data.validation.trainRows
                    ? `训练 ${data.validation.trainRows} / 验证 ${data.validation.validationRows} / 测试 ${data.validation.testRows}，测试集保持独立。`
                    : '相同种子与 5 折划分，每折仅用训练部分拟合预处理。'
                }}
              </p>
            </article>
            <article>
              <span class="timeline-dot"></span
              ><strong>模型评估 → 交互推理</strong>
              <p>
                {{
                  data.validation.trainRows
                    ? '只在验证集选择正类 F1 最优阈值；测试集仅用于最终评估。交互推理保留训练集模型。'
                    : '各验证折预测汇总为指标；评估后全量重训用于推理。'
                }}
              </p>
            </article>
          </div>
          <p class="info-note">{{ data.randomForest }}</p>
          <small class="muted"
            >训练时间：{{ date(data.trainedAt, true) }}</small
          >
        </article>
      </section>
      <section class="ml-experiment">
        <form class="card" @submit.prevent="predict">
          <div class="card-heading">
            <div>
              <h2>交互推理</h2>
              <p>输入一组实验特征，比较多种模型的输出</p>
            </div>
            <Icon name="clipboard" />
          </div>
          <div class="form-grid">
            <label class="field"
              >年龄<input
                v-model.number="draft.age"
                type="number"
                min="16"
                max="80"
                required /></label
            ><label class="field"
              >性别<select v-model="draft.gender">
                <option value="Female">女</option>
                <option value="Male">男</option>
              </select></label
            ><label v-if="!isLifestyle" class="field"
              >专业组<select v-model="draft.courseGroup">
                <option value="Computing">计算机 / 信息技术</option>
                <option value="Engineering">工程类</option>
                <option value="Business">经管类</option>
                <option value="Other">其他</option>
              </select></label
            ><label v-if="!isLifestyle" class="field"
              >在读年级<select v-model.number="draft.studyYear">
                <option v-for="year in 4" :key="year" :value="year">
                  第 {{ year }} 年
                </option>
              </select></label
            ><label class="field"
              >{{ isLifestyle ? 'CGPA（0–4）' : 'CGPA 区间中点（0–4）'
              }}<input
                v-model.number="draft.cgpa"
                type="number"
                min="0"
                max="4"
                step="0.005"
                required /></label
            ><label v-if="!isLifestyle" class="field"
              >婚姻状态<select v-model="draft.married">
                <option value="No">未婚</option>
                <option value="Yes">已婚</option>
              </select></label
            ><template v-if="isLifestyle"
              ><label class="field"
                >院系<select v-model="draft.department">
                  <option value="Science">理科</option>
                  <option value="Engineering">工程</option>
                  <option value="Arts">人文艺术</option>
                  <option value="Medical">医学</option>
                  <option value="Business">经管</option>
                </select></label
              ><label class="field"
                >睡眠时长（小时 / 天）<input
                  v-model.number="draft.sleepHours"
                  type="number"
                  min="0"
                  max="24"
                  step="0.1"
                  required /></label
              ><label class="field"
                >学习时长（小时 / 天）<input
                  v-model.number="draft.studyHours"
                  type="number"
                  min="0"
                  max="24"
                  step="0.1"
                  required /></label
              ><label class="field"
                >社交媒体（小时 / 天）<input
                  v-model.number="draft.socialMediaHours"
                  type="number"
                  min="0"
                  max="24"
                  step="0.1"
                  required /></label
              ><label class="field"
                >每周运动（分钟）<input
                  v-model.number="draft.physicalActivity"
                  type="number"
                  min="0"
                  max="10080"
                  required /></label
              ><label class="field"
                >压力等级（0–10）<input
                  v-model.number="draft.stressLevel"
                  type="number"
                  min="0"
                  max="10"
                  required /></label
            ></template>
          </div>
          <button class="btn primary" :disabled="predicting">
            {{ predicting ? '计算中…' : '比较模型预测'
            }}<Icon name="arrow" :size="17" />
          </button>
          <div v-if="prediction">
            <div
              v-for="row in prediction.predictions"
              :key="row.id"
              class="prediction-row"
            >
              <div>
                <strong>{{ row.name }}</strong
                ><small
                  >预测标签 {{ row.predictedClass }} · 判定阈值
                  {{ row.threshold }}</small
                >
              </div>
              <span class="prediction-probability"
                >{{ percent(row.positiveProbability) }}<small>%</small></span
              >
            </div>
            <p class="experiment-note">{{ prediction.note }}</p>
          </div>
        </form>
        <article class="card">
          <div class="card-heading">
            <div>
              <h2>可解释模型</h2>
              <p>
                {{
                  data.validation.trainRows ? '训练集模型' : '全量重训模型'
                }}的规则或系数
              </p>
            </div>
          </div>
          <div class="tabs">
            <button
              :class="{active: explanation === 'j48'}"
              @click="explanation = 'j48'"
            >
              决策树</button
            ><button
              :class="{active: explanation === 'logistic'}"
              @click="explanation = 'logistic'"
            >
              逻辑回归</button
            ><button
              v-if="data.models.some((m) => m.id === 'randomForest')"
              :class="{active: explanation === 'randomForest'}"
              @click="explanation = 'randomForest'"
            >
              随机森林
            </button>
          </div>
          <pre class="model-text">{{ modelText }}</pre>
        </article>
      </section>
      <div class="rule-note">
        <Icon name="shield" :size="20" />
        <div>
          <strong>解释清楚结果的边界</strong>
          <p>{{ data.target }}。{{ data.limitations }}</p>
        </div>
      </div>
    </template>
    <section v-else-if="data" class="card state">
      <Icon name="chart" :size="38" />
      <h2>准备公开数据，开启模型实验</h2>
      <p>{{ data.message }}</p>
      <code>{{ data.downloadCommand }}</code>
      <p>在仓库根目录执行上述命令，然后重启后端或点击“重新训练模型”。</p>
    </section>
  </PageState>
</template>
