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
  const comercialToggle = sidebar.getByRole('button', { name: /Comercial/ });

  // On the system home no module is active, so the Comercial section is collapsed and its items (Leads)
  // are hidden.
  await expect(comercialToggle).toHaveAttribute('aria-expanded', 'false');
  await expect(leadsLink).toBeHidden();

  // Opening the Comercial module reveals its sub-menu and navigates to its home.
  await sidebar.getByRole('link', { name: 'Comercial', exact: true }).click();
  await expect(page).toHaveURL(/\/comercial$/);
  await expect(comercialToggle).toHaveAttribute('aria-expanded', 'true');
  await expect(leadsLink).toBeVisible();

  // Switching to Acompanhamento closes the Comercial sub-menu (one open at a time = short menu).
  await sidebar.getByRole('link', { name: 'Acompanhamento', exact: true }).click();
  await expect(page).toHaveURL(/\/acompanhamento$/);
  await expect(comercialToggle).toHaveAttribute('aria-expanded', 'false');
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

test('Escape exits the Novo lead screen — closes the dropdown first, then warns when changed', async ({
  page,
}) => {
  await login(page);
  await page.goto('/leads/new');

  // With the origin dropdown open, Esc closes the dropdown — it must NOT leave the screen.
  await page.getByText('Selecione a origem').click();
  await expect(page.getByRole('option').first()).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('option')).toHaveCount(0); // dropdown closed
  await expect(page).toHaveURL(/\/leads\/new$/); // still on the form

  // Esc on the changed form warns before discarding; Descartar leaves to the home.
  await page.locator('#name').fill('Rascunho via Esc');
  await page.keyboard.press('Escape');
  await expect(page.getByText(/alterações não salvas/)).toBeVisible();
  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(page).toHaveURL(/\/$/);
});

test('canceling a changed dialog warns; Descartar closes it', async ({ page }) => {
  await login(page);
  await page.goto('/cadastros/origens');

  // Scope to the page content (the cadastro "Novo" button lives in the page header).
  await page.locator('main').getByRole('button', { name: 'Novo' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.locator('#code').fill('TESTE_E2E');

  await dialog.getByRole('button', { name: 'Cancelar' }).click();
  await expect(page.getByText(/alterações não salvas/)).toBeVisible();

  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(dialog).toBeHidden();
});

test('Escape exits the cadastro dialog — immediately when clean, warning when changed', async ({
  page,
}) => {
  await login(page);
  await page.goto('/cadastros/origens');

  // Esc on a clean dialog closes it right away (the "exit cadastros" shortcut).
  await page.locator('main').getByRole('button', { name: 'Novo' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();

  // Esc on a CHANGED dialog warns before discarding (the guard is not bypassed by the shortcut).
  await page.locator('main').getByRole('button', { name: 'Novo' }).click();
  await expect(dialog).toBeVisible();
  await dialog.locator('#code').fill('ESC_E2E');
  await page.keyboard.press('Escape');
  await expect(page.getByText(/alterações não salvas/)).toBeVisible();
  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(dialog).toBeHidden();
});
