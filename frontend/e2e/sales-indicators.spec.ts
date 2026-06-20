import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 / Slice 12 end-to-end: the minimum sales indicators (Proposals and Commercial Orders) are
 * reachable from the Vendas module and render their KPI cards and breakdowns. The numbers' data-driven
 * behaviour (period vs snapshot, per-profile visibility, the contract fields) is covered thoroughly by
 * ProposalIndicatorsApiIntegrationTest / OrderIndicatorsApiIntegrationTest; this E2E covers the
 * cross-cutting concern — the navigation entries, the routes and the page render.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the proposal indicators page is reachable from Vendas and renders its KPI cards', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');

  // Open the Vendas module from the sidebar (which expands the accordion), then its proposal indicators.
  await page.locator('.sidebar').getByRole('link', { name: 'Vendas' }).click();
  await expect(page).toHaveURL(/\/vendas$/);
  await page.locator('.sidebar').getByRole('link', { name: 'Indicadores de propostas' }).click();
  await expect(page).toHaveURL(/\/propostas\/indicadores$/);

  await expect(page.getByRole('heading', { name: 'Indicadores de propostas' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Volume no período' })).toBeVisible();
  await expect(page.getByText('Total no período')).toBeVisible();
  await expect(page.getByText('Valor proposto')).toBeVisible();
  await expect(page.getByText('Aguardando revisão')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por status' })).toBeVisible();
});

test('the order indicators page is reachable from Vendas and renders its KPI cards', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');

  await page.locator('.sidebar').getByRole('link', { name: 'Vendas' }).click();
  await expect(page).toHaveURL(/\/vendas$/);
  await page.locator('.sidebar').getByRole('link', { name: 'Indicadores de pedidos' }).click();
  await expect(page).toHaveURL(/\/pedidos\/indicadores$/);

  await expect(page.getByRole('heading', { name: 'Indicadores de pedidos' })).toBeVisible();
  await expect(page.getByText('Total no período')).toBeVisible();
  await expect(page.getByText('Valor total')).toBeVisible();
  await expect(page.getByText('Pendentes de reserva')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por responsável' })).toBeVisible();
});
