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
  await page.locator('#phone').fill('11999992222');
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();
}

async function openDetail(page: Page, name: string): Promise<void> {
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

test('opens a lead detail from the list', async ({ page }) => {
  await login(page);
  const name = `E2E Detail ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);
  await expect(page.getByText('Dados')).toBeVisible();
  await expect(page.getByText('Histórico de interações')).toBeVisible();
});

test('qualifies a lead from its detail', async ({ page }) => {
  await login(page);
  const name = `E2E Qualify ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);

  await page.getByRole('button', { name: 'Qualificar' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Qualificar' }).click();

  await expect(page.getByText('Qualificação')).toBeVisible();
});

test('marks a lead as lost with a reason', async ({ page }) => {
  await login(page);
  const name = `E2E Lose ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);

  await page.getByRole('button', { name: 'Marcar como perdido' }).click();
  await page.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Sem resposta' }).click();
  const confirm = page.getByRole('dialog').getByRole('button', { name: 'Marcar como perdido' });
  await expect(confirm).toBeEnabled();
  await confirm.click();

  await expect(page.getByText('Perda', { exact: true })).toBeVisible();
});
