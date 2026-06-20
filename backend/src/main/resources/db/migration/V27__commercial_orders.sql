-- Sprint 3 / Slice 10: Commercial Orders. A Commercial Order is the formal internal record of a closed deal,
-- created from an ACCEPTED Proposal (snapshot of its items + total + source references). Creating it closes the
-- source Opportunity as WON (a new terminal stage, mirroring LOST) and creates NO Booking, Receivable, Payment,
-- Commission or Customer Care data.

-- (a) Add the new terminal WON stage to the Opportunity stage CHECKs (the stage column + the stage history).
ALTER TABLE opportunities DROP CONSTRAINT opportunities_stage_check;
ALTER TABLE opportunities ADD CONSTRAINT opportunities_stage_check CHECK (
    stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'WON', 'LOST'));

ALTER TABLE opportunity_stage_changes DROP CONSTRAINT opportunity_stage_changes_from_stage_check;
ALTER TABLE opportunity_stage_changes ADD CONSTRAINT opportunity_stage_changes_from_stage_check CHECK (
    from_stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'WON', 'LOST'));

ALTER TABLE opportunity_stage_changes DROP CONSTRAINT opportunity_stage_changes_to_stage_check;
ALTER TABLE opportunity_stage_changes ADD CONSTRAINT opportunity_stage_changes_to_stage_check CHECK (
    to_stage IN ('NEW_OPPORTUNITY', 'DISCOVERY', 'PRODUCT_FIT', 'READY_FOR_PROPOSAL', 'WON', 'LOST'));

-- (b) The Commercial Order aggregate (mirrors proposals / proposal_items). The items are a snapshot of the
-- Proposal's lines; subtotal/total are snapshotted too. At most ONE active (non-cancelled) Order per Proposal.
CREATE TABLE commercial_orders (
    id                    UUID PRIMARY KEY,
    version               BIGINT         NOT NULL DEFAULT 0,
    proposal_id           UUID           NOT NULL REFERENCES proposals (id),
    opportunity_id        UUID           NOT NULL REFERENCES opportunities (id),
    lead_id               UUID           NOT NULL REFERENCES leads (id),
    responsible_person_id UUID,
    status                VARCHAR(30)    NOT NULL
        CHECK (status IN ('PENDING_BOOKING', 'BOOKING_NOT_REQUIRED', 'CANCELLED')),
    subtotal              NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    total                 NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID           NOT NULL,
    updated_by            UUID           NOT NULL
);

CREATE INDEX idx_commercial_orders_proposal ON commercial_orders (proposal_id);
CREATE INDEX idx_commercial_orders_responsible ON commercial_orders (responsible_person_id);

CREATE UNIQUE INDEX ux_orders_active_per_proposal
    ON commercial_orders (proposal_id)
    WHERE status <> 'CANCELLED';

CREATE TABLE commercial_order_items (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL REFERENCES commercial_orders (id),
    type           VARCHAR(30)    NOT NULL
        CHECK (type IN ('TRAVEL_PACKAGE', 'CAR_RENTAL', 'SERVICE_FEE', 'OTHER')),
    description    VARCHAR(500)   NOT NULL,
    quantity       INTEGER        NOT NULL CHECK (quantity >= 1),
    unit_value     NUMERIC(15, 2) NOT NULL CHECK (unit_value >= 0),
    discount_type  VARCHAR(10)    CHECK (discount_type IN ('AMOUNT', 'PERCENT')),
    discount_value NUMERIC(15, 2) CHECK (discount_value IS NULL OR discount_value >= 0),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_item_discount_pair CHECK ((discount_type IS NULL) = (discount_value IS NULL)),
    CONSTRAINT ck_order_item_percent_max CHECK (discount_type <> 'PERCENT' OR discount_value <= 100)
);

CREATE INDEX idx_commercial_order_items_order ON commercial_order_items (order_id);

-- (c) Commercial Order scopes (sales:order:*), mirroring the Proposal profile model:
--   manager (001): read:all + create
--   vendedor (002): read + read:unassigned + create
--   representante (003): read + create
--   diretor (004): read:all (consultation only)
--   financeiro (005): none
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'sales:order:read:all'),
    ('00000000-0000-0000-0000-000000000001', 'sales:order:create'),
    ('00000000-0000-0000-0000-000000000002', 'sales:order:read'),
    ('00000000-0000-0000-0000-000000000002', 'sales:order:read:unassigned'),
    ('00000000-0000-0000-0000-000000000002', 'sales:order:create'),
    ('00000000-0000-0000-0000-000000000003', 'sales:order:read'),
    ('00000000-0000-0000-0000-000000000003', 'sales:order:create'),
    ('00000000-0000-0000-0000-000000000004', 'sales:order:read:all');
