# Sprint 5 — Closing report (Financial Operations)

- **Date:** 2026-06-24 23:30
- **Branch:** `feature/sprint5-closeout` → `develop` → `main`
- **Version:** 0.60.1 → **0.60.2** (PATCH — sprint closeout; docs + release note + handoff paragraph, no application-code change)
- **Type:** sprint closeout (`docs:`). Customer release note: `artifacts/release-notes/v0.60.2.md`.

## 1. Sprint goal status

**Achieved.** Sprint 5 delivered the **Financial Operations** bounded context (`domain.financial`, `financial:*`
scopes, `financeiro` persona) as a coherent, end-to-end flow: from a **Commercial Order with a confirmed booking**
(Sprint 4) through creating the **Receivable**, splitting it into **installments**, registering **full and partial
payments**, consolidating the **Receivable status**, **reflecting the financial status onto the Commercial Order**,
identifying **overdue** receivables, **reversing** payments while preserving history, an **operational received-
payments view** and the **minimum financial indicators**, to a **Paid Receivable / Order identifiable as ready for
Commission Management**. The delivery was **validated end to end** (Slice 12, driving the real Sprint 3 → 4 → 5
handoff), and this closeout re-ran the full regression and documents the handoff to Sprint 6.

## 2. Completed capabilities

All planned capabilities are delivered and tested:

1. **Financial Operations bounded context** (`domain.financial`; `financial:receivable:*` read tiers +
   `financial:payment:register` / `financial:payment:reverse` operation scopes; `financeiro` persona; `PaymentMethod`
   cadastro).
2. **Receivable creation** from a Commercial Order whose `booking_status = CONFIRMED` (preserves the commercial
   origin + payer; one active Receivable per Order; starts `OPEN`).
3. **Installment schedule** (defined at creation; always sums to the total; one full-amount installment by default).
4. **Receivable listing** (operational worklist with filters + per-profile visibility; OVERDUE stays visible).
5. **Receivable detail** (summary, traceable commercial origin, payer, installment schedule, payment history).
6. **Full payment registration** (method/date/amount/user; installment → Paid, Receivable → Paid).
7. **Partial payment registration** (amount-driven; Partially Paid; multiple partials until settled; outstanding
   stays visible).
8. **Receivable status update** (state-derived `OPEN`/`PARTIALLY_PAID`/`PAID`/`OVERDUE`/`CANCELLED`; consolidated on
   every payment/reversal/overdue check).
9. **Commercial Order financial-status reflection** (event-driven, Sales-owned `financial_status`; Order never
   changes its own lifecycle, never cancelled).
10. **Overdue identification** (stored `OVERDUE` set by a daily `@Scheduled` check, per-installment-precise; visible
    in list/detail; no interest/fee/notification).
11. **Payment reversal** (reason + who/when; payment kept as Reversed; paid/outstanding + statuses recomputed;
    reflection republished; no refund/Commission).
12. **Operational received-payments view** (`Recebimentos`, in the Financeiro module + the Indicadores hub).
13. **Minimum Financial Operations indicators** (period volume — created/to-receive, received/by-method, settled +
    avg days to payment — and the current snapshot — by status, outstanding, overdue, ready-for-Commission).
14. **End-to-end validation** (Slice 12) **+ functional handoff to Sprint 6** (documented this slice).

Supporting: the **Customer (payer)** is materialized from the Lead at Order creation (cross-context read by Finance);
the **payment-method cadastro** is admin-managed; the **`ReceivableOverdueJob`** is the project's first scheduled job.

Test footprint at close: **backend 767 tests** (`verify` GREEN, incl. ArchUnit + Spring Modulith), **frontend 505
tests + build**, **E2E 58 passed**, across the forward migrations **V60–V68** (financial scopes, installments,
payment methods, payments + denormalization, register scope, installment amount-paid, order `financial_status`,
payment reversal, reverse scope).

## 3. Acceptance criteria status

- **Sprint 5 behaviour is coherent end to end** — ✓ (`FinancialOperationsEndToEndIntegrationTest`: full payment,
  partial, overdue, reversal + visibility, driven from a real Sprint-4 confirmed booking).
