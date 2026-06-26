package com.fksoft.erp.application.api.dto;

import com.fksoft.erp.domain.crm.model.ContactMethod;
import com.fksoft.erp.domain.crm.service.data.CreateCustomerCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request to create or consolidate a Customer Profile from a Commercial Order. Only {@code commercialOrderId} is
 * required; the remaining fields enrich the profile and are optional (a {@code null} field keeps the value already
 * known from the Lead snapshot).
 *
 * @param commercialOrderId the source Commercial Order to consolidate from (required)
 * @param name the customer-facing name (optional override)
 * @param document the fiscal document (CPF/CNPJ), or {@code null}
 * @param documentType the document type, or {@code null}
 * @param email the e-mail, or {@code null}
 * @param phone the phone (digits only), or {@code null}
 * @param whatsapp the WhatsApp number (digits only), or {@code null}
 * @param preferredContactMethod the preferred contact channel, or {@code null}
 * @param notes free-text notes, or {@code null}
 */
public record CreateCustomerRequest(
        @NotNull UUID commercialOrderId,
        @Size(max = 200) String name,
        @Size(max = 30) String document,
        @Size(max = 20) String documentType,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "\\d*") @Size(max = 30) String phone,
        @Pattern(regexp = "\\d*") @Size(max = 30) String whatsapp,
        ContactMethod preferredContactMethod,
        @Size(max = 2000) String notes) {

    /**
     * Maps this request to the application-service command.
     *
     * @return the create/consolidate command
     */
    public CreateCustomerCommand toCommand() {
        return new CreateCustomerCommand(
                commercialOrderId, name, document, documentType, email, phone, whatsapp, preferredContactMethod, notes);
    }
}
