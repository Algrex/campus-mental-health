import {createRouter, createWebHistory} from 'vue-router';
import OverviewView from '../views/OverviewView.vue';
import ModulePlaceholder from '../components/ModulePlaceholder.vue';

const moduleRoutes = [
  ['/auth/login', '账号与权限', 'A / E', '登录、角色与数据访问范围'],
  ['/student/profile', '学生档案', 'B / E', '个人档案与授权信息'],
  ['/student/assessments', '心理测评', 'B / E', '量表填写、计分与历史记录'],
  ['/student/check-ins', '自评打卡', 'B / E', '日常打卡与历史变化'],
  ['/workspace/students', '学生管理', 'B / F', '授权档案查询与多源数据导入'],
  ['/workspace/risks', '风险与趋势', 'C / F', '模型评估、异常线索与时序趋势'],
  ['/workspace/alerts', '预警通知', 'D / F', '分级推送、投递确认与受理'],
  [
    '/workspace/interventions',
    '干预随访',
    'D / F',
    '咨询、干预、随访与复评关闭',
  ],
  [
    '/contact/notifications',
    '联系人通知',
    'A / D / F',
    '授权通知摘要与接收确认',
  ],
];

export const routes = [
  {path: '/', name: 'overview', component: OverviewView},
  ...moduleRoutes.map(([path, title, owner, description]) => ({
    path,
    component: ModulePlaceholder,
    props: {title, owner, description},
    meta: {title, owner, scaffold: true},
  })),
  {
    path: '/:pathMatch(.*)*',
    component: ModulePlaceholder,
    props: {
      title: '页面不存在',
      owner: 'E / F',
      description: '请通过左侧导航进入已有模块。',
    },
  },
];

export default createRouter({history: createWebHistory(), routes});
