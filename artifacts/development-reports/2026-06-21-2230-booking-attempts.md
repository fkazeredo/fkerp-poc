# Sprint 4 / Slice 5 — Register manual booking attempts

- **Date:** 2026-06-21 22:30
- **Branch:** `feature/booking-attempts` → `develop` → `main`
- **Version:** 0.40.0 → **0.41.0** (MINOR — new feature)
- **Migration:** `V31__booking_attempts.sql` (table + `last_attempt_at` column + scope seed)

## 1. What was implemented

**Backend — manual booking attempts (`POST /api/bookings/{id}/attempts`).** An append-only operational history
on the Booking Request, mirroring the Opportunity's commercial activities (`OpportunityActivity` /
`recordActivity`):

- `BookingAttemptType` (external system access / supplier phone / supplier email / internal verification /
  manual availability check / other) and `BookingAttemptResult` (started / waiting for supplier / waiting for
  internal info / availability found / availability not found / needs retry / failed / other) enums.
- `BookingAttempt` entity (child of the `BookingRequest` aggregate): author (`registeredBy`), date
  (`occurredAt`), type, result, description, optional `bookingItemId` (one item or the whole request) and
  optional `nextActionDate`. **No monetary data.**
- `BookingRequest.recordAttempt(...)` — validates the linked item belongs to the request
  (`BookingItemNotFoundException`, **404**), appends the attempt, refreshes the denormalized `last_attempt_at`,
  and moves `PENDING → IN_PROGRESS`. It **never** confirms the booking, **never** changes a booking item's
  status (even an attempt result of `FAILED` is history only) and **never** creates Financial/Commission data.
- `BookingRequestService.recordAttempt`, `RecordBookingAttemptCommand`, `RegisterBookingAttemptRequest`
  (Bean Validation: required type/result/description/`@PastOrPresent` date), controller `POST /{id}/attempts`
  returning the refreshed `BookingRequestDetail`. The detail now carries the `attempts` list (newest first); the
  **list** now populates `lastBookingAttemptAt` (the denormalized latest attempt).
- New operation scope **`booking:request:update`** gating the endpoint (`SecurityConfig`), seeded in **V31** for
  operações(006) and the Manager(001); the Director(004) stays read-only.

**Frontend.** `BookingService.registerAttempt` + the attempt types; `auth.canOperateBookings()`. The reservation
**detail** gained a **"Histórico de tentativas"** card (newest first: date, type · result, description, item /
"Reserva toda", next action, author) and a **"Registrar tentativa"** dialog (type*, result*, item link, date*
`@PastOrPresent`, description*, optional next action) — shown only to users with the update scope, wired with the
`a` keyboard shortcut, the unsaved-changes discard guard and a success toast, refreshing the detail on save. The
reservation **list** gained an **"Última tentativa"** column. The `?` overlay documents `a` on the reservation
detail.

## 2. Functional rules covered

Every attempt has an **author** (the authenticated user), a **date**, a **type**, a **result** and a
**description** (all required — 400 otherwise); it **may link to one Booking Item or the whole request** (an
item outside the request → 404); it **may define a next action date** (optional); the **history is append-only**
(no delete/edit endpoint); registering an attempt **may move PENDING → IN_PROGRESS**; it **does not confirm the
booking automatically** and **creates no Financial/Commission data** (asserted: items keep their status, request
never becomes CONFIRMED/FAILED, the detail exposes no financial fields).

## 3. Acceptance criteria covered

Authorized users register an attempt ✓ (operações + Manager; Director/seller → 403); attempt requires
type/result/date/author/description ✓ (400 on missing; author from the token); next action date optional ✓; the
attempt appears in the Booking Request detail ✓; the latest attempt appears in the list when available ✓
(`lastBookingAttemptAt`); the request can move PENDING → IN_PROGRESS after the first attempt ✓; no external
booking or financial behavior is created ✓; existing Booking Operations behavior (create, list, detail) remains
working ✓ (full suite green).

## 4. Files changed

