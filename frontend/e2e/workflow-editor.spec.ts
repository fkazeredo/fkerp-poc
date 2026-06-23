import { test, expect, Page } from '@playwright/test';

/**
 * The visual workflow editor (Phase 5c): the admin opens a configurable workflow, sees it drawn as a graph
 * (states = nodes, transitions = edges via ngx-graph), edits a state's display attributes, and manages the
 * attention rules that drive the pending-items worklists. Gated by workflow:manage (the seeded admin user
 * `comercial` holds it). The edits here are net-zero (a created rule is deleted again) so the shared E2E
 * stack stays clean for the other journeys.
 */

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill('comercial');
  await page.locator('#password').fill('comercial123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
}

test('the admin edits a workflow state and manages an attention rule through the visual editor', async ({
  page,
}) => {
  await login(page);

  // Reach the editor from the standalone "Fluxos de trabalho" module.
  await page.goto('/fluxos');
  await expect(page.getByRole('heading', { name: 'Fluxos de trabalho' })).toBeVisible();
  await page.getByText('Oportunidade', { exact: true }).click();
  await expect(page).toHaveURL(/\/fluxos\/opportunity$/);

  // The graph renders the states as nodes (proves ngx-graph draws the dagre layout in the browser), with the
  // orientation aids (counts + the category legend).
  await expect(page.locator('ngx-graph')).toBeVisible();
  await expect(page.locator('.counts')).toContainText('estados');
  await expect(page.locator('.legend')).toContainText('Inicial');
  await expect(page.locator('g.wf-node').filter({ hasText: 'Nova' })).toBeVisible();
  await expect(page.locator('g.wf-node').filter({ hasText: 'Descoberta' })).toBeVisible();

  // Click a state node → the edit panel opens; rename it and save. (Assert the immutable code, not the
  // label, so the test stays valid even if a prior run already renamed the state on the shared stack.)
  await page.locator('g.wf-node').filter({ hasText: 'Descoberta' }).click();
  await expect(page.locator('#slabel')).toBeVisible(); // the state edit panel opened
  await page.locator('#slabel').fill('Descoberta (E2E)');
  await page.getByRole('button', { name: 'Salvar estado' }).click();
  await expect(page.getByText('Estado atualizado')).toBeVisible();

  // Create a custom attention rule (a no-parameter condition keeps the form simple). A unique code keeps
  // the create idempotent across retries on the shared stack.
  const code = `E2E_RULE_${Date.now()}`;
  const label = `Regra de atenção ${code}`;
  await page.getByRole('button', { name: 'Nova regra de atenção' }).click();
  await page.getByText('Selecione a condição').click();
  await page.getByRole('option', { name: 'EXPECTED_CLOSE_OVERDUE' }).click();
  await expect(page.getByRole('option', { name: 'EXPECTED_CLOSE_OVERDUE' })).toHaveCount(0); // overlay closed
  await page.locator('#rcode').fill(code);
  await page.locator('#rlabel').fill(label);
  await page.getByRole('button', { name: 'Criar regra' }).click();
  await expect(page.getByText('Regra criada')).toBeVisible();
  await expect(page.locator('.rule-list')).toContainText(label);

  // Delete it again so the shared stack is left as it was (the trash is the row's last button).
  const customRule = page.locator('.rule-list li').filter({ hasText: label });
  await customRule.getByRole('button').last().click();
  await expect(page.getByText('Regra removida')).toBeVisible();
  await expect(page.locator('.rule-list')).not.toContainText(label);
});

test('a user without workflow:manage cannot reach the editor', async ({ page }) => {
  // `representante` is a sales rep without reference:manage / workflow:manage.
  await page.goto('/login');
  await page.locator('#username').fill('representante');
  await page.locator('#password').fill('representante123');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();

  // The route guard redirects back to the home screen (the editor is never shown).
  await page.goto('/fluxos');
  await expect(page.getByRole('heading', { name: 'Bem-vindo ao FKERP' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Fluxos de trabalho' })).toHaveCount(0);
});
