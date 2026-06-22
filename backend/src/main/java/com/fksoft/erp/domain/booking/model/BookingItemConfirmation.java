package com.fksoft.erp.domain.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The manual confirmation of a {@link BookingItem}'s external reservation (Sprint 4): the operator records the
 * result of confirming the booking with the supplier/external system before any real integration exists. It
 * carries the external system/supplier name and locator (both required at confirmation), the confirmation date
 * and author, plus optional type-specific metadata — Travel Package (package description, travel dates, traveler
 * notes) and Car Rental (rental company, pickup/dropoff location and date-time, car category) — and operational
 * notes. It is a single {@code @Embeddable} value object on the item (the fields not relevant to the item's type
 * stay null), populated only when the item is confirmed. It carries <b>no monetary data</b>, creates no
 * Financial/Commission/Customer Care record and triggers no voucher or external call.
 */
@Embeddable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BookingItemConfirmation {

    @Column(name = "confirmation_external_system")
    private String externalSystem;

    @Column(name = "confirmation_external_locator")
    private String externalLocator;

    @Column(name = "confirmation_confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmation_confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmation_package_description")
    private String packageDescription;

    @Column(name = "confirmation_travel_start_date")
    private LocalDate travelStartDate;

    @Column(name = "confirmation_travel_end_date")
    private LocalDate travelEndDate;

    @Column(name = "confirmation_traveler_notes")
    private String travelerNotes;

    // Car-rental confirmation metadata (populated only when a Car Rental item is confirmed; null otherwise).
    @Column(name = "confirmation_rental_company")
    private String rentalCompany;

    @Column(name = "confirmation_pickup_location")
    private String pickupLocation;

    @Column(name = "confirmation_dropoff_location")
    private String dropoffLocation;

    @Column(name = "confirmation_pickup_at")
    private Instant pickupAt;

    @Column(name = "confirmation_dropoff_at")
    private Instant dropoffAt;

    @Column(name = "confirmation_car_category")
    private String carCategory;

    @Column(name = "confirmation_operational_notes")
    private String operationalNotes;
}
