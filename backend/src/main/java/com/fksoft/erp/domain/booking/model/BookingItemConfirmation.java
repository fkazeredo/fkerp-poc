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
 * and author, plus optional travel metadata (package description, travel dates, traveler notes) and operational
 * notes. It is an {@code @Embeddable} value object on the item, populated only when the item is confirmed — it
 * carries <b>no monetary data</b>, creates no Financial/Commission/Customer Care record and triggers no voucher
 * or external call.
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

    @Column(name = "confirmation_operational_notes")
    private String operationalNotes;
}
