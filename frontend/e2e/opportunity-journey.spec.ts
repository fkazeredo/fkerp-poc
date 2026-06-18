import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 2 coherent end-to-end journeys through the real UI: the Opportunity slices chained into the two
 * business flows (validation slice), complementing the per-operation specs.
 *
 * - Main: a Qualified Lead originates an Opportunity that is moved forward through the strict funnel
 *   (Descoberta → Aderência → Pronta p/ proposta), with commercial activities and a commercial-details
 *   edit along the way; it ends ready for a Sprint 3 Proposal, and no Proposal/Sale UI exists.
 * - Lost: an Opportunity is worked, found to have no fit and marked Lost (a reason is mandatory); it
 *   leaves the default list, is found again via the Perdida filter, and the source Lead stays reachable.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

/** Creates a Lead assigned to the manager, logs an effective contact and qualifies it (→ QUALIFIED). */
async function qualifiedLead(page: Page, name: string): Promise<void> {
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
}

/** Creates the Opportunity from the (open) qualified Lead detail. */
async function createOpportunity(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Criar oportunidade' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('#optype').fill('Software corporativo');
  await dialog.locator('#opvalue').fill('15000');
  await dialog.getByRole('button', { name: 'Criar oportunidade' }).click();
  await expect(page.getByText('Oportunidade criada')).toBeVisible();
}

/** Opens the Opportunity detail from the operational list (the title links to it). */
async function openOpportunity(page: Page, name: string): Promise<void> {
  await page.goto('/oportunidades');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

/** Advances the open Opportunity one step (the dialog pre-selects the only forward stage). */
async function advanceStage(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Avançar estágio' }).click();
  const dialog = page.getByRole('dialog');
  // The dialog confirm is "Avançar" (scoped to the dialog, so it never matches the "Avançar estágio"
  // header button); its accessible name carries a leading space from the icon, so do not match exactly.
  await dialog.getByRole('button', { name: 'Avançar' }).click();
  await expect(page.getByText('Estágio atualizado')).toBeVisible();
}

/** Registers a commercial activity (the date pre-fills to now). */
async function registerActivity(page: Page, type: string, result: string, description: string): Promise<void> {
  await page.getByRole('button', { name: 'Registrar atividade' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: type, exact: true }).click();
  await dialog.getByText('Selecione o resultado').click();
  await page.getByRole('option', { name: result, exact: true }).click();
  await dialog.locator('#adesc').fill(description);
  await dialog.getByRole('button', { name: 'Registrar' }).click();
  await expect(page.getByText('Atividade registrada')).toBeVisible();
}

test('main journey: qualified lead → opportunity → funnel → ready for proposal', async ({ page }) => {
  const name = `E2E Opp Journey ${Date.now()}`;

  await login(page, 'comercial', 'comercial123');
  await qualifiedLead(page, name);
  await createOpportunity(page);
  await openOpportunity(page, name);

  // New → Discovery, a commercial activity, and a commercial-details edit.
  await advanceStage(page);
  await registerActivity(page, 'Reunião', 'Cliente engajado', 'Reunião de descoberta');

  await page.getByRole('button', { name: 'Editar dados comerciais' }).click();
  const edit = page.getByRole('dialog');
  await edit.locator('#evalue').fill('25000');
  await edit.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText('Dados comerciais atualizados')).toBeVisible();

  // Discovery → Product Fit (fit identified) → Ready for Proposal.
  await advanceStage(page);
  await registerActivity(page, 'Reunião', 'Aderência identificada', 'Aderência confirmada');
  await advanceStage(page);

  // It is ready for a Proposal — there is no further pipeline advance, and (Sprint 3) it now offers the
  // "Criar proposta" action.
  await expect(page.getByText('Pronta p/ proposta').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Avançar estágio' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Criar proposta' })).toBeVisible();
});

test('lost journey: opportunity marked lost requires a reason, leaves the default list, stays traceable', async ({
  page,
}) => {
  const name = `E2E Opp Lost ${Date.now()}`;

  await login(page, 'comercial', 'comercial123');
  await qualifiedLead(page, name);
  await createOpportunity(page);
  await openOpportunity(page, name);

  await registerActivity(page, 'Ligação', 'Sem interesse', 'Cliente sem fit');

  // Marking lost requires a reason — the confirm stays disabled until one is chosen.
  await page.getByRole('button', { name: 'Marcar como perdida' }).click();
  const lose = page.getByRole('dialog');
  const confirm = lose.getByRole('button', { name: 'Marcar como perdida' });
  await expect(confirm).toBeDisabled();
  await lose.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Sem orçamento' }).click();
  await expect(confirm).toBeEnabled();
  await confirm.click();
  await expect(page.getByText('Oportunidade marcada como perdida')).toBeVisible();
  await expect(page.getByText('Perdida').first()).toBeVisible();

  // It leaves the default operational list…
  await page.goto('/oportunidades');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('link', { name })).toHaveCount(0);

  // …but is found again through the explicit Perdida filter (historically accessible).
  await page.getByText('Exceto perdidas').click();
  await page.getByRole('option', { name: 'Perdida' }).click();
  await page.keyboard.press('Escape');
  await page.locator('#q').fill(name);
  await expect(page.getByRole('link', { name })).toBeVisible();

  // The source Lead remains reachable from the Opportunity detail.
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  await page.getByRole('link', { name: 'Ver lead de origem' }).click();
  await expect(page).toHaveURL(/\/leads\//);
});
