-- Customer Management / Sprint 7 Slice 1 (CARE7-001): evolve the Customer into a managed Customer Profile.
-- Adds the preserved commercial origin (source Commercial Order / Proposal / Opportunity), a preferred contact
-- channel, free-text notes, and a 3-state lifecycle (Active/Inactive/Blocked) replacing the boolean `active`.
-- Purely additive to the existing customer master — no Booking, Receivable, Payment or Commission data touched.
ALTER TABLE customers
    ADD COLUMN source_commercial_order_id UUID REFERENCES commercial_orders (id),
    ADD COLUMN source_proposal_id         UUID REFERENCES proposals (id),
    ADD COLUMN source_opportunity_id      UUID REFERENCES opportunities (id),
    ADD COLUMN preferred_contact_method   VARCHAR(20),
    ADD COLUMN notes                      VARCHAR(2000),
    ADD COLUMN status                     VARCHAR(20);

-- Backfill the lifecycle from the boolean flag, then make it the source of truth.
UPDATE customers SET status = CASE WHEN active THEN 'ACTIVE' ELSE 'INACTIVE' END;

ALTER TABLE customers
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN status SET NOT NULL,
    DROP COLUMN active;

-- The schema mirrors the domain invariants (the enum value sets).
ALTER TABLE customers
    ADD CONSTRAINT customers_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    ADD CONSTRAINT customers_preferred_contact_check
        CHECK (preferred_contact_method IS NULL OR preferred_contact_method IN ('EMAIL', 'PHONE', 'WHATSAPP'));
