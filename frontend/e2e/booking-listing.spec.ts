import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 4 / Slice 3 end-to-end: the operational Booking Request list is reachable from the Reservas module
 * (a separate operations module in the reorganized navigation) and renders its operational columns and
 * filters. The list's data-driven behaviour (read tiers, the default status set, the filters and the item
 * counts) is covered thoroughly by BookingRequestListApiIntegrationTest; this E2E covers the cross-cutting
 * concern — the new navigation entry, the route, the guard and the page render. The operations user (006)
 * holds the booking read tier.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the reservation list is reachable from the Reservas module and shows its operational columns', async ({
  page,
}) => {
  await login(page, 'operacoes', 'operacoes123');

  // The Reservas module sits on its own in the sidebar; its home is the list itself.
  await page.locator('.sidebar').getByRole('link', { name: 'Reservas', exact: true }).click();
  await expect(page).toHaveURL(/\/reservas$/);

  await expect(page.getByRole('heading', { name: 'Reservas' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Pedido' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Itens p/ reservar' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Confirmados' })).toBeVisible();
  // The filter bar (status, operator, item type, has-failed) is present.
  await expect(page.locator('.filters')).toContainText('Status');
  await expect(page.locator('.filters')).toContainText('Operador');
  await expect(page.locator('.filters')).toContainText('Com falhas');
});

test('the "g r" shortcut navigates to the reservation list', async ({ page }) => {
  await login(page, 'operacoes', 'operacoes123');
  await page.keyboard.press('g');
  await page.keyboard.press('r');
  await expect(page).toHaveURL(/\/reservas$/);
  await expect(page.getByRole('heading', { name: 'Reservas' })).toBeVisible();
});

test('a seller without a booking read tier cannot reach the reservation list', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');

  // No Reservas module in the sidebar (no booking read scope).
  await expect(page.locator('.sidebar').getByRole('link', { name: 'Reservas', exact: true })).toHaveCount(0);

  // Navigating to /reservas is blocked by the guard (redirected back to the system home).
  await page.goto('/reservas');
  await expect(page).not.toHaveURL(/\/reservas$/);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
