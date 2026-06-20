# Relatório — Sprint 3 / Slice 11: Lista e consulta operacional de Pedidos Comerciais

- **Data:** 2026-06-20
- **Branch:** `feature/sales-order-listing` (a partir de `develop`)
- **Versão:** 0.34.0 → **0.35.0** (MINOR — nova funcionalidade)
- **Escopo entregue:** a **lista operacional de Pedidos Comerciais** (endpoint filtrável + tela no módulo Vendas)
  para o gerente/usuário comercial acompanhar os negócios fechados, com **visibilidade por perfil**. Introduz o
  **número sequencial amigável** do pedido (**`PC-000n`**) e enriquece o detalhe com o número + a nota do próximo
  passo. **Nada** de Reserva/Financeiro/Comissão.

## 1. O que foi implementado
**Backend — número do pedido:**
- **`V28__commercial_order_number.sql`**: sequence `commercial_order_number_seq` + coluna `number` (backfill +
  NOT NULL + UNIQUE). **`CommercialOrder.number`** (atribuído por `createFromProposal(p, byUser, number)`);
  **`CommercialOrderRepository.nextOrderNumber()`** (nativo); **`CommercialOrderService.create`** consome o
  `nextval`; **`CommercialOrderDetail`** + `number`.

**Backend — lista:**
- **`CommercialOrderListItem`** (number, id, proposalId, **proposalTitle** [resumo], opportunityId/Name,
  status, responsibleId/Name, unassigned, total, **requiresBooking** [= PENDING_BOOKING], createdAt).
- **`CommercialOrderSearchCriteria`** + **`CommercialOrderSpecifications`** (filtros: status [default exclui
  CANCELLED], responsável/unassigned, período de criação, faixa de valor, **necessidade de reserva**
  [`BookingNeed` → status], **busca `q`** no título da Proposta via subquery). **`OrderAccessPolicy.visibleTo`**
  (tiers de leitura). **`CommercialOrderService.list`** (resolve responsável + título da proposta + nome da
  oportunidade). **`CommercialOrderListParams`** + **`CommercialOrderController.list`** (`GET /api/orders`, já
  gated por `ORDER_READ_SCOPES`).

**Frontend:**
- `order.service`: tipos `CommercialOrderListItem`/`OrderFilters`/`BookingNeed`, `list(...)`, `responsibles()`,
  `+ number` no detalhe.
- **`features/orders/order-list/`** (novo, espelha `proposal-list`): tabela paginada com **Identificador**
  (`PC-000n`, link), **Resumo** (título da proposta), **Oportunidade** (link), **Responsável**, **Total**,
  **Status** (tag), **Reserva** (indicador *Exige reserva* / *Não exige*), **Criado em**; filtros status /
  necessidade de reserva / responsável / criação de‑até / valor mín‑máx / busca.
