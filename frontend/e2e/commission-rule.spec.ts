import { test, expect, Page } from '@playwright/test';

/**
 * Sprint 6 / Slice 1 end-to-end: Commission Management — the commission rules screen (under Cadastros) is wired
 * into the navigation, the route and the manage guard. A commercial/financial manager creates a basic commission
 * rule and sees it listed; a seller has no commission scope and cannot reach the route. The validation and the
 * 422/403/401 boundaries are covered thoroughly by CommissionRuleApiIntegrationTest; this E2E covers the
 * cross-cutting concerns — the module visibility, the route, the guard and the create journey through the UI.
 */

async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('a manager creates a commission rule and sees it listed', async ({ page }) => {
  await login(page, 'comercial', 'comercial123');

  await page.goto('/cadastros/regras-comissao');
  await expect(page.getByRole('heading', { name: 'Regras de comissão' })).toBeVisible();

  await page.getByRole('button', { name: 'Nova regra' }).click();
  const name = `E2E Regra ${Date.now()}`;
  await page.getByPlaceholder('Ex.: Padrão vendedores').fill(name);
  // Commit the percentage into the p-inputNumber (zoneless: pressSequentially, not fill).
  const percent = page.locator('p-inputnumber input');
  await percent.click();
  await percent.pressSequentially('5');
  await percent.blur();

  await page.getByRole('button', { name: 'Criar regra' }).click();

  // The dialog closes and the new rule appears in the table.
  await expect(page.getByText(name)).toBeVisible();
  await expect(page.getByText('5%')).toBeVisible();
});

test('a seller cannot reach the commission rules route', async ({ page }) => {
  await login(page, 'vendedor', 'vendedor123');
  await page.goto('/cadastros/regras-comissao');
  // The manage guard redirects back to the system home.
  await expect(page).not.toHaveURL(/regras-comissao/);
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
});
