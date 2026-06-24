-- Financial Operations / Sprint 5 Slice 6: per-installment paid amount. Partial payments need to know how much
-- each installment has received to tell PARTIALLY_PAID from PAID; the amount is denormalized on the installment
-- (mirrors receivables.amount_paid). A payment never exceeds the installment's outstanding (overpayment is out of
-- scope), so amount_paid <= amount.
ALTER TABLE receivable_installments
    ADD COLUMN amount_paid NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0 AND amount_paid <= amount);

-- Backfill the Slice-5 fully-paid installments so the new column matches their status (only OPEN/PAID existed
-- before; OPEN installments stay 0). The receivable-level amount_paid (V63) already matches the sum.
UPDATE receivable_installments SET amount_paid = amount WHERE status = 'PAID';
