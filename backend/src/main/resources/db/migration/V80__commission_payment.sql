-- Commission Management / Sprint 6 Slice 8: register a manual commission payment (APPROVED -> PAID). The payment is
-- single and full (no partial): the paid amount equals the commission amount. The payment method reuses the financial
-- payment_methods cadastro (no second mechanism). This is the commission-payment record itself — no Accounts Payable,
-- payroll, tax, accounting or bank-transfer data, and no bank integration.
ALTER TABLE commissions
    ADD COLUMN paid_amount       NUMERIC(14, 2),
    ADD COLUMN payment_date      DATE,
    ADD COLUMN payment_method_id UUID REFERENCES payment_methods (id),
    ADD COLUMN payment_note      VARCHAR(2000),
    ADD COLUMN paid_by           UUID;
