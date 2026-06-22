# Sprint 4 / Slice 11 — Minimum Booking Operations indicators

- **Date:** 2026-06-22 17:00
- **Branch:** `feature/booking-indicators` → `develop` → `main`
- **Version:** 0.46.0 → **0.47.0** (MINOR — new feature)
- **Migration:** `V37__booking_request_confirmed_at.sql` (denormalized `confirmed_at`; no new scope)

## 1. What was implemented

**Backend — `GET /api/bookings/indicators`.** A read-only indicators view, mirroring the Order/Opportunity
indicator pattern (two scopes: volume-in-period + current snapshot):

- New `BookingIndicatorQueries` (`@Component`, Criteria; every query reuses the caller's `BookingRequestAccessPolicy`
  visibility `Specification` + the optional period, all rooted at `BookingRequest`): `countByStatus`,
  `countItemsByType` (join `r.items`), `countFailedItems`, and `avgConfirmationSeconds` (selects the
  `(createdAt, confirmedAt)` pairs of the visible, confirmed-in-period requests and averages them in Java — bounded
  set, DB-portable, correct narrowing for every tier).
- The average reads a new **denormalized `confirmed_at`** (V37): `BookingRequest.consolidateStatus` stamps it
  `Instant.now()` the first time the request reaches `CONFIRMED`.
- New read model `BookingIndicators` (`total`, `byStatus`, `itemsByType`, `failedItems`, `readyForFinance`,
  `avgConfirmationSeconds` nullable) and `BookingRequestService.indicators(...)`: volume = countByStatus(period) (→
  `byStatus` + `total`) + itemsByType + failedItems + avg; snapshot `readyForFinance` =
  countByStatus(null, null).get(CONFIRMED). Controller `GET /indicators` (createdFrom/createdTo ISO dates), reusing
  the existing `toStartOfDayUtc`; the `/api/bookings/**` GET security matcher already covers it — **no SecurityConfig
  change**.

**Frontend.** `BookingService.indicators`, the `BookingIndicators`/`BookingStatusCount`/`BookingItemTypeCount`
types, a new **`booking-indicators`** screen (period KPIs Total / Failed items, snapshot KPIs Ready-for-Finance /
Avg-time, by-status and by-item-type CSS bars; period month-to-date), a **Reservas** tab in the **Acompanhamento →
Indicadores** hub (gated by `canSeeBookings()`), and the navigation updated so a booking-only profile reaches the
Indicadores hub.

## 2. Functional rules covered

The indicators report the requested minimum set: total in period, by status (pending / in progress / partially
confirmed / confirmed / failed / cancelled), items by type, failed items, ready-for-Finance (= currently CONFIRMED),
and the average creation→confirmation time. Visibility respects the Booking read tiers + `BookingRequestAccessPolicy`
(a manager sees the global numbers; no unauthorized request is exposed; a seller/representative with no booking read
tier → 403). It is operational, **not** an executive dashboard: read-only, no Financial/Payment/Commission and no
external-integration metrics (integrations do not exist yet).

## 3. Acceptance criteria

- Authorized users see Booking Requests by status — **met** (`byStatus`).
- Authorized users see failed Booking Items — **met** (`failedItems` + `itemsByType`).
- Authorized users see Booking Requests ready for Financial Operations — **met** (`readyForFinance`).
- Indicators do not expose unauthorized Booking Requests — **met** (visibility tiers + policy at the query level;
  seller → 403, integration-tested).
- Indicators do not include Finance/Payment/Commission data — **met** (read-model keyset asserts only the
  operational figures).
- Existing Booking Operations behaviour still works — **met** (Slices 4–10 green; only `consolidateStatus` extended).

## 4. Files changed

**Backend (new):** `domain/booking/repository/BookingIndicatorQueries.java`,
`domain/booking/service/data/BookingIndicators.java`,
`resources/db/migration/V37__booking_request_confirmed_at.sql`,
`test/…/application/api/BookingIndicatorsApiIntegrationTest.java`.
**Backend (edited):** `BookingRequest.java` (`confirmedAt` + `consolidateStatus`), `BookingRequestService.java`
(`indicators`), `BookingRequestController.java` (`GET /indicators`), `application.yml` (0.47.0).
**Frontend (new):** `features/bookings/booking-indicators/{ts,html,css,spec}`.
**Frontend (edited):** `core/api/booking.service.ts`, `features/indicadores/indicadores-hub/{ts,html,spec}`,
`core/navigation/navigation.ts`.
**Docs:** `CLAUDE.md` §10 (indicators), bilingual user manual (§4 + §10.9), this report.

## 5. Tests / validations

- **Backend:** `BookingIndicatorsApiIntegrationTest` (6: totals/by-status/items-by-type/failed-items + the
  read-model keyset proving no financial fields; ready-for-Finance is the current confirmed count independent of the
  period; the average creation→confirmation time is computed correctly and is null with no confirmed data; manager +
  board read-all consult; seller without a booking read tier → 403; 401). **`./mvnw verify` GREEN — 611 tests.**
- **Frontend:** `booking-indicators.spec` (period + snapshot KPIs, by-status/by-type bars, `formatDuration`
  days/hours/minutes + null, period apply/clear, error) and `indicadores-hub.spec` (the Reservas tab appears with
  `canSeeBookings`; a booking-only profile sees only Reservas). **`ng test` 401 passed; `ng build` clean.**
- **E2E (full cycle `e2e:up → e2e → e2e:down`):** **49 passed, 1 flaky** — `proposal-rejection` (PrimeNG
  `<p-select>`, passed on retry), the documented pre-existing flake. No new E2E for the indicator data: there is no
  UI to **create** a Booking Request, so the isolated stack cannot seed bookings — covered by the integration tests,
  as in S3–S10.

## 6. Known gaps

The average reads a **denormalized `confirmed_at`** stamped on the first `CONFIRMED` transition via `Instant.now()`
in the aggregate (a localized clock use, consistent with the existing denormalizations); requests confirmed **before**
V37 have a null `confirmed_at` and are excluded from the average (a forward-looking metric). The average is exposed as
a plain `avgConfirmationSeconds` (Long, nullable) — the frontend formats it — to avoid `Duration` JSON-serialization
ambiguity. Only the `read:all` tier is seeded today; own/unassigned exist in the policy for future operator profiles.
Out of scope as specified: supplier-performance analytics, integration-health metrics, financial/commission/customer-
care metrics, the executive dashboard.

## 7. Recommended next prompt

> Read CLAUDE.md and the current Sprint 4 Booking Operations implementation. Continue Sprint 4. Implement only the
> next functional slice: **assign / take a Booking Request operator** — let an authorized user assign a Booking
> Request to a booking operator (or take it themselves), recording who/when, with the operator from the booking
> personas; the unassigned-operator pending reason then clears and the operator appears on the list/detail. Out of
> scope: the explicit cancellation flow and any Financial data. After finishing, report 1–7.