- Rota `/pedidos` (orderReadGuard); **nav "Pedidos"** no módulo Vendas (sidebar + home + paleta, gated por
  `canSeeOrders`); atalho **`g d`** → /pedidos (shell + overlay `?`). `order-detail`: cabeçalho **`Pedido
  PC-000n`** + linha **"Próximo passo"** (PENDING_BOOKING → "a próxima etapa pode iniciar as operações de
  reserva").

## 2. Regras funcionais cobertas
- A lista respeita as **regras de visibilidade**: **representantes veem só os próprios** pedidos; **gerentes
  veem todos** (aplicado por query — nenhum filtro burla). **Filtros** exigidos: status, responsável, período de
  criação, faixa de valor, **necessidade de reserva**. Pedidos **Pendentes de reserva** são identificáveis
  (status + indicador). CANCELLED excluído por padrão.
- A lista e o detalhe expõem **só dados do pedido** — **nada** de Booking, Receivable/Payment, Commission ou
  Customer Care.

## 3. Critérios de aceite cobertos
Autorizado lista ✓ · autorizado abre o detalhe ✓ · representante vê só os próprios ✓ · gerente vê todos ✓ ·
filtros funcionam por escopo ✓ · Pendentes de reserva identificáveis ✓ · nada de Booking/Finance/Commission ✓ ·
criação de Proposta/Pedido preservada ✓.

## 4. Arquivos alterados (principais)
- **Backend (novos):** `model/BookingNeed.java`, `service/CommercialOrderSpecifications.java`,
  `service/data/{CommercialOrderListItem,CommercialOrderSearchCriteria}.java`,
  `api/dto/CommercialOrderListParams.java`, `db/migration/V28__commercial_order_number.sql`,
  `test/.../CommercialOrderListingApiIntegrationTest.java`. **(editados):** `model/CommercialOrder.java`
  (number + factory), `repository/CommercialOrderRepository.java`, `service/CommercialOrderService.java`
  (create + list), `service/OrderAccessPolicy.java` (visibleTo), `service/data/CommercialOrderDetail.java`
  (number), `api/CommercialOrderController.java` (list), `application.yml` (0.35.0); testes
  `CommercialOrderTest`, `CommercialOrderApiIntegrationTest` (number).
- **Frontend (novos):** `features/orders/order-list/{ts,html,css,spec}`. **(editados):** `core/api/order.service.ts`,
  `app.routes.ts`, `core/navigation/navigation.ts` (+spec), `core/layout/shell.{ts,html}` (+spec),
  `features/orders/order-detail/{ts,html,spec}`; e2e `order-listing.spec.ts` (novo).
- **Docs:** manual en-US + pt-BR (9.9), este relatório.

## 5. Testes / validações
- **Backend `./mvnw verify`: 450 verdes** (ArchUnit/Modulith + Testcontainers + V28; Spotless/Checkstyle limpos).
  Novo `CommercialOrderListingApiIntegrationTest` (8): contrato das colunas (sem Booking/Finance/Commission);
  representante só os próprios / gerente todos; filtros responsável / necessidade de reserva / faixa de valor;
  CANCELLED oculto por padrão e visível quando filtrado; financeiro → 403; não autenticado → 401; cada item traz
  o `number`. `CommercialOrderTest`/`CommercialOrderApiIntegrationTest` conferem o número.
- **Frontend `ng test`: 310 verdes** (order-list: lazy load, filtros aplica/zera, responsáveis, `orderCode`,
  status, erro, DOM das colunas + indicador de reserva; order-detail: `PC-000n` + próximo passo; navigation:
  Pedidos só com `canSeeOrders`). **`ng build`** verde.
- **E2E `npm run e2e`:** novo `order-listing` — a lista carrega com as colunas e é acessível por **Vendas →
  Pedidos**; um pedido criado pela jornada real aparece na lista, filtra por necessidade de reserva, e o
  **representante não vê** o pedido do gerente (`test.slow`).
- Pilha dev recriada; **V28 aplicada**; `/api/version` = 0.35.0.

## 6. Gaps conhecidos
- A lista/detalhe expõem **só dados do pedido** — Booking/Receivable/Payment/Commission/Customer Care não
  existem ainda. O status PENDING_BOOKING + o indicador apenas **sinalizam** que a próxima sprint pode iniciar a
  reserva.
- **Necessidade de reserva** é derivada do `status` (sem coluna separada); o filtro mapeia para o status.
- **`CANCELLED`** segue no enum/índice mas **sem ação de cancelar** o pedido nesta slice.
- **Busca `q`** (título da Proposta) é um extra de usabilidade além dos 5 filtros exigidos (paridade com a lista
  de Propostas).

## 7. Próximo prompt recomendado
> **SLICE 12: Iniciar as Operações de Reserva (Booking) a partir de um Pedido Pendente de reserva.** Criar o
> conceito de **Reserva (Booking)** ligado ao Pedido — provavelmente um novo bounded context `domain.booking`
> (ou `domain.operations`) com seu agregado, escopos próprios (`booking:*`) e tela — permitindo **abrir/registrar**
> a reserva dos itens que exigem reserva (Pacote/Locação), mudando o status do Pedido conforme. Decidir com o dono:
> granularidade (reserva por item vs por pedido), estados da reserva, e o efeito no status do Pedido. **Ainda sem**
> financeiro/comissão; cobrir feliz + tristes e manter todo o ciclo Proposta→Pedido→lista funcionando.
