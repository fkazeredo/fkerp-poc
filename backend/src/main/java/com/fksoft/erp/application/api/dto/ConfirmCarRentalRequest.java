package com.fksoft.erp.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Request to manually confirm a Car Rental booking item.
 *
 * @param externalSystem the external system or supplier name (required)
 * @param externalLocator the external locator or booking code (required)
 * @param confirmedAt when the booking was confirmed (required; not in the future)
 * @param rentalCompany the rental company (optional)
 * @param pickupLocation the pickup location (optional)
 * @param dropoffLocation the dropoff location (optional)
 * @param pickupAt the pickup date/time (optional; may be in the future)
 * @param dropoffAt the dropoff date/time (optional; may be in the future)
 * @param carCategory the car category (optional)
 * @param operationalNotes operational notes (optional)
 */
public record ConfirmCarRentalRequest(
        @NotBlank(message = "Sistema ou fornecedor é obrigatório") @Size(max = 200, message = "Texto muito longo")
                String externalSystem,
        @NotBlank(message = "Localizador ou código de reserva é obrigatório")
                @Size(max = 100, message = "Texto muito longo")
                String externalLocator,
        @NotNull(message = "Data da confirmação é obrigatória")
                @PastOrPresent(message = "A data da confirmação não pode ser no futuro")
                Instant confirmedAt,
        @Size(max = 200, message = "Texto muito longo") String rentalCompany,
        @Size(max = 300, message = "Texto muito longo") String pickupLocation,
        @Size(max = 300, message = "Texto muito longo") String dropoffLocation,
        Instant pickupAt,
        Instant dropoffAt,
        @Size(max = 100, message = "Texto muito longo") String carCategory,
        @Size(max = 2000, message = "Texto muito longo") String operationalNotes) {}
