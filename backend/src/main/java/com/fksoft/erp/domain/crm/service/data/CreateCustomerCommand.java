package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.ContactMethod;
import java.util.UUID;

/**
 * Command to create or consolidate a Customer Profile from a Commercial Order. The {@code commercialOrderId} is the
 * source deal (required); the remaining fields enrich the profile and are optional — a {@code null} field keeps the
 * value already known (the Lead snapshot), so consolidating never wipes contacts.
 *
 * @param commercialOrderId the source Commercial Order to consolidate the customer from (required)
 * @param name the customer-facing name (optional override; null keeps the Lead snapshot)
 * @param document the fiscal document (CPF/CNPJ), or {@code null}
 * @param documentType the document type, or {@code null}
 * @param email the e-mail, or {@code null}
 * @param phone the phone (digits only), or {@code null}
 * @param whatsapp the WhatsApp number (digits only), or {@code null}
 * @param preferredContactMethod the preferred contact channel, or {@code null}
 * @param notes free-text notes, or {@code null}
 */
public record CreateCustomerCommand(
        UUID commercialOrderId,
        String name,
        String document,
        String documentType,
        String email,
        String phone,
        String whatsapp,
        ContactMethod preferredContactMethod,
        String notes) {}
