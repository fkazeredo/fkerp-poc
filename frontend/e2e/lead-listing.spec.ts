import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

test('the lead list loads with its columns', async ({ page }) => {
  await login(page);
  await page.goto('/leads');
  await expect(page.getByRole('heading', { name: 'Leads' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Responsável' })).toBeVisible();
});

test('creates an assigned lead and finds it via search on the list', async ({ page }) => {
  await login(page);
  const name = `E2E List ${Date.now()}`;

  // Create a lead with a responsible person.
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill('11999991234');
  await page.getByText('Sem responsável').click();
  await page.getByRole('option', { name: 'comercial' }).click();
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  // Find it on the operational list via search.
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('cell', { name })).toBeVisible({ timeout: 7000 });
});
