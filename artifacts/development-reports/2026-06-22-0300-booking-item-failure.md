# Sprint 4 / Slice 8 — Register a booking-item failure and allow retry

- **Date:** 2026-06-22 03:00
- **Branch:** `feature/booking-item-failure` → `develop` → `main`
- **Version:** 0.43.0 → **0.44.0** (MINOR — new feature)
- **Migration:** `V34__booking_item_failure.sql` (booking-item failure columns; no new scope)

## 1. What was implemented

**Backend — `POST /api/bookings/{id}/items/{itemId}/fail`.** A booking-operations user marks a booking item as
**failed**, recording a reason (required), an optional note, and who/when:

- New enum `BookingFailureReason` (9 commercial-operations reasons: `NO_AVAILABILITY`, `SUPPLIER_UNAVAILABLE`,
  `INVALID_COMMERCIAL_DATA`, `MISSING_TRAVELER_DATA`, `EXTERNAL_SYSTEM_UNAVAILABLE`, `PRICE_CHANGED`,
  `MANUAL_OPERATION_ERROR`, `OUT_OF_POLICY`, `OTHER` — labels live in the frontend, like `OpportunityLossReason`).
- New `@Embeddable BookingItemFailure` VO on the item (Lombok `@Builder`, mirroring the confirmation VO):
  `failureReason`, `failureNote`, `failedBy` (the authenticated user), `failedAt` (operator-supplied,
  `@PastOrPresent`). **No monetary data.** Columns `failure_*`, all nullable.
- `BookingItem.fail(failure)` — fails only an item that **requires booking** (else
  `BookingItemNotFailableException`, **422** `booking.item-not-failable`) and is **not already resolved**
  (CONFIRMED/CANCELLED → reuses `BookingItemAlreadyResolvedException`, **422**); sets status `FAILED`. A `FAILED`
  item may be re-failed (updates the reason). The existing `confirm(c, expectedType)` guard already permits
  confirming a `FAILED` item, so **retry** needs no change.
- `BookingRequest.failBookingItem(itemId, failure, byUser)` finds the item (`BookingItemNotFoundException`
  **404**) and runs a **single unified roll-up** (`rollUpStatus()`) over the items requiring booking, now used by
  both confirm and fail: all CONFIRMED → `CONFIRMED`; ≥1 CONFIRMED (not all) → `PARTIALLY_CONFIRMED`; else ≥1
  FAILED → `FAILED`. So failing the only requiring item ⇒ `FAILED`; failing one with another confirmed ⇒
  `PARTIALLY_CONFIRMED`; later confirming a failed item **reconsolidates** the request.
- `BookingRequestService.failBookingItem`, `FailBookingItemCommand`, `FailBookingItemRequest` (Bean Validation:
  `@NotNull` reason, `@Size(2000)` note, `@NotNull @PastOrPresent` date), controller POST returning the refreshed
  `BookingRequestDetail`. Reuses the **`booking:request:update`** scope (no new scope; `SecurityConfig` matcher
  extended). `toDetail` now also resolves the `failedBy` name; the detail `Item` gained a nullable `Failure`
  record (`failureReason`, `failureNote`, `failedByName`, `failedAt`). `HttpErrorMapping` maps the new exception
  to 422.

**Frontend.** `BookingService.failBookingItem` + the failure type/field on the booking item. The reservation
detail's items table now offers a **"Falhar"** action (red) for any item that **requires booking and is not
resolved** (alongside "Confirmar"); the **fail dialog** captures the reason (select of the 9), the date
(`[maxDate]="now"`) and an optional note. A new **"Problemas operacionais"** card lists the failed items with
their reason / note / who / when. A `FAILED` item still offers **"Confirmar"** (the retry path). The
three-dialog management (attempt / confirm / fail) — `anyDialogOpen`, `liveSnapshot`, `closeOpenDialog`,
`requestClose`, unsaved-changes guard — was extended to the fail dialog.

## 2. Functional rules covered

