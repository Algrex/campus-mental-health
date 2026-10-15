import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import CaseDrawer from './CaseDrawer.vue';
import {api} from '../api/portal.js';
vi.mock('../api/portal.js', () => ({
  api: {alert: vi.fn(), transition: vi.fn(), intervention: vi.fn()},
}));
vi.mock('../state/session.js', () => ({refreshInbox: vi.fn()}));
vi.mock('element-plus', () => ({
  ElMessage: {success: vi.fn(), error: vi.fn()},
}));
let wrapper;
let data;
const options = {
  props: {modelValue: 'case-1'},
  global: {stubs: {ElDrawer: {template: '<div><slot /></div>'}}},
};
const button = (text) =>
  wrapper.findAll('button').find((b) => b.text() === text);
beforeEach(() => {
  data = {
    student: {
      displayName: '回归同学',
      college: '计算机学院',
      yearName: '大一',
      studentCode: 'QA001',
    },
    alert: {
      status: 'PENDING',
      riskLevel: 'MEDIUM',
      reason: '规则测试',
      createdAt: '2026-10-15T10:00:00',
    },
    records: [],
  };
  api.alert.mockImplementation(async () => structuredClone(data));
  api.transition.mockImplementation(async (_id, action, reason) => {
    data.alert.status = {
      accept: 'ACCEPTED',
      review: 'AWAITING_REASSESSMENT',
      close: 'CLOSED',
    }[action];
    if (action === 'close') data.alert.closeReason = reason;
  });
  api.intervention.mockImplementation(async (_id, record) => {
    data.records.push({...record, id: 'record-1'});
    data.alert.status = 'INTERVENING';
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.resetAllMocks();
});
describe('care case interactions', () => {
  it('loads a case supplied on initial mount, including a deep-linked alert', async () => {
    wrapper = mount(CaseDrawer, options);
    await flushPromises();
    expect(api.alert).toHaveBeenCalledWith('case-1');
    expect(wrapper.text()).toContain('回归同学');
  });
  it('supports accept, cancel, intervention, review and reasoned closure', async () => {
    wrapper = mount(CaseDrawer, options);
    await flushPromises();
    await button('受理并跟进').trigger('click');
    await flushPromises();
    await button('添加干预记录').trigger('click');
    await button('取消').trigger('click');
    expect(wrapper.find('textarea').exists()).toBe(false);
    await button('添加干预记录').trigger('click');
    await wrapper.find('textarea').setValue('本次回归测试的支持安排');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(api.intervention).toHaveBeenCalledWith(
      'case-1',
      expect.objectContaining({
        content: '本次回归测试的支持安排',
        nextDate: null,
      }),
    );
    await button('转入复评').trigger('click');
    await flushPromises();
    await button('复评并关闭').trigger('click');
    await wrapper.find('textarea').setValue('复评完成，继续保留后续支持');
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(api.transition).toHaveBeenLastCalledWith(
      'case-1',
      'close',
      '复评完成，继续保留后续支持',
    );
    expect(wrapper.text()).toContain('这个个案已完成');
    expect(wrapper.text()).toContain('本次回归测试的支持安排');
    expect(wrapper.emitted('changed')).toHaveLength(4);
  });
  it('shows a service failure and reloads through the retry interaction', async () => {
    api.alert.mockRejectedValueOnce(new Error('演示服务不可用'));
    wrapper = mount(CaseDrawer, options);
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('演示服务不可用');
    await button('重新加载').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('回归同学');
  });
});
