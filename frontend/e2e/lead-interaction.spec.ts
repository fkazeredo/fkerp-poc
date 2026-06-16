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

async function openDetail(page: Page, name: string): Promise<void> {
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

async function registerInteraction(page: Page, result: string, description: string): Promise<void> {
  await page.getByRole('button', { name: 'Registrar interação' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'Ligação' }).click();
  await dialog.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: result, exact: true }).click();
  await dialog.locator('#idesc').fill(description);
  const confirm = dialog.getByRole('button', { name: 'Registrar' });
  await expect(confirm).toBeEnabled();
  await confirm.click();
  await expect(page.getByText('Interação registrada')).toBeVisible();
}

test('an effective contact moves a new lead to Contacted and shows in history', async ({
  page,
}) => {
  await login(page);
  const name = `E2E Contacted ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);
  await expect(page.getByText('Novo', { exact: true })).toBeVisible();

  await registerInteraction(page, 'Contato realizado', 'Cliente atendeu e quer proposta');

  await expect(page.getByText('Em contato', { exact: true })).toBeVisible();
  await expect(page.getByText('Cliente atendeu e quer proposta')).toBeVisible();
});

test('a no-answer attempt keeps the lead New but records the history', async ({ page }) => {
  await login(page);
  const name = `E2E NoAnswer ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);

  await registerInteraction(page, 'Não atendeu', 'Ligação caiu na caixa postal');

  await expect(page.getByText('Novo', { exact: true })).toBeVisible();
  await expect(page.getByText('Ligação caiu na caixa postal')).toBeVisible();
});
