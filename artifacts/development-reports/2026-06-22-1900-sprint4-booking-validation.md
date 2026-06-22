# Sprint 4 / Slice 12 — End-to-end validation of the Booking Operations flow

- **Date:** 2026-06-22 19:00
- **Branch:** `feature/sprint4-booking-validation` → `develop` → `main`
- **Version:** 0.47.0 → **0.47.1** (PATCH — validation / hardening; no migration, no contract change)
- **Type:** validation slice (`test:`) — no production-code change; **no defect found**.

## 1. Validation performed

A new end-to-end integration test — `BookingOperationsEndToEndIntegrationTest` (MockMvc + real Postgres) — drives
the Booking Operations flow as **one coherent journey through the real API**, starting from a Commercial Order
built by the **Sprint 3 flow** (seed Lead/Opportunity/Proposal → add items + validity + submit/approve/send/accept
via the proposal API → `POST /api/orders`), then running the Sprint 4 booking lifecycle (create request → attempt
→ confirm/fail/retry) and asserting the Commercial Order reflection at each step. Four tests: the **main
confirmation flow**, the **failure + retry flow**, the **partially-confirmed flow**, and **visibility during the
flow**. Then the **full regression suite** was run: backend `./mvnw verify` (the new test + ArchUnit + Modulith +
the entire Sprint 1/2/3/4 suite), the frontend `ng test` + `ng build`, and the full E2E cycle
(`e2e:up → e2e → e2e:down`).

- **Backend `./mvnw verify`: BUILD SUCCESS — 615 tests, 0 failures** (611 + the 4 new flow tests).
- **Frontend: `ng test` 401 passed; `ng build` clean.**
- **E2E (full cycle): 49 passed, 1 flaky** — `proposal-rejection` (the documented PrimeNG `<p-select>` flake,
  passed on retry), unrelated to Sprint 4.

## 2. Flow results

- **Main confirmation flow ✓** — A Sprint-3 Order (`PENDING_BOOKING`, with travel package + car rental + service
  fee) → create Booking Request → **PENDING**; items classified (travel package & car rental **require** booking =
  PENDING; service fee **NOT_REQUIRED**); the Order reflects `bookingStatus = PENDING`. Manual attempt →
  **IN_PROGRESS** (reflected). Confirm the travel package (locator `ABC123`) → **PARTIALLY_CONFIRMED** (reflected).
  Confirm the car rental (locator `CAR-77`) → **CONFIRMED**; the Order reflects `CONFIRMED` (ready for Financial
  Operations) while its own lifecycle stays `PENDING_BOOKING` (Sales keeps ownership). The confirmed booking keeps
  the source Order/Proposal/Opportunity/Lead traceable and the items carry their external locators; the Order
  carries the commercial total. No financial fields appear in either contract.
- **Failure + retry flow ✓** — create → attempt → **fail** the car rental (reason `NO_AVAILABILITY`) → **FAILED**
  (the item is an operational problem with its failure recorded; the Order reflects `FAILED`). A new attempt on the
  failed item is history only (stays FAILED). **Confirm** the previously failed car rental (retry) → reconsolidates
  to **PARTIALLY_CONFIRMED** (travel package still pending); confirming the travel package → **CONFIRMED**.
- **Partially-confirmed flow ✓** — Order with two booking-required items → confirm only one → **PARTIALLY_CONFIRMED**
  (the other stays PENDING); the Order reflects `PARTIALLY_CONFIRMED`, its lifecycle untouched, no financial data.
- **Visibility ✓** — a seller (no booking read tier) gets **403** on the reservation detail, the pending worklist
  and the indicators during the flow.

## 3. Defects found and fixed

**None.** The end-to-end journeys pass on the existing Sprint 4 implementation (Slices 4–11) without any code
change. The only change in this slice is the new validation test (and the routine version bump). No Sprint 4
acceptance-criterion gap was revealed, so no production code was touched and no scope was expanded.

## 4. Acceptance criteria covered

- The main Booking Request confirmation flow works end to end — **✓** (test `mainConfirmationFlow…`).
- Failed item and retry flow works end to end — **✓** (`failureThenRetryFlow`).
- Partially-Confirmed flow works end to end — **✓** (`partiallyConfirmedFlow…`).
- No step depends on external data beyond the intentionally manual confirmation fields — **✓** (the whole journey
  is driven by in-system API calls; the only manual inputs are the operator-supplied external system/locator/date).
- Visibility rules hold during the flow — **✓** (`visibilityHoldsDuringTheFlow…`: seller → 403).
- Confirmed Booking Request contains enough information for Sprint 5 Financial Operations — **✓** (the confirmed
  booking keeps Order/Proposal/Opportunity/Lead traceable + item locators; the Order is identifiable as
  `bookingStatus = CONFIRMED` and carries the commercial total — the monetary data Finance needs lives on the
  Order, by design).
- No future-scope feature introduced — **✓** (no Finance/Receivable/Payment/Commission/Customer-Care/integration
  code; no new endpoints, entities or migrations).
- Existing Sprint 1/2/3 behaviour remains working — **✓** (the full `verify` + E2E regression is green).

## 5. Remaining risks

- **No "create booking" UI** → the full journey is not exercisable through the browser, so it is validated by the
  backend integration test (as in Slices 3–11), not by Playwright. Known limitation, not a defect of this slice; a
  create-booking screen would be a future UX slice (out of scope here).
- **Consolidation precedence (Slices 8/9):** failing the car rental while the travel package is still pending makes
  the request `FAILED` (nothing confirmed + ≥1 failed); confirming it on retry returns it to `PARTIALLY_CONFIRMED`.
  This is the delivered behaviour and is now reaffirmed by the flow test.
- **"Enough for Finance" is via the Order, not the booking:** the booking carries no monetary data (by design); a
  Sprint 5 Finance slice will key off the Order (`bookingStatus = CONFIRMED` + total + customer refs). The handoff
  is validated here but the Finance side is intentionally not implemented.
- Pre-existing E2E flakes (`proposal-rejection` PrimeNG `<p-select>`; `lead-indicators` UTC-midnight straddle) are
  unrelated to Sprint 4 and pass on retry.

## 6. Recommended next implementation prompt

> Sprint 4 (Booking Operations) is validated end to end and complete. Begin **Sprint 5 — Financial Operations**:
> read CLAUDE.md and prepare the bounded context. Implement only the first functional slice — surface the
> **Commercial Orders ready for Financial Operations**: a read-only worklist/list of the Orders whose booking is
> `CONFIRMED` (and not yet invoiced), gated by a new `finance:*` read scope, carrying the commercial total + the
> source Order/Proposal/Opportunity/Lead references, but creating **no** Receivable/Payment yet. Keep the Order
> owned by Sales; Finance only reads. After finishing, report 1–7.
