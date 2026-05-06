import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api/measures': { target: 'http://localhost:8080', rewrite: (path: string) => path.replace(/^\/api\/measures/, '/api') },
      '/api/profiles': { target: 'http://localhost:8082', rewrite: (path: string) => path.replace(/^\/api\/profiles/, '/api') },
      '/api/treatments': { target: 'http://localhost:8083', rewrite: (path: string) => path.replace(/^\/api\/treatments/, '/api') },
      '/api/analyze': { target: 'http://localhost:8084', rewrite: (path: string) => path.replace(/^\/api\/analyze/, '/api') },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
