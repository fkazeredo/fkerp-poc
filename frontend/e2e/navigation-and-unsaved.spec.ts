import { test, expect, Page } from '@playwright/test';

/**
 * Proves two behaviours end-to-end (real browser):
 * 1. the sidebar stays compact — only the active module's sub-menu is open (accordion);
 * 2. canceling a changed form/dialog warns before losing the data.
 */
async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('sidebar is compact: only the active module sub-menu is shown', async ({ page }) => {
  await login(page);
  const sidebar = page.locator('.sidebar');
  // Target the sidebar Leads link by href — robust against decorative icon glyphs polluting the name.
  const leadsLink = sidebar.locator('a[href="/leads"]');
  const crmToggle = sidebar.getByRole('button', { name: /Comercial \/ CRM/ });

  // On the system home no module is active, so the CRM section is collapsed and its items (Leads) are hidden.
  await expect(crmToggle).toHaveAttribute('aria-expanded', 'false');
  await expect(leadsLink).toBeHidden();

  // Opening the Comercial / CRM module reveals its sub-menu and navigates to its home.
  await sidebar.getByText('Comercial / CRM').click();
  await expect(page).toHaveURL(/\/crm$/);
  await expect(crmToggle).toHaveAttribute('aria-expanded', 'true');
  await expect(leadsLink).toBeVisible();

  // Switching to Vendas closes the CRM sub-menu (one open at a time = short menu).
  await sidebar.getByText('Vendas').click();
  await expect(page).toHaveURL(/\/vendas$/);
  await expect(crmToggle).toHaveAttribute('aria-expanded', 'false');
  await expect(leadsLink).toBeHidden();
});

test('canceling a changed form warns before discarding (Continuar editando keeps you)', async ({
  page,
}) => {
  await login(page);
  await page.goto('/leads/new');
  await page.locator('#name').fill('Rascunho não salvo');

  await page.getByRole('button', { name: 'Cancelar' }).click();
  await expect(page.getByText(/alterações não salvas/)).toBeVisible();

  await page.getByRole('button', { name: 'Continuar editando' }).click();
  await expect(page).toHaveURL(/\/leads\/new$/);

  // Choosing Descartar this time actually leaves the form.
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(page).toHaveURL(/\/$/);
});

test('canceling a changed dialog warns; Descartar closes it', async ({ page }) => {
  await login(page);
  await page.goto('/cadastros/origens');

  // Scope to the page content so this matches the cadastro "Novo" button, not the sidebar "Novo lead" CTA.
  await page.locator('main').getByRole('button', { name: 'Novo' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.locator('#code').fill('TESTE_E2E');

  await dialog.getByRole('button', { name: 'Cancelar' }).click();
  await expect(page.getByText(/alterações não salvas/)).toBeVisible();

  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(dialog).toBeHidden();
});
