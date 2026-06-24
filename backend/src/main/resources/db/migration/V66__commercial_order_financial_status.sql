-- Financial Operations / Sprint 5 Slice 7: reflect the Receivable's financial status onto the Commercial Order.
-- The Order stays owned by Sales & Proposals — this nullable column is a read-only reflection written by a
-- Sales event listener reacting to the Financial context (never by Financial), mirroring booking_status. Null
-- until a Receivable exists for the Order. No CHECK (free reflection, like booking_status).
ALTER TABLE commercial_orders ADD COLUMN financial_status VARCHAR(60);

-- Backfill from the active (non-cancelled) Receivable, if any (at most one per Order). Orders without a
-- Receivable stay NULL.
UPDATE commercial_orders o
SET financial_status = r.status
FROM receivables r
WHERE r.commercial_order_id = o.id AND r.status <> 'CANCELLED';
