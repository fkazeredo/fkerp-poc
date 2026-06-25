-- Commission Management / Sprint 6 Slice 3: a Commission becomes ELIGIBLE (pending approval) when its related
-- Receivable is fully paid. When that happens the commission preserves the financial evidence for the future
-- approval review: when it became eligible and the paid Receivable's id. Both are null while the commission is still
-- EXPECTED (a forecast). They carry no monetary data; making a commission eligible creates no Payment, Accounts
-- Payable, payroll, tax or accounting data. The status CHECK already allows 'ELIGIBLE' (V71).
ALTER TABLE commissions
    ADD COLUMN eligible_at  TIMESTAMPTZ,
    ADD COLUMN receivable_id UUID;
