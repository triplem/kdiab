import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3005',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], storageState: 'e2e/.auth/sarah.json' },
      dependencies: ['setup'],
      testIgnore: /auth\.setup\.ts|screenshots\.spec\.ts/,
    },
    {
      // Manual screenshot capture for the patient guide — not run in CI.
      // Usage: npx playwright test e2e/screenshots.spec.ts --project=screenshots
      name: 'screenshots',
      use: { ...devices['Desktop Chrome'], storageState: 'e2e/.auth/sarah.json' },
      dependencies: ['setup'],
      testMatch: /screenshots\.spec\.ts/,
    },
  ],
});
