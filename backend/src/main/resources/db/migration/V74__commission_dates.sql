-- Commission Management / Sprint 6 Slice 4: the operational commission list shows the approval and payment dates of
-- a commission. These transitions are later slices (approval / commission payment); the columns are added now,
-- nullable, so the list contract and the payment-period filter are complete and stable. They are null until those
-- slices populate them, and carry no payroll, tax, accounting or accounts-payable data.
ALTER TABLE commissions
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN paid_at     TIMESTAMPTZ;
