package com.fksoft.erp.domain.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
