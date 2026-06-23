package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A commercial Customer: the company's client, the **commercial graduation of a Lead**. It is materialized
 * from the source Lead when a Commercial Order is created (deal closed), snapshotting the Lead's name and
 * contacts so the client identity is stable for downstream use (it is the **payer** referenced by Financial
 * Operations' Receivables, and the future Customer Care subject). One Lead originates at most one Customer
 * ({@code leadId} unique). It is NOT a Receivable, Payment, Invoice or accounting record. The document
 * (CPF/CNPJ) and billing address are optional placeholders filled by a later slice.
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

    // Fiscal document (CPF/CNPJ) — optional placeholder; a later slice captures and validates it.
    @Size(max = 30)
    private String document;

    @Size(max = 20)
    @Column(name = "document_type")
    private String documentType;

    // Free-text billing address — optional placeholder; a later slice structures it.
    @Size(max = 500)
    @Column(name = "billing_address")
    private String billingAddress;

    @Column(nullable = false)
    private boolean active;

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
     * Materializes a Customer from its source Lead, snapshotting the Lead's name and contacts. The customer
     * starts active; the document and billing address are left empty for a later slice.
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
        customer.active = true;
        customer.createdBy = createdBy;
        customer.updatedBy = createdBy;
        return customer;
    }
}
