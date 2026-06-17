package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A commercial Proposal: the formalized commercial offer to the client, created from an Opportunity that
 * is {@link OpportunityStage#READY_FOR_PROPOSAL}. It is the aggregate root of the Sales &amp; Proposals
 * bounded context ({@code domain.sales}). It is NOT a Sale, Sales Order, Booking, Customer, Financial,
 * Payment or Commission record. The source Opportunity (and through it, the Lead) remains the system of
 * record for the negotiation; the Proposal references it by {@link #opportunityId} (never modified) and
 * keeps the source {@link #leadId} for traceability. A new Proposal starts at {@link ProposalStatus#DRAFT}.
 */
@Entity
@Table(name = "proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Proposal {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source (READY_FOR_PROPOSAL) Opportunity this Proposal was created from, kept for traceability.
    // The Opportunity is not modified and stays the source of truth for the negotiation.
    @NotNull
    @Column(name = "opportunity_id", nullable = false, updatable = false)
    private UUID opportunityId;

    // The source Lead reference, denormalized from the Opportunity for direct traceability (the Lead is
    // still the system of record for the contact and its history).
    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String title;

    @Size(max = 2000)
    private String notes;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Size(max = 4000)
    @Column(name = "commercial_terms")
    private String commercialTerms;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

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
     * Creates a new Proposal (status {@link ProposalStatus#DRAFT}) from a READY_FOR_PROPOSAL Opportunity,
     * seeding the source Opportunity and Lead references from it and copying the client-facing data. The
     * Opportunity itself is not changed, and no Sale, Sales Order, Booking, Financial or Commission data
     * is created.
     *
     * @param opportunity the source Opportunity; must be READY_FOR_PROPOSAL
     * @param responsiblePersonId the responsible (the command's value, or the Opportunity's by default)
     * @param command the proposal data (title, notes, validity, commercial terms)
     * @param createdBy id of the user creating the Proposal
     * @return a new, unsaved Proposal
     * @throws OpportunityNotReadyForProposalException if the Opportunity is not READY_FOR_PROPOSAL
     */
    public static Proposal createFromOpportunity(
            Opportunity opportunity, UUID responsiblePersonId, CreateProposalCommand command, UUID createdBy) {
        if (opportunity.stage() != OpportunityStage.READY_FOR_PROPOSAL) {
            throw new OpportunityNotReadyForProposalException();
        }
        Proposal proposal = new Proposal();
        proposal.id = UUID.randomUUID();
        proposal.opportunityId = opportunity.id();
        proposal.leadId = opportunity.leadId();
        proposal.responsiblePersonId = responsiblePersonId;
        proposal.title = command.title();
        proposal.notes = emptyToNull(command.notes());
        proposal.validUntil = command.validUntil();
        proposal.commercialTerms = emptyToNull(command.commercialTerms());
        proposal.status = ProposalStatus.DRAFT;
        proposal.createdBy = createdBy;
        proposal.updatedBy = createdBy;
        return proposal;
    }

    /**
     * Whether the Proposal is still open (not a terminal-negative outcome). See {@link ProposalStatus#isOpen()}.
     *
     * @return {@code true} unless the Proposal is rejected, expired or cancelled
     */
    public boolean isOpen() {
        return status.isOpen();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
