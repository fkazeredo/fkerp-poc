package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.service.data.CreateCustomerCommand;
import com.fksoft.erp.domain.sales.model.CommercialOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A commercial Customer: the company's real client, the <b>commercial graduation of a Lead</b> and the subject of
 * Customer Management. It is first materialized from the source Lead when a Commercial Order is created (deal
 * closed), snapshotting the Lead's name and contacts so the client identity is stable for downstream use (it is the
 * <b>payer</b> referenced by Financial Operations' Receivables, and the Customer Care subject). One Lead originates
 * at most one Customer ({@code leadId} unique). A Customer Management user later <b>consolidates</b> the profile from
 * a Commercial Order ({@link #consolidateFromOrder}), enriching the contacts/document/notes and preserving the
 * commercial origin (source Order / Proposal / Opportunity). It is NOT a Lead, an Opportunity, a Receivable, a
 * Payment, an Invoice or an accounting record, and Customer Management never owns the Order/Booking/Receivable/
 * Commission it references.
 */
@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The originating Lead (1:1 — a Lead graduates to at most one Customer). Preserved, immutable.
    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false, unique = true)
    private UUID leadId;

    // The commercial origin preserved when the profile is consolidated from a Commercial Order. Set once (the
    // originating deal), never overwritten; null until consolidated. Customer Management does NOT own these.
    @Column(name = "source_commercial_order_id")
    private UUID sourceCommercialOrderId;

    @Column(name = "source_proposal_id")
    private UUID sourceProposalId;

    @Column(name = "source_opportunity_id")
    private UUID sourceOpportunityId;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String name;

    @Pattern(regexp = "\\d*")
    @Size(max = 30)
    private String phone;

    @Pattern(regexp = "\\d*")
    @Size(max = 30)
    private String whatsapp;

    @Email
    @Size(max = 255)
    private String email;

    // The preferred contact channel (optional), one of the channels the Customer holds.
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", length = 20)
    private ContactMethod preferredContactMethod;

    // Fiscal document (CPF/CNPJ) — optional; a later slice validates it.
    @Size(max = 30)
    private String document;

    @Size(max = 20)
    @Column(name = "document_type")
    private String documentType;

    // Free-text billing address — optional placeholder; a later slice structures it.
    @Size(max = 500)
    @Column(name = "billing_address")
    private String billingAddress;

    @Size(max = 2000)
    private String notes;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    /**
     * Materializes a Customer from its source Lead, snapshotting the Lead's name and contacts. The customer starts
     * {@code ACTIVE}; the document, billing address and commercial-origin references are left empty until a Customer
     * Management user consolidates the profile.
     *
     * @param lead the source Lead (the commercial origin)
     * @param createdBy id of the user (or system action) creating the Customer
     * @return a new, unsaved Customer
     */
    public static Customer fromLead(Lead lead, UUID createdBy) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.leadId = lead.id();
        customer.name = lead.name();
        customer.phone = lead.phone();
        customer.whatsapp = lead.whatsapp();
        customer.email = lead.email();
        customer.status = CustomerStatus.ACTIVE;
        customer.createdBy = createdBy;
        customer.updatedBy = createdBy;
        return customer;
    }

    /**
     * Consolidates this customer's profile from a Commercial Order: preserves the commercial origin (source Order /
     * Proposal / Opportunity — set once, the originating deal) and enriches the customer-facing name, contacts,
     * preferred channel, document and notes with the supplied data. A field left absent ({@code null}) keeps its
     * current value (the Lead snapshot), so consolidating never wipes contacts already known. It does not change the
     * source Order, Booking, Receivable, Payment or Commission, and creates no Customer Care data.
     *
     * @param order the source Commercial Order (the commercial origin to preserve)
     * @param command the supplied profile data (name / document / contacts / preferred channel / notes)
     * @param by id of the user performing the consolidation
     */
    public void consolidateFromOrder(CommercialOrder order, CreateCustomerCommand command, UUID by) {
        if (this.sourceCommercialOrderId == null) {
            this.sourceCommercialOrderId = order.id();
            this.sourceProposalId = order.proposalId();
            this.sourceOpportunityId = order.opportunityId();
        }
        if (command.name() != null && !command.name().isBlank()) {
            this.name = command.name().strip();
        }
        if (command.document() != null) {
            this.document = command.document();
        }
        if (command.documentType() != null) {
            this.documentType = command.documentType();
        }
        if (command.email() != null) {
            this.email = command.email();
        }
        if (command.phone() != null) {
            this.phone = command.phone();
        }
        if (command.whatsapp() != null) {
            this.whatsapp = command.whatsapp();
        }
        if (command.preferredContactMethod() != null) {
            this.preferredContactMethod = command.preferredContactMethod();
        }
        if (command.notes() != null) {
            this.notes = command.notes();
        }
        this.updatedBy = by;
    }
}
