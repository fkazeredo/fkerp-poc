# Sprint 4 / Slice 3 — Operational Booking Request listing + navigation reorganization

- **Date:** 2026-06-21 18:15
- **Branch:** `feature/booking-listing-and-nav` → `develop` → `main`
- **Version:** 0.38.0 → **0.39.0** (MINOR — new feature)
- **Migration:** `V30__booking_request_read_scopes.sql` (scope seed only — no schema change)

## 1. What was implemented

**Backend — operational Booking Request list (`GET /api/bookings`).** A paginated, filtered read over the
Booking Requests the caller may see, mirroring the Commercial Order list pattern:

- `BookingRequestListItem` read model (`domain.booking.service.data`) — source Order number (`PC-000n`, the
  human identifier; the reservation has no number of its own — Rule Zero), source Proposal title, booking
  status, operator + commercial responsible (names resolved, no N+1), counts of items requiring booking /
  confirmed, creation/update instants, and a reserved `lastBookingAttemptAt` (null until the attempt slice).
  **No monetary/financial field.**
- `BookingRequestSearchCriteria` + `BookingRequestSpecifications.matching` — status (default excludes the
  terminal `CONFIRMED` + `CANCELLED`, keeps `FAILED`), operator (incl. an `unassigned` token), commercial
  responsible, creation period, source order, **item type** and **has-failed-items** (the last two are
  correlated `EXISTS` subqueries over the request's `items`, so pagination counts stay correct).
- `BookingRequestAccessPolicy` — three escalating read tiers as a query Specification (own = booking operator
  OR commercial responsible; `+unassigned` adds operator-is-null; `read:all` = everything), applied to the
  list so filters can never bypass visibility.
- `BookingRequestService.list(...)`, `BookingRequestListParams`, `BookingRequestController` GET
  (`PageResponse<BookingRequestListItem>`), `BookingRequestRepository.findItemCounts` (one grouped native
  query → `BookingItemCountsRow`), `SecurityConfig` `BOOKING_READ_SCOPES` GET gate, and `V30` seeding
  `booking:request:read:all` for operações (006), Manager (001) and Director (004).

**Frontend — navigation reorganized by WORKFLOW (owner request: the old backend-mirrored menu was "confusing
and not intuitive").** The sidebar/system-home/module-homes (single `NavigationService`) now follow the user's
flow rather than the backend bounded contexts:

- **Comercial** (`/comercial`) — the whole funnel in order: Leads · Oportunidades · Propostas · Pedidos.
- **Reservas** (`/reservas`) — the new operations worklist (the booking list; standalone module).
- **Acompanhamento** (`/acompanhamento`) — two single hubs: **Pendências** (`/pendencias`) and **Indicadores**
  (`/indicadores`), each a tabbed page that **reuses the existing area screens** (Leads/Oportunidades/
  Propostas/Pedidos), showing only the tabs the profile may see ("one place" for all pending lists / all
  indicators).
- **Cadastros** (`/cadastros`) — unchanged.

Plus: `auth.canSeeBookings()`, `bookingReadGuard`, the `/reservas` booking list page (`BookingService` +
`BookingList` table with status/operator/responsible/item-type/has-failed/period filters and loading/empty/
error states), the `g r` keyboard shortcut (+ `?` overlay entry + command-palette entry, derived from the nav
config). The per-area indicator/pending routes (`/oportunidades/indicadores|pendencias`, `/propostas|pedidos/
indicadores`) and the old `/crm`,`/vendas` homes were removed (consolidated into the hubs / Comercial).

## 2. Functional rules

- The reservation list shows **operational reservation data only** — never Financial/Payment/Commission data
  (asserted by a contract test pinning the exact field set).
- Default view **excludes** terminal `CONFIRMED` + `CANCELLED`, **keeps `FAILED`** visible; any status can be
  requested explicitly.
- Visibility by read tier: operações(006)/Manager(001)/Director(004) see all; Sellers/Representatives and
  Finance/HR/IT have **no** booking read tier → **403** (and no Reservas module in the UI).
- The reservation's human identifier is the **source Order code `PC-000n`** (1:1 with the active Order); the
  row links to that Order (the booking detail is a later slice).
- Hubs gate their tabs by profile and default to the first visible tab; navigation visibility mirrors the
  backend, which stays the only guard.

## 3. Acceptance criteria — met

All list columns required by the slice are present (identifier, source order, commercial reference, status,
operator, commercial responsible, items-requiring-booking, confirmed-items, created/updated; latest-attempt
reserved). All required filters present (status, operator, commercial responsible, creation period, source
order, item type, has-failed-items). Read-tier visibility enforced at the query level. Out-of-scope items
(financial/integration dashboards, attempt/confirmation, booking detail, a reservation number of its own,
external integration) were **not** implemented.

## 4. Files changed (high level)

- **Backend (new):** `BookingRequestListItem`, `BookingRequestSearchCriteria`, `BookingRequestSpecifications`,
  `BookingRequestAccessPolicy`, `BookingItemCountsRow`, `BookingRequestListParams`, `V30` migration,
  `BookingRequestListApiIntegrationTest`. **(edited):** `BookingRequestService` (+`list`),
  `BookingRequestRepository` (+`findItemCounts`), `BookingRequestController` (+GET), `SecurityConfig`,
  `application.yml` (version).
- **Frontend (new):** `booking.service.ts`, `booking-read.guard.ts`, `features/bookings/booking-list/*`,
  `features/indicadores/indicadores-hub/*`, `features/pendencias/pendencias-hub/*` (+specs).
  **(edited):** `navigation.ts` (+spec), `auth.service.ts`, `app.routes.ts`, `shell.ts`/`shell.html` (+spec),
  `home.spec.ts`, `module-home.spec.ts`.
- **E2E (new):** `booking-listing.spec.ts`. **(edited):** `order-listing`, `sales-indicators`,
  `lead-indicators`, `lead-pending`, `lead-journey`, `lead-visibility`, `navigation-and-unsaved`,
  `proposal-creation`, `shortcuts`.
- **Docs:** `CLAUDE.md` §10 (Booking read tiers + AccessPolicy + list) and §12 (workflow-oriented navigation).

## 5. Tests

- **Backend:** `./mvnw verify` green — **503 tests** (ArchUnit + Spring Modulith + HttpErrorMapping
  completeness included). New `BookingRequestListApiIntegrationTest` = 13 tests covering the contract
  (operational-only fields), read tiers (operações/manager/director list; seller/representative → 403; finance
  → 403; unauthenticated → 401), default status exclusion (CONFIRMED+CANCELLED hidden, FAILED kept), every
  filter, the item counts, and operator-unassigned.
- **Frontend:** `ng test` green — **350 tests** (new: `booking-list.spec` 9, `indicadores-hub.spec` 6,
  `pendencias-hub.spec` 5; rewritten `navigation.spec`, updated `shell.spec`, `home/module-home` specs). `ng
  build` green.
- **E2E (full cycle `e2e:up → e2e → e2e:down`):** **46 passed**, 2 flaky-then-passed-on-retry (the
  pre-existing PrimeNG `<p-select>` option-timing flakiness in the proposal-rejection specs; not introduced
  here). New `booking-listing.spec` (3) + the navigation-updated specs all pass.

## 6. Known gaps / deferred

- `lastBookingAttemptAt` is always null and `confirmedItems` is computed from item status (always 0 today) —
  both are wired for the future **attempt/confirmation** slices (BOOK4-005..007).
- The booking own/unassigned read tiers are defined in the policy but **not seeded** (only `read:all` today),
  ready for future operator profiles.
- No **booking detail** yet (BOOK4-004) — the list row links to the source Order.
- The two flaky proposal-rejection E2E specs remain (pre-existing PrimeNG timing; pass on retry).

## 7. Recommended next prompt

> Sprint 4 / SLICE 4: **Booking Request detail** (`GET /api/bookings/{id}`, gated by the booking read tiers
> with the same `canSee` 404/403 logic) — show the source Order/Proposal/Opportunity/Lead references, the
> commercial responsible and operator, and the booking items (type, description, quantity, requires-booking,
> status), with **no** Financial/Payment/Commission data. Add the frontend booking-detail route + page
> (reachable from the list), its keyboard shortcut/`?` entry, and full TDD coverage (happy + 401/403/404).
