const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
let csrfToken = '';
export function setCsrfToken(token) {
  csrfToken = token || '';
}
export async function request(
  path,
  {method = 'GET', body, signal, form = false} = {},
) {
  const headers = {Accept: 'application/json'};
  if (body !== undefined)
    headers['Content-Type'] = form
      ? 'application/x-www-form-urlencoded'
      : 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken;
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    signal,
    body:
      body === undefined
        ? undefined
        : form
          ? new URLSearchParams(body).toString()
          : JSON.stringify(body),
  });
  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`;
    try {
      message = (await response.json()).message || message;
    } catch {
      /* Non-JSON errors keep their HTTP status. */
    }
    const error = new Error(message);
    error.status = response.status;
    if (response.status === 401 && !path.includes('/auth/'))
      window.dispatchEvent(new Event('session-expired'));
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
export const getJson = (path, options) => request(path, options);
export const post = (path, body = {}) => request(path, {method: 'POST', body});
export const put = (path, body) => request(path, {method: 'PUT', body});
