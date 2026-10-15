import {afterEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import StudentDrawer from './StudentDrawer.vue';
import {api} from '../api/portal.js';
vi.mock('../api/portal.js', () => ({api: {student: vi.fn()}}));
let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.resetAllMocks();
});
describe('student detail interactions', () => {
  it('loads initial identity and switches between history tabs', async () => {
    api.student.mockResolvedValue({
      student: {
        displayName: '测试同学',
        college: '计算机学院',
        major: '计算机',
        studentCode: 'QA',
        yearName: '大一',
        riskLevel: 'LOW',
        currentScore: 0,
        sourceName: '回归测试',
      },
      assessments: [
        {
          id: 'a',
          score: 0,
          riskLevel: 'LOW',
          sourceName: '测试自评',
          createdAt: '2026-10-15T10:00:00',
        },
      ],
      checkIns: [
        {
          id: 'c',
          checkDate: '2026-10-15',
          mood: 3,
          sleepHours: 8,
          stress: 2,
          note: '日常测试',
        },
      ],
      trend: [],
    });
    wrapper = mount(StudentDrawer, {
      props: {modelValue: 'student-1'},
      global: {
        stubs: {ElDrawer: {template: '<div><slot /></div>'}, Chart: true},
      },
    });
    await flushPromises();
    expect(api.student).toHaveBeenCalledWith('student-1');
    expect(wrapper.text()).toContain('暂无趋势记录');
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '测评记录')
      .trigger('click');
    expect(wrapper.text()).toContain('测试自评');
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '日常打卡')
      .trigger('click');
    expect(wrapper.text()).toContain('日常测试');
    await wrapper.setProps({modelValue: ''});
    await flushPromises();
    expect(wrapper.text()).not.toContain('测试同学');
  });
});
