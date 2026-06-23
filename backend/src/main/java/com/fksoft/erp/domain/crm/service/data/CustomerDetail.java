package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.Customer;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a Customer (the commercial client / payer). Built from the entity via {@link #from(Customer)};
 * the JSON contract for any Customer read.
 *
 * @param id the customer id
 * @param leadId the originating Lead id
 * @param name the client name (snapshot from the Lead)
 * @param phone the phone (snapshot), or {@code null}
 * @param whatsapp the WhatsApp number (snapshot), or {@code null}
 * @param email the e-mail (snapshot), or {@code null}
 * @param document the fiscal document (CPF/CNPJ), or {@code null}
 * @param documentType the document type, or {@code null}
 * @param billingAddress the billing address, or {@code null}
 * @param active whether the customer is active
 * @param createdAt when the customer was materialized
 */
public record CustomerDetail(
        UUID id,
        UUID leadId,
        String name,
        String phone,
        String whatsapp,
        String email,
        String document,
        String documentType,
        String billingAddress,
        boolean active,
        Instant createdAt) {

    /**
     * Builds the read model from the entity.
     *
     * @param c the customer entity
     * @return the read model
     */
    public static CustomerDetail from(Customer c) {
        return new CustomerDetail(
                c.id(),
                c.leadId(),
                c.name(),
                c.phone(),
                c.whatsapp(),
                c.email(),
                c.document(),
                c.documentType(),
                c.billingAddress(),
                c.active(),
                c.createdAt());
    }
}
