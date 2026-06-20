-- Sprint 3 / Slice 8: register a Proposal sent to the client. An approved Proposal (APPROVED) can be marked
-- as sent (→ SENT), recording who/when in the status-change history (V23) and an optional descriptive
-- sending channel here. SENT is a non-terminal status (the Proposal stays open for the client's decision).
-- Marking as sent does NOT trigger any real e-mail/WhatsApp/phone integration and creates no customer
-- acceptance, Commercial Order, Booking, Financial or Commission data. The channel is optional.
ALTER TABLE proposals
    ADD COLUMN sending_channel VARCHAR(30);

ALTER TABLE proposals
    ADD CONSTRAINT ck_proposal_sending_channel CHECK (
        sending_channel IS NULL OR sending_channel IN (
            'EMAIL', 'WHATSAPP', 'PHONE_PRESENTATION', 'IN_PERSON_PRESENTATION', 'OTHER'));

-- No new scope: registering the send reuses sales:proposal:update (the seller/manager who operates the
-- Proposal records that it was sent/presented to the client), already seeded for the relevant profiles.
