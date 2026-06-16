# Relatório — Sprint 2 / Slice 5 (CRM2-005): Pipeline comercial mínimo (movimentação de estágio)

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-pipeline` (a partir de `develop`)
- **Versão:** **0.17.0** (feature nova → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem release note**
  (regra de cadência: só no fim de uma entrega completa/sprint).
- **Escopo entregue:** **mover a Oportunidade entre os estágios ativos** do pipeline e **registrar o
  histórico** dessas movimentações (decisão do dono: movimento livre entre ativos + histórico agora). O
  enum, o início em `NEW_OPPORTUNITY`, `LOST` fora da lista e a perda já existiam (Slices 1–4). **Não**
  cria Proposta, probabilidade, scoring, pipeline configurável, múltiplos pipelines nem forecast.

## 1. O que foi implementado

- **Domínio (`domain.crm`):**
  - `OpportunityStageChange` (novo `@Entity`) — entrada de histórico (de/para/quando/quem), parte do
    agregado, espelhando `LeadAssignment`.
  - `Opportunity`: coleção `stageChanges` (`@OneToMany` cascade ALL + orphanRemoval por `opportunity_id`,
    como o Lead) + `moveToStage(target, byUser)` — movimento **livre** entre estágios ativos; rejeita
    sair de `LOST` (terminal), ir para `LOST` (use a perda) ou ir para o mesmo estágio →
    `OpportunityStageTransitionException`; registra cada movimentação. `markLost(...)` passa a **também**
    registrar a movimentação `→ LOST`.
  - `OpportunityStageTransitionException` (novo, `opportunity.invalid-stage-transition`) → **422**.
  - `OpportunityService.changeStage(id, target, userId, canSeeAll, canSeeUnassigned)` (`@Transactional`):
    `loadVisible` → `moveToStage` → `saveAndFlush` → detalhe refrescado.
  - Read model `OpportunityDetail`: `stageHistory` deixa de ser reservado e passa a `List<StageChange>`
    (de/para/quando/quem, mais recente primeiro); a factory recebe o **mapa de nomes** (resolve
    responsável/`lostBy`/`changedBy`). `activities`/`nextActionDate` seguem reservados.
- **Delivery/segurança:** `POST /api/opportunities/{id}/stage` (`@Valid OpportunityStageChangeRequest`,
  `@NotNull OpportunityStage`) → detalhe. `SecurityConfig`: o matcher de `update` passa a cobrir
  `/{id}/lose` **e** `/{id}/stage` (`SCOPE_crm:opportunity:update`). **Nenhum escopo novo** (reusa o
  `crm:opportunity:update` da Slice 4). `HttpErrorMapping` + i18n para a nova exceção (422).
- **Persistência:** `V17__opportunity_stage_changes.sql` — tabela `opportunity_stage_changes`
  (FK→opportunities, `from_stage`/`to_stage` com CHECK no conjunto de estágios, `changed_at` default
  now(), `changed_by`) + índice por `opportunity_id`.
- **Frontend:** `opportunity.service` (`OpportunityStageChange` + `stageHistory` tipado +
  `changeStage(id, stage)`); detalhe ganha a ação **"Mudar estágio"** (atalho `s`, `p-select` dos
  estágios ativos exceto o atual) e passa a **renderizar a movimentação de estágio** (de → para, data,
  por); confirmação reaproveita um helper `act(...)` compartilhado com a perda.

## 2. Regras funcionais cobertas

Toda Oportunidade tem **um estágio atual** (NOT NULL + default). Nova começa em **Nova**. Os estágios
são **operacionais**: movimento livre entre os ativos (Nova/Descoberta/Aderência/Pronta p/ proposta),
ida e volta, cada um registrado no histórico. **Pronta p/ proposta** existe mas **não** cria Proposta.
**Perdida** existe, é **terminal** e é alcançada só pela ação de perder (não vem de uma Proposta).
Estágios **não** implicam Venda/Booking/Financeiro. Visibilidade + escopo (`canSee` + `crm:opportunity:update`)
exigidos em toda transição. Comportamento anterior (criar/listar/filtrar/detalhe/perder) intacto.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Toda Oportunidade tem estágio atual / nova começa em Nova → coberto pelas Slices anteriores +
  `OpportunityCreationApiIntegrationTest` (verde).
- Estágios disponíveis para uso operacional → `movesToAnActiveStageAndRecordsTheMovement`,
  `allowsFreeMovementBackToAnEarlierStage`, `representativeMovesOwnOpportunity`.
- Pronta p/ proposta é só estágio (não cria Proposta) / Perdida não vem de Proposta → não há criação de
  Proposta no código; `rejectsMovingToLostThroughTheStageEndpoint` + a perda continua só via `/lose`.
- Comportamento existente segue funcionando → suítes de criação/lista/detalhe verdes (regressão).
- (Regras) sair de LOST / mesmo estágio → 422; sem `update` → 403; sem ver → 403; `stage` ausente/inválido
  → 400; perder registra a movimentação `→ LOST` → `losingRecordsTheMovementToLost` + unidade.

## 4. Arquivos alterados

- **Backend (novos):** `model/OpportunityStageChange`, `exception/OpportunityStageTransitionException`,
  `application/api/dto/OpportunityStageChangeRequest`, `db/migration/V17__opportunity_stage_changes.sql`,
  teste `OpportunityStageApiIntegrationTest`.
- **Backend (editados):** `Opportunity` (+`stageChanges`/`moveToStage`/`recordStageChange`; `markLost`
  registra), `OpportunityService` (+`changeStage`; `toDetail` resolve `changedBy`),
  `OpportunityDetail` (`stageHistory` real + `from(o,lead,names)`), `OpportunityController`
  (+`POST /{id}/stage`), `HttpErrorMapping`, `messages.properties`, `SecurityConfig`,
  `OpportunityServiceTest` (+2), `OpportunityTest` (+5).
- **Frontend (editados):** `core/api/opportunity.service.ts` (tipo + `changeStage`),
  `features/opportunities/opportunity-detail/` (`.ts`/`.html`/`.css`/`.spec.ts`).
- **Docs/contrato:** `CLAUDE.md` §10; `application.yml` + `compose.yaml` (0.16.0 → 0.17.0); manuais
  `en-US`/`pt-BR` (§8.3); este relatório. **`.env`/`.env.example`:** `APP_VERSION=0.17.0` a ajustar pelo
  dono (gestão manual; os defaults já resolvem 0.17.0).

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 233 testes verdes** (Postgres real via Testcontainers),
  incl. `OpportunityStageApiIntegrationTest` (11), `OpportunityTest` (7), `OpportunityServiceTest` (11),
  ArchUnit, Modulith e completude do `HttpErrorMapping`; Spotless + Checkstyle.
- **Frontend:** `ng test` **verde (111)** — +4 em `opportunity-detail` (stageOptions/canChangeStage/
  confirmStage). `ng build` (produção) verde.
- **E2E:** **não reexecutado** — sem mudança de spec E2E; a movimentação é coberta por integração +
  componente. Stack isolado (porta 4201, Postgres efêmero) segue como caminho do ciclo completo, sem
  tocar o DB de dev.

## 6. Lacunas conhecidas

- **Histórico de atividades comerciais** segue reservado/vazio (`activities`) e `nextActionDate` nulo —
  fatia de atividades futura. Só a **movimentação de estágio** virou real nesta fatia.
- A movimentação de estágio vive **no detalhe** (não há ação rápida na lista).
- Movimento é **livre** entre ativos (sem funil rígido nem regras de ordem) — decisão do dono.

## 7. Próximo prompt recomendado

> **Sprint 2 / Slice 6: Atividades comerciais da Oportunidade.** Registrar interações/atividades na
> Oportunidade (tipo, data, anotação, próxima ação) preenchendo as seções reservadas `activities` e
> `nextActionDate` do detalhe (e `lastActivityAt`/`nextActionDate` da lista), gated por
> `crm:opportunity:update` + `canSee`. Bump → 0.18.0; sem release note (só no fim da Sprint 2); manuais
> atualizados. (Encerrando a Sprint 2, escrever então a **release note** consolidada.)
