-- Sprint 4 / Slice 1: Booking Operations. A Booking Request starts the (still manual) reservation process for
-- a Commercial Order that is PENDING_BOOKING. It is the aggregate root of the new Booking Operations context
-- (domain.booking). It preserves the source Order / Proposal / Opportunity / Lead references and the commercial
-- responsible, and snapshots WHAT must be reserved (the booking items, classified by booking need) — carrying
-- NO monetary data. A Booking Request is NOT an external integration, a Receivable, a Payment, a Commission or
-- Customer Care. At most ONE active (non-cancelled) Booking Request per Commercial Order.

CREATE TABLE booking_requests (
    id                    UUID PRIMARY KEY,
    version               BIGINT       NOT NULL DEFAULT 0,
    commercial_order_id   UUID         NOT NULL REFERENCES commercial_orders (id),
    proposal_id           UUID         NOT NULL REFERENCES proposals (id),
    opportunity_id        UUID         NOT NULL REFERENCES opportunities (id),
    lead_id               UUID         NOT NULL REFERENCES leads (id),
    responsible_person_id UUID,
    booking_operator_id   UUID,
    status                VARCHAR(30)  NOT NULL
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PARTIALLY_CONFIRMED', 'CONFIRMED', 'FAILED', 'CANCELLED')),
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NOT NULL,
    updated_by            UUID         NOT NULL
);

CREATE INDEX idx_booking_requests_order ON booking_requests (commercial_order_id);
CREATE INDEX idx_booking_requests_responsible ON booking_requests (responsible_person_id);
CREATE INDEX idx_booking_requests_operator ON booking_requests (booking_operator_id);

-- At most one ACTIVE (non-cancelled) Booking Request per Commercial Order (the service returns a friendly 409
-- first; this partial unique index is the last-resort guard). A new request is allowed once the previous one is
-- CANCELLED.
CREATE UNIQUE INDEX ux_booking_requests_active_per_order
    ON booking_requests (commercial_order_id)
    WHERE status <> 'CANCELLED';

-- The booking lines: a snapshot of the Order's items (type, description, quantity), classified by booking need
-- and with their own reservation status. NO monetary data (a Booking Request is not financial data).
CREATE TABLE booking_items (
    id                 UUID PRIMARY KEY,
    booking_request_id UUID         NOT NULL REFERENCES booking_requests (id),
    order_item_id      UUID         NOT NULL,
    type               VARCHAR(30)  NOT NULL
        CHECK (type IN ('TRAVEL_PACKAGE', 'CAR_RENTAL', 'SERVICE_FEE', 'OTHER')),
    description        VARCHAR(500) NOT NULL,
    quantity           INTEGER      NOT NULL CHECK (quantity >= 1),
    requires_booking   BOOLEAN      NOT NULL,
    status             VARCHAR(20)  NOT NULL
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'CONFIRMED', 'FAILED', 'NOT_REQUIRED', 'CANCELLED')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_items_request ON booking_items (booking_request_id);

-- Booking Operations persona + scopes (booking:request:*). A new back-office 'operacoes' user (006) is the
-- booking operator; the commercial Manager (001) may also create requests (oversight). The operations user
-- gets sales:order:read:all so it can see the source Order to create the request. The booking READ tiers
-- (booking:request:read*) arrive with the booking list/detail slice. BCrypt of 'operacoes123' (DEV ONLY).
INSERT INTO users (id, username, password_hash, active) VALUES
    ('00000000-0000-0000-0000-000000000006', 'operacoes',
     '$2a$10$OBEWSQMkVtJOvpT7.6bnf.Y4TqmeToujC7.u/ovDOTAUMt95TLY4q', TRUE);

INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000006', 'booking:request:create'),
    ('00000000-0000-0000-0000-000000000006', 'sales:order:read:all'),
    ('00000000-0000-0000-0000-000000000001', 'booking:request:create');
