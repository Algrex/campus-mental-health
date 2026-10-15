import {afterEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import ProfileView from './ProfileView.vue';
import {api} from '../api/portal.js';

vi.mock('../api/portal.js', () => ({api: {profile: vi.fn()}}));
vi.mock('../components/Chart.vue', () => ({
  default: {template: '<div />'},
}));

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllMocks();
});

function profile(currentLevel, assessmentLevel) {
  return {
    student: {
      displayName: '测试同学',
      college: '计算机学院',
      major: '软件工程',
      yearName: '一年级',
      studentCode: 'TEST-001',
      riskLevel: currentLevel,
    },
    assessments: [
      {
        id: 'assessment-1',
        score: 0,
        riskLevel: 'LOW',
        ruleRiskLevel: assessmentLevel,
        createdAt: '2026-10-13T10:00:00',
        sourceName: '固定合成测试数据',
      },
    ],
    checkIns: [],
    trend: [],
  };
}

async function loadProfile(data) {
  api.profile.mockResolvedValue(data);
  wrapper = mount(ProfileView, {
    global: {stubs: {RouterLink: {template: '<a><slot /></a>'}}},
  });
  await flushPromises();
}

describe('profile assessment attention levels', () => {
  it('shows the saved combined level in the latest assessment and history while retaining the PHQ score', async () => {
    await loadProfile(profile('HIGH', 'HIGH'));

    expect(api.profile).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.assessment-score strong').text()).toBe('0');
    expect(wrapper.find('.centered .level-badge').classes()).toContain('high');
    expect(wrapper.find('tbody .level-badge').classes()).toContain('high');
    expect(wrapper.text()).toContain(
      '关注等级综合参考提交时的自评与近期打卡记录',
    );
    expect(wrapper.find('.wellbeing-tip').text()).toContain('心理中心');
    expect(wrapper.find('.wellbeing-tip').text()).not.toContain(
      '保持适合自己的生活节奏',
    );
  });

  it('falls back to the PHQ level when a legacy response has no combined level', async () => {
    const data = profile('LOW', undefined);
    delete data.assessments[0].ruleRiskLevel;
    await loadProfile(data);

    expect(wrapper.find('.centered .level-badge').classes()).toContain('low');
    expect(wrapper.find('tbody .level-badge').classes()).toContain('low');
    expect(wrapper.find('.wellbeing-tip').text()).toContain(
      '保持适合自己的生活节奏',
    );
  });

  it('offers support when newer check-ins raise the current level above the saved assessment', async () => {
    await loadProfile(profile('HIGH', 'LOW'));

    expect(wrapper.find('.profile-banner .level-badge').classes()).toContain(
      'high',
    );
    expect(wrapper.find('.centered .level-badge').classes()).toContain('low');
    expect(wrapper.find('tbody .level-badge').classes()).toContain('low');
    expect(wrapper.find('.wellbeing-tip').text()).toContain('心理中心');
  });
});
