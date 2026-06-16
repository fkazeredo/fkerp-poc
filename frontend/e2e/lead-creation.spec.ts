import { test, expect } from '@playwright/test';

// Critical journey: sign in with the seed user, register a lead, see the success feedback.
test('signs in and registers a lead', async ({ page }) => {
  // --- Login ---
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();

  // --- New lead form ---
  await page.goto('/leads/new');
  await expect(page.getByRole('heading', { name: 'Novo Lead' })).toBeVisible();

  await page.locator('#name').fill(`E2E Lead ${Date.now()}`);

  // Origin is a PrimeNG select: open it and pick the first option.
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();

  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);

  await page.getByRole('button', { name: 'Salvar lead' }).click();

  // Success feedback (toast) and return to the home screen.
  await expect(page.getByText('Lead criado')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
});

test('rejects login with wrong credentials', async ({ page }) => {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('wrong-password');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.getByText('Usuário ou senha inválidos.')).toBeVisible();
});

// Regression: Cancel on the lead form must take the user back to the home screen.
test('cancel on the lead form returns to home', async ({ page }) => {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();

  await page.goto('/leads/new');
  await page.locator('#name').fill('Para descartar');
  await page.getByRole('button', { name: 'Cancelar' }).click();

  await expect(page).not.toHaveURL(/leads\/new/);
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
});
