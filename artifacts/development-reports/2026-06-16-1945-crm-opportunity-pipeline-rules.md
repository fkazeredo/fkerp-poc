# Relatório — Sprint 2 / Slice 6 (CRM2-006): Mover Oportunidade pelo pipeline (funil estrito)

- **Data:** 2026-06-16
- **Branch:** `feature/crm-opportunity-pipeline-rules` (a partir de `develop`)
- **Versão:** **0.18.0** (mudança de regra/feature → MINOR; bump por entrega, `CLAUDE.md` §14). **Sem
  release note** (regra de cadência: só no fim de uma entrega completa/sprint).
- **Escopo entregue:** endurecer a movimentação de estágio para um **funil estrito para frente**
  (`Nova → Descoberta → Aderência → Pronta p/ proposta`, um passo por vez), **substituindo** o movimento
  livre da Slice 5 — decisão do dono confirmada no planejamento (reconciliação explícita, `CLAUDE.md`
  §2). Todo o resto que a story pede (histórico, quem/quando, visibilidade/operação, `LOST` terminal, sem
  criar Proposta/Venda/Booking/Financeiro/Cliente) **já existia** desde a Slice 5.

## 1. O que foi implementado

- **Domínio:**
  - `OpportunityStage` virou **enum-com-comportamento**: mapa estático do próximo estágio +
    `canAdvanceTo(target)` — só o passo imediato à frente é permitido (de `LOST` → false; `→ LOST` →
    false; mesmo/voltar/pular → false).
  - `Opportunity.moveToStage(target, byUser)` passou a delegar a regra ao enum
    (`if (!stage.canAdvanceTo(target)) throw OpportunityStageTransitionException`); registro da
    movimentação inalterado. A perda (`markLost`) segue gravando `→ LOST`.
- **Sem mudança** em controller/serviço/segurança/migração: `POST /api/opportunities/{id}/stage` e
  `/lose`, o escopo `crm:opportunity:update`, o read model `stageHistory`, o `HttpErrorMapping` (422) e a
  tabela `opportunity_stage_changes` já existiam (Slice 5). A regra de adjacência é **de domínio**, não de
  schema.
- **Frontend:** o detalhe reflete o funil — mapa `NEXT_STAGE`, `canChangeStage()` exige ter próximo
  (some em `Pronta p/ proposta`/`Perdida`), `stageOptions()` oferece **apenas o próximo** estágio (pré-
  selecionado), e a ação/diálogo foi renomeada para **"Avançar estágio"** com nota de "um passo por vez".

## 2. Regras funcionais cobertas

Transições permitidas via `/stage`: **Nova→Descoberta→Aderência→Pronta p/ proposta** (um passo). **`→
Perdida`** continua só pela ação de perder (com motivo), de qualquer estágio ativo (inclui Pronta p/
proposta). **Bloqueadas:** voltar, pular, `→ Perdida` via `/stage`, a partir de `Perdida` (terminal),
mesmo estágio → **422**. Histórico preservado com **quem/quando** (Slice 5). Só opera quem **vê** a
Oportunidade e tem `crm:opportunity:update` (representante só as próprias). **Pronta p/ proposta** não
cria Proposta. Estágios não implicam Venda/Booking/Financeiro/Cliente.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Avançar pelas transições permitidas → `advancesThroughTheWholeFunnel`,
  `movesToAnActiveStageAndRecordsTheMovement`, `representativeMovesOwnOpportunity`.
- Transições bloqueadas rejeitadas → `rejectsMovingBackward`, `rejectsSkippingAStage`,
  `rejectsMovingToLostThroughTheStageEndpoint`, `rejectsMovingAStageFromLost`, `rejectsMovingToTheSameStage`
  (todos 422) + `rejectsAnUnknownStageValue`/`rejectsStageChangeWithoutAStage` (400).
- Histórico preservado → `advancesThroughTheWholeFunnel` (3 entradas) + `losingRecordsTheMovementToLost`.
- Pronta p/ proposta não cria Proposta → nenhum código de Proposta; `LOST` não volta para ativo →
  `rejectsMovingAStageFromLost`.
- Visibilidade/operação → `rejectsStageChangeWithoutTheUpdateScope` (403),
  `representativeCannotMoveAnotherUsersOpportunity` (403).
- Comportamento existente intacto → suítes de criação/lista/detalhe verdes.

## 4. Arquivos alterados

- **Backend (editados):** `model/OpportunityStage` (+`canAdvanceTo`/mapa), `model/Opportunity`
  (`moveToStage` usa o enum), testes `OpportunityStageApiIntegrationTest` (substitui "free back" por
  backward/skip + funil completo; corrige o passo do representante) e `OpportunityTest` (idem na
  entidade).
- **Frontend (editados):** `features/opportunities/opportunity-detail/` (`.ts`/`.html`/`.spec.ts`) —
  funil para frente + "Avançar estágio".
- **Docs/contrato:** `CLAUDE.md` §10; `application.yml` + `compose.yaml` (0.17.0 → 0.18.0); manuais
  `en-US`/`pt-BR` (§8.3); este relatório. **`.env`/`.env.example`:** `APP_VERSION=0.18.0` a ajustar pelo
  dono (gestão manual; os defaults já resolvem 0.18.0).

## 5. Testes / validações

- **Backend:** `./mvnw verify` **BUILD SUCCESS — 237 testes verdes** (Postgres real via Testcontainers),
  incl. `OpportunityStageApiIntegrationTest` (13), `OpportunityTest` (9), ArchUnit, Modulith e completude
  do `HttpErrorMapping`; Spotless + Checkstyle.
- **Frontend:** `ng test` **verde (112)**. `ng build` (produção) verde.
- **E2E:** **não reexecutado** — sem mudança de spec E2E; a regra é coberta por integração + componente.

## 6. Lacunas conhecidas

- **Não há voltar nem pular:** correção de estágio fora do fluxo previsto não é suportada (fora de
  escopo). `Pronta p/ proposta` só sai para `Perdida` (via perder) nesta sprint.
- **Histórico de atividades comerciais** (`activities`) e `nextActionDate` seguem reservados/vazios —
  fatia futura.
- A movimentação vive **no detalhe** (sem ação rápida na lista).

## 7. Próximo prompt recomendado

> **Sprint 2 / Slice 7: Atividades comerciais da Oportunidade.** Registrar interações/atividades (tipo,
> data, anotação, próxima ação) preenchendo as seções reservadas `activities`/`nextActionDate` do detalhe
> e as colunas reservadas da lista, gated por `crm:opportunity:update` + `canSee`. Bump → 0.19.0. Ao
> encerrar a Sprint 2, escrever a **release note consolidada** (regra de cadência).
