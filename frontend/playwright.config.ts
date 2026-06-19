import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests. They run against an isolated, throwaway stack with an ephemeral database so they
 * never touch the development data: `npm run e2e:up` (brings up compose.e2e.yaml on port 4201),
 * `npm run e2e`, then `npm run e2e:down`. Override the target with E2E_BASE_URL (e.g. to point at the
 * dev stack on 4200).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // One retry everywhere: the heavy end-to-end journeys (lead→opportunity→funnel→proposal) drive many
  // sequential UI steps against a single shared backend, so under parallel load a dropdown/toast can
  // occasionally miss the timeout. Retries absorb that infra timing without weakening any assertion.
  retries: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4201',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
