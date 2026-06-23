package com.fksoft.erp.domain.crm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * A stage-movement entry of an {@link Opportunity} (part of the Opportunity aggregate): which stage it
 * moved from and to, when, and who moved it. Preserves the commercial history of the pipeline
 * progression (including the move to {@code LOST}).
 */
@Entity
@Table(name = "opportunity_stage_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpportunityStageChange {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "from_stage", nullable = false)
    private String fromStage;

    @NotBlank
    @Size(max = 60)
    @Column(name = "to_stage", nullable = false)
    private String toStage;

    @NotNull
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @NotNull
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    static OpportunityStageChange of(String fromStage, String toStage, UUID changedBy) {
        OpportunityStageChange change = new OpportunityStageChange();
        change.id = UUID.randomUUID();
        change.fromStage = fromStage;
        change.toStage = toStage;
        change.changedBy = changedBy;
        change.changedAt = Instant.now();
        return change;
    }
}
