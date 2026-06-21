import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the indicators page shows the top-of-funnel after a lead is created', async ({ page }) => {
  await login(page);

  // Create a lead so the month-to-date funnel has at least one entry.
  const name = `E2E Ind ${Date.now()}`;
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  // Open the Indicadores hub from the Acompanhamento module. Only the active module's sub-menu is shown
  // (accordion), and after creating a lead we are on the system home — so open the Acompanhamento module
  // first (deterministically expands it), then its Indicadores hub. The hub defaults to the first visible
  // tab (Leads for the manager), which renders the top-of-funnel lead indicators.
  await page.locator('.sidebar').getByRole('link', { name: 'Acompanhamento' }).click();
  await expect(page).toHaveURL(/\/acompanhamento$/);
  await page.locator('.sidebar a[href="/indicadores"]').click();
  await expect(page).toHaveURL(/\/indicadores$/);
  await expect(page.getByRole('heading', { name: 'Indicadores', exact: true })).toBeVisible();

  // The KPI cards render with numbers; Total is at least 1 (the lead we just created).
  const total = page.locator('.kpi', { hasText: 'Total' });
  await expect(total.locator('.kpi-value')).toHaveText(/\d+/);
  await expect(page.getByText('Novos', { exact: true })).toBeVisible();
  await expect(page.getByText('Perdidos', { exact: true })).toBeVisible();
  await expect(page.getByText('Aguardando 1º contato')).toBeVisible();

  // The breakdown sections are present.
  await expect(page.getByRole('heading', { name: 'Por origem' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por responsável' })).toBeVisible();
});
