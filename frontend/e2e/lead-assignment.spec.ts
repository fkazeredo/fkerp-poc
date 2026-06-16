import { test, expect, Page } from '@playwright/test';

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

/** Creates an unassigned lead (no responsible selected) and returns its name. */
async function createUnassignedLead(page: Page, name: string): Promise<void> {
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

test('a manager reassigns an unassigned lead to another user', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  const name = `E2E Assign Mgr ${Date.now()}`;
  await createUnassignedLead(page, name);
  await openDetail(page, name);

  // A manager has full assignment authority: the Reatribuir action is present, Assumir is not.
  await expect(page.getByRole('button', { name: 'Reatribuir' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Assumir' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Reatribuir' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('Sem responsável').click();
  await page.getByRole('option', { name: 'vendedor' }).click();
  await dialog.getByRole('button', { name: 'Salvar' }).click();

  await expect(page.getByText('Responsável atualizado')).toBeVisible();
  await expect(page.locator('dt:has-text("Responsável") + dd')).toHaveText('vendedor');
});

test('a sales rep self-claims an unassigned lead and cannot reassign', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');
  const name = `E2E Assign Rep ${Date.now()}`;
  await createUnassignedLead(page, name);
  await openDetail(page, name);

  // A rep lacks crm:lead:assign: no Reatribuir, but may claim the unassigned lead for themselves.
  await expect(page.getByRole('button', { name: 'Reatribuir' })).toHaveCount(0);
  const claim = page.getByRole('button', { name: 'Assumir' });
  await expect(claim).toBeVisible();

  await claim.click();

  await expect(page.getByText('Lead atribuído a você')).toBeVisible();
  await expect(page.locator('dt:has-text("Responsável") + dd')).toHaveText('vendedor');
  // Once claimed the lead is no longer unassigned, so the claim action disappears.
  await expect(page.getByRole('button', { name: 'Assumir' })).toHaveCount(0);
});
