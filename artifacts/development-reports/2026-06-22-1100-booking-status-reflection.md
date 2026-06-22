# Sprint 4 / Slice 9 — Consolidate Booking Request status and reflect it on the Commercial Order

- **Date:** 2026-06-22 11:00
- **Branch:** `feature/booking-status-reflection` → `develop` → `main`
- **Version:** 0.44.0 → **0.45.0** (MINOR — new feature)
- **Migration:** `V35__commercial_order_booking_status.sql` (Sales-owned `booking_status` column; no new scope)

## 1. What was implemented

**Booking — formal consolidation + event.** `BookingRequest.rollUpStatus()` was generalised into
`consolidateStatus()`, now run after **every** mutation (attempt/confirm/fail) and deriving the full status from
the items requiring booking **plus** the attempt history (never overriding an explicit `CANCELLED`): all confirmed
→ `CONFIRMED`; some (not all) confirmed → `PARTIALLY_CONFIRMED`; none confirmed but ≥1 failed → `FAILED`; nothing
confirmed/failed yet but ≥1 attempt → `IN_PROGRESS`; else `PENDING`. The old inline `PENDING→IN_PROGRESS` in
`recordAttempt` was removed (folded into the consolidation). A new domain event
`BookingStatusConsolidated{bookingRequestId, commercialOrderId, status}` is published by `BookingRequestService`
on create (reflecting `PENDING`) and after each mutation (via a `saveAndReflect` helper).

**Sales — owned reflection (event-driven, persisted).** A new Sales-owned `@EventListener`
(`CommercialOrderBookingStatusListener`, **synchronous** → atomic with the booking change) reacts to the event and
writes the Order's own nullable `booking_status` column via the new business method
`CommercialOrder.reflectBookingStatus(...)`. The Order stays owned by Sales & Proposals: Booking **never** writes
the Order, and the reflection **never** touches the Order's own lifecycle (`status`) nor cancels it. The read
models `CommercialOrderDetail`/`CommercialOrderListItem` expose `bookingStatus` (null = no Booking Request yet) —
no extra query, since it is a column on the order row.

**Frontend.** `order.service` gains `bookingStatus` on both read models. The **order detail** shows a *"Status da
reserva"* tag + a hint (CONFIRMED → "pode seguir para o Financeiro", FAILED → "Problema na reserva", null →
"Reserva ainda não iniciada") and a header tag; the **order list** gains a *"Status da reserva"* column.

## 2. Functional rules covered

The Booking Request status consolidates deterministically from the booking-item statuses + attempts (the five
rules above; `CANCELLED` reserved for explicit cancellation). The Commercial Order shows the consolidated booking
status (detail + list). A **Confirmed** booking makes the Order identifiable as **ready for Financial Operations**
(its `bookingStatus = CONFIRMED`); a **Failed** booking makes it identifiable as having a **booking problem**
(`bookingStatus = FAILED`). The Order ownership boundary is respected — Booking takes no ownership, never changes
the Order's lifecycle, and a failed booking does **not** cancel the Order. No Receivable / Payment / Commission /
Customer Care data is created. Existing Booking Operations behaviour (attempts/confirm/fail/retry, Slices 5–8) is
unchanged.

## 3. Acceptance criteria

- Booking Request status consolidates from Booking Item statuses — **met** (`consolidateStatus()`; unit +
  integration tests across all five outcomes).
- Commercial Order shows booking status summary — **met** (`bookingStatus` on detail + list, reflected via event).
- Confirmed Booking Request makes the Order identifiable as ready for Financial Operations — **met**
  (`bookingStatus = CONFIRMED`, asserted via the order detail).
- Failed Booking Request makes the Order identifiable as having a booking problem — **met** (`bookingStatus =
  FAILED`).
- No Receivable / Payment / Commission behaviour is created — **met** (order-detail keyset asserts only commercial
  fields + `bookingStatus`).
- Commercial Order ownership boundary respected; existing Booking Operations behaviour still works — **met** (the
  Order lifecycle stays `PENDING_BOOKING` through confirm/fail; Slices 5–8 green).

## 4. Files changed

