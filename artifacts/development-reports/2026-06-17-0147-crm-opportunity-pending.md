# Relatório — Sprint 2 / Slice 10: Pendências operacionais de Oportunidades

- **Data:** 2026-06-17
- **Branch:** `feature/crm-opportunity-pending` (a partir de `develop`)
- **Versão:** 0.21.0 → **0.22.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** um **worklist operacional** (somente leitura) das Oportunidades que precisam de
  ação, para a negociação não estagnar silenciosamente. Espelha o recurso de **pendências de Lead**
  (Slice 9 da Sprint 1). Reaproveita as **read tiers de visibilidade de Opportunity** — sem nova
  autorização, sem migração. Não é dashboard executivo; sem motor de notificação/e-mail/SLA/workflow.

## 1. O que foi implementado

- **Endpoint** `GET /api/opportunities/pending` (paginado, `createdAt` desc), coberto pelo gate de
  leitura existente (`/api/opportunities/**` → qualquer read tier; Finance/HR/IT → 403). O caminho
  literal `/pending` é priorizado pelo Spring sobre `/{id}`. Sem migração, sem DTO de entrada, sem i18n
  no backend.
- **Categorias de pendência** (uma Oportunidade aparece uma vez com todos os motivos que se aplicam),
  em SQL (OR) **E** com a Specification de visibilidade — filtros/visibilidade não furam. Janela de
  estagnação = **14 dias** (constante única, sem config/SLA); "parada em estágio" medida pela **data de
  criação** (proxy simples, sem consultar o histórico de estágio):
  1. `WITHOUT_RECENT_ACTIVITY` — criada há +14d **e** sem atividade comercial nos últimos 14d
     (`NOT EXISTS` em `opportunity_activities` com `occurred_at >= hoje-14d`);
  2. `OVERDUE_NEXT_ACTION` — `next_action_date` vencida;
  3. `STUCK_IN_NEW` — em **Nova** há +14d;
  4. `STUCK_IN_DISCOVERY` — em **Descoberta** há +14d;
  5. `READY_FOR_PROPOSAL` — em **Pronta p/ proposta** (sem prazo; etapa tratada numa Sprint futura);
  6. `EXPECTED_CLOSE_OVERDUE` — `expected_close_date` vencida.
  **LOST** nunca casa (terminal), então sai naturalmente.
- **`OpportunityPendingReasons.of(opportunity, now, today, lastActivityAt)`** é a fonte única dos
  motivos (unit-testada); `OpportunityPendingSpecifications.pending(now, today)` espelha os mesmos
  predicados na query. `today` derivado em UTC (§5.8).
- **`OpportunityActivity.opportunityId`** (mapeamento read-only do FK, espelha `LeadInteraction.leadId`)
  habilita o subquery `NOT EXISTS` da categoria 1 (sem mudança de schema — a coluna já existe).
- **`OpportunityService.pending(...)`** reusa `findLastActivityAt` (lote, sem N+1) e a
  `OpportunityAccessPolicy.visibleTo`; monta o read model `PendingOpportunity` em `service.data`,
  devolvido direto pelo controller via `PageResponse` (sem `*Response` paralelo — §5.4).
- **Frontend**: página `/oportunidades/pendencias` (guarda `opportunityReadGuard`, registrada **antes**
  de `oportunidades/:id`) com tabela em `.surface` — título (link ao detalhe), estágio, responsável,
  valor, fechamento, próxima ação, última atividade e **chips de motivo**; estados loading/empty/erro.
  Entrada na **sidebar** ("Oportunidades pendentes") e na **paleta de comandos**, restritas a quem pode
  ver Oportunidades. Rótulos pt-BR: Sem atividade recente / Próxima ação atrasada / Parada em Nova /
  Parada em Descoberta / Pronta p/ proposta / Fechamento vencido.

## 2. Regras funcionais cobertas

Pendências respeitam a visibilidade (representante vê só as suas; gerente vê todas); é operacional, não
executivo; ajuda a decidir o próximo passo (motivos explícitos); LOST nunca aparece; uma atividade
recente "resgata" uma Oportunidade antiga da categoria 1; sem notificação/e-mail/SLA/workflow e sem
proposta/venda/reserva/financeiro.

## 3. Critérios de aceite cobertos (→ categoria + teste que trava)

