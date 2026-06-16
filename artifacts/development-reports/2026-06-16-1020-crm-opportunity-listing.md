# Relatório — Sprint 2 / Slice 2 (CRM2-002): Listagem operacional de Oportunidades

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-listing` (a partir de `develop`)
- **Versão:** **0.14.0** (feature nova → MINOR; bump por entrega, regra do `CLAUDE.md` §14)
- **Escopo entregue:** o endpoint e a tela para o usuário comercial **ver as Oportunidades que pode
  trabalhar**, priorizando negociações. Espelha o padrão da listagem de Lead. **Não** entrega detalhe
  de Oportunidade, transições de estágio, atividades, perda nem dashboard — e **não** expõe
  proposta/venda/pedido/booking/financeiro/comissão (nada disso existe).

## 1. O que foi implementado

- **Domínio (`domain.crm`):** `OpportunityRepository` passa a estender `JpaSpecificationExecutor`.
  Novos `OpportunityAccessPolicy` (cópia da `LeadAccessPolicy`: Specification sobre
  `responsiblePersonId` — own / own+pool / all), `OpportunitySpecifications` (`stageFilter` que exclui
  `LOST` por padrão salvo filtro explícito + `search` por nome/produto/interesse),
  `OpportunitySearchCriteria` e `OpportunityListView` (inclui `lastActivityAt`/`nextActionDate`,
  reservados e nulos). `OpportunityService.list(criteria, pageable, userId, canSeeAll, canSeeUnassigned)`
  combina visibilidade + filtros na consulta e resolve o nome do responsável em lote (sem N+1).
- **Delivery:** `GET /api/opportunities` (`OpportunityController.list`) paginado (default `createdAt`
  desc, size 20), filtros `stage` (repetível) e `q` → `PageResponse<OpportunityListItemResponse>`. O
  controller deriva os tiers de **opportunity** (`crm:opportunity:read:all` / `:read:unassigned`); a
  criação segue usando os tiers de **lead** (visibilidade do lead de origem).
- **Segurança/persistência:** matcher `GET /api/opportunities[/**]` exige qualquer tier de leitura de
  opportunity; `V14__opportunity_read_scopes.sql` semeia os escopos por perfil (comercial=`read:all`;
  vendedor=`read`+`read:unassigned`; representante=`read`; diretor=`read:all`; financeiro=nenhum).
  Sem nova tabela/índice (os índices de `stage`/`responsible_person_id` da V13 cobrem os filtros).
- **Frontend:** `OpportunityService.list` + tipos `OpportunityListItem`/`OpportunityFilters`;
  `AuthService.canSeeOpportunities()`; `opportunityReadGuard`; tela `features/opportunities/opportunity-list`
  (tabela PrimeNG lazy + paginação, filtro de **estágio** + busca debounced, estados de
  carregamento/vazio/erro, rótulos pt-BR dos estágios; o título linka para o **lead de origem**,
  `/leads/{leadId}`, pois ainda não há detalhe de Oportunidade). Rota `/oportunidades`, item de nav
  "Oportunidades" (gated) e entrada na paleta de comandos.
- **Contrato:** `CLAUDE.md` §10 ganhou o **modelo de autorização de Oportunidade (normativo)**,
  espelhando o de Lead (3 tiers de leitura + create; mapa por perfil; `OpportunityAccessPolicy`).

## 2. Regras funcionais cobertas

Oportunidades perdidas **fora** da lista padrão; aparecem **só** com `?stage=LOST`. Visibilidade por
perfil aplicada na consulta (representante = próprias; vendedor = próprias + pool; gerência/diretoria =
todas; financeiro = sem acesso → 403). Filtros e busca **não** furam a visibilidade. A lista mostra
título, lead de origem, responsável, estágio, valor estimado (quando houver), fechamento previsto
(quando houver) e criação; **última atividade** e **próxima ação** entram no contrato já, **nulas** até
a fatia de atividades. Nada de proposta/venda/booking/financeiro.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Usuários autorizados listam Oportunidades → `listsForAuthorizedUserAndExcludesLostByDefault`,
  `managerSeesEveryOpportunity`, `directorConsultsEveryOpportunity`.
- A lista mostra as informações operacionais exigidas → `exposesOnlyCommercialFieldsAndReservesActivityColumns`
  (conjunto exato de campos) + colunas no template/`opportunity-list.spec`.
- Perdidas não aparecem por padrão / aparecem sob filtro → `listsForAuthorizedUserAndExcludesLostByDefault`
  + `showsLostOnlyWhenExplicitlyFiltered`.
- Representante vê só as próprias; gerente vê todas → `representativeSeesOnlyOwnOpportunities`,
  `managerSeesEveryOpportunity` (+ `sellerSeesOwnPlusTheUnassignedPool`, `filtersDoNotBypassVisibility`).
- Sem proposta/venda/booking/financeiro → `exposesOnlyCommercialFieldsAndReservesActivityColumns`.
- Comportamento de Lead intacto → suíte de Lead permanece verde no `verify`.
- (Extras) financeiro → 403 (`financeHasNoAccessToOpportunities`); só `create` não lista
  (`rejectsWithoutAReadScope`); não autenticado → 401 (`rejectsUnauthenticated`); envelope de
  paginação (`paginatesWithEnvelope`).

## 4. Arquivos alterados

- **Backend (novos):** `OpportunityAccessPolicy`, `OpportunitySpecifications`,
  `OpportunitySearchCriteria`, `OpportunityListView`, `application/api/dto/OpportunityListItemResponse`,
  `db/migration/V14__opportunity_read_scopes.sql`, teste `OpportunityListApiIntegrationTest`.
- **Backend (editados):** `OpportunityRepository` (+`JpaSpecificationExecutor`), `OpportunityService`
  (+`list`), `OpportunityController` (+`GET`), `infra/security/SecurityConfig` (matcher GET +
  `OPPORTUNITY_READ_SCOPES`).
- **Frontend (novos):** `features/opportunities/opportunity-list/` (`.ts`/`.html`/`.css`/`.spec.ts`),
  `core/auth/opportunity-read.guard.ts`, `e2e/opportunity-listing.spec.ts`.
- **Frontend (editados):** `core/api/opportunity.service.ts` (+`list`/tipos),
  `core/auth/auth.service.ts` (+`canSeeOpportunities`), `app.routes.ts`, `core/layout/shell.ts`,
  `core/api/opportunity.service.spec.ts`.
- **Docs/contrato:** `CLAUDE.md` §10; `application.yml` + `compose.yaml` (0.13.0 → 0.14.0);
  `artifacts/release-notes/v0.14.0.md`; manuais `en-US`/`pt-BR`; este relatório.

## 5. Testes / validações

- **Backend:** `OpportunityListApiIntegrationTest` — **12 testes, verde** (Postgres real). Suíte
  completa segue passando no `./mvnw verify` (ArchUnit, Modulith, completude do `HttpErrorMapping`).
- **Frontend:** `npm test` **verde (95)** — +7 (2 de `opportunity.service` list, 5 de `opportunity-list`).
  `npm run build` (produção) verde.
- **E2E:** `opportunity-listing.spec.ts` (colunas carregam; criar via lead → aparecer na lista por busca)
  no stack isolado (porta 4201, Postgres efêmero — DB de dev intocado).

## 6. Lacunas conhecidas

- **Sem detalhe de Oportunidade** (CRM2-004): o título linka para o lead de origem.
- `lastActivityAt`/`nextActionDate` **sempre nulos** até a fatia de **atividades** da Oportunidade.
- Filtros desta fatia: **estágio** + **busca**; responsável/origem/data adiados (a visibilidade já
  restringe o conjunto) — Rule Zero.
- Sem ordenação/colunas configuráveis na UI além do default `createdAt` desc.

## 7. Próximo prompt recomendado

> **Sprint 2 / Slice 3 (CRM2-003): Detalhe da Oportunidade.** `GET /api/opportunities/{id}` (visibilidade
> por perfil via `OpportunityAccessPolicy.canSee`, 404/403 no padrão `{code,message,fields}`) + tela de
> detalhe (dados comerciais, link ao lead de origem). Sem transições/atividades ainda. Bump → 0.15.0 +
> release note + manuais.
