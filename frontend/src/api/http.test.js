import {afterEach, describe, expect, it, vi} from 'vitest';
import {getJson} from './http.js';

afterEach(() => vi.unstubAllGlobals());

describe('API requests', () => {
  it('rejects permission errors instead of returning a fake success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok: false, status: 403}));
    await expect(getJson('/api/v1/students')).rejects.toThrow('HTTP 403');
  });

  it('passes cancellation through to the network request', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({stage: 'SCAFFOLD'}),
    });
    vi.stubGlobal('fetch', fetchMock);
    await getJson('/api/v1/system/info', {signal: controller.signal});
    expect(fetchMock.mock.calls[0][1].signal).toBe(controller.signal);
  });
});
