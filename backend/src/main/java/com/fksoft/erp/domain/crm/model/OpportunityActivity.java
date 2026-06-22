package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A commercial activity registered on an {@link Opportunity} (part of the Opportunity aggregate): the
 * type, the outcome (result), a description, when it happened, who registered it, and an optional next
 * action date. Preserves the negotiation history (append-only). It never creates a Proposal, Sale,
 * Booking or Financial record, and never moves the pipeline stage on its own.
 */
@Entity
@Table(name = "opportunity_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityActivity {

    @Id
    private UUID id;

    // Read-only mapping of the owning FK (written by the parent Opportunity's @OneToMany @JoinColumn);
    // lets queries reference the opportunity id (e.g. the "without recent activity" pending predicate).
    @Column(name = "opportunity_id", insertable = false, updatable = false)
    private UUID opportunityId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private OpportunityActivityType type;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private OpportunityActivityResult result;

    @NotBlank
    @Size(max = 4000)
    @Column(nullable = false)
    private String description;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @NotNull
    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    static OpportunityActivity of(
            OpportunityActivityType type,
            OpportunityActivityResult result,
            String description,
            Instant occurredAt,
            LocalDate nextActionDate,
            UUID registeredBy) {
        OpportunityActivity activity = new OpportunityActivity();
        activity.id = UUID.randomUUID();
        activity.type = type;
        activity.result = result;
        activity.description = description;
        activity.occurredAt = occurredAt;
        activity.nextActionDate = nextActionDate;
        activity.registeredBy = registeredBy;
        return activity;
    }
}
