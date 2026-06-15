import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests for the critical journey (login -> create lead). They run against a running
 * stack — bring it up first (`docker compose up` from the repo root), then `npm run e2e`. Override
 * the target with E2E_BASE_URL.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
