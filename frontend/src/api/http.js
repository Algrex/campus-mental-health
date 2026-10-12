const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

/** Fetches JSON and rejects unavailable services and non-success responses. */
export async function getJson(path, {signal} = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'GET',
    headers: {Accept: 'application/json'},
    credentials: 'same-origin',
    signal,
  });
  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`);
  }
  return response.json();
}
