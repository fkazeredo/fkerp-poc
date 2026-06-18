import { test, expect, Page } from '@playwright/test';

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
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

test('opens a lead detail from the list', async ({ page }) => {
  await login(page);
  const name = `E2E Detail ${Date.now()}`;
  await createLead(page, name);
  await openDetail(page, name);
  await expect(page.getByText('Dados')).toBeVisible();
  await expect(page.getByText('Histórico de interações')).toBeVisible();
});

test('qualifies a contacted, assigned lead with a main interest', async ({ page }) => {
  await login(page);
  const name = `E2E Qualify ${Date.now()}`;

  // Qualification requires a responsible and an effective contact first.
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByText('Sem responsável').click();
  await page.getByRole('option', { name: 'comercial' }).click();
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  await openDetail(page, name);

  // A NEW lead cannot be qualified yet.
  await expect(page.getByRole('button', { name: 'Qualificar' })).toHaveCount(0);

  // Log an effective contact → CONTACTED.
  await page.getByRole('button', { name: 'Registrar interação' }).click();
  const interaction = page.getByRole('dialog');
  await interaction.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'Ligação' }).click();
  await interaction.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: 'Contato realizado', exact: true }).click();
  await interaction.locator('#idesc').fill('Cliente interessado');
  await interaction.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByText('Interação registrada')).toBeVisible();

  // Now qualify with a main interest.
  await page.getByRole('button', { name: 'Qualificar' }).click();
  const qualify = page.getByRole('dialog');
  await qualify.locator('#qinterest').fill('Pacote corporativo');
  await qualify.getByRole('button', { name: 'Qualificar' }).click();

  await expect(page.getByText('Qualificação')).toBeVisible();
  await expect(page.getByText('Pacote corporativo')).toBeVisible();
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
