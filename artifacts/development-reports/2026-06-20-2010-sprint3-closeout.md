# Relatório de encerramento — Sprint 3 (Vendas: Propostas e Pedidos Comerciais)

- **Data:** 2026-06-20
- **Branch:** `feature/sales-sprint3-closeout` (a partir de `develop`)
- **Versão:** 0.36.1 → **0.36.2** (PATCH)
- **Natureza:** encerramento da Sprint 3 (revisão + confirmação dos critérios + handoff p/ Sprint 4 +
  relatório). Mudança de produto **mínima e aditiva**: o detalhe do Pedido passou a **superficiar** o
  contexto comercial (termos, validade, notas) da Proposta de origem **preservada** e um indicador explícito
  de **necessidade de reserva** — **sem** migração e **sem** duplicar dados. **Nenhuma** feature de Sprint 4.

## 1. Status do objetivo do Sprint

**ATINGIDO.** A Sprint 3 entregou o contexto **Vendas — Propostas e Pedidos Comerciais**: da **criação da
proposta** a partir de uma Oportunidade *Pronta para proposta*, passando por **itens**, **totais/descontos/
validade**, **revisão interna** (aprovar/rejeitar), **envio ao cliente** e **decisão do cliente** (aceite/
recusa), até a criação do **Pedido Comercial** do negócio fechado — com **listas**, **detalhes** e
**indicadores** mínimos, tudo com visibilidade por perfil e **validado ponta a ponta**. Um Pedido *Pendente
de reserva* carrega tudo que a Sprint 4 precisa para iniciar a **reserva**, sem recapturar dados, e **nada**
de Booking/Financeiro/Comissão foi criado. Lead, Oportunidade, Proposta e Pedido permanecem **separados**.

## 2. Capacidades concluídas (escopo entregue → onde vive + testes que travam)

| # | Capacidade | Onde vive | Testes que travam |
|---|---|---|---|
| 1 | Bounded context **Sales & Proposals** | `domain.sales` (model/repository/service/service.data/exception) | `ArchitectureTest` + `ModularityTests` |
| 2 | Criar Proposta de uma Oportunidade *Ready* | `POST /api/proposals`, `Proposal.createFromOpportunity` | `ProposalCreationApiIntegrationTest` |
| 3 | **Itens** da proposta (add/editar/remover) | `POST/PUT/DELETE /api/proposals/{id}/items` | `ProposalItemsApiIntegrationTest` |
| 4 | **Totais, desconto e validade** | `PUT /api/proposals/{id}`, `Proposal.updateCommercialDetails` | `ProposalLifecycleApiIntegrationTest` |
| 5 | **Lista** de propostas (busca/filtros) | `GET /api/proposals` + `ProposalSpecifications` | `ProposalListingApiIntegrationTest` |
| 6 | **Detalhe** da proposta | `GET /api/proposals/{id}` (`ProposalDetail`) | `ProposalLifecycleApiIntegrationTest` |
| 7 | **Submeter para revisão** | `POST /{id}/submit`, `Proposal.submitForReview` | `ProposalLifecycleApiIntegrationTest` |
| 8 | **Aprovação / rejeição internas** | `POST /{id}/approve`, `/{id}/reject` | `ProposalApprovalApiIntegrationTest` |
| 9 | **Registrar envio ao cliente** | `POST /{id}/send`, `Proposal.markAsSent` | `ProposalApprovalApiIntegrationTest` |
| 10 | **Aceite / recusa do cliente** | `POST /{id}/accept`, `/{id}/decline` | `ProposalCustomerDecisionApiIntegrationTest` |
| 11 | **Criar Pedido** de Proposta Aceita | `POST /api/orders`, `CommercialOrder.createFromProposal` | `CommercialOrderApiIntegrationTest` |
| 12 | **Lista e detalhe** do Pedido | `GET /api/orders`, `/{id}` (`CommercialOrderDetail`) | `CommercialOrderListingApiIntegrationTest` + `CommercialOrderApiIntegrationTest` |
| 13 | **Indicadores** de Propostas e Pedidos | `GET /api/proposals/indicators`, `/api/orders/indicators` | `Proposal/OrderIndicatorsApiIntegrationTest` |
| 14 | **Validação ponta a ponta** | jornadas (3 fluxos) | `ProposalSprint3JourneyApiIntegrationTest` + `proposal-creation`/`-rejection`/`-customer-rejection` (e2e) |
| 15 | **Handoff funcional p/ Sprint 4** | `artifacts/commercial-order-to-booking-handoff.md` + detalhe do Pedido com contexto comercial | `ProposalSprint3JourneyApiIntegrationTest` (asserts `requiresBooking`/termos/validade) |

Frontend correspondente: criação/itens/totais/validade, detalhe com ações (submeter, aprovar/rejeitar,
enviar, aceitar/recusar, criar pedido), listas de propostas e pedidos, indicadores de vendas e o detalhe do
pedido com o contexto comercial — com gating por escopo; coberto por Vitest + Playwright.

## 3. Status dos critérios de aceite (do enunciado de fechamento)