- **Backend (new):** `BookingAttemptType`, `BookingAttemptResult`, `BookingAttempt`,
  `BookingItemNotFoundException`, `RecordBookingAttemptCommand`, `RegisterBookingAttemptRequest`,
  `V31__booking_attempts.sql`, `BookingAttemptApiIntegrationTest`. **(edited):** `BookingRequest` (+attempts /
  `lastAttemptAt` / `recordAttempt`), `BookingRequestService` (+`recordAttempt`, name resolution),
  `BookingRequestDetail` (+`attempts`), `BookingRequestListItem` (`lastBookingAttemptAt`),
  `BookingRequestController` (+POST), `HttpErrorMapping`, `SecurityConfig`, `messages.properties`,
  `application.yml`, `BookingRequestDetailApiIntegrationTest` (keyset).
- **Frontend (edited):** `booking.service.ts`, `auth.service.ts`, `features/bookings/booking-detail/*`,
  `features/bookings/booking-list/{html}`, `core/layout/shell.html`, plus `booking-detail.spec` /
  `booking-list.spec`.
- **Docs:** `CLAUDE.md` §10 (manual booking attempts).

## 5. Tests / validations added

- **Backend:** `BookingAttemptApiIntegrationTest` — 13 tests: register (appears in detail, PENDING → IN_PROGRESS,
  items unchanged / no confirm), manager can register, required fields → 400, future date → 400, optional next
  action, item link ok, item not in request → 404, Director → 403, seller → 403, can't-see → 403, unknown
  request → 404, unauthenticated → 401, latest attempt surfaces on the list. Full `./mvnw verify` green —
  **523 tests**.
- **Frontend:** `booking-detail.spec` — attempt-dialog gating by scope, validation, item-link options, register
  → refresh + toast, history DOM render, empty state. `booking-list.spec` — "Última tentativa" column.
  `ng test` green — **363 tests**; `ng build` green.
- **E2E:** full regression cycle `e2e:up → e2e → e2e:down` (the detail/list changed) — **47 passed**, the 2
  long-standing `proposal-rejection` PrimeNG `<p-select>` flakes (pass on retry), and **1 environmental,
  time-of-day flake in `lead-indicators`** (unrelated to this slice — see below). The changed booking specs
  (`booking-listing`, `booking-detail`) passed. No new register E2E: there is no UI to **create** a Booking
  Request, so the isolated stack can't seed one to click into; the register happy path is covered by the
  integration test (seeded Postgres), as in Slices 3–4.
  - **`lead-indicators` E2E flake (pre-existing, environmental — NOT a regression):** root-caused to a UTC
    midnight straddle. The run happened at ~21:5x local (UTC−3) = 00:5x **UTC**; the seeded leads' `created_at`
    is a UTC instant on the *next* calendar day, while the indicators' default month-to-date upper bound uses
    the browser's **local** date, so the backend's UTC-anchored `[from, to)` range excludes them. Verified
    directly against the live API: `…&createdTo=2026-06-21` (local date) → `total:0`; `…&createdTo=2026-06-22`
    (UTC date) → `total:29`. The booking code in this slice does not touch CRM indicators; Slices 3–4 ran
    outside this window and passed. (A genuine but separate timezone-handling nuance for evening UTC−3 users,
    out of this slice's scope.)

## 6. Known gaps

- The attempt model captures the operational log; the **richer confirmation reference / failure reason** as a
  dedicated structure (and the item/request confirmation & failure transitions) remain later slices (BOOK4‑006..).
- An attempt result of `FAILED` is **history only** — it does not fail the reservation or its items (by design).
- Terminal statuses (`CONFIRMED`/`CANCELLED`) are unreachable today, so registering an attempt is not blocked by
  status; a future slice that adds those transitions can decide whether to restrict attempts then.
- No register E2E (no create-booking UI to seed); covered by the backend integration test.

## 7. Recommended next implementation prompt

> **Sprint 4 / SLICE 6: Confirm / fail a booking item.** Add operations (gated by `booking:request:update` +
> `canSee`) to set a booking item's outcome — `POST /api/bookings/{id}/items/{itemId}/confirm` (optionally a
> supplier confirmation reference) and `.../fail` (a failure reason) — recording the change in the history and
> rolling the request status up (`PENDING/IN_PROGRESS → PARTIALLY_CONFIRMED → CONFIRMED`, or `FAILED`) per fixed
> rules; reflect the item statuses + counts on the detail and list. Still no Financial/Payment/Commission and no
> external integration. Cover happy + all sad paths (401/403/404/422 invalid transition) and the status roll-up.
