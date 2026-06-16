import { test, expect } from '@playwright/test';

test('shows the application version on the login screen', async ({ page }) => {
  await page.goto('/login');
  await expect(page.locator('.login-version')).toHaveText(/v\d+\.\d+\.\d+/);
});
