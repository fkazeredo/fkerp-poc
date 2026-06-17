-- Sprint 3 / Slice 2: items of a commercial Proposal (part of the Proposal aggregate). Each item is what
-- the company intends to sell — a type, description, quantity, unit value and an optional discount (an
-- absolute amount or a percentage). Items contribute to the Proposal total. They do NOT create a Booking,
-- check external availability, or compute supplier cost / margin / tax. Items are managed only while the
-- Proposal is a Draft. No new scope: managing items reuses sales:proposal:update (seeded in V20).

ALTER TABLE proposals ADD COLUMN total NUMERIC(15, 2) NOT NULL DEFAULT 0;

CREATE TABLE proposal_items (
    id             UUID PRIMARY KEY,
    proposal_id    UUID          NOT NULL REFERENCES proposals (id),
    type           VARCHAR(30)   NOT NULL
        CHECK (type IN ('TRAVEL_PACKAGE', 'CAR_RENTAL', 'SERVICE_FEE', 'OTHER')),
    description    VARCHAR(500)  NOT NULL,
    quantity       INTEGER       NOT NULL CHECK (quantity >= 1),
    unit_value     NUMERIC(15, 2) NOT NULL CHECK (unit_value >= 0),
    discount_type  VARCHAR(10)   CHECK (discount_type IN ('AMOUNT', 'PERCENT')),
    discount_value NUMERIC(15, 2) CHECK (discount_value IS NULL OR discount_value >= 0),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- discount type and value are present together or both absent; a percentage is at most 100.
    CONSTRAINT ck_proposal_item_discount_pair CHECK ((discount_type IS NULL) = (discount_value IS NULL)),
    CONSTRAINT ck_proposal_item_percent_max CHECK (discount_type <> 'PERCENT' OR discount_value <= 100)
);

CREATE INDEX idx_proposal_items_proposal ON proposal_items (proposal_id);
