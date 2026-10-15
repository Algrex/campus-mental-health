import {afterEach, describe, expect, it, vi} from 'vitest';
import {mount, flushPromises} from '@vue/test-utils';
import OverviewView from './OverviewView.vue';
import {api} from '../api/portal.js';

vi.mock('../api/portal.js', () => ({api: {dashboard: vi.fn()}}));
vi.mock('vue-router', () => ({useRouter: () => ({push: vi.fn()})}));
vi.mock('../state/session.js', () => ({
  session: {user: {displayName: '测试中心'}},
}));
vi.mock('../components/Chart.vue', () => ({
  default: {template: '<div />'},
}));
vi.mock('../components/CaseDrawer.vue', () => ({
  default: {template: '<div />'},
}));

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllMocks();
});

function followUp(id, displayName, followUpStatus, status = 'INTERVENING') {
  return {
    id,
    alertId: `alert-${id}`,
    displayName,
    kind: '电话随访',
    authorName: '测试辅导员',
    nextDate: '2026-10-13',
    followUpStatus,
    status,
  };
}

function dashboard(followUps) {
  return {
    stats: {
      students: 10,
      assessmentRate: 80,
      attention: 2,
      pending: 1,
      activeAlerts: 2,
    },
    trend: [],
    distribution: [{level: 'LOW', value: 10}],
    priority: [],
    source: '固定合成测试数据',
    followUps,
  };
}

describe('overview follow-up plans', () => {
  it('shows only open planned arrangements and an empty state when only history remains', async () => {
    const history = [
      followUp('replaced', '已替代记录', 'SUPERSEDED'),
      followUp('cancelled', '已取消记录', 'CANCELLED'),
      {...followUp('unplanned', '未安排记录', 'NONE'), nextDate: null},
      followUp('closed', '已关闭记录', 'PLANNED', 'CLOSED'),
    ];
    api.dashboard
      .mockResolvedValueOnce(
        dashboard([
          ...history,
          followUp('current', '当前计划', 'PLANNED'),
          followUp('review', '复评中计划', 'PLANNED', 'AWAITING_REASSESSMENT'),
        ]),
      )
      .mockResolvedValueOnce(dashboard(history));
    wrapper = mount(OverviewView, {
      global: {stubs: {RouterLink: {template: '<a><slot /></a>'}}},
    });
    await flushPromises();

    expect(api.dashboard).toHaveBeenCalledTimes(1);
    expect(
      wrapper.findAll('.followup-item strong').map((item) => item.text()),
    ).toEqual(['当前计划 · 电话随访', '复评中计划 · 电话随访']);
    expect(wrapper.text()).not.toContain('暂无随访安排');
    for (const row of history) {
      expect(wrapper.text()).not.toContain(row.displayName);
    }

    await wrapper.find('.page-heading button').trigger('click');
    await flushPromises();
    expect(api.dashboard).toHaveBeenCalledTimes(2);
    expect(wrapper.findAll('.followup-item')).toHaveLength(0);
    expect(wrapper.text()).toContain('暂无随访安排');
  });
});
