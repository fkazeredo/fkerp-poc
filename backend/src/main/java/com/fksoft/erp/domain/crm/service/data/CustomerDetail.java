package com.fksoft.erp.domain.crm.service.data;

import com.fksoft.erp.domain.crm.model.ContactMethod;
import com.fksoft.erp.domain.crm.model.Customer;
import com.fksoft.erp.domain.crm.model.CustomerStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a Customer Profile (the commercial client / payer / Customer Care subject). Built from the entity
 * via {@link #from(Customer)}; the JSON contract for any Customer read. It exposes the customer master plus its
 * preserved commercial origin (source Order / Proposal / Opportunity) — never Booking, Receivable, Payment or
 * Commission data.
 *
 * @param id the customer id
 * @param leadId the originating Lead id
 * @param sourceCommercialOrderId the source Commercial Order the profile was consolidated from, or {@code null}
 * @param sourceProposalId the source Proposal (commercial origin), or {@code null}
 * @param sourceOpportunityId the source Opportunity (commercial origin), or {@code null}
 * @param name the client name
 * @param phone the phone, or {@code null}
 * @param whatsapp the WhatsApp number, or {@code null}
 * @param email the e-mail, or {@code null}
 * @param preferredContactMethod the preferred contact channel, or {@code null}
 * @param document the fiscal document (CPF/CNPJ), or {@code null}
 * @param documentType the document type, or {@code null}
 * @param billingAddress the billing address, or {@code null}
 * @param notes free-text notes, or {@code null}
 * @param status the customer lifecycle status (starts {@code ACTIVE})
 * @param createdAt when the customer was first materialized
 */
public record CustomerDetail(
        UUID id,
        UUID leadId,
        UUID sourceCommercialOrderId,
        UUID sourceProposalId,
        UUID sourceOpportunityId,
        String name,
        String phone,
        String whatsapp,
        String email,
        ContactMethod preferredContactMethod,
        String document,
        String documentType,
        String billingAddress,
        String notes,
        CustomerStatus status,
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
                c.sourceCommercialOrderId(),
                c.sourceProposalId(),
                c.sourceOpportunityId(),
                c.name(),
                c.phone(),
                c.whatsapp(),
                c.email(),
                c.preferredContactMethod(),
                c.document(),
                c.documentType(),
                c.billingAddress(),
                c.notes(),
                c.status(),
                c.createdAt());
    }
}
