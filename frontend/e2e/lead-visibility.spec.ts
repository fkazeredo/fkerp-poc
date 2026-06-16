import { test, expect, Page } from '@playwright/test';

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
}

test('a director consults a lead read-only — no actions, no Novo lead', async ({ page }) => {
  // A manager creates a lead the director will browse.
  await login(page, 'comercial', 'comercial123');
  const name = `E2E Vis ${Date.now()}`;
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();
  await logout(page);

  // The director sees it but cannot operate.
  await login(page, 'diretor', 'diretor123');
  await page.goto('/leads');
  await expect(page.getByRole('button', { name: 'Novo lead' })).toHaveCount(0);
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();

  await expect(page.getByRole('button', { name: 'Registrar interação' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Qualificar' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Marcar como perdido' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Reatribuir' })).toHaveCount(0);
});

test('a finance user has no access to the Lead module', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');

  // No Leads navigation, and the home shows a no-access notice.
  await expect(page.getByRole('link', { name: 'Leads' })).toHaveCount(0);
  await expect(page.getByText('Você não tem acesso ao módulo Comercial / CRM.')).toBeVisible();

  // Navigating to /leads is blocked by the guard (redirected back home).
  await page.goto('/leads');
  await expect(page).not.toHaveURL(/\/leads$/);
  await expect(page.getByText('Você não tem acesso ao módulo Comercial / CRM.')).toBeVisible();
});
