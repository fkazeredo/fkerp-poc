# Sprint 6 — Closing report (Commission Management)

- **Date:** 2026-06-26 00:30
- **Branch:** `feature/sprint6-closeout` → `develop` → `main`
- **Version:** 0.72.1 → **0.72.2** (PATCH — sprint closeout; docs + release note + Sprint 7 handoff paragraph, no application-code change)
- **Type:** sprint closeout (`docs:`). Customer release note: `artifacts/release-notes/v0.72.2.md`.

## 1. Sprint goal status

**Achieved.** Sprint 6 delivered the **Commission Management** bounded context (`domain.commission`, `commission:*`
scopes) as a coherent, end-to-end flow: from a **Commercial Order whose Receivable is Paid** (Sprint 5) through
defining a **Commission Rule**, **generating** the Expected Commission, **eligibility** once the Receivable is paid,
**listing/detail**, **approval**, **rejection/cancellation**, **manual payment** (with reversal), the **statement by
beneficiary**, the **Commercial Order commission-status reflection**, the **operational commission view** and the
**minimum commission indicators**, to a **Paid Commission that closes the first commercial-financial cycle**. The
delivery was **validated end to end** (Slice 13) and this closeout re-ran the full regression and documents the
handoff to Sprint 7 (Customer Care / post-sale). Commission Management **owns neither** the Order (Sales) nor the
Receivable/Payment (Financial) — it only **reads** them.

## 2. Completed capabilities

All planned Sprint 6 capabilities are delivered and tested:

1. **Commission Management bounded context** (`domain.commission`; `commission:rule:manage`, the `commission:read` /
   `commission:read:all` read tiers, and the `commission:create` / `:approve` / `:reject` / `:cancel` / `:pay` /
   `:payment:reverse`-style operation scopes; the `CommissionResolutionReason` cadastro for reject/cancel reasons).
2. **Commission Rule** creation/management (percentage; name; validity window; target type + optional specific user;
   safe-percentage limit with explicit override).
3. **Expected Commission generation** from a closed Order (preserves the commercial origin + beneficiary + applied
   rule snapshot; amount from the received amount or the commercial total; one active commission per Order; `EXPECTED`).
4. **Commission eligibility** (`EXPECTED → ELIGIBLE`) driven by the Financial `ReceivableStatusChanged(PAID)` event
   (and at generation when the Receivable is already paid); no partial eligibility, no clawback.
5. **Operational Commission list** (paginated, filtered, per-tier visibility; operational statuses by default).
6. **Commission detail** (full traceable origin, calculation, rule snapshot, lifecycle stamps).
7. **Approval** of an Eligible commission (`ELIGIBLE → APPROVED`; segregation of duties — the beneficiary cannot
   approve their own; approver + notes recorded).
8. **Reject / cancel** (`ELIGIBLE → REJECTED`; `EXPECTED`/`APPROVED → CANCELLED`; required reason cadastro; voided
   commissions stay historically visible; touch no Order/Receivable).
9. **Manual Commission Payment** (`APPROVED → PAID`, full amount, payment method cadastro) **+ reversal** (kept in
   history; status re-derived).
10. **Commission statement by beneficiary** (entries + per-status totals; own-tier callers locked to themselves).
11. **Commercial Order commission-status reflection** (Slice 10 — event-driven, Sales-owned `commission_status`:
    `EXPECTED`/`ELIGIBLE`/`APPROVED`/`PAID`/`ISSUE`; read-only; never changes the Order lifecycle).
12. **Operational Commission view** (Slice 11 — `GET /api/commissions/summary`: count + total amount by status and by
    beneficiary, same filters/visibility as the list; the `Resumo operacional` block on `/comissoes`).
13. **Minimum Commission indicators** (Slice 12 — `GET /api/commissions/indicators`: snapshot by status/beneficiary +
    pending-approval/pending-payment amounts; paid-in-period; eligibility→approval & approval→payment latency averages;
    the `Comissões` tab of the Indicadores hub).
14. **End-to-end validation** (Slice 13) **+ functional handoff to Sprint 7** (documented this slice).