**Backend (new):** `domain/booking/model/BookingStatusConsolidated.java`,
`domain/sales/service/CommercialOrderBookingStatusListener.java`,
`resources/db/migration/V35__commercial_order_booking_status.sql`,
`test/…/application/api/OrderBookingStatusReflectionApiIntegrationTest.java`.
**Backend (edited):** `BookingRequest.java` (`consolidateStatus`), `BookingRequestService.java`
(`saveAndReflect` + publish), `CommercialOrder.java` (`bookingStatus` + `reflectBookingStatus`),
`CommercialOrderDetail.java`, `CommercialOrderListItem.java`, `application.yml` (0.45.0); tests
`BookingRequestTest.java` (+6 consolidation), `CommercialOrderTest.java` (+4 reflection),
`CommercialOrderApiIntegrationTest.java` / `CommercialOrderListingApiIntegrationTest.java` (keyset += `bookingStatus`).
**Frontend (edited):** `core/api/order.service.ts`, `features/orders/order-detail/{ts,html}`,
`features/orders/order-list/{ts,html}`, their `.spec.ts`, and `e2e/order-listing.spec.ts` (exact column match +
new "Status da reserva" column).
**Docs:** `CLAUDE.md` §10 (consolidation rules + Order reflection), bilingual user manual (§9 Pedidos + §10.7),
this report.

## 5. Tests / validations

- **Backend:** unit — `BookingRequestTest` (+6: PENDING / IN_PROGRESS / CONFIRMED / PARTIALLY_CONFIRMED / FAILED /
  retry-reconsolidation), `CommercialOrderTest` (+4: reflect without lifecycle change, FAILED does not cancel,
  re-reflection, starts null). Integration — `OrderBookingStatusReflectionApiIntegrationTest` (6: create→PENDING,
  attempt→IN_PROGRESS, confirm-one→PARTIALLY then confirm-all→CONFIRMED with the Order lifecycle unchanged,
  fail→FAILED without cancelling the Order, retry→reconsolidation, and the order-detail keyset = commercial fields
  + `bookingStatus` only). Order detail/list keyset tests updated. **`./mvnw verify` green — 581 tests** (ArchUnit
  + Modulith + Spotless + Checkstyle).
- **Frontend:** `order-detail.spec` / `order-list.spec` — booking-status labels/severities, the ready-for-finance
  and problem hints, the null "não iniciada" state, and the DOM rendering (tag + column). **`ng test` 386 passed;
  `ng build` clean.**
- **E2E (full cycle `e2e:up → e2e → e2e:down`):** **49 passed, 1 flaky** — `proposal-rejection` (PrimeNG
  `<p-select>`, passed on retry), the documented pre-existing flake. The `order-listing` spec was updated (the new
  *"Status da reserva"* column made the `name: 'Reserva'` substring matcher ambiguous → made it `exact` and added
  the new column assertion). No new E2E for the reflection happy-path: there is still no UI to **create** a Booking
  Request, so the isolated stack can't seed a confirmation — covered by the integration test, as in S3–S8.

## 6. Known gaps

The consolidation keeps the **Slice-8 precedence** "none confirmed and ≥1 failed → `FAILED`" (even if some items
are still `PENDING`), read as rule 5's "the operation cannot proceed"; preserves delivered behaviour and the
"booking problem" identifiability (signalled, not a new invented rule). `CANCELLED` is reserved (no cancel action
yet), so the `consolidateStatus()` CANCELLED-guard branch is defensive and not exercised by a test (the state is
unreachable today). Out of scope as specified: Receivable creation, commission, customer-care tickets, post-sale
cancellation, refund, external integration, automatically cancelling the Order, and changing the Order's own
lifecycle. **One Booking Request per Order** is assumed (no cancellation/multiple requests yet); when cancellation
ships, revisit which request reflects.

## 7. Recommended next prompt

> Read CLAUDE.md and the current Sprint 4 Booking Operations implementation. Continue Sprint 4. Implement only the
> next functional slice: **assign / take a Booking Request operator** — let an authorized user assign a Booking
> Request to a booking operator (or take it themselves), recording who/when, with the operator from the booking
> personas; reflect the operator on the list/detail and in the "unassigned" visibility tier. Out of scope: the
> explicit cancellation flow and any Financial data. After finishing, report 1–7.
