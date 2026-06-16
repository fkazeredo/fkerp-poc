import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 1 coherent end-to-end journeys through the real UI, across the real actors. These chain the
 * individual slices into the two business flows (validation slice), complementing the per-operation
 * specs.
 */

/** Signs in as the given user (reloads the SPA, so it also switches the active user mid-journey). */
async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Comercial / CRM' })).toBeVisible();
}

interface NewLead {
  name: string;
  origin: string;
  phone: string;
  responsible?: string;
}

async function createLead(page: Page, lead: NewLead): Promise<void> {
  await page.goto('/leads/new');
  await page.locator('#name').fill(lead.name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option', { name: lead.origin }).click();
  await page.locator('#phone').fill(lead.phone);
  if (lead.responsible) {
    await page.getByText('Sem responsável').click();
    await page.getByRole('option', { name: lead.responsible }).click();
  }
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();
}

async function openDetail(page: Page, name: string): Promise<void> {
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

test('main journey: Instagram unassigned → manager assigns → seller WhatsApp contact → qualified', async ({
  page,
}) => {
  const name = `E2E Journey Main ${Date.now()}`;

  // The manager registers an Instagram lead with no responsible.
  await login(page, 'comercial', 'comercial123');
  await createLead(page, {
    name,
    origin: 'Instagram',
    phone: `119${Date.now()}${Math.floor(Math.random() * 1000)}`,
  });

  // The manager finds it as a pending, unassigned lead.
  await page.locator('.sidebar').getByRole('link', { name: 'Pendências' }).click();
  await expect(page.getByRole('heading', { name: 'Pendências' })).toBeVisible();
  await expect(page.getByRole('link', { name })).toBeVisible();

  // The manager assigns it to the seller.
  await openDetail(page, name);
  await page.getByRole('button', { name: 'Reatribuir' }).click();
  const assign = page.getByRole('dialog');
  await assign.getByText('Sem responsável').click();
  await page.getByRole('option', { name: 'vendedor' }).click();
  await assign.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText('Responsável atualizado')).toBeVisible();
  await expect(page.locator('dt:has-text("Responsável") + dd')).toHaveText('vendedor');

  // The seller signs in, opens the lead — still NEW, so it cannot be qualified yet.
  await login(page, 'vendedor', 'vendedor123');
  await openDetail(page, name);
  await expect(page.getByRole('button', { name: 'Qualificar' })).toHaveCount(0);

  // The seller logs a WhatsApp effective contact → CONTACTED (the Qualificar action appears).
  await page.getByRole('button', { name: 'Registrar interação' }).click();
  const interaction = page.getByRole('dialog');
  await interaction.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'WhatsApp' }).click();
  await interaction.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: 'Contato realizado', exact: true }).click();
  await interaction.locator('#idesc').fill('Cliente interessado via WhatsApp');
  await interaction.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByText('Interação registrada')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Qualificar' })).toBeVisible();

  // The seller records the commercial interest and qualifies → QUALIFIED.
  await page.getByRole('button', { name: 'Qualificar' }).click();
  const qualify = page.getByRole('dialog');
  await qualify.locator('#qinterest').fill('Plano corporativo');
  await qualify.getByRole('button', { name: 'Qualificar' }).click();
  await expect(page.getByText('Qualificação')).toBeVisible();
  await expect(page.getByText('Plano corporativo')).toBeVisible();
});

test('lost journey: referral → responsible no-interest → Lost → manager finds via Perdido filter', async ({
  page,
}) => {
  const name = `E2E Journey Lost ${Date.now()}`;

  // The representative registers a referral lead they own.
  await login(page, 'representante', 'representante123');
  await createLead(page, {
    name,
    origin: 'Indicação',
    phone: `119${Date.now()}${Math.floor(Math.random() * 1000)}`,
    responsible: 'representante',
  });

  // They attempt contact and the lead shows no interest.
  await openDetail(page, name);
  await page.getByRole('button', { name: 'Registrar interação' }).click();
  const interaction = page.getByRole('dialog');
  await interaction.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'WhatsApp' }).click();
  await interaction.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: 'Não interessado' }).click();
  await interaction.locator('#idesc').fill('Sem interesse no momento');
  await interaction.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByText('Interação registrada')).toBeVisible();

  // They mark it Lost — a reason is required.
  await page.getByRole('button', { name: 'Marcar como perdido' }).click();
  const lose = page.getByRole('dialog');
  await lose.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Sem interesse' }).click();
  const confirm = lose.getByRole('button', { name: 'Marcar como perdido' });
  await expect(confirm).toBeEnabled();
  await confirm.click();
  await expect(page.getByText('Perda', { exact: true })).toBeVisible();

  // The manager signs in: the Lost lead is gone from the default operational list...
  await login(page, 'comercial', 'comercial123');
  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('link', { name })).toHaveCount(0);

  // ...but is still found through the explicit Perdido filter (historically accessible).
  await page.getByText('Exceto perdidos').click();
  await page.getByRole('option', { name: 'Perdido' }).click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('link', { name })).toBeVisible();
});
