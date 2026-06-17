# Relatório — Sprint 3 / Slice 2: Itens da Proposal (adicionar / editar / remover)

- **Data:** 2026-06-17
- **Branch:** `feature/sales-proposal-items` (a partir de `develop`)
- **Versão:** 0.24.0 → **0.25.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** uma Proposal em **DRAFT** passa a receber **itens** (o que a empresa pretende
  vender), cada um contribuindo para o **total** da proposta. Sem reserva, sem disponibilidade externa,
  sem financeiro/comissão/imposto/margem.

## 1. O que foi implementado

- **Coleção-filha do agregado `Proposal`** (espelha `OpportunityActivity`): nova entidade
  **`ProposalItem`** (`@OneToMany`, cascade + `orphanRemoval`, `@JoinColumn proposal_id`) com `type`,
  `description`, `quantity`, `unitValue`, `discountType`, `discountValue`, `createdAt`. Enums
  **`ProposalItemType`** (`TRAVEL_PACKAGE`, `CAR_RENTAL`, `SERVICE_FEE`, `OTHER`) e **`DiscountType`**
  (`AMOUNT`, `PERCENT`). Cálculo no próprio item: `subtotal = unitValue × quantity`; `lineTotal =
  subtotal − desconto` (AMOUNT: valor; PERCENT: `subtotal × %/100`), `BigDecimal` escala 2 `HALF_UP`.
- **Agregado `Proposal` estendido**: coluna denormalizada **`total`** + métodos `addItem`, `updateItem`,
  `removeItem`, todos com a guarda **`requireDraft()`** e `recomputeTotal()` (soma dos `lineTotal`), e
  atualização de `updatedBy`. O total é recomputado **dentro do agregado** a cada mudança (sem N+1 na
  lista).
- **Desconto flexível (decisão do dono)**: por linha, **valor (R$)** *ou* **percentual (%)** —
  `discountType` + `discountValue` (ambos nulos = sem desconto). Validação ambos-ou-nenhum, `PERCENT`
  0–100, `AMOUNT` 0–subtotal → `ProposalItemInvalidException`.
- **3 novas exceptions** registradas no `HttpErrorMapping`: `ProposalNotEditableException`
  (`proposal.not-editable`, **422**), `ProposalItemNotFoundException` (`proposal.item-not-found`, **404**),
  `ProposalItemInvalidException` (`proposal.item-invalid`, **422**); chaves i18n `proposal.*` adicionadas.
- **`service.data`**: `ProposalItemCommand` (input único reusado por add e update); `ProposalDetail`
  estendido com `List<Item> items` (id, type, description, quantity, unitValue, discountType,
  discountValue, lineTotal) + `total`; `ProposalListItem` estendido com `total`.
- **`ProposalService`**: `toDetail(proposal)` extraído (carrega a Opp + resolve nomes + monta o detalhe),
  reusado por `detail` e pelas operações; `addItem/updateItem/removeItem` → `loadVisible` → mutação no
  agregado → `saveAndFlush` → `toDetail`.
- **Endpoints** (`ProposalController`): `POST /api/proposals/{id}/items`,
  `PUT /api/proposals/{id}/items/{itemId}`, `DELETE /api/proposals/{id}/items/{itemId}` — todos devolvem o
  **`ProposalDetail`** atualizado; DTO `ProposalItemRequest` (Bean Validation). `SecurityConfig`: matchers
  POST/PUT/DELETE `/api/proposals/*/items[/*]` → `SCOPE_sales:proposal:update` (sem novo escopo).
- **Migração `V21__proposal_items.sql`**: `ALTER TABLE proposals ADD COLUMN total` + tabela
  `proposal_items` com FK, **CHECKs** (tipo ∈ 4 valores, `quantity ≥ 1`, `unit_value ≥ 0`, `discount_type
  ∈ {AMOUNT,PERCENT}`, `discount_value ≥ 0`, `(discount_type IS NULL) = (discount_value IS NULL)`,
  `discount_type <> 'PERCENT' OR discount_value <= 100`) e índice em `proposal_id`.
- **Frontend**: `proposal.service` (tipos `ProposalItem*`/`DiscountType`, `items`+`total` no detalhe,
  `total` na lista; métodos `addItem/updateItem/removeItem`); **card "Itens"** no detalhe da proposta —
  tabela (Tipo, Descrição, Qtd, Valor unit., Desconto, Total da linha) + **Total da proposta**, com botão
  **Adicionar item** e ações **editar/remover** por linha quando `canOperateProposal() && status==='DRAFT'`;
  diálogo add/edit com tipo, descrição, quantidade, valor unitário e **desconto** (Sem desconto / Valor
  (R$) / Percentual (%)). Lista de propostas ganhou a coluna **Total**.

## 2. Regras funcionais

- Apenas uma Proposal em **DRAFT** recebe/edita/remove itens; em qualquer outro status a operação é
  rejeitada (`proposal.not-editable`, 422). A guarda já protege os estados futuros (Sent/Accepted) — não há
  fluxo de revisão nesta slice.
- Item exige **descrição**, **quantidade** (inteiro ≥ 1) e **valor unitário** (≥ 0); o **desconto** é
  opcional e por linha (valor ou percentual).
- Cada item compõe o **total** da proposta (= soma dos totais de linha), recalculado a cada mudança e
  exibido no detalhe e na lista.
- Item inexistente em update/remove → `proposal.item-not-found` (404). Desconto inválido →
  `proposal.item-invalid` (422).
