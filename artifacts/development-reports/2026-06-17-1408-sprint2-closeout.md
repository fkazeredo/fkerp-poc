# Relatório de encerramento — Sprint 2 (Comercial / CRM: Oportunidades comerciais)

- **Data:** 2026-06-17
- **Branch:** `feature/crm-sprint2-validation` (a partir de `develop`)
- **Natureza:** encerramento da Sprint 2 (validação ponta a ponta + documentação de entrega). Código de
  produto **inalterado** nesta entrega; as adições são o teste de jornada, o E2E de jornada, a correção
  de um seletor de E2E (regressão da Slice 11), a release note, o manual e este relatório.

## 1. Status do objetivo do Sprint

**ATINGIDO.** A Sprint 2 entregou o ciclo operacional de **Oportunidades comerciais** — da criação a
partir de um Lead **qualificado** até a etapa **Pronta para proposta** (ou **Perda**), com pipeline em
funil estrito, atividades comerciais, edição de dados comerciais, lista/filtros, pendências e
indicadores — tudo com visibilidade por perfil, e **validado ponta a ponta** (Slice 12). Uma Oportunidade
**Pronta para proposta** carrega tudo que a Sprint 3 precisa para gerar a **proposta**, sem recapturar
dados, e **nada** de Proposta/Venda/Pedido/Booking/Financeiro/Comissão foi criado.

## 2. Capacidades concluídas (escopo entregue → onde vive + testes que travam)

| # (slice) | Capacidade | Onde vive | Testes que travam |
|---|---|---|---|
| 1 | Criar Oportunidade de um Lead qualificado | `POST /api/opportunities`, `Opportunity.createFromLead` | `OpportunityCreationApiIntegrationTest` |
| 2 | Lista operacional (exclui LOST por padrão) | `GET /api/opportunities` (paginado) | `OpportunityListApiIntegrationTest` |
| 3 | Busca e filtros | `OpportunitySearchCriteria` + `OpportunitySpecifications` | `OpportunityListApiIntegrationTest` |
| 4 | Detalhe + marcar como **Perdida** (com motivo) | `GET /{id}`, `POST /{id}/lose`, `Opportunity.markLost` | `OpportunityDetailApiIntegrationTest` |
| 5–6 | Pipeline em **funil estrito** (1 passo p/ frente) | `POST /{id}/stage`, `OpportunityStage.canAdvanceTo` | `OpportunityStageApiIntegrationTest` |
| 7 | **Atividades comerciais** (histórico append-only) | `POST /{id}/activities`, `Opportunity.recordActivity` | `OpportunityActivityApiIntegrationTest` |
| 8 | **Editar dados comerciais** (valor/fechamento/produto/notas) | `PUT /{id}`, `Opportunity.updateCommercialDetails` | `OpportunityDetailsUpdateApiIntegrationTest` |
| 9 | **Motivos de perda** próprios da Oportunidade (enum) | `OpportunityLossReason` (V19) | `OpportunityDetailApiIntegrationTest` |
| 10 | **Oportunidades pendentes** (worklist) | `GET /api/opportunities/pending` | `OpportunityPendingApiIntegrationTest` |
| 11 | **Indicadores** (volume no período + pipeline atual) | `GET /api/opportunities/indicators`, `OpportunityIndicatorQueries` | `OpportunityIndicatorsApiIntegrationTest` |
| 12 | **Validação ponta a ponta** | jornadas | `OpportunitySprint2JourneyApiIntegrationTest` + `opportunity-journey.spec.ts` |

Frontend correspondente: lista com filtros, detalhe com ações (avançar estágio, registrar atividade,
editar, perder), **Oportunidades pendentes** e **Indicadores de oportunidades**, com gating por escopo;
coberto por Vitest + Playwright.

## 3. Status dos critérios de aceite (do enunciado da Slice 12)

| Critério | Status | Evidência |
|---|---|---|
| Fluxo principal ponta a ponta | ✅ | jornada backend + E2E |
| Fluxo alternativo (Perda) ponta a ponta | ✅ | jornada backend + E2E |
| Nenhum passo depende de planilha/dado externo | ✅ | tudo via API/UI |
| Visibilidade se mantém no fluxo | ✅ | 403 financeiro / representante não-dono; gerente vê |
| "Pronta para proposta" suficiente p/ Sprint 3 | ✅ | detalhe (valor/previsão/interesse/Lead/estágios/atividades) |
| Perdida acessível, fora da lista padrão | ✅ | exclusão + filtro Perdida |
| Nenhuma feature de escopo futuro introduzida | ✅ | asserts `doesNotExist`; nada novo no produto |

### Prova: o que a Sprint 2 **NÃO** implementou (correto)

| Item futuro | Presente no código? |
|---|---|
| Proposal | **NÃO** |
| Sale | **NÃO** |
| Sales Order | **NÃO** |
| Booking | **NÃO** |
| Finance (módulo) | **NÃO** (só o usuário-semente `financeiro` p/ controle de acesso) |
| Commission | **NÃO** |
| Conversão de Customer | **NÃO** |

## 4. Defeitos / lacunas encontrados

- **1 regressão de teste corrigida:** o seletor da sidebar em `lead-indicators.spec.ts` ficou ambíguo
  após a Slice 11 adicionar o item **"Indicadores de oportunidades"** (ambos os rótulos contêm
  "Indicadores"). Corrigido com seletor por **href**. Detalhe na Slice 12 (relatório de validação).
- **Nenhum defeito de comportamento de produto.** As duas jornadas passaram sem alterar código de
  produção.

## 5. Riscos

- Indicadores são **ponto-no-tempo** (sem série histórica); o volume é por `createdAt` e o pipeline é um
  retrato atual — já documentado.
- "Pronta para proposta" sinaliza prontidão; a **proposta** em si é da Sprint 3.
- A jornada E2E roda como um ator; a visibilidade multi-ator no fluxo é coberta no backend.

## 6. A Sprint 3 pode começar?

**SIM.** A Sprint 2 está coerente e verde, a Oportunidade Pronta para proposta preserva os dados
necessários e nada bloqueante ficou em aberto.

**Snapshot de fechamento (gates):** `./mvnw verify` **verde (291 testes, Postgres real)**; `ng test`
**verde (131)**; `ng build` **verde**; **E2E `playwright test` verde (30, stack isolada)** — executado
nesta Slice 12.

## 7. Primeira tarefa recomendada para a Sprint 3

> *Sprint 3 / Slice 1: gerar uma **Proposta** comercial a partir de uma Oportunidade `READY_FOR_PROPOSAL`
> — entidade Proposal separada (referenciando `opportunityId`), precondição de domínio a partir da
> Oportunidade pronta, novo escopo `crm:proposal:*`, migração Flyway, validação em profundidade (§5.5),
> erros/i18n padronizados, honrando as read tiers. Incluir testes (unit + integração real + e2e) e
> relatório. Ainda **sem** Venda/Pedido/Booking/Financeiro/Comissão/Cliente.*
