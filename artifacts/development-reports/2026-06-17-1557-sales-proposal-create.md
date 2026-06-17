# Relatório — Sprint 3 / Slice 1: Estabelecer Sales & Proposals + criar Proposal

- **Data:** 2026-06-17
- **Branch:** `feature/sales-proposal-create` (a partir de `develop`)
- **Versão:** 0.23.2 → **0.24.0** (MINOR — nova funcionalidade / novo bounded context)
- **Escopo entregue:** estabelece o contexto **Sales & Proposals** (`com.fksoft.erp.domain.sales`, mesmo
  design de `domain.crm`) e sua primeira capacidade: criar uma **Commercial Proposal** a partir de uma
  Opportunity em `READY_FOR_PROPOSAL`. Honra o handoff `artifacts/opportunity-to-proposal-handoff.md`.

## 1. O que foi implementado

- **Novo bounded context `domain.sales`** (model/repository/service/service.data/exception), espelhando o
  padrão Lead→Opportunity. Agregado **`Proposal`** com factory `createFromOpportunity(...)` (valida
  `READY_FOR_PROPOSAL`, semeia `opportunityId`/`leadId`/responsável/título/notas/validade/termos, nasce
  **DRAFT**); enum **`ProposalStatus`** com os 8 estados do ciclo (só DRAFT usado nesta slice;
  `isOpen()` = não-terminal); evento `ProposalCreated`; `ProposalRepository`; `ProposalAccessPolicy`
  (tiers own/unassigned/all em `responsiblePersonId`); `ProposalService.create/detail/list`; read models
  `ProposalDetail` (+ `SourceOpportunity`) e `ProposalListItem`; 4 exceptions
  (`OpportunityNotReadyForProposalException` 422, `ProposalAlreadyExistsForOpportunityException` 409 com
  detail `proposalId`, `ProposalNotFoundException` 404, `ProposalAccessDeniedException` 403), registradas
  no `HttpErrorMapping`; chaves i18n `proposal.*`.
- **Escopos `sales:proposal:*`** (primeiro prefixo não-`crm:`): `read`/`read:unassigned`/`read:all` +
  `create` + `update`, semeados por perfil na migração e cobertos por novos matchers no `SecurityConfig`.
- **Endpoints** (`/api/proposals`): `POST` (create de uma Opp ready → 201 DRAFT; reusa os read tiers de
  Opportunity p/ ver a origem), `GET` (lista paginada, visibilidade narrowed), `GET /{id}` (detalhe). DTOs
  `ProposalCreateRequest`/`ProposalResponse`.
- **Migração `V20__proposals.sql`**: tabela `proposals` (FKs p/ opportunities e leads, CHECK dos 8 status,
  audit), índices, **índice único parcial** `ux_proposals_active_per_opportunity` (defesa em profundidade
  da regra "uma ativa por Opportunity") e os seeds `sales:proposal:*`.
- **Frontend**: `proposal.service` (create/detail/list + tipos), helpers de auth
  (`canSeeProposals`/`canCreateProposal`/`canOperateProposal`), `proposal-read.guard`, ação **"Criar
  proposta"** no detalhe da Opportunity (gated por `canCreateProposal && stage===READY_FOR_PROPOSAL`;
  diálogo título/validade/responsável/termos/notas → cria → navega ao detalhe + toast), telas
  **proposal-list** (porta do módulo Vendas) e **proposal-detail** (status, oportunidade de origem, lead),
  rotas `propostas`/`propostas/:id`.
- **Divisão de módulos na tela (pedido do dono):** a sidebar passou a ter **grupos** — **Comercial / CRM**
  (Leads + Oportunidades) e **Vendas** (Propostas) — cada um gated pelos seus read scopes; comando na
  paleta.

## 2. Regras funcionais cobertas

Só uma Opportunity `READY_FOR_PROPOSAL` origina proposta (estágios anteriores e LOST → 422); a proposta
preserva a Opportunity de origem, o Lead e o responsável (padrão da Opp); nasce **DRAFT**; **uma ativa por
Opportunity** (409 amigável + índice parcial; nova permitida após terminal-negativo); criar proposta
**não** cria Order/Sale/Booking/Financial/Payment/Commission e **não** altera a Opportunity; visibilidade
respeitada (criar exige ver a Opp de origem; list/detalhe pelos tiers da Proposal).

## 3. Critérios de aceite cobertos (→ teste que trava)

- Cria de uma Opp ready → `ProposalCreationApiIntegrationTest.createsProposalFromReadyOpportunity…` (201
  DRAFT, link à origem) + `ProposalTest`/`ProposalServiceTest`.
- Opp não-ready / LOST não originam → `rejectsCreatingFromANonReadyOpportunity` / `…FromALostOpportunity`
  (422 `proposal.opportunity-not-ready`).