Test footprint at close: **backend 945 tests** (`./mvnw verify` GREEN, incl. ArchUnit + Spring Modulith + JaCoCo),
**frontend `ng test` + `ng build`** GREEN (the only red runs are the documented full-suite-load DOM flakes — all pass
in isolation), **E2E 72 passed** on the isolated 4201 stack (the lone failure is the documented UTC-midnight
`lead-indicators` flake; `commission-rule` is a documented zoneless+PrimeNG flake that passes on retry). Forward
migration **V82** (Order `commission_status`); Slices 11–13 added **no migration** (pure reads / tests).

## 3. Acceptance criteria status

- **Sprint 6 behaviour is coherent end to end** — ✓ (the commission lifecycle is exercised by
  `CommissionApiIntegrationTest` and `OrderCommissionStatusReflectionApiIntegrationTest`, driving the real Sprint
  3 → 4 → 5 → 6 handoff; Slice 13 added the void-flow boundary tests).
- **Paid Commission closes the first commercial-financial cycle** — ✓. The completed-commission record is preserved
  across the **separated** aggregates/contexts (§4).
- **Commercial Order, Receivable, Payment and Commission remain separated** — ✓ (distinct aggregates; Commission
  references the Order/Receivable read-only and writes neither; the Order keeps its own `commission_status` reflection
  via a Sales-owned listener).
- **No Payroll / Accounts Payable / Tax / Accounting / bank integration / refund / Customer Care / post-sale
  cancellation / advanced-split / margin-/target-based commission** — ✓ (asserted by the `doesNotContain` guards on
  the list/detail/summary/indicators contracts and by scope; none implemented).
- **No future-scope feature introduced; Sprints 1–5 behaviour intact** — ✓ (full regression GREEN).
- **Sprint 6 closing report produced** — ✓ (this document + the customer release note `v0.72.2.md`).

## 4. Handoff to Sprint 7 (Customer Care / post-sale) — data preservation

A completed commission preserves the full record so Sprint 7 (and any post-sale review) starts without recapturing
data, split across the separated aggregates (`CommercialOrder` ⟂ `BookingRequest` ⟂ `Receivable`/`ReceivablePayment` ⟂
`Commission`):

- **Commission** (`domain.commission`) — source `commercialOrderId` / `proposalId` / `opportunityId` / `leadId`; the
  **beneficiary** (commercial responsible snapshot); the applied **`ruleId` + `rulePercentage`** snapshot; the
  **`baseAmount` + `basisType` + `amount`**; **`eligibleAt`**; the **approval** (`approvedBy`/`approvedAt`/
  `approvalNotes`); the **rejection/cancellation** (`resolutionReason`/`resolutionNote`/`resolvedBy`/`resolvedAt`); the
  **payment** (`paidAt`/`paidAmount`/`paymentDate`/`paymentMethod`/`paymentNote`/`paidBy`) incl. reversed payments; and
  the **final `status`**. All surfaced by `CommissionDetail`.
- **Commercial Order** (owned by Sales) — the read-only **`commission_status`** summary (Slice 10) alongside
  `booking_status` and `financial_status`; the **commercial total**, the items and the **customer**.
- **Receivable** (owned by Financial) — the closed **commercial-financial cycle reference**: `financial_status = PAID`
  + the Receivable's payments.

No write crosses the boundary in the handoff; Sprint 7 only reads. Adding Customer Care / post-sale is Sprint 7 and was
not started.

## 5. Inconsistencies found / fixed

**None requiring code change.** The closeout verified the handoff-data preservation against the spec list (all present)
and re-ran the full regression. The only documentation added is the Sprint 7 handoff paragraph (CLAUDE.md), the
customer release note and this report.

## 6. Risks / known flakes (carried, not introduced)

- `lead-indicators` E2E fails after UTC midnight (month-to-date vs UTC next-day lead storage — a pre-existing CRM edge).
- `booking-detail` / list-table component DOM tests time out under full-suite load (pass in isolation).
- `commission-rule` E2E zoneless + PrimeNG inputNumber flake (passes on retry).

None block the Sprint 6 delivery.

## 7. Recommended next implementation prompt

Begin **Sprint 7 — Customer Care / post-sale operations**, slice 1 (the bounded context skeleton + the first post-sale
entity), reading the completed-commission / paid-order handoff (read-only) and keeping the Order, Receivable, Payment
and Commission aggregates separated.
