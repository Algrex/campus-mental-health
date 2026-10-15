import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import CheckInView from './CheckInView.vue';
import {api} from '../api/portal.js';
import {localDay} from '../utils/format.js';
vi.mock('../api/portal.js', () => ({
  api: {profile: vi.fn(), checkIn: vi.fn()},
}));
vi.mock('element-plus', () => ({
  ElMessage: {success: vi.fn(), error: vi.fn()},
}));
let wrapper;
let profile;
const button = (text) =>
  wrapper.findAll('button').find((b) => b.text() === text);
beforeEach(() => {
  profile = {checkIns: []};
  api.profile.mockImplementation(async () => structuredClone(profile));
  api.checkIn.mockImplementation(async (draft) => {
    profile.checkIns = [{...draft, id: 'daily-record', checkDate: localDay()}];
    return profile.checkIns[0];
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.resetAllMocks();
});
describe('daily check-in interactions', () => {
  it('binds all mood and stress choices, saves and reloads the same daily entry', async () => {
    wrapper = mount(CheckInView, {global: {stubs: {Chart: true}}});
    await flushPromises();
    for (const label of ['低落', '偏低', '平静', '愉快', '很好']) {
      await wrapper
        .findAll('.mood-options button')
        .find((b) => b.text().includes(label))
        .trigger('click');
    }
    for (const label of ['很轻松', '较轻松', '一般', '较大', '很大'])
      await button(label).trigger('click');
    await wrapper.get('input[type="range"]').setValue(6.5);
    await wrapper.get('textarea').setValue('今日状态回归测试');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(api.checkIn).toHaveBeenCalledWith({
      mood: 5,
      sleepHours: 6.5,
      stress: 5,
      note: '今日状态回归测试',
    });
    expect(wrapper.text()).toContain('今日已打卡');
    expect(wrapper.get('textarea').element.value).toBe('今日状态回归测试');
    await wrapper.get('textarea').setValue('修改同一天的记录');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(profile.checkIns).toHaveLength(1);
    expect(wrapper.text()).toContain('更新今日打卡');
  });
  it('restores saved fields after a fresh mount', async () => {
    profile.checkIns = [
      {
        id: 'existing',
        checkDate: localDay(),
        mood: 2,
        sleepHours: '8.5',
        stress: 4,
        note: '已有日常记录',
      },
    ];
    wrapper = mount(CheckInView, {global: {stubs: {Chart: true}}});
    await flushPromises();
    expect(wrapper.get('input[type="range"]').element.value).toBe('8.5');
    expect(wrapper.get('textarea').element.value).toBe('已有日常记录');
    expect(wrapper.find('.mood-options .selected').text()).toContain('偏低');
    expect(wrapper.find('.stress-options .selected').text()).toBe('较大');
  });
});
