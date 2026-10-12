import {getJson} from './http.js';

export function getSystemInfo(options) {
  return getJson('/api/v1/system/info', options);
}
