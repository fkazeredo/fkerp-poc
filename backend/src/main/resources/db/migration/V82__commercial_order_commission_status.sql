-- Commission Management / Sprint 6 Slice 10: reflect the Commission status summary onto the Commercial Order.
-- The Order stays owned by Sales & Proposals — this nullable column is a read-only reflection written by a Sales event
-- listener reacting to the Commission Management context (never by Commission), mirroring booking_status /
-- financial_status. Null until a Commission exists for the Order; a Rejected/Cancelled commission reflects ISSUE.
-- Adds no scope and changes no Receivable/Payment/payroll/tax/accounting data.
ALTER TABLE commercial_orders
    ADD COLUMN commission_status VARCHAR(60)
        CHECK (commission_status IN ('EXPECTED', 'ELIGIBLE', 'APPROVED', 'PAID', 'ISSUE'));

-- Backfill from the active (non-voided) Commission, if any (at most one active per Order) → its status.
UPDATE commercial_orders o
SET commission_status = c.status
FROM commissions c
WHERE c.commercial_order_id = o.id AND c.status NOT IN ('REJECTED', 'CANCELLED');

-- Orders whose only Commission is voided (Rejected/Cancelled) → ISSUE; the rest stay NULL (no Commission).
UPDATE commercial_orders o
SET commission_status = 'ISSUE'
WHERE o.commission_status IS NULL
  AND EXISTS (
      SELECT 1 FROM commissions c
      WHERE c.commercial_order_id = o.id AND c.status IN ('REJECTED', 'CANCELLED'));
