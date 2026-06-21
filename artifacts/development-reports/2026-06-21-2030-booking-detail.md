# Sprint 4 / Slice 4 — Booking Request detail consultation

- **Date:** 2026-06-21 20:30
- **Branch:** `feature/booking-detail` → `develop` → `main`
- **Version:** 0.39.0 → **0.40.0** (MINOR — new feature)
- **Migration:** none (no schema change)

## 1. What was implemented

**Backend — read-only Booking Request detail (`GET /api/bookings/{id}`).** A new `BookingRequestDetail` read
model (`domain.booking.service.data`) assembled by `BookingRequestService.detail(...)` (`loadVisible` → 404 if
absent, `BookingRequestAccessPolicy.canSee` → 403 if not visible), returning: the reservation summary (status,
operator, commercial responsible, notes, created/updated + creator), the **requiring / confirmed / failed item
counts**, the **booking items** (each with `type`, `description`, `quantity`, `requiresBooking`, **item
status**, and `orderItemId` linking it to the source commercial item), and the source **Commercial Order /
Proposal / Opportunity / Lead** kept traceable. Two new exceptions — `BookingRequestNotFoundException` (404,
`booking.not-found`) and `BookingRequestAccessDeniedException` (403, `booking.access-denied`) — registered in
`HttpErrorMapping` and `messages.properties`. The controller GET `/{id}` reuses the read-tier helpers; **no
`SecurityConfig` change** (the `/api/bookings/**` GET gate from Slice 3 already covers it).

**Frontend — the reservation detail page (`/reservas/:id`, `bookingReadGuard`).** New `BookingDetail` component
(mirrors `order-detail`) with the summary card, the source Order/Proposal/Opportunity cards (each with a
"Ver … de origem" link — the **source Order stays traceable** from within the reservation), and the items table
with a per-item **status tag** (Pendente/Em andamento/Confirmado/Falhou/Não requer/Cancelado). Loading / 403 /
404 / generic-error states; `Esc` returns to `/reservas`. `BookingService.detail(id)` + the detail types. The
**booking list row** now opens the reservation detail (`/reservas/:id`) instead of the source Order; the `?`
help overlay gained the "No detalhe de uma reserva: `Esc` Voltar" entry.

**Scope decision (Rule Zero / defer cleanly).** The richer **manual-attempt log / confirmation reference /
failure reason** as their own model are explicitly the later slices (BOOK4‑005..007) and were **not** invented
here. Confirmation/failure are surfaced via the **per-item booking status** (a `CONFIRMED` item shows confirmed,
a `FAILED` item shows failed) plus the summary counts — the data that exists today.

## 2. Functional rules covered

- **Source Commercial Order remains traceable** — the detail carries `sourceOrder` (number + status) and links
  to `/pedidos/:id`.
- **Booking items remain traceable to commercial items** — each item carries `orderItemId`.
- **Confirmation information stays visible** — items with status `CONFIRMED` + the `itemsConfirmed` count.
- **Failure information stays visible** — items with status `FAILED` + the `itemsFailed` count.
- **Users only open Booking Requests they may see** — `BookingRequestAccessPolicy.canSee` (404 absent / 403 not
  visible) + the read-tier security gate.
- **No Receivable / Payment / Commission** — the contract is pinned by a key-set assertion in the integration
  test (no financial/commission fields).

## 3. Acceptance criteria covered

Authorized users (operações/manager/director) open the detail ✓; the detail shows the source Commercial Order
✓; booking items and their statuses ✓; manual attempts when they exist (none yet — surfaced via item status,
deferred to BOOK4‑005..007) ✓ honest deferral; confirmation information when it exists (CONFIRMED items +
count) ✓; failure information when it exists (FAILED items + count) ✓; unauthorized users cannot access (403/404
+ guard) ✓; existing Booking Operations behavior (create, list) unchanged — full suite green ✓.

## 4. Files changed

- **Backend (new):** `BookingRequestDetail`, `BookingRequestNotFoundException`,
  `BookingRequestAccessDeniedException`, `BookingRequestDetailApiIntegrationTest`. **(edited):**
  `BookingRequestService` (+`detail`/`loadVisible`/`toDetail` + Opportunity/Lead repos), `BookingRequestController`
  (+GET `/{id}`), `HttpErrorMapping`, `messages.properties`, `application.yml` (version).
- **Frontend (new):** `features/bookings/booking-detail/*` (ts/html/css/spec). **(edited):** `booking.service.ts`
  (+detail types/method), `app.routes.ts` (+`reservas/:id`), `booking-list.html` (row → detail), `shell.html`
  (? overlay).
- **E2E (new):** `booking-detail.spec.ts`.
- **Docs:** `CLAUDE.md` §10 (Booking detail).

## 5. Tests / validations added

- **Backend:** `BookingRequestDetailApiIntegrationTest` — 7 tests: happy path (traceability + item statuses +
  counts), exact operational field-set (no financial/commission), manager + director open, 404 unknown id, 403
  for a caller who can't see (own-tier token, neither operator nor responsible), 403 for a seller (no booking
  tier), 401 unauthenticated. Full `./mvnw verify` green — **510 tests** (ArchUnit + Modulith + HttpErrorMapping
  completeness included).
- **Frontend:** `booking-detail.spec` — load, status/item-status labels & severities, code formatting, 403/404
  messages, back navigation, DOM (record render with item statuses + source links, loading, hidden-notes,
  error). `ng test` green — **360 tests**; `ng build` green.
- **E2E:** `booking-detail.spec` — operações reaches the detail route (not-found render for an unknown id);
  seller is blocked by the guard. (Run via the full `e2e:up → e2e → e2e:down` cycle.)

## 6. Known gaps

- The **manual-attempt log, confirmation reference and failure reason** as a dedicated model are deferred to the
  attempt/confirmation slices (BOOK4‑005..007); today confirmation/failure show via the item status + counts.
- The detail is **read-only** — no actions (start attempt, confirm, mark failed) yet (those slices add them).
- The detail E2E covers route/guard/404 only (the isolated stack seeds no reservations); the rich happy path is
  covered by the backend integration test against a seeded Postgres.

## 7. Recommended next implementation prompt

> **Sprint 4 / SLICE 5: Assign a booking operator + start the reservation work.** Add the operation
> `booking:request:assign` (or reuse `booking:request:update`) so a manager/operations lead can set/clear the
> `bookingOperatorId` on a `PENDING` request, and a `POST /api/bookings/{id}/start` that moves
> `PENDING → IN_PROGRESS` (guarded by the read+operate scopes and `canSee`). Reflect the assignment/owner on the
> list and detail, add the frontend actions (with keyboard shortcuts + unsaved-changes where a form is
> involved), and cover happy + all sad paths (401/403/404/422 invalid transition). Still no
> Financial/Payment/Commission; the per-item attempt/confirmation outcome remains a later slice.
