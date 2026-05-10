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
      '/api/carbs': {
        target: 'http://localhost:8085',
        rewrite: (path: string) => path.replace(/^\/api\/carbs/, '/api'),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      exclude: [
        'src/api/generated/**',
        'src/test/**',
        '**/*.d.ts',
        'src/main.tsx',
        'src/vite-env.d.ts',
      ],
      thresholds: {
        statements: 20,
        branches: 20,
        functions: 20,
        lines: 20,
      },
    },
  },
})
