# Relatório — Sprint 2 / Slice 9 (CRM2-009): Marcar Oportunidade como perdida (motivos próprios)

- **Data:** 2026-06-17
- **Branch:** `feature/crm-opportunity-loss-reasons` (a partir de `develop`)
- **Versão:** **0.21.0** (feature/refactor de regra → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem
  release note** (Sprint 2 ainda em andamento).
- **Escopo entregue:** a maior parte da Slice 9 (marcar como perdida; motivo obrigatório + nota opcional;
  quem/quando; sair da lista padrão; buscável por filtro explícito; LOST terminal; não toca o Lead/
  Finance/Booking/Proposal/CustomerCare) **já existia** das Slices 4–6. O que esta fatia adiciona é o
  **vocabulário próprio de motivos de perda da Oportunidade** (10 razões comerciais), **substituindo** o
  reuso do cadastro compartilhado `loss_reasons` do Lead. **Decisão do dono:** modelar como **enum
  próprio** `OpportunityLossReason` (como os enums de atividade da Slice 7).

## 1. O que foi implementado

- **Enum `OpportunityLossReason`** (10): `NO_BUDGET, NO_DECISION, NO_RESPONSE, COMPETITOR_CHOSEN,
  PRODUCT_MISMATCH, PRICE_TOO_HIGH, TRAVEL_CANCELLED, DUPLICATED_OPPORTUNITY, OUT_OF_PROFILE, OTHER`.
- **Refatoração da perda (substitui o FK do cadastro do Slice 4):**
  - `Opportunity.lossReason` passa de `@ManyToOne LossReason` (FK ao cadastro) para coluna enum
    `@Enumerated(STRING)`; `markLost(OpportunityLossReason, byUser, note)`.
  - `OpportunityService.markLost(id, OpportunityLossReason, note, ...)` — deixa de consultar o cadastro
    (some `LossReasonRepository`/`LossReasonNotAvailableException` do serviço); valida na borda (enum
    inválido → 400).
  - DTO próprio `LoseOpportunityRequest(@NotNull OpportunityLossReason reason, @Size note)` — o Lead
    mantém o `LoseRequest` (UUID do cadastro).
  - `OpportunityDetail.LossInfo.reason` passa de rótulo (String) para o **valor do enum** (o front mapeia
    o rótulo, como estágio/atividade).
- **Persistência:** `V19__opportunity_loss_reason_enum.sql` — adiciona `loss_reason VARCHAR(40)` com CHECK
  no conjunto, faz backfill de Oportunidades já perdidas para `OTHER`, troca o CHECK
  `chk_opportunities_lost_has_reason` da coluna FK para a coluna enum e remove `loss_reason_id`. O Lead
  segue no cadastro `loss_reasons` (intocado).
- **Frontend:** tipo `OpportunityLossReason` + `lose(id, reason, note)`; o detalhe usa um mapa
  `LOSS_REASON_LABELS` (pt-BR) — o diálogo de perder mostra as 10 razões (enum, sem `ReferenceService`) e
  o card de Perda mostra o rótulo mapeado.

## 2. Regras funcionais cobertas

Motivo **obrigatório** (enum, validado no DTO + CHECK), nota **opcional**, **quem/quando** gravados.
Perdida **sai da lista padrão** e é **buscável por filtro explícito** (`stage=LOST`). Perdida é
**terminal** (não volta a estágio ativo). Perder **não altera** o Lead nem cria Finance/Booking/Proposal/
CustomerCare. Só opera quem **vê** + tem `crm:opportunity:update`. Comportamento anterior intacto.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Marcar Oportunidade ativa como perdida → `marksAsLostWithAReasonAndShowsLoss` (motivo
  `COMPETITOR_CHOSEN` + nota).
- Motivo obrigatório / nota opcional → `rejectsLoseWithoutAReason` (400);
  `marksAsLostWithAReasonAndShowsLoss` (com nota) e o caso sem nota nas demais suítes.
- Sai da lista padrão / aparece sob filtro → `listsForAuthorizedUserAndExcludesLostByDefault` +
  `showsLostOnlyWhenExplicitlyFiltered` (`OpportunityListApiIntegrationTest`).
- Não volta a estágio ativo → `rejectsMovingAStageFromLost` (Slice 6, mantido).
- Loss aparece no detalhe → `marksAsLostWithAReasonAndShowsLoss` (`$.loss.reason` = `COMPETITOR_CHOSEN`).
- Comportamento existente intacto → suítes de criação/lista/detalhe/estágio/atividades/edição verdes.
- (Extras) já-perdida → 422 (`rejectsLosingAnAlreadyLostOpportunity`); motivo inválido → 400
  (`rejectsLoseWithUnknownReasonValue`); sem `update` → 403; de outro → 403.

## 4. Arquivos alterados

- **Backend (novos):** `model/OpportunityLossReason`, `application/api/dto/LoseOpportunityRequest`,
  `db/migration/V19__opportunity_loss_reason_enum.sql`.
- **Backend (editados):** `Opportunity` (campo enum + `markLost`), `OpportunityService` (drop cadastro),
  `OpportunityController` (DTO), `OpportunityDetail` (`LossInfo.reason` enum); testes
  `OpportunityDetail/Stage/Activity/List/DetailsUpdateApiIntegrationTest` (seeds LOST + posts `/lose`),
  `OpportunityServiceTest`, `OpportunityTest`. (Lead e o cadastro `loss_reasons` **intocados**.)
- **Frontend (editados):** `core/api/opportunity.service.ts` (tipo + `lose`),
  `features/opportunities/opportunity-detail/` (`.ts`/`.html`/`.spec.ts`).
- **Docs/contrato:** `CLAUDE.md` §10; `application.yml` + `compose.yaml` (0.20.0 → 0.21.0); manuais
  `en-US`/`pt-BR` (§8.3); este relatório. **`.env`/`.env.example`:** `APP_VERSION=0.21.0` a ajustar pelo
  dono.

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 263 testes verdes** (Postgres real via Testcontainers,
  incluindo a migração V19), ArchUnit, Modulith e completude do `HttpErrorMapping`; Spotless + Checkstyle.
- **Frontend:** `ng test` **verde (121)**. `ng build` (produção) verde.
- **E2E:** **não reexecutado** — sem mudança de spec E2E; coberto por integração + componente.

## 6. Lacunas conhecidas

- Os motivos de perda da Oportunidade são **enum fixo** (sem cadastro/admin nesta sprint).
- Oportunidades já perdidas (dados pré-existentes) foram **backfilled para OTHER** na migração (os códigos
  do cadastro antigo não mapeavam 1:1).
- Loss continua **append-only**: não há "reverter perda" (LOST é terminal nesta sprint).
- A perda não toca o Lead (sem regra documentada de propagação) — conforme a story.

## 7. Próximo prompt recomendado

> **Encerramento da Sprint 2 — release note consolidada.** A Oportunidade está completa para a sprint
> (criação, lista/filtros, detalhe, perda com motivos próprios, pipeline, atividades e edição comercial).
> Escrever a **release note** da entrega em `artifacts/release-notes/v0.21.0.md`, em linguagem de negócio,
> por capacidade (regra de cadência §14). Em paralelo, **Sprint 3** começa pela **Proposta** a partir de
> uma Oportunidade *Ready for Proposal*.
