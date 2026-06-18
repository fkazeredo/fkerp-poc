import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('a new unassigned lead shows up in the pending worklist with its reasons', async ({
  page,
}) => {
  await login(page);
  const name = `E2E Pend ${Date.now()}`;

  // Create an unassigned lead with no initial note → unassigned + new-without-interaction.
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  // Open Pendências from the sidebar (newest first, so the lead is on page 1). The accordion shows only
  // the active module's sub-menu, so open the Comercial / CRM module first.
  await page.locator('.sidebar').getByRole('link', { name: 'Comercial / CRM' }).click();
  await expect(page).toHaveURL(/\/crm$/);
  await page.locator('.sidebar').getByRole('link', { name: 'Pendências' }).click();
  await expect(page.getByRole('heading', { name: 'Pendências' })).toBeVisible();

  await expect(page.getByRole('link', { name })).toBeVisible();
  await expect(page.getByText('Sem interação').first()).toBeVisible();

  // The name links to the lead detail.
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
});
