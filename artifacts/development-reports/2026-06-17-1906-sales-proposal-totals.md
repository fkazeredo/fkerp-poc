# Relatório — Sprint 3 / Slice 3: Totais/descontos/validade da Proposal + separação de módulos + atalhos + aviso de alterações não salvas

- **Data:** 2026-06-17
- **Branch:** `feature/sales-proposal-totals` (a partir de `develop`)
- **Versão:** 0.25.0 → **0.26.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** a Proposal passa a deixar a oferta **comercialmente clara antes do envio** (subtotal,
  desconto da proposta, total, validade, termos, notas de pagamento) e ganha a transição **Enviar para
  revisão**. Além disso, a pedido do dono, três mudanças transversais de frontend: **separação visual dos
  módulos**, **atalhos de teclado para todas as funcionalidades** e **aviso de alterações não salvas**.

## 1. O que foi implementado

**Backend — `domain.sales`**
- **`DiscountType`** ganhou `amountOf(value, base)` e `isValid(value, base)`; `ProposalItem` passou a
  **delegar** seu cálculo/validação de desconto a esses métodos (DRY) — o desconto de item e o da proposta
  agora compartilham as mesmas regras.
- **`Proposal`** ganhou `subtotal`, `discountType`/`discountValue` (desconto da proposta), `paymentNotes`, e
  `recomputeTotals()` (`subtotal = Σ lineTotal`; `total = subtotal − desconto`, com o desconto **limitado ao
  subtotal** para o total nunca ficar negativo). Novos métodos `updateCommercialDetails(...)` e
  `submitForReview(...)` (DRAFT-only; submit exige ≥1 item e total > 0).
- **3 exceptions** novas mapeadas no `HttpErrorMapping`: `ProposalDiscountInvalidException`
  (`proposal.discount-invalid`, 422), `ProposalHasNoItemsException` (`proposal.no-items`, 422),
  `ProposalTotalRequiredException` (`proposal.total-required`, 422); a mensagem de `proposal.not-editable`
  foi generalizada (não fala só de itens).
- **`service.data`**: `UpdateProposalCommand`; `ProposalDetail` estendido (`subtotal`, `discountType`,
  `discountValue`, `paymentNotes`). **`ProposalService`**: `updateDetails(...)` e `submitForReview(...)`.
- **Endpoints**: `PUT /api/proposals/{id}` (editar dados comerciais) e `POST /api/proposals/{id}/submit`
  (enviar p/ revisão) — ambos gated por `sales:proposal:update`, devolvem o `ProposalDetail`. DTO
  `ProposalUpdateRequest`. Matchers correspondentes no `SecurityConfig`.
- **Migração `V22__proposal_totals.sql`**: colunas `subtotal`/`discount_type`/`discount_value`/`payment_notes`
  + backfill `subtotal = total` + CHECKs (par desconto, percent ≤ 100, não-negativos, `subtotal/total ≥ 0`).
  Sem novo escopo.

**Frontend**
- **Separação de módulos (sidebar):** cada módulo (Comercial / CRM, Vendas, Cadastros) é um **bloco visual
  distinto** com ícone, divisória e acento; subtítulo da marca neutro ("Gestão comercial"). Cadastros virou
  bloco próprio.
- **Atalhos abrangentes:** revisão do mapa global (`g i/l/o/p/c`, `n`, `?`, `Ctrl/⌘ K`), paleta de comandos
  como índice universal, **atalhos de contexto na proposta** (`i` item, `e` editar dados, `s` enviar p/
  revisão, `Esc`), e o overlay `?` reescrito com o mapa completo por módulo.
- **Aviso de alterações não salvas (transversal):** `UnsavedChangesService` + `unsavedChangesGuard`
  (`CanDeactivate`) aplicado às rotas de formulário (lead novo/detalhe, oportunidade detalhe, proposta
  detalhe, cadastros); cada componente implementa `hasUnsavedChanges()`; `<p-confirmDialog>` no shell e
  `beforeunload` para fechar aba/recarregar. Cobre saída por link, paleta **e atalhos**.
- **Proposta — totais/desconto/validade/submit:** card de **Itens** agora mostra **Subtotal / Desconto da
  proposta / Total**; diálogo **Editar dados comerciais** (validade, termos, notas de pagamento, desconto da
  proposta); botão **Enviar para revisão** (gated por DRAFT + itens + total > 0); coluna **Total** já na
  lista (Slice 2).

## 2. Regras funcionais cobertas

- Total derivado dos itens e do desconto da proposta; **total nunca negativo** (desconto limitado ao
  subtotal). Desconto da proposta em **valor ou percentual**; inválido → `proposal.discount-invalid` (422).
- **Notas de pagamento** = texto descritivo; **não** cria financeiro/recebível.
- **Validade** pode ser informada agora; obrigatória apenas no *envio* (etapa futura — guarda documentada).
- **Enviar para revisão** exige **≥ 1 item** e **total > 0** (`proposal.no-items` / `proposal.total-required`,
  422); move `DRAFT → READY_FOR_REVIEW`; editar/itens só em DRAFT.
