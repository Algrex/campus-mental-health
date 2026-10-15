import {afterEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import FollowUpsView from './FollowUpsView.vue';
import {api} from '../api/portal.js';

vi.mock('../api/portal.js', () => ({api: {followUps: vi.fn()}}));
vi.mock('../components/CaseDrawer.vue', () => ({
  default: {template: '<div />'},
}));

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.useRealTimers();
  vi.clearAllMocks();
});

function record(
  id,
  displayName,
  followUpStatus,
  nextDate,
  status = 'INTERVENING',
) {
  return {
    id,
    alertId: `alert-${id}`,
    displayName,
    college: '计算机学院',
    riskLevel: 'HIGH',
    kind: '电话随访',
    content: '了解近况并确认后续支持安排',
    authorName: '测试辅导员',
    createdAt: '2026-10-10T10:00:00',
    followUpStatus,
    nextDate,
    status,
  };
}

describe('follow-up plans and history', () => {
  it('counts only open planned follow-ups as active or due while retaining all history', async () => {
    vi.useFakeTimers({toFake: ['Date']});
    vi.setSystemTime(new Date(2026, 9, 13, 12));
    api.followUps.mockResolvedValue([
      record('overdue', '逾期计划', 'PLANNED', '2026-10-12'),
      record('today', '今日计划', 'PLANNED', '2026-10-13'),
      record('future', '未来计划', 'PLANNED', '2026-10-14'),
      record('replaced', '已替代记录', 'SUPERSEDED', '2026-10-11'),
      record('cancelled', '已取消记录', 'CANCELLED', '2026-10-11'),
      record('unplanned', '未安排记录', 'NONE', null),
      record('closed', '已关闭记录', 'PLANNED', '2026-10-11', 'CLOSED'),
    ]);
    wrapper = mount(FollowUpsView, {
      global: {stubs: {RouterLink: {template: '<a><slot /></a>'}}},
    });
    await flushPromises();

    const visibleNames = () =>
      wrapper
        .findAll('.followup-card .person strong')
        .map((item) => item.text());
    expect(api.followUps).toHaveBeenCalledTimes(1);
    expect(visibleNames()).toEqual(['逾期计划', '今日计划', '未来计划']);
    const stats = wrapper.findAll('.followup-stats strong');
    expect(stats[0].text()).toMatch(/^2\s*次$/);
    expect(stats[1].text()).toMatch(/^3\s*个个案$/);
    expect(stats[2].text()).toMatch(/^7\s*条$/);

    await wrapper
      .findAll('.tabs button')
      .find((button) => button.text() === '今日及逾期')
      .trigger('click');
    expect(visibleNames()).toEqual(['逾期计划', '今日计划']);

    await wrapper
      .findAll('.tabs button')
      .find((button) => button.text() === '全部记录')
      .trigger('click');
    expect(visibleNames()).toEqual([
      '逾期计划',
      '今日计划',
      '未来计划',
      '已替代记录',
      '已取消记录',
      '未安排记录',
      '已关闭记录',
    ]);
    expect(wrapper.text()).toContain('未安排下次日期');
    expect(wrapper.text()).toContain('当前计划');
    expect(wrapper.text()).toContain('已替代');
    expect(wrapper.text()).toContain('已取消');
    expect(wrapper.text()).toContain('未安排');
  });
});
