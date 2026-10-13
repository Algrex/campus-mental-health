import {afterEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import AssessmentView from './AssessmentView.vue';
import {api} from '../api/portal.js';
vi.mock('../api/portal.js', () => ({api: {assess: vi.fn()}}));
vi.mock('element-plus', () => ({ElMessage: {warning: vi.fn()}}));
let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllMocks();
});
describe('psychological assessment', () => {
  it('requires all nine answers and persists the result through the API', async () => {
    api.assess.mockResolvedValue({score: 0, riskLevel: 'LOW'});
    wrapper = mount(AssessmentView, {
      global: {stubs: {RouterLink: {template: '<a><slot /></a>'}}},
    });
    expect(
      wrapper.find('button[type="submit"], .questionnaire-footer button')
        .element.disabled,
    ).toBe(true);
    for (let index = 0; index < 9; index++)
      await wrapper
        .find(`input[name="question-${index}"][value="0"]`)
        .setValue();
    expect(wrapper.find('.questionnaire-footer button').element.disabled).toBe(
      false,
    );
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(api.assess).toHaveBeenCalledWith({
      answers: Array(9).fill(0),
      requestId: expect.any(String),
    });
    expect(wrapper.text()).toContain('本次自评已保存');
    expect(wrapper.find('.level-badge').classes()).toContain('low');
    expect(wrapper.find('.wellbeing-tip').text()).toContain('继续保持');
  });
  it('uses the combined rule level while keeping the PHQ score', async () => {
    api.assess.mockResolvedValue({
      score: 0,
      riskLevel: 'LOW',
      ruleRiskLevel: 'HIGH',
    });
    wrapper = mount(AssessmentView, {global: {stubs: {RouterLink: true}}});
    for (let index = 0; index < 9; index++)
      await wrapper
        .find(`input[name="question-${index}"][value="0"]`)
        .setValue();
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(wrapper.find('.assessment-score strong').text()).toBe('0');
    expect(wrapper.find('.level-badge').classes()).toContain('high');
    expect(wrapper.text()).toContain('关注等级综合参考本次自评与近期打卡记录');
    expect(wrapper.find('.wellbeing-tip').text()).not.toContain('继续保持');
    expect(wrapper.find('.wellbeing-tip').text()).toContain('心理中心');
  });
  it('keeps a stable idempotency key when a failed submission is retried', async () => {
    api.assess
      .mockRejectedValueOnce(new Error('服务暂时不可用'))
      .mockResolvedValueOnce({score: 0, riskLevel: 'LOW'});
    wrapper = mount(AssessmentView, {global: {stubs: {RouterLink: true}}});
    for (let index = 0; index < 9; index++)
      await wrapper
        .find(`input[name="question-${index}"][value="0"]`)
        .setValue();
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('服务暂时不可用');
    const firstKey = api.assess.mock.calls[0][0].requestId;
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(api.assess.mock.calls[1][0].requestId).toBe(firstKey);
  });
});
