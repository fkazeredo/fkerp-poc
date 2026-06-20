import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 3 end-to-end (Slices 1–10): a READY_FOR_PROPOSAL Opportunity originates a commercial Proposal
 * through the real UI, the new **Vendas** module (Propostas) is reachable in the menu, and the Proposal is
 * driven through its full lifecycle — items, commercial details, submit for review, approve, mark as sent to
 * the client, register the client's acceptance, and finally create the Commercial Order (which wins the source
 * Opportunity). Drives the Opportunity to "Pronta p/ proposta" (reusing the funnel flow) and then creates and
 * progresses the Proposal.
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

async function createReadyOpportunity(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: 'Criar oportunidade' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('#optype').fill('Software corporativo');
  await dialog.locator('#opvalue').fill('15000');
  await dialog.getByRole('button', { name: 'Criar oportunidade' }).click();
  await expect(page.getByText('Oportunidade criada')).toBeVisible();

  // Open it and advance through the funnel to Ready for Proposal.
  await page.goto('/oportunidades');
  await page.locator('#q').fill(name);
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  // Advance New → Descoberta → Aderência → Pronta p/ proposta, asserting the new stage tag each time
  // (back-to-back identical success toasts would stack, so assert the deterministic stage label instead).
  for (const stage of ['Descoberta', 'Aderência', 'Pronta p/ proposta']) {
    await page.getByRole('button', { name: 'Avançar estágio' }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByRole('button', { name: 'Avançar' }).click();
    await expect(page.getByText(stage).first()).toBeVisible();
  }
}

test('a ready opportunity originates a commercial proposal, reachable in the Vendas module', async ({
  page,
}) => {
  test.slow(); // a long end-to-end journey: qualify → opportunity funnel → proposal → items → submit → approve → send
  const name = `E2E Proposta ${Date.now()}`;

  await login(page, 'comercial', 'comercial123');
  await qualifiedLead(page, name);
  await createReadyOpportunity(page, name);

  // Create the Proposal from the ready Opportunity (the title pre-fills from the opportunity name).
  await page.getByRole('button', { name: 'Criar proposta' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.locator('#ptitle')).toHaveValue(name);
  await dialog.getByRole('button', { name: 'Criar proposta' }).click();
  await expect(page.getByText('Proposta criada')).toBeVisible();

  // It lands on the new Proposal detail, as a Draft, traceable to the source Opportunity.
  await expect(page).toHaveURL(/\/propostas\//);
  await expect(page.getByRole('heading', { name })).toBeVisible();
  await expect(page.getByText('Rascunho').first()).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver oportunidade de origem' })).toBeVisible();

  // Add an item to the Draft Proposal and see the Total reflect it (Slice 2).
  await expect(page.getByText('Nenhum item ainda.')).toBeVisible();
  await page.getByRole('button', { name: 'Adicionar item' }).click();
  const itemDialog = page.getByRole('dialog');
  await itemDialog.locator('#idesc').fill('Pacote de viagem corporativo');
  await itemDialog.locator('#iqty').fill('2');
  await itemDialog.locator('#iunit').fill('1500');
  await itemDialog.getByRole('button', { name: 'Adicionar' }).click();
  await expect(page.getByText('Item adicionado')).toBeVisible();

  // The item shows in the table; the subtotal and total reflect 2 × R$ 1.500 = R$ 3.000 (Slice 2).
  await expect(page.getByText('Pacote de viagem corporativo')).toBeVisible();
  await expect(page.getByText('Nenhum item ainda.')).toBeHidden();
  await expect(page.getByText('Subtotal')).toBeVisible();
  await expect(page.getByText(/3[.,]000/).first()).toBeVisible();

  // Edit the commercial details: set a validity date (required to submit — Slice 6) and a 10% proposal
  // discount that drops the total to R$ 2.700 (Slice 3).
  await page.getByRole('button', { name: 'Editar dados comerciais' }).click();
  const detailsDialog = page.getByRole('dialog');
  const dvalid = detailsDialog.locator('#dvalid');
  await dvalid.click();
  await dvalid.pressSequentially('31/12/2026'); // real key events so PrimeNG parses the typed date
  await dvalid.press('Enter');
  await expect(dvalid).toHaveValue('31/12/2026');
  await detailsDialog.getByText('Sem desconto').click();
  await page.getByRole('option', { name: 'Percentual (%)' }).click();
  const ddval = detailsDialog.locator('#ddval');
  await ddval.waitFor({ state: 'visible' });
  await ddval.click();
  await ddval.fill('10');
  await ddval.blur();
  const saveDetails = detailsDialog.getByRole('button', { name: 'Salvar' });
  await expect(saveDetails).toBeEnabled();
  await saveDetails.click();
  await expect(page.getByText('Dados atualizados')).toBeVisible();
  await expect(page.getByText(/2[.,]700/).first()).toBeVisible();

  // Submit the proposal for review (Slice 3): it leaves Draft and becomes "Pronta para revisão".
  await page.getByRole('button', { name: 'Enviar para revisão' }).click();
  await expect(page.getByText('Proposta enviada para revisão')).toBeVisible();
  await expect(page.getByText('Pronta para revisão').first()).toBeVisible();

  // Slice 5 — the detail consultation: the status history records the submit transition, and the source
  // Lead card is shown (name + contacts), alongside the source Opportunity.
  await expect(page.getByText('Histórico de status')).toBeVisible();
  await expect(page.locator('.history')).toContainText('Rascunho');
  await expect(page.locator('.history')).toContainText('Pronta para revisão');
  await expect(page.getByText('Lead de origem', { exact: true })).toBeVisible();

  // Slice 7 — the manager (an approver) approves the Proposal under review → Aprovada, recorded in history.
  await page.getByRole('button', { name: 'Aprovar' }).click();
  await expect(page.getByText('Proposta aprovada')).toBeVisible();
  await expect(page.getByText('Aprovada').first()).toBeVisible();
  await expect(page.locator('.history')).toContainText('Aprovada');

  // Slice 8 — the operator marks the approved Proposal as sent to the client, recording a channel → Enviada.
  // Open via the `m` shortcut (also avoids the lingering "Proposta aprovada" toast overlapping the button).
  await page.keyboard.press('m');
  const sendDialog = page.getByRole('dialog');
  await sendDialog.getByText('Selecione').click();
  await page.getByRole('option', { name: 'E-mail' }).click();
  await sendDialog.getByRole('button', { name: 'Marcar como enviada' }).click();
  await expect(page.getByText('Proposta marcada como enviada')).toBeVisible();
  await expect(page.getByText('Enviada').first()).toBeVisible();
  await expect(page.locator('.history')).toContainText('Enviada');
  await expect(page.getByText('Canal de envio', { exact: true })).toBeVisible(); // the summary row
  await expect(page.locator('.sending-channel')).toHaveText('E-mail');

  // Slice 9 — the operator registers the client's acceptance (via the `c` shortcut, avoiding the toast),
  // optionally with a confirmation note → Aceita.
  await page.keyboard.press('c');
  const acceptDialog = page.getByRole('dialog');
  await acceptDialog.locator('#anote').fill('Cliente confirmou por e-mail');
  await acceptDialog.getByRole('button', { name: 'Registrar aceite' }).click();
  await expect(page.getByText('Aceite registrado')).toBeVisible();
  await expect(page.getByText('Aceita').first()).toBeVisible();
  await expect(page.locator('.history')).toContainText('Aceita');
  await expect(page.getByText('Nota do aceite', { exact: true })).toBeVisible(); // the summary row

  // Slice 10 — create the commercial order from the accepted proposal; it lands on the order detail, which
  // preserves the items/total and traces back to the proposal. Let the acceptance toast clear first so it does
  // not overlap the header button.
  await expect(page.getByText('Aceite registrado')).toBeHidden();
  await page.getByRole('button', { name: 'Criar pedido comercial' }).click();
  await expect(page.getByText('Pedido comercial criado')).toBeVisible();
  await expect(page).toHaveURL(/\/pedidos\//);
  await expect(page.getByRole('heading', { name: 'Pedido comercial' })).toBeVisible();
  await expect(page.getByText('Pendente de reserva').first()).toBeVisible(); // a travel package needs booking
  await expect(page.getByText('Pacote de viagem corporativo')).toBeVisible(); // the snapshotted item
  await expect(page.getByText('Ganha').first()).toBeVisible(); // the source opportunity is now won
  await expect(page.getByRole('link', { name: 'Ver proposta de origem' })).toBeVisible();

  // Back on the accepted proposal, the action now links to the existing order instead of creating a new one.
  await page.getByRole('button', { name: 'Voltar para a proposta' }).click();
  await expect(page).toHaveURL(/\/propostas\//);
  await expect(page.getByRole('button', { name: 'Ver pedido comercial' })).toBeVisible();

  // The Vendas module exposes "Propostas" in the menu, and the proposal shows on its list. The row now
  // carries both a title link (→ the proposal) and a source-opportunity link, so match the proposal one.
  await expect(page.locator('.sidebar').getByText('Vendas')).toBeVisible();
  await page.locator('.sidebar').getByRole('link', { name: 'Propostas' }).click();
  await expect(page.getByRole('heading', { name: 'Propostas' })).toBeVisible();
  await page.locator('#q').fill(name); // search so it is found regardless of pagination
  await expect(page.locator('a[href^="/propostas/"]').filter({ hasText: name })).toBeVisible();
});
