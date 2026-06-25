-- Commission Management / Sprint 6 Slice 6: approving an Eligible Commission (ELIGIBLE -> APPROVED) records who
-- approved it and the optional approval notes (the approved_at instant already exists since V74). Approval makes the
-- commission ready for payment but registers no payment and carries no payroll, tax, accounting or bank data.
ALTER TABLE commissions
    ADD COLUMN approved_by    UUID,
    ADD COLUMN approval_notes VARCHAR(2000);