- Gestão de itens **não** cria Booking, **não** checa disponibilidade externa e **não** cria Financial/
  Commission/custo de fornecedor/margem/imposto/nota; a Opportunity e o Lead de origem permanecem
  inalterados.
- Autorização: ver/abrir a proposta segue os read tiers `sales:proposal:read[:unassigned|:all]`; operar
  itens exige `sales:proposal:update` (o chamador também precisa **poder ver** a proposta).

## 3. Critérios de aceitação

- ✅ Adicionar item em proposta DRAFT → 200 com o detalhe atualizado e **total** correto.
- ✅ Campos obrigatórios ausentes → 400 (corpo `{code, message, fields}`).
- ✅ Desconto valor/percentual aplicado ao total da linha; percentual > 100 ou valor > subtotal → 422
  `proposal.item-invalid`.
- ✅ Editar e remover recomputam o total.
- ✅ Tentar gerir itens fora de DRAFT → 422 `proposal.not-editable`.
- ✅ Visibilidade/escopo: representante não-dono → 403, financeiro → 403, diretor (sem update) → 403,
  não autenticado → 401.
- ✅ O contrato do detalhe expõe somente os campos comerciais (+ `items`, `total`) — nenhum campo de
  Sale/Order/Booking/Financial/Commission.

## 4. Arquivos alterados (principais)

- **Backend domain**: `model/ProposalItem.java` (novo), `model/ProposalItemType.java` (novo),
  `model/DiscountType.java` (novo), `model/Proposal.java` (estendido), `exception/{ProposalNotEditable,
  ProposalItemNotFound, ProposalItemInvalid}Exception.java` (novos), `service/data/ProposalItemCommand.java`
  (novo), `service/data/ProposalDetail.java` + `ProposalListItem.java` (estendidos),
  `service/ProposalService.java` (estendido).
- **Backend delivery/infra**: `application/api/ProposalController.java` + `api/dto/ProposalItemRequest.java`,
  `infra/web/HttpErrorMapping.java`, `infra/security/SecurityConfig.java`, `resources/messages.properties`,
  `resources/db/migration/V21__proposal_items.sql`.
- **Frontend**: `core/api/proposal.service.ts`, `features/proposals/proposal-detail/{ts,html,css,spec}`,
  `features/proposals/proposal-list/{ts,html,css,spec}`.
- **Docs/versão**: `CLAUDE.md` (§10 — bloco de itens da Proposal), manual en-US + pt-BR (§9.3),
  `backend/.../application.yml` + `compose.yaml` (0.25.0), `frontend/e2e/proposal-creation.spec.ts`.

## 5. Testes

- **Unit (domínio)** `ProposalItemsTest` (10): totais de linha sem desconto / AMOUNT / PERCENT, soma do
  total, add/update/remove recomputando, descontos inválidos, item inexistente, e edição fora de DRAFT
  (via `ReflectionTestUtils`) → `ProposalNotEditableException`.
- **Unit (serviço)** `ProposalServiceTest` (+2): addItem em proposta inexistente → 404; não visível → 403.
- **Integração (Postgres real)** `ProposalItemsApiIntegrationTest` (12): adicionar/editar/remover com total
  correto, percentual, obrigatórios → 400, desconto inválido → 422, DRAFT-only (UPDATE direto p/ 'SENT') →
  422, visibilidade (rep 403, finance 403, diretor 403), não autenticado 401, contrato do item.
- **Slice 1 ajustada**: `ProposalCreationApiIntegrationTest.exposesOnlyCommercialOfferFields` agora inclui
  `items` e `total` no contrato do detalhe.
- **Backend `./mvnw verify`**: **339 testes, 0 falhas** (ArchUnit + Modulith + Checkstyle + completude do
  `HttpErrorMapping`).
- **Frontend**: `proposal-detail.spec` estendido (add/edit/remove chamam o serviço e atualizam o detalhe;
  gating por DRAFT + escopo; desconto percentual; obrigatoriedade do valor de desconto), `proposal-list.spec`
  ajustado (`total`). **147 testes, 0 falhas**; `ng build` verde.
- **E2E (stack isolada 4201)**: `proposal-creation.spec.ts` estendido — após criar a proposta, **adiciona um
  item** (2 × R$ 1.500) e confirma o **Total** refletido. Suíte E2E completa **verde** (31 testes).

## 6. Lacunas conhecidas / fora de escopo

- Desconto é **por linha**; não há desconto no nível da proposta.
- A transição que tira a Proposal de DRAFT (e um eventual fluxo de revisão de itens em estados posteriores)
  é slice futura — a guarda `requireDraft()` já existe e é testada.
- Sem disponibilidade externa, reserva (booking), custo de fornecedor, margem, imposto ou nota fiscal.
- Sem Sale / Sales Order / Financial / Commission — etapas seguintes do módulo Vendas.

## 7. Próximo prompt sugerido

> Read CLAUDE.md and the current Sprint 3 Sales & Proposals implementation. Continue Sprint 3. Implement
> only the next functional slice: **SLICE 3: Proposal review & internal approval** — move a Draft proposal
> (with items) to **Ready for review** and let an authorized user **Approve** or send it back to **Draft**,
> recording who/when. Out of scope: customer acceptance, sending to the client, sale/order/booking/financial.
> Define the allowed transitions, the gating scope, the audit and the read model, then report the 7 points.
