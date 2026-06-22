-- Rewire the Booking Request and Booking Item onto the configurable workflow. Unlike the user-driven
-- lifecycles (Lead/Opportunity/Proposal/Order), the booking statuses are STATE-DERIVED in code (never a
-- user-chosen target), so the denormalized status code stays the entity's source value and the current_state_id
-- FK is kept consistent by a BEFORE INSERT OR UPDATE trigger (rather than being written by the aggregate). The
-- FK still gives the data-driven storage; the states/labels are editable rows.

-- Booking Request.
ALTER TABLE booking_requests ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE booking_requests r SET current_state_id = (
    SELECT s.id FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'booking_request'
    WHERE s.code = r.status);

ALTER TABLE booking_requests ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_booking_requests_current_state ON booking_requests (current_state_id);

CREATE OR REPLACE FUNCTION booking_requests_fill_current_state() RETURNS trigger AS $$
BEGIN
    NEW.current_state_id := (
        SELECT s.id FROM workflow_states s
        JOIN workflow_definitions d ON d.id = s.definition_id
        WHERE d.code = 'booking_request' AND s.code = NEW.status);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_requests_fill_current_state
    BEFORE INSERT OR UPDATE OF status ON booking_requests
    FOR EACH ROW EXECUTE FUNCTION booking_requests_fill_current_state();

-- Booking Item.
ALTER TABLE booking_items ADD COLUMN current_state_id UUID REFERENCES workflow_states (id);

UPDATE booking_items i SET current_state_id = (
    SELECT s.id FROM workflow_states s
    JOIN workflow_definitions d ON d.id = s.definition_id AND d.code = 'booking_item'
    WHERE s.code = i.status);

ALTER TABLE booking_items ALTER COLUMN current_state_id SET NOT NULL;
CREATE INDEX idx_booking_items_current_state ON booking_items (current_state_id);

CREATE OR REPLACE FUNCTION booking_items_fill_current_state() RETURNS trigger AS $$
BEGIN
    NEW.current_state_id := (
        SELECT s.id FROM workflow_states s
        JOIN workflow_definitions d ON d.id = s.definition_id
        WHERE d.code = 'booking_item' AND s.code = NEW.status);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_items_fill_current_state
    BEFORE INSERT OR UPDATE OF status ON booking_items
    FOR EACH ROW EXECUTE FUNCTION booking_items_fill_current_state();
