-- Sprint 4 / Slice 10: operational pending-items worklist for Booking Operations.
-- Denormalise the latest manual attempt's planned next-action date onto the Booking Request (mirroring the
-- existing last_attempt_at), so the pending worklist can flag overdue next actions without an N+1 / subquery.
-- Nullable (no next action planned). Adds no new scope and creates no Financial/Payment/Commission data.
ALTER TABLE booking_requests
    ADD COLUMN next_action_date DATE;