| Critério | Status | Evidência |
|---|---|---|
| Comportamento da Sprint 3 coerente ponta a ponta | ✅ | jornada backend (3 fluxos) + E2E |
| Pedido pronto p/ Sprint 4 **sem recapturar** dados comerciais | ✅ | detalhe do Pedido: refs + itens/tipos + total + termos/validade/notas (da Proposta) + `requiresBooking` |
| Oportunidade, Proposta e Pedido permanecem **separados** | ✅ | agregados/tabelas distintos; só referências (`opportunityId`/`proposalId`/`leadId`) |
| Nenhuma implementação de **Booking** | ✅ | sem `domain.booking`, sem escopo `booking:*`, sem endpoint de reserva |
| Nenhuma implementação **Financeira** | ✅ | sem `domain.finance`, sem `finance:*`; `payment_notes` é texto descritivo |
| Nenhuma implementação de **Comissão** | ✅ | sem `commission` em domínio/escopo/contrato |
| Nenhuma feature de escopo futuro introduzida | ✅ | asserts `doesNotExist` no contrato; nada novo de Sprint 4 |
| Relatório de encerramento produzido | ✅ | este documento |

### Prova: o que a Sprint 3 **NÃO** implementou (correto)

| Item futuro | Presente no código? |
|---|---|
| Booking (operações de reserva) | **NÃO** (só `PENDING_BOOKING`/`BOOKING_NOT_REQUIRED` como marcador de status) |
| Reserva de Pacote de viagem | **NÃO** (só `ProposalItemType.TRAVEL_PACKAGE` como tipo de item) |
| Reserva de Locação de veículo | **NÃO** (só `ProposalItemType.CAR_RENTAL` como tipo de item) |
| Integração de reserva | **NÃO** |
| Finance / Receivable / Payment | **NÃO** (só `payment_notes` descritivo na Proposta) |
| Commission | **NÃO** |
| Customer Care | **NÃO** (só perfil de acesso) |
| Refund | **NÃO** |
| Cancelamento operacional pós-venda | **NÃO** (`CANCELLED` é valor de enum reservado, **sem** ação/endpoint) |

## 4. Defeitos / lacunas encontrados

- **Nenhum defeito de comportamento de produto** em todo o fechamento. As guardas da máquina de estados e a
  preservação de dados já estavam corretas (confirmado na validação da Slice 13 e reconfirmado aqui).
- **Consistência aprimorada (não é correção de bug):** o detalhe do Pedido **não exibia** os termos
  comerciais, a validade e as notas (embora já preservados via a Proposta de origem imutável) nem um campo
  explícito de necessidade de reserva. Esta slice **superficia** esses dados no detalhe (lendo da Proposta
  que o serviço já carrega) — **sem migração e sem duplicar dados** — deixando o Pedido pronto p/ a Sprint 4.
- **1 teste E2E intermitente** (`proposal-rejection`, timing do `<p-select>` do motivo de rejeição) — passa
  no retry (`retries:1`); não relacionado a esta entrega.

## 5. Riscos

- **Necessidade de reserva** permanece **derivada** do `status` (sem coluna nova); `requiresBooking` é só a
  exposição explícita disso. A Sprint 4 deve tratar a reserva por **tipo de item** (Pacote/Locação).
- O contexto comercial (termos/validade/notas) é **lido da Proposta imutável**, não copiado — uma Proposta
  Aceita é terminal, então o dado é estável; se a Sprint 4 precisar de independência total, decidir lá.
- E2E de jornada longa é sensível a timing (toasts/animação de selects), mitigado com atalhos de teclado e
  `test.slow()`; `retries:1` cobre transitórios.

## 6. A Sprint 4 pode começar?

**SIM.** A Sprint 3 está coerente e verde, o Pedido *Pendente de reserva* preserva/superficia os dados
necessários para iniciar a reserva sem recapturar, e nada bloqueante ficou em aberto.

**Snapshot de fechamento (gates):** `./mvnw verify` **verde (471 testes, Postgres real)**; `ng test`
**verde (329)**; `ng build` **verde**; **E2E `playwright test` verde (44, stack isolada)**.

## 7. Primeira tarefa recomendada para a Sprint 4

> *Sprint 4 / Slice 1: iniciar as **Operações de Reserva (Booking)** a partir de um **Pedido Comercial
> `PENDING_BOOKING`** — novo bounded context `domain.booking` (agregado, escopos próprios `booking:*`,
> migração Flyway, tela), permitindo **abrir/registrar** a reserva dos itens que a exigem
> (`TRAVEL_PACKAGE`/`CAR_RENTAL`) e atualizando o status do Pedido conforme. Precondição de domínio a partir
> do Pedido pronto (`orderId`), conforme o `commercial-order-to-booking-handoff.md`. Decidir com o dono:
> granularidade (reserva por item vs por pedido), estados da reserva e efeito no status do Pedido. Incluir
> testes (unit + integração real + e2e) e relatório. Ainda **sem** integração com fornecedor, Financeiro,
> Receivable, Payment ou Comissão.*
