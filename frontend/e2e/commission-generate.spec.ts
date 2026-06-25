import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slice 2 end-to-end: generating an Expected Commission from a Commercial Order. The data-driven
 * behavior (the rule matching, the commercial-vs-received basis, the one-active-per-order rule, the 422/409/403/401
 * boundaries) is covered thoroughly by CommissionApiIntegrationTest against a seeded Postgres, and the button
 * gating + the inline result by CommissionService / OrderDetailPage component specs. This E2E covers the
 * cross-cutting concerns the lower layers miss — the keyboard-access wiring (the help overlay documents the
 * "Gerar comissão" shortcut) and that the Order detail page still renders with the commission wiring (the isolated
 * e2e stack has no seeded orders, so there is no closed order to generate from here).
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the help overlay documents the generate-commission shortcut on an order', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.keyboard.press('?');
  const help = page.getByRole('dialog', { name: 'Atalhos do teclado' });
  await expect(help).toBeVisible();
  await expect(help.getByText('No detalhe de um pedido')).toBeVisible();
  await expect(help.getByText('Gerar comissão')).toBeVisible();
});

test('the Order detail page renders with the commission wiring (empty stack: not found)', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');
  await page.goto('/pedidos/00000000-0000-0000-0000-0000000000ff');
  // The page loads (the new CommissionService injection did not break it); the empty stack has no such order.
  await expect(page.getByText('Pedido não encontrado.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Voltar' })).toBeVisible();
});
