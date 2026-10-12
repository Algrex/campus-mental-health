<script setup>
import {ref, computed} from 'vue';
import {ElMessage} from 'element-plus';
import {api} from '../api/portal.js';
import Icon from '../components/Icon.vue';
import LevelBadge from '../components/LevelBadge.vue';
const questions = [
  '做事时提不起劲或没有兴趣',
  '感到心情低落、沮丧或绝望',
  '入睡困难、睡不安稳，或睡眠过多',
  '感觉疲倦或没有活力',
  '食欲不振或吃得过多',
  '觉得自己很糟，或觉得自己让自己或家人失望',
  '对事物难以专注，例如阅读或看电视',
  '动作或说话变得缓慢，或坐立不安、比平常更容易动来动去',
  '觉得不如死掉，或想到以某种方式伤害自己',
];
const choices = ['完全没有', '有几天', '超过一半天数', '几乎每天'];
const answers = ref(Array(9).fill(null));
const saving = ref(false);
const result = ref(null);
const requestId = ref(crypto.randomUUID());
const error = ref('');
const completed = computed(
  () => answers.value.filter((value) => value !== null).length,
);
async function submit() {
  if (completed.value !== 9) {
    ElMessage.warning('请回答全部 9 个问题后提交');
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    result.value = await api.assess({
      answers: answers.value,
      requestId: requestId.value,
    });
    window.scrollTo({top: 0, behavior: 'smooth'});
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
function again() {
  answers.value = Array(9).fill(null);
  requestId.value = crypto.randomUUID();
  result.value = null;
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">A MOMENT FOR YOURSELF</span>
      <h1>心理自评</h1>
      <p>留一点时间给自己，诚实地回应近两周的感受。</p>
    </div>
    <span class="subtle-tag">PHQ-9 · 约 3 分钟</span>
  </div>
  <section v-if="result" class="card assessment-result">
    <span class="result-icon"><Icon name="check" :size="30" /></span>
    <h2>谢谢你，认真回应自己的感受。</h2>
    <p class="muted">本次自评已保存到你的健康档案。</p>
    <div class="assessment-score">
      <strong>{{ result.score }}</strong
      ><span>/ 27</span>
    </div>
    <LevelBadge :level="result.riskLevel" />
    <p class="wellbeing-tip">
      {{
        result.riskLevel === 'LOW'
          ? '继续保持适合自己的节奏。你也可以用每日打卡记录小小的变化。'
          : '分数不代表诊断。如果近来感到困难，建议与学校心理中心或信任的人聊一聊。'
      }}
    </p>
    <p v-if="answers[8] > 0" class="urgent-support">
      如果你此刻可能伤害自己，请先到有人陪伴的安全场所，立即联系身边可信任的人、学校值班人员或当地急救服务获得即时帮助。
    </p>
    <div class="action-row centered">
      <router-link to="/profile" class="btn primary">查看我的档案</router-link
      ><router-link to="/check-ins" class="btn">记录今日心情</router-link
      ><button class="text-button" @click="again">再做一次</button>
    </div>
  </section>
  <template v-else
    ><div class="assessment-layout">
      <form class="card questionnaire" @submit.prevent="submit">
        <div class="questionnaire-intro">
          <h2>过去两周，以下情况困扰你的频率是？</h2>
          <p>请选择最接近自身感受的选项。没有“正确答案”，也无需担心评价。</p>
        </div>
        <fieldset
          v-for="(question, index) in questions"
          :key="index"
          class="question"
        >
          <legend>
            <span>{{ String(index + 1).padStart(2, '0') }}</span
            >{{ question }}
          </legend>
          <div class="answer-options">
            <label
              v-for="(choice, value) in choices"
              :key="choice"
              :class="{chosen: answers[index] === value}"
              ><input
                v-model="answers[index]"
                type="radio"
                :name="'question-' + index"
                :value="value"
                required
              /><span>{{ choice }}</span
              ><small>{{ value }} 分</small></label
            >
          </div>
        </fieldset>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <div class="questionnaire-footer">
          <span>{{ completed }} / 9 项已完成</span
          ><button class="btn primary" :disabled="saving || completed !== 9">
            {{ saving ? '正在保存…' : '提交自评'
            }}<Icon name="arrow" :size="17" />
          </button>
        </div>
      </form>
      <aside class="assessment-aside">
        <div class="card">
          <span class="stat-icon green"><Icon name="heart" :size="24" /></span>
          <h2>这是了解自己的起点</h2>
          <p>自评帮助你整理近期的感受，也为后续沟通提供线索。</p>
          <div class="mini-progress">
            <i :style="{width: (completed / 9) * 100 + '%'}"></i>
          </div>
          <small class="muted">已完成 {{ completed }} / 9 项</small>
          <hr />
          <p>结果用于辅助关注与人工复核，不替代专业诊断。</p>
          <p>出现困扰时，主动求助是一种照顾自己的方式。</p>
        </div>
        <div class="info-note">
          参考 PHQ-9。问题措辞为演示中文表达。第 9
          项非零会提示工作人员优先复核。
        </div>
      </aside>
    </div></template
  >
</template>
