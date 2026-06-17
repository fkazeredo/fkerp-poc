package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.crm.model.OpportunityStage;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    // The commercial offer's lines (part of the aggregate). Editable only while the Proposal is a Draft.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proposal_id", nullable = false)
    private List<ProposalItem> items = new ArrayList<>();

    // The Proposal total, denormalized from the items (recomputed on every item change) so the list and
    // detail expose it without an N+1.
    @NotNull
    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

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

    /**
     * Adds an item to the Proposal (Draft only) and refreshes the total. Creates no Booking, Financial or
     * Commission data and does not check external availability.
     *
     * @param command the item data (type, description, quantity, unit value, optional discount)
     * @param byUser id of the user editing the Proposal
     * @throws ProposalNotEditableException if the Proposal is not a Draft
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException if the discount is invalid
     */
    public void addItem(ProposalItemCommand command, UUID byUser) {
        requireDraft();
        items.add(ProposalItem.of(
                command.type(),
                command.description(),
                command.quantity(),
                command.unitValue(),
                command.discountType(),
                command.discountValue()));
        recomputeTotal();
        updatedBy = byUser;
    }

    /**
     * Updates an existing item (Draft only) and refreshes the total.
     *
     * @param itemId the item id
     * @param command the new item data
     * @param byUser id of the user editing the Proposal
     * @throws ProposalNotEditableException if the Proposal is not a Draft
     * @throws ProposalItemNotFoundException if the item does not belong to this Proposal
     * @throws com.fksoft.erp.domain.sales.exception.ProposalItemInvalidException if the discount is invalid
     */
    public void updateItem(UUID itemId, ProposalItemCommand command, UUID byUser) {
        requireDraft();
        ProposalItem item = items.stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow(ProposalItemNotFoundException::new);
        item.update(
                command.type(),
                command.description(),
                command.quantity(),
                command.unitValue(),
                command.discountType(),
                command.discountValue());
        recomputeTotal();
        updatedBy = byUser;
    }

    /**
     * Removes an item from the Proposal (Draft only) and refreshes the total.
     *
     * @param itemId the item id
     * @param byUser id of the user editing the Proposal
     * @throws ProposalNotEditableException if the Proposal is not a Draft
     * @throws ProposalItemNotFoundException if the item does not belong to this Proposal
     */
    public void removeItem(UUID itemId, UUID byUser) {
        requireDraft();
        if (!items.removeIf(i -> i.id().equals(itemId))) {
            throw new ProposalItemNotFoundException();
        }
        recomputeTotal();
        updatedBy = byUser;
    }

    private void requireDraft() {
        if (status != ProposalStatus.DRAFT) {
            throw new ProposalNotEditableException();
        }
    }

    private void recomputeTotal() {
        total = items.stream()
                .map(ProposalItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
