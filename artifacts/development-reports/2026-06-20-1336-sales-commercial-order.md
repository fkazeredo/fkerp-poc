# Relatório — Sprint 3 / Slice 10: Criar Pedido Comercial a partir de uma Proposta aceita

- **Data:** 2026-06-20
- **Branch:** `feature/sales-commercial-order` (a partir de `develop`)
- **Versão:** 0.33.0 → **0.34.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** um usuário comercial autorizado cria um **Pedido Comercial** a partir de uma Proposta
  **Aceita** — um **registro formal interno** do negócio fechado (snapshot dos itens + total + referências +
  responsável). Criar o pedido **marca a Oportunidade de origem como Ganha** (novo estágio terminal `WON`,
  espelhando `LOST`). **Não** cria Reserva, Recebível, Pagamento, Comissão nem Customer Care.

## 1. O que foi implementado
**CRM (mudança cross-area):**
- **`OpportunityStage.WON`** (estágio terminal; `isTerminal()` + `terminalStages()` cobrem WON e LOST). Não
  entra no funil (`canAdvanceTo`), só via `markWon`.
- **`Opportunity.markWon(byUser)`** (espelha `markLost`): registra a transição no histórico de estágio; guard
  `OpportunityCannotBeMarkedWonException` (nova, **422**) se já encerrada (won/lost); `markLost` também passou a
  rejeitar a partir de qualquer terminal. WON é tratado como terminal em todo lugar (lista padrão, pendências,
  pipeline/indicadores "ativos" — agora excluem WON e LOST).

**Sales — agregado novo `CommercialOrder` (`domain.sales`):**
- **`CommercialOrderStatus`** (`PENDING_BOOKING`, `BOOKING_NOT_REQUIRED`, `CANCELLED`; `isActive()` = não
  cancelado). **`CommercialOrderItem`** (snapshot de `ProposalItem`). **`CommercialOrder.createFromProposal(p,
  byUser)`**: guard `p.status()==ACCEPTED` senão **`ProposalNotAcceptedException`** (**422**); copia itens,
  subtotal/total, referências (proposalId/opportunityId/leadId) e responsável; status `PENDING_BOOKING` se algum
  item é `TRAVEL_PACKAGE`/`CAR_RENTAL` (exige reserva), senão `BOOKING_NOT_REQUIRED`.
- **`CommercialOrderRepository`** (`findFirstByProposalIdAndStatusIn` ativo — "um pedido ativo por proposta").
  **`OrderAccessPolicy.canSee`** (tiers de leitura, espelha `ProposalAccessPolicy`). **`CommercialOrderService`**:
  `create(...)` (carrega + valida a proposta visível, guarda duplicidade → **`CommercialOrderAlreadyExistsException`**
  **409**, cria o pedido, **marca a Oportunidade ganha** na mesma transação, publica `CommercialOrderCreated`) e
  `detail(...)`. **`CommercialOrderDetail`** (read model com itens + origem proposta/oportunidade/lead).
- **`CommercialOrderController`**: `POST /api/orders` (gate `sales:order:create`; reusa os tiers de leitura da
  **proposta** para ver a origem) e `GET /api/orders/{id}` (gate `ORDER_READ_SCOPES`). **`SecurityConfig`** +
  `HttpErrorMapping` (5 mapeamentos novos) + i18n (`proposal.not-accepted`, `order.*`, `opportunity.cannot-mark-won`).
- **`ProposalDetail.commercialOrderId`** (o pedido ativo da proposta, resolvido em `toDetail`).
- **Flyway `V27__commercial_orders.sql`**: WON nos CHECK de estágio (opportunities + stage_changes);
  `commercial_orders` + `commercial_order_items` (+ índice único parcial "um ativo por proposta"); seed
  `sales:order:*` (gerente read:all+create; vendedor read+read:unassigned+create; representante read+create;
  diretor read:all; financeiro nenhum).

**Frontend:**
- `order.service` (tipos + `create`/`detail`); `auth.service` (`canCreateOrder`/`canSeeOrders`);
  `proposal.service` (`ProposalDetail.commercialOrderId`).
- `proposal-detail`: botão **Criar pedido comercial** (quando `canCreateOrder()` = escopo + *Aceita* + sem
  pedido) ou link **Ver pedido comercial** (quando já existe); atalho **`o`** + overlay `?`.
- **`features/orders/order-detail/`** (novo, read-only): status, total, itens, e os cards de origem (proposta/
  oportunidade/lead) com links; rota `/pedidos/:id` + `orderReadGuard`. `STAGE_LABELS` de Oportunidade ganham
  **Ganha** em todos os componentes que mostram estágio.

## 2. Regras funcionais cobertas
- Só Proposta **Aceita** gera pedido (senão **422** `proposal.not-accepted`). **Um pedido ativo por proposta**
  (senão **409** `order.already-exists`). O pedido **preserva** proposta, oportunidade, responsável, itens e
  total. Nasce **`PENDING_BOOKING`** com item que exige reserva (pacote/locação), senão `BOOKING_NOT_REQUIRED`.
- Criar o pedido marca a Oportunidade como **Ganha** (estado fechado-ganho do CRM). **Não** cria Reserva,
  Recebível, Pagamento, Comissão; o fechamento da Oportunidade **não** dispara financeiro/reserva.

