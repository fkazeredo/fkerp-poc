# Relatório — Sprint 2 / Slice 11: Indicadores funcionais mínimos de Oportunidades

- **Data:** 2026-06-17
- **Branch:** `feature/crm-opportunity-indicators` (a partir de `develop`)
- **Versão:** 0.22.0 → **0.23.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** uma visão mínima do **pipeline comercial** para o gerente. Espelha os
  **Indicadores de Lead** (`LeadIndicators` + `LeadIndicatorQueries` + `LeadService.indicators` + tela
  `lead-indicators`): agregações por **Criteria GROUP BY** com a **visibilidade aplicada na query**, nomes
  resolvidos em lote, filtro de período por data de criação. **Somente leitura.** Não cria Proposta; sem
  Venda/Pedido/Reserva/Financeiro/Comissão.

## 1. O que foi implementado

- **Endpoint** `GET /api/opportunities/indicators` (params opcionais `createdFrom`/`createdTo` ISO),
  coberto pelo gate de leitura existente (`/api/opportunities/**` → qualquer read tier; Finance/HR/IT →
  403). O caminho literal `/indicators` é priorizado pelo Spring sobre `/{id}`. Sem migração, sem novo
  escopo, sem nova exceção, sem i18n no backend.
- **Decisão do dono (padrão de mercado):** o painel tem **dois escopos**:
  - **Volume — no período** (por data de criação): `total`, `lost`, `byStage`, `byOrigin`,
    `byResponsible` (contagem).
  - **Pipeline — retrato atual** (todas as Oportunidades visíveis, independe do período): `active`
    (não-LOST), `readyForProposal`, `overdueClose`, `activePipelineValue`, `valueByResponsible`.
  A **visibilidade** narrows ambos (representante vê só o próprio pipeline). "Ativa" = não-LOST;
  `estimated_value` nulo conta como 0; "today" em UTC.
- **`OpportunityIndicatorQueries`** (`@Component`, Criteria API, espelha `LeadIndicatorQueries`): helper
  `where(...)` aplica a `Specification` de visibilidade + período em `createdAt`. Queries: `countByStage`
  (chamada 2× — período → volume `byStage`/`total`/`lost`; snapshot `null/null` → deriva `active` =
  soma(não-LOST) e `readyForProposal`), `countByOrigin`, `countByResponsible` (inclui o bucket null),
  `sumActivePipelineValue`, `sumActiveValueByResponsible`, `countActiveOverdueClose`.
- **`OpportunityIndicators`** (`service.data`): record com os dois escopos + records aninhados
  `StageCount`/`OriginCount`/`ResponsibleCount`/`ResponsibleValue` (`responsibleName == null` = não
  atribuído). **`OpportunityService.indicators(...)`** monta tudo, resolve nomes (lote, união das chaves
  dos dois mapas de responsável). Devolvido direto pelo controller (sem `*Response` paralelo, §5.4).
- **Frontend**: tela `/oportunidades/indicadores` (`opportunityReadGuard`, registrada **antes** de
  `oportunidades/:id`): filtro de período (padrão mês atual + "Todo o período"); **dois blocos de KPIs**
  ("Volume no período" e "Pipeline atual", incl. **Pipeline ativo (R$)**) e **quatro quebras** com barras
  CSS (por estágio/origem/responsável + valor por responsável). `null` → "Sem responsável". Rótulos de
  estágio pt-BR. Entrada na **sidebar** e na **paleta de comandos**, gated por `canSeeOpportunities()`.

## 2. Regras funcionais cobertas

Indicadores respeitam a visibilidade (gerente global, representante só os próprios); LOST conta no volume
mas nunca no pipeline ativo/valor; "Prontas p/ proposta" apenas sinaliza (não cria Proposta); só dados
comerciais de pipeline — nada de Venda/Pedido/Reserva/Financeiro/Comissão; sem receita/forecast/ROI.

## 3. Critérios de aceite cobertos (→ teste que trava)