- Vejo as Oportunidades que precisam de ação, com o motivo → as 6 categorias;
  `OpportunityPendingApiIntegrationTest.managerSeesEveryPendingCategoryAndExcludesNonPending` +
  `reasonsAreReportedPerOpportunity`.
- Negociação parada por inatividade aparece → `WITHOUT_RECENT_ACTIVITY`; idem (e `OldButActive`/
  `ActiveRecent` excluídas — atividade recente resgata).
- Próxima ação vencida / fechamento vencido aparecem → `OVERDUE_NEXT_ACTION` / `EXPECTED_CLOSE_OVERDUE`.
- Parada cedo no funil aparece → `STUCK_IN_NEW` / `STUCK_IN_DISCOVERY`.
- Vejo apenas o que me compete → visibilidade na query + `representativeSeesOnlyOwnPending` (rep não vê
  as do gerente).
- A visão não expõe Oportunidades não autorizadas → `financeHasNoAccessToPending` (403),
  `rejectsWithoutAReadScope` (só create → 403), `rejectsUnauthenticated` (401).
- LOST nunca pendente → `LostExcluded` excluída + `OpportunityPendingReasonsTest.lostIsNeverPending`.
- Slices anteriores seguem funcionando → suíte completa verde (o `/pending` não conflita com `/{id}`).

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm/model` — `OpportunityPendingReason`, `OpportunityPendingReasons` (novos),
  `OpportunityActivity` (+`opportunityId` read-only); `domain/crm/service` —
  `OpportunityPendingSpecifications` (novo), `OpportunityService` (+`pending`); `domain/crm/service/data`
  — `PendingOpportunity` (novo); `application/api` — `OpportunityController` (+`GET /pending`).
  **Sem migração, sem endpoint de escrita, sem nova exceção, sem novo escopo.**
- **Frontend:** `core/api/opportunity.service.ts` (`pending` + tipos `OpportunityPendingReason`/
  `PendingOpportunity`), `features/opportunities/opportunity-pending/*` (novo), `app.routes.ts` (rota
  guardada antes de `:id`), `core/layout/shell.ts` (nav + comando).
- **Docs/versão:** `CLAUDE.md` §10 (nota da worklist), `application.yml` + `compose.yaml`
  (`0.22.0`), manual bilíngue (en-US + pt-BR, seção "Oportunidades pendentes").

## 5. Testes / validações adicionados

- **Backend unit `OpportunityPendingReasonsTest`** (10): cada categoria isolada/combinada; LOST nunca
  pendente; recém-criada sem motivo; atividade recente resgata da estagnação.
- **Backend integração (Postgres real) `OpportunityPendingApiIntegrationTest`** (7): gerente vê todas as
  categorias com `reasons` corretos e exclui não-pendentes (recente/ativa-antiga/LOST); representante só
  as próprias; envelope de paginação; finance 403; só-create 403; não autenticado 401.
- **Frontend unit (Vitest) `opportunity-pending.spec.ts`** (3): carrega via lazy-load; mapeia rótulos de
  motivo/estágio pt-BR; estado de erro amigável.
- `cd backend && ./mvnw verify` **verde: 280 testes** (ArchUnit/Modulith/Checkstyle/Spotless +
  completude do `HttpErrorMapping`); `cd frontend && npx ng test --watch=false` **verde (124)** e
  `npx ng build` **verde**.

## 6. Gaps conhecidos

- **Janela única de 14 dias (sem SLA/config):** não há limiar por estágio/perfil; "parada" usa a **data
  de criação** como idade (não consulta o histórico de estágio) — decisão do dono pela simplicidade.
- **Sem filtros/ordenação por motivo** na tela (lista simples, ordenada por criação desc); sem
  notificações/alertas/e-mail.
- Motivos são **ponto-no-tempo** (recalculados a cada carregamento), não persistidos.
- `READY_FOR_PROPOSAL` apenas sinaliza; a geração de proposta é de uma Sprint futura.

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 11: enriquecer as Pendências de Oportunidade com ordenação por urgência e filtro por
> motivo; ou iniciar a etapa de **Proposta** a partir de uma Oportunidade `READY_FOR_PROPOSAL`
> (honrando as read tiers e o escopo de operação). Incluir testes (unit + integração real) e relatório.*
