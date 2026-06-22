-- Sprint 4 / Slice 5: manual booking attempts. The operations team registers what it did (accessed an
-- external system, contacted the supplier, verified internally, checked availability…) while working a
-- reservation, building an append-only operational history BEFORE confirmation or failure. An attempt records
-- an author, date, type, result and description, optionally links to one booking item (or the whole request),
-- and may define a next action date. Registering an attempt may move the request PENDING -> IN_PROGRESS; it
-- NEVER confirms the booking, never changes a booking item's status, and never creates Financial/Commission
-- data. The booking_requests.last_attempt_at column denormalizes the latest attempt instant for the list.

ALTER TABLE booking_requests ADD COLUMN last_attempt_at TIMESTAMPTZ;

CREATE TABLE booking_attempts (
    id                 UUID PRIMARY KEY,
    booking_request_id UUID          NOT NULL REFERENCES booking_requests (id),
    booking_item_id    UUID          REFERENCES booking_items (id),
    type               VARCHAR(40)   NOT NULL
        CHECK (type IN ('EXTERNAL_SYSTEM_ACCESS', 'SUPPLIER_PHONE_CONTACT', 'SUPPLIER_EMAIL_CONTACT',
                        'INTERNAL_VERIFICATION', 'MANUAL_AVAILABILITY_CHECK', 'OTHER')),
    result             VARCHAR(40)   NOT NULL
        CHECK (result IN ('STARTED', 'WAITING_FOR_SUPPLIER', 'WAITING_FOR_INTERNAL_INFO', 'AVAILABILITY_FOUND',
                          'AVAILABILITY_NOT_FOUND', 'NEEDS_RETRY', 'FAILED', 'OTHER')),
    description        VARCHAR(4000) NOT NULL,
    occurred_at        TIMESTAMPTZ   NOT NULL,
    next_action_date   DATE,
    registered_by      UUID          NOT NULL
);

CREATE INDEX idx_booking_attempts_request ON booking_attempts (booking_request_id, occurred_at DESC);

-- New operation scope booking:request:update gates registering an attempt (and future operate actions —
-- assign / confirm / fail). The operations user (006) and the commercial Manager (001) work the reservations;
-- the Board/Director (004) stays read-only (consultation). Sellers/Representatives and Finance/HR/IT: none.
INSERT INTO user_scopes (user_id, scope) VALUES
    ('00000000-0000-0000-0000-000000000001', 'booking:request:update'),
    ('00000000-0000-0000-0000-000000000006', 'booking:request:update');
