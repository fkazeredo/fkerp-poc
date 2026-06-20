-- Sprint 3 / Slice 11: a human-friendly sequential number for the Commercial Order (e.g. PC-0001, formatted
-- in the UI). The raw number is a per-Order sequence value; the application assigns it from the sequence at
-- creation (no column DEFAULT, so the sequence is consumed exactly once per Order).
CREATE SEQUENCE commercial_order_number_seq START 1;

ALTER TABLE commercial_orders ADD COLUMN number BIGINT;

-- Backfill any existing Orders (the sequence advances past them, so new Orders keep increasing).
UPDATE commercial_orders SET number = nextval('commercial_order_number_seq');

ALTER TABLE commercial_orders
    ALTER COLUMN number SET NOT NULL,
    ADD CONSTRAINT ux_commercial_orders_number UNIQUE (number);
