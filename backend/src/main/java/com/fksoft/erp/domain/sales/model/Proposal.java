package com.fksoft.erp.domain.sales.model;

import com.fksoft.erp.domain.crm.model.Opportunity;
import com.fksoft.erp.domain.sales.exception.OpportunityNotReadyForProposalException;
import com.fksoft.erp.domain.sales.exception.ProposalDiscountInvalidException;
import com.fksoft.erp.domain.sales.exception.ProposalHasNoItemsException;
import com.fksoft.erp.domain.sales.exception.ProposalItemNotFoundException;
import com.fksoft.erp.domain.sales.exception.ProposalNotEditableException;
import com.fksoft.erp.domain.sales.exception.ProposalRejectionReasonRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalResponsibleRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalTotalRequiredException;
import com.fksoft.erp.domain.sales.exception.ProposalValidityRequiredException;
import com.fksoft.erp.domain.sales.service.data.CreateProposalCommand;
import com.fksoft.erp.domain.sales.service.data.ProposalItemCommand;
import com.fksoft.erp.domain.sales.service.data.UpdateProposalCommand;
import com.fksoft.erp.domain.workflow.WorkflowState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * is {@code READY_FOR_PROPOSAL}. It is the aggregate root of the Sales &amp; Proposals
 * bounded context ({@code domain.sales}). It is NOT a Sale, Sales Order, Booking, Customer, Financial,
 * Payment or Commission record. The source Opportunity (and through it, the Lead) remains the system of
 * record for the negotiation; the Proposal references it by {@link #opportunityId} (never modified) and
 * keeps the source {@link #leadId} for traceability. A new Proposal starts at {@code DRAFT}.
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

    // Descriptive payment notes for the offer (free text). NOT a Financial / Payment / Receivable record.
    @Size(max = 4000)
    @Column(name = "payment_notes")
    private String paymentNotes;

    // Denormalized status code (== currentState.code()), kept for cheap filtering/grouping and the read
    // contract; the data-driven source of truth is the workflow state, kept in sync on every transition.
    @NotBlank
    @Size(max = 60)
    @Column(nullable = false)
    private String status;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_state_id", nullable = false)
    private WorkflowState currentState;

    // The commercial offer's lines (part of the aggregate). Editable only while the Proposal is a Draft.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proposal_id", nullable = false)
    private List<ProposalItem> items = new ArrayList<>();

    // The status-change history (part of the aggregate): every lifecycle transition is kept for the record.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "proposal_id", nullable = false)
    private List<ProposalStatusChange> statusChanges = new ArrayList<>();

    // An optional Proposal-level discount applied to the items subtotal (in addition to per-line discounts).
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type")
    private DiscountType discountType;

    @Column(name = "discount_value")
    private BigDecimal discountValue;

    // The items subtotal (sum of the lines' totals), denormalized so the detail exposes it without an N+1.
    @NotNull
    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    // The final Proposal total (subtotal minus the Proposal-level discount; never negative), denormalized
    // from the items + discount (recomputed on every change) so the list and detail expose it without an N+1.
    @NotNull
    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    // Set when the Proposal is rejected at internal review: the structured reason and an optional note (the
    // "why"). The who/when of the rejection lives in the status-change history.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejection_reason_id")
    private ProposalRejectionReason rejectionReason;

    @Size(max = 2000)
    @Column(name = "rejection_note")
    private String rejectionNote;

    // Set when the Proposal is marked as sent to the client: the optional descriptive channel (the "how").
    // The who/when of the send lives in the status-change history. Informational only — no real e-mail/
    // WhatsApp/phone integration, and no customer acceptance, Order, Booking, Financial or Commission data.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sending_channel_id")
    private SendingChannel sendingChannel;

    // Set when the client accepts the Proposal: an optional confirmation note (the who/when lives in the
    // status-change history). Acceptance creates no Booking, Financial, Commission or Commercial Order data.
    @Size(max = 2000)
    @Column(name = "acceptance_note")
    private String acceptanceNote;

    // Set when the client rejects the Proposal: the structured reason and an optional note (the "why"). The
    // who/when lives in the status-change history. Distinct from the internal-review rejectionReason.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_rejection_reason_id")
    private CustomerRejectionReason customerRejectionReason;

    @Size(max = 2000)
    @Column(name = "customer_rejection_note")
    private String customerRejectionNote;

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
     * Creates a new Proposal (status {@code DRAFT}) from a READY_FOR_PROPOSAL Opportunity,
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
            Opportunity opportunity,
            UUID responsiblePersonId,
            CreateProposalCommand command,
            WorkflowState initialState,
            UUID createdBy) {
        if (!"READY_FOR_PROPOSAL".equals(opportunity.stage())) {
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
        proposal.currentState = initialState;
        proposal.status = initialState.code();
        proposal.createdBy = createdBy;
        proposal.updatedBy = createdBy;
        return proposal;
    }

    /**
     * Whether the Proposal is still open (not a terminal-negative outcome: REJECTED/EXPIRED/CANCELLED).
     *
     * @return {@code true} unless the Proposal is rejected, expired or cancelled
     */
    public boolean isOpen() {
        return !"REJECTED".equals(status) && !"EXPIRED".equals(status) && !"CANCELLED".equals(status);
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
        recomputeTotals();
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
        recomputeTotals();
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
        recomputeTotals();
        updatedBy = byUser;
    }

    /**
     * Edits the Proposal's commercial details (Draft only): validity, commercial terms, descriptive payment
     * notes and the optional Proposal-level discount, then refreshes the total. Creates no Financial,
     * Receivable, Payment, Booking or Commission data.
     *
     * @param validUntil the validity date (optional)
     * @param commercialTerms the commercial terms (optional)
     * @param paymentNotes descriptive payment notes (optional, free text — not a Financial record)
     * @param discountType the Proposal-level discount type, or {@code null} for no discount
     * @param discountValue the Proposal-level discount value, or {@code null} for no discount
     * @param byUser id of the user editing the Proposal
     * @throws ProposalNotEditableException if the Proposal is not a Draft
     * @throws ProposalDiscountInvalidException if the discount is invalid (pairing or range)
     */
    public void updateCommercialDetails(
            LocalDate validUntil,
            String commercialTerms,
            String paymentNotes,
            DiscountType discountType,
            BigDecimal discountValue,
            UUID byUser) {
        requireDraft();
        validateProposalDiscount(discountType, discountValue);
        this.validUntil = validUntil;
        this.commercialTerms = emptyToNull(commercialTerms);
        this.paymentNotes = emptyToNull(paymentNotes);
        this.discountType = discountType;
        this.discountValue = discountValue;
        recomputeTotals();
        updatedBy = byUser;
    }

    /** Convenience overload taking the command record. */
    public void updateCommercialDetails(UpdateProposalCommand command, UUID byUser) {
        updateCommercialDetails(
                command.validUntil(),
                command.commercialTerms(),
                command.paymentNotes(),
                command.discountType(),
                command.discountValue(),
                byUser);
    }

    /**
     * Submits the Proposal for review (Draft → {@code READY_FOR_REVIEW}). The offer must have
     * at least one item, a positive total, a validity date and a responsible person. This action does not
     * send the Proposal to the client, and creates no Sale, Order, Booking, Financial or Commission data.
     *
     * @param byUser id of the user submitting the Proposal
     * @throws ProposalNotEditableException if the Proposal is not a Draft
     * @throws ProposalHasNoItemsException if the Proposal has no items
     * @throws ProposalTotalRequiredException if the Proposal total is not positive
     * @throws ProposalValidityRequiredException if the Proposal has no validity date
     * @throws ProposalResponsibleRequiredException if the Proposal has no responsible person
     */
    public void applySubmit(WorkflowState target, UUID byUser) {
        if (items.isEmpty()) {
            throw new ProposalHasNoItemsException();
        }
        if (total.signum() <= 0) {
            throw new ProposalTotalRequiredException();
        }
        if (validUntil == null) {
            throw new ProposalValidityRequiredException();
        }
        if (responsiblePersonId == null) {
            throw new ProposalResponsibleRequiredException();
        }
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    /**
     * Approves a Proposal under review (Ready for Review → {@code APPROVED}), recording who and
     * when in the status history. This does not send the Proposal to the client, and creates no Sale, Order,
     * Booking, Financial or Commission data.
     *
     * @param byUser id of the approver
     * @throws ProposalNotUnderReviewException if the Proposal is not Ready for Review
     */
    public void applyApprove(WorkflowState target, UUID byUser) {
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    /**
     * Rejects a Proposal under review (Ready for Review → {@code REJECTED}) with a reason,
     * recording who and when in the status history and keeping the structured reason and optional note. The
     * rejected Proposal is terminal (it frees the Opportunity for a new Proposal); it is not sent to the
     * client and creates no Sale, Order, Booking, Financial or Commission data.
     *
     * @param byUser id of the approver
     * @param reason the rejection reason (required)
     * @param note an optional free-text note
     * @throws ProposalNotUnderReviewException if the Proposal is not Ready for Review
     * @throws ProposalRejectionReasonRequiredException if no reason is given
     */
    public void applyReject(WorkflowState target, UUID byUser, ProposalRejectionReason reason, String note) {
        if (reason == null) {
            throw new ProposalRejectionReasonRequiredException();
        }
        rejectionReason = reason;
        rejectionNote = emptyToNull(note);
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    /**
     * Marks an approved Proposal as sent to the client (Approved → {@code SENT}), recording who
     * and when in the status history and keeping the optional descriptive sending channel. The Proposal stays
     * open for the client's decision. This does NOT trigger any real e-mail/WhatsApp/phone integration, and
     * creates no customer acceptance, Commercial Order, Booking, Financial or Commission data.
     *
     * @param byUser id of the user registering the send
     * @param channel the descriptive sending channel, or {@code null} (the channel is optional)
     * @throws ProposalNotApprovedException if the Proposal is not Approved
     */
    public void applySend(WorkflowState target, UUID byUser, SendingChannel channel) {
        sendingChannel = channel;
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    /**
     * Registers that the client accepted a sent Proposal (Sent → {@code ACCEPTED}), recording
     * who and when in the status history and keeping an optional confirmation note. The accepted Proposal
     * stays open (it is the winning offer and prepares the future Commercial Order). This does NOT create any
     * Booking, Financial, Commission or Commercial Order data.
     *
     * @param byUser id of the user registering the acceptance
     * @param note an optional client confirmation note
     * @throws ProposalNotSentException if the Proposal is not Sent
     */
    public void applyAccept(WorkflowState target, UUID byUser, String note) {
        acceptanceNote = emptyToNull(note);
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    /**
     * Registers that the client rejected a sent Proposal (Sent → {@code REJECTED}) with a
     * reason, recording who and when in the status history and keeping the structured reason and optional
     * note. The rejected Proposal is terminal (it frees the Opportunity for a new Proposal); it creates no
     * Booking, Financial, Commission or Commercial Order data.
     *
     * @param byUser id of the user registering the rejection
     * @param reason the customer-rejection reason (required)
     * @param note an optional free-text note
     * @throws ProposalNotSentException if the Proposal is not Sent
     * @throws ProposalRejectionReasonRequiredException if no reason is given
     */
    public void applyDecline(WorkflowState target, UUID byUser, CustomerRejectionReason reason, String note) {
        if (reason == null) {
            throw new ProposalRejectionReasonRequiredException();
        }
        customerRejectionReason = reason;
        customerRejectionNote = emptyToNull(note);
        recordStatusChange(status, target.code(), byUser);
        status = target.code();
        currentState = target;
        updatedBy = byUser;
    }

    // Appends a lifecycle transition to the status history (the initial DRAFT is not recorded — the history
    // stays empty until the first transition). Future transitions (approve/send/accept/reject) reuse this.
    private void recordStatusChange(String from, String to, UUID byUser) {
        statusChanges.add(ProposalStatusChange.of(from, to, byUser));
    }

    private void requireDraft() {
        if (!"DRAFT".equals(status)) {
            throw new ProposalNotEditableException();
        }
    }

    private void validateProposalDiscount(DiscountType type, BigDecimal value) {
        if ((type == null) != (value == null)) {
            throw new ProposalDiscountInvalidException();
        }
        if (type != null && !type.isValid(value, itemsSubtotal())) {
            throw new ProposalDiscountInvalidException();
        }
    }

    private void recomputeTotals() {
        subtotal = itemsSubtotal();
        BigDecimal discount = discountType == null ? BigDecimal.ZERO : discountType.amountOf(discountValue, subtotal);
        // Cap the effective discount at the subtotal so the total can never go negative — e.g. if items are
        // removed after a fixed-amount discount was set, leaving the discount larger than the new subtotal.
        discount = discount.min(subtotal);
        total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal itemsSubtotal() {
        return items.stream()
                .map(ProposalItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
