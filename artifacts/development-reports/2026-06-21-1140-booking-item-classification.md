# Relatório — Sprint 4 / Slice 2: Classificar itens do Pedido por necessidade de reserva

- **Data:** 2026-06-21
- **Branch:** `feature/booking-item-classification` (a partir de `develop`)
- **Versão:** 0.37.0 → **0.38.0** (MINOR)
- **Escopo:** **completar a classificação** dos itens da Booking Request com a regra que faltava — **`OTHER` só
  exige reserva quando marcado explicitamente** na criação. Backend-only; **sem migração**.

## 1. O que foi implementado
- **`BookingItem.requiresBooking(type, explicitlyRequired)`** (regra de domínio, `switch`): `TRAVEL_PACKAGE`/
  `CAR_RENTAL` → sempre `true`; `SERVICE_FEE` → `false`; `OTHER` → `explicitlyRequired`. A factory
  `snapshotOf(source, explicitlyRequired)` usa a regra (status `PENDING` se exige, senão `NOT_REQUIRED`),
  preservando `orderItemId`/`type`/`description`/`quantity` (sem dado monetário).
- **`BookingRequest.createFromOrder(order, operator, notes, bookingRequiredItemIds, createdBy)`**: valida as marcas
  **no domínio** — cada id deve ser um item **OTHER do pedido** (cheque único `typeById.get(id) != OTHER` cobre
  "id fora do pedido" e "tipo não-OTHER") senão **`BookingItemNotMarkableException(id)`**; depois snapshota cada
  item com `explicitlyRequired = marcados.contains(item.id())`.
- **Delivery:** `CreateBookingRequestRequest` + campo opcional `bookingRequiredItemIds (Set<UUID>)`;
  `BookingRequestService.create`/`BookingRequestController` repassam ao domínio.
- **Infra:** `HttpErrorMapping` (`BookingItemNotMarkableException → 422`); `messages.properties`
  (`booking.item-not-markable`). **Sem migração** (a tabela `booking_items` já tem `requires_booking`/`status`).
- **`CLAUDE.md` §10:** regra de classificação atualizada (OTHER-explícito + 422 ao marcar não-OTHER).

## 2. Regras funcionais cobertas
TRAVEL_PACKAGE exige reserva ✓ · CAR_RENTAL exige ✓ · SERVICE_FEE não exige (NOT_REQUIRED) ✓ · OTHER exige **só
quando marcado** na criação ✓ · itens que não exigem → `NOT_REQUIRED` ✓ · itens que exigem → `PENDING` ✓ ·
preserva item de origem (`orderItemId`), descrição e tipo ✓ · **não** cria reserva externa, disponibilidade,
Financeiro/Comissão ✓. Marcar item não-OTHER (ex.: SERVICE_FEE) ou id fora do pedido → **422** (defesa em
profundidade da regra "SERVICE_FEE nunca").

## 3. Critérios de aceite cobertos
Identifica Travel Package como exige-reserva ✓ · identifica Car Rental como exige-reserva ✓ · identifica Service
Fee como Not Required ✓ · Other pode ser marcado conforme a necessidade ✓ · BookingItems preservam a informação do
item de origem ✓ · nenhum comportamento de reserva externa/financeiro ✓ · a criação da Booking Request da Slice 1
segue funcionando (sem marcas → OTHER fica NOT_REQUIRED, compatível) ✓.

## 4. Arquivos alterados
- **Backend (novo):** `domain/booking/exception/BookingItemNotMarkableException.java`. **(editados):**
  `domain/booking/model/BookingItem.java` (regra + factory), `domain/booking/model/BookingRequest.java`
  (assinatura + validação), `domain/booking/service/BookingRequestService.java`,
  `application/api/BookingRequestController.java`, `application/api/dto/CreateBookingRequestRequest.java`,
  `infra/web/HttpErrorMapping.java`, `messages.properties`, `application.yml` (0.38.0); testes
  `domain/booking/BookingRequestTest.java`, `application/api/BookingRequestApiIntegrationTest.java`.
- **Docs:** `CLAUDE.md` (§10), este relatório.

## 5. Testes / validações
- **Unit `BookingRequestTest` (7):** preserva refs + classifica; **OTHER exige só quando marcado**; marcar não-OTHER
  → exceção; marcar id fora do pedido → exceção; operador/notas nulos; rejeita pedido não-`PENDING_BOOKING`;
  BookingItem sem campo monetário.
- **Integração `BookingRequestApiIntegrationTest` (12):** marca OTHER na criação → OTHER `PENDING`/`requires_booking`
  (via JDBC em `booking_items`), TRAVEL `PENDING`, SERVICE_FEE `NOT_REQUIRED`; OTHER sem marca → `NOT_REQUIRED`;
  marcar SERVICE_FEE → 422 `booking.item-not-markable` (+ `fields.itemId`); marcar id fora do pedido → 422; + os 8
  da Slice 1 (criação, refs, duplicado 409, acesso 403, escopo 403, operador 422, 401).
- **Gates (ciclo completo):** backend `./mvnw verify` verde (ArchUnit/Modulith + Testcontainers +
  `HttpErrorMappingTest`); `ng test` + `ng build` verdes; **E2E `e2e:up → e2e → e2e:down`** verde. Pilha dev recriada;
  `/api/version` = 0.38.0.

## 6. Gaps conhecidos
- A marca de OTHER é **na criação**; **mudar** a necessidade depois (toggle por item) é slice futura (endpoint de item).
- Um pedido **só com OTHER** nasce `BOOKING_NOT_REQUIRED` na origem (regra de Vendas/Sprint 3) e **não** origina
  reserva — o OTHER-explícito só vale dentro de um pedido já `PENDING_BOOKING`. Revisar a regra de status do pedido
  está fora do escopo.
- Sem lista/detalhe da reserva (BOOK4-003/004) — asserções via JDBC; "notas comerciais" = a **descrição** do item
  (não há nota por item na origem; sem campo novo).
- Tentativa/confirmação/falha (BOOK4-005..008) e indicadores (011) são próximas slices.

## 7. Próximo prompt recomendado
> **Sprint 4 / Slice 3 (BOOK4-003/004): Consultar a lista operacional e o detalhe da Booking Request.** Read models
> `BookingRequestListItem`/`BookingRequestDetail` em `domain.booking.service.data` (com itens + classificação, sem
> dinheiro), `BookingRequestAccessPolicy` + read tiers `booking:request:read`/`:read:unassigned`/`:read:all`
> (migração de escopos), `GET /api/bookings` + `/{id}` gated, e a primeira tela no novo módulo **Operações/Reservas**
> (sidebar + paleta + atalho). TDD em todas as categorias; ainda **sem** tentativa/confirmação, integração externa,
> Finance/Payment/Commission.
