# Sprint 4 / Slice 10 — Operational pending-items worklist for Booking Operations

- **Date:** 2026-06-22 14:00
- **Branch:** `feature/booking-pending` → `develop` → `main`
- **Version:** 0.45.0 → **0.46.0** (MINOR — new feature)
- **Migration:** `V36__booking_request_next_action_date.sql` (denormalized `next_action_date`; no new scope)

## 1. What was implemented

**Backend — `GET /api/bookings/pending`.** A read-only worklist of the Booking Requests that need action, each
tagged with its reasons, mirroring the Opportunity pending worklist:

- New enum `BookingPendingReason` (7 codes) + `BookingRequestPendingReasons.of(...)` (the single source of truth,
  `STALE_DAYS = 7`) and `BookingRequestPendingSpecifications.pending(now, today)` (the query, mirroring the reasons
  so the page contains exactly the requests with ≥1 reason; terminal CONFIRMED/CANCELLED excluded).
- The "overdue next action" reason reads a **denormalized `next_action_date`** on the request (mirrors
  `last_attempt_at`): `BookingRequest.recordAttempt` now also stores the latest attempt's planned next action.
- New read model `PendingBookingRequest` (the list item + `nextActionDate`, `failedItems` and `reasons`), a grouped
  projection `findPendingItemCounts` (requiring / confirmed / failed / requiring-pending, no N+1), and
  `BookingRequestService.pending(...)` (gated by the Booking read tiers; `BookingRequestAccessPolicy` narrows
  visibility at the query level). Controller `GET /pending` declared **before** `/{id}` (Spring prefers the literal
  segment); the existing `/api/bookings/**` GET security matcher already covers it — **no SecurityConfig change**.

**Frontend.** `BookingService.pending`, the `PendingBookingRequest`/`BookingPendingReason` types, a new
**`booking-pending`** screen (reason chips, PC-000n link to the booking detail, loading/empty/error states), a
**Reservas** tab added to the **Acompanhamento → Pendências** hub (gated by `canSeeBookings()`), and the
navigation updated so a booking-only profile (e.g. operações) reaches the Pendências hub.

## 2. Functional rules covered

A request is pending when it has **no operator**, is **PENDING** (no attempt), is **IN PROGRESS without a recent
attempt** (7-day window), has a **failed item**, has a **requiring-booking item still pending**, is **PARTIALLY
CONFIRMED**, or has an **overdue next action**; CONFIRMED/CANCELLED are excluded. Visibility respects the Booking
read tiers + `BookingRequestAccessPolicy` (the worklist never exposes a request the caller may not see). It is
operational, **not** an executive dashboard: read-only, no notification/SLA engine, no automatic external retry,
and no Financial/Payment/Commission/Customer Care data.

## 3. Acceptance criteria

- Booking operations users can see unassigned requests, requests without attempts, failed items, overdue next
  actions, and partially-confirmed requests — **met** (each maps to a reason; integration-tested).
- The pending view does not expose unauthorized requests — **met** (gated by read tiers; the policy narrows the
  query; a seller/representative with no booking read tier → 403).
- Existing Booking Operations behaviour still works — **met** (Slices 4–9 green; `recordAttempt` extended only).

## 4. Files changed

**Backend (new):** `domain/booking/model/BookingPendingReason.java`,
`domain/booking/model/BookingRequestPendingReasons.java`,
`domain/booking/service/BookingRequestPendingSpecifications.java`,
`domain/booking/service/data/PendingBookingRequest.java`,
`domain/booking/repository/BookingPendingItemCountsRow.java`,
`resources/db/migration/V36__booking_request_next_action_date.sql`,
`test/…/domain/booking/BookingRequestPendingReasonsTest.java`,
`test/…/application/api/BookingPendingApiIntegrationTest.java`.
**Backend (edited):** `BookingRequest.java` (`nextActionDate` + `recordAttempt`), `BookingRequestRepository.java`
(`findPendingItemCounts`), `BookingRequestService.java` (`pending`), `BookingRequestController.java` (`GET
/pending`), `application.yml` (0.46.0).
**Frontend (new):** `features/bookings/booking-pending/{ts,html,css,spec}`.
**Frontend (edited):** `core/api/booking.service.ts`, `features/pendencias/pendencias-hub/{ts,html,spec}`,
`core/navigation/navigation.ts`.
**Docs:** `CLAUDE.md` §10 (pending worklist), bilingual user manual (§4 + §10.8), this report.

## 5. Tests / validations

- **Backend:** unit `BookingRequestPendingReasonsTest` (15: each reason isolated, the 7-day window edge, terminal
  exclusion, several reasons adding up). Integration `BookingPendingApiIntegrationTest` (9: unassigned/pending
  with the exact reason set + the read-model keyset proving no financial fields; partially-confirmed appears;
  failed item; overdue next action; in-progress stale; CONFIRMED/CANCELLED excluded; manager + board read-all
  consult; seller without a booking read tier → 403; 401). **`./mvnw verify` GREEN — 605 tests.**
- **Frontend:** `booking-pending.spec` (load, reason/status/code labels, error, DOM rows + reason tags, empty
  state) and `pendencias-hub.spec` (the Reservas tab appears with `canSeeBookings`; a booking-only profile sees
  only Reservas). **`ng test` 392 passed; `ng build` clean.**
- **E2E (full cycle `e2e:up → e2e → e2e:down`):** **49 passed, 1 flaky** — `proposal-rejection` (PrimeNG
  `<p-select>`, passed on retry), the documented pre-existing flake. No new E2E for the pending data: there is no
  UI to **create** a Booking Request, so the isolated stack cannot seed pending bookings — covered by the
  integration tests, as in S3–S9.

## 6. Known gaps

The "overdue next action" reads a **denormalized** `next_action_date` (the latest attempt's planned next action;
an older attempt registered later does not override it) — a decision consistent with the existing `last_attempt_at`
denormalization. `PENDING without attempt` is just `status == PENDING` (the Slice-9 consolidation guarantees
PENDING ⟺ no attempt), so a freshly-created request appears immediately (it needs to be picked up — the worklist's
intent). Only the `read:all` tier is seeded today (operações/manager/board); the own/unassigned tiers exist in the
policy for future operator profiles. Out of scope as specified: email alerts, notification engine, SLA engine,
automatic task assignment, external-integration retry, executive dashboard.

## 7. Recommended next prompt

> Read CLAUDE.md and the current Sprint 4 Booking Operations implementation. Continue Sprint 4. Implement only the
> next functional slice: **assign / take a Booking Request operator** — let an authorized user assign a Booking
> Request to a booking operator (or take it themselves), recording who/when, with the operator from the booking
> personas; the unassigned-operator pending reason then clears. Out of scope: the explicit cancellation flow and
> any Financial data. After finishing, report 1–7.
