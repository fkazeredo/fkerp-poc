import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 4 / Slice 4 end-to-end: the Booking Request detail route is reachable and guarded. The data-driven
 * detail (summary, source Order/Proposal/Opportunity, item statuses, traceability, the read tiers) is covered
 * thoroughly by BookingRequestDetailApiIntegrationTest against a seeded Postgres; this E2E covers the
 * cross-cutting concern — the route, the read guard and the not-found rendering (the isolated e2e stack has no
 * seeded reservations, so a real id is not available to click into).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('an authorized operations user reaches the reservation detail route (not-found render for an unknown id)', async ({
  page,
}) => {
  await login(page, 'operacoes', 'operacoes123');

  // The route and the read guard let the operations user in; an unknown id renders the not-found state.
  await page.goto('/reservas/00000000-0000-0000-0000-0000000000ff');
  await expect(page).toHaveURL(/\/reservas\//);
  await expect(page.getByText('Reserva não encontrada.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
});

test('a seller without a booking read tier cannot reach the reservation detail', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');

  // The read guard blocks the detail route and redirects back to the system home.
  await page.goto('/reservas/00000000-0000-0000-0000-0000000000ff');
  await expect(page).not.toHaveURL(/\/reservas\//);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
