import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

test('global keyboard shortcuts navigate the app', async ({ page }) => {
  await login(page);

  // n -> new lead
  await page.keyboard.press('n');
  await expect(page).toHaveURL(/\/leads\/new$/);

  // g then l -> leads list
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
  await page.keyboard.press('g');
  await page.keyboard.press('l');
  await expect(page).toHaveURL(/\/leads$/);
});

test('the command palette opens from the top bar', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: /Buscar comando/ }).click();
  await expect(page.getByRole('dialog', { name: 'Comandos' })).toBeVisible();
});

test('the "i" shortcut opens the interaction dialog on a lead detail', async ({ page }) => {
  await login(page);

  const name = `E2E Shortcut ${Date.now()}`;
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill('11999995555');
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();

  await page.keyboard.press('i');
  await expect(page.getByRole('dialog', { name: 'Registrar interação' })).toBeVisible();
});

test('the theme toggle switches to dark mode', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: 'Tema escuro' }).click();
  await expect(page.locator('html')).toHaveClass(/app-dark/);
});
