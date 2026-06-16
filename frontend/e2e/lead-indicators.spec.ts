import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

test('the indicators page shows the top-of-funnel after a lead is created', async ({ page }) => {
  await login(page);

  // Create a lead so the month-to-date funnel has at least one entry.
  const name = `E2E Ind ${Date.now()}`;
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill('11999997777');
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  // Open Indicadores from the sidebar.
  await page.locator('.sidebar').getByRole('link', { name: 'Indicadores' }).click();
  await expect(page.getByRole('heading', { name: 'Indicadores' })).toBeVisible();

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
