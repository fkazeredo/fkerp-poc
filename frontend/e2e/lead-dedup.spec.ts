import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

async function fillLeadForm(page: Page, name: string, phone: string): Promise<void> {
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(phone);
}

test('blocks a second lead that reuses an open lead phone', async ({ page }) => {
  await login(page);
  // A unique 11-digit phone so this test never collides with other e2e leads.
  const phone = `1199${Date.now().toString().slice(-7)}`;

  // First lead is created normally.
  await fillLeadForm(page, `E2E Dedup A ${Date.now()}`, phone);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  // A second lead with the same phone is rejected with the friendly duplicate message.
  await fillLeadForm(page, `E2E Dedup B ${Date.now()}`, phone);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Já existe um lead ativo com este telefone ou e-mail')).toBeVisible();
});
