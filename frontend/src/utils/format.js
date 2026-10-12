export const levels = {
  LOW: {name: '常规关注', color: '#529882'},
  MEDIUM: {name: '适度关注', color: '#c79b45'},
  HIGH: {name: '重点关注', color: '#d67d5c'},
  CRITICAL: {name: '优先联系', color: '#c46171'},
};
export const statuses = {
  PENDING: '待受理',
  ACCEPTED: '已受理',
  INTERVENING: '跟进中',
  AWAITING_REASSESSMENT: '待复评',
  CLOSED: '已完成',
};
export function date(value, full = false) {
  if (!value) return '—';
  return value.replace('T', ' ').slice(0, full ? 16 : 10);
}
export function localDay() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
export function levelName(value) {
  return levels[value]?.name || value;
}
