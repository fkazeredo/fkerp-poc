# Relatório — Sprint 4 / Slice 1: Criar Booking Request de um Pedido Comercial Pendente de reserva

- **Data:** 2026-06-21
- **Branch:** `feature/booking-request-create` (a partir de `develop`)
- **Versão:** 0.36.2 → **0.37.0** (MINOR — novo bounded context)
- **Escopo:** abrir o contexto **Booking Operations** (`domain.booking`) e criar a **Booking Request** a partir de
  um **Commercial Order `PENDING_BOOKING`**, iniciando o processo operacional de reserva (ainda **manual**, sem
  integração). **Backend-only** (decisão do dono); a UI de reserva começa nas slices de lista/detalhe.

## 1. O que foi implementado
**Novo contexto `domain.booking`:**
- **`BookingRequest`** (raiz; espelha `CommercialOrder`): preserva `commercialOrderId`/`proposalId`/`opportunityId`/
  `leadId` + `responsiblePersonId` (responsável comercial) + `bookingOperatorId` (opcional) + `notes`; `status`
  (`BookingRequestStatus`); `items` (`@OneToMany`). Factory `createFromOrder(order, operator, notes, createdBy)`
  guarda `order.status() == PENDING_BOOKING` (senão `CommercialOrderNotPendingBookingException`), começa `PENDING`,
  snapshota os itens classificados.
- **`BookingItem`** (filho; espelha `CommercialOrderItem` **sem dados monetários**): `orderItemId`, `type`
  (reusa `ProposalItemType`), `description`, `quantity`, `requiresBooking`, `status` (`BookingItemStatus`). Factory
  `snapshotOf(CommercialOrderItem)` classifica: bookável (`TRAVEL_PACKAGE`/`CAR_RENTAL`) → `PENDING`; senão
  `NOT_REQUIRED`.
- **`BookingRequestStatus`** (`PENDING/IN_PROGRESS/PARTIALLY_CONFIRMED/CONFIRMED/FAILED/CANCELLED` + `isActive()`/
  `activeStatuses()`), **`BookingItemStatus`** (`PENDING/IN_PROGRESS/CONFIRMED/FAILED/NOT_REQUIRED/CANCELLED`),
  evento **`BookingRequestCreated`**, **`BookingRequestRepository`** (`findFirstByCommercialOrderIdAndStatusIn`).
- **`BookingRequestService.create(...)`**: carrega o pedido (`CommercialOrderNotFoundException`), checa acesso
  reusando `OrderAccessPolicy.canSee` (`CommercialOrderAccessDeniedException`), barra duplicado ativo
  (`BookingRequestAlreadyExistsException` 409), valida o operador se informado (`BookingOperatorNotFoundException`),
  cria via factory (guarda PENDING_BOOKING), salva, publica o evento.

**Delivery:** `POST /api/bookings` (`BookingRequestController`) com `CreateBookingRequestRequest` (`commercialOrderId`
obrigatório + `bookingOperatorId`/`notes` opcionais) → 201 + Location + `BookingRequestResponse(id, status=PENDING)`.

**Infra:** `SecurityConfig` (POST `/api/bookings` → `SCOPE_booking:request:create`); `HttpErrorMapping`
(`booking.already-exists`→409, `booking.order-not-pending`→422, `booking.operator-not-found`→422);
`messages.properties` (3 chaves pt-BR). **Migração `V29`**: tabelas `booking_requests` + `booking_items` (índice
único parcial `ux_booking_requests_active_per_order WHERE status <> 'CANCELLED'`), **novo usuário-semente 006
`operacoes`** + escopos (`booking:request:create` + `sales:order:read:all`; Manager 001 também `booking:request:create`).

## 2. Regras funcionais cobertas
Só pedidos `PENDING_BOOKING` originam reserva; **um ativo por pedido**; preserva pedido/proposta/oportunidade/lead +
responsável comercial; operador opcional; começa `PENDING`; **não** cria reserva externa, Receivable, Payment,
Commission nem contata sistemas externos. Os itens que exigem reserva são identificados (`requiresBooking` + status
`PENDING` vs `NOT_REQUIRED`).

## 3. Critérios de aceite cobertos
Usuário autorizado cria de pedido `PENDING_BOOKING` ✓ · pedido não-`PENDING_BOOKING` não origina (422) ✓ · sem
duplicado ativo (409) ✓ · começa `PENDING` ✓ · ligada ao pedido ✓ · preserva referências comerciais ✓ · sem reserva
externa ✓ · sem comportamento Financeiro/Payment/Commission ✓ · Sprint 1/2/3 seguem funcionando (suíte completa
verde) ✓.

