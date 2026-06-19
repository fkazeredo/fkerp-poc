import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 / Slice 4 end-to-end: the operational Proposal list. The list shows the operational columns,
 * filters by status, and respects commercial ownership (a representative sees only their own Proposals).
 * A DRAFT Proposal is created through the real flow (qualify a lead → opportunity → funnel → proposal).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

/** Creates a DRAFT Proposal owned by the logged-in manager (comercial), via the real funnel flow. */
async function createDraftProposal(page: Page, name: string): Promise<void> {
  // Lead assigned to the manager, contacted and qualified.
  await page.goto('/leads/new');
  await page.locator('#name').fill(name);
  await page.getByText('Selecione a origem').click();
  await page.getByRole('option').first().click();
  await page.locator('#phone').fill(`119${Date.now()}${Math.floor(Math.random() * 1000)}`);
  await page.getByText('Sem responsável').click();
  await page.getByRole('option', { name: 'comercial' }).click();
  await page.getByRole('button', { name: 'Salvar lead' }).click();
  await expect(page.getByText('Lead criado')).toBeVisible();

  await page.goto('/leads');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();

  await page.getByRole('button', { name: 'Registrar interação' }).click();
  const interaction = page.getByRole('dialog');
  await interaction.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'Ligação' }).click();
  await interaction.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: 'Contato realizado', exact: true }).click();
  await interaction.locator('#idesc').fill('Cliente interessado');
  await interaction.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByText('Interação registrada')).toBeVisible();

  await page.getByRole('button', { name: 'Qualificar' }).click();
  const qualify = page.getByRole('dialog');
  await qualify.locator('#qinterest').fill('Pacote corporativo');
  await qualify.getByRole('button', { name: 'Qualificar' }).click();
  await expect(page.getByText('Qualificação')).toBeVisible();

  // Opportunity from the qualified lead, advanced through the funnel to Ready for Proposal.
  await page.getByRole('button', { name: 'Criar oportunidade' }).click();
  const opp = page.getByRole('dialog');
  await opp.locator('#optype').fill('Software corporativo');
  await opp.locator('#opvalue').fill('15000');
  await opp.getByRole('button', { name: 'Criar oportunidade' }).click();
  await expect(page.getByText('Oportunidade criada')).toBeVisible();

  await page.goto('/oportunidades');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  for (const stage of ['Descoberta', 'Aderência', 'Pronta p/ proposta']) {
    await page.getByRole('button', { name: 'Avançar estágio' }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByRole('button', { name: 'Avançar' }).click();
    await expect(page.getByText(stage).first()).toBeVisible();
  }

  // Create the Proposal from the ready Opportunity (title pre-fills from the opportunity name).
  await page.getByRole('button', { name: 'Criar proposta' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.locator('#ptitle')).toHaveValue(name);
  await dialog.getByRole('button', { name: 'Criar proposta' }).click();
  await expect(page.getByText('Proposta criada')).toBeVisible();
  await expect(page).toHaveURL(/\/propostas\//);
}

test('the proposal list loads with its operational columns', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.goto('/propostas');
  await expect(page.getByRole('heading', { name: 'Propostas' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Oportunidade' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Total' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Atualizada em' })).toBeVisible();
});

test('a created proposal is listed, filterable by status, and respects representative visibility', async ({
  page,
}) => {
  const name = `E2E PropList ${Date.now()}`;

  await login(page, 'comercial', 'comercial123');
  await createDraftProposal(page, name);

  // It shows on the operational list (a Draft, so it is part of the default active view).
  await page.goto('/propostas');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('link', { name }).first()).toBeVisible({ timeout: 7000 });

  // Filtering by an inactive status (Cancelada) hides the active Draft — proving the status filter narrows.
  // The Status multiselect shows its "Ativas" placeholder until a status is chosen.
  await page.getByText('Ativas', { exact: true }).click();
  await page.getByRole('option', { name: 'Cancelada' }).click();
  await page.keyboard.press('Escape'); // close the multiselect panel
  await expect(page.getByRole('link', { name })).toHaveCount(0);

  // Commercial ownership: a representative does not see the manager's Proposal.
  await login(page, 'representante', 'representante123');
  await page.goto('/propostas');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('heading', { name: 'Propostas' })).toBeVisible();
  await expect(page.getByRole('link', { name })).toHaveCount(0);
});
