package com.fksoft.erp.domain.booking.service.data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Input to manually confirm a Travel Package booking item.
 *
 * @param externalSystem the external system or supplier name (required)
 * @param externalLocator the external locator or booking code (required)
 * @param confirmedAt when the booking was confirmed (operator-supplied)
 * @param packageDescription the destination/package description, or {@code null}
 * @param travelStartDate the travel start date, or {@code null}
 * @param travelEndDate the travel end date, or {@code null}
 * @param travelerNotes passenger/traveler notes, or {@code null}
 * @param operationalNotes operational notes, or {@code null}
 */
public record ConfirmTravelPackageCommand(
        String externalSystem,
        String externalLocator,
        Instant confirmedAt,
        String packageDescription,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        String travelerNotes,
        String operationalNotes) {}
