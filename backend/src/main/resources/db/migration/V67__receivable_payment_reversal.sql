-- Financial Operations / Sprint 5 Slice 9: Reverse a registered payment. A payment-entry correction marks the
-- payment reversed (kept in history, never deleted) and re-derives the paid amount and the installment/Receivable
-- status. The reversal fields are nullable (null = the payment stands); no backfill. Reversing a payment creates no
-- refund, Commission or Customer Care record.
ALTER TABLE receivable_payments ADD COLUMN reversal_reason VARCHAR(2000);
ALTER TABLE receivable_payments ADD COLUMN reversed_by     UUID;
ALTER TABLE receivable_payments ADD COLUMN reversed_at     TIMESTAMPTZ;
