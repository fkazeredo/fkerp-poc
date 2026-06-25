import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slices 5–6 end-to-end: the Commission detail consultation + approve action. The populated detail and the
 * approve flow (eligible→approved, self-approval block, not-eligible, own-vs-all visibility) are covered thoroughly by
 * CommissionApiIntegrationTest and the CommissionDetailPage component spec; this E2E covers the cross-cutting concerns
 * — the route + read guard, the not-found render and the help-overlay shortcut wiring (the isolated e2e stack has no
 * seeded commissions, so the populated approve flow is exercised in the integration test).
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

test('the help overlay documents the approve/reject/cancel-commission shortcuts', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.keyboard.press('?');
  const help = page.getByRole('dialog', { name: 'Atalhos do teclado' });
  await expect(help).toBeVisible();
  await expect(help.getByText('No detalhe de uma comissão')).toBeVisible();
  const line = help.locator('li', { hasText: 'Aprovar comissão' });
  await expect(line).toContainText('Rejeitar');
  await expect(line).toContainText('Cancelar');
});
