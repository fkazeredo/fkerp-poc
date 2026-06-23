import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 / Slice 7 end-to-end: internal rejection of a Proposal under review. A manager (an approver)
 * rejects a Ready-for-Review Proposal with a reason; it becomes Rejeitada, the reason shows on the detail,
 * and the transition is recorded in the status history. Drives a Proposal to Ready for Review through the
 * real flow (qualify a lead → opportunity → funnel → proposal → item → validity → submit) and then rejects.
 */

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

/** Creates a Proposal and brings it to Ready for Review (item + validity + submit), staying on the detail. */
async function proposalUnderReview(page: Page, name: string): Promise<void> {
  // Qualified lead assigned to the manager.
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

  // Opportunity advanced to Ready for Proposal.
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

  // Proposal from the ready Opportunity, with an item + validity, submitted for review.
  await page.getByRole('button', { name: 'Criar proposta' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Criar proposta' }).click();
  await expect(page.getByText('Proposta criada')).toBeVisible();
  await expect(page).toHaveURL(/\/propostas\//);

  await page.getByRole('button', { name: 'Adicionar item' }).click();
  const itemDialog = page.getByRole('dialog');
  // The item type is an explicit choice (waiting for the option also ensures the cadastro has loaded).
  await itemDialog.getByText('Selecione o tipo').click();
  await page.getByRole('option', { name: 'Pacote de viagem' }).click();
  await itemDialog.locator('#idesc').fill('Pacote de viagem');
  await itemDialog.locator('#iqty').fill('1');
  // Type the unit value as real keystrokes (PrimeNG inputNumber ignores Playwright's synthetic fill()).
  await itemDialog.locator('#iunit').pressSequentially('2000');
  await itemDialog.getByRole('button', { name: 'Adicionar' }).click();
  await expect(page.getByText('Item adicionado')).toBeVisible();

  await page.getByRole('button', { name: 'Editar dados comerciais' }).click();
  const detailsDialog = page.getByRole('dialog');
  const dvalid = detailsDialog.locator('#dvalid');
  await dvalid.click();
  await dvalid.pressSequentially('31/12/2026');
  await dvalid.press('Enter');
  await detailsDialog.getByRole('button', { name: 'Salvar' }).click();
  await expect(page.getByText('Dados atualizados')).toBeVisible();

  // Submit via the `s` shortcut: keyboard dodges the lingering success toast that overlaps the header button.
  await page.keyboard.press('s');
  await expect(page.getByText('Proposta enviada para revisão')).toBeVisible();
  await expect(page.getByText('Pronta para revisão').first()).toBeVisible();
}

test('a manager rejects a Proposal under review with a reason, recorded on the detail', async ({ page }) => {
  const name = `E2E Reject ${Date.now()}`;
  await login(page);
  await proposalUnderReview(page, name);

  // Reject with a reason + note. The `r` shortcut opens the dialog, dodging the lingering submit toast
  // that overlaps the header button.
  await page.keyboard.press('r');
  const reject = page.getByRole('dialog');
  await reject.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Preço muito alto' }).click();
  // Wait for the select overlay to fully close before clicking the confirm (its closing animation can
  // otherwise intercept the click).
  await expect(page.getByRole('option', { name: 'Preço muito alto' })).toHaveCount(0);
  await reject.locator('#rnote').fill('Acima do orçamento do cliente');
  await reject.getByRole('button', { name: 'Rejeitar proposta' }).click();
  await expect(page.getByText('Proposta rejeitada')).toBeVisible();

  // The Proposal is Rejeitada, the reason shows on the detail, and the transition is in the history.
  await expect(page.getByText('Rejeitada').first()).toBeVisible();
  await expect(page.getByText('Motivo da rejeição')).toBeVisible();
  // Scope to the detail's rejection line (the dialog's select label also reads the reason).
  await expect(page.locator('.rejection')).toContainText('Preço muito alto');
  await expect(page.locator('.rejection')).toContainText('Acima do orçamento do cliente');
  await expect(page.locator('.history')).toContainText('Rejeitada');

  // A Rejected Proposal offers no further actions: it cannot be approved/rejected again, marked sent, or
  // turned into a Commercial Order (the backend re-enforces this; the UI simply hides the header actions).
  // Exact names so the (closed) reject dialog's "Rejeitar proposta" button is not matched.
  await expect(page.getByRole('button', { name: 'Aprovar', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Rejeitar', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Marcar como enviada', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Criar pedido comercial', exact: true })).toHaveCount(0);
});
