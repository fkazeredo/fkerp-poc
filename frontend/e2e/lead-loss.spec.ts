import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

async function createLead(page: Page, name: string): Promise<void> {
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();
}

test('a lost lead leaves the default list and reappears under the Perdido filter', async ({
  page,
}) => {
  await login(page);
  const name = `E2E Lost ${Date.now()}`;
  await createLead(page, name);

  // Open the detail and mark it Lost with a reason.
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();

  await page.getByRole('button', { name: 'Marcar como perdido' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Sem resposta' }).click();
  const confirm = dialog.getByRole('button', { name: 'Marcar como perdido' });
  await expect(confirm).toBeEnabled();
  await confirm.click();
  await expect(page.getByText('Perda', { exact: true })).toBeVisible();

  // It is gone from the default operational list.
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('link', { name })).toHaveCount(0);

  // It reappears when the LOST status is explicitly filtered.
  await page.getByText('Exceto perdidos').click();
  await page.getByRole('option', { name: 'Perdido' }).click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('link', { name })).toBeVisible();
});