## 4. Arquivos alterados
- **Backend (novos):** `domain/booking/model/{BookingRequest,BookingItem,BookingRequestStatus,BookingItemStatus,
  BookingRequestCreated}.java`, `domain/booking/repository/BookingRequestRepository.java`,
  `domain/booking/service/BookingRequestService.java`, `domain/booking/exception/{BookingRequestAlreadyExists,
  CommercialOrderNotPendingBooking,BookingOperatorNotFound}Exception.java`, `application/api/BookingRequestController.java`,
  `application/api/dto/{CreateBookingRequestRequest,BookingRequestResponse}.java`,
  `db/migration/V29__booking_requests.sql`; testes `domain/booking/BookingRequestTest.java`,
  `application/api/BookingRequestApiIntegrationTest.java`. **(editados):** `infra/security/SecurityConfig.java`,
  `infra/web/HttpErrorMapping.java`, `messages.properties`, `application.yml` (0.37.0).
- **Docs:** `CLAUDE.md` (§10 modelo de autorização de Booking), este relatório.

## 5. Testes / validações
- **Unit `BookingRequestTest` (4):** cria de `PENDING_BOOKING` preservando refs + classificando itens; aceita
  operador/notas nulos; rejeita pedido não-`PENDING_BOOKING`; **`BookingItem` não tem campo monetário** (reflexão).
- **Integração `BookingRequestApiIntegrationTest` (8):** operações(006) cria → 201 + agregado salvo (refs +
  itens classificados via JDBC); Manager(001) também cria; não-`PENDING_BOOKING` → 422 `booking.order-not-pending`;
  duplicado → 409 `booking.already-exists` (+ `fields.bookingRequestId`); sem escopo create → 403; com create mas
  sem ver o pedido → 403 `order.access-denied`; operador inexistente → 422 `booking.operator-not-found`; não
  autenticado → 401.
- **Backend `./mvnw verify` verde** (ArchUnit/Modulith + Testcontainers + V29 + `HttpErrorMappingTest`). Frontend
  `ng test`/`ng build` e E2E verdes (regressão; sem mudança de frontend). Pilha dev recriada (V29 aplicada);
  `/api/version` = 0.37.0.

## 6. Suposições
- **`BookingItem` sem dados monetários** (tipo/descrição/quantidade/requiresBooking/status) — "Booking Request não é
  dado financeiro"; o dinheiro fica no Pedido.
- **Todos** os itens do pedido viram BookingItem, classificados (BOOK4-002 embutido na criação, conforme os dados
  funcionais da slice).
- **Sem número amigável** da reserva (UUID), como o Pedido na sua 1ª slice.
- `bookingOperatorId` opcional e tipicamente nulo na criação (atribuição é slice futura); validado se presente.
- Falha ao ver o pedido de origem reusa a exceção de Vendas → code **`order.access-denied`** (não `booking.*`).

## 7. Gaps conhecidos
- **Sem lista/detalhe** da Booking Request ainda (BOOK4-003/004) — a asserção do agregado é via repositório/JDBC.
- O Pedido **não reflete** ainda o status de reserva (BOOK4-009); por isso a duplicidade é barrada por 409, não
  escondida na UI (não há UI nesta slice).
- **Tentativas/confirmação/falha** de reserva (BOOK4-005..008) e indicadores (BOOK4-011) são próximas slices.
- Read tiers `booking:request:read*` ainda não existem (chegam com a lista/detalhe).

## 8. Próximo prompt recomendado
> **Sprint 4 / Slice 2 (BOOK4-003/004): Consultar a lista operacional e o detalhe da Booking Request.** Read models
> `BookingRequestListItem`/`BookingRequestDetail` em `domain.booking.service.data` (com os itens + classificação,
> sem dados monetários), `BookingRequestService` (list/detail) com uma `BookingRequestAccessPolicy` + read tiers
> `booking:request:read`/`:read:unassigned`/`:read:all` (migração de escopos), `GET /api/bookings` + `/{id}`
> gated, e a tela no novo módulo **Operações/Reservas** (sidebar + paleta + atalho). TDD em todas as categorias;
> ainda **sem** tentativa/confirmação de reserva, integração externa, Finance/Payment/Commission.
