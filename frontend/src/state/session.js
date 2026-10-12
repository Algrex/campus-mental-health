import {reactive} from 'vue';
import {getJson, request, post, setCsrfToken} from '../api/http.js';
import {api} from '../api/portal.js';
export const session = reactive({user: null, loaded: false, inbox: []});
export const roleNames = {
  CENTER: '心理中心',
  COUNSELOR: '学院辅导员',
  STUDENT: '学生',
  CONTACT: '联系人',
};
export function home(role = session.user?.role) {
  return role === 'STUDENT'
    ? '/profile'
    : role === 'CONTACT'
      ? '/notifications'
      : '/';
}
export async function loadSession() {
  const data = await getJson('/api/v1/auth/session');
  session.user = data.user;
  session.loaded = true;
  setCsrfToken(data.csrfToken);
  return data.user;
}
export async function login(username, password) {
  await loadSession();
  await request('/api/v1/auth/login', {
    method: 'POST',
    form: true,
    body: {username, password},
  });
  await loadSession();
  await refreshInbox();
}
export async function logout() {
  await post('/api/v1/auth/logout');
  session.inbox = [];
  await loadSession();
}
export async function refreshInbox() {
  if (session.user) session.inbox = await api.notifications();
}
