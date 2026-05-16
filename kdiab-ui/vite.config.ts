import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-hook-form'],
          query: ['@tanstack/react-query'],
          charts: ['recharts'],
          oidc: ['oidc-client-ts', 'react-oidc-context'],
          i18n: ['i18next', 'react-i18next'],
          zod: ['zod', '@hookform/resolvers'],
          axios: ['axios'],
        },
      },
    },
  },
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
      '/api/calc': {
        target: 'http://localhost:8086',
        rewrite: (path: string) => path.replace(/^\/api\/calc/, '/api'),
      },
      '/api/users': {
        target: 'http://localhost:8088',
        rewrite: (path: string) => path.replace(/^\/api\/users/, '/api'),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    exclude: ['**/node_modules/**', '**/e2e/**'],
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
        statements: 60,
        branches: 50,
        functions: 50,
        lines: 63,
      },
    },
  },
})
