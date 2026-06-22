-- Sprint 4 / Slice 7: manual confirmation of a Car Rental booking item. The operator records the external car
-- reservation result before any real integration exists. These columns extend the single confirmation
-- @Embeddable on the booking item with the car-rental-specific metadata (rental company, pickup/dropoff location
-- and date-time, car category); they are populated only when a Car Rental item is confirmed and stay null
-- otherwise. NO monetary data, no Financial/Commission/Customer Care record. Only the schema changes here — the
-- operation reuses the booking:request:update scope (V31); no new scope.

ALTER TABLE booking_items
    ADD COLUMN confirmation_rental_company   VARCHAR(200),
    ADD COLUMN confirmation_pickup_location  VARCHAR(300),
    ADD COLUMN confirmation_dropoff_location VARCHAR(300),
    ADD COLUMN confirmation_pickup_at        TIMESTAMPTZ,
    ADD COLUMN confirmation_dropoff_at       TIMESTAMPTZ,
    ADD COLUMN confirmation_car_category     VARCHAR(100);
