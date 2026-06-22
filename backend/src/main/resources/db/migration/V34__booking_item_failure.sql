-- Sprint 4 / Slice 8: manual failure of a booking item. When the operator determines a booking item could not
-- be reserved, they record the reason (a fixed set), an optional note, and who/when. These columns extend the
-- booking item with the failure metadata (@Embeddable value object; populated only when the item is failed,
-- null otherwise). A failed item stays visible as an operational problem and may later receive new attempts or
-- be confirmed (retry). NO monetary data; the failure never cancels the Commercial Order and creates no
-- Financial/Commission/Customer Care record. Only the schema changes here — the operation reuses the
-- booking:request:update scope (V31); no new scope.

ALTER TABLE booking_items
    ADD COLUMN failure_reason VARCHAR(40)
        CHECK (failure_reason IN ('NO_AVAILABILITY', 'SUPPLIER_UNAVAILABLE', 'INVALID_COMMERCIAL_DATA',
                                  'MISSING_TRAVELER_DATA', 'EXTERNAL_SYSTEM_UNAVAILABLE', 'PRICE_CHANGED',
                                  'MANUAL_OPERATION_ERROR', 'OUT_OF_POLICY', 'OTHER')),
    ADD COLUMN failure_note   VARCHAR(2000),
    ADD COLUMN failure_by     UUID,
    ADD COLUMN failure_at     TIMESTAMPTZ;
