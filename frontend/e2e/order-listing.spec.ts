import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 / Slice 11 end-to-end: the operational Commercial Order list is reachable from the Vendas module
 * and renders its operational columns. The list's data-driven behaviour (per-profile visibility, the filters
 * and the booking-need indicator) is covered thoroughly by CommercialOrderListingApiIntegrationTest; this E2E
 * covers the cross-cutting concern — the navigation entry, the route and the page render.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the order list is reachable from the Vendas module and shows its operational columns', async ({
  page,
}) => {
  await login(page, 'comercial', 'comercial123');

  // Open the Vendas module from the sidebar (which expands the accordion), then its Pedidos destination.
  await page.locator('.sidebar').getByRole('link', { name: 'Vendas' }).click();
  await expect(page).toHaveURL(/\/vendas$/);
  await page.locator('.sidebar').getByRole('link', { name: 'Pedidos' }).click();
  await expect(page).toHaveURL(/\/pedidos$/);

  await expect(page.getByRole('heading', { name: 'Pedidos' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Identificador' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Resumo' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Reserva' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Total' })).toBeVisible();
  // The filter bar (status, booking need, search) is present.
  await expect(page.locator('.filters')).toContainText('Status');
  await expect(page.locator('.filters #q')).toBeVisible();
});

test('the "g d" shortcut navigates to the order list', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.keyboard.press('g');
  await page.keyboard.press('d');
  await expect(page).toHaveURL(/\/pedidos$/);
  await expect(page.getByRole('heading', { name: 'Pedidos' })).toBeVisible();
});
