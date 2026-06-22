-- Sprint 4 / Slice 11: minimum Booking Operations indicators.
-- Denormalise the instant a Booking Request first reached CONFIRMED, so the indicators can compute the average
-- time from creation to confirmation without scanning the item/attempt history. Nullable (set on the first
-- CONFIRMED transition; requests confirmed before this migration stay null and are excluded from the average).
-- Carries no Financial/Payment/Commission data and adds no new scope.
ALTER TABLE booking_requests
    ADD COLUMN confirmed_at TIMESTAMPTZ;
