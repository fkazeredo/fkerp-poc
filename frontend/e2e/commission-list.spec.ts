import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slice 4 end-to-end: the operational Commission list (Comercial module). The data-driven behavior (the
 * eight filters, the default operational set, the own-vs-all visibility) is covered thoroughly by
 * CommissionApiIntegrationTest against a seeded Postgres; this E2E covers the cross-cutting concerns — the module/nav
 * wiring, the route, the read guard, the keyboard shortcut and the empty render (the isolated e2e stack has no seeded
 * commissions, so the list is empty).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('a manager opens the Comissões list with its columns and filters (empty stack)', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');

  await page.goto('/comissoes');
  await expect(page.getByRole('heading', { name: 'Comissões' })).toBeVisible();
  // The required operational columns + filters are present.
  await expect(page.getByRole('columnheader', { name: 'Beneficiário' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Conta a receber' })).toBeVisible();
  await expect(page.getByText('Elegível de')).toBeVisible();
  await expect(page.getByText('Paga de')).toBeVisible();
  // Empty stack → the empty state.
  await expect(page.getByText('Nenhuma comissão para acompanhar.')).toBeVisible();
});

test('the "g m" shortcut jumps to the Comissões list', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.keyboard.press('g');
  await page.keyboard.press('m');
  await expect(page).toHaveURL(/\/comissoes$/);
  await expect(page.getByRole('heading', { name: 'Comissões' })).toBeVisible();
});

test('a representative (own tier) reaches the Comissões list', async ({ page }) => {
  await login(page, 'representante', 'representante123');
  await page.goto('/comissoes');
  await expect(page.getByRole('heading', { name: 'Comissões' })).toBeVisible();
  await expect(page.getByText('Nenhuma comissão para acompanhar.')).toBeVisible();
});

test('a user without any commission scope is redirected away from the Comissões route', async ({ page }) => {
  await login(page, 'operacoes', 'operacoes123');
  await page.goto('/comissoes');
  await expect(page).not.toHaveURL(/\/comissoes/);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
