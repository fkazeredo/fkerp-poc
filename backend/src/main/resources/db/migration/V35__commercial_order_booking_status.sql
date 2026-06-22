-- Sprint 4 / Slice 9: reflect the consolidated Booking Request status onto the Commercial Order.
-- The Commercial Order stays owned by Sales & Proposals; this Sales-owned column is updated by a Sales
-- event listener reacting to the Booking context's consolidation event. NULL = no Booking Request yet.
-- Adds no new scope and creates no Financial/Receivable/Payment/Commission data.
ALTER TABLE commercial_orders
    ADD COLUMN booking_status VARCHAR(30)
        CHECK (booking_status IN (
            'PENDING', 'IN_PROGRESS', 'PARTIALLY_CONFIRMED', 'CONFIRMED', 'FAILED', 'CANCELLED'
        ));
