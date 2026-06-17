package com.fksoft.erp.domain.crm.model;

import com.fksoft.erp.domain.crm.exception.LeadNotQualifiedForOpportunityException;
import com.fksoft.erp.domain.crm.exception.OpportunityCannotBeMarkedLostException;
import com.fksoft.erp.domain.crm.exception.OpportunityStageTransitionException;
import com.fksoft.erp.domain.crm.service.data.CreateOpportunityCommand;
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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
 * A commercial Opportunity: a real negotiation created from a QUALIFIED Lead. It is NOT a Proposal,
 * Sale, Sales Order, Booking, Customer or Financial record. The source Lead remains the system of
 * record for the contact and its history (referenced by {@link #leadId}, never modified); the
 * Opportunity seeds the data it needs to be worked on its own (origin, responsible, main interest) and
 * moves through the commercial pipeline. Aggregate root of the Opportunity area of the Commercial /
 * CRM module.
 */
@Entity
@Table(name = "opportunities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Opportunity {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // The source (QUALIFIED) Lead this Opportunity was created from, kept for traceability. The Lead is
    // not modified and stays the source of truth for contact data and history. One Opportunity per Lead.
    @NotNull
    @Column(name = "lead_id", nullable = false, updatable = false, unique = true)
    private UUID leadId;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false)
    private Origin origin;

    @Column(name = "responsible_person_id")
    private UUID responsiblePersonId;

    @Size(max = 500)
    @Column(name = "main_interest")
    private String mainInterest;

    @Size(max = 200)
    @Column(name = "product_type")
    private String productType;

    @PositiveOrZero
    @Column(name = "estimated_value")
    private BigDecimal estimatedValue;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OpportunityStage stage;

    @Size(max = 2000)
    private String notes;

    // Loss outcome (set when the Opportunity is marked lost; kept for history). The source Lead is never
    // touched — the Opportunity owns its own loss outcome, mirroring the Lead's loss fields.
    @Column(name = "lost_at")
    private Instant lostAt;

    @Column(name = "lost_by")
    private UUID lostBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_reason")
    private OpportunityLossReason lossReason;

    @Size(max = 2000)
    @Column(name = "loss_note")
    private String lossNote;

    // Pipeline stage-movement history (part of the aggregate): every transition is kept for the record.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private List<OpportunityStageChange> stageChanges = new ArrayList<>();

    // Commercial activity history (part of the aggregate): append-only negotiation log.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "opportunity_id", nullable = false)
    private List<OpportunityActivity> activities = new ArrayList<>();

    // Planned next action date, denormalized from the latest activity that defined one (like Lead.nextContactAt).
    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

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
     * Creates a new Opportunity (stage {@link OpportunityStage#NEW_OPPORTUNITY}) from a QUALIFIED Lead,
     * seeding the origin, name, responsible and main interest from it and copying the optional
     * commercial estimates. The Lead itself is not changed.
     *
     * @param lead the source Lead; must be QUALIFIED
     * @param responsiblePersonId the responsible (the command's value, or the Lead's by default)
     * @param command the optional commercial data (product type, estimated value, expected close, note)
     * @param createdBy id of the user creating the Opportunity
     * @return a new, unsaved Opportunity
     * @throws LeadNotQualifiedForOpportunityException if the Lead is not QUALIFIED
     */
    public static Opportunity createFromLead(
            Lead lead, UUID responsiblePersonId, CreateOpportunityCommand command, UUID createdBy) {
        if (lead.status() != LeadStatus.QUALIFIED) {
            throw new LeadNotQualifiedForOpportunityException();
        }
        Opportunity opportunity = new Opportunity();
        opportunity.id = UUID.randomUUID();
        opportunity.leadId = lead.id();
        opportunity.name = lead.name();
        opportunity.origin = lead.origin();
        opportunity.responsiblePersonId = responsiblePersonId;
        opportunity.mainInterest = lead.mainInterest();
        opportunity.productType = emptyToNull(command.productType());
        opportunity.estimatedValue = command.estimatedValue();
        opportunity.expectedCloseDate = command.expectedCloseDate();
        opportunity.stage = OpportunityStage.NEW_OPPORTUNITY;
        opportunity.notes = emptyToNull(command.initialNote());
        opportunity.createdBy = createdBy;
        opportunity.updatedBy = createdBy;
        return opportunity;
    }

    /**
     * Marks the Opportunity as lost with a reason. Allowed from any non-lost stage; the loss
     * (reason/who/when/note) is kept for history. The source Lead is not affected.
     *
     * @param reason the loss reason
     * @param byUser id of the user marking the Opportunity lost
     * @param note optional loss note
     * @throws OpportunityCannotBeMarkedLostException if the Opportunity is already lost
     */
    public void markLost(OpportunityLossReason reason, UUID byUser, String note) {
        if (stage == OpportunityStage.LOST) {
            throw new OpportunityCannotBeMarkedLostException();
        }
        recordStageChange(stage, OpportunityStage.LOST, byUser);
        stage = OpportunityStage.LOST;
        lossReason = reason;
        lostAt = Instant.now();
        lostBy = byUser;
        lossNote = emptyToNull(note);
        updatedBy = byUser;
    }

    /**
     * Advances the Opportunity one step forward along the pipeline funnel
     * ({@code NEW_OPPORTUNITY → DISCOVERY → PRODUCT_FIT → READY_FOR_PROPOSAL}) and records the movement.
     * Skipping a stage, going back, and moving to LOST are rejected (LOST is reached only through
     * {@link #markLost}, and it is terminal).
     *
     * @param target the destination stage (must be the immediate next stage)
     * @param byUser id of the user moving the Opportunity
     * @throws OpportunityStageTransitionException if {@code target} is not the immediate next stage
     */
    public void moveToStage(OpportunityStage target, UUID byUser) {
        if (!stage.canAdvanceTo(target)) {
            throw new OpportunityStageTransitionException();
        }
        recordStageChange(stage, target, byUser);
        stage = target;
        updatedBy = byUser;
    }

    private void recordStageChange(OpportunityStage from, OpportunityStage to, UUID byUser) {
        stageChanges.add(OpportunityStageChange.of(from, to, byUser));
    }

    /**
     * Updates the editable commercial details (estimated value, expected closing date, product type and
     * commercial notes); a {@code null} clears the field. Does not touch the main interest (kept from the
     * Lead qualification), the stage, the responsible or the source Lead, and creates no Financial,
     * Booking, Proposal or Commission data. The change is traced via the audit fields ({@code updatedBy}).
     *
     * @param estimatedValue the estimated value ({@code >= 0}), or {@code null}
     * @param expectedCloseDate the expected closing date, or {@code null}
     * @param productType the product / service interest area (free text), or {@code null}
     * @param notes the commercial notes, or {@code null}
     * @param byUser id of the user editing the Opportunity
     */
    public void updateCommercialDetails(
            BigDecimal estimatedValue, LocalDate expectedCloseDate, String productType, String notes, UUID byUser) {
        this.estimatedValue = estimatedValue;
        this.expectedCloseDate = expectedCloseDate;
        this.productType = emptyToNull(productType);
        this.notes = emptyToNull(notes);
        updatedBy = byUser;
    }

    /**
     * Registers a commercial activity (append-only negotiation history) and updates the planned next
     * action date when the activity defines one. The activity never moves the pipeline stage and never
     * creates a Proposal, Sale, Booking or Financial record.
     *
     * @param type the activity type
     * @param result the activity outcome
     * @param description what happened
     * @param occurredAt when the activity happened
     * @param nextActionDate optional planned next action date
     * @param byUser id of the user registering the activity (its author)
     */
    public void recordActivity(
            OpportunityActivityType type,
            OpportunityActivityResult result,
            String description,
            Instant occurredAt,
            LocalDate nextActionDate,
            UUID byUser) {
        activities.add(OpportunityActivity.of(type, result, description, occurredAt, nextActionDate, byUser));
        if (nextActionDate != null) {
            this.nextActionDate = nextActionDate;
        }
        updatedBy = byUser;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
