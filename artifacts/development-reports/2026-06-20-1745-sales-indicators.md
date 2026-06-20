# Relatório — Sprint 3 / Slice 12: Indicadores mínimos de Propostas e Pedidos Comerciais

- **Data:** 2026-06-20
- **Branch:** `feature/sales-indicators` (a partir de `develop`)
- **Versão:** 0.35.0 → **0.36.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** dar ao gerente comercial uma **visão mínima do fluxo de propostas e dos pedidos fechados**.
  Dois endpoints de **indicadores** (por área) e **duas telas** no módulo Vendas — espelhando 1:1 o padrão de
  **Indicadores de Oportunidades**. **Período + snapshot:** o volume é medido no período por data de criação; as
  cifras de espera/pendência são retratos atuais. **Nada** de Reserva/Financeiro/Comissão; **não** é dashboard
  executivo. **Sem migração** e **sem novo escopo** (os GET já são gated pelos read-tiers).

## 1. O que foi implementado
**Backend — Propostas:**
- **`ProposalIndicators`** (record, `service.data`): volume — `total`, `byStatus`, `byResponsible`,
  `proposedAmount`, `acceptedAmount`, `rejectedCount`; snapshot — `waitingForReview` (READY_FOR_REVIEW),
  `waitingForCustomerDecision` (SENT). Records aninhados `StatusCount`/`ResponsibleCount`.
- **`ProposalIndicatorQueries`** (`repository`, `@Component`, CriteriaBuilder): `countByStatus`,
  `sumTotalByStatus`, `countByResponsible` — cada um reusa a `Specification` de visibilidade + período `[from, to)`.
- **`ProposalService.indicators(userId, canSeeAll, canSeeUnassigned, from, to)`** monta o read model
  (`resolveNames` já existente). **`ProposalController#indicators`** — `GET /api/proposals/indicators`
  (`createdFrom`/`createdTo` ISO → `Instant [from, to)`), reusando os read-tiers de proposta.

**Backend — Pedidos:**
- **`OrderIndicators`** (record): volume — `total`, `totalAmount`, `byResponsible`; snapshot — `pendingBooking`
  (PENDING_BOOKING). **`OrderIndicatorQueries`**: `countByStatus`, `sumTotal`, `countByResponsible`.
- **`CommercialOrderService.indicators(...)`** + **`CommercialOrderController#indicators`**
  (`GET /api/orders/indicators`), reusando os read-tiers de pedido.

**Frontend (duas telas separadas, decisão do dono):**
- `proposal.service`/`order.service`: tipos `ProposalIndicators`/`OrderIndicators` (+ counts aninhados) e
  `indicators(createdFrom?, createdTo?)`.
- **`features/proposals/proposal-indicators/`** e **`features/orders/order-indicators/`** (espelham
  `opportunity-indicators`): datepickers de período (default mês corrente), KPI cards de **período** e de
  **snapshot**, barras CSS (sem lib de gráfico) por status / responsável. Estados loading / erro / vazio.
- Rotas `/propostas/indicadores` (proposalReadGuard) e `/pedidos/indicadores` (orderReadGuard), **antes** das
  rotas `:id`. **Nav** "Indicadores de propostas" / "Indicadores de pedidos" no módulo Vendas (gated por
  `canSeeProposals`/`canSeeOrders`) — cascateia para sidebar + home + **paleta de comandos**. Sem mudança no shell.

## 2. Regras funcionais cobertas
- **Indicadores de Propostas:** total no período; por status; por responsável; valor proposto; valor aceito;
  recusadas; aguardando revisão; aguardando decisão do cliente. **Indicadores de Pedidos:** total no período;
  valor total; pendentes de reserva; por responsável.
- **Visibilidade respeitada:** gerente/diretoria (read:all) veem global; **representante vê só os próprios**
  (mesma `visibleTo` das listas — aplicada na query, nenhum filtro burla). Volume medido no **período por
  criação**; espera/pendência são **snapshots atuais**.
- Os indicadores expõem **só cifras comerciais** — **nada** de Booking, Receivable, Payment, Commission; **não**
  é Executive Reporting (sem revenue/forecast/ROI/fluxo de caixa).