- Começa DRAFT; permanece ligada à Opp; preserva responsável → asserts do detalhe + `ProposalServiceTest`.
- Nenhum Order/Booking/Financial/Payment/Commission → `exposesOnlyCommercialOfferFields` (contrato exato).
- Comportamento Sprint 1/2 segue funcionando → suíte completa verde; o E2E da jornada Sprint 2 foi
  ajustado (a Opp ready agora **oferece** "Criar proposta").
- (Extra) "uma ativa por Opp" → `rejectsASecondActiveProposal…` (409) +
  `allowsANewProposalAfterThePreviousIsCancelled`; visibilidade → `listAndDetailRespectVisibility`,
  finance 403, sem-create 403, não autenticado 401.

## 4. Arquivos alterados (principais)

- **Backend:** `domain/sales/**` (novo: Proposal, ProposalStatus, ProposalCreated, ProposalRepository,
  ProposalAccessPolicy, ProposalService, CreateProposalCommand, ProposalDetail, ProposalListItem, 4
  exceptions); `application/api/ProposalController` + `dto/ProposalCreateRequest`/`ProposalResponse`;
  `infra/web/HttpErrorMapping` (+4); `infra/security/SecurityConfig` (matchers); `messages.properties`
  (+`proposal.*`); `db/migration/V20__proposals.sql`.
- **Frontend:** `core/api/proposal.service.ts`, `core/auth/auth.service.ts` (+helpers),
  `core/auth/proposal-read.guard.ts`, `features/proposals/proposal-list/*` + `proposal-detail/*` (novos),
  `features/opportunities/opportunity-detail/*` (+ação "Criar proposta"), `app.routes.ts`,
  `core/layout/shell.ts` + `shell.html` (sidebar em módulos).
- **Docs/versão:** `CLAUDE.md` §10 (Proposal authorization model + `domain.sales` + divisão de módulos),
  `application.yml` + `compose.yaml` (`0.24.0`), manual bilíngue (nova seção "Propostas").

## 5. Testes / validações adicionados

- **Backend unit `ProposalTest`** (factory: DRAFT + campos; rejeita 4 estágios não-ready) e
  **`ProposalServiceTest`** (8: create feliz, Opp inexistente, não visível, não-ready, 2ª ativa,
  responsável desconhecido, detalhe inexistente/não-visível).
- **Backend integração `ProposalCreationApiIntegrationTest`** (10, Postgres real): create+detalhe,
  não-ready/LOST 422, 2ª ativa 409, nova após CANCELLED, rep não-dono 403, finance 403, sem-create 403,
  não autenticado 401, lista/detalhe por visibilidade, contrato exato.
- **Frontend** (Vitest): `proposal-detail.spec` (4), `proposal-list.spec` (3), `opportunity-detail.spec`
  (+3 da ação "Criar proposta").
- **E2E `proposal-creation.spec.ts`** (Playwright, stack isolada): jornada até uma Opp ready → Criar
  proposta → detalhe DRAFT + link de volta + módulo Vendas/Propostas no menu. Ajustado o
  `opportunity-journey.spec` (a Opp ready agora oferece "Criar proposta").
- `./mvnw verify` **verde: 315** (Postgres real; ArchUnit/Modulith com `domain.sales`; completude do
  `HttpErrorMapping`); `ng test` **verde: 141**; `ng build` **verde**; **E2E `playwright test` verde: 31**.

## 6. Suposições

- Ciclo de 8 estados **definido** (enum + CHECK) mas só **DRAFT** usado; transições e itens/valores/
  descontos/aprovação/aceite e o **pedido comercial** são slices futuras.
- O título da proposta vem pré-preenchido com o nome da Opportunity (editável); o responsável padrão é o da
  Opportunity.
- `domain.sales` reusa, do `domain.crm`, a Opportunity de origem e suas exceptions de acesso/404 — sem
  Facade (domínio internamente aberto, §6).

## 7. Gaps conhecidos

- Lista de Propostas **sem filtros/busca** (mínima, só a porta do módulo); sem ações de ciclo no detalhe
  (read-only nesta slice).
- "Uma ativa por Opp" é checada no service + índice parcial; ainda não há a transição que leva uma proposta
  a terminal-negativo pela UI (feito direto no banco no teste).
- Sem itens/valores/descontos na proposta ainda (a oferta é só título/termos/validade/notas nesta slice).

## 8. Próximo prompt de implementação recomendado

> *Sprint 3 / Slice 2: **itens e valores da Proposal** — adicionar Proposal Items (produto/serviço,
> quantidade, preço, desconto) e o total da proposta, com edição enquanto DRAFT; e/ou a **lista de
> Propostas com filtros** (status, responsável, período). Honrar `sales:proposal:update`, validação em
> profundidade (§5.5), migração Flyway, testes (unit + integração real + e2e) e relatório. Ainda sem
> aprovação/aceite/pedido/venda/booking/financeiro.*
