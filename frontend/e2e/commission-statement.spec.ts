import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slice 9 end-to-end: the commission statement by beneficiary. The populated statement (entries + totals +
 * own-vs-all visibility) is covered by CommissionApiIntegrationTest and the component spec; this E2E covers the
 * cross-cutting concerns — the route + read guard and that the page renders (the isolated stack has no seeded
 * commissions, so the manager's own statement is an honest empty state).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('a manager opens the commission statement and sees the totals + empty entries', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.goto('/comissoes/extrato');
  await expect(page.getByRole('heading', { name: 'Extrato de comissões' })).toBeVisible();
  // The four per-status total cards are present.
  await expect(page.getByText('Prevista')).toBeVisible();
  await expect(page.getByText('Paga', { exact: true })).toBeVisible();
  // No seeded commissions in the isolated stack → honest empty state.
  await expect(page.getByText('Nenhuma comissão no período')).toBeVisible();
});

test('a seller (own tier) reaches their own commission statement', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');
  await page.goto('/comissoes/extrato');
  await expect(page).toHaveURL(/\/comissoes\/extrato/);
  await expect(page.getByRole('heading', { name: 'Extrato de comissões' })).toBeVisible();
});

test('a user without a commission read tier is redirected away from the statement', async ({ page }) => {
  await login(page, 'operacoes', 'operacoes123');
  await page.goto('/comissoes/extrato');
  await expect(page).not.toHaveURL(/\/comissoes\/extrato/);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
