# Sprint 4 — Closing report (Booking Operations)

- **Date:** 2026-06-22 21:00
- **Branch:** `feature/sprint4-closeout` → `develop` → `main`
- **Version:** 0.47.1 → **0.47.2** (PATCH — sprint closeout; docs + release note, no application-code change)
- **Type:** sprint closeout (`docs:`). Customer release note: `artifacts/release-notes/v0.47.2.md`.

## 1. Sprint goal status

**Achieved.** Sprint 4 delivered the **Booking Operations** bounded context as a coherent, end-to-end flow: from a
Commercial Order **Pending Booking** (Sprint 3) through creating the reservation, working it manually (attempts,
package/car confirmation, failure + retry), to a **Confirmed** (or **Partially Confirmed** / **Failed**) booking
whose **consolidated status is reflected onto the Commercial Order**, with an operational **pending-items worklist**
and minimum **indicators**. It is a back-office, **manual** process — no external integration, no Finance. The
delivery was **validated end to end** (Slice 12) and this closeout re-ran the full regression.

## 2. Completed capabilities

All 14 planned capabilities are delivered and tested:

1. **Booking Operations bounded context** (`domain.booking`, `booking:*` scopes, `operacoes` persona).
2. **Booking Request creation** from a `PENDING_BOOKING` Commercial Order (preserves the source references; one
   active request per order).
3. **Booking Item classification** (Travel package / Car rental require booking; Service fee not; Other only when
   marked).
4. **Booking Request listing** (operational worklist with filters + per-profile visibility).
5. **Booking Request detail** (summary, traceable sources, items + statuses, attempts, confirmations, problems).
6. **Manual booking attempts** (append-only history; Pending → In Progress).
7. **Manual Travel Package confirmation** (external system/locator/date + travel metadata).
8. **Manual Car Rental confirmation** (external system/locator/date + car metadata).
9. **Booking failure and retry** (reason + note + who/when; failed item stays visible, may be retried/confirmed).
10. **Consolidated Booking Request status** (state-derived from items + attempts; Pending / In Progress /
    Partially Confirmed / Confirmed / Failed; Cancelled reserved).
11. **Commercial Order booking-status reflection** (event-driven, Sales-owned `booking_status`; Order never
    changes its own lifecycle, never cancelled).
12. **Operational pending items** (worklist with reason tags; 7-day staleness window).
13. **Minimum Booking Operations indicators** (volume by status / items by type / failed; ready-for-Finance
    snapshot; average creation→confirmation time).
14. **End-to-end validation** (Slice 12) **+ functional handoff to Sprint 5** (documented this slice).

Test footprint at close: **backend 615 tests** (verify GREEN, incl. ArchUnit + Modulith), **frontend 401 tests +
build**, **E2E 49 passed** (1 documented pre-existing flake), across 7 forward migrations (V31–V37).

## 3. Acceptance criteria status

- **Sprint 4 behaviour is coherent end to end** — ✓ (`BookingOperationsEndToEndIntegrationTest`: main confirmation,
  failure+retry, partially-confirmed, visibility).
- **Confirmed Booking Request is ready for Sprint 5 without recapturing basic booking data** — ✓. Every field in
  the handoff list is preserved: source Order/Proposal/Opportunity/Lead, commercial responsible, booking operator,
  items + types, confirmed external locators, supplier/external-system names, confirmation dates, attempt/failure
  history, consolidated status, and **the commercial total by reference** (read from the Order via the preserved
  `commercialOrderId`), plus readiness (`status = CONFIRMED` + the Order's `booking_status = CONFIRMED`).
- **Commercial Order and Booking Request remain separated** — ✓. They are distinct aggregates/contexts; the booking
  references the order read-only and carries **no monetary data** (the total stays on the Order — owner decision).
- **No Financial / Commission / external-integration implementation exists** — ✓ (no Receivable/Payment/Commission/
  Customer-Care entities; no external client/adapter; confirmations are operator-entered).
- **No future-scope feature introduced** — ✓ (closeout is docs + release note + version bump only; no new
  endpoints/entities/migrations).
- **Sprint 4 closing report produced** — ✓ (this document) + the customer release note `v0.47.2`.

## 4. Defects or gaps found

**None.** The closeout review found every required handoff field already preserved (the order **total** is
available by reference via the linked Order — owner-confirmed approach — so no booking contract change was needed).
The only changes this slice are documentation: the **CLAUDE.md §10 "Handoff to Financial Operations"** normative
paragraph, the bilingual manual (handoff note + Sprint-4-complete status), the customer **release note**, and the
version bump.

## 5. Risks

- **No "create booking" UI:** a booking request is created via the API by operations; the full journey is therefore
  validated by the backend integration test, not by a browser E2E (a create-booking screen would be a future UX
  slice). Known limitation, not a defect.
- **Handoff via reference:** Sprint 5 must read the commercial **total** from the Commercial Order (through the
  booking's preserved `commercialOrderId`) — the booking deliberately holds no amount. This keeps the contexts
  separated but means Finance always joins booking → order for the money.
- **Cancellation is reserved, not implemented:** `CANCELLED` exists in the status model but no cancel action ships
  yet; the consolidation defends against overriding it. A post-sale cancellation/refund flow is explicitly out of
  scope and remains future work.
- Pre-existing E2E flakes (`proposal-rejection` PrimeNG `<p-select>`; `lead-indicators` UTC-midnight straddle) are
  unrelated to Sprint 4 and pass on retry.

## 6. Whether Sprint 5 can start

**Yes.** The Booking → Finance handoff is documented and the confirmed-booking data is in place and queryable: a
Commercial Order is identifiable as ready for Finance by `booking_status = CONFIRMED`, and it carries the total +
customer references; the booking carries the reservation evidence (locators, systems, dates) and the source links.
Finance can begin as a **read** over the confirmed orders without any change to Booking. No blockers.

## 7. Recommended first task for Sprint 5

> Begin **Sprint 5 — Financial Operations**. Read CLAUDE.md and define the `domain.finance` bounded context and a
> new `finance:*` read tier. Implement only the first slice — a **read-only worklist of Commercial Orders ready for
> Financial Operations**: the Orders whose `booking_status = CONFIRMED` (and not yet invoiced), gated by
> `finance:order:read`, showing the order number, the **commercial total** (read from the Order), the customer
> (Lead) and the source Proposal/Opportunity references, plus the booking confirmation evidence (locators/dates)
> read from the confirmed Booking Request. Create **no** Receivable/Payment/Commission yet; the Order stays owned by
> Sales and Finance only reads. Mirror the existing list patterns (specification + access policy + read model). After
> finishing, report 1–7.