## 3. Critérios de aceite cobertos
Gerente vê Propostas por status ✓ · vê valores proposto e aceito ✓ · vê Pedidos pendentes de reserva ✓ ·
representante vê só os próprios números ✓ · indicadores não expõem dado não autorizado (403 financeiro/sem-escopo,
401 não autenticado) ✓ · nada de Booking/Finance/Commission ✓ · comportamento existente da Sprint 3 preservado ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `service/data/ProposalIndicators.java`, `service/data/OrderIndicators.java`,
  `repository/ProposalIndicatorQueries.java`, `repository/OrderIndicatorQueries.java`; testes
  `domain/sales/{ProposalIndicatorsAssemblyTest,OrderIndicatorsAssemblyTest}.java`,
  `application/api/{ProposalIndicatorsApiIntegrationTest,OrderIndicatorsApiIntegrationTest}.java`.
  **(editados):** `service/ProposalService.java`, `service/CommercialOrderService.java`,
  `api/ProposalController.java`, `api/CommercialOrderController.java`, `application.yml` (0.36.0).
- **Frontend (novos):** `features/proposals/proposal-indicators/{ts,html,css,spec}`,
  `features/orders/order-indicators/{ts,html,css,spec}`; e2e `sales-indicators.spec.ts`. **(editados):**
  `core/api/proposal.service.ts`, `core/api/order.service.ts`, `app.routes.ts`,
  `core/navigation/navigation.ts` (+spec).
- **Docs:** manual en-US + pt-BR (9.10/9.11), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify` verde** (ArchUnit/Modulith + Testcontainers; Spotless/Checkstyle limpos; sem migração
  nova). **+18 testes novos:** os 2 `*AssemblyTest` (montagem volume-período vs snapshot, bucket nulo) e os 2
  `*ApiIntegrationTest` (8 cada): contrato expõe **apenas** os campos do indicador (sem Booking/Finance/Commission);
  gerente global vs **representante só os próprios**; **período estreita o volume mas não o snapshot**; financeiro
  → 403; sem read-scope → 403; não autenticado → 401.
- **Frontend `ng test`: 328 verdes** (+18: proposal-indicators 9, order-indicators 8, navigation +1) — default mês
  corrente, KPI período vs snapshot, labels de status, bucket "Sem responsável", re-fetch ao trocar período, limpar
  → all-time, erro, DOM dos cards + barras. **`ng build`** verde.
- **E2E `sales-indicators`** (leve): Vendas → **Indicadores de propostas** e **Indicadores de pedidos** acessíveis,
  cards e seções renderizam (o comportamento de dados está coberto na integração).
- Pilha dev recriada (`up -d --build`, sem migração); `/api/version` = **0.36.0**.

## 6. Gaps conhecidos
- **Semântica de volume:** "por status", "valor aceito" e "recusadas" são medidos sobre as propostas **criadas no
  período**, agrupadas pelo **status atual** (mesma semântica do `lost` de Oportunidades). Espera/pendência são
  **snapshots ao vivo**, independentes do período (decisão do dono).
- "Total de pedidos no período" conta **todos** os pedidos criados no período — não há ação de cancelar ainda,
  então `CANCELLED` não ocorre na prática.
- Os indicadores **não** são dashboard executivo (sem revenue recognition/forecast/ROI/fluxo de caixa) e expõem
  **só** cifras comerciais.
- **Duas telas separadas** (decisão do dono via AskUserQuestion), em `/propostas/indicadores` e
  `/pedidos/indicadores` — sem store global, sem lib de gráfico (barras CSS).

## 7. Próximo prompt recomendado
> **SLICE 13: Iniciar as Operações de Reserva (Booking) a partir de um Pedido Pendente de reserva.** Criar o
> conceito de **Reserva (Booking)** ligado ao Pedido — provavelmente um novo bounded context `domain.booking` com
> seu agregado, escopos próprios (`booking:*`) e tela — permitindo **abrir/registrar** a reserva dos itens que a
> exigem (Pacote/Locação) e mudando o status do Pedido conforme. Decidir com o dono: granularidade (reserva por
> item vs por pedido), estados da reserva e o efeito no status do Pedido. **Ainda sem** financeiro/comissão; cobrir
> feliz + tristes e manter todo o ciclo Proposta → Pedido → indicadores funcionando.
