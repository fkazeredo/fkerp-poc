-- Sprint 4 / Slice 6: manual confirmation of a Travel Package booking item. The operator records the result of
-- the external reservation (system/supplier + locator, confirmation date + author, plus optional travel
-- metadata) before any real integration exists. The confirmation is an @Embeddable value object on the booking
-- item (all columns nullable; populated only when the item is confirmed). It carries NO monetary data and
-- creates no Financial/Commission/Customer Care record. Only the schema changes here — the operation reuses the
-- booking:request:update scope seeded in V31 (no new scope).

ALTER TABLE booking_items
    ADD COLUMN confirmation_external_system      VARCHAR(200),
    ADD COLUMN confirmation_external_locator     VARCHAR(100),
    ADD COLUMN confirmation_confirmed_at         TIMESTAMPTZ,
    ADD COLUMN confirmation_confirmed_by         UUID,
    ADD COLUMN confirmation_package_description  VARCHAR(500),
    ADD COLUMN confirmation_travel_start_date    DATE,
    ADD COLUMN confirmation_travel_end_date      DATE,
    ADD COLUMN confirmation_traveler_notes       VARCHAR(2000),
    ADD COLUMN confirmation_operational_notes    VARCHAR(2000);
