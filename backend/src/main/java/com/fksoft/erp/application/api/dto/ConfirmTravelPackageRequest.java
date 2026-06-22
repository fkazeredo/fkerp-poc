package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Request to manually confirm a Travel Package booking item.
 *
 * @param externalSystem the external system or supplier name (required)
 * @param externalLocator the external locator or booking code (required)
 * @param confirmedAt when the booking was confirmed (required; not in the future)
 * @param packageDescription the destination/package description (optional)
 * @param travelStartDate the travel start date (optional)
 * @param travelEndDate the travel end date (optional)
 * @param travelerNotes passenger/traveler notes (optional)
 * @param operationalNotes operational notes (optional)
 */
public record ConfirmTravelPackageRequest(
        @NotBlank(message = "Sistema ou fornecedor é obrigatório") @Size(max = 200, message = "Texto muito longo")
                String externalSystem,
        @NotBlank(message = "Localizador ou código de reserva é obrigatório")
                @Size(max = 100, message = "Texto muito longo")
                String externalLocator,
        @NotNull(message = "Data da confirmação é obrigatória")
                @PastOrPresent(message = "A data da confirmação não pode ser no futuro")
                Instant confirmedAt,
        @Size(max = 500, message = "Texto muito longo") String packageDescription,
        LocalDate travelStartDate,
        LocalDate travelEndDate,
        @Size(max = 2000, message = "Texto muito longo") String travelerNotes,
        @Size(max = 2000, message = "Texto muito longo") String operationalNotes) {}
