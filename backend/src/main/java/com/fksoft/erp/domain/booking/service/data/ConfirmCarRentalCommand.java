package com.fksoft.erp.domain.booking.service.data;

import java.time.Instant;

/**
 * Input to manually confirm a Car Rental booking item.
 *
 * @param externalSystem the external system or supplier name (required)
 * @param externalLocator the external locator or booking code (required)
 * @param confirmedAt when the booking was confirmed (operator-supplied)
 * @param rentalCompany the rental company, or {@code null}
 * @param pickupLocation the pickup location, or {@code null}
 * @param dropoffLocation the dropoff location, or {@code null}
 * @param pickupAt the pickup date/time, or {@code null}
 * @param dropoffAt the dropoff date/time, or {@code null}
 * @param carCategory the car category, or {@code null}
 * @param operationalNotes operational notes, or {@code null}
 */
public record ConfirmCarRentalCommand(
        String externalSystem,
        String externalLocator,
        Instant confirmedAt,
        String rentalCompany,
        String pickupLocation,
        String dropoffLocation,
        Instant pickupAt,
        Instant dropoffAt,
        String carCategory,
        String operationalNotes) {}