- Nada cria Receivable/Payment/Booking/Commission/imposto/margem; itens da Slice 2 continuam funcionando.
- Saída de qualquer formulário/edição em andamento avisa antes de perder dados (inclusive via atalhos).

## 3. Critérios de aceitação cobertos

- ✅ Subtotal reflete os itens; ✅ total reflete o desconto; ✅ total não fica negativo.
- ✅ Validade e termos comerciais podem ser informados (e notas de pagamento).
- ✅ Não move para revisão sem itens; ✅ não move para revisão sem total válido (> 0).
- ✅ Nenhum comportamento de Financial/Booking/Commission criado.
- ✅ Comportamento de itens da Proposal continua funcionando.
- ✅ (Extra do dono) módulos separados visualmente; ✅ atalhos para todas as funcionalidades; ✅ aviso de
  alterações não salvas, inclusive com atalhos.

## 4. Arquivos alterados (principais)

- **Backend domain**: `model/DiscountType.java`, `model/ProposalItem.java`, `model/Proposal.java`,
  `exception/{ProposalDiscountInvalid,ProposalHasNoItems,ProposalTotalRequired}Exception.java`,
  `service/data/{UpdateProposalCommand,ProposalDetail}.java`, `service/ProposalService.java`.
- **Backend delivery/infra**: `application/api/ProposalController.java` + `api/dto/ProposalUpdateRequest.java`,
  `infra/web/HttpErrorMapping.java`, `infra/security/SecurityConfig.java`, `resources/messages.properties`,
  `resources/db/migration/V22__proposal_totals.sql`.
- **Frontend core**: `core/forms/unsaved-changes.service.ts`, `core/guards/unsaved-changes.guard.ts`,
  `core/layout/shell.{ts,html,css}`, `app.config.ts`, `app.routes.ts`, `core/api/proposal.service.ts`.
- **Frontend features**: `features/proposals/proposal-detail/{ts,html,css}`; `hasUnsavedChanges()` em
  `lead-create`, `lead-detail`, `opportunity-detail`, `reference-list`.
- **Docs/versão**: `CLAUDE.md` (§3, §10, §12), manual en-US + pt-BR, `application.yml` + `compose.yaml`
  (0.26.0), specs e E2E.

## 5. Testes / validações

- **Unit (domínio)** `ProposalTotalsTest` (11): subtotal/total, desconto AMOUNT/PERCENT, total nunca
  negativo (cap), `updateCommercialDetails` DRAFT-only, `submitForReview` feliz/sem itens/total 0/fora de
  DRAFT, notas de pagamento. **`ProposalServiceTest`** (+4): updateDetails/submit visibilidade/inexistente.
- **Integração (Postgres real)** `ProposalLifecycleApiIntegrationTest` (10): editar (validade/termos/notas/
  desconto) com subtotal/total; desconto > subtotal → 422; submit feliz/sem itens/total 0; DRAFT-only;
  visibilidade (rep 403, finance 403, diretor 403, não autenticado 401). Contrato do detalhe atualizado.
- **Backend `./mvnw verify`: 364 testes, 0 falhas** (ArchUnit/Modulith/Checkstyle + completude do
  `HttpErrorMapping`).
- **Frontend**: `proposal-detail.spec` (editar dados/desconto, gating de submit, unsaved), `shell.spec`
  (`g o`/`g p`), novo `unsaved-changes.guard.spec`, e `ConfirmationService` adicionado aos specs afetados.
  **155 testes, 0 falhas**; `ng build` verde (aviso leve de budget de CSS — não bloqueante).
- **E2E (stack isolada 4201): 32 testes verdes** — `proposal-creation` (item → desconto 10% → total cai p/
  2.700 → Enviar para revisão → "Pronta para revisão"), `shortcuts` (`g o`, `g p`, e **aviso de alterações
  não salvas ao sair via atalho**).

## 6. Lacunas conhecidas / fora de escopo

- Aviso de alterações não salvas: o **guard de rota + beforeunload** cobrem o pedido (sair/atalhos/fechar
  aba). Nos diálogos de detalhe, "edição em andamento" = diálogo aberto (heurística), e fechar via *Cancelar*
  é ação explícita (não pergunta). Refinar para detecção campo-a-campo é evolução futura.
- Validade obrigatória só no *envio ao cliente* (transição futura). Restante do ciclo
  (Approve/Send/Accept/Reject/Expire/Cancel) é slice futura.
- Aviso leve de budget de CSS do `shell.css` (warning, longe do limite de erro).
- Fora de escopo: imposto, cronograma de pagamento, recebível, gateway, comissão, validação de preço de
  booking, margem.

## 7. Próximo prompt sugerido

> Read CLAUDE.md and the current Sprint 3 Sales & Proposals implementation. Continue Sprint 3. Implement only
> the next functional slice: **SLICE 4: Proposal review & approval** — an authorized reviewer **approves** a
> `READY_FOR_REVIEW` proposal (`→ APPROVED`) or **sends it back to Draft** (`→ DRAFT`) with a note, recording
> who/when in a status history; gate it with a distinct review permission and keep the read model/audit. Out
> of scope: sending to the client, acceptance, sale/order/booking/financial. Define the allowed transitions,
> the gating scope, the audit/history and the read model, then report the 7 points.
