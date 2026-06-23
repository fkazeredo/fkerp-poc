import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 validation end-to-end (ALT 2 — customer rejection): a Sent Proposal is declined by the customer
 * with a reason → it becomes Rejeitada, the customer reason shows on the detail, NO Commercial Order can be
 * created, and the source Opportunity stays traceable and was never won (still Pronta p/ proposta). Drives a
 * Proposal all the way to Sent through the real flow (qualify a lead → opportunity funnel → proposal → item →
 * validity → submit → approve → send) and then registers the customer's rejection.
 */

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

/** Creates a Proposal and brings it all the way to Sent (item + validity + submit + approve + send). */
async function sentProposal(page: Page, name: string): Promise<void> {
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

  // Approve, then mark as sent (open the send dialog via the `m` shortcut to dodge the lingering toast).
  await page.getByRole('button', { name: 'Aprovar' }).click();
  await expect(page.getByText('Proposta aprovada')).toBeVisible();
  await expect(page.getByText('Aprovada').first()).toBeVisible();

  await page.keyboard.press('m');
  const sendDialog = page.getByRole('dialog');
  await sendDialog.getByText('Selecione').click();
  await page.getByRole('option', { name: 'E-mail' }).click();
  await sendDialog.getByRole('button', { name: 'Marcar como enviada' }).click();
  await expect(page.getByText('Proposta marcada como enviada')).toBeVisible();
  await expect(page.getByText('Enviada').first()).toBeVisible();
}

test('a sent proposal declined by the customer becomes Rejected, creates no order, and the opportunity stays traceable', async ({
  page,
}) => {
  test.slow();
  const name = `E2E Recusa Cliente ${Date.now()}`;
  await login(page);
  await sentProposal(page, name);

  // Register the customer's rejection via the `x` shortcut (keyboard avoids the lingering "Enviada" toast).
  await page.keyboard.press('x');
  const declineDialog = page.getByRole('dialog');
  await declineDialog.getByText('Selecione').click();
  await page.getByRole('option', { name: 'Escolheu concorrente' }).click();
  await declineDialog.locator('#dnote').fill('Cliente fechou com a concorrência');
  await declineDialog.getByRole('button', { name: 'Registrar recusa' }).click();
  await expect(page.getByText('Recusa registrada')).toBeVisible();

  // The Proposal is Rejeitada and the customer reason + note show on the detail.
  await expect(page.getByText('Rejeitada').first()).toBeVisible();
  await expect(page.getByText('Motivo da recusa do cliente')).toBeVisible();
  await expect(page.locator('.rejection')).toContainText('Escolheu concorrente');
  await expect(page.locator('.rejection')).toContainText('Cliente fechou com a concorrência');

  // No Commercial Order can be created, and none exists — neither action button is offered.
  await expect(page.getByRole('button', { name: 'Criar pedido comercial' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Ver pedido comercial' })).toHaveCount(0);

  // The source Opportunity remains traceable and was never won (still Pronta p/ proposta).
  await page.getByRole('link', { name: 'Ver oportunidade de origem' }).click();
  await expect(page).toHaveURL(/\/oportunidades\//);
  await expect(page.getByText('Pronta p/ proposta').first()).toBeVisible();
  await expect(page.getByText('Ganha')).toHaveCount(0);
});