## 3. Critérios de aceite cobertos
Autorizado cria pedido de proposta aceita ✓ · não-aceitas não geram pedido ✓ · sem pedido ativo duplicado ✓ ·
preserva proposta/oportunidade/responsável/itens/total ✓ · nasce Pendente de Reserva quando exige ✓ ·
Oportunidade marcada como Ganha ✓ · nenhuma reserva ✓ · nenhum recebível ✓ · nenhum pagamento/comissão ✓ ·
comportamento existente da Proposta preservado ✓.

## 4. Arquivos alterados (principais)
- **CRM (novos):** `exception/OpportunityCannotBeMarkedWonException.java`. **(editados):** `OpportunityStage.java`
  (WON + isTerminal/terminalStages), `Opportunity.java` (markWon + markLost), `OpportunitySpecifications`,
  `OpportunityPendingSpecifications`, `OpportunityIndicatorQueries`, `OpportunityService`, `OpportunityPendingReasons`.
- **Sales (novos):** `model/CommercialOrder.java`, `CommercialOrderItem.java`, `CommercialOrderStatus.java`,
  `CommercialOrderCreated.java`, `repository/CommercialOrderRepository.java`, `service/CommercialOrderService.java`,
  `service/OrderAccessPolicy.java`, `service/data/CommercialOrderDetail.java`,
  `exception/{ProposalNotAccepted,CommercialOrderAlreadyExists,CommercialOrderNotFound,CommercialOrderAccessDenied}Exception.java`,
  `api/CommercialOrderController.java`, `api/dto/{CreateOrderRequest,OrderResponse}.java`,
  `db/migration/V27__commercial_orders.sql`. **(editados):** `ProposalService` + `ProposalDetail`
  (commercialOrderId), `SecurityConfig`, `HttpErrorMapping`, `messages.properties`, `application.yml` (0.34.0).
- **Frontend (novos):** `core/api/order.service.ts`, `core/auth/order-read.guard.ts`,
  `features/orders/order-detail/{ts,html,css,spec}`. **(editados):** `auth.service.ts`, `proposal.service.ts`,
  `opportunity.service.ts` (WON), 5 `STAGE_LABELS`, `proposal-detail.{ts,html,spec}`, `app.routes.ts`,
  `shell.html`; e2e `proposal-creation.spec.ts`.
- **Testes (novos):** `CommercialOrderTest`, `CommercialOrderApiIntegrationTest`, `order-detail.spec`,
  `order-read.guard.spec`. **(editados):** `OpportunityTest` (markWon), `ProposalCreationApiIntegrationTest`
  (contrato +commercialOrderId), `proposal-detail.spec`.
- **Docs:** manual en-US + pt-BR (9.8 + nota CRM Ganha), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 442 verdes** (ArchUnit/Modulith + Testcontainers + V27 + `HttpErrorMappingTest`;
  Spotless/Checkstyle limpos). Domínio: `CommercialOrderTest` (snapshot, status por reserva, não-aceita → 422);
  `OpportunityTest` (markWon + histórico; já encerrada → 422; won não pode ser marcado perdido). Integração
  `CommercialOrderApiIntegrationTest` (10): criar → 201 + detalhe preserva tudo + Oportunidade WON +
  `commercialOrderId` na proposta; só-serviço → BOOKING_NOT_REQUIRED; vendedor cria a própria; não-aceita → 422;
  duplicado → 409; diretor não cria mas lê; financeiro 403 (criar e ler); não autenticado → 401.
- **Frontend `ng test`: 299 verdes** (`canCreateOrder` por escopo+status+sem-pedido; createOrder navega; atalho
  `o`; "Ver pedido" quando existe; order-detail render + erros; orderReadGuard). **`ng build`** verde.
- **E2E `npm run e2e`: 39 verdes** — `proposal-creation` agora segue até **Criar pedido comercial** → detalhe do
  pedido (itens/total + origem + Oportunidade *Ganha*) e volta à proposta mostrando "Ver pedido comercial". O
  `proposal-rejection` (pesado, pré-existente) ficou *flaky* e passou no retry (`retries:1`).
- Pilha dev recriada; **V27 aplicada**; `/api/version` = 0.34.0.

## 6. Gaps conhecidos
- **Criar o pedido NÃO** cria Reserva (Booking), Recebível, Pagamento, Comissão nem Customer Care — só o
  registro + marca a Oportunidade Ganha. `PENDING_BOOKING` é **apenas um status** (a reserva real é slice futura).
- **`CANCELLED`** existe no enum (e no índice "um ativo por proposta") mas **não há ação de cancelar**; **sem**
  histórico de status do pedido (sem transições ainda) — só auditoria de criação. **Sem número sequencial**
  (usa o UUID).
- **Sem lista de Pedidos nem menu/paleta "Pedidos"** — o detalhe é acessível pela proposta; é a **próxima** slice.
- **WON** entra no CRM como terminal (como LOST); **sem** novas métricas de "ganho" (vitória/forecast são
  evolução futura).

## 7. Próximo prompt recomendado
> **SLICE 11: Listar e consultar os Pedidos Comerciais (módulo Vendas → Pedidos).** Adicionar a **lista**
> operacional de Pedidos (visibilidade por tier `sales:order:read*`, filtros por status/responsável/busca,
> paginação) e a entrada **"Pedidos"** no módulo Vendas (sidebar + home + paleta + atalho `g`-leader), reusando o
> detalhe já existente. **Sem** ainda Reserva/Financeiro/Comissão. Cobrir feliz + tristes (sem permissão,
> visibilidade por perfil) e manter todo o ciclo Proposta→Pedido funcionando.
