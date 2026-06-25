import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slice 5 end-to-end: the Commission detail consultation page. The populated detail (origin/calculation/
 * receivable/eligibility + the own-vs-all visibility) is covered thoroughly by CommissionApiIntegrationTest and the
 * CommissionDetailPage component spec; this E2E covers the cross-cutting concerns — the route + read guard and the
 * not-found render (the isolated e2e stack has no seeded commissions).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('a manager opening an unknown commission sees the not-found state', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.goto('/comissoes/00000000-0000-0000-0000-0000000000ff');
  await expect(page.getByText('Comissão não encontrada.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
});

test('a representative (own tier) reaches the commission detail route', async ({ page }) => {
  await login(page, 'representante', 'representante123');
  await page.goto('/comissoes/00000000-0000-0000-0000-0000000000ff');
  // The read guard lets them in (own tier); the unknown id renders not-found.
  await expect(page).toHaveURL(/\/comissoes\//);
  await expect(page.getByText('Comissão não encontrada.')).toBeVisible();
});

test('a user without any commission scope is redirected away from the detail route', async ({ page }) => {
  await login(page, 'operacoes', 'operacoes123');
  await page.goto('/comissoes/00000000-0000-0000-0000-0000000000ff');
  await expect(page).not.toHaveURL(/\/comissoes\//);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