- **Paid Receivable is ready for Sprint 6 without recapturing basic financial data** — ✓. Every field in the handoff
  list is preserved across the two separated contexts: the **Order** keeps `financial_status = PAID`, the commercial
  **total**, the items and the **customer** + the source Proposal/Opportunity/Lead and the **commercial responsible**;
  the **Receivable** keeps the source Order/Proposal/Opportunity/Lead references, the **payer**, the **commercial
  responsible** snapshot, the **total**, the **installment schedule**, the **payments** (amount, date, method,
  registered-by user, plus any **reversed** payments kept in history), the **amount paid** / **outstanding amount**,
  and the **final status** (`PAID`). Readiness = the Order's `financial_status = PAID` (and the indicators'
  `readyForCommission`).
- **Commercial Order, Booking Request, Receivable and Payment remain separated** — ✓. Distinct aggregates/contexts;
  the Receivable references the Order read-only; the reflection onto the Order is event-driven and read-only (Sales
  keeps ownership).
- **No Commission implementation exists** — ✓ (no Commission entity/scope/endpoint/calculation/approval/payment; the
  `commissions` table does not exist — asserted in the E2E validation).
- **No Accounts Payable implementation exists** — ✓.
- **No bank reconciliation implementation exists** — ✓ (and no expenses/refund/Customer-Care/tax-invoice/bank/gateway
  implementation).
- **No future-scope feature introduced** — ✓ (closeout is docs + release note + handoff paragraph + version bump
  only; no new endpoints/entities/migrations/scopes).
- **Sprint 5 closing report produced** — ✓ (this document) + the customer release note `v0.60.2`.

## 4. Defects or gaps found

**None.** The closeout review confirmed every required handoff field already preserved in `ReceivableDetail` + the
Order's `financialStatus`; no code/contract change was needed. The only changes this slice are documentation: the
**CLAUDE.md "Handoff to Commission Management (Sprint 6)"** normative paragraph, the bilingual manual (Sprint-5-
complete + handoff note), the customer **release note**, and the version bump.

## 5. Risks

- **Readiness is a status flag, not a worklist:** an Order is "ready for Commission" by `financial_status = PAID`
  (queryable on the Order; counted by `readyForCommission`). Sprint 6 will add the eligible-orders worklist by
  reading that flag — there is no Finance-side "ready for Commission" list yet (intentional; out of Sprint 5 scope).
- **Payer document/billing placeholders:** the Customer carries the name + contacts (snapshot of the Lead); CPF/CNPJ
  and billing address are optional placeholders not yet filled — a later slice, not needed for the Sprint-6 handoff.
- **Cancellation reserved, not implemented:** `CANCELLED` exists in the Receivable/installment status model but no
  cancel action ships (the uniqueness rule already allows a new Receivable once the previous is cancelled). A
  cancellation flow is future work, out of scope.
- **No financial UI journey via the browser:** the financial flow is validated by the backend integration journey
  (the isolated E2E stack has no seeded confirmed-booking orders); the financial UI cross-cutting concerns (routes,
  guards, nav, indicators screens) are covered by the Playwright `receivable.spec`. Known limitation, not a defect.
- **Pre-existing flakes** (`booking-detail` confirm-package DOM unit test at the 5s timeout under full-suite load —
  passes in isolation; `lead-indicators` UTC-midnight straddle) are unrelated to Sprint 5.

## 6. Whether Sprint 6 can start

**Yes.** The Finance → Commission handoff is documented and the paid-financial data is in place and queryable: a
Commercial Order is identifiable as ready for Commission by `financial_status = PAID`, and it carries the total +
customer + commercial responsible + the source commercial references; the Receivable carries the payment evidence
(amounts, dates, methods, registered-by, reversals) and the final status. Commission Management can begin as a
**read** over the paid orders without any change to Financial Operations. No blockers.

## 7. Recommended first task for Sprint 6

> Begin **Sprint 6 — Commission Management**. Read CLAUDE.md and define the `domain.commission` bounded context and a
> new `commission:*` read tier. Implement only the first slice — a **read-only worklist of Commercial Orders eligible
> for Commission Management**: the Orders whose `financial_status = PAID` (and without an Expected Commission yet),
> gated by `commission:read`, showing the order number, the **commercial total** (read from the Order), the customer
> (payer), the **commercial responsible** and the source Proposal/Opportunity references, plus the payment evidence
> (the received amount + the final Receivable status) read from the paid Receivable. Create **no** Commission record/
> calculation/approval/payment yet; the Order stays owned by Sales, the Receivable owned by Financial, and Commission
> only reads. Mirror the existing list patterns (specification + access policy + read model). After finishing, report
> 1–7.
