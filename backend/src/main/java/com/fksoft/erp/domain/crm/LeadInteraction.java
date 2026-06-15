package com.fksoft.erp.domain.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A single interaction/history entry of a {@link Lead} (part of the Lead aggregate). In this slice
 * only the initial note (an internal note) is created.
 */
@Entity
@Table(name = "lead_interactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeadInteraction {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private InteractionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private InteractionResult result;

    @Column(columnDefinition = "text")
    private String content;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

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
}
