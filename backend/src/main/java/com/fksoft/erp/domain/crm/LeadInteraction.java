package com.fksoft.erp.domain.crm;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single interaction/history entry of a {@link Lead} (part of the Lead aggregate): the
 * creation-time internal note ({@link #note}) and contacts/attempts registered later
 * ({@link #record}). History is append-only and never deleted.
 */
@Entity
@Table(name = "lead_interactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeadInteraction {

    @Id
    private UUID id;

    // Read-only mapping of the owning FK (written by the parent Lead's @OneToMany @JoinColumn); lets
    // queries reference the lead id (e.g. the "no interaction" pending predicate).
    @Column(name = "lead_id", insertable = false, updatable = false)
    private UUID leadId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private InteractionType type;

    // Required for registered interactions (enforced at the boundary/service); null only for the
    // creation-time internal note, which is not a contact and has no result.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private InteractionResult result;

    @NotBlank
    @Size(max = 4000)
    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // Next contact scheduled by this interaction, if any (history of what was planned and when).
    @Column(name = "next_contact_at")
    private Instant nextContactAt;

    @NotNull
    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    static LeadInteraction note(InteractionType type, String content, UUID registeredBy) {
        LeadInteraction interaction = new LeadInteraction();
        interaction.id = UUID.randomUUID();
        interaction.type = type;
        interaction.content = content;
        interaction.occurredAt = Instant.now();
        interaction.registeredBy = registeredBy;
        return interaction;
    }

    static LeadInteraction record(
            InteractionType type,
            InteractionResult result,
            String content,
            Instant occurredAt,
            Instant nextContactAt,
            UUID registeredBy) {
        LeadInteraction interaction = new LeadInteraction();
        interaction.id = UUID.randomUUID();
        interaction.type = type;
        interaction.result = result;
        interaction.content = content;
        interaction.occurredAt = occurredAt;
        interaction.nextContactAt = nextContactAt;
        interaction.registeredBy = registeredBy;
        return interaction;
    }
}