Only an item that requires booking can be failed (else 422 `booking.item-not-failable`); a CONFIRMED/CANCELLED
item can't be failed (422 `booking.item-already-resolved`); the reason is required and the date must be present
and not in the future (400); the note is optional; the failure records who (authenticated user) and when
(operator-supplied date); the item becomes `FAILED`; the request status consolidates (`FAILED` /
`PARTIALLY_CONFIRMED`) from the items requiring booking. A failed item **stays visible** as an operational
problem, **may receive new attempts** (Slice 5 is unchanged — an attempt may reference a `FAILED` item and the
request stays `FAILED`), and **may be confirmed later** (retry) — which reconsolidates the request. Failing
**never** cancels the Commercial Order and creates **no** Financial / Payment / Commission / Customer Care data
(asserted: the failure object's keyset is exactly `failureReason, failureNote, failedByName, failedAt`).

## 3. Acceptance criteria

- Authorized users can mark a booking item as Failed; the reason is required; the user and date are recorded —
  **met**.
- Failed items are visible in the detail and surface as operational problems — **met** (the "Problemas
  operacionais" card + the persisted `Failure`).
- A new attempt can be registered after a failure, and a failed item can later be confirmed — **met** (covered
  by integration tests).
- No Customer Care / Financial / Payment / Commission data is created; existing behavior still works — **met**.

## 4. Files changed

**Backend (new):** `domain/booking/model/BookingFailureReason.java`, `…/model/BookingItemFailure.java`,
`…/exception/BookingItemNotFailableException.java`, `…/service/data/FailBookingItemCommand.java`,
`application/api/dto/FailBookingItemRequest.java`, `resources/db/migration/V34__booking_item_failure.sql`,
`test/…/application/api/BookingItemFailApiIntegrationTest.java`.
**Backend (edited):** `BookingItem.java`, `BookingRequest.java` (unified `rollUpStatus`),
`BookingRequestService.java`, `BookingRequestDetail.java`, `BookingRequestController.java`,
`infra/web/HttpErrorMapping.java`, `infra/security/SecurityConfig.java`, `resources/messages.properties`,
`resources/application.yml` (0.44.0), `BookingRequestDetailApiIntegrationTest.java` (item keyset += `failure`).
**Frontend (edited):** `core/api/booking.service.ts`, `features/bookings/booking-detail/{ts,html,css}`,
`…/booking-detail.spec.ts`.
**Docs:** `CLAUDE.md` §10 (failure flow), `artifacts/user-manual/*.{pt-BR,en-US}.md` (see §6),
this report.

## 5. Tests

- **Backend:** `BookingItemFailApiIntegrationTest` — **15** new tests: fail → FAILED + reason recorded +
  roll-up to FAILED; confirm one + fail other → PARTIALLY_CONFIRMED; required reason/date 400; future date 400;
  note optional; SERVICE_FEE → 422 not-failable; already-confirmed → 422 already-resolved; item not in request →
  404; failed item receives a new attempt and stays FAILED; failed item later confirmed → CONFIRMED + request
  reconsolidates; `?hasFailedItems=true` finds it; director/seller 403; 401; unknown request 404; can't-see 403.
  Detail keyset test extended. **Full `./mvnw verify` green** (ArchUnit + Modulith + Spotless + Checkstyle).
- **Frontend:** `booking-detail.spec.ts` — fail-reason labels, `canFailItem` visibility (scope / requires-booking
  / not-resolved / a FAILED item still failable), `canSaveFail`, `failItem` calls the service + refreshes +
  success toast, null note when blank, a FAILED item still offers Confirmar (retry), the operational-problems
  card DOM (filled + empty), the "Falhar" action DOM. **`ng test` 379 passed; `ng build` clean.**
- **E2E (full cycle, `e2e:up → e2e → e2e:down`):** **49 passed, 1 flaky** — `proposal-rejection` (PrimeNG
  `<p-select>` option "element is not stable", **passed on retry #1**), the documented pre-existing flake,
  unrelated to the booking changes. No new E2E for the fail happy-path: there is still no UI to **create** a
  Booking Request, so the isolated stack can't seed one — covered by the backend integration tests, as in S3–S7.

## 6. Known gaps / out of scope

Out of scope as specified: automatic / external-integration retry, customer notification, commercial
renegotiation, the cancellation and refund flows, cancelling the Commercial Order, and any Financial / Payment /
Commission / Customer Care data. Failing a `SERVICE_FEE` / non-requiring item is rejected. A `FAILED` request
stays `FAILED` while only new attempts are registered (an attempt is history) — it reconsolidates when the item
is **confirmed** (no auto-resume; a deliberate, signalled decision). The booking-request lifecycle transitions
to `IN_PROGRESS` other than via the first attempt, and reflecting the booking status back onto the Order, remain
later slices.

**User manual (owner directive this slice — now a standing rule):** the bilingual manual had drifted — the
entire Sprint 4 **Reservas** module was absent and the navigation still described a non-existent "Vendas"
module. Both editions (`pt-BR` + `en-US`) were brought current: a new **"Operando reservas / Booking
operations"** section (the worklist, the detail, attempts, confirming Travel Package and Car Rental items, and
the failure-with-retry flow); the module map corrected to **Comercial** (Leads·Oportunidades·Propostas·Pedidos),
**Reservas**, **Acompanhamento** (Pendências + Indicadores hubs, tabbed by area) and **Cadastros**; the
`Vendas → …` paths, the indicator locations (now under **Acompanhamento → Indicadores**), the `g`-shortcuts (`g
r` Reservas, booking `a`), the scope header (v0.44.0) and the "What's next" footer all updated. A memory was
saved to update the manual **every slice** going forward.

## 7. Recommended next prompt

> Read CLAUDE.md and the current Sprint 4 Booking Operations implementation. Continue Sprint 4. Implement only
> the next functional slice: **assign / take a Booking Request operator** — let an authorized user assign a
> Booking Request to a booking operator (or take it themselves), recording who/when, with the operator coming
> from the booking personas; reflect the operator on the list/detail and in the "unassigned" visibility tier.
> Out of scope: the lifecycle reflection onto the Commercial Order, cancellation, and any Financial data. After
> finishing, report 1–7.
