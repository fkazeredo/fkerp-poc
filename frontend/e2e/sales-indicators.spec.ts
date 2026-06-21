import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 / Slice 12 end-to-end (updated Sprint 4 / Slice 3): the minimum sales indicators (Proposals and
 * Commercial Orders) are now reachable through the single Indicadores hub (in the Acompanhamento module),
 * selecting the area tab. The numbers' data-driven behaviour (period vs snapshot, per-profile visibility, the
 * contract fields) is covered thoroughly by ProposalIndicatorsApiIntegrationTest /
 * OrderIndicatorsApiIntegrationTest; this E2E covers the cross-cutting concern — the navigation entry, the hub
 * route, the tab switch and the page render.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

async function openIndicadoresHub(page: Page): Promise<void> {
  // Open the Acompanhamento module from the sidebar (expands the accordion), then the Indicadores hub.
  await page.locator('.sidebar').getByRole('link', { name: 'Acompanhamento' }).click();
  await expect(page).toHaveURL(/\/acompanhamento$/);
  await page.locator('.sidebar').getByRole('link', { name: 'Indicadores' }).click();
  await expect(page).toHaveURL(/\/indicadores$/);
}

test('the proposal indicators are reachable through the Indicadores hub and render their KPI cards', async ({
  page,
}) => {
  await login(page, 'comercial', 'comercial123');
  await openIndicadoresHub(page);

  // Switch to the Propostas tab inside the hub.
  await page.getByRole('tab', { name: 'Propostas' }).click();

  await expect(page.getByRole('heading', { name: 'Indicadores de propostas' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Volume no período' })).toBeVisible();
  await expect(page.getByText('Total no período')).toBeVisible();
  await expect(page.getByText('Valor proposto')).toBeVisible();
  await expect(page.getByText('Aguardando revisão')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por status' })).toBeVisible();
});

test('the order indicators are reachable through the Indicadores hub and render their KPI cards', async ({
  page,
}) => {
  await login(page, 'comercial', 'comercial123');
  await openIndicadoresHub(page);

  // Switch to the Pedidos tab inside the hub.
  await page.getByRole('tab', { name: 'Pedidos' }).click();

  await expect(page.getByRole('heading', { name: 'Indicadores de pedidos' })).toBeVisible();
  await expect(page.getByText('Total no período')).toBeVisible();
  await expect(page.getByText('Valor total')).toBeVisible();
  await expect(page.getByText('Pendentes de reserva')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por responsável' })).toBeVisible();
});
