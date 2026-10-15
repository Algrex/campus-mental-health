import {afterEach, describe, expect, it, vi} from 'vitest';
import {getJson, post, request, setCsrfToken} from './http.js';

afterEach(() => {
  vi.unstubAllGlobals();
  setCsrfToken('');
});

describe('API requests', () => {
  it('sends the refreshed CSRF token and JSON body for writes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({ok: true, json: async () => ({ok: true})});
    vi.stubGlobal('fetch', fetchMock);
    setCsrfToken('fresh-login-token');
    await post('/api/v1/check-ins', {mood: 4});
    const options = fetchMock.mock.calls[0][1];
    expect(options.headers['X-XSRF-TOKEN']).toBe('fresh-login-token');
    expect(options.credentials).toBe('same-origin');
    expect(JSON.parse(options.body)).toEqual({mood: 4});
  });
  it('preserves a readable server rejection message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({message: '请完成全部问题'}),
      }),
    );
    await expect(post('/api/v1/assessments', {})).rejects.toThrow(
      '请完成全部问题',
    );
  });
  it('rejects permission errors instead of returning a fake success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok: false, status: 403}));
    await expect(getJson('/api/v1/students')).rejects.toThrow('HTTP 403');
  });

  it('passes cancellation through to the network request', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({stage: 'DEMO'}),
    });
    vi.stubGlobal('fetch', fetchMock);
    await getJson('/api/v1/system/info', {signal: controller.signal});
    expect(fetchMock.mock.calls[0][1].signal).toBe(controller.signal);
  });

  it('handles empty logout responses and URL-encoded login', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ok: true, status: 204})
      .mockResolvedValueOnce({ok: true, json: async () => ({ok: true})});
    vi.stubGlobal('fetch', fetchMock);
    expect(await post('/api/v1/auth/logout')).toBeNull();
    await request('/api/v1/auth/login', {
      method: 'POST',
      form: true,
      body: {username: 'center', password: 'a&b'},
    });
    expect(fetchMock.mock.calls[1][1].body).toBe(
      'username=center&password=a%26b',
    );
  });
  it('dispatches session expiry only for protected APIs', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({message: '会话已失效'}),
      }),
    );
    const listener = vi.fn();
    window.addEventListener('session-expired', listener);
    try {
      await expect(getJson('/api/v1/students')).rejects.toThrow('会话已失效');
      expect(listener).toHaveBeenCalledTimes(1);
      await expect(post('/api/v1/auth/login', {})).rejects.toThrow(
        '会话已失效',
      );
      expect(listener).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener('session-expired', listener);
    }
  });
});
