-- Turn the ProposalItemType enum into a managed cadastro (reference data) carrying an extra
-- `requires_booking` attribute (absorbing the booking-need classification), and rewire the three
-- referencing item columns to FKs (expand phase: the former enum columns are kept, now unmapped/dead,
-- and dropped in a later contract migration). Codes match the former enum constants; TRAVEL_PACKAGE and
-- CAR_RENTAL stay the reserved codes that anchor the type-specific confirmation flows. The inline
-- type CHECK constraints are dropped since the editable cadastro is now the source of truth (the service
-- validates the code is active); booking requirement now reads `requires_booking`.

CREATE TABLE proposal_item_types (
    id               UUID PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    label            VARCHAR(120) NOT NULL,
    requires_booking BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO proposal_item_types (id, code, label, requires_booking, sort_order) VALUES
    (gen_random_uuid(), 'TRAVEL_PACKAGE', 'Pacote de viagem',   TRUE,  1),
    (gen_random_uuid(), 'CAR_RENTAL',     'Locação de veículo', TRUE,  2),
    (gen_random_uuid(), 'SERVICE_FEE',    'Taxa de serviço',    FALSE, 3),
    (gen_random_uuid(), 'OTHER',          'Outro',              FALSE, 4);

-- Shared BEFORE INSERT trigger function: when a raw insert sets the legacy `type` code without the FK,
-- fill `type_id` from the cadastro so existing raw-SQL fixtures keep working; entity inserts (which write
-- `type_id` directly) are unaffected. Reused by all three item tables (each has `type` + `type_id`).
CREATE OR REPLACE FUNCTION fill_proposal_item_type_id() RETURNS trigger AS $$
BEGIN
    IF NEW.type_id IS NULL AND NEW.type IS NOT NULL THEN
        NEW.type_id := (SELECT id FROM proposal_item_types WHERE code = NEW.type);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- proposal_items.type → FK (required).
ALTER TABLE proposal_items ADD COLUMN type_id UUID REFERENCES proposal_item_types (id);
UPDATE proposal_items i SET type_id = (SELECT t.id FROM proposal_item_types t WHERE t.code = i.type);
ALTER TABLE proposal_items ALTER COLUMN type_id SET NOT NULL;
ALTER TABLE proposal_items DROP CONSTRAINT IF EXISTS proposal_items_type_check;
ALTER TABLE proposal_items ALTER COLUMN type DROP NOT NULL;
CREATE INDEX idx_proposal_items_type ON proposal_items (type_id);
CREATE TRIGGER trg_proposal_items_fill_type
    BEFORE INSERT ON proposal_items FOR EACH ROW EXECUTE FUNCTION fill_proposal_item_type_id();

-- commercial_order_items.type → FK (required).
ALTER TABLE commercial_order_items ADD COLUMN type_id UUID REFERENCES proposal_item_types (id);
UPDATE commercial_order_items i SET type_id = (SELECT t.id FROM proposal_item_types t WHERE t.code = i.type);
ALTER TABLE commercial_order_items ALTER COLUMN type_id SET NOT NULL;
ALTER TABLE commercial_order_items DROP CONSTRAINT IF EXISTS commercial_order_items_type_check;
ALTER TABLE commercial_order_items ALTER COLUMN type DROP NOT NULL;
CREATE INDEX idx_commercial_order_items_type ON commercial_order_items (type_id);
CREATE TRIGGER trg_commercial_order_items_fill_type
    BEFORE INSERT ON commercial_order_items FOR EACH ROW EXECUTE FUNCTION fill_proposal_item_type_id();

-- booking_items.type → FK (required).
ALTER TABLE booking_items ADD COLUMN type_id UUID REFERENCES proposal_item_types (id);
UPDATE booking_items i SET type_id = (SELECT t.id FROM proposal_item_types t WHERE t.code = i.type);
ALTER TABLE booking_items ALTER COLUMN type_id SET NOT NULL;
ALTER TABLE booking_items DROP CONSTRAINT IF EXISTS booking_items_type_check;
ALTER TABLE booking_items ALTER COLUMN type DROP NOT NULL;
CREATE INDEX idx_booking_items_type ON booking_items (type_id);
CREATE TRIGGER trg_booking_items_fill_type
    BEFORE INSERT ON booking_items FOR EACH ROW EXECUTE FUNCTION fill_proposal_item_type_id();