- Gerente vê Oportunidades por estágio / valor do pipeline ativo / prontas p/ proposta / por responsável →
  `OpportunityIndicatorsApiIntegrationTest.managerSeesGlobalVolumeIncludingLost` +
  `managerSeesTheCurrentPipelineSnapshot`.
- Representantes veem só os próprios indicadores → `representativeIndicatorsAreScopedToTheirOwnPipeline`.
- Indicadores não expõem Oportunidades não autorizadas → visibilidade na query + `financeHasNoAccess…`
  (403), `rejectsWithoutAReadScope` (403), `rejectsUnauthenticated` (401).
- Não incluem Proposta/Venda/Reserva/Financeiro/Comissão → `exposesOnlyCommercialIndicatorFields` (o
  contrato JSON é exatamente os 10 campos previstos).
- Comportamento existente das Oportunidades segue funcionando → suíte completa verde (o `/indicators` não
  conflita com `/{id}`).

## 4. Arquivos alterados (principais)

- **Backend:** `domain/crm/repository/OpportunityIndicatorQueries` (novo),
  `domain/crm/service/data/OpportunityIndicators` (novo), `domain/crm/service/OpportunityService`
  (+`indicators`, +`resolveNames`), `application/api/OpportunityController` (+`GET /indicators`).
  **Sem migração, sem endpoint de escrita, sem novo escopo.**
- **Frontend:** `core/api/opportunity.service.ts` (`indicators` + tipos),
  `features/opportunities/opportunity-indicators/*` (novo), `app.routes.ts` (rota antes de `:id`),
  `core/layout/shell.ts` (nav + comando).
- **Docs/versão:** `CLAUDE.md` §10 (nota dos indicadores), `application.yml` + `compose.yaml` (`0.23.0`),
  manual bilíngue (seção "Indicadores de oportunidades").

## 5. Testes / validações adicionados

- **Backend unit `OpportunityIndicatorsAssemblyTest`** (1, mocka as queries): deriva `total`/`lost`/
  `byStage` do período e `active`/`readyForProposal` do snapshot; união/resolução de nomes (null → não
  atribuído); valores e overdue passam direto.
- **Backend integração (Postgres real) `OpportunityIndicatorsApiIntegrationTest`** (8): volume global do
  gerente (incl. LOST), snapshot do pipeline (ativas/prontas/vencidas/valor/valor-por-responsável), o
  período estreita o volume mas **não** o snapshot, representante só os próprios, contrato exato dos
  campos, finance 403, só-create 403, não autenticado 401.
- **Frontend unit (Vitest) `opportunity-indicators.spec.ts`** (7): período padrão mês-a-hoje; KPIs de
  volume e de pipeline; rótulos de estágio pt-BR; `null` → "Sem responsável" (contagem e valor); recarrega
  ao mudar o período; limpa para todo o período; estado de erro.
- `cd backend && ./mvnw verify` **verde** (ArchUnit/Modulith/Checkstyle/Spotless + completude do
  `HttpErrorMapping`); `cd frontend && npx ng test --watch=false` **verde (131)** e `npx ng build`
  **verde**.

## 6. Gaps conhecidos

- **Dois escopos no mesmo painel** (volume no período × pipeline atual) — a UI rotula claramente, mas é
  preciso ler os títulos para não confundir.
- `byStage` (quebra) é **no período** (distribuição das criadas no período pelo estágio atual); o
  funil-snapshot fica implícito nos KPIs `active`/`readyForProposal`.
- Sem gráficos avançados (apenas barras CSS), sem exportação, sem comparação entre períodos.
- Sem receita/fluxo de caixa/booking/comissão/forecast/ROI (fora de escopo).

## 7. Próximo prompt de implementação recomendado

> *Sprint 2 / Slice 12: iniciar a etapa de **Proposta** a partir de uma Oportunidade `READY_FOR_PROPOSAL`
> (honrando as read tiers e um novo escopo de operação), OU enriquecer os Indicadores com comparação entre
> períodos / exportação. Incluir testes (unit + integração real) e relatório.*
