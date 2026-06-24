import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 5 / Slice 1 end-to-end: the Financeiro module (Contas a receber) is wired into the navigation, the
 * routes and the read/create guards. The data-driven behavior (creating a Receivable from a confirmed Order,
 * the one-active-per-order rule, the visibility tiers, the eligible-orders selector) is covered thoroughly by
 * ReceivableApiIntegrationTest against a seeded Postgres; this E2E covers the cross-cutting concerns the
 * lower layers miss — the module visibility, the routes, the guards, the keyboard shortcut and the empty/
 * not-found rendering (the isolated e2e stack has no seeded orders, so there is nothing eligible to bill).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('finance reaches the receivables list and the create form (empty stack: no eligible orders)', async ({
  page,
}) => {
  await login(page, 'financeiro', 'financeiro123');

  await page.goto('/financeiro/contas-a-receber');
  await expect(page.getByRole('heading', { name: 'Contas a receber' })).toBeVisible();
  await expect(page.getByText('Nenhuma conta a receber para acompanhar.')).toBeVisible();

  // The operational list exposes its follow-up columns and filters (next due date, overdue-only, payer).
  await expect(page.getByText('Próx. vencimento')).toBeVisible();
  await expect(page.getByText('Somente vencidas')).toBeVisible();
  await expect(page.getByText('Cliente (pagador)')).toBeVisible();

  // Finance may create one; the form opens and, on the empty stack, offers no eligible order.
  await page.getByRole('link', { name: 'Nova conta a receber' }).click();
  await expect(page).toHaveURL(/\/financeiro\/contas-a-receber\/nova/);
  await expect(page.getByRole('heading', { name: 'Nova conta a receber' })).toBeVisible();
  await expect(page.locator('label[for="dueDate"]')).toBeVisible();

  // The installment editor is present: a "Parcelas" section with an add-installment action that, when used,
  // reveals the live "Remaining" balance against the order total.
  await expect(page.getByText('Parcelas', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Adicionar parcela' }).click();
  await expect(page.getByText('Restante')).toBeVisible();
});

test('the "g f" shortcut jumps to the receivables list', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');
  await page.keyboard.press('g');
  await page.keyboard.press('f');
  await expect(page).toHaveURL(/\/financeiro\/contas-a-receber$/);
  await expect(page.getByRole('heading', { name: 'Contas a receber' })).toBeVisible();
});

test('finance sees a not-found render for an unknown receivable id', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');
  await page.goto('/financeiro/contas-a-receber/00000000-0000-0000-0000-0000000000ff');
  await expect(page.getByText('Conta a receber não encontrada.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
});

test('the payment-methods cadastro lists the seeded methods', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');

  await page.goto('/cadastros/formas-pagamento');
  await expect(page.getByRole('heading', { name: 'Formas de pagamento' })).toBeVisible();
  // The seeded methods are listed (Slice 5: Cash / Bank transfer / Pix / … / Other).
  await expect(page.getByText('Dinheiro', { exact: true })).toBeVisible();
  await expect(page.getByText('Pix', { exact: true })).toBeVisible();
});

test('the help overlay documents the register-payment shortcut on a receivable', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');
  await page.keyboard.press('?');
  const help = page.getByRole('dialog', { name: 'Atalhos do teclado' });
  await expect(help).toBeVisible();
  await expect(help.getByText('Registrar pagamento')).toBeVisible();
});

test('finance opens the Recebimentos operational view from the Financeiro module', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');

  await page.goto('/financeiro/recebimentos');
  await expect(page.getByRole('heading', { name: 'Recebimentos' })).toBeVisible();
  // The two-part operational view: the current snapshot and the received-in-period section.
  await expect(page.getByText('Em aberto (R$)')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recebido no período' })).toBeVisible();
  // The isolated e2e stack has no payments → the by-method breakdown shows its empty state.
  await expect(page.getByText('Nenhum pagamento recebido no período.')).toBeVisible();
});

test('finance reaches the same view via the Financeiro tab of the Indicadores hub', async ({ page }) => {
  await login(page, 'financeiro', 'financeiro123');

  await page.goto('/indicadores');
  const financeiroTab = page.getByRole('tab', { name: 'Financeiro' });
  await expect(financeiroTab).toBeVisible();
  await financeiroTab.click();
  await expect(page.getByRole('heading', { name: 'Recebimentos' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recebido no período' })).toBeVisible();
});

test('a seller without a financial read tier cannot reach the receivables routes', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');

  // The read guard blocks the list, create and indicators routes and redirects back to the system home.
  await page.goto('/financeiro/contas-a-receber');
  await expect(page).not.toHaveURL(/\/financeiro\//);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();

  await page.goto('/financeiro/contas-a-receber/nova');
  await expect(page).not.toHaveURL(/\/financeiro\//);

  await page.goto('/financeiro/recebimentos');
  await expect(page).not.toHaveURL(/\/financeiro\//);
});
