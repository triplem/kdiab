import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: (id: string) => {
          if (id.includes('node_modules/recharts') || id.includes('node_modules/d3')) return 'charts'
          if (id.includes('node_modules/i18next') || id.includes('node_modules/react-i18next')) return 'i18n'
          if (id.includes('node_modules/oidc-client-ts') || id.includes('node_modules/react-oidc-context')) return 'oidc'
          if (id.includes('node_modules/@tanstack/react-query')) return 'query'
          if (id.includes('node_modules/zod') || id.includes('node_modules/@hookform')) return 'zod'
          if (id.includes('node_modules/axios')) return 'axios'
          if (id.includes('node_modules/react') || id.includes('node_modules/react-dom') || id.includes('node_modules/react-hook-form')) return 'react'
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
        // thin axios-wrapper files: no business logic, analogous to generated code
        'src/api/analyzeApi.ts',
        'src/api/carbsApi.ts',
        'src/api/measuresApi.ts',
        'src/api/profilesApi.ts',
        'src/api/treatmentsApi.ts',
        'src/api/usersApi.ts',
        'src/i18n/**',
        'src/test/**',
        '**/*.d.ts',
        'src/main.tsx',
        // top-level orchestration — tab routing, auth rendering, role guards;
        // integration-level concerns tested via E2E; toast handler unit tests live
        // in App.test.tsx and verify mock interactions without requiring full
        // branch coverage of the composition root
        'src/App.tsx',
        'src/vite-env.d.ts',
        // dashboard rendering components — business logic lives in basalUtils.ts (tested)
        'src/features/dashboard/DashboardView.tsx',
        'src/features/dashboard/GlucoseTrendChart.tsx',
        'src/features/dashboard/BasalRateChart.tsx',
        // data-fetching hook — integration concern, tested via component tests
        'src/features/dashboard/useDashboardData.ts',
        // BolusBarShape and recharts callbacks (formatHour, shape, labelFormatter, formatter)
        // are only reachable through unmocked recharts rendering — integration-level concern
        'src/features/analytics/BolusAvgChart.tsx',
        // treatment sub-forms — thin UI adapters, business logic tested in AddTreatmentModal tests
        'src/features/treatments/forms/**',
      ],
      // Coverage thresholds reflect non-excluded files — see docs/adr/ADR-015-coverage-exclusions.adoc
      thresholds: {
        statements: 72,
        branches: 59,
        functions: 63,
        lines: 74,
      },
    },
  },
})
