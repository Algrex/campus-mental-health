import {defineConfig} from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import {loadEnv} from 'vite';

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const port = Number(env.VITE_DEV_PORT || 5173);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('VITE_DEV_PORT must be an integer between 1 and 65535.');
  }
  const backendUrl = env.VITE_BACKEND_URL || 'http://127.0.0.1:8080';
  return {
    plugins: [vue()],
    server: {
      port,
      strictPort: true,
      proxy: {
        '/api': backendUrl,
        '/actuator': backendUrl,
      },
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.test.js'],
    },
  };
});
