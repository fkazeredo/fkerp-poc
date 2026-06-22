# Sprint 4 / Slice 7 — Manually confirm a Car Rental booking item

- **Date:** 2026-06-22 00:30
- **Branch:** `feature/booking-confirm-car-rental` → `develop` → `main`
- **Version:** 0.42.0 → **0.43.0** (MINOR — new feature)
- **Migration:** `V33__booking_item_car_confirmation.sql` (car-rental confirmation columns; no new scope)

## 1. What was implemented

**Backend — `POST /api/bookings/{id}/items/{itemId}/confirm-car-rental`.** Mirrors the Slice 6 Travel Package
flow for `CAR_RENTAL` items:

- The single `@Embeddable BookingItemConfirmation` was **extended** with the car-rental metadata (`rentalCompany`,
  `pickupLocation`, `dropoffLocation`, `pickupAt`, `dropoffAt`, `carCategory`) — the type-irrelevant fields stay
  null; one VO and one `Confirmation` read shape serve both item types. **No monetary data.**
- `BookingItem.confirmCarRental(...)` (DRY: both flows delegate to a private `confirm(c, expectedType)` guard)
  confirms only a `CAR_RENTAL` item that requires booking and is not already resolved
  (`BookingItemNotConfirmableException` / `BookingItemAlreadyResolvedException`, **422**) and sets status
  CONFIRMED. `BookingRequest.confirmCarRentalItem(...)` finds the item (`BookingItemNotFoundException` **404**)
  and reuses the existing **status roll-up** (all items requiring booking confirmed → `CONFIRMED`, otherwise
  `PARTIALLY_CONFIRMED`).
- `BookingRequestService.confirmCarRentalItem`, `ConfirmCarRentalCommand`, `ConfirmCarRentalRequest` (Bean
  Validation: required system/locator/`@PastOrPresent` date; pickup/dropoff are optional and may be in the
  future), controller POST returning the refreshed `BookingRequestDetail`. Reuses the **`booking:request:update`**
  scope (no new scope; `SecurityConfig` matcher extended). `V33` adds the car columns only.

**Frontend.** `BookingService.confirmCarRental` + the car fields on the confirmation type. The reservation
detail's **"Confirmar"** action now appears for **both** Travel Package and Car Rental confirmable items; the
confirm **dialog is type-aware** (the header and the type-specific fields switch on the item type — travel
metadata for a package, rental company / pickup-dropoff location & date-time / car category for a car), routing
the submit to `confirmTravelPackage` or `confirmCarRental`. The **"Confirmações de reserva"** card renders the
car-rental block for confirmed car items.

## 2. Functional rules covered

Only a `CAR_RENTAL` item that requires booking can be confirmed via this flow (else 422); a confirmed/cancelled
item can't be re-confirmed (422); external locator and system are required (400); the confirmation records who
(authenticated user) and when (operator-supplied date); the item becomes CONFIRMED; the request status
consolidates (PARTIALLY_CONFIRMED / CONFIRMED) from the items requiring booking; no external integration is
called; no Financial/Payment/Commission/Customer Care data and no voucher are created (asserted: the confirmation
contract has only operational fields; items carry no monetary data).

## 3. Acceptance criteria covered

Authorized users (operações + Manager) confirm a Car Rental item ✓ (Director/seller → 403); external locator
required ✓; external system required ✓; confirmation user + date recorded ✓; item becomes CONFIRMED ✓; request
status updates per confirmed/pending items ✓ (a package+car request reaches CONFIRMED only when both are
confirmed); no external integration ✓; no Financial/Payment/Commission behavior ✓; existing Booking Operations
behavior (create/list/detail/attempts/travel-package confirm) unchanged ✓ (full suite green).

## 4. Files changed

- **Backend (new):** `ConfirmCarRentalCommand`, `ConfirmCarRentalRequest`, `V33`,
  `BookingItemConfirmCarRentalApiIntegrationTest`. **(edited):** `BookingItemConfirmation` (+6 car fields),
  `BookingItem` (DRY `confirm` + `confirmCarRental`), `BookingRequest` (+`confirmCarRentalItem`),
  `BookingRequestService` (+`confirmCarRentalItem`), `BookingRequestDetail` (`Confirmation` +6 car fields),
  `BookingRequestController` (+POST), `SecurityConfig`, `application.yml`, the Travel Package test keyset.
- **Frontend (edited):** `booking.service.ts`, `features/bookings/booking-detail/*` (ts/html/spec).
- **Docs:** `CLAUDE.md` §10 (Car Rental confirmation).

## 5. Tests / validations added

- **Backend:** `BookingItemConfirmCarRentalApiIntegrationTest` — 14 tests: confirm + record car fields + roll-up
  to CONFIRMED, partial roll-up (package+car → PARTIALLY then both → CONFIRMED), required system/locator/date →
  400, future date → 400, travel-package / service-fee via the car endpoint → 422 not-confirmable, re-confirm →
  422 already-resolved, item not in request → 404, Director → 403, seller → 403, can't-see → 403, unknown request
  → 404, unauthenticated → 401. The Travel Package test keyset was updated to the unified confirmation shape.
  Full `./mvnw verify` green — **550 tests**.
- **Frontend:** `booking-detail.spec` — car item is confirmable, the dialog opens in car-rental mode, `confirmItem`
  routes to `confirmCarRental`, the car confirmation block renders. `ng test` green — **370 tests**; `ng build`
  green.
- **E2E:** full regression cycle `e2e:up → e2e → e2e:down` — **48 passed** (incl. the changed `booking-detail`
  and `booking-listing` specs), `proposal-rejection` flaked then passed on retry, and **1 pre-existing
  `lead-indicators` failure** — the same UTC-midnight straddle root-caused in the Slice 5 report (this run was at
  23:29 local UTC−3 = 02:29 **UTC**, inside the window). Neither flake touches the booking code in this slice. No
  new confirm E2E (there is no UI to **create** a Booking Request, so the isolated stack can't seed one to click
  into; the confirm happy path is covered by the integration test, as in Slices 3–6).

## 6. Known gaps

- The single confirmation VO/read shape carries both travel and car fields (the irrelevant ones null) — a Rule
  Zero choice (no second table); the frontend renders per type.
- `pickupAt`/`dropoffAt` are stored as instants without ordering validation (optional metadata; deferred unless
  requested).
- Confirming `SERVICE_FEE`/`OTHER` items, item **failure** and cancellation remain later slices (the roll-up
  already handles "all requiring items confirmed").
- No confirm E2E (no create-booking UI to seed); covered by the backend integration test.
- The known pre-existing E2E flakes (`lead-indicators` UTC-midnight straddle in UTC−3; `proposal-rejection`
  PrimeNG `<p-select>` timing) may recur depending on the run time/retry — unrelated to this slice.

## 7. Recommended next implementation prompt

> **Sprint 4 / SLICE 8: Register a booking item failure.** Add `POST /api/bookings/{id}/items/{itemId}/fail`
> (gated by `booking:request:update` + `canSee`) to record a failure reason on a booking item that requires
> booking and is not already resolved → item status `FAILED`, kept on the detail with the reason, and
> consolidate the request status per fixed rules (e.g. a `FAILED` requiring item blocks `CONFIRMED` and surfaces
> via the list's has-failed filter). Still no Financial/Payment/Commission and no external integration. Cover
> happy + all sad paths (401/403/404/422) and the status roll-up.
